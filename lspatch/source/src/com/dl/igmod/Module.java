package com.dl.igmod;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.robv.android.xposed.AndroidAppHelper;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Module implements IXposedHookLoadPackage {
    static final String TAG = "DLM";
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    static Class<?> MEDIA_CLS;
    static Class<?> MEDIA_EXT;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.instagram.android".equals(lpparam.packageName)) return;
        if (lpparam.processName != null && !lpparam.processName.equals(lpparam.packageName)) return;
        final ClassLoader cl = lpparam.classLoader;
        Log.i(TAG, "module init");

        initPrefs(cl);
        hookStealth(cl);
        hookStory(cl);
        hookMedia(cl);
        hookProfileText(cl);
    }

    static void initPrefs(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("com.instagram.app.InstagramAppShell", cl, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Prefs.init((Context) param.thisObject);
                        Log.i(TAG, "prefs initialized");
                    } catch (Throwable t) {
                    }
                }
            });
            Log.i(TAG, "prefs hook");
        } catch (Throwable t) {
            Log.e(TAG, "prefs hook failed", t);
            MAIN.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        Context c = AndroidAppHelper.currentApplication();
                        if (c != null) {
                            Prefs.init(c);
                        } else {
                            MAIN.postDelayed(this, 500);
                        }
                    } catch (Throwable x) {
                    }
                }
            }, 500);
        }
    }

    static long lastSeenLog = 0;

    static void hookStealth(final ClassLoader cl) {
        try {
            Class<?> oh = XposedHelpers.findClass("X.00OH", cl);
            Class<?> ap = XposedHelpers.findClass("X.00AP", cl);
            XposedHelpers.findAndHookMethod(oh, "A03", ap, String.class, String.class, String.class, Map.class, long.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Prefs.anon) {
                        long now = System.currentTimeMillis();
                        if (now - lastSeenLog > 3000) {
                            lastSeenLog = now;
                            Log.i(TAG, "seen suppressed");
                        }
                        param.setResult(null);
                    }
                }
            });
            Log.i(TAG, "stealth hook");
        } catch (Throwable t) {
            Log.e(TAG, "stealth hook failed", t);
        }
    }

    static void hookStory(final ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod("instagram.features.stories.fragment.ReelViewerFragment", cl, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Story.active = true;
                }
            });
            XposedHelpers.findAndHookMethod("instagram.features.stories.fragment.ReelViewerFragment", cl, "onPause", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Story.active = false;
                }
            });
            Log.i(TAG, "story hook");
        } catch (Throwable t) {
            Log.e(TAG, "story hook failed", t);
        }
    }

    static void hookMedia(final ClassLoader cl) {
        try {
            MEDIA_CLS = XposedHelpers.findClass("com.instagram.feed.media.Media", cl);
            MEDIA_EXT = XposedHelpers.findClass("com.instagram.feed.media.MediaExtKt", cl);
            XposedBridge.hookAllMethods(MEDIA_EXT, "A0F", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args != null && param.args.length > 0 && param.args[0] != null) {
                            if (MEDIA_CLS.isInstance(param.args[0])) MediaTracker.add(param.args[0]);
                        }
                    } catch (Throwable t) {
                    }
                }
            });
            Log.i(TAG, "media hook");
        } catch (Throwable t) {
            Log.e(TAG, "media hook failed", t);
        }
    }

    static long lastMenuMs = 0;

    static void hookProfileText(final ClassLoader cl) {
        try {
            Class<?> tv = XposedHelpers.findClass("android.widget.TextView", cl);
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length == 0 || param.args[0] == null) return;
                        Object a = param.args[0];
                        String s;
                        if (a instanceof CharSequence) {
                            s = a.toString();
                        } else if (a instanceof Integer) {
                            TextView v = (TextView) param.thisObject;
                            s = v.getContext().getResources().getString((Integer) a);
                        } else {
                            return;
                        }
                        if ("Edit profile".equals(s)) {
                            final TextView anchor = (TextView) param.thisObject;
                            MAIN.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Profile.inject(anchor);
                                }
                            }, 300);
                        } else if ("Not interested".equals(s) || "AI info".equals(s) || "Report".equals(s)) {
                            long now = System.currentTimeMillis();
                            if (now - lastMenuMs > 1200) {
                                lastMenuMs = now;
                                final TextView anchor = (TextView) param.thisObject;
                                final Object menuMedia = MediaTracker.pick();
                                MAIN.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        MenuInjector.inject(anchor, menuMedia);
                                    }
                                }, 300);
                            }
                        }
                    } catch (Throwable t) {
                    }
                }
            };
            XposedHelpers.findAndHookMethod(tv, "setText", CharSequence.class, hook);
            XposedHelpers.findAndHookMethod(tv, "setText", int.class, hook);
            Log.i(TAG, "text hooks");
        } catch (Throwable t) {
            Log.e(TAG, "text hooks failed", t);
        }
    }
}

