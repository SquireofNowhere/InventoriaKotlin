# Realtime Database rules

`database.rules.json` is the reviewable copy of the rules. **Editing it changes nothing on its own** —
the live rules are whatever is in **Firebase Console → Realtime Database → Rules**. Paste this file
there (or `firebase deploy --only database`) for a change to take effect.

It is tracked because access control was previously only visible in the console, which meant no
review of the sharing model could cover the half that actually enforces it.

## What the model is meant to be

Three states, and the rules have to keep them apart:

- **Own account** — `users/$uid` is yours, full read and write.
- **Shared in** — someone gave you their invite code, so you read and write *their* inventory data.
- **Nobody else** — no access at all.

## Why the rules look like this

### `invites/$code` must validate its value

`.write` allows claiming a code whose slot is empty. Without the `.validate`, the *value* written is
unconstrained, and that is a complete authorization bypass rather than a small gap:

1. Attacker learns a victim's uid. The app shows it on screen — "This device's ID" has a reveal
   toggle, and the delete dialog prints it — so it travels in screenshots, and every joiner already
   knows the owner's uid because it is their `manualSyncId`.
2. Attacker claims any unused code with the victim's uid as the value: `invites/XYZ999 = victimUid`.
   The old `.write` permits this, because `!data.exists()` is true and nothing checks the value.
3. Attacker writes `users/victimUid/sharedWith/attackerUid = "XYZ999"`. The `sharedWith` rule asks
   whether `invites/XYZ999` points at the victim — it does, because the attacker just made it so.
4. Attacker now has the victim's whole account.

No invite code from the victim is involved at any point. `newData.val() === auth.uid` closes it: a
code may only ever point at the person who claimed it. `.validate` is skipped on deletes, so
retiring a code still works.

### `users/$uid` `.write` must be owner-only

Rules cascade downward and a deeper rule can only *add* permission, never take it away. Granting
`.write` at `users/$uid` to anyone in `sharedWith` therefore handed every joiner far more than the
shared inventory:

- delete `users/$uid` outright, destroying the owner's entire database;
- write `users/$uid/sharedWith/<anyone>`, permanently attaching a third party — who survives the
  owner revoking the original joiner;
- remove other joiners, and overwrite `my_invite_code`.

So `.write` at `users/$uid` is now `$uid === auth.uid`, and the eight data children carry the
joiner's write grant individually. The owner still inherits write everywhere from the parent, which
is what lets account deletion remove the whole node.

The child list is written out rather than using a `$section` wildcard on purpose: named children do
take precedence over a wildcard at the same level, but a security boundary is the wrong place to
lean on that.

### Codes expire after 24 hours

`invites/$code` holds `{ uid, expiresAt }` rather than a bare uid string, and both the `expiresAt`
child validator and the `sharedWith` write rule check it against `now`.

This is the answer to codes being brute-forceable: any authenticated user — including a fresh
anonymous account, which this app hands out freely — can probe `invites/<guess>` without limit, and
an attacker does not need a *particular* code, just any live one. Six characters is about 2.2 billion
combinations, so what actually bounds the risk is how many codes are alive at once. A day is short
enough to keep that number near zero most of the time. (The parent `invites` node still has no
`.read`, so the list itself cannot be enumerated.)

Two deliberate consequences:

- **Expiry gates joining, not access.** A `sharedWith` entry is only written once, at link time, and
  nothing re-validates it afterwards — so someone who joined before the code died stays connected.
  Revoke, and retiring the code, are what cut people off.
- **Expired entries are not deleted**, because rules cannot delete on a timer. Anyone may overwrite
  an entry whose `expiresAt` has passed (`data.child('expiresAt').val() < now` in `.write`), so
  slots recycle themselves as codes are generated. `FirebaseAuthRepository` also retires the
  previous code whenever it mints a new one.

`expiresAt` is set by the client, so the validator bounds it: strictly in the future, and less than
48 hours out. The generous ceiling against a 24-hour lifetime is clock-skew slack — a device whose
clock runs fast can still generate a code. A too-long code is only self-harm anyway, since `uid` is
pinned to `auth.uid`.

### Legacy codes stop working, gracefully

Codes issued before this change are bare strings with no `expiresAt`. The `sharedWith` rule requires
`expiresAt > now`, which a missing value fails, so they can no longer be used to join. Nothing needs
migrating: `getExistingInviteCode()` reports a missing `expiresAt` as long expired, the owner is
shown "your invite code has expired", and generating a replacement retires the old entry. Retiring a
legacy code by hand also still works, because `.write` keeps the `data.val() === auth.uid` clause
that matches the old string shape.

## Known limits, not yet addressed

- **Storage rules are separate.** Item photos go to `users/{uid}/item_images` in Cloud Storage under
  its own ruleset, which is not in this file — see [`storage.rules.md`](storage.rules.md).
