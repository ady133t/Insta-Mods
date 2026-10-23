# Skills — Analyzing and Patching Instagram with an LSPatch Java Module

This is the repeatable methodology used to build the non-root Instagram mod.
It covers static analysis, runtime discovery, LSPatch module packaging, hook patterns,
verification, and performance measurement.

Exact class/method findings are in [`Findings.md`](Findings.md);
the final artifact is described in [`Readme.md`](Readme.md).

---

## 1. Principles

1. **Do not assume smali is the loaded code.** IG obfuscates, duplicates and case-collides class
   names across dex files. Verify what the runtime actually loaded.
2. **Prefer runtime evidence over guesses.** Use Frida (or a probe module) to capture Java stacks,
   class loaders, view hierarchies and field values before writing a hook.
3. **Hook IG-specific methods where possible.** Framework hooks (`TextView.setText`, touch events)
   are hot; use them only for discovery or one-shot injection.
4. **Keep work off the UI thread when expensive.** Use `Handler`/worker threads; post UI work back.
5. **Verify behavior with content, not just logs.** Pull the downloaded file and compare it to the
   on-screen post/slide (e.g. ffmpeg still frame).
6. **Measure on the target architecture.** x86_64 emulator results do not predict arm64 phone cost;
   Xposed/Frida hook overhead differs by architecture and ART build.

---

## 2. Toolchain

- ADB + Android platform tools
- `apktool.jar` (decode/rebuild)
- Android build-tools: `aapt2`, `d8`, `zipalign`, `apksigner`
- JDK (javac)
- **JingMatrix LSPatch v1.2** CLI (`lspatch-v1.2-487-release.jar`)
- Frida + gadget (diagnostics only)
- Python (zip/patch helpers, resource parsing)
- ffmpeg (verify downloaded video frames)
- 7-Zip (inspect APKs; do not use it to update LSPatch module entries — it duplicates/bloats)

For the Java module, compile-time Xposed stubs are needed. The module is compiled against
`android.jar` plus a small stub jar; the stubs are **not shipped** in the module APK.

---

## 3. Establish the target

```bat
adb devices -l
adb -s <serial> shell getprop ro.product.model
adb -s <serial> shell getprop ro.build.version.release
adb -s <serial> shell getprop ro.product.cpu.abilist
adb -s <serial> shell pm path com.instagram.android
adb -s <serial> shell dumpsys package com.instagram.android | findstr /i "versionName primaryCpuAbi"
```

Notes from this project:

- MuMu emulator: `127.0.0.1:7555`, x86_64 host, app runs arm64-v8a via translation.
- Real phone: Infinix X6873, Android 16, arm64-v8a.
- A newly connected wireless-debug device appears as an mDNS-style serial, e.g.
  `adb-<id>-<random>._adb-tls-connect._tcp`. Use it verbatim with `-s`.

For UI automation, always obtain the **real** resolution:

```bat
adb shell wm size        REM e.g. Physical size: 1224x2720
adb shell wm density
```

Screenshots may be displayed scaled; scale all tap coordinates by
`real_width / displayed_width`.

---

## 4. Static analysis workflow

### 4.1 Inspect the APK/manifest

```bat
aapt2 dump badging app.apk
aapt2 dump xmltree app.apk --file AndroidManifest.xml
```

Check: package/version, targetSdk, `extractNativeLibs`, split names, app component factory.

### 4.2 Decode

```bat
java -jar apktool.jar d -f -o decoded app.apk
```

For a split-installed app, obtain the base + splits (`pm path`) and merge them, or work from a
merged APK. A clean apktool rebuild was used as the LSPatch base.

### 4.3 Search UI text correctly

IG UI strings often live in GraphQL string catalogs under `assets/strings/*.frsc`,
**not** in `res/values/strings.xml`. Searching `res` for "Edit profile" can return nothing.
Use runtime evidence instead of assuming a resource ID.

### 4.4 Beware Windows case collisions

