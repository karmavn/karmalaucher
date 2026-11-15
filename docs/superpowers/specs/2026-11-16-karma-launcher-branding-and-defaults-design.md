# Design Document: Karma Launcher Rebranding, Defaults & Minimalist Icon

> **Date:** 2026-11-16  
> **Status:** Draft (Pending Approval)  
> **Target:** `karmalaucher` (Android 16 / Launcher3 base)

---

## 1. Intent & Context
Transform the upstream codebase into **Karma Launcher**, establishing its brand identity, optimizing first-run out-of-the-box user experience, and introducing a modern minimalist "K" adaptive icon.

---

## 2. Goals & Non-Goals

### Goals
- **Branding**: App display name updated to **"Karma Launcher"** (Release) and **"Karma Launcher (Debug)"** across system home screens, app drawer, settings, and recents.
- **About Screen**: Links updated to point to `https://github.com/karmavn/karmalaucher` with cleaned community/contributor metadata.
- **Default Grid & Preferences**: First install defaults to a modern 5×5 workspace grid, 5 hotseat columns, and 5 app drawer columns with Material You dynamic theming.
- **Minimalist Icon**: Pure vector adaptive icon (`adaptive-icon`) featuring a sharp, geometric minimalist letter **"K"** with light/dark contrast and Android 13+ monochrome Themed Icon support.

### Non-Goals
- Changing `applicationId` (`app.lawnchair`): Preserves Android Quickstep recents provider compatibility and avoids breaking system gesture bindings.

---

## 3. Approaches Considered

| Approach | Description | Tradeoffs | Selected? |
| :--- | :--- | :--- | :---: |
| **Approach 1 (Token & Asset Rebranding)** | Update `derived_app_name`, customize `About.kt`, adjust default preference specs, and design crisp Vector XML adaptive icons. | Fast, non-destructive, 100% vector scalability, zero risk of breaking system recents gestures. | **YES (Chosen)** |
| **Approach 2 (Full Package Renaming)** | Rename root package and `applicationId` to `app.karma.launcher`. | High risk of breaking Quickstep recents compatibility on custom ROMs and requires full keystore re-generation. | **NO (Rejected)** |

---

## 4. Implementation Specifications

### A. Branding Changes
1. **`build.gradle`**:
   - `debug { resValue("string", "derived_app_name", "Karma Launcher (Debug)") }`
   - `release { resValue("string", "derived_app_name", "Karma Launcher") }`
2. **`lawnchair/src/app/lawnchair/ui/preferences/about/AboutViewModel.kt`**:
   - Update repository link to `https://github.com/karmavn/karmalaucher`.
   - Update commit hash URL to `https://github.com/karmavn/karmalaucher/commit/${BuildConfig.COMMIT_HASH}`.

### B. Out-of-the-box Defaults
1. **`lawnchair/src/app/lawnchair/preferences/PreferenceManager.kt`**:
   - Ensure default grid specification evaluates to 5 workspace columns, 5 workspace rows, and 5 hotseat columns.
2. **`lawnchair/src/app/lawnchair/preferences2/PreferenceManager2.kt`**:
   - Default all-apps drawer columns to 5.

### C. Minimalist "K" Vector Adaptive Icon
1. **`res/mipmap-anydpi-v26/ic_launcher_home.xml` & `ic_launcher_home_round.xml`**:
   - Point `background` to `@drawable/ic_launcher_home_background`
   - Point `foreground` to `@drawable/ic_launcher_home_foreground`
   - Point `monochrome` to `@drawable/ic_launcher_home_monochrome`
2. **`res/drawable/ic_launcher_home_foreground.xml`**:
   - Clean, geometric minimalist "K" motif constructed in a 108×108dp viewport (safe zone: center 72dp).
3. **`res/drawable/ic_launcher_home_background.xml`**:
   - Elegant dark slate / subtle gradient background conforming to Material 3 surface elevation.
4. **`lawnchair/res/drawable/ic_launcher_home_monochrome.xml`**:
   - Single-color vector "K" glyph for Android 13–16 Material You themed icons.
5. **`lawnchair/res/drawable/ic_launcher_home_comp.png`**:
   - Updated logo asset for the About screen header.

---

## 5. Verification Plan
- Static validation of XML vector syntax against Android AAPT2 schema.
- Verification of string resolution in Gradle build config.
- Verification of default preference fallback values.
