"""Tests for the listener's per-chat sessions, run against fake_claude.py (no model, login or network).

    python -m unittest tools/agent-listener/test_listener.py        (from the repo root)
"""
import copy
import os
import shlex
import sys
import tempfile
import threading
import time
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parent))
import listener  # noqa: E402

FAKE = str(Path(__file__).resolve().parent / "fake_claude.py").replace("\\", "/")


class FakeVault:
    """An in-memory stand-in for the Firebase node tree, applying the listener's PUT/PATCH writes and
    echoing them to the manager the way the real event streams would."""

    def __init__(self):
        self.db = {}
        self.writes = []
        self.manager = None
        self.lock = threading.RLock()

    def write(self, node, value, method="PUT"):
        with self.lock:
            self.writes.append((node, copy.deepcopy(value), method))
            parts = node.split("/")
            target = self.db
            for part in parts[:-1]:
                target = target.setdefault(part, {})
            if method == "PUT":
                target[parts[-1]] = self._resolve(value)
            else:
                existing = target.setdefault(parts[-1], {})
                for key, item in value.items():
                    if item is None:
                        existing.pop(key, None)
                    else:
                        existing[key] = self._resolve(item)
            self._echo()

    @staticmethod
    def _resolve(value):
        if isinstance(value, dict) and value == {".sv": "timestamp"}:
            return int(time.time() * 1000)
        return copy.deepcopy(value)

    def _echo(self):
        chat = self.db.get("agent_chat", {})
        if self.manager is not None:
            self.manager.on_chats(chat.get("chats", {}))
            self.manager.on_messages(chat.get("messages", {}))

    # -- helpers for tests --
    def post(self, chat_id, message_id, text):
        """What the app does: a user message, pending."""
        with self.lock:
            node = self.db.setdefault("agent_chat", {}).setdefault("messages", {}).setdefault(chat_id, {})
            node[message_id] = {"role": "user", "text": text, "createdAt": 1, "status": "pending"}
            self._echo()

    def message(self, chat_id, message_id):
        with self.lock:
            return copy.deepcopy(self.db["agent_chat"]["messages"][chat_id].get(message_id))

    def meta(self, chat_id):
        with self.lock:
            return copy.deepcopy(self.db.get("agent_chat", {}).get("chats", {}).get(chat_id, {}))

    def wait_for(self, check, timeout=20):
        deadline = time.time() + timeout
        while time.time() < deadline:
            value = check()
            if value:
                return value
            time.sleep(0.05)
        raise AssertionError("timed out waiting; writes so far:\n" + "\n".join(map(str, self.writes[-12:])))


