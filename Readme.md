# Instagram Mod v452.0.0.48.64 — LSPatch Java Module

A permanent, non-root Instagram mod built as an **LSPatch-embedded Java module**.
No Frida, no Frida server, no JS bridge, no root, and no PC attach at runtime.

- **Repository:** https://github.com/ady133t/Insta-Mods
- **Download (Releases):** https://github.com/ady133t/Insta-Mods/releases/latest

## Deliverable

| Item | Value |
|---|---|
| APK | `Instagram_Mod_LSPatch_v452.0.0.48.64-r3.apk` (GitHub Release asset) |
| Local build path | `lspatch\Instagram_Mod_LSPatch_v452.0.0.48.64-r3.apk` |
| Size | 165,370,808 bytes |
| SHA-256 | `0964B62A9E6E1BB648C816290BFB81597B8761B9D49F324180172B261EEF7DD2` |
| Package | `com.instagram.android` |
| Version | 452.0.0.48.64 (versionCode 385810270) |
| ABI | arm64-v8a |
| Signer | Android debug key (`fd55cd8d…`) |
| Module package | `com.dl.igmod` |
| LSPatch | JingMatrix LSPatch v1.2 (487), `--injectdex --target-sdk 35 -l 2` |

The APK is published as a **release asset** because it exceeds GitHub's 100 MB per-file git limit.
The repo contains docs and the module source; `perm_test_decoded` and `base_clean.apk` are excluded
because of their size.

Build `r2`: renamed the Mod Settings row to **Download (Stories,Reels,Feeds)**.
Build `r3`: moved the menu Download row to the **top**, turned it into a rounded green
`#1ED760` button, and added the semi-transparent ghost badge overlay in the story viewer when
anonymous view is ON. The badge is anchored to the visible story-content frame, with matching
~12 dp visible spacing on its right and bottom edges, and lifts above Like/Share only on layouts
where those icons overlap the corner. Deployed and verified on the MuMu emulator and Infinix
X6873 phone.

Supporting docs:

- [`Skills.md`](Skills.md) — repeatable analysis / patching methodology
- [`Findings.md`](Findings.md) — exact IG classes, methods, stacks and runtime findings
- `Instagram_Mod_LSPatch_README.txt` — earlier install/rebuild note (superseded by this file)

## Features

- **Mod Setting** button on your own profile, centered below Edit/Share.
  Opens a native dialog with:
  - Anonymous story view: ON/OFF
  - Download (Stories,Reels,Feeds): ON/OFF
- When Anonymous story view is ON, a 36 dp semi-transparent ghost badge is drawn inside the
  bottom-right corner of the story image, with matching ~12 dp visible spacing on its right and
  bottom edges (above the reply bar). On layouts where the Like/Share icons overlap that corner,
  the badge moves just above them.
- Settings persist in `SharedPreferences("igmod")` (`anon_story`, `download_enabled`).
- **Download** button pinned at the top of post and story overflow menus, styled as a rounded
  green button. Shown only when Download (Stories,Reels,Feeds) is ON.
- **Feed/post download** resolves the exact visible post through its overflow-button
  listener/tag, then downloads:
  - best video variant via `Media.AB6()`, or
  - best image candidate via `MediaExtKt.A0g()` (verified 1080 px), falling back to `A0F`.
- **Story download** resolves the currently displayed slide and saves the exact slide media.
- **Anonymous story view**: when ON, the story seen-report path (`X.00OH.A03`) is suppressed;
  when OFF it forwards normally.
- Files are saved by `DownloadManager` to the public Download folder:
  `ig_<mediaId>.jpg` / `ig_<mediaId>.mp4`.
- No duplicate downloads for the same media ID while one is active.

## Performance

Measured with a 30-second `/proc/<pid>/stat` CPU-time delta on a static, screen-on screen.
Feed video playback naturally raises CPU and is not a mod cost.

| Device | LSPatch Java (this build) | Frida v7 | Original v4 |
|---|---|---|---|
| MuMu emulator, profile | **2.5%** | 11.1% | ~367% (spot) |
| Infinix X6873, profile | **13.6%** | ~31.9%+ | 242% |

The Android 16 phone at login measured ~28% while IG's Voltron/login background work was active.
The old Frida build's arm64 JS bridge was the dominant cost; LSPatch removes it.