final class Prefs {
    static SharedPreferences sp;
    static volatile boolean anon = true;
    static volatile boolean download = true;

    static synchronized void init(Context c) {
        if (c == null) return;
        try {
            sp = c.getSharedPreferences("igmod", 0);
            anon = sp.getBoolean("anon_story", true);
            download = sp.getBoolean("download_enabled", true);
        } catch (Throwable t) {
        }
    }

    static synchronized void setAnon(Context c, boolean v) {
        anon = v;
        try {
            if (sp == null) init(c);
            if (sp != null) sp.edit().putBoolean("anon_story", v).apply();
        } catch (Throwable t) {
        }
    }

    static synchronized void setDownload(Context c, boolean v) {
        download = v;
        try {
            if (sp == null) init(c);
            if (sp != null) sp.edit().putBoolean("download_enabled", v).apply();
        } catch (Throwable t) {
        }
    }
}

final class Story {
    static volatile boolean active = false;
}

final class Ui {
    static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static boolean hasTag(ViewGroup g, String tag) {
        for (int i = 0; i < g.getChildCount(); i++) {
            try {
                Object t = g.getChildAt(i).getTag();
                if (t != null && tag.equals(t.toString())) return true;
            } catch (Throwable t) {
            }
        }
        return false;
    }
}

final class Profile {
    static void inject(final TextView anchor) {
        try {
            if (anchor == null) return;
            View cur = anchor;
            View row = null;
            for (int i = 0; i < 8 && cur != null; i++) {
                String cn = cur.getClass().getName();
                if (cn.contains("IgLinearLayout")) {
                    ViewGroup vg = (ViewGroup) cur;
                    boolean hasFollow = false;
                    for (int k = 0; k < vg.getChildCount(); k++) {
                        try {
                            if (vg.getChildAt(k).getClass().getName().contains("FollowChainingButton")) {
                                hasFollow = true;
                                break;
                            }
                        } catch (Throwable t) {
                        }
                    }
                    if (hasFollow) {
                        row = cur;
                        break;
                    }
                }
                ViewParent p = cur.getParent();
                if (!(p instanceof View)) break;
                cur = (View) p;
            }
            if (row == null) {
                Log.i(Module.TAG, "profile row not found");
                return;
            }
            ViewParent pp = row.getParent();
            if (!(pp instanceof ViewGroup)) return;
            ViewGroup parent = (ViewGroup) pp;
            if (Ui.hasTag(parent, "MOD_SETTING_BTN")) return;

            final Context ctx = anchor.getContext();
            TextView btn = new TextView(ctx);
            btn.setText("Mod Setting");
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(14f);
            btn.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#7B2FF7"));
            bg.setCornerRadius(Ui.dp(ctx, 20));
            btn.setBackground(bg);
            btn.setPadding(Ui.dp(ctx, 32), Ui.dp(ctx, 10), Ui.dp(ctx, 32), Ui.dp(ctx, 10));
            btn.setTag("MOD_SETTING_BTN");
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Settings.show(v.getContext());
                }
            });

            FrameLayout holder = new FrameLayout(ctx);
            holder.setTag("MOD_SETTING_BTN");
            holder.setPadding(0, Ui.dp(ctx, 24), 0, 0);
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            holder.addView(btn, fp);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(ctx, 5);
            lp.bottomMargin = Ui.dp(ctx, 8);
            int idx = parent.indexOfChild(row);
            try {
                parent.addView(holder, idx + 1, lp);
            } catch (Throwable t) {
                parent.addView(holder);
            }
            Log.i(Module.TAG, "profile button added");
        } catch (Throwable t) {
            Log.e(Module.TAG, "profile inject failed", t);
        }
    }
}

final class Settings {
    static void show(final Context ctx) {
        try {
            final LinearLayout ll = new LinearLayout(ctx);
            ll.setOrientation(LinearLayout.VERTICAL);
            ll.setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 12), Ui.dp(ctx, 24), Ui.dp(ctx, 4));