Obfuscated names can differ only by case (`X/04B8` vs `X/04b8`). On a case-insensitive filesystem,
apktool's smali tree cannot represent both; a full smali rebuild may silently lose a class.
If doing native/smali work, patch **dex-level** with dexlib2 or work on a case-sensitive FS.
For LSPatch Java hooks this does not matter, because we do not rebuild IG's classes.

### 4.5 Find real class copies

A class name may have several smali copies in different dexes:

```bat
for each smali* dir: test X\<name>.smali
```

Check size and method list. The loaded copy is usually the large implementation, e.g.:

- `X/00OH`: real copy in `smali_classes2` (has `A03`)
- `X/0EBd`: real copy in `smali_classes6` (has `bindView`)
- `X/0I1V`: `smali_classes14`

Verify at runtime with a class-loader probe before relying on a copy.

---

## 5. Runtime discovery (Frida / probe module)

Use Frida only during analysis; the final mod does not use it.

### 5.1 Class loader

IG may load classes through a custom loader. Set the bridge factory loader before `Java.use` of app
classes:

```js
Java.perform(function () {
  var appLoader = null;
  Java.enumerateClassLoaders({
    onMatch: function (l) {
      if (appLoader) return;
      try { if (l.loadClass('X.00OH')) appLoader = l; } catch (e) {}
    },
    onComplete: function () {}
  });
  if (appLoader) Java.classFactory.loader = appLoader;
});
```

Log `Java.classFactory.loader`, `Class.getClassLoader()` and declared methods to confirm the real
copy. IG's runtime classes were loaded from `base.apk` via `dalvik.system.PathClassLoader`.

### 5.2 Capture the Java stack for UI text

Hook `TextView.setText` and, when the target text appears, log:

- `this.getClass().getName()`
- the parent chain (classes, IDs)
- `Log.getStackTraceString(new Throwable())`

This produced the exact IG binder methods for "Edit profile" and menu rows.

Example profile stack:

```
TextView.setText
IgdsButton.setText
X.0FjS.A01
X.0FeR.A01
X.0EBd.bindView
X.05Jm.getView
...
```

### 5.3 Dump view/adapter fields

For a menu/sheet:

- find the `RecyclerView` ancestor from the row
- log the adapter class and its declared fields (class + value class; list sizes)
- dump item classes/fields

Important: menu rows are often **not attached during bind**, so ancestor scanning immediately in
`setText` fails. Post a delayed runnable (~250–300 ms) before looking for the RecyclerView.

The menu adapter (`X.0I1V`) held only labels/icons (`X.0IuY` items), not Media.

### 5.4 Find the real Media

The reliable association was the **visible post behind the bottom sheet**:

1. Hit-test the decor view at `(width/2, height/3)` (above the sheet top).
2. Find the visible feed item's root and visit its view tree.
3. Reflect `View.mListenerInfo` and scan every `*Listener` field, plus `View.getTag()`.
4. Run a bounded object-graph search for an instance assignable to
   `com.instagram.feed.media.Media`.
5. The overflow-button click listener held the exact Media for the visible post/slide.

This was verified against the on-screen video/image content.

---

## 6. Build the LSPatch Java module

### 6.1 Module entry

```java
package com.dl.igmod;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Module implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"com.instagram.android".equals(lp.packageName)) return;
        if (lp.processName != null && !lp.processName.equals(lp.packageName)) return;
        // install hooks
    }
}
```

**API signature matters.** The method must take
`de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam`.
Using an older nested `IXposedHookLoadPackage.LoadPackageParam` compiles but throws
`AbstractMethodError` at runtime.

### 6.2 Module manifest

```xml
<application android:label="DLM">
  <meta-data android:name="xposedmodule" android:value="true" />
  <meta-data android:name="xposeddescription" android:value="DLM" />
  <meta-data android:name="xposedminversion" android:value="93" />
</application>
```

