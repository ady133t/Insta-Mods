package com.dl.igmod;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedHelpers;

/**
 * Adds a Follower / Not Follower pill beside the username on a profile.
 *
 * <p>UserDetailViewModel.A01 is the viewed User. The followed_by flag comes from
 * User.A1H() (RelationshipInfoDict).CGs(), with User.A2P() as a fallback.</p>
 */
final class FollowerBadge {
    private static final String TAG = "IG_FOLLOWER_BADGE";
    private static final String BADGE_TAG = "IG_FOLLOWER_BADGE";
    private static final Map<View, Boolean> SHIFTED = new WeakHashMap<View, Boolean>();
    private static final Map<View, Boolean> HOSTS = new WeakHashMap<View, Boolean>();
    private static WeakReference<Object> LAST_FRAGMENT = new WeakReference<Object>(null);
    private static final int[] RETRY_MS = {0, 350, 900, 1800, 3200};

    private FollowerBadge() {
    }

    static void refresh(final Object fragment) {
        if (fragment == null) return;
        LAST_FRAGMENT = new WeakReference<Object>(fragment);
        if (!Prefs.follower) {
            hideAll();
            return;
        }
        for (int i = 0; i < RETRY_MS.length; i++) {
            final int delay = RETRY_MS[i];
            final boolean allowFallback = i == RETRY_MS.length - 1;
            Module.MAIN.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        install(fragment, allowFallback);
                    } catch (Throwable t) {
                    }
                }
            }, delay);
        }
    }

    private static void install(Object fragment, boolean allowFallback) {
        View root;
        Object user;
        try {
            Object v = XposedHelpers.callMethod(fragment, "getView");
            if (!(v instanceof View)) return;
            root = (View) v;
            if (root.getWidth() <= 0) return;
            if (!Prefs.follower) {
                removeBadges(root);
                return;
            }

            Object vm = XposedHelpers.getObjectField(fragment, "A0c");
            if (vm == null) return;
            user = XposedHelpers.getObjectField(vm, "A01");
            if (user == null) return;
        } catch (Throwable t) {
            return;
        }

        try {
            // Own profile shows Edit profile instead of Follow.
            if (hasText(root, "Edit profile")) {
                removeBadges(root);
                return;
            }

            Object followed = null;
            try {
                Object relationship = XposedHelpers.callMethod(user, "A1H");
                if (relationship != null) {
                    followed = XposedHelpers.callMethod(relationship, "CGs");
                }
            } catch (Throwable t) {
            }
            if (followed == null) {
                try {
                    followed = XposedHelpers.callMethod(user, "A2P");
                } catch (Throwable t) {
                }
            }

            boolean follower = Boolean.TRUE.equals(followed);
            TextView title = findToolbarTitle(root);
            if (title != null) attachNextToTitle(title, root, follower);
        } catch (Throwable t) {
            Log.e(TAG, "follower badge failed", t);
        }
    }

    private static View findProfilePhoto(View root) {
        try {
            int[] rootLoc = new int[2];
            root.getLocationOnScreen(rootLoc);
            int min = Ui.dp(root.getContext(), 60);
            int max = Ui.dp(root.getContext(), 130);
            ArrayDeque<View> q = new ArrayDeque<View>();
            q.add(root);
            View best = null;
            long bestArea = 0;
            int guard = 0;
            while (!q.isEmpty() && guard++ < 5000) {
                View v = q.poll();
                if (v == null || !v.isShown()) continue;
                CharSequence desc = v.getContentDescription();
                if (desc != null && desc.toString().toLowerCase().contains("profile")) return v;
                if (v instanceof ViewGroup && hasPhotoChild((ViewGroup) v)) {
                    Rect r = new Rect();
                    if (v.getGlobalVisibleRect(r)) {
                        int relTop = r.top - rootLoc[1];
                        int relLeft = r.left - rootLoc[0];
                        int w = r.width();
                        int h = r.height();
                        if (w >= min && h >= min && w <= max && h <= max
                                && relTop < root.getHeight() / 3
                                && relLeft < root.getWidth() / 2
                                && Math.abs(w - h) <= Math.max(w, h) / 4) {
                            long area = (long) w * h;
                            if (area > bestArea) {
                                best = v;
                                bestArea = area;
                            }
                        }
                    }
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
            return best;
        } catch (Throwable t) {
        }
        return null;
    }

    private static boolean hasPhotoChild(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            try {
                View c = group.getChildAt(i);
                if (c instanceof ImageView || c instanceof android.widget.Button) return true;
            } catch (Throwable t) {
            }
        }
        return false;
    }

    private static boolean attachBelowPhoto(View photo, View root, boolean follower, TextView existing) {
        ViewGroup header = null;
        ViewParent p = photo.getParent();
        for (int depth = 0; depth < 6 && p instanceof ViewGroup; depth++) {
            ViewGroup g = (ViewGroup) p;
            if (g.getWidth() >= root.getWidth() / 2
                    && g.getHeight() < root.getHeight() / 2
                    && g.getHeight() >= photo.getHeight() + Ui.dp(root.getContext(), 20)) {
                header = g;
                break;
            }
            p = g.getParent();
        }
        if (header == null) return false;

        if (existing != null) {
            if (existing.getParent() == header) {
                style(existing, follower);
                positionAbovePhoto(header, photo, existing);
                shiftHeader(header, existing);
                fixCategoryOverlap(header, photo);
                return true;
            }
            removeBadges(root);
        }

        try {
            final ViewGroup parent = header;
            final TextView badge = createBadge(photo.getContext());
            parent.addView(badge, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            style(badge, follower);
            positionAbovePhoto(parent, photo, badge);
            shiftHeader(parent, badge);
            fixCategoryOverlap(parent, photo);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void positionAbovePhoto(ViewGroup parent, View photo, TextView badge) {
        try {
            int w = badge.getMeasuredWidth();
            if (w <= 0) {
                badge.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                w = badge.getMeasuredWidth();
            }
            int[] pl = new int[2];
            int[] gl = new int[2];
            photo.getLocationOnScreen(pl);
            parent.getLocationOnScreen(gl);
            float x = pl[0] - gl[0] + (photo.getWidth() - w) / 2f;
            badge.setX(x);
            badge.setY(Ui.dp(parent.getContext(), 4));
        } catch (Throwable t) {
        }
    }

    private static void fixCategoryOverlap(ViewGroup parent, View photo) {
        try {
            Rect pr = new Rect();
            if (!photo.getGlobalVisibleRect(pr)) return;
            TextView best = null;
            int bestLen = 0;
            ArrayDeque<View> q = new ArrayDeque<View>();
            q.add(parent);
            int guard = 0;
            while (!q.isEmpty() && guard++ < 4000) {
                View v = q.poll();
                if (v == null || !v.isShown()) continue;
                if (v instanceof TextView && !BADGE_TAG.equals(String.valueOf(v.getTag()))) {
                    CharSequence raw = ((TextView) v).getText();
                    String s = raw == null ? "" : raw.toString().trim();
                    if (s.length() > 0 && !"New".equalsIgnoreCase(s)
                            && !"Follow".equalsIgnoreCase(s) && !"Following".equalsIgnoreCase(s)) {
                        Rect r = new Rect();
                        if (v.getGlobalVisibleRect(r) && Rect.intersects(r, pr)
                                && r.top > pr.centerY() && s.length() > bestLen) {
                            best = (TextView) v;
                            bestLen = s.length();
                        }
                    }
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
            if (best != null) {
                Rect r = new Rect();
                if (best.getGlobalVisibleRect(r)) {
                    int delta = pr.bottom + Ui.dp(parent.getContext(), 4) - r.top;
                    if (delta > 0) best.setTranslationY(best.getTranslationY() + delta);
                }
            }
        } catch (Throwable t) {
        }
    }

    private static void shiftHeader(ViewGroup parent, TextView badge) {
        try {
            synchronized (SHIFTED) {
                if (SHIFTED.containsKey(parent)) return;
            }
            int h = badge.getMeasuredHeight();
            if (h <= 0) {
                badge.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                h = badge.getMeasuredHeight();
            }
            int shift = h + Ui.dp(parent.getContext(), 8);
            for (int i = 0; i < parent.getChildCount(); i++) {
                View c = parent.getChildAt(i);
                if (c == null || c == badge) continue;
                c.setTranslationY(c.getTranslationY() + shift);
            }
            ViewGroup.LayoutParams lp = parent.getLayoutParams();
            if (lp != null) {
                lp.height = parent.getHeight() + shift;
                parent.setLayoutParams(lp);
            }
            parent.setClipChildren(true);
            synchronized (SHIFTED) {
                SHIFTED.put(parent, Boolean.TRUE);
            }
            parent.requestLayout();
        } catch (Throwable t) {
        }
    }

    private static void attachNextToTitle(TextView title, View root, boolean follower) {
        HOSTS.put(root, Boolean.TRUE);
        TextView existing = findBadge(root);
        if (existing != null) {
            style(existing, follower);
            return;
        }
        View child = title;
        ViewParent p = title.getParent();
        for (int depth = 0; depth < 6 && p instanceof View; depth++) {
            if (p instanceof LinearLayout
                    && ((LinearLayout) p).getOrientation() == LinearLayout.HORIZONTAL) {
                LinearLayout row = (LinearLayout) p;
                int index = row.indexOfChild(child);
                if (index < 0) return;

                Context ctx = title.getContext();
                TextView badge = createBadge(ctx);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.leftMargin = Ui.dp(ctx, 7);
                lp.gravity = Gravity.CENTER_VERTICAL;
                row.addView(badge, index + 1, lp);
                style(badge, follower);
                return;
            }
            child = (View) p;
            p = child.getParent();
        }
    }

    static void hideAll() {
        List<View> roots;
        synchronized (HOSTS) {
            roots = new ArrayList<View>(HOSTS.keySet());
            HOSTS.clear();
        }
        for (View v : roots) removeBadges(v);
    }

    static void refreshLast() {
        if (!Prefs.follower) return;
        Object f = LAST_FRAGMENT.get();
        if (f != null) refresh(f);
    }

    private static TextView createBadge(Context ctx) {
        TextView badge = new TextView(ctx);
        badge.setId(View.generateViewId());
        badge.setTag(BADGE_TAG);
        badge.setTextSize(11f);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        badge.setSingleLine(true);
        badge.setPadding(Ui.dp(ctx, 8), Ui.dp(ctx, 3), Ui.dp(ctx, 8), Ui.dp(ctx, 3));
        badge.setClickable(false);
        badge.setFocusable(false);
        return badge;
    }

    private static void style(TextView badge, boolean follower) {
        Context ctx = badge.getContext();
        boolean dark = (ctx.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        GradientDrawable bg = new GradientDrawable();
        if (follower) {
            // Follow the app theme: white pill in dark mode, dark pill in light mode.
            bg.setColor(dark ? Color.WHITE : Color.BLACK);
            badge.setTextColor(dark ? Color.BLACK : Color.WHITE);
        } else {
            bg.setColor(dark ? Color.parseColor("#2C2C2E") : Color.parseColor("#E5E5EA"));
            badge.setTextColor(dark ? Color.WHITE : Color.BLACK);
        }
        bg.setCornerRadius(Ui.dp(ctx, 10));
        badge.setBackground(bg);
        badge.setText(follower ? "Follower" : "Not Follower");
    }

    private static TextView findBadge(View root) {
        try {
            ArrayDeque<View> q = new ArrayDeque<View>();
            q.add(root);
            int guard = 0;
            while (!q.isEmpty() && guard++ < 5000) {
                View v = q.poll();
                if (v == null) continue;
                if (v instanceof TextView && BADGE_TAG.equals(String.valueOf(v.getTag()))) {
                    return (TextView) v;
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
        }
        return null;
    }

    private static void removeBadges(View root) {
        try {
            ArrayDeque<View> q = new ArrayDeque<View>();
            q.add(root);
            List<View> remove = new ArrayList<View>();
            int guard = 0;
            while (!q.isEmpty() && guard++ < 5000) {
                View v = q.poll();
                if (v == null) continue;
                if (v instanceof TextView && BADGE_TAG.equals(String.valueOf(v.getTag()))) {
                    remove.add(v);
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
            for (View v : remove) {
                try {
                    ViewParent p = v.getParent();
                    if (p instanceof ViewGroup) ((ViewGroup) p).removeView(v);
                } catch (Throwable t) {
                }
            }
        } catch (Throwable t) {
        }
    }

    private static boolean hasText(View root, String text) {
        if (root == null || text == null) return false;
        try {
            ArrayDeque<View> q = new ArrayDeque<View>();
            q.add(root);
            int guard = 0;
            while (!q.isEmpty() && guard++ < 5000) {
                View v = q.poll();
                if (v == null || !v.isShown()) continue;
                if (v instanceof TextView) {
                    CharSequence raw = ((TextView) v).getText();
                    if (raw != null && text.equalsIgnoreCase(raw.toString().trim())) return true;
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
        }
        return false;
    }

    private static TextView findToolbarTitle(View root) {
        try {
            int maxTop = root.getHeight() / 5;
            ArrayDeque<View> q = new ArrayDeque<View>();
            q.add(root);
            TextView best = null;
            int bestTop = Integer.MAX_VALUE;
            int guard = 0;
            while (!q.isEmpty() && guard++ < 5000) {
                View v = q.poll();
                if (v == null || !v.isShown()) continue;
                if (v instanceof TextView) {
                    CharSequence raw = ((TextView) v).getText();
                    String s = raw == null ? "" : raw.toString().trim();
                    if (s.length() > 0 && !"Edit profile".equalsIgnoreCase(s)) {
                        Rect r = new Rect();
                        if (v.getGlobalVisibleRect(r) && r.top <= maxTop && r.top < bestTop) {
                            best = (TextView) v;
                            bestTop = r.top;
                        }
                    }
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
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

}
