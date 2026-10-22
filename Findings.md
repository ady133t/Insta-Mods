# Findings — Instagram 452.0.0.48.64 Patch Reference

Everything required to reproduce the LSPatch Java-module features in this project.
Companion docs: [`Readme.md`](Readme.md) (artifact/install) and [`Skills.md`](Skills.md) (methodology).

---

## 1. Target and artifact

| Item | Value |
|---|---|
| Package | `com.instagram.android` |
| Version | 452.0.0.48.64 |
| versionCode | 385810270 |
| compileSdk | 37 |
| Final targetSdk | 35 (LSPatch override) |
| ABI | arm64-v8a (MuMu runs it under arm64 translation) |
| Base | clean apktool rebuild, no Frida/gadget |
| Patch framework | JingMatrix LSPatch v1.2 (487) |
| Module | `com.dl.igmod` |
| Final APK | `lspatch\Instagram_Mod_LSPatch_v452.0.0.48.64.apk` |
| Size | 165,366,712 bytes |
| SHA-256 | `DBABBFDD99EEA0CBFA7C7E819F2457629F44DAA145E4024EBE3061F8C6C93953` |
| Signer | Android debug key (`fd55cd8d6d8b8065bd2e398af56f3d5b167269f4a71d36843900f801fb836b97`) |

Test devices:

- MuMu emulator `127.0.0.1:7555` — x86_64 host, IG arm64-v8a, used x86_64 LSPatch loader.
- Infinix X6873 — Android 16, arm64-v8a, physical phone (wireless ADB).
- Phone resolution 1224×2720; screenshots in tools may be scaled — scale tap coordinates accordingly.

---

## 2. APK / class-loading structure

### 2.1 Runtime class loader

Probe result on the installed app:

```
X.00OH loader = dalvik.system.PathClassLoader
  DexPathList[[zip file "/data/app/.../base.apk"], nativeLibraryDirectories=[.../lib/arm64, ...]]
  methods = 11: A00..A09 (A03 present)
X.0EBd methods = 10: A00..A04, bindView, buildRowViewTypes, createView, getIdentifier, getViewTypeCount
X.0I1V methods = 6: A00, A01, A0b, A0c, getItemCount, getItemViewType
```

All classes are loaded from `base.apk`, not from a separate dextricks jar. `secondary-program-dex-jars`
contained only `dex_manifest.txt`/`metadata.txt` in the decoded tree; `private` module dexes can still
be extracted at runtime (e.g. `modules/pytorch.../prog-*.dex`).

### 2.2 Duplicate class copies / Windows case collisions

Multiple smali files exist for the same class name across `smali*` directories. The real loaded copy
was the large implementation:

- `X/00OH`: `smali_classes2` (28,406 bytes; has A03). A 366-byte stub exists in `smali/`.
- `X/0EBd`: `smali_classes6` (355,046 bytes; has bindView). Small copies exist in classes5/15/16/17.
- `X/00EU`: real copy in `smali_classes2` (59,508 bytes).
- `X/0I1V`: single copy in `smali_classes14`.

Obfuscated names also differ only by case (`X/04B8` vs `X/04b8`, `X/0QQR` vs `X/0QQr`). Windows is
case-insensitive, so one file overwrites the other during apktool decode. This makes **full smali
rebuilds risky** and was one reason the final route is LSPatch Java hooks rather than smali edits.
Dex-level patching (dexlib2) would avoid the collision.

### 2.3 Menu vs progress adapter

`X.0I1V` is the **overflow-menu RecyclerView adapter**:

```
.class public final LX/0I1V;
.super LX/04b8;
fields:
  A00:LX/0Wqi;      // view binding/callback helper
  A01:LX/0QQR;      // enum
  A02:Z
  A03:Ljava/util/List;   // menu items
  A04:Z
methods: constructor, A00, A01, A0b (create view), A0c (bind), getItemCount, getItemViewType
```

Menu item class: `X.0IuY` (label in `A09:String`, icon ints/floats, `A07:X.0Iwf`, flags).
Runtime dump showed items such as `"Interested"`, `"Not interested"`, `"Report"`.
The adapter holds **no Media object** — only labels/icons. Do not try to obtain the post Media from it.