            final TextView row1 = new TextView(ctx);
            row1.setText("Anonymous story view: " + (Prefs.anon ? "ON" : "OFF"));
            row1.setTextSize(16f);
            row1.setTextColor(Color.WHITE);
            row1.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 18), Ui.dp(ctx, 12), Ui.dp(ctx, 18));
            row1.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setAnon(ctx, !Prefs.anon);
                    row1.setText("Anonymous story view: " + (Prefs.anon ? "ON" : "OFF"));
                }
            });

            final TextView row2 = new TextView(ctx);
            row2.setText("Download option: " + (Prefs.download ? "ON" : "OFF"));
            row2.setTextSize(16f);
            row2.setTextColor(Color.WHITE);
            row2.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 18), Ui.dp(ctx, 12), Ui.dp(ctx, 18));
            row2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setDownload(ctx, !Prefs.download);
                    row2.setText("Download option: " + (Prefs.download ? "ON" : "OFF"));
                }
            });

            ll.addView(row1);
            ll.addView(row2);

            AlertDialog.Builder b = new AlertDialog.Builder(ctx);
            b.setTitle("Mod Settings");
            b.setView(ll);
            b.setPositiveButton("Close", null);
            b.show();
        } catch (Throwable t) {
            Log.e(Module.TAG, "settings failed", t);
        }
    }
}

final class MediaTracker {
    static final List<Object> MEDIA = new ArrayList<Object>();
    static final List<Long> TS = new ArrayList<Long>();
    static final List<Boolean> MAIN = new ArrayList<Boolean>();

    static synchronized void add(Object m) {
        try {
            if (m == null) return;
            String id = idOf(m);
            if (id == null) return;
            long now = System.currentTimeMillis();
            boolean main = "main".equals(Thread.currentThread().getName());
            for (int i = 0; i < MEDIA.size(); i++) {
                if (id.equals(idOf(MEDIA.get(i)))) {
                    TS.set(i, now);
                    MAIN.set(i, main);
                    return;
                }
            }
            MEDIA.add(m);
            TS.add(now);
            MAIN.add(main);
            while (MEDIA.size() > 80) {
                MEDIA.remove(0);
                TS.remove(0);
                MAIN.remove(0);
            }
        } catch (Throwable t) {
        }
    }

    static synchronized Object pick() {
        long now = System.currentTimeMillis();
        for (int i = MEDIA.size() - 1; i >= 0; i--) {
            if (MAIN.get(i) && now - TS.get(i) < 60000) return MEDIA.get(i);
        }
        for (int i = MEDIA.size() - 1; i >= 0; i--) {
            if (now - TS.get(i) < 60000) return MEDIA.get(i);
        }
        return null;
    }

    static synchronized int size() {
        return MEDIA.size();
    }

    static String idOf(Object m) {
        try {
            Object v = XposedHelpers.callMethod(m, "getId");
            return v == null ? null : v.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}

final class MenuInjector {
    static void inject(final TextView anchor, final Object menuMedia) {
        if (!Prefs.download) return;
        try {
            View cur = anchor;
            ViewGroup rv = null;
            for (int i = 0; i < 15 && cur != null; i++) {
                String cn = cur.getClass().getName();
                if (cn.contains("RecyclerView")) {
                    rv = (ViewGroup) cur;
                    break;
                }
                ViewParent p = cur.getParent();
                if (!(p instanceof View)) break;
                cur = (View) p;
            }
            if (rv == null) {
                Log.i(Module.TAG, "menu: no RecyclerView");
                return;
            }
            ViewParent pp = rv.getParent();
            if (!(pp instanceof LinearLayout)) {
                Log.i(Module.TAG, "menu: parent is " + (pp == null ? "null" : pp.getClass().getName()));
                return;
            }
            LinearLayout parent = (LinearLayout) pp;
            if (Ui.hasTag(parent, "DL_MENU_ROW")) return;
            Object vis = VisibleMedia.findAt(anchor.getRootView());
            final Object chosen = vis != null ? vis : menuMedia;
            Log.i(Module.TAG, "menu media " + (chosen == null ? "null" : MediaTracker.idOf(chosen)));
            final Context ctx = anchor.getContext();
            TextView row = new TextView(ctx);
            row.setText("Download");
            row.setTextSize(16f);
            row.setTextColor(Color.WHITE);
            row.setPadding(Ui.dp(ctx, 32), Ui.dp(ctx, 24), Ui.dp(ctx, 32), Ui.dp(ctx, 24));
            row.setTag("DL_MENU_ROW");
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Downloader.downloadMenu(v.getContext(), chosen);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int idx = parent.indexOfChild(rv);
            if (idx < 0) idx = 0;
            parent.addView(row, idx, lp);
            row.bringToFront();
            final ViewGroup touchParent = parent;
            final TextView touchRow = row;
            touchParent.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Rect r = new Rect();
                        touchRow.getHitRect(r);
                        touchParent.setTouchDelegate(new TouchDelegate(r, touchRow));
                    } catch (Throwable t) {
                    }
                }
            });
            Log.i(Module.TAG, "menu row added at " + idx);
        } catch (Throwable t) {
            Log.e(Module.TAG, "menu inject failed", t);
        }
    }
}

