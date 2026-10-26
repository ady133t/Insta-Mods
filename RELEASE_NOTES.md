# Instagram Mod v452.0.0.48.64 - Build r8 (LSPatch)

Permanent, non-root Instagram mod. No Frida, no root, no PC attach.

## Changes in r8
- Removed Instagram's own **Previous story / Next story** arrow buttons from the story viewer.
  They are ordinary Buttons with those content descriptions; the mod makes them invisible while
  the story viewer is open (re-checked every 400 ms, since IG recreates them per story item).
- Copy comment keeps working exactly as before.

## Included
- **Copy comment**: long-press a comment, tap the themed **Copy** pill, the exact comment text is
  copied, the menu closes, and a single confirmation toast is shown.
- Comment menus never get the Download row (both menu shapes detected).
- Scrolling lag fixes (touch hook stores coordinates only; media tracker uses identity checks).

## Verified
- Phone (Infinix X6873): story viewer shows no Previous/Next buttons (hierarchy + screenshot);
  comment long-press copied "bosan dh dgr, x amik action gak" exactly.

## Release asset
`Instagram_Mod_LSPatch_v452.0.0.48.64-r8.apk` (165391288 bytes)
SHA-256: `D3D2D6A9569E7BD019ECAB1CFA8EF1B5CCD2180EFB0B8775B9347FB8DCBF07D6`