---

## 3. LSPatch findings

### 3.1 Working configuration

```bat
java -jar lspatch-v1.2-487-release.jar base_clean.apk ^
  -m module.apk -o out -f ^
  --injectdex --target-sdk 35 -l 2 ^
  -k debug.keystore android androiddebugkey android
```

- `--injectdex` is mandatory for this IG/LSPatch/Android combination.
- Output manifest targetSdk becomes 35.
- Output is signed with the debug key, so `adb install -r` works over previous debug-signed mods.
- Final APK structure:
  - `assets/lspatch/origin.apk` (clean IG)
  - `assets/lspatch/loader.dex`, `assets/lspatch/so/*/liblspatch.so`
  - `assets/lspatch/modules/com.dl.igmod.apk`
  - `classes24.dex` loader stub

### 3.2 Failure modes observed

- **Default local mode / stock merged base** → `VerifyError` in `com.facebook.litho.BaseMountingView`:

  ```
  'this' argument 'Reference: android.view.ViewGroupOverlay'
  not instance of 'Unresolved Reference: android.view.ViewOverlay'
  ```

  `--injectdex` on the clean apktool base resolved it; default mode did not.

- **LSPatch + Frida gadget** in the same APK → native startup crash in `libandroid_runtime` /
  `AndroidRuntime::start`. Do not combine them.

- **Older module API signature**:

  ```
  AbstractMethodError: void de.robv.android.xposed.IXposedHookLoadPackage
    .handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam)
  ```

  The entry class must implement `handleLoadPackage(XC_LoadPackage.LoadPackageParam)`, not the
  legacy `IXposedHookLoadPackage.LoadPackageParam`.

- **Updating the embedded module with jar/7-Zip**:
  - `jar uf` rewrote the APK and dropped ~17k resource entries (“code is missing”).
  - 7-Zip update left the old module first and inflated the APK to ~324 MB.
  - Correct method: **re-run LSPatch with the new module** each time.

- LSPatch logs include `FbVoltronModuleLoader` errors on the phone because the app sees the extracted
  origin path instead of `/data/app/.../base.apk`; these were non-fatal and the app ran.

---

## 4. Anonymous story view (seen suppression)

### 4.1 Target method

Real class: `smali_classes2/X/00OH.smali`.

```
.method public static final A03(LX/00AP;Ljava/lang/String;Ljava/lang/String;
        Ljava/lang/String;Ljava/util/Map;J)V
    .locals 10
    ...
```

- Runtime methods: A00–A09; A03 is the seen-report path.
- Hook via:

```java
Class<?> oh = XposedHelpers.findClass("X.00OH", cl);
Class<?> ap = XposedHelpers.findClass("X.00AP", cl);
XposedHelpers.findAndHookMethod(oh, "A03", ap, String.class, String.class,
    String.class, Map.class, long.class, new XC_MethodHook() {
  @Override protected void beforeHookedMethod(MethodHookParam p) {
    if (Prefs.anon) p.setResult(null);   // skip original void method
    else /* forward */;
  }
});
```

- `setResult(null)` skips the original for a void method in Xposed.
- Initial implementation was gated by `Story.active`, set by
  `instagram.features.stories.fragment.ReelViewerFragment.onResume/onPause`.
  On the arm64 phone the flag did not become true reliably (no suppression), while the same code
  worked on MuMu. Final fix: **do not gate on Story.active**; suppress whenever `Prefs.anon` is ON.
  The A03 path is the story seen reporter.
- Verified logs: `seen suppressed` on both MuMu and phone when ON; no suppression when OFF.

---

## 5. Profile Mod Setting button

### 5.1 Runtime stack (when "Edit profile" is set)

```
android.widget.TextView.setText
com.instagram.igds.components.button.IgdsButton.setText
X.0FjS.A01
X.0FeR.A01
X.0EBd.bindView
X.05Jm.getView
X.01sE.A02
...
X.0Eoi.onChanged
...
com.instagram.profile.fragment.UserDetailTabController.A0Q
com.instagram.profile.fragment.UserDetailFragment.A03
```

View chain at the moment of setText:

```
TextView (internal)
  parent: com.instagram.igds.components.button.IgdsButton  id=2131429426
    parent: androidx.constraintlayout.widget.ConstraintLayout
```

### 5.2 Injection

Hook `TextView.setText` (`CharSequence` and `int` overloads). On `"Edit profile"`:

1. Retain the anchor `TextView`.
2. Post a runnable ~300 ms after layout.
3. Walk up to an ancestor `IgLinearLayout` that has a child class containing
   `FollowChainingButton`.
4. Use that ancestor's parent (`LinearLayout`) and insert a holder after the row.
5. Guard with tag `MOD_SETTING_BTN` (both holder and button).
6. Button: purple `GradientDrawable` `#7B2FF7`, white 14sp text, centered, rounded 20dp,
   padding 32/10 dp.
7. Click shows a native `AlertDialog` with two clickable `TextView` rows:
   - Anonymous story view: ON/OFF
   - Download option: ON/OFF

The header sometimes rebuilds; the setText hook re-fires and the tag check avoids duplicates.
Logs on failure: `profile row not found`; on success: `profile button added`.

The button appeared correctly on MuMu and the phone (purple pill below Edit/Share).

---

## 6. Overflow menu Download row

### 6.1 Runtime stack (menu row text)

```
android.widget.TextView.setText
X.0I1V.A0c
X.04b8.A0V
X.04b8.A0R
X.00EU.A02
X.00EU.A06
X.00EU.A04
X.00YW.A00
androidx.recyclerview.widget.LinearLayoutManager.layoutChunk
...
```

The menu adapter is `X.0I1V`; row labels are set through `TextView.setText`.
Rows are bound by `LinearLayoutManager.layoutChunk`; the row view is **detached during bind**, so
immediate ancestor scans fail. A delayed runnable (250–300 ms) is required.

### 6.2 Injection

Hook `TextView.setText` for:

- `"Not interested"`
- `"AI info"`
- `"Report"`

Throttle one injection per ~1.2 s, then after 300 ms:

1. From the anchor TextView, climb up to 15 parents to a `RecyclerView`.
2. Take `rv.getParent()`; in observed menus it is a `LinearLayout`.
3. Check the parent children for tag `DL_MENU_ROW`.
4. Create `TextView("Download")`, white 16sp, padding 32/24 dp, tag `DL_MENU_ROW`.
5. Add at `parent.indexOfChild(rv)` with `LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)`.
6. `row.bringToFront()`.
7. Post a `TouchDelegate` on the parent using the row's hit rect. This is required because the
   RecyclerView can visually overlap the injected row and swallow taps.
8. Click → downloader.

Observed row positions varied with menu contents:
- post menu with “Why you're seeing this”: Download around y≈958 (900x2000 screenshot coords)
- post menu without it: y≈1062
- story menu: Download at the bottom, y≈1370–1418

Gating: if `Prefs.download` is false, `MenuInjector.inject` returns before adding anything.
Verified: toggling Download OFF hides the Download row; ON shows it.

---

## 7. Exact media resolution for downloads

### 7.1 Failed heuristics

Using the most recent Media captured from `MediaExtKt.A0F` at click time is unreliable due to
prefetch. Observed wrong downloads:

- Visible CNBC image → downloaded evolving.ai/NVIDIA post image
  (`4011336997200261359_58712495227`).
- Visible yahoofinance post → downloaded an unrelated vertical video
  (`3968081797214211531_49993700677`, “We Murdered These”).

The menu adapter and its `X.0IuY` items contain only labels/icons, not Media.
The sheet fragment found at menu time (`X.00lt`) had no Media or media argument.
`frag` search returned nothing.

### 7.2 Working resolver

At menu open, use the visible post/slide behind the bottom sheet:

1. Anchor root = `anchor.getRootView()`.
2. Hit-test at `(root.width/2, root.height/3)` in screen coordinates. This is above the sheet and
   lands on the visible post/story.
3. `hitTest` recursively checks `getGlobalVisibleRect`/`isShown` and returns the deepest view under
   the point.
