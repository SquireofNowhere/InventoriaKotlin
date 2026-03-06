# 📱 Deployment Guide - Publishing to Google Play Store

This guide walks you through preparing and publishing Inventoria to the Google Play Store.

## Pre-Deployment Checklist

### 1. App Quality & Features
- [ ] Test **Bi-Directional Sync** across multiple devices.
- [ ] Verify **Equip/Repack** logic works correctly with live GPS.
- [ ] Test **Drag-and-Drop** hierarchy in both Inventory and Collections screens.
- [ ] Confirm **Monotonic Task Sync** handles rapid operations without desync.
- [ ] Test in both light and dark themes.
- [ ] No crashes or ANRs.

### 2. Assets Preparation
- [ ] App icon in all required sizes (Purple Sheen style).
- [ ] Feature graphic (1024 x 500).
- [ ] Screenshots showing tree-view and map features.
- [ ] App description emphasizing hierarchy and linking features.

### 3. Legal & Privacy
- [ ] Privacy policy created (must mention Firebase usage).
- [ ] Target audience defined (13+).

## Step 1: Generate Release Artifacts

### 1.1 Create a Keystore

```bash
# Generate keystore
keytool -genkey -v -keystore inventoria-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias inventoria
```

**Important:** 
- Store this file securely.
- Never commit to version control.

### 1.2 Configure Signing

Create `keystore.properties` in project root (add to `.gitignore`):

```properties
storePassword=YOUR_STORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=inventoria
storeFile=/path/to/inventoria-release-key.jks
```

### 1.3 Update build.gradle.kts

Add the signing configuration to the `android` block and set `isMinifyEnabled = true` for the release build type to optimize performance and reduce APK size.

### 1.4 Build the Release

Generate App Bundle (Recommended):
```bash
./gradlew bundleRelease
```

## Step 2: Google Play Console Setup

### App Details

**Title:** Inventoria - Hierarchical Inventory

**Full Description Snippet:**
```
📦 Inventoria - Physical Hierarchy & Logical Linking

Manage your assets with a system that understands the real world. 

✨ KEY INNOVATIONS
• PHYSICAL HIERARCHY: Nest items inside containers (bags inside boxes inside rooms).
• LOGICAL LINKING: Link items to follow each other regardless of physical location.
• SMART REPACKING: One-tap un-equip that knows exactly which box an item came from.
• PRECISION SYNC: Monotonic timestamping for flawless multi-device coordination.
• TACTILE UI: Premium haptic feedback for every drag-and-drop.
```

## Step 3: Production Rollout

### Release Notes Example:
```
🎉 Version 1.15 - The Tactile Update

✨ NEW: Deep hierarchical views now available inside Collections.
✨ NEW: Context-aware drag-and-drop (Store vs Link).
✨ NEW: Monotonic task syncing for perfect multi-device harmony.
✨ NEW: Automatic sync every time you switch tabs.
🛠️ FIXED: Circular dependency loops are now automatically detected and healed.
```

## Step 4: Post-Launch Monitoring

- **Firebase Console**: Monitor Realtime Database usage and connection stability.
- **Play Console Vitals**: Track ANR/Crash rates, especially during high-frequency sync operations.

---
**Build with 💜 for power users.**