`assets/xposed_init` contains the entry class:

```
com.dl.igmod.Module
```

### 6.3 Compile/package

Compile against `android.jar` + compile-only Xposed stubs, then d8 only the module classes
(never ship the stubs):

```bat
javac --release 8 -cp android.jar;stubs.jar -d module_classes src\com\dl\igmod\Module.java
d8 --release --lib android.jar --lib stubs.jar --output dex module_classes\com\dl\igmod\*.class
aapt2 link -o module_base.apk -I android.jar --manifest AndroidManifest.xml -A assets ^
  --min-sdk-version 28 --target-sdk-version 35
REM append dex\classes.dex to module_base.apk
zipalign -f -p 4 module.apk module_aligned.apk
apksigner sign ... --out module_signed.apk module_aligned.apk
```

### 6.4 Build the clean base

For LSPatch, use a **clean, non-Frida** Instagram APK. In this project:

- `perm_test_decoded` was an apktool tree.
- The Frida loader call, `assets/igmod.js` and gadget `.so`/configs were removed.
- `apktool b` + zipalign + debug signing → `base_clean.apk`.

Do not patch an APK that already contains Frida; LSPatch + Frida crashed natively at startup.

### 6.5 LSPatch patch

```bat
java -jar lspatch-v1.2-487-release.jar base_clean.apk ^
  -m module_signed.apk ^
  -o out -f ^
  --injectdex --target-sdk 35 -l 2 ^
  -k %USERPROFILE%\.android\debug.keystore android androiddebugkey android
```

Flags used and why:

- `--injectdex` — required on this IG/LSPatch/Android combination.
- `--target-sdk 35` — compatibility target; final manifest targetSdk becomes 35.
- `-l 2` — signature bypass level 2.
- `-k ...` — sign with the same debug key so `install -r` preserves data over previous debug builds.

Output APK structure:

```
assets/lspatch/origin.apk                     clean original IG
assets/lspatch/loader.dex                     LSPatch loader
assets/lspatch/modules/com.dl.igmod.apk       embedded module
assets/lspatch/so/<abi>/liblspatch.so
classes24.dex                                 loader stub dex
```

---

## 7. LSPatch troubleshooting

### VerifyError in Litho

Symptom (default local mode, stock merged APK or plain apktool base):

```
VerifyError: Rejecting class com.facebook.litho.LithoView ...
BaseMountingView.A06: ViewGroupOverlay is not instance of Unresolved ViewOverlay
```

- Caused by LSPatch's extracted-origin classloader on this IG/Android combination.
- `--injectdex` on the clean apktool base resolved it.
- Do not combine LSPatch with a Frida gadget (native startup crash).

### Module not loading

Check:

```bat
adb logcat -d | grep -E "LSPatch|DLM|Vector|AbstractMethodError"
```

- Ensure `assets/xposed_init` exists inside the module APK.
- Ensure `IXposedHookLoadPackage.handleLoadPackage(XC_LoadPackage.LoadPackageParam)`.
- Ensure the module APK is signed and embedded.

### Updating the embedded module

Do **not** use `jar uf` (it dropped 17k APK entries) or plain 7-Zip update (duplicated entries,
grew APK to ~324 MB, and LSPatch loaded the old module).
Repatch the clean base with the new module APK each time.

### APK entry counts

The LSPatch APK is intentionally unusual: the original IG is nested in
`assets/lspatch/origin.apk`, so only a small number of top-level entries exist outside that jar.

---

## 8. Hook implementation patterns

### 8.1 Preferences

Hook `com.instagram.app.InstagramAppShell.onCreate` after the original, passing the Application
(`param.thisObject`) to a `Prefs.init(Context)` helper. This guarantees prefs are loaded early
without depending on hidden `ActivityThread` APIs.

Defaults: `anon_story=true`, `download_enabled=true`.

### 8.2 Early-return a void method

For `X.00OH.A03(…, Map, long)V`:

```java
XposedHelpers.findAndHookMethod(oh, "A03", ap, String.class, String.class,
    String.class, Map.class, long.class, new XC_MethodHook() {
  protected void beforeHookedMethod(MethodHookParam p) {
    if (Prefs.anon) {
      p.setResult(null);   // skips the original void method
    }
  }
});
```

Initially this was gated by a `ReelViewerFragment.onResume` flag. On the arm64 phone the flag was
not being set reliably, so stealth was changed to depend only on the anonymous preference.
The A03 hook itself is the story seen-report path.

### 8.3 Inject a profile button

Hook `TextView.setText`; when the string is `"Edit profile"`:

1. retain the `TextView` anchor;
2. post a delayed runnable (~300 ms);
3. walk up to the header container (look for an `IgLinearLayout` containing a
   `FollowChainingButton` child);
4. check the parent for the `MOD_SETTING_BTN` tag;
5. build a `TextView` button + `FrameLayout` holder;
6. insert below the Edit/Share row;
7. click → native `AlertDialog` settings.

Avoid `Java.choose` loops; the one-shot setText trigger is cheap.

### 8.3b Story ghost badge overlay

When anonymous view is ON, add a small semi-transparent badge to the story viewer:

1. Hook `ReelViewerFragment.onResume`; get the fragment root via `getView()`.
2. Add a custom `GhostDrawable` to `root.getOverlay()` (a `ViewOverlay` drawable), so it does not
   affect layout or intercept touches.
3. Find the largest visible story-content frame under the root whose bottom is at least ~40 dp
   above the root bottom (phone: `RoundedCornerFrameLayout` `[0,211][1224,2387]`). Do **not**
   anchor to the fragment root or `ReboundViewPager`: both extend behind the reply bar, and a fixed
   `96 dp` bottom margin is wrong across densities/layouts.
4. Cache that frame's measured visible rect, refresh it in `OnLayoutChangeListener`, and place the
   badge 36 dp with 16 dp insets from its right and bottom edges. If small Like/Share `ImageView`s
   overlap the corner (MuMu), use their top edge as the bottom bound.
5. Draw a white outlined ghost (wavy bottom, eyes, mouth) with stroke alpha ~150 and a subtle dark
   backing stroke for contrast.
6. Remove the drawable/listener in `onPause` and when anonymous is switched OFF.

Verified on the phone: 16 dp above the story image bottom, clear of the reply bar. Verified on the
emulator: visible with anonymous ON and lifted above the action icons; absent with OFF.

### 8.4 Insert a menu row

Hook `TextView.setText` for `"Not interested"`, `"AI info"`, `"Report"`.
Throttle (one injection per ~1.2 s), then after ~300 ms:

1. from the anchor, climb to the menu `RecyclerView`;
2. climb from the RecyclerView up to 5 parents to the first **vertical** `LinearLayout`;
3. check for the `DL_MENU_ROW` tag;
4. create a rounded green `TextView("Download")`: `GradientDrawable #1ED760`, corner 24 dp,
   white bold 16 sp centered, height 48 dp, side margins 24 dp, elevation 4 dp;
5. add it at **index 0** of that LinearLayout to pin it to the top; do not call `bringToFront()`;
6. add a `TouchDelegate` from the button's hit rect on the parent so taps reach it even when the
   RecyclerView overlaps the row;
7. click -> downloader.

The top button was verified on the emulator in r3.

### 8.5 Exact Media for the visible post/slide

Do not use "last captured media" heuristics; prefetch makes them wrong.

At menu-open:

1. hit-test the decor at `(width/2, height/3)` (the post/story area above the sheet);
2. walk the hit view/ancestors, scanning fields for a `Media`;
3. if needed, reflect `View.mListenerInfo` and scan listener fields — the post overflow-button
   listener holds the exact Media;
4. also search the hit view's tags.

Fallback: `MediaTracker` (from an `A0F` hook) and a menu-time snapshot.

