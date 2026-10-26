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

    def read(self, node):
        with self.lock:
            target = self.db
            for part in node.split("/"):
                if not isinstance(target, dict) or part not in target:
                    return None
                target = target[part]
            return copy.deepcopy(target)

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


class SessionCase(unittest.TestCase):
    """The shared set-up: a fake vault, a fake claude, and helpers to wait for answers."""

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



class SessionTests(SessionCase):
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

    def test_a_crashed_worker_is_replaced_and_nothing_it_held_is_lost(self):
        manager = self.manager()
        self.vault.post("chatA", "m1", "first")
        self.answered("chatA", "m1")
        original = listener.ChatWorker._handle

        def die(self, message_id):
            raise SystemExit  # ends the worker's thread quietly, as an unexpected failure could

        listener.ChatWorker._handle = die
        try:
            self.vault.post("chatA", "m2", "second")
            self.vault.wait_for(lambda: not manager.workers["chatA"].is_alive())
        finally:
            listener.ChatWorker._handle = original
        self.vault.post("chatA", "m3", "third")  # submitting to the dead worker replaces it
        third = self.answered("chatA", "m3")
        second = self.answered("chatA", "m2")    # and the message the dead worker never answered
        self.assertEqual("done", self.vault.message("chatA", "m2")["status"])
        self.assertIn("turn", second["text"])
        self.assertIn("turn", third["text"])

    def test_a_rescan_queues_a_waiting_message_the_updates_missed(self):
        manager = self.manager()
        waiting = {"role": "user", "text": "hello", "createdAt": 1, "status": "pending"}
        self.vault.db = {"agent_chat": {"messages": {"chatA": {"m1": dict(waiting)}}}}
        manager.messages = {"chatA": {"m1": dict(waiting)}}  # in the mirror, but never queued
        manager.rescan()
        self.assertEqual("done", self.answered("chatA", "m1") and self.vault.message("chatA", "m1")["status"])

    def test_a_stopped_listener_closes_every_session(self):
        manager = self.manager()
        self.vault.post("chatA", "m1", "hello")
        self.answered("chatA", "m1")
        manager.shutdown()
        closed = self.vault.wait_for(lambda: self.vault.meta("chatA").get("state") == "closed" and self.vault.meta("chatA"))
        self.assertEqual("the listener stopped", closed["closedReason"])


