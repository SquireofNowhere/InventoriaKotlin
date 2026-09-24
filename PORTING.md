# Porting Inventoria to an online app

Branch: `unstable-online` (forked from `unstable`).

The goal is one Inventoria, reachable from a browser as well as from the Android app, with the cloud
as the source of truth. The approach is **Kotlin Multiplatform**: the data model and domain rules
move into a `shared` module that every client compiles, and the web client is Compose Multiplatform
on Kotlin/Wasm, so the Compose skills and much of the UI code carry over.

## Layout

| Module    | Targets             | What lives there |
|-----------|---------------------|------------------|
| `app`     | Android             | The existing app. Unchanged so far: Room + Firebase SDK + live sync. |
| `shared`  | JVM, Wasm (browser) | The synced rows as `@Serializable` classes shaped exactly like the Realtime Database nodes, the pure domain rules (todo repeat cycles, reminders, schedule recurrence, task-type stats), and a Firebase REST client (Auth + Realtime Database). |
| `webApp`  | Wasm (browser)      | The Compose Multiplatform web client. |

## Building

The Android app needs an Android SDK just to configure, so the web half can be built without it:

```bash
# Run the shared tests
./gradlew -Pinventoria.webOnly=true :shared:jvmTest

# Dev server with hot reload, on http://localhost:8080
./gradlew -Pinventoria.webOnly=true :webApp:wasmJsBrowserDevelopmentRun

# Production bundle -> webApp/build/dist/wasmJs/productionExecutable/
./gradlew -Pinventoria.webOnly=true :webApp:wasmJsBrowserDistribution
```

The production bundle is static files and can be hosted anywhere (Firebase Hosting, GitHub Pages).

The first web build writes `kotlin-js-store/yarn.lock` (the npm packages the Wasm build uses).
Commit it, so later builds resolve the same versions.

### Configuration

The web app reads the same `.env` as the Android build, plus one more key:

- `FIREBASE_WEB_API_KEY`: Firebase Console → Project settings → General → Web API key.
- `FIREBASE_DATABASE_URL`: already there for Android.
- `DEFAULT_WEB_CLIENT_ID`: already there for Android. It's the Google OAuth web client that sign-in uses.

Any of these can also come from an environment variable of the same name (for CI) or from
`-Pinventoria.<NAME>=...`.

Two console steps are needed before Google sign-in works from a browser:

1. **Google Cloud Console → APIs & Services → Credentials → the web OAuth client** → add each
   origin the app is served from (for example `http://localhost:8080` and the hosting URL) under
   *Authorized JavaScript origins*.
2. **Firebase Console → Authentication → Settings → Authorized domains** → add the hosting domain.

The database rules in `database.rules.json` already work unchanged, because the web client signs in
as the same Firebase user and sends that user's ID token with every request.

## How the web client talks to Firebase

There is no local database on the web. `InventoriaRemote` reads `users/$uid/*` over the Realtime
Database REST API and writes with PATCH. Every write bumps `updatedAt`, which is the same rule the
Android sync relies on, so a phone merging the row treats the web's edit as the newer one.

Auth uses Google Identity Services in the page for an OAuth access token. That token is exchanged
with Identity Toolkit's `signInWithIdp` for a Firebase session. The refresh token is kept in
`localStorage`, so a reload stays signed in, which is also what the Firebase JS SDK does.

## Milestones

- [x] **1. Foundations.** `shared` models and domain rules with tests, the REST client, and a web
      client with Google sign-in. Today, Tasks, Todos, Inventory and Account screens read live
      data, and todos can be checked off.
- [ ] **2. Live updates.** Replace the one-minute poll with the REST streaming API (`EventSource`
      on `*.json?auth=...`), so edits from the phone show up in the browser within a second.
- [ ] **3. Editing on the web.** Start and stop task sessions, and create or edit todos, schedule
      blocks and items. Scoring (`TaskRepository.computeFrozenScore`) and streak logic move into
      `shared` first, so both clients freeze identical scores.
- [ ] **4. Android on `shared`.** Make the Android `data/model` classes map to and from the
      `shared` ones, and delete the duplicated domain logic in the app (`settleCycles`,
      `occursOn`, `computeTaskTypeStats`, …). `shared`'s JVM target is already consumable by the
      Android app.
- [ ] **5. Shared inventories.** Support reading another account's node through the
      invite/`sharedWith` flow (the Android app's manual sync id).
- [ ] **6. Parity screens.** Collections and readiness, productivity stats and pie chart, the
      schedule day view, and the map (Leaflet or MapLibre through JS interop in place of OSMDroid).
- [ ] **7. Hosting & CI.** Build the web bundle in GitHub Actions and deploy it to Firebase Hosting.

## Known gaps in the web client today

- Read-mostly: the only write is checking todos off.
- Refreshes every 60 s (and on demand from Account), not live.
- Photos aren't shown yet. Storage download URLs are in `imageUrls`, but they need an image loader
  for Wasm.
- TaskKind names drop their emoji, because the web build ships no emoji font. The colour dot
  carries the same meaning.
