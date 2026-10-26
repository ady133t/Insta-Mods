package com.dl.igmod;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import de.robv.android.xposed.XposedHelpers;

/**
 * Hides Instagram's "Previous story" / "Next story" arrow buttons while the story viewer is open.
 * They are ordinary clickable Buttons, so they are matched by their content description and made
 * invisible (kept in place so the layout does not shift).
 */
final class StoryNavHider {
    private static WeakReference<View> rootRef = new WeakReference<View>(null);
    private static volatile boolean running = false;

    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                hide(rootRef.get());
            } catch (Throwable t) {
            }
            if (running) Module.MAIN.postDelayed(this, 400L);
        }
    };

    static void start(Object fragment) {
        try {
            Object v = XposedHelpers.callMethod(fragment, "getView");
            if (!(v instanceof View)) return;
            rootRef = new WeakReference<View>((View) v);
            running = true;
            Module.MAIN.removeCallbacks(TICK);
            Module.MAIN.post(TICK);
        } catch (Throwable t) {
            Log.e("DLM", "story nav hider start failed", t);
        }
    }

    static void stop() {
        running = false;
        Module.MAIN.removeCallbacks(TICK);
        rootRef = new WeakReference<View>(null);
    }

    private static void hide(View root) {
        if (root == null || !(root instanceof ViewGroup)) return;
        ArrayList<View> stack = new ArrayList<View>();
        ViewGroup rg = (ViewGroup) root;
        for (int i = 0; i < rg.getChildCount(); i++) {
            try {
                stack.add(rg.getChildAt(i));
            } catch (Throwable t) {
            }
        }
        int guard = 0;
        while (!stack.isEmpty() && guard++ < 3000) {
            View v = stack.remove(stack.size() - 1);
            if (v == null) continue;
            if (isStoryNav(v)) {
                try {
                    if (v.getVisibility() != View.INVISIBLE) {
                        v.setVisibility(View.INVISIBLE);
                        v.setClickable(false);
                        v.setEnabled(false);
                    }
                } catch (Throwable t) {
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
    }

    private static boolean isStoryNav(View v) {
        try {
            CharSequence cd = v.getContentDescription();
            if (cd != null) {
                String s = cd.toString().trim().toLowerCase(java.util.Locale.US);
                if (s.equals("previous story") || s.equals("next story")
                        || s.equals("previous") || s.equals("next")) {
                    return true;
                }
            }
            if (v instanceof android.widget.Button) {
                CharSequence t = ((android.widget.Button) v).getText();
                if (t != null) {
                    String s = t.toString().trim().toLowerCase(java.util.Locale.US);
                    if (s.equals("previous") || s.equals("next")) return true;
                }
            }
        } catch (Throwable t) {
        }
        return false;
    }
}
