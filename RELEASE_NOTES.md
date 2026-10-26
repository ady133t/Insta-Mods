# Instagram Mod v452.0.0.48.64 - Build r6 (LSPatch)

Permanent, non-root Instagram mod. No Frida, no root, no PC attach.

## Changes in r6
- Fixed the double confirmation after copying a comment. The mod's own Comment copied toast is
  now the only toast: the clipboard entry is flagged sensitive so the system/OEM clipboard UI no
  longer adds its own Copied toast, and any duplicate Copied toast from IG is suppressed.
- The comment action is the themed **Copy** pill with a copy icon from r5 (white pill with dark
  text in light mode, dark pill with white text in dark mode).

## Included
- **Copy comment**: long-press a comment, tap the pill, the exact comment text lands on the
  clipboard and the menu closes.
- Comment menus never get the Download row (both menu shapes detected).
- Scrolling lag fixes (touch hook stores coordinates only; media tracker uses identity checks).

## Verified
- Phone (Infinix X6873): one toast (Comment copied) after tapping the pill; exact comment copied;
  menu closes; comment menu has no Download; post overflow still shows Download.

## Release asset
`Instagram_Mod_LSPatch_v452.0.0.48.64-r6.apk` (165391288 bytes)
SHA-256: `9775B633166B2431A9957130B2C819CBC6672B02567FCA9E9E322ED9C79276EC`