4. Walk the hit view and its ancestors:
   - scan fields for an instance of `com.instagram.feed.media.Media`;
   - if a `RecyclerView` ancestor is found, use `findChildViewUnder` →
     `getChildViewHolder` and scan the holder/item view fields.
5. `findListenerMedia`:
   - climb from the hit view to the feed item root (until the parent is a RecyclerView);
   - BFS the item's view tree (≤600 views);
   - reflect `android.view.View.mListenerInfo`;
   - for each field whose name contains `listener`, run a bounded object-graph search for a Media;
   - also search `View.getTag()`.
6. The **overflow-button click listener** held the exact Media for the visible post.
   Example found: `4013583233155739720_66535610953` (entrepreneursonig Jensen Huang video).
7. Fallback order: listener/tag media → ancestor/holder media → menu-time snapshot →
   `MediaTracker` pick.

Bounded graph search filters classes to `X.*`, `com.instagram.*`, `com.facebook.litho.*`, and
`java.util.List`, with a visited set and object cap to keep menu-open cost acceptable.

### 7.3 URL resolution

- ID: `Media.getId()`.
- **Video**: `Media.AB6()` returns a list of video versions. Choose the entry with the highest
  `getWidth()`; read `getUrl()`.
  - Example URL host: `instagram.fkul16-2.fna.fbcdn.net/o1/v/t2/...mp4`.
  - Downloaded Jensen Huang MP4 size 3,440,118 bytes; ffmpeg frame matched the visible post.
- **Image**: `MediaExtKt.A0g(media)` → info object → `BgD()` list of image candidates.
  Choose the highest `getWidth()` and `getUrl()`.
  - Phone Monet carousel download: 1080 px candidate, file 232,618 bytes; visually matched slide 1.
  - Story Q&A image: 720 px candidate, file 136,329 bytes; matched the visible slide.
  - Fallback `MediaExtKt.A0F(media).getUrl()` often returns a 320 px thumbnail; prefer A0g.
- Runtime Media implementation is obfuscated (e.g. `X.05ra`), so filter hook args with
  `Media.class.isInstance(arg)` — not a class-name check.

### 7.4 Media tracking hook

```java
MEDIA_CLS = XposedHelpers.findClass("com.instagram.feed.media.Media", cl);
MEDIA_EXT = XposedHelpers.findClass("com.instagram.feed.media.MediaExtKt", cl);
XposedBridge.hookAllMethods(MEDIA_EXT, "A0F", new XC_MethodHook() {
  after: if (MEDIA_CLS.isInstance(arg0)) MediaTracker.add(arg0);
});
```

Tracker stores Media, timestamp, main-thread flag; bounded to 80; used only as a fallback.

### 7.5 Download

```java
DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
req.setTitle(name);
req.setNotificationVisibility(VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
((DownloadManager) ctx.getSystemService(DOWNLOAD_SERVICE)).enqueue(req);
```

- Filename: `ig_<sanitized mediaId>.jpg`/`.mp4`.
- Async worker thread; Toast on the main thread; in-flight set prevents duplicate IDs.
- Downloads appeared in `/sdcard/Download` on both devices.

---

## 8. Story-specific findings

- Viewer fragment: `instagram.features.stories.fragment.ReelViewerFragment`.
  It overrides `onResume()V`, `onPause()V`, `onDestroyView()V`, `onStart()V`, `onStop()V`,
  `onViewCreated(...)`.
- Lifecycle hooks were installed and logged `story hook`, but the `Story.active` flag was not
  reliable on the arm64 phone for the seen hook. Final stealth uses `Prefs.anon` only.
- Owner (if needed) comes from view content descriptions containing `"'s story"`; slide index from
  `SegmentedProgressBar.getCurrentSegment()/getSegments()`.
- For downloads, the exact visible slide is resolved by the same hit-test/listener path; the current
  slide's media ID is selected. Verified:
  - houseofhighlights Q&A slide → `4013199898818484516_1254997058`, 720 px JPG, matched on-screen.
- The story menu's action rows are in a `IgLinearLayout`/`ConstraintLayout` inside a RecyclerView;
  Download insertion and TouchDelegate work the same as post menus.

---

## 9. Settings / persistence

`SharedPreferences("igmod")`:

| Key | Default | Effect |
|---|---|---|
| `anon_story` | `true` | suppress `X.00OH.A03` seen reporting |
| `download_enabled` | `true` | show/inject Download row |

Initialized by hooking `com.instagram.app.InstagramAppShell.onCreate(Context)` and calling
`Prefs.init((Context) param.thisObject)` after the original.

On disk (rooted emulator check):

```xml
<map>
  <boolean name="anon_story" value="true" />
  <boolean name="download_enabled" value="true" />
</map>
```

Toggles update both in-memory statics and SharedPreferences immediately.

---

## 10. Performance findings

All figures are 30-second `/proc/<pid>/stat` CPU-time averages on a static, screen-on screen,
unless noted. Spot `top` values can be wildly misleading during feed video/startup.

| Build | Device/screen | Avg CPU |
|---|---|---|
| LSPatch Java final | MuMu, profile | **2.5%** |
| LSPatch Java final | Infinix phone, profile | **13.6%** |
| LSPatch Java final | Infinix phone, login/startup | ~28% (IG Voltron/login background) |
| Frida v7 | MuMu, profile | 11.1% |
| Frida v7 | Infinix phone, login | 31.9% |
| Frida v4 | Infinix phone, login | 242% (spot; pre-optimization) |
| No-op gadget | MuMu, profile | 12.5% (measurement noise) |
| Bridge-only | MuMu, profile | ~7.8% |
| No-op gadget | Infinix phone, login | 9.4% |

Why Frida was slow on the phone:

- arm64 Frida Java bridge + JS callback per hooked call.
- Hot hooks (`TextView.setText`, Media bind methods, periodic `Java.choose` scans) forced ART
  instrumentation on a budget CPU.
- The emulator has a desktop CPU and an x86_64 gadget/bridge, hiding the cost.

Frida v7 improvements that still apply conceptually:

- removed 700 ms/1.2 s/1.5 s `Java.choose` heap scans;
- removed global `Activity.dispatchTouchEvent`;
- removed legacy dialog/Toast guards and Media-constructor hooks;
- dynamic seen hook only during stories.

LSPatch Java hooks removed the JS bridge entirely and dropped phone profile CPU to ~13.6%.
Feed video playback still drives CPU high (native IG behavior), e.g. ~31% while a video feed loads.

---

## 11. Feature verification evidence

- **Profile UI**: purple Mod Setting pill appeared below Edit/Share on MuMu and phone;
  dialog toggles updated/persisted (`shared_prefs/igmod.xml`).
- **Menu row**: `[DLM] menu row added at ...`; Download visible; toggling Download OFF hides it.
- **Feed download (MuMu)**: visible entrepreneursonig Jensen Huang post → media
  `4013583233155739720_66535610953`, MP4, ffmpeg first frame matched the on-screen video.
- **Story download (MuMu)**: visible houseofhighlights Q&A slide → media
  `4013199898818484516_1254997058`, 720 px JPG, image matched the on-screen slide.
- **Feed download (phone)**: visible Monet carousel slide → 1080 px JPG (232,618 bytes), matched.
- **Stealth**: MuMu and phone logged `seen suppressed` with anonymous ON; no suppression with OFF.
- **Gating**: Download option OFF removed the row; ON restored it.

---

## 12. Known limitations / notes

- Final targetSdk is 35 due to LSPatch; app runs on Android 15/16 test devices.
- LSPatch's loader changes APK structure and execution; anti-tamper detection risk remains.
- The final APK is debug-signed; installing over Play Store requires uninstall/re-login.
- Play Store updates overwrite the mod.
- Anonymous mode suppresses A03 whenever ON (the tested seen-report path), not only while a
  `ReelViewerFragment` is resumed.
- The menu-open media resolver does a bounded reflection scan; it is intentionally limited but
  still more expensive than a direct model hook.
- Native smali patching is complicated on Windows by case-only class collisions; dex-level editing
  (dexlib2) is the safer native route if ever needed.
- `jar uf`/7-Zip must not be used to swap the embedded module; repatch with LSPatch.
- Final artifact hash: `DBABBFDD99EEA0CBFA7C7E819F2457629F44DAA145E4024EBE3061F8C6C93953`.
