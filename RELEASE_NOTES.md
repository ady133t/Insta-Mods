# Instagram Mod v452.0.0.48.64 - Build r4 (LSPatch)

Permanent, non-root Instagram mod. No Frida, no root, no PC attach.

## Changes in r4
- **Copy comment**: long-press a comment and tap the green **Copy comment** pill to copy that
  exact comment's text. IG only builds the merged "user said body" row label while an
  accessibility service is connected, so the module reports one as connected inside the IG
  process only, caches the labels as Litho pushes them through
  `ComponentHost.setContentDescription`, and copies the entry nearest the press.
- Comment menus no longer receive the Download row. Both menu shapes are detected: the
  `Reply with a reel` variant and the phone variant with only `Share / Report / Block / Dismiss`.
- The injected row resizes its PopupWindow, otherwise the pill is drawn outside the window's
  touchable area and taps only dismiss the menu.
- **Scrolling lag fixed**: the touch hook only records coordinates (no window walk on every
  finger-down) and the media tracker uses identity checks instead of reflective id lookups.

## Verified
- Phone (Infinix X6873): long-press comment -> green Copy comment pill -> clipboard contains the
  exact comment, menu closes; post overflow still shows Download; comment menu has no Download.
- Emulator (MuMu, Android 15): same flow plus feed/story downloads, ghost badge, follower badge.

## Release asset
`Instagram_Mod_LSPatch_v452.0.0.48.64-r4.apk` (165,391,288 bytes)
SHA-256: `1AAB2A854A169AA09B1A33624E008E9633F55F0194E76631CFDEB5F0172ED085`