class SessionTests(unittest.TestCase):
    def setUp(self):
        self.home = Path(tempfile.mkdtemp())
        self.env_backup = dict(os.environ)
        os.environ["INVENTORIA_CLAUDE_CMD"] = f"{shlex.quote(sys.executable.replace(chr(92), '/'))} {FAKE}"
        os.environ["FAKE_CLAUDE_STATE"] = str(self.home / "fake-sessions.json")
        os.environ.pop("FAKE_CLAUDE_FAIL_RESUME", None)
        self.find_java = listener.find_java
        listener.find_java = lambda: ("java", None)
        self.vault = FakeVault()
        self.stop = threading.Event()
        self.managers = []

    def tearDown(self):
        for manager in self.managers:
            manager.shutdown()
        listener.find_java = self.find_java
        os.environ.clear()
        os.environ.update(self.env_backup)

    def manager(self, idle_minutes=60, max_sessions=3):
        args = SimpleNamespace(dry_run=False, timeout=30, idle_minutes=idle_minutes, max_sessions=max_sessions)
        manager = listener.ChatManager(self.vault, self.home, args, self.stop)
        self.vault.manager = manager
        manager.chats_ready.set()
        self.managers.append(manager)
        return manager

    def answered(self, chat_id, message_id):
        return self.vault.wait_for(
            lambda: (self.vault.message(chat_id, message_id) or {}).get("status") in ("done", "error")
            and self.vault.message(chat_id, message_id + "~r"))

    # -- the tests --
    def test_messages_in_one_chat_share_one_session(self):
        self.manager()
        self.vault.post("chatA", "m1", "first question")
        first = self.answered("chatA", "m1")
        self.vault.post("chatA", "m2", "second question")
        second = self.answered("chatA", "m2")
        self.assertIn("turn 1", first["text"])
        self.assertIn("turn 2", second["text"])  # the same process remembered the first message
        self.assertEqual(first["text"].split(";")[0].split(" of ")[1], second["text"].split(";")[0].split(" of ")[1])
        self.assertEqual("done", self.vault.message("chatA", "m2")["status"])

    def test_two_chats_are_two_separate_sessions(self):
        self.manager()
        self.vault.post("chatA", "a1", "hello from A")
        self.vault.post("chatB", "b1", "hello from B")
        a, b = self.answered("chatA", "a1"), self.answered("chatB", "b1")
        self.assertIn("turn 1", a["text"])
        self.assertIn("turn 1", b["text"])  # B did not inherit A's conversation
        self.assertNotEqual(a["text"].split(";")[0], b["text"].split(";")[0])

    def test_context_use_is_published_for_the_app(self):
        self.manager()
        self.vault.post("chatA", "m1", "hello")
        self.answered("chatA", "m1")
        meta = self.vault.wait_for(lambda: self.vault.meta("chatA").get("contextPercent") is not None
                                   and self.vault.meta("chatA"))
        self.assertEqual("live", meta["state"])
        self.assertEqual(200_000, meta["contextWindow"])
        self.assertEqual(10_020, meta["contextTokens"])  # 100 + 400 + 9_500 cache read + 20 output
        self.assertEqual(5, meta["contextPercent"])
        self.assertEqual(60, meta["idleMinutes"])
        self.assertTrue(meta["sessionId"].startswith("fake-"))
        self.assertGreater(meta["lastActiveAt"], 0)

    def test_context_grows_with_the_conversation(self):
        self.manager()
        for index in (1, 2, 3):
            self.vault.post("chatA", f"m{index}", f"message {index}")
            self.answered("chatA", f"m{index}")
        meta = self.vault.wait_for(lambda: self.vault.meta("chatA").get("contextTokens") == 30_020 and self.vault.meta("chatA"))
        self.assertEqual(15, meta["contextPercent"])

    def test_an_idle_session_closes_and_the_next_message_resumes_it(self):
        manager = self.manager()
        manager.idle_seconds = 1
        self.vault.post("chatA", "m1", "remember the number 42")
        first = self.answered("chatA", "m1")
        session_id = first["text"].split(";")[0].split(" of ")[1]
        closed = self.vault.wait_for(lambda: self.vault.meta("chatA").get("state") == "closed" and self.vault.meta("chatA"), 15)
        self.assertEqual("idle", closed["closedReason"])
        self.assertEqual(session_id, closed["sessionId"])  # kept, so the chat can be picked back up
        self.assertEqual(5, closed["contextPercent"])      # and so is how full it was
        manager.idle_seconds = 3600
        self.vault.post("chatA", "m2", "what number?")
        second = self.answered("chatA", "m2")
        self.assertIn(f"turn 2 of {session_id}", second["text"])  # resumed: same session, remembers turn 1
        self.assertIn("primed=False", second["text"])
        self.assertEqual("live", self.vault.wait_for(lambda: self.vault.meta("chatA")["state"] == "live" and self.vault.meta("chatA"))["state"])

    def test_a_session_that_cannot_be_resumed_starts_over_with_the_transcript(self):
        os.environ["FAKE_CLAUDE_FAIL_RESUME"] = "1"
        self.vault.db = {"agent_chat": {"chats": {"chatA": {"sessionId": "ghost-session", "state": "closed"}}}}
        self.manager()
        self.vault.post("chatA", "m1", "an earlier question")
        self.vault.write("agent_chat/messages/chatA/m1~r", {"role": "assistant", "text": "an earlier answer"})
        self.vault.write("agent_chat/messages/chatA/m1", {"status": "done"}, method="PATCH")
        self.vault.post("chatA", "m2", "a new question")
        reply = self.answered("chatA", "m2")
        self.assertIn("primed=True", reply["text"])  # the earlier conversation was handed to the new session
        self.assertEqual("done", self.vault.message("chatA", "m2")["status"])
        self.assertNotIn("ghost-session", reply["text"])

    def test_the_least_recently_used_session_makes_room(self):
        self.manager(max_sessions=1)
        self.vault.post("chatA", "a1", "hello A")
        self.answered("chatA", "a1")
        self.vault.post("chatB", "b1", "hello B")
        self.answered("chatB", "b1")
        closed = self.vault.wait_for(lambda: self.vault.meta("chatA").get("state") == "closed" and self.vault.meta("chatA"))
        self.assertEqual("to make room for another chat", closed["closedReason"])
        self.assertEqual("live", self.vault.meta("chatB")["state"])

    def test_a_crashed_session_reports_an_error_and_the_chat_recovers(self):
        self.manager()
        self.vault.post("chatA", "m1", "please CRASH now")
        reply = self.answered("chatA", "m1")
        self.assertEqual("error", self.vault.message("chatA", "m1")["status"])
        self.assertIn("couldn't answer", reply["text"])
        self.vault.post("chatA", "m2", "are you back?")
        second = self.answered("chatA", "m2")
        self.assertEqual("done", self.vault.message("chatA", "m2")["status"])
        self.assertIn("turn", second["text"])

    def test_messages_in_a_chat_are_answered_in_order(self):
        self.manager()
        self.vault.post("chatA", "m1", "SLOW first")
        self.vault.post("chatA", "m2", "second")
        first = self.answered("chatA", "m1")
        second = self.answered("chatA", "m2")
        self.assertIn("turn 1", first["text"])
        self.assertIn("turn 2", second["text"])

    def test_a_stopped_listener_closes_every_session(self):
        manager = self.manager()
        self.vault.post("chatA", "m1", "hello")
        self.answered("chatA", "m1")
        manager.shutdown()
        closed = self.vault.wait_for(lambda: self.vault.meta("chatA").get("state") == "closed" and self.vault.meta("chatA"))
        self.assertEqual("the listener stopped", closed["closedReason"])