final class Downloader {
    static final Set<String> ACTIVE = new HashSet<String>();

    static void downloadMenu(final Context ctx, final Object snapshot) {
        Log.i(Module.TAG, "download clicked snapshot=" + (snapshot == null ? "null" : MediaTracker.idOf(snapshot)));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Object m = snapshot != null ? snapshot : MediaTracker.pick();
                    if (m == null) {
                        Log.i(Module.TAG, "download: no media");
                        toast(ctx, "No media found");
                        return;
                    }
                    String id = MediaTracker.idOf(m);
                    Log.i(Module.TAG, "download pick id=" + id);
                    String url = null;
                    boolean video = false;
                    try {
                        Object list = XposedHelpers.callMethod(m, "AB6");
                        if (list instanceof List) {
                            List<?> l = (List<?>) list;
                            int bestW = -1;
                            Object best = null;
                            for (int i = 0; i < l.size(); i++) {
                                Object v = l.get(i);
                                int w = -1;
                                try {
                                    Object wv = XposedHelpers.callMethod(v, "getWidth");
                                    if (wv instanceof Number) w = ((Number) wv).intValue();
                                } catch (Throwable t) {
                                }
                                if (best == null || w > bestW) {
                                    best = v;
                                    bestW = w;
                                }
                            }
                            if (best != null) {
                                Object uv = XposedHelpers.callMethod(best, "getUrl");
                                if (uv != null) {
                                    url = uv.toString();
                                    video = true;
                                }
                            }
                        }
                    } catch (Throwable t) {
                    }
                    if (url == null) {
                        try {
                            Object info = XposedHelpers.callStaticMethod(Module.MEDIA_EXT, "A0g", m);
                            if (info != null) {
                                Object list = XposedHelpers.callMethod(info, "BgD");
                                if (list instanceof List) {
                                    List<?> imgs = (List<?>) list;
                                    int bestW = -1;
                                    Object best = null;
                                    for (int i = 0; i < imgs.size(); i++) {
                                        Object im = imgs.get(i);
                                        int w = -1;
                                        try {
                                            Object wv = XposedHelpers.callMethod(im, "getWidth");
                                            if (wv instanceof Number) w = ((Number) wv).intValue();
                                        } catch (Throwable t) {
                                        }
                                        if (best == null || w > bestW) {
                                            best = im;
                                            bestW = w;
                                        }
                                    }
                                    if (best != null) {
                                        Object uv = XposedHelpers.callMethod(best, "getUrl");
                                        if (uv != null) {
                                            url = uv.toString();
                                            Log.i(Module.TAG, "image candidate width=" + bestW);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) {
                        }
                    }
                    if (url == null) {
                        try {
                            Object img = XposedHelpers.callStaticMethod(Module.MEDIA_EXT, "A0F", m);
                            if (img != null) {
                                Object uv = XposedHelpers.callMethod(img, "getUrl");
                                if (uv != null) url = uv.toString();
                            }
                        } catch (Throwable t) {
                        }
                    }
                    if (url == null) {
                        toast(ctx, "No URL found");
                        return;
                    }
                    String ext = (video || url.contains(".mp4")) ? ".mp4" : ".jpg";
                    String clean = id == null ? String.valueOf(System.currentTimeMillis()) : id.replaceAll("[^A-Za-z0-9_-]", "");
                    final String name = "ig_" + clean + ext;
                    synchronized (ACTIVE) {
                        if (ACTIVE.contains(name)) {
                            toast(ctx, "Already downloading");
                            return;
                        }
                        ACTIVE.add(name);
                    }
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setTitle(name);
                    req.setDescription("Instagram");
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                    DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
                    dm.enqueue(req);
                    toast(ctx, "Download started: " + name);
                    Log.i(Module.TAG, "download " + name + " video=" + video + " url=" + url);
                } catch (Throwable t) {
                    Log.e(Module.TAG, "download failed", t);
                    toast(ctx, "Download failed");
                }
            }
        }).start();
    }