## Install

1. Download `Instagram_Mod_LSPatch_v452.0.0.48.64-r3.apk` from
   [Releases](https://github.com/ady133t/Insta-Mods/releases/latest).
2. Prefer a **burner Instagram account**; IG/LSPatch may be detectable.
3. From the Play Store build, **uninstall Instagram first** (signature mismatch).
   This deletes login and app data.
4. Install the APK:
   `adb install -r -d Instagram_Mod_LSPatch_v452.0.0.48.64-r3.apk`
5. Log in.
6. Disable Play Store auto-update for Instagram.
7. Accept Play Protect / unknown-source prompts if shown.

Updating from a previous **debug-signed** mod can use `install -r` and keep data.
The current test phone is already running this build.

## How it works

```
Instagram process
  └─ LSPatch loader (embedded)
       ├─ loads original IG from assets/lspatch/origin.apk
       └─ loads embedded module assets/lspatch/modules/com.dl.igmod.apk
            └─ com.dl.igmod.Module (IXposedHookLoadPackage)
                 └─ Java/Xposed hooks into IG and Android views
```

The Instagram base is a **clean apktool rebuild** (no Frida/gadget). LSPatch repackages it,
adds its loader, embeds the module, and rewrites the app component factory. All mod logic
is custom Java in the module.

Key hooks:

| Purpose | Hook |
|---|---|
| Preferences | `InstagramAppShell.onCreate(Context)` |
| Seen suppression | `X.00OH.A03(…Map, long)` |
| Profile button | `TextView.setText("Edit profile")` |
| Menu detection | `TextView.setText("Not interested"/"AI info"/"Report")` |
| Media tracking | `MediaExtKt.A0F(Media)` |
| Story lifecycle | `ReelViewerFragment.onResume/onPause` |

## Known limitations

- LSPatch changes the app's loading model and **targetSdk is lowered to 35** for compatibility.
- LSPatch/IG anti-tamper may detect the patched app. Use at your own risk.
- A Play Store update replaces the mod; keep auto-update off.
- Anonymous suppression applies to `X.00OH.A03` whenever the toggle is ON (this is the story
  seen-report path found in testing).
- First launch after install can be slower while LSPatch extracts the origin APK and module.
- Debug signature means installing over the Play Store app is impossible without uninstall.
- If the app still feels heavy under load, the remaining cost is LSPatch's Java hook framework;
  a pure smali port (no hook framework) would be the next step.

## Workspace layout / rebuild

Local build workspace (not every large item is committed to GitHub):

```
workspace\
├─ lspatch\
│  ├─ Instagram_Mod_LSPatch_v452.0.0.48.64-r3.apk   final patched APK (release asset)
│  ├─ module.apk                                 signed Java module APK
│  ├─ base_clean.apk                             clean IG base used for patching (not committed)
│  ├─ lspatch-v1.2-487-release.jar               patcher
│  └─ source\
│     ├─ AndroidManifest.xml
│     ├─ assets\xposed_init
│     ├─ stubs\de\robv\android\xposed\...         compile-time Xposed stubs
│     ├─ src\com\dl\igmod\Module.java            hooks, download, settings UI
│     └─ src\com\dl\igmod\GhostOverlay.java      story ghost badge + media anchoring
├─ perm_test_decoded\                            clean apktool tree (not committed)
└─ instagram_merged.apk                          stock merged base (not committed)
```

The public repository includes:
`Readme.md`, `Skills.md`, `Findings.md`, `lspatch\source\`, `lspatch\module.apk`,
and `lspatch\lspatch-v1.2-487-release.jar`.
Large files (`base_clean.apk`, `perm_test_decoded\`, `instagram_merged.apk`) are intentionally
excluded; the final APK is the GitHub release asset.

Rebuild outline:

```text
1. javac Module.java against android.jar + stubs.jar
2. d8 module classes → classes.dex
3. aapt2 link manifest/assets + classes.dex → module.apk; sign
4. java -jar lspatch-v1.2-487-release.jar base_clean.apk ^
      -m module.apk -o out -f --injectdex --target-sdk 35 -l 2 ^
      -k <debug.keystore> android androiddebugkey android
5. install the LSPatch output (debug signature)
```

See `Skills.md` for the full process and `Findings.md` for the IG-specific details.