class ModelChoiceTests(SessionCase):
    """The model and effort a chat asks for: read from Claude's own list, applied when a session starts,
    and re-applied (resuming the same conversation) when the choice changes."""

    def choose(self, chat_id, **fields):
        self.vault.write(f"agent_chat/chats/{chat_id}", fields, method="PATCH")

    def with_catalog(self, **kwargs):
        manager = self.manager(**kwargs)
        models, version = listener.fetch_catalog(self.home)
        manager.set_catalog(models)
        return manager, models, version

    def test_the_catalog_comes_from_claude_not_from_the_listener(self):
        models, version = listener.fetch_catalog(self.home)
        self.assertEqual("9.9.9", version)
        self.assertEqual(["default", "opus", "haiku"], [m["value"] for m in models])
        by_value = {m["value"]: m for m in models}
        self.assertEqual("Fake Opus", by_value["opus"]["name"])
        self.assertEqual("fake-opus", by_value["opus"]["resolved"])
        self.assertEqual(["low", "medium", "high"], by_value["opus"]["efforts"])
        self.assertEqual(["low", "medium", "high", "xhigh", "max"], by_value["default"]["efforts"])
        self.assertEqual([], by_value["haiku"]["efforts"])  # a model with no effort setting offers none

    def test_a_chat_with_no_choice_starts_on_claudes_defaults(self):
        self.with_catalog()
        self.vault.post("chatA", "m1", "hello")
        self.assertIn("model=None; effort=None", self.answered("chatA", "m1")["text"])

    def test_a_chosen_model_and_effort_start_the_session(self):
        self.with_catalog()
        self.choose("chatA", modelChoice="opus", effortChoice="high")
        self.vault.post("chatA", "m1", "hello")
        reply = self.answered("chatA", "m1")
        self.assertIn("model=opus; effort=high", reply["text"])
        meta = self.vault.wait_for(lambda: self.vault.meta("chatA").get("appliedModel") and self.vault.meta("chatA"))
        self.assertEqual("opus", meta["appliedModel"])
        self.assertEqual("high", meta["appliedEffort"])

    def test_changing_the_choice_reopens_the_same_conversation_on_the_new_model(self):
        self.with_catalog()
        self.vault.post("chatA", "m1", "first")
        first = self.answered("chatA", "m1")
        session_id = first["text"].split(";")[0].split(" of ")[1]
        self.assertIn("model=None", first["text"])
        self.choose("chatA", modelChoice="opus", effortChoice="medium")
        self.vault.post("chatA", "m2", "second")
        second = self.answered("chatA", "m2")
        self.assertIn(f"turn 2 of {session_id}", second["text"])  # same conversation, resumed
        self.assertIn("primed=False", second["text"])
        self.assertIn("model=opus; effort=medium", second["text"])
        # and switching back to the default drops the flags again
        self.choose("chatA", modelChoice="default", effortChoice=None)
        self.vault.post("chatA", "m3", "third")
        third = self.answered("chatA", "m3")
        self.assertIn(f"turn 3 of {session_id}", third["text"])
        self.assertIn("model=None; effort=None", third["text"])

    def test_an_unchanged_choice_keeps_the_session_open(self):
        self.with_catalog()
        self.choose("chatA", modelChoice="opus")
        self.vault.post("chatA", "m1", "one")
        self.answered("chatA", "m1")
        self.vault.post("chatA", "m2", "two")
        second = self.answered("chatA", "m2")
        self.assertIn("turn 2", second["text"])
        opened = [w for w in self.vault.writes if w[1].get("state") == "closed"]
        self.assertEqual([], opened)  # nothing was closed in between

    def test_a_model_claude_does_not_offer_is_ignored(self):
        self.with_catalog()
        self.choose("chatA", modelChoice="nonesuch")
        self.vault.post("chatA", "m1", "hello")
        reply = self.answered("chatA", "m1")
        self.assertIn("model=None", reply["text"])
        self.assertEqual("done", self.vault.message("chatA", "m1")["status"])

    def test_an_effort_the_model_does_not_take_is_dropped(self):
        self.with_catalog()
        self.choose("chatA", modelChoice="haiku", effortChoice="high")  # haiku has no effort setting
        self.vault.post("chatA", "m1", "hello")
        self.assertIn("model=haiku; effort=None", self.answered("chatA", "m1")["text"])
        self.choose("chatB", modelChoice="opus", effortChoice="max")    # opus tops out at high
        self.vault.post("chatB", "m1", "hello")
        self.assertIn("model=opus; effort=None", self.answered("chatB", "m1")["text"])

    def test_without_a_catalog_the_choice_is_passed_through(self):
        manager = self.manager()  # the catalog could not be read
        self.assertEqual(("some-model", "high"), manager.resolve_choice("some-model", "high"))
        self.assertEqual((None, None), manager.resolve_choice("default", ""))
        self.assertEqual((None, None), manager.resolve_choice(None, None))

    def test_the_choice_made_just_before_a_message_is_not_missed(self):
        manager, _, _ = self.with_catalog()
        # The chat's details arrive on another stream than its messages: simulate the mirror lagging.
        self.vault.db.setdefault("agent_chat", {}).setdefault("chats", {})["chatA"] = {"modelChoice": "opus"}
        self.assertNotIn("chatA", manager.chats)
        self.vault.post("chatA", "m1", "hello")
        self.assertIn("model=opus", self.answered("chatA", "m1")["text"])


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
