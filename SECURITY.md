# Security notes

This repository is public. `.env` and `app/google-services.json` were committed to it from the
start and have now been untracked — but **untracking does not unpublish them**. Both remain in the
git history and in every existing clone and fork.

## What was exposed, and how much it matters

| Value | In the public repo | Actually a secret? |
|---|---|---|
| Firebase API key (`google-services.json`) | Yes, in history | No — it ships inside the APK and is readable by anyone who unpacks it |
| OAuth web client ID (`.env`) | Yes, in history | No — same, it is a public client identifier |
| Realtime Database URL | Yes, in history | No, but it tells anyone exactly which database to try |
| Storage bucket name | Yes, in history | No, same |

None of these are passwords, and rotating them is mostly not a thing you can do. That is the
normal Firebase model: **the client credentials are public by design, and the only thing standing
between a stranger and your data is your security rules.**

The practical consequence of the repo being public is therefore not "someone has your keys" — it
is "someone knows where your database lives and can trivially test whether it is open."

## The checklist that actually matters

Run through this in the Firebase console. Everything above is noise until these are right.

### 1. Realtime Database rules

Open **Firebase Console → Realtime Database → Rules**. If you see anything resembling:

```json
{ "rules": { ".read": true, ".write": true } }
```

then your entire database is world-readable and world-writable right now, and the public repo means
the URL to read it is a search away. Fix this first.

Rules should scope every path to the authenticated owner, roughly:

```json
{
  "rules": {
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid || root.child('users').child($uid).child('sharedWith').hasChild(auth.uid)",
        ".write": "$uid === auth.uid"
      }
    }
  }
}
```

Adjust to match the actual shape this app writes, including the invite-code sharing path — the app
supports syncing to another account, so the read rule has to admit invited UIDs without opening the
whole tree.

### 2. Storage rules

**Firebase Console → Storage → Rules.** Item photos are uploaded here. The default template in some
projects allows any authenticated user to read any path, which across accounts means other people's
photos. Scope reads and writes to the owning UID.

### 3. Check whether anonymous or self-signup access is enabled

**Authentication → Sign-in method.** If anything beyond Google is enabled that you are not using —
particularly Anonymous — anyone can obtain an authenticated session and then whatever your rules
grant to "any signed-in user".

### 4. Restrict the API key

**Google Cloud Console → APIs & Services → Credentials → your Android key.** Restrict it to the
Android app (package name `com.inventoria.app` plus your signing certificate's SHA-1) and to only
the APIs this app uses. This does not protect the database — rules do that — but it stops the key
being reused by anything else.

### 5. Look at usage

**Firebase Console → Usage.** Unexpected reads, writes or storage egress are the signal that
something already found the database. Worth one look now that you know the URL has been public.

## Realtime Database rules

The rules are tracked at [`database.rules.json`](database.rules.json), with the reasoning in
[`database.rules.md`](database.rules.md). That file is a *copy* — the live rules are whatever is in
Firebase Console -> Realtime Database -> Rules, so a change there is what takes effect.

Read `database.rules.md` before changing anything under `invites` or `sharedWith`. It documents an
authorization bypass the earlier rules allowed (an unvalidated invite value let anyone who knew your
uid grant themselves your whole account) and why a joiner must not hold `.write` on `users/$uid`
itself.

## Going forward

- `.env` and `app/google-services.json` are in `.gitignore` and no longer tracked. A fresh clone
  will not build until both are supplied — copy `.env.example` to `.env` and download
  `google-services.json` from the Firebase console.
- If you ever want the history genuinely clean, that means rewriting it (`git filter-repo`) and
  force-pushing, which breaks every existing clone. Given none of these values are secrets, that is
  usually not worth it — getting the rules right is.