class ContextMeterTests(unittest.TestCase):
    def test_reads_the_last_prompt_not_the_sum_of_calls(self):
        meter = listener.ContextMeter()
        for prompt in (1000, 5000):
            meter.observe({"type": "assistant", "message": {"usage": {"input_tokens": prompt, "output_tokens": 10}}})
        self.assertEqual(5010, meter.tokens)

    def test_ignores_sub_agent_turns(self):
        meter = listener.ContextMeter()
        meter.observe({"type": "assistant", "message": {"usage": {"input_tokens": 100}}})
        meter.observe({"type": "assistant", "parent_tool_use_id": "t1", "message": {"usage": {"input_tokens": 90_000}}})
        self.assertEqual(100, meter.tokens)

    def test_percent_uses_the_reported_window_or_a_guess_from_the_model(self):
        meter = listener.ContextMeter()
        meter.observe({"type": "system", "subtype": "init", "model": "claude-x[1m]"})
        meter.observe({"type": "assistant", "message": {"usage": {"input_tokens": 100_000}}})
        self.assertEqual(1_000_000, meter.snapshot()["contextWindow"])
        self.assertEqual(10, meter.snapshot()["contextPercent"])
        meter.observe({"type": "result", "modelUsage": {"m": {"contextWindow": 200_000}}})
        self.assertEqual(50, meter.snapshot()["contextPercent"])

    def test_percent_never_exceeds_100_and_starts_from_the_saved_reading(self):
        meter = listener.ContextMeter({"contextTokens": 500_000, "contextWindow": 200_000})
        self.assertEqual(100, meter.snapshot()["contextPercent"])
        self.assertEqual(0, listener.ContextMeter().snapshot()["contextPercent"])


if __name__ == "__main__":
    unittest.main()
