# Instagram Mod v452.0.0.48.64 — Build r3 (LSPatch)

Permanent, non-root Instagram mod. No Frida, no root, no PC attach.

## Changes in r3
- Both Mod Setting and menu Download buttons now use **#1ED760** (30, 215, 96).
- Menu Download is pinned at the **top** of post/story ≡ sheets as a rounded button.
- Anonymous story view now shows a small semi-transparent ghost badge inside the bottom-right
  corner of the story image, with matching ~12 dp visible spacing on the right and bottom (above
  the reply bar).
- **Ghost position fix:** the badge anchors to the visible story-content frame instead of the
  fragment root, and lifts just above Like/Share only when those icons overlap the corner (MuMu).
- Deployed and verified on the MuMu emulator and Infinix X6873 phone.

## Release asset
- `Instagram_Mod_LSPatch_v452.0.0.48.64-r3.apk`
- Size: 165,370,808 bytes
- SHA-256: `0964B62A9E6E1BB648C816290BFB81597B8761B9D49F324180172B261EEF7DD2`

## Features
- Mod Setting button on your profile with Anonymous story view and
  Download (Stories,Reels,Feeds) toggles.
- Download button at the top of post and story menus, gated by the download setting.
- Feed/post downloads resolve the exact visible post (video or best image up to 1080 px).
- Story downloads resolve the currently displayed slide.
- Anonymous story view suppresses the story seen report and displays the ghost badge while ON;
  forwards and hides the badge while OFF.
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
