# Instagram Mod v452.0.0.48.64 - Build r7 (LSPatch)

Permanent, non-root Instagram mod. No Frida, no root, no PC attach.

## Changes in r7
- The accessibility pretence used by **Copy comment** is now off while the story viewer is open.
  That removes the left/right floating navigation buttons IG shows in stories when an
  accessibility service is detected, while comments keep working.
- Everything else from r6/r5 is unchanged.

## Included
- **Copy comment**: long-press a comment, tap the themed **Copy** pill (icon + label, white/dark
  per system theme); the exact comment text is copied, the menu closes and only one confirmation
  toast is shown.
- Comment menus never get the Download row (both menu shapes detected).
- Scrolling lag fixes (touch hook stores coordinates only; media tracker uses identity checks).

## Verified
- Phone (Infinix X6873): story plays with normal UI only (no accessibility nav buttons);
  comment long-press copies the exact comment ("Lantak la" test) with a single toast.

## Release asset
`Instagram_Mod_LSPatch_v452.0.0.48.64-r7.apk` (165391288 bytes)
SHA-256: `DB817C6EECCB7032D81C236C3DC72607911A42541539C29A99BD8D7D92769D00`
