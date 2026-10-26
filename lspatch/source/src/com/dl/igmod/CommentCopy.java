package com.dl.igmod;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TouchDelegate;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Caches the comment under the long-press so the popup menu can copy it.
 *
 * The comment body is drawn by Litho (rendercore) and is not readable through getText(),
 * getContentDescription() or the view tree. IG only materialises the merged "user said body"
 * label on the row's ComponentHost while an accessibility service is running, so
 * {@link Module#hookAccessibilityPretend} makes the process believe one is connected;
 * {@link #HOST_DESC} then caches those labels through ComponentHost.setContentDescription.
 */
final class CommentStore {
    private static volatile String body;
    private static volatile long capturedAt;
    private static volatile WeakReference<Activity> activityRef;
    private static volatile float touchX;
    private static volatile float touchY;
    private static volatile long touchAt;
    private static volatile WeakReference<View> anchorRef;

    /**
     * Text that rendercore mounted into a view, keyed by the view. Filled from the
     * RCTextView / IgTextLayoutView mount hooks, so it works without any accessibility
     * service running.
     */
    static final java.util.WeakHashMap<View, String> RENDER_TEXT =
            new java.util.WeakHashMap<View, String>();

    /**
     * Content descriptions set on Litho ComponentHosts. The merged "user said body" label for a
     * comment row is pushed through ComponentHost.setContentDescription, so every call is cached
     * with its host view; at menu time the entry nearest the press is the comment.
     */
    static final java.util.WeakHashMap<View, String> HOST_DESC =
            new java.util.WeakHashMap<View, String>();

    /** Called from the RCTextView / IgTextLayoutView mount hooks with the text just bound. */
    static void onRenderCoreText(View view, CharSequence text) {
        try {
            if (view == null || text == null) return;
            String s = text.toString().trim();
            if (s.length() < 2 || s.length() > 1000) return;
            if (!hasLetter(s)) return;
            synchronized (RENDER_TEXT) {
                RENDER_TEXT.put(view, s);
            }
        } catch (Throwable t) {
        }
    }

    /** Called from the ComponentHost.setContentDescription hook. */
    static void onHostDescription(View view, CharSequence text) {
        try {
            if (view == null || text == null) return;
            String s = text.toString().trim();
            if (s.length() == 0) return;
            if (s.length() > 2000) return;
            synchronized (HOST_DESC) {
                HOST_DESC.put(view, s);
            }
        } catch (Throwable t) {
        }
    }

    static void touch(Activity activity, MotionEvent event) {
        try {
            if (activity == null || event == null) return;
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return;
            // Cheap only: remember where the finger went down. The expensive window walk is
            // deferred to captureForMenu() so scrolling never pays for it on every ACTION_DOWN.
            activityRef = new WeakReference<Activity>(activity);
            touchX = event.getRawX();
            touchY = event.getRawY();
            touchAt = System.currentTimeMillis();
        } catch (Throwable t) {
            Log.e("DLM", "comment touch failed", t);
        }
    }

    /**
     * Fires on View.performLongClick() for any view. Comment rows are long-pressable and sit in
     * the comments window, so reading the pressed view's own subtree gives the exact comment.
     */
    static void onLongPress(View pressed) {
        try {
            if (pressed == null) return;
            String found = saidFromAncestors(pressed);
            if (found != null) {
                body = found;
                capturedAt = System.currentTimeMillis();
                Log.i("DLM", "comment longpress captured: " + body);
            } else {
                Log.i("DLM", "comment longpress miss: " + pressed.getClass().getName());
            }
        } catch (Throwable t) {
            Log.e("DLM", "comment longpress failed", t);
        }
    }

    /** Reads "user said body" from a comment row, falling back to the body text itself. */
    private static String rowText(View row) {
        if (row == null) return null;
        String merged = accessibilityLabel(row);
        if (merged != null) {
            merged = merged.trim();
            if (!isJunk(merged) && merged.toLowerCase(Locale.US).contains(" said ")) {
                String b = extractBody(merged);
                if (b != null && b.length() > 0) return b;
            }
        }
        return null;
    }

    /**
     * Climbs from a pressed/anchored child view until it reaches the comment row that carries
     * the "user said body" accessibility label. Inner views (like the Like button) carry their
     * own labels, so only " said " labels are accepted.
     */
    private static String saidFromAncestors(View start) {
        if (start == null) return null;
        View cur = start;
        for (int depth = 0; depth < 12 && cur != null; depth++) {
            String text = rowText(cur);
            if (text != null) return text;
            if (cur.getClass().getName().contains("RecyclerView")) break;
            android.view.ViewParent parent = cur.getParent();
            if (!(parent instanceof View)) break;
            cur = (View) parent;
        }
        return null;
    }

    /**
     * The context menu PopupWindow is anchored to the long-pressed comment row, so the
     * anchor view itself is the most reliable source of the comment text.
     */
    static void onMenuAnchor(View anchor) {
        try {
            if (anchor == null) return;
            anchorRef = new WeakReference<View>(anchor);
            // Only accept a real comment row. The anchor is often the Like button, whose
            // label ("76 likes. Double tap...") must never be treated as the comment.
            String found = saidFromAncestors(anchor);
            if (found != null && found.length() > 0) {
                body = found;
                capturedAt = System.currentTimeMillis();
                Log.i("DLM", "comment anchor captured: " + body);
            } else {
                Log.i("DLM", "comment anchor miss: " + anchor.getClass().getName());
            }
        } catch (Throwable t) {
            Log.e("DLM", "comment anchor failed", t);
        }
    }

    static void captureForMenu(View menu) {
        try {
            long now = System.currentTimeMillis();
            // The PopupWindow anchor hook already resolved the exact comment row; do not let
            // the hit-test scan (which can land on the feed post behind the sheet) overwrite it.
            String anchored = body;
            if (anchored != null && anchored.length() > 0 && now - capturedAt < 10000L) {
                Log.i("DLM", "comment capture skipped, anchor already has: " + anchored);
                return;
            }
            float px = touchX;
            float py = touchY;
            boolean haveTouch = (now - touchAt) < 120000L && px > 0f && py > 0f;
            if (!haveTouch && menu != null) {
                int[] ml = new int[2];
                menu.getLocationOnScreen(ml);
                px = ml[0] + menu.getWidth() / 2f;
                py = ml[1] + menu.getHeight() / 2f;
            }

            List<View> roots = roots(activity());
            // Primary: the merged "user said body" description Litho pushed onto the row host.
            String best = hostSaid(roots, px, py, menu);
            if (best != null) Log.i("DLM", "comment via host desc: " + best);
            // Secondary: text captured at mount time by the rendercore text hook.
            if (best == null) {
                best = renderSaid(roots, px, py, menu);
                if (best != null) Log.i("DLM", "comment via rendercore: " + best);
            }
            // Tertiary: locate the real comment row by its Litho component tag
            // ("row_comment_section_container_...") and read the text drawn inside it.
            if (best == null) best = commentRowByTag(roots, menu, menu);
            if (best != null) Log.i("DLM", "comment via row tag: " + best);
            // Then: the same accessibility tree uiautomator reads.
            if (best == null) best = a11ySaid(roots, py, menu);
            // Then: the long-pressed comment is the text drawn directly above the popup.
            if (best == null) {
                best = textAboveMenu(roots, menu, menu);
                if (best != null) Log.i("DLM", "comment above menu: " + best);
            }
            if (best == null) {
                best = providerSaid(roots, py, menu);
                if (best != null) Log.i("DLM", "comment via provider: " + best);
            }
            if (best == null) best = commentRowText(roots, px, py, menu);
            if (best == null) best = scan(roots, px, py, menu);
            if (best == null) {
                // Touch point may sit between rows; fall back to the comment row nearest to it.
                best = nearestSaid(roots, px, py, menu);
            }

            if (best != null) {
                body = extractBody(best);
                capturedAt = System.currentTimeMillis();
                Log.i("DLM", "comment captured: " + body);
            } else {
                Log.i("DLM", "comment capture miss: roots=" + roots.size()
                        + " touch=" + (int) px + "," + (int) py);
            }
        } catch (Throwable t) {
            Log.e("DLM", "comment capture failed", t);
        }
    }

    static String get() {
        String s = body;
        if (s == null || s.length() == 0) return null;
        if (System.currentTimeMillis() - capturedAt > 120000L) return null;
        return s;
    }

    /**
     * Walks the cached ComponentHost content descriptions and returns the comment row nearest the
     * press. "user said body" entries win outright; otherwise the closest readable body is used.
     */
    private static String hostSaid(List<View> roots, float px, float py, View skip) {
        return nearestCached(roots, px, py, skip, HOST_DESC, true);
    }

    /** Same selection over the raw text rendercore mounted into views. */
    private static String renderSaid(List<View> roots, float px, float py, View skip) {
        // Only merged "user said body" labels: the caption view (IgTextLayoutView) also lands in
        // this cache and sits right above the first comment, so raw text must not be trusted here.
        return nearestCached(roots, px, py, skip, RENDER_TEXT, true);
    }

    private static String nearestCached(List<View> roots, float px, float py, View skip,
            java.util.WeakHashMap<View, String> cache, boolean saidOnly) {
        List<View> window = new ArrayList<View>();
        for (int r = 0; r < roots.size(); r++) {
            List<View> all = new ArrayList<View>();
            collectAll(roots.get(r), skip, all);
            // Only the comments sheet carries the "Comments" header; the feed behind it must
            // never contribute (its captions would be copied instead).
            if (!hasCommentsHeader(all)) continue;
            window.addAll(all);
        }
        if (window.isEmpty()) return null;
        String said = null;
        float saidDist = Float.MAX_VALUE;
        String body = null;
        float bodyDist = Float.MAX_VALUE;
        synchronized (cache) {
            for (java.util.Map.Entry<View, String> e : cache.entrySet()) {
                View v = e.getKey();
                if (v == null || v == skip || !v.isShown() || v.getWidth() <= 0) continue;
                if (!window.contains(v)) continue;
                String t = e.getValue();
                if (t == null || t.length() < 2) continue;
                int[] loc = new int[2];
                try {
                    v.getLocationOnScreen(loc);
                } catch (Throwable x) {
                    continue;
                }
                float cy = loc[1] + v.getHeight() / 2f;
                float d = Math.abs(cy - py);
                // The pressed row is at the finger; anything much further away (post caption
                // behind the sheet, next post) must not win.
                if (d > 200f) continue;
                String low = t.toLowerCase(Locale.US);
                if (low.indexOf(" said ") >= 0) {
                    if (d < saidDist) {
                        saidDist = d;
                        said = t;
                    }
                    continue;
                }
                if (saidOnly) continue;
                if (isJunk(t) || isChrome(low) || isMenuItem(low) || isHandle(t)) continue;
                if (!hasLetter(t)) continue;
                if (d < bodyDist) {
                    bodyDist = d;
                    body = t;
                }
            }
        }
        if (said != null) {
            String extracted = extractBody(said);
            if (extracted != null && extracted.length() > 0) return extracted;
        }
        return body;
    }

    /**
     * Litho tags each comment row with "row_comment_section_container_...". That tag is the only
     * stable way to tell a comment row apart from the post header, like buttons and reply rows, so
     * the row nearest the popup is located by it and then its drawn text is read.
     */
    private static String commentRowByTag(List<View> roots, View menu, View skip) {
        float px = touchX;
        float py = touchY;
        View bestRow = null;
        String bestTag = null;
        float bestDist = Float.MAX_VALUE;
        for (int r = 0; r < roots.size(); r++) {
            List<View> all = new ArrayList<View>();
            collectAll(roots.get(r), skip, all);
            for (int i = 0; i < all.size(); i++) {
                View v = all.get(i);
                if (v == null || !v.isShown() || v.getHeight() <= 0) continue;
                String tag = componentTag(v);
                if (tag == null) continue;
                int[] loc = new int[2];
                try {
                    v.getLocationOnScreen(loc);
                } catch (Throwable t) {
                    continue;
                }
                // The pressed row is the one under the finger; fall back to the vertically nearest.
                float cy = loc[1] + v.getHeight() / 2f;
                boolean inside = py >= loc[1] && py <= loc[1] + v.getHeight()
                        && px >= loc[0] && px <= loc[0] + v.getWidth();
                float d = inside ? -1f : Math.abs(cy - py);
                if (d < bestDist) {
                    bestDist = d;
                    bestRow = v;
                    bestTag = tag;
                }
            }
        }
        if (bestRow == null) {
            Log.i("DLM", "comment row tag: none touch=" + (int) px + "," + (int) py);
            return null;
        }
        Log.i("DLM", "comment row tag: " + bestTag);
        // The body is the longest readable text inside the row that is not the author handle.
        // Litho often mounts text straight into a drawable on the ComponentHost, so fall back to a
        // bounded reflection sweep over the row's object graph.
        String best = null;
        List<View> inner = new ArrayList<View>();
        collectAll(bestRow, skip, inner);
        try {
            List<CharSequence> found = new ArrayList<CharSequence>();
            collectStrings(bestRow, found, 5, 4000);
            for (int i = 0; i < found.size(); i++) {
                CharSequence cs = found.get(i);
                if (cs == null) continue;
                String t = cs.toString().trim();
                if (t.length() < 2 || t.length() > 400) continue;
                if (isJunk(t)) continue;
                String low = t.toLowerCase(Locale.US);
                if (isChrome(low) || isMenuItem(low) || isHandle(t)) continue;
                if (low.indexOf("row_comment") >= 0 || low.indexOf("com.") >= 0) continue;
                // Internal signal / analytics payloads, not comment text.
                if (low.indexOf("::") >= 0 || low.indexOf(",0.0.") >= 0
                        || low.indexOf("ig_signals") >= 0 || low.indexOf("_general") >= 0) {
                    continue;
                }
                // Comments are sentences; require a space and no long ALL_CAPS_SCREAMING_CASE.
                if (t.indexOf(' ') < 0) continue;
                if (t.indexOf('_') >= 0 && t.length() > 40) continue;
                if (!hasLetter(t)) continue;
                if (best == null || t.length() > best.length()) best = t;
            }
            if (best != null) {
                Log.i("DLM", "comment deep text: " + best);
                return best;
            }
        } catch (Throwable t) {
            Log.e("DLM", "deep text failed", t);
        }
        // Fallback: topmost readable text inside the row is the comment body.
        best = null;
        int bestTop = Integer.MAX_VALUE;
        for (int i = 0; i < inner.size(); i++) {
            View v = inner.get(i);
            if (v == null || !v.isShown() || v.getWidth() <= 0) continue;
            String text = textOf(v);
            if (text == null) continue;
            text = text.trim();
            if (text.length() < 2 || isJunk(text)) continue;
            String low = text.toLowerCase(Locale.US);
            if (isChrome(low) || isMenuItem(low) || isHandle(text)) continue;
            if (low.indexOf(" said ") >= 0) {
                String ex = extractBody(text);
                if (ex != null && ex.length() > 0) return ex;
            }
            if (!hasLetter(text)) continue;
            int[] loc = new int[2];
            try {
                v.getLocationOnScreen(loc);
            } catch (Throwable t) {
                continue;
            }
            if (loc[1] < bestTop) {
                bestTop = loc[1];
                best = text;
            }
        }
        if (best != null) Log.i("DLM", "comment row text: " + best);
        return best;
    }

    /** Reads the Litho/rendercore component tag that identifies a row's component type. */
    private static String componentTag(View v) {
        // The tag lives in a component field that reflectText already surfaces.
        CharSequence direct = reflectText(v);
        if (direct != null && direct.toString().indexOf("row_comment_section_container") >= 0) {
            return direct.toString();
        }
        Class<?> c = v.getClass();
        int guard = 0;
        while (c != null && c != Object.class && guard++ < 8) {
            Field[] fs;
            try {
                fs = c.getDeclaredFields();
            } catch (Throwable t) {
                break;
            }
            for (int i = 0; i < fs.length; i++) {
                Field f = fs[i];
                try {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    Class<?> ft = f.getType();
                    if (ft != String.class && !CharSequence.class.isAssignableFrom(ft)) continue;
                    f.setAccessible(true);
                    Object val = f.get(v);
                    if (val == null) continue;
                    String s = val.toString();
                    if (s.indexOf("row_comment_section_container") >= 0) return s;
                } catch (Throwable t) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * Walks the accessibility tree exactly like uiautomator does (root node + getChild recursion).
     * Litho's merged "user said body" label exists only in that tree.
     */
    private static String a11ySaid(List<View> roots, float py, View skip) {
        String best = null;
        float bestDist = Float.MAX_VALUE;
        for (int r = 0; r < roots.size(); r++) {
            View rootView = roots.get(r);
            if (rootView == null || !rootView.isShown()) continue;
            AccessibilityNodeInfo rootInfo = null;
            try {
                rootInfo = rootView.createAccessibilityNodeInfo();
            } catch (Throwable t) {
            }
            if (rootInfo == null) continue;
            List<AccessibilityNodeInfo> stack = new ArrayList<AccessibilityNodeInfo>();
            stack.add(rootInfo);
            int guard = 0;
            while (!stack.isEmpty() && guard++ < 4000) {
                AccessibilityNodeInfo info = stack.remove(stack.size() - 1);
                if (info == null) continue;
                String label = nodeLabel(info);
                if (label != null && label.toLowerCase(Locale.US).indexOf(" said ") >= 0
                        && !isJunk(label)) {
                    Rect b = new Rect();
                    try {
                        info.getBoundsInScreen(b);
                    } catch (Throwable t) {
                    }
                    float d = Math.abs(b.centerY() - py);
                    if (d < bestDist) {
                        bestDist = d;
                        best = label;
                    }
                }
                int count = 0;
                try {
                    count = info.getChildCount();
                } catch (Throwable t) {
                }
                for (int c = 0; c < count && c < 300; c++) {
                    try {
                        AccessibilityNodeInfo child = info.getChild(c);
                        if (child != null) stack.add(child);
                    } catch (Throwable t) {
                    }
                }
                try {
                    info.recycle();
                } catch (Throwable t) {
                }
            }
        }
        if (best != null) return extractBody(best);
        return null;
    }

    /**
     * The comment menu is a PopupWindow dropped right below the long-pressed comment, so the
     * closest readable text sitting above the popup is that comment's body. Author handles,
     * timestamps and the Reply/Like chrome are filtered out, and a "user said body" label wins.
     */
    private static String textAboveMenu(List<View> roots, View menu, View skip) {
        if (menu == null) return null;
        int[] ml = new int[2];
        try {
            menu.getLocationOnScreen(ml);
        } catch (Throwable t) {
            return null;
        }
        int menuTop = ml[1];
        String best = null;
        int bestBottom = Integer.MIN_VALUE;
        for (int r = 0; r < roots.size(); r++) {
            List<View> all = new ArrayList<View>();
            collectAll(roots.get(r), skip, all);
            // Only trust the window that hosts comment rows (it has a "Reply" action); otherwise a
            // feed overflow sheet ("Add to Saved") would be copied instead.
            if (!hasReplyAction(all)) continue;
            for (int i = 0; i < all.size(); i++) {
                View v = all.get(i);
                if (v == null || !v.isShown() || v.getWidth() <= 0) continue;
                // The post caption is drawn by IgTextLayoutView and sits directly above the
                // first comment; it must never be treated as a comment.
                if (v.getClass().getName().indexOf("IgTextLayoutView") >= 0) continue;
                String text = textOf(v);
                if (text == null) continue;
                text = text.trim();
                if (text.length() < 2 || isJunk(text)) continue;
                String low = text.toLowerCase(Locale.US);
                if (low.indexOf(" said ") >= 0) {
                    String ex = extractBody(text);
                    if (ex != null && ex.length() > 0) {
                        Log.i("DLM", "above said: " + ex);
                        return ex;
                    }
                }
                if (isChrome(low) || isMenuItem(low) || isHandle(text)) continue;
                if (isTimestamp(text)) continue;
                if (!hasLetter(text)) continue;
                int[] loc = new int[2];
                try {
                    v.getLocationOnScreen(loc);
                } catch (Throwable t) {
                    continue;
                }
                int bottom = loc[1] + v.getHeight();
                if (bottom > menuTop + 24) continue;
                if (bottom > bestBottom) {
                    bestBottom = bottom;
                    best = text;
                }
            }
        }
        return best;
    }

    /**
     * Litho exposes comment rows as *virtual* accessibility nodes served by
     * AccessibilityNodeProvider. Walking real children never reaches them: the provider's host
     * node (-1) must be requested first, then its getChild() chain contains the merged
     * "user said body" nodes (the same ones uiautomator prints).
     */
    private static String providerSaid(List<View> roots, float py, View skip) {
        String best = null;
        float bestDist = Float.MAX_VALUE;
        for (int r = 0; r < roots.size(); r++) {
            List<View> all = new ArrayList<View>();
            collectAll(roots.get(r), skip, all);
            for (int i = 0; i < all.size(); i++) {
                View hostView = all.get(i);
                if (hostView == null || !hostView.isShown() || hostView.getWidth() <= 0) continue;
                AccessibilityNodeProvider provider = null;
                try {
                    provider = hostView.getAccessibilityNodeProvider();
                } catch (Throwable t) {
                }
                if (provider == null) continue;
                AccessibilityNodeInfo hostNode = null;
                try {
                    hostNode = provider.createAccessibilityNodeInfo(-1);
                } catch (Throwable t) {
                }
                if (hostNode == null) {
                    try {
                        hostNode = hostView.createAccessibilityNodeInfo();
                    } catch (Throwable t) {
                    }
                }
                if (hostNode == null) continue;
                List<AccessibilityNodeInfo> stack = new ArrayList<AccessibilityNodeInfo>();
                stack.add(hostNode);
                int guard = 0;
                while (!stack.isEmpty() && guard++ < 2000) {
                    AccessibilityNodeInfo info = stack.remove(stack.size() - 1);
                    if (info == null) continue;
                    String label = nodeLabel(info);
                    if (label != null && label.toLowerCase(Locale.US).indexOf(" said ") >= 0
                            && !isJunk(label)) {
                        Rect b = new Rect();
                        try {
                            info.getBoundsInScreen(b);
                        } catch (Throwable t) {
                        }
                        float d = Math.abs(b.centerY() - py);
                        if (d < bestDist) {
                            bestDist = d;
                            best = label;
                        }
                    }
                    int count = 0;
                    try {
                        count = info.getChildCount();
                    } catch (Throwable t) {
                    }
                    for (int c = 0; c < count && c < 200; c++) {
                        try {
                            AccessibilityNodeInfo child = info.getChild(c);
                            if (child != null) stack.add(child);
                        } catch (Throwable t) {
                        }
                    }
                    try {
                        info.recycle();
                    } catch (Throwable t) {
                    }
                }
            }
        }
        if (best != null) return extractBody(best);
        return null;
    }

    private static String nodeLabel(AccessibilityNodeInfo info) {
        try {
            CharSequence cd = info.getContentDescription();
            if (cd != null && cd.length() > 0) return cd.toString().trim();
            CharSequence t = info.getText();
            if (t != null && t.length() > 0) return t.toString().trim();
        } catch (Throwable t) {
        }
        return null;
    }

    /**
     * Locates the long-pressed comment row through its "Reply" action (the only labelled view in a
     * comment row) and returns the body text drawn inside that row.
     */
    private static String commentRowText(List<View> roots, float px, float py, View skip) {
        View bestReply = null;
        float bestDist = Float.MAX_VALUE;
        for (int r = 0; r < roots.size(); r++) {
            List<View> all = new ArrayList<View>();
            collectAll(roots.get(r), skip, all);
            for (int i = 0; i < all.size(); i++) {
                View v = all.get(i);
                if (!v.isShown() || v.getWidth() <= 0) continue;
                String t = accessibilityLabel(v);
                if (t == null) continue;
                if (!"reply".equals(t.trim().toLowerCase(Locale.US))) continue;
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                float d = Math.abs(loc[1] - py);
                if (d < bestDist) {
                    bestDist = d;
                    bestReply = v;
                }
            }
        }
        if (bestReply == null) {
            Log.i("DLM", "comment row: no Reply anchor");
            return null;
        }
        // Climb to the row container (a few levels up from the Reply button).
        View row = bestReply;
        for (int i = 0; i < 4 && row.getParent() instanceof View; i++) {
            View p = (View) row.getParent();
            int[] loc = new int[2];
            p.getLocationOnScreen(loc);
            if (p.getHeight() > row.getHeight() * 4) {
                row = p;
                break;
            }
            row = p;
        }
        // Topmost readable text inside the row is the comment body (author sits above it).
        String best = null;
        int bestTop = Integer.MAX_VALUE;
        List<View> all = new ArrayList<View>();
        collectAll(row, skip, all);
        for (int i = 0; i < all.size(); i++) {
            View v = all.get(i);
            if (!v.isShown() || v.getWidth() <= 0) continue;
            String text = textOf(v);
            if (text == null) continue;
            text = text.trim();
            if (text.length() < 2 || isJunk(text)) continue;
            String low = text.toLowerCase(Locale.US);
            if (isChrome(low)) continue;
            if (low.indexOf(" said ") >= 0) {
                String ex = extractBody(text);
                if (ex != null && ex.length() > 0) return ex;
            }
            if (isHandle(text)) continue;
            if (!hasLetter(text)) continue;
            int[] loc = new int[2];
            v.getLocationOnScreen(loc);
            if (loc[1] < bestTop) {
                bestTop = loc[1];
                best = text;
            }
        }
        return best;
    }

    private static boolean hasReplyAction(List<View> all) {
        for (int i = 0; i < all.size(); i++) {
            View v = all.get(i);
            if (v == null || !v.isShown()) continue;
            String t = accessibilityLabel(v);
            if (t != null && "reply".equals(t.trim().toLowerCase(Locale.US))) return true;
        }
        return false;
    }

    /** True when the window contains the comments sheet header ("Comments" title). */
    private static boolean hasCommentsHeader(List<View> all) {
        for (int i = 0; i < all.size(); i++) {
            View v = all.get(i);
            if (v == null || !v.isShown()) continue;
            String t = accessibilityLabel(v);
            if (t != null && "comments".equals(t.trim().toLowerCase(Locale.US))) return true;
        }
        return false;
    }

    /** Feed overflow sheet entries that must never be mistaken for a comment. */
    private static boolean isMenuItem(String lower) {
        return lower.equals("add to saved") || lower.equals("save") || lower.equals("unsave")
                || lower.equals("copy link") || lower.equals("share to") || lower.equals("hide")
                || lower.equals("not interested") || lower.equals("why you're seeing this ad")
                || lower.equals("about this account") || lower.equals("unfollow")
                || lower.equals("remove from followers") || lower.equals("mute")
                || lower.equals("pin to profile") || lower.equals("delete") || lower.equals("edit")
                || lower.equals("go to post") || lower.equals("tagged users")
                || lower.startsWith("add to ") || lower.startsWith("about this ");
    }

    /** True for relative timestamps ("6 days ago", "2d", "23h") and the Edited marker. */
    private static boolean isTimestamp(String s) {
        String t = s.trim().toLowerCase(Locale.US);
        if (t.length() == 0) return false;
        if (t.equals("edited") || t.endsWith(" ago")) return true;
        if (t.length() <= 4) {
            boolean digits = false;
            for (int i = 0; i < t.length(); i++) {
                char ch = t.charAt(i);
                if (Character.isDigit(ch)) {
                    digits = true;
                    continue;
                }
                if (i == t.length() - 1 && "smhdw".indexOf(ch) >= 0) continue;
                return false;
            }
            return digits;
        }
        return false;
    }

    /** Filters resource-id placeholders and other non-content labels. */
    private static boolean isJunk(String label) {
        if (label == null) return true;
        String s = label.trim();
        if (s.length() == 0) return true;
        if (s.startsWith("@") || s.startsWith("0x")) return true;
        // Resource-id style strings ("row_comment_section_container_...") are never comment text.
        String low = s.toLowerCase(Locale.US);
        if (low.startsWith("row_") || low.startsWith("layout_") || low.startsWith("container_")
                || low.indexOf("_container_") >= 0 || low.indexOf("_section_") >= 0
                || low.indexOf("_button") >= 0 || low.indexOf("_view_") >= 0
                || low.indexOf("com.instagram") >= 0) {
            return true;
        }
        // Long trailing digit runs mean an id was formatted into the label.
        int digits = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (Character.isDigit(s.charAt(i))) digits++;
            else break;
        }
        if (digits >= 8) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || Character.isWhitespace(c)) return false;
        }
        return true;
    }

    /** True when the text looks like a bare @handle or username, not comment content. */
    private static boolean isHandle(String text) {
        String s = text.trim();
        if (s.length() == 0) return false;
        boolean allHandleChars = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '@';
            if (!ok) {
                allHandleChars = false;
                break;
            }
        }
        // Handles are short and have no spaces, so any whitespace means it's real content.
        return allHandleChars && s.indexOf(' ') < 0 && s.length() < 40;
    }

    private static boolean isChrome(String lower) {
        if (lower.equals("reply") || lower.equals("like") || lower.equals("share")
                || lower.equals("report") || lower.equals("block") || lower.equals("author")
                || lower.startsWith("view ") || lower.startsWith("add a comment")
                || lower.startsWith("join the conversation") || lower.startsWith("go to ")) {
            return true;
        }
        // App chrome and screen titles.
        if (lower.equals("instagram home feed") || lower.equals("comments")
                || lower.endsWith(" home feed") || lower.startsWith("instagram ")) {
            return true;
        }
        // Like counters: "12 likes. Double tap to like comment and press and hold to see all likes"
        if (lower.contains("double tap to like comment")
                || lower.contains("press and hold to see all likes")) {
            return true;
        }
        // Generic "N likes." / "N like." counters (also "likes," in feed labels).
        int idx = lower.indexOf(" like");
        if (idx > 0 && idx <= 12) {
            boolean digits = true;
            for (int i = 0; i < idx; i++) {
                char c = lower.charAt(i);
                if (!Character.isDigit(c) && c != ',' && c != ' ') {
                    digits = false;
                    break;
                }
            }
            if (digits) return true;
        }
        // Post/media accessibility labels from the feed behind the comment sheet.
        if (lower.startsWith("photo ") || lower.startsWith("video ")
                || lower.startsWith("reel by") || lower.startsWith("suggested photo")
                || lower.startsWith("suggested reel") || lower.startsWith("send post")
                || lower.startsWith("like by ") || lower.startsWith("audio by ")
                || lower.contains(" likes,") || lower.contains(" shares.")
                || lower.contains("button. double tap") || lower.endsWith(" comments")) {
            return true;
        }
        return false;
    }

    /** Depth-first walk that prunes any branch whose bounds do not contain the point. */
    private static void walk(View v, float px, float py, View skip, List<View> out) {
        if (v == null || v == skip) return;
        if (!v.isShown() || v.getWidth() <= 0 || v.getHeight() <= 0) return;
        int[] loc = new int[2];
        try {
            v.getLocationOnScreen(loc);
        } catch (Throwable t) {
            return;
        }
        int left = loc[0];
        int top = loc[1];
        if (px < left || px > left + v.getWidth() || py < top || py > top + v.getHeight()) return;

        out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            int n = g.getChildCount();
            for (int i = 0; i < n; i++) {
                try {
                    walk(g.getChildAt(i), px, py, skip, out);
                } catch (Throwable t) {
                }
            }
        }
    }

    /**
     * Reads the label the accessibility framework reports for this view. IG sets the
     * "user said text" label through a delegate, not through setContentDescription.
     */
    private static String label(View v, float px, float py) {
        String merged = accessibilityLabel(v);
        if (merged != null) {
            merged = merged.trim();
            // The merged "user said body" label is the best source; scan() strips the prefix.
            if (merged.toLowerCase(Locale.US).contains(" said ")) return merged;
            if (merged.length() > 0 && !merged.startsWith("@")) return merged;
        }

        String real = realText(v, px, py);
        if (real != null && real.length() > 0) return real;
        return merged;
    }

    /** Longest real text inside the row (the comment body, not the author handle). */
    private static String realText(View root, float px, float py) {
        String best = null;
        List<View> stack = new ArrayList<View>();
        stack.add(root);
        int guard = 0;
        while (!stack.isEmpty() && guard++ < 400) {
            View v = stack.remove(stack.size() - 1);
            if (v == null || !v.isShown()) continue;
            String text = null;
            if (v instanceof TextView) {
                CharSequence cs = ((TextView) v).getText();
                if (cs != null) text = cs.toString().trim();
            }
            if (text == null || text.length() == 0) {
                text = textOf(v);
                if (text != null) {
                    text = text.trim();
                    String low = text.toLowerCase(Locale.US);
                    if (low.indexOf(" said ") >= 0) text = extractBody(text);
                }
            }
            if (text != null && text.length() > 0 && !isJunk(text) && !isHandle(text)
                    && !isChrome(text.toLowerCase(Locale.US))) {
                if (best == null || text.length() > best.length()) best = text;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                int n = g.getChildCount();
                for (int i = 0; i < n; i++) {
                    try {
                        stack.add(g.getChildAt(i));
                    } catch (Throwable t) {
                    }
                }
            }
        }
        return best;
    }

    /**
     * Reads the text a view draws by reflecting over its fields. Litho/rendercore render the
     * comment body into a plain View (RCTextView/ComponentHost) and expose no getText(), so the
     * CharSequence has to be pulled out of the component state.
     */
    private static CharSequence reflectText(View v) {
        // rendercore draws the text through a real android.text.Layout; Layout.getText() is exactly
        // the CharSequence on screen, even when accessibility reports nothing for the view.
        CharSequence drawn = layoutText(v);
        if (drawn != null) return drawn;
        // rendercore/litho text views expose their CharSequence through getText()-style methods
        // even when accessibility reports nothing (that is the comment body case).
        String[] methods = {"getFullText", "getTextForAccessibility", "getText",
                "getTextContent", "getCharSequence", "getContent"};
        for (int m = 0; m < methods.length; m++) {
            try {
                Method md = v.getClass().getMethod(methods[m]);
                if (md.getParameterTypes().length != 0) continue;
                Object val = md.invoke(v);
                if (val instanceof CharSequence) {
                    CharSequence cs = (CharSequence) val;
                    String s = cs.toString().trim();
                    if (s.length() > 1 && hasLetter(s)) return cs;
                }
            } catch (Throwable t) {
            }
        }
        Class<?> c = v.getClass();
        int guard = 0;
        while (c != null && c != Object.class && guard++ < 8) {
            Field[] fs;
            try {
                fs = c.getDeclaredFields();
            } catch (Throwable t) {
                break;
            }
            for (int i = 0; i < fs.length; i++) {
                Field f = fs[i];
                try {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object val = f.get(v);
                    if (val == null) continue;
                    if (val instanceof CharSequence) {
                        CharSequence cs = (CharSequence) val;
                        if (cs.length() > 0) return cs;
                    } else {
                        String cn = val.getClass().getName();
                        if (cn.indexOf("TextContent") >= 0 || cn.indexOf("Spannable") >= 0
                                || cn.indexOf("TextLayout") >= 0) {
                            String s = String.valueOf(val);
                            if (s != null && s.length() > 1 && hasLetter(s)) return s;
                        }
                    }
                } catch (Throwable t) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * Finds the android.text.Layout a rendercore/litho text view draws with and returns its text.
     * Layout.getText() is exactly what is painted, and it is readable from the app process even
     * when the accessibility node exposes nothing.
     */
    private static CharSequence layoutText(View v) {
        Class<?> c = v.getClass();
        int guard = 0;
        while (c != null && c != Object.class && guard++ < 8) {
            Field[] fs;
            try {
                fs = c.getDeclaredFields();
            } catch (Throwable t) {
                break;
            }
            for (int i = 0; i < fs.length; i++) {
                Field f = fs[i];
                try {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    if (!android.text.Layout.class.isAssignableFrom(f.getType())) continue;
                    f.setAccessible(true);
                    Object val = f.get(v);
                    if (!(val instanceof android.text.Layout)) continue;
                    CharSequence cs = ((android.text.Layout) val).getText();
                    if (cs != null && cs.length() > 1 && hasLetter(cs.toString())) return cs;
                } catch (Throwable t) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /** Recursively collects CharSequence fields from an object graph (bounded). */
    private static void collectStrings(Object root, List<CharSequence> out, int depth, int budget) {
        if (root == null || depth < 0 || budget <= 0 || out.size() > 400) return;
        // Litho mounts text as a drawable stored in SparseArrays and arrays on the host, so those
        // containers must be unwrapped even though they live in android.* packages.
        if (root instanceof android.util.SparseArray) {
            android.util.SparseArray sa = (android.util.SparseArray) root;
            for (int i = 0; i < sa.size() && budget > 0; i++) {
                collectStrings(sa.valueAt(i), out, depth - 1, budget - 1);
            }
            return;
        }
        if (root instanceof Object[]) {
            Object[] arr = (Object[]) root;
            for (int i = 0; i < arr.length && budget > 0; i++) {
                collectStrings(arr[i], out, depth - 1, budget - 1);
            }
            return;
        }
        Class<?> c = root.getClass();
        String cn = c.getName();
        if (cn.startsWith("android.view.") || cn.startsWith("android.widget.")
                || cn.startsWith("android.graphics.") || cn.startsWith("java.lang.")
                || cn.startsWith("java.util.") || cn.startsWith("android.content.")) {
            return;
        }
        int guard = 0;
        while (c != null && c != Object.class && guard++ < 6) {
            Field[] fs;
            try {
                fs = c.getDeclaredFields();
            } catch (Throwable t) {
                break;
            }
            for (int i = 0; i < fs.length; i++) {
                if (out.size() > 400 || budget <= 0) return;
                Field f = fs[i];
                try {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    Class<?> ft = f.getType();
                    f.setAccessible(true);
                    Object val = f.get(root);
                    if (val == null) continue;
                    if (val instanceof CharSequence) {
                        out.add((CharSequence) val);
                    } else if (ft == android.util.SparseArray.class || ft.isArray()) {
                        collectStrings(val, out, depth, budget - 1);
                    } else if (depth > 0) {
                        String vn = val.getClass().getName();
                        if (vn.startsWith("java.") || vn.startsWith("kotlin.")) continue;
                        collectStrings(val, out, depth - 1, budget - 1);
                    }
                } catch (Throwable t) {
                }
            }
            c = c.getSuperclass();
        }
    }

    /** Combined best-effort text of a view (accessibility label first, then reflected fields). */
    private static String textOf(View v) {
        String acc = accessibilityLabel(v);
        if (acc != null) {
            acc = acc.trim();
            if (acc.length() > 0) return acc;
        }
        CharSequence cs = reflectText(v);
        if (cs != null) {
            String s = cs.toString().trim();
            if (s.length() > 0) return s;
        }
        return null;
    }

    private static String accessibilityLabel(View v) {
        AccessibilityNodeInfo info = null;
        try {
            info = AccessibilityNodeInfo.obtain();
            // dispatchInitializeAccessibilityNodeInfo (hidden API, not in the SDK stubs) also runs
            // the view's AccessibilityDelegate - that is where Litho builds the merged
            // "user said body" label. Fall back to the public method when absent.
            boolean done = false;
            try {
                Method m = v.getClass().getMethod("dispatchInitializeAccessibilityNodeInfo",
                        AccessibilityNodeInfo.class);
                m.invoke(v, info);
                done = true;
            } catch (Throwable t) {
            }
            if (!done) v.onInitializeAccessibilityNodeInfo(info);
            CharSequence d = info.getContentDescription();
            if (d != null && d.length() > 0) return d.toString();
            CharSequence t = info.getText();
            if (t != null && t.length() > 0) return t.toString();
        } catch (Throwable t) {
        } finally {
            if (info != null) {
                try {
                    info.recycle();
                } catch (Throwable t) {
                }
            }
        }
        return null;
    }

    private static Activity activity() {
        try {
            WeakReference<Activity> ref = activityRef;
            return ref == null ? null : ref.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<View> roots(Activity activity) {
        List<View> out = new ArrayList<View>();
        try {
            if (activity != null && activity.getWindow() != null) {
                View decor = activity.getWindow().getDecorView();
                if (decor != null) out.add(decor);
            }
        } catch (Throwable t) {
        }
        try {
            Class<?> cls = Class.forName("android.view.WindowManagerGlobal");
            Method getInstance = cls.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            Object global = getInstance.invoke(null);
            Field viewsField = cls.getDeclaredField("mViews");
            viewsField.setAccessible(true);
            Object value = viewsField.get(global);
            if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object o = list.get(i);
                    if (o instanceof View && !out.contains(o)) out.add((View) o);
                }
            }
        } catch (Throwable t) {
            Log.i("DLM", "window roots unavailable: " + t);
        }
        return out;
    }

    private static void collectAll(View v, View skip, List<View> out) {
        if (v == null || v == skip) return;
        List<View> stack = new ArrayList<View>();
        stack.add(v);
        int guard = 0;
        while (!stack.isEmpty() && guard++ < 6000) {
            View cur = stack.remove(stack.size() - 1);
            if (cur == null || cur == skip) continue;
            out.add(cur);
            if (cur instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) cur;
                for (int i = 0; i < g.getChildCount(); i++) {
                    try { stack.add(g.getChildAt(i)); } catch (Throwable t) {}
                }
            }
        }
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) return true;
        }
        return false;
    }

    /**
     * Returns the comment body under the point. The accessibility tree merges "user said text"
     * into a single label, while the real view tree keeps the body in its own text view, so the
     * longest readable text containing the point is the comment.
     */
    private static String scan(List<View> roots, float px, float py, View skip) {
        String fallback = null;
        for (int r = 0; r < roots.size(); r++) {
            List<View> hits = new ArrayList<View>();
            walk(roots.get(r), px, py, skip, hits);

            // First pass: the real comment row carries "user said text". The post behind the
            // sheet also sits under the touch point, so it must never win over this.
            for (int i = 0; i < hits.size(); i++) {
                String label = label(hits.get(i), px, py);
                if (label == null) continue;
                label = label.trim();
                if (isJunk(label)) continue;
                String lower = label.toLowerCase(Locale.US);
                if (lower.indexOf(" said ") < 0) continue;
                String extracted = extractBody(label);
                if (extracted != null && extracted.length() > 0) {
                    Log.i("DLM", "comment label: " + extracted);
                    return extracted;
                }
            }

            // Second pass: fallback for comment rows that expose only the body text.
            // Only trust this inside a window that actually hosts comment rows ("user said ..."),
            // otherwise the feed post caption behind the sheet would be copied instead.
            boolean rootHasSaid = false;
            for (int i = 0; i < hits.size(); i++) {
                String l = label(hits.get(i), px, py);
                if (l != null && l.toLowerCase(Locale.US).indexOf(" said ") >= 0) {
                    rootHasSaid = true;
                    break;
                }
            }
            if (!rootHasSaid) continue;
            for (int i = 0; i < hits.size(); i++) {
                String label = label(hits.get(i), px, py);
                if (label == null) continue;
                label = label.trim();
                if (label.length() < 2) continue;
                if (isJunk(label)) continue;
                String lower = label.toLowerCase(Locale.US);
                if (lower.indexOf(" said ") >= 0) continue;
                if (isChrome(lower)) continue;
                if (isHandle(label)) continue;
                if (fallback == null || label.length() > fallback.length()) {
                    fallback = label;
                }
            }
        }
        return fallback;
    }

    /** Nearest visible "user said body" row to the point, ignoring rows inside the menu. */
    private static String nearestSaid(List<View> roots, float px, float py, View skip) {
        String best = null;
        float bestDist = Float.MAX_VALUE;
        for (int r = 0; r < roots.size(); r++) {
            List<View> all = new ArrayList<View>();
            collectAll(roots.get(r), skip, all);
            for (int i = 0; i < all.size(); i++) {
                View v = all.get(i);
                if (!v.isShown() || v.getWidth() <= 0) continue;
                String label = accessibilityLabel(v);
                if (label == null) continue;
                label = label.trim();
                if (label.toLowerCase(Locale.US).indexOf(" said ") < 0) continue;
                int[] loc = new int[2];
                v.getLocationOnScreen(loc);
                float cy = loc[1] + v.getHeight() / 2f;
                float cx = loc[0] + v.getWidth() / 2f;
                float dy = cy - py;
                float dx = cx - px;
                float d = dy * dy + dx * dx * 0.25f;
                if (d < bestDist) {
                    bestDist = d;
                    best = label;
                }
            }
        }
        if (best != null) return extractBody(best);
        return null;
    }

    private static String extractBody(String s) {
        String lower = s.toLowerCase(Locale.US);
        int i = lower.indexOf(" said ");
        if (i >= 0) {
            String b = s.substring(i + 6).trim();
            if (b.length() > 0) return b;
        }
        return s.trim();
    }
}

/** Adds a Copy comment button to the comment long-press menu. */
final class CommentCopy {
    static void inject(final LinearLayout parent, final Context ctx) {
        try {
            if (parent == null || ctx == null) return;
            if (Ui.hasTag(parent, "DL_COPY_ROW")) return;
            final String text = CommentStore.get();
            if (text == null) {
                Log.i("DLM", "copy comment: no captured text");
                return;
            }

            TextView row = new TextView(ctx);
            row.setText("Copy comment");
            row.setTextSize(16f);
            row.setTypeface(Typeface.DEFAULT_BOLD);
            row.setTextColor(Color.WHITE);
            row.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#1ED760"));
            bg.setCornerRadius(Ui.dp(ctx, 24));
            row.setBackground(bg);
            row.setPadding(Ui.dp(ctx, 24), Ui.dp(ctx, 12), Ui.dp(ctx, 24), Ui.dp(ctx, 12));
            row.setElevation(Ui.dp(ctx, 4));
            row.setTag("DL_COPY_ROW");
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) {
                            cm.setPrimaryClip(ClipData.newPlainText("Instagram comment", text));
                            Toast.makeText(ctx, "Comment copied", Toast.LENGTH_SHORT).show();
                            Log.i("DLM", "comment copied: " + text);
                        }
                    } catch (Throwable t) {
                        Log.e("DLM", "copy comment failed", t);
                    }
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 48));
            lp.leftMargin = Ui.dp(ctx, 24);
            lp.rightMargin = Ui.dp(ctx, 24);
            lp.topMargin = Ui.dp(ctx, 12);
            lp.bottomMargin = Ui.dp(ctx, 8);
            parent.addView(row, 0, lp);

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
            Log.i("DLM", "copy comment row added");
        } catch (Throwable t) {
            Log.e("DLM", "copy comment inject failed", t);
        }
    }
}
