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
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

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
    private static final int[] RETRY_MS = {0, 350, 900, 1800, 3200};

    private FollowerBadge() {
    }

    static void refresh(final Object fragment) {
        if (fragment == null) return;
        for (final int delay : RETRY_MS) {
            Module.MAIN.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        install(fragment);
                    } catch (Throwable t) {
                    }
                }
            }, delay);
        }
    }

    private static void install(Object fragment) {
        View root;
        Object user;
        try {
            Object v = XposedHelpers.callMethod(fragment, "getView");
            if (!(v instanceof View)) return;
            root = (View) v;
            if (root.getWidth() <= 0) return;

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
            if (title == null) return;

            TextView badge = findBadge(root);
            if (badge != null) {
                style(badge, follower);
                return;
            }

            attachNextToTitle(title, root, follower);
        } catch (Throwable t) {
            Log.e(TAG, "follower badge failed", t);
        }
    }

    private static void attachNextToTitle(TextView title, View root, boolean follower) {
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

    private static TextView createBadge(Context ctx) {
        TextView badge = new TextView(ctx);
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
