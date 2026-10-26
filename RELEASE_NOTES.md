# Instagram Mod v452.0.0.48.64 - Build r5 (LSPatch)

Permanent, non-root Instagram mod. No Frida, no root, no PC attach.

## Changes in r5
- The comment menu action is now a compact **Copy** pill with a copy icon (two overlapping sheets)
  instead of the wide green "Copy comment" button.
- The pill follows the system theme: white background with dark text in light mode, dark
  background with white text in dark mode, plus a subtle 1 dp outline so it reads as a chip on
  either menu surface.
- Copying still closes the menu automatically.

## Still included from r4
- **Copy comment** copies the exact long-pressed comment (merged `user said body` labels are made
  available inside the IG process only, then cached from `ComponentHost.setContentDescription`).
- Comment menus never receive the Download row (both menu shapes detected).
- Scrolling lag fixes (touch hook stores coordinates only; media tracker uses identity checks).

## Verified
- Phone (Infinix X6873), dark and light themes: pill renders correctly in both, tapping copies the
  exact comment and dismisses the menu; comment menu has no Download row; post overflow still does.

## Release asset
`Instagram_Mod_LSPatch_v452.0.0.48.64-r5.apk` (165,391,288 bytes)
SHA-256: `A63798B114FD6D3E540DFFDDCB9CD7C77068A9D4F348F8D4F2E0986B711E7345`
