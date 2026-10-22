# Instagram Mod v452.0.0.48.64 — LSPatch Java Module

Permanent, non-root Instagram mod. No Frida, no root, no PC attach.

## Release asset
- `Instagram_Mod_LSPatch_v452.0.0.48.64.apk`
- Size: 165,366,712 bytes
- SHA-256: `DBABBFDD99EEA0CBFA7C7E819F2457629F44DAA145E4024EBE3061F8C6C93953`

## Features
- Mod Setting button on your profile with Anonymous story view and Download toggles.
- Download row in post and story menus, gated by the Download setting.
- Feed/post downloads resolve the exact visible post (video or best image up to 1080 px).
- Story downloads resolve the currently displayed slide.
- Anonymous story view suppresses the story seen report while ON; forwards while OFF.
- Settings persist in SharedPreferences `igmod`.

## Install
1. Uninstall the Play Store Instagram (different signature; deletes login/data).
2. Install the APK and log in.
3. Disable Play Store auto-update for Instagram.
4. Allow Play Protect / unknown-source install if prompted.

Prefer a burner account; IG/LSPatch may be detectable.

## Notes
- Base: Instagram 452.0.0.48.64, arm64-v8a.
- LSPatch v1.2 (`--injectdex --target-sdk 35 -l 2`), embedded module `com.dl.igmod`.
- Debug-signed. Updating from a previous debug-signed mod can use `adb install -r`.
- Repo docs: `Readme.md`, `Skills.md`, `Findings.md`; module source under `lspatch/source`.