    static void toast(final Context c, final String s) {
        Module.MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                }
            }
        });
    }
}

final class VisibleMedia {
    static Object find(View root) {
        if (root == null) return null;
        try {
            List<View> stack = new ArrayList<View>();
            stack.add(root);
            int guard = 0;
            while (!stack.isEmpty() && guard++ < 3000) {
                View v = stack.remove(stack.size() - 1);
                if (v == null) continue;
                String cn = v.getClass().getName();
                if (cn.contains("RecyclerView") && v instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) v;
                    if (g.isShown()) {
                        int n = g.getChildCount();
                        for (int i = 0; i < n && i < 8; i++) {
                            View ch = g.getChildAt(i);
                            if (ch == null || !ch.isShown()) continue;
                            Object holder = null;
                            try {
                                holder = XposedHelpers.callMethod(v, "getChildViewHolder", ch);
                            } catch (Throwable t) {
                            }
                            if (holder != null) {
                                Object m = findInFields(holder);
                                if (m != null) return m;
                            }
                            Object m2 = findInFields(ch);
                            if (m2 != null) return m2;
                        }
                    }
                }
                if (v instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) v;
                    for (int i = 0; i < g.getChildCount(); i++) {
                        try {
                            View c = g.getChildAt(i);
                            if (c != null) stack.add(c);
                        } catch (Throwable t) {
                        }
                    }
                }
            }
        } catch (Throwable t) {
        }
        return null;
    }

    static Object findInFields(Object obj) {
        if (obj == null || Module.MEDIA_CLS == null) return null;
        try {
            Class<?> c = obj.getClass();
            for (int d = 0; d < 4 && c != null && c != Object.class; d++) {
                Field[] fs = c.getDeclaredFields();
                for (int i = 0; i < fs.length; i++) {
                    try {
                        if (Modifier.isStatic(fs[i].getModifiers())) continue;
                        fs[i].setAccessible(true);
                        Object val = fs[i].get(obj);
                        if (val != null && Module.MEDIA_CLS.isInstance(val)) return val;
                    } catch (Throwable t) {
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable t) {
        }
        return null;
    }

    static Object findInFragments(Context ctx) {
        try {
            Activity act = null;
            Context c = ctx;
            for (int i = 0; i < 8 && c != null; i++) {
                if (c instanceof Activity) {
                    act = (Activity) c;
                    break;
                }
                if (c instanceof ContextWrapper) c = ((ContextWrapper) c).getBaseContext();
                else break;
            }
            if (act == null) return null;
            Object fm = null;
            try {
                fm = XposedHelpers.callMethod(act, "getSupportFragmentManager");
            } catch (Throwable t) {
            }
            if (fm == null) {
                try {
                    fm = XposedHelpers.callMethod(act, "getFragmentManager");
                } catch (Throwable t) {
                }
            }
            if (fm == null) return null;
            Object fl = XposedHelpers.callMethod(fm, "getFragments");
            if (!(fl instanceof List)) return null;
            List<?> frags = (List<?>) fl;
            for (int pass = 0; pass < 2; pass++) {
                for (int i = frags.size() - 1; i >= 0; i--) {
                    Object f = frags.get(i);
                    if (f == null) continue;
                    String cn = f.getClass().getName().toLowerCase();
                    if (pass == 0 && !(cn.contains("menu") || cn.contains("sheet") || cn.contains("dialog") || cn.contains("overflow") || cn.contains("action"))) continue;
                    Log.i(Module.TAG, "frag " + f.getClass().getName());
                    if (pass == 0) {
                        Debug.dump(f, "frag", 2);
                        dumpArgs(f);
                    }
                    Object m = searchGraph(f, 5);
                    if (m != null) {
                        Log.i(Module.TAG, "frag media " + MediaTracker.idOf(m));
                        return m;
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(Module.TAG, "fragment search failed", t);
        }
        return null;
    }

    static void dumpArgs(Object f) {
        try {
            Object b = XposedHelpers.callMethod(f, "getArguments");
            if (!(b instanceof Bundle)) {
                Log.i(Module.TAG, "frag args none");
                return;
            }
            Bundle bundle = (Bundle) b;
            for (String k : bundle.keySet()) {
                Object v = bundle.get(k);
                String vs = v == null ? "null" : (v.getClass().getName() + "=" + v);
                Log.i(Module.TAG, "frag arg " + k + " : " + vs);
            }
        } catch (Throwable t) {
            Log.e(Module.TAG, "frag args failed", t);
        }
    }

    static Object findAt(View root) {
        if (root == null) return null;
        try {
            int[] loc = new int[2];
            root.getLocationOnScreen(loc);
            return findAt(root, loc[0] + root.getWidth() / 2, loc[1] + root.getHeight() / 3);
        } catch (Throwable t) {
            return null;
        }
    }

    static Object findAt(View root, int x, int y) {
        View hit = hitTest(root, x, y);
        if (hit == null) return null;
        Object lm = findListenerMedia(hit);
        if (lm != null) return lm;
        View cur = hit;
        for (int i = 0; i < 12 && cur != null; i++) {
            Object m = findInFields(cur);
            if (m != null) return m;
            if (cur.getClass().getName().contains("RecyclerView")) {
                try {
                    int[] loc = new int[2];
                    cur.getLocationOnScreen(loc);
                    Object child = XposedHelpers.callMethod(cur, "findChildViewUnder", x - loc[0], y - loc[1]);
                    if (child != null) {
                        Log.i(Module.TAG, "holder child=" + child.getClass().getName());
                        Object holder = XposedHelpers.callMethod(cur, "getChildViewHolder", child);
                        m = findInFields(holder);
                        if (m != null) return m;
                        m = findMediaDeep(child, 2);
                        if (m != null) return m;
                    }
                } catch (Throwable t) {
                }
            }
            ViewParent p = cur.getParent();
            cur = (p instanceof View) ? (View) p : null;
        }
        return null;
    }

    static View hitTest(View v, int x, int y) {
        if (v == null || !v.isShown()) return null;
        try {
            Rect r = new Rect();
            if (!v.getGlobalVisibleRect(r)) return null;
            if (!r.contains(x, y)) return null;
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = g.getChildCount() - 1; i >= 0; i--) {
                    View h = hitTest(g.getChildAt(i), x, y);
                    if (h != null) return h;
                }
            }
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    static Object searchGraph(Object root, int maxDepth) {
        if (root == null || Module.MEDIA_CLS == null) return null;
        try {
            IdentityHashMap<Object, Object> seen = new IdentityHashMap<Object, Object>();
            ArrayDeque<Object[]> q = new ArrayDeque<Object[]>();
            q.add(new Object[]{root, 0});
            int guard = 0;
            while (!q.isEmpty() && guard++ < 4000) {
                Object[] e = q.poll();
                Object v = e[0];
                int d = (Integer) e[1];
                if (v == null || seen.containsKey(v)) continue;
                seen.put(v, v);
                if (Module.MEDIA_CLS.isInstance(v)) {
                    Log.i(Module.TAG, "graph media " + MediaTracker.idOf(v));
                    return v;
                }
                if (d >= maxDepth) continue;
                if (v instanceof List) {
                    List<?> l = (List<?>) v;
                    for (int i = 0; i < l.size() && i < 12; i++) q.add(new Object[]{l.get(i), d + 1});
                    continue;
                }
                String cn = v.getClass().getName();
                if (!(cn.startsWith("X.") || cn.startsWith("com.instagram.") || cn.startsWith("com.facebook.litho"))) continue;
                Class<?> c = v.getClass();
                for (int k = 0; k < 3 && c != null && c != Object.class; k++) {
                    Field[] fs = c.getDeclaredFields();
                    for (int i = 0; i < fs.length; i++) {
                        try {
                            if (Modifier.isStatic(fs[i].getModifiers())) continue;
                            fs[i].setAccessible(true);
                            Object val = fs[i].get(v);
                            if (val == null) continue;
                            if (Module.MEDIA_CLS.isInstance(val)) {
                                Log.i(Module.TAG, "graph media " + MediaTracker.idOf(val));
                                return val;
                            }
                            q.add(new Object[]{val, d + 1});
                        } catch (Throwable t) {
                        }
                    }
                    c = c.getSuperclass();
                }
            }
        } catch (Throwable t) {
        }
        return null;
    }

    static Object findListenerMedia(View hit) {
        try {
            View item = hit;
            for (int i = 0; i < 12 && item != null; i++) {
                ViewParent p = item.getParent();
                if (!(p instanceof View)) break;
                String pn = ((View) p).getClass().getName();
                if (pn.contains("RecyclerView")) break;
                item = (View) p;
            }
            return scanListeners(item);
        } catch (Throwable t) {
        }
        return null;
    }

    static Object scanListeners(View root) {
        if (root == null) return null;
        try {
            ArrayDeque<View> q = new ArrayDeque<View>();
            q.add(root);
            int guard = 0;
            Field lf = View.class.getDeclaredField("mListenerInfo");
            lf.setAccessible(true);
            while (!q.isEmpty() && guard++ < 600) {
                View v = q.poll();
                if (v == null) continue;
                try {
                    Object li = lf.get(v);
                    if (li != null) {
                        Field[] fs = li.getClass().getDeclaredFields();
                        for (int i = 0; i < fs.length; i++) {
                            if (!fs[i].getName().toLowerCase().contains("listener")) continue;
                            try {
                                fs[i].setAccessible(true);
                                Object l = fs[i].get(li);
                                if (l == null) continue;
                                Object m = searchGraph(l, 3);
                                if (m != null) {
                                    Log.i(Module.TAG, "listener media " + MediaTracker.idOf(m));
                                    return m;
                                }
                            } catch (Throwable t) {
                            }
                        }
                    }
                    Object tag = v.getTag();
                    if (tag != null) {
                        Object m = searchGraph(tag, 3);
                        if (m != null) return m;
                    }
                } catch (Throwable t) {
                }
                if (v instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) v;
                    for (int i = 0; i < g.getChildCount(); i++) {
                        try {
                            View c = g.getChildAt(i);
                            if (c != null) q.add(c);
                        } catch (Throwable t) {
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(Module.TAG, "listener scan failed", t);
        }
        return null;
    }

    static Object findMediaDeep(Object obj, int depth) {
        if (obj == null || depth < 0) return null;
        Object m = findInFields(obj);
        if (m != null) return m;
        if (depth == 0) return null;
        try {
            Class<?> c = obj.getClass();
            for (int d = 0; d < 3 && c != null && c != Object.class; d++) {
                Field[] fs = c.getDeclaredFields();
                for (int i = 0; i < fs.length; i++) {
                    try {
                        if (Modifier.isStatic(fs[i].getModifiers())) continue;
                        fs[i].setAccessible(true);
                        Object v = fs[i].get(obj);
                        if (v == null) continue;
                        if (Module.MEDIA_CLS.isInstance(v)) return v;
                        String vn = v.getClass().getName();
                        if (vn.startsWith("X.") || vn.startsWith("com.instagram.")) {
                            Object r = findMediaDeep(v, depth - 1);
                            if (r != null) return r;
                        }
                    } catch (Throwable t) {
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable t) {
        }
        return null;
    }
}

final class Debug {
    static void dump(Object o, String label, int depth) {
        if (o == null || depth < 0) return;
        try {
            Class<?> c = o.getClass();
            for (int d = 0; d < 3 && c != null && c != Object.class; d++) {
                Field[] fs = c.getDeclaredFields();
                for (int i = 0; i < fs.length; i++) {
                    try {
                        if (Modifier.isStatic(fs[i].getModifiers())) continue;
                        fs[i].setAccessible(true);
                        Object v = fs[i].get(o);
                        String s = label + "." + fs[i].getName() + " : " + (v == null ? "null" : v.getClass().getName());
                        if (v instanceof String || v instanceof Number || v instanceof Boolean) s += "=" + v;
                        Log.i(Module.TAG, s);
                        if (v instanceof List && depth > 0) {
                            List<?> l = (List<?>) v;
                            Log.i(Module.TAG, label + "." + fs[i].getName() + " size=" + l.size());
                            for (int k = 0; k < l.size() && k < 3; k++) {
                                Object it = l.get(k);
                                if (it != null) {
                                    Log.i(Module.TAG, " item" + k + "=" + it.getClass().getName());
                                    dump(it, label + ".item" + k, depth - 1);
                                }
                            }
                        }
                    } catch (Throwable t) {
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Throwable t) {
        }
    }
}



