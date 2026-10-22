# Instagram Mod v452.0.0.48.64 — Build r2 (LSPatch)

Permanent, non-root Instagram mod. No Frida, no root, no PC attach.

## Changes in r2
- Renamed the Mod Settings download toggle to **Download (Stories,Reels,Feeds)**.
- Rebuilt and redeployed on the MuMu emulator and Infinix X6873 phone.

## Release asset
- `Instagram_Mod_LSPatch_v452.0.0.48.64-r2.apk`
- Size: 165,366,712 bytes
- SHA-256: `BCB019E152A5CFBD935A1CD398D374D7B487C616A7530871390A46A02A62C15C`

## Features
- Mod Setting button on your profile with Anonymous story view and
  Download (Stories,Reels,Feeds) toggles.
- Download row in post and story menus, gated by the download setting.
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
