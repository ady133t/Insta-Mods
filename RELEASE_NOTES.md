# Instagram Mod v452.0.0.48.64 - Build r9 (LSPatch)

Permanent, non-root Instagram mod. No Frida, no root, no PC attach.

## Story navigation restored (r9)

Cause: the mod pretends an accessibility service is connected so Instagram builds the merged
"user said body" labels that Copy comment reads. While Instagram believes a screen reader is
active it swaps the normal full-width left/right tap zones for two small **Previous story /
Next story** arrow buttons, so only taps landing exactly on those buttons navigate.

Fix: the accessibility pretence is now switched **off for the whole story viewer** - starting at
the `ReelViewerFragment` constructor, before any view is inflated - and restored when the viewer
pauses. Instagram therefore builds its normal story UI: no arrow buttons, and tapping the left or
right side of the story navigates exactly like the stock app.

## Unchanged

- **Copy comment**: long-press a comment, tap the **Copy** pill, exact text is copied, one toast.
- Comment menus never get a Download row.
- Anonymous story view, ghost badge, follower badge, and post/story downloads.

## Verified

- Emulator (MuMu 16384): no story arrow buttons; comment labels still build after a cold start and
  the exact comment was captured (`Great job`).
- Phone verification of edge taps pending (device was offline during this build).

## Release asset

`Instagram_Mod_LSPatch_v452.0.0.48.64-r9.apk` (165,395,384 bytes)

SHA-256: `97A050F443D82CA7D51F9405BDD906B4CA5B0EEE9C910D36F6E18314F1D0D702`
