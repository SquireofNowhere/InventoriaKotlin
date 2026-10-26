#!/usr/bin/env python3
"""A stand-in for `claude -p --input-format stream-json --output-format stream-json`, for the listener's
tests. It speaks the same line protocol (one JSON user message per line in, system/assistant/result
events out) without a model, a login or a network.

Behaviour the tests lean on:
  * it remembers the messages of a session, and `--resume ID` finds them again, through the file named
    by FAKE_CLAUDE_STATE (a real Claude keeps sessions on disk the same way);
  * with FAKE_CLAUDE_FAIL_RESUME set, resuming an id it does not know exits with an error, as Claude does;
  * a message containing CRASH kills the process, one containing SLOW takes three seconds;
  * every reply says which turn of the session it is, which session and whether the message arrived
    with the earlier conversation attached, so a test can tell a kept session from a fresh one.
"""
import json
import os
import sys
import time


def emit(event):
    print(json.dumps(event), flush=True)


def main():
    argv = sys.argv[1:]
    resume = argv[argv.index("--resume") + 1] if "--resume" in argv else None
    state_path = os.environ.get("FAKE_CLAUDE_STATE")
    state = {}
    if state_path and os.path.exists(state_path):
        with open(state_path, encoding="utf-8") as handle:
            state = json.load(handle)

    if resume and resume not in state:
        if os.environ.get("FAKE_CLAUDE_FAIL_RESUME"):
            print(f"No conversation found with session ID: {resume}", file=sys.stderr, flush=True)
            return 1
        state[resume] = []
    session_id = resume or f"fake-{os.getpid()}"
    history = state.setdefault(session_id, [])

    emit({"type": "system", "subtype": "init", "session_id": session_id, "model": "fake-model",
          "mcp_servers": [{"name": "inventoria", "status": "connected"}]})

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        message = json.loads(line)
        text = message["message"]["content"]
        if "CRASH" in text:
            return 3
        if "SLOW" in text:
            time.sleep(3)
        history.append(text)
        if state_path:
            with open(state_path, "w", encoding="utf-8") as handle:
                json.dump(state, handle)
        turn = len(history)
        primed = text.startswith("This chat already has history")
        # What the prompt would weigh: grows with the conversation, so the meter has something to read.
        prompt_tokens = 10_000 * turn
        reply = f"turn {turn} of {session_id}; primed={primed}; heard={text[:40]!r}"
        emit({"type": "assistant", "session_id": session_id, "message": {
            "content": [{"type": "text", "text": reply}],
            "usage": {"input_tokens": 100, "cache_creation_input_tokens": 400,
                      "cache_read_input_tokens": prompt_tokens - 500, "output_tokens": 20}}})
        emit({"type": "result", "subtype": "success", "is_error": False, "result": reply,
              "session_id": session_id,
              "modelUsage": {"fake-model": {"contextWindow": 200_000}}})
    return 0


if __name__ == "__main__":
    sys.exit(main())