### 8.6 Resolve URLs and download

- Videos: `Media.AB6()` returns a list of video versions; choose the highest `getWidth()`,
  then `getUrl()`.
- Images: `MediaExtKt.A0g(media)` → `BgD()` list of image candidates; choose highest `getWidth()`
  and `getUrl()`. Fallback: `MediaExtKt.A0F(media).getUrl()`.
- ID: `getId()`.
- Download with Android `DownloadManager`:
  - `setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)`
  - filename `ig_<sanitizedId>.jpg|.mp4`
  - async worker thread, toast on main thread, in-flight duplicate guard.

Important: the runtime Media implementation is obfuscated (e.g. `X.05ra`). Filter hook arguments with
`Media.class.isInstance(arg)`, not `arg.getClass().getName().contains("Media")`.

---

## 9. Verification

### 9.1 Logs

```bat
adb logcat -d | grep -E "DLM|LSPatch|FATAL|VerifyError"
```

Module logs include hook install, `menu row added`, `seen suppressed`, `download clicked/pick/url`.

### 9.2 UI

- Profile: Mod Setting button visible below Edit/Share; dialog toggles update text and
  `shared_prefs/igmod.xml`.
- Menu: Download row visible; toggling Download OFF must hide it.
- Story: open a story, check `seen suppressed`; toggle anonymous OFF and verify it forwards.
- Downloads: pull the file and compare:
  - image: view it / check dimensions;
  - video: `ffmpeg -ss 1 -i file.mp4 -frames:v 1 frame.png` and compare with the visible post.

### 9.3 Content checks performed

- Feed video on MuMu matched the visible entrepreneursonig/Jensen Huang post.
- Story image matched the visible houseofhighlights Q&A slide.
- Phone feed image matched the visible Monet carousel item at 1080 px.

---

## 10. Performance measurement

Use a stable screen (profile), screen on, app foreground. Prefer CPU-time delta over spot `top`:

```bat
set pid=...
adb shell "awk '{print $14+$15}' /proc/%pid%/stat"
REM wait 30 s
adb shell "awk '{print $14+$15}' /proc/%pid%/stat"
REM percent = (delta_ticks * 100) / (100 * 30)
```

Record the state with a screenshot. Feed video playback dominates CPU and invalidates idle
comparisons. Compare the mod against an identical screen/build before drawing conclusions.

---

## 11. Adapting to a new IG version

1. Pull the new APK/splits; verify version/ABI.
2. Build a clean base; LSPatch patch with `--injectdex` and test boot.
3. Probe the new class names with runtime stack traces:
   - seen-report method (old `X.00OH.A03`)
   - profile binder/text setter
   - menu row type/texts
4. Re-check whether menu adapters hold Media; re-run hit-test/listener scan.
5. Update hook class names/parameter types in `Module.java`.
6. Rebuild module/repatch; verify UI, downloads and content matches.
7. Re-measure CPU on the phone.

The UI label strings may come from GraphQL catalogs; detect them at runtime rather than by resource ID.

---

## 12. Checklist / pitfalls

- [ ] Confirm the app process is the main package (skip `:fbns`).
- [ ] Use the real class loader; verify loaded class methods.
- [ ] Hook IG-specific rows, not global framework methods when possible.
- [ ] Delay view-tree scans until after layout/attachment.
- [ ] Tag injected views and guard against duplicates.
- [ ] Use `TouchDelegate` when overlays intercept touches.
- [ ] Never identify Media by class-name substring; use `isInstance`.
- [ ] Freeze the media candidate at menu open; prefetch changes later.
- [ ] Verify downloads against content, not just log lines.
- [ ] Do not combine Frida and LSPatch in the same APK.
- [ ] Do not update the LSPatch module with `jar uf`/7-Zip; repatch.
- [ ] Sign with the intended key; record the hash/size.
- [ ] Test both arm64 phone and x86_64 emulator paths.
