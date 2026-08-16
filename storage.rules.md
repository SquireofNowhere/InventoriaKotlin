# Cloud Storage rules

`storage.rules` is the reviewable copy. **Editing it changes nothing on its own** — the live rules
are whatever is in **Firebase Console → Storage → Rules**. Paste it there (or
`firebase deploy --only storage`) for a change to take effect. Same arrangement as
[`database.rules.md`](database.rules.md).

Item photos are the only thing in the bucket: `users/{uid}/item_images/img_{millis}_{uuid}.jpg`.

## What the previous rules allowed

```
match /users/{userId}/{allPaths=**} {
  allow read:  if request.auth != null;
  allow write: if request.auth != null;
}
```

Both conditions are just "is signed in", and this app hands an anonymous account to anyone who
installs it. The comment on the old `write` rule said the app repository handles folder targeting —
that is the client deciding its own access, which is what rules exist to not rely on.

Given any uid — and uids are on screen in the app, printed in the delete dialog, and held by every
joiner as their `manualSyncId` — any installer could:

- **list and download every photo in that account.** `list` is governed by `read`, so the random
  UUID filenames protect nothing once the folder can be enumerated;
- **delete every photo**, leaving the item records pointing at dead URLs;
- **overwrite existing objects** with arbitrary content, served from the project's bucket under the
  victim's folder and displayed by the victim's own app;
- **upload unlimited files of unlimited size**, billed to the project. There was no size cap and no
  content-type cap.

## Why the fix had to touch the client

Storage rules can query Firestore, but **not the Realtime Database**. The sharing model lives in
`users/{uid}/sharedWith` in RTDB, so no Storage rule can tell a genuine joiner from any other signed
in user. With the old layout — joiners uploading into the *owner's* folder — that left no way to
write a correct rule: `getDownloadUrl()` needs `read` on the object it just wrote, so a joiner
needed read on someone else's folder, and the only expressible version of that is "anyone".

So `FirebaseStorageRepository.uploadItemImage` now uses `getOrCreateOwnUserId()` instead of
`getOrCreateUserId()`. Everyone uploads under their own uid, and the rule collapses to
`request.auth.uid == userId`.

**Sharing still works.** What syncs through RTDB is the tokenized download URL
(`…?alt=media&token=…`), and fetching that URL does not consult Storage rules at all — which is also
why the old `allow read` was never what made shared photos visible, only extra exposure on top.

## Consequences worth knowing

- **Photos follow whoever uploaded them.** A joiner who deletes their account removes the photos
  they contributed to your inventory; previously those stayed in your folder. The reverse improved:
  deleting your account no longer leaves your photos sitting in someone else's folder.
- **Existing images are unaffected.** Anything already uploaded keeps working — its download URL is
  unchanged, and objects already in an owner's own folder still match the new rule. Only photos a
  *joiner* previously uploaded into an owner's folder become unreachable to that joiner through the
  Storage API; the owner still has them, and the synced URLs still render for everyone.
- `deleteUserAccount()` lists and deletes `users/{uid}/item_images` under the account's own uid, so
  it satisfies the new rule unchanged.
