package com.dl.igmod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedHelpers;

/**
 * Semi-transparent ghost badge shown at the bottom-right of the story image while
 * anonymous story view is enabled.
 */
final class GhostOverlay {
    private static final Map<View, Holder> ACTIVE = new WeakHashMap<View, Holder>();
    private static final Map<View, Rect> MEDIA_RECTS = new WeakHashMap<View, Rect>();

    static void showFor(Object fragment) {
        if (fragment == null || !Prefs.anon) return;
        try {
            Object v = XposedHelpers.callMethod(fragment, "getView");
            if (v instanceof View) show((View) v);
        } catch (Throwable t) {
        }
    }

    static void hideFor(Object fragment) {
        try {
            Object v = XposedHelpers.callMethod(fragment, "getView");
            if (v instanceof View) {
                hide((View) v);
            } else {
                hideAll();
            }
        } catch (Throwable t) {
            hideAll();
        }
    }

    static void show(final View root) {
        if (root == null || !Prefs.anon) return;
        synchronized (ACTIVE) {
            if (ACTIVE.containsKey(root)) return;
        }
        try {
            final Context ctx = root.getContext();
            final GhostDrawable d = new GhostDrawable();
            final int size = Ui.dp(ctx, 36);
            final int right = Ui.dp(ctx, 16);
            final int bottom = Ui.dp(ctx, 16);

            final Holder h = new Holder(d, null, findMediaView(root));
            h.listener = new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    View found = findMediaView(root);
                    if (found != null) h.media = found;
                    updateBounds(root, d, h.media, size, right, bottom);
                }
            };
            synchronized (ACTIVE) {
                ACTIVE.put(root, h);
            }
            root.addOnLayoutChangeListener(h.listener);
            root.getOverlay().add(d);
            root.post(new Runnable() {
                @Override
                public void run() {
                    View found = findMediaView(root);
                    if (found != null) h.media = found;
                    updateBounds(root, d, h.media, size, right, bottom);
                }
            });
        } catch (Throwable t) {
            Log.e("DLM", "ghost badge failed", t);
        }
    }

    private static void updateBounds(View root, GhostDrawable d, View media, int size, int right, int bottom) {
        try {
            int[] rootLoc = new int[2];
            root.getLocationOnScreen(rootLoc);
            int baseRight;
            int baseBottom;
            if (media != null && media.isShown()) {
                Rect visible = MEDIA_RECTS.get(media);
                if (visible == null || visible.isEmpty()) {
                    visible = new Rect();
                    if (!media.getGlobalVisibleRect(visible)) visible = null;
                }
                if (visible != null && !visible.isEmpty()) {
                    baseRight = visible.right - rootLoc[0];
                    baseBottom = visible.bottom - rootLoc[1];
                } else {
                    int[] mediaLoc = new int[2];
                    media.getLocationOnScreen(mediaLoc);
                    baseRight = mediaLoc[0] - rootLoc[0] + media.getWidth();
                    baseBottom = mediaLoc[1] - rootLoc[1] + media.getHeight();
                }
            } else {
                baseRight = root.getWidth();
                baseBottom = root.getHeight();
            }
            if (baseRight <= 0 || baseBottom <= 0) return;
            int actionTop = findActionTop(root, rootLoc);
            if (actionTop > 0 && actionTop < baseBottom) {
                baseBottom = actionTop;
            }
            int l = baseRight - right - size;
            int t = baseBottom - bottom - size;
            if (l < 0) l = 0;
            if (t < 0) t = 0;
            d.setBounds(l, t, l + size, t + size);
            root.invalidate();
        } catch (Throwable t) {
        }
    }

    /**
     * Top edge (relative to the viewer root) of a small action icon in the lower-right.
     * Some story layouts draw Like/Share over the media instead of below it.
     */
    private static int findActionTop(View root, int[] rootLoc) {
        if (!(root instanceof ViewGroup)) return -1;
        try {
            Context ctx = root.getContext();
            int min = Ui.dp(ctx, 24);
            int max = Ui.dp(ctx, 80);
            int rw = root.getWidth();
            int rh = root.getHeight();
            if (rw <= 0 || rh <= 0) return -1;

            ArrayList<View> stack = new ArrayList<View>();
            ViewGroup rg = (ViewGroup) root;
            for (int i = 0; i < rg.getChildCount(); i++) stack.add(rg.getChildAt(i));

            int best = Integer.MAX_VALUE;
            int guard = 0;
            while (!stack.isEmpty() && guard++ < 3000) {
                View v = stack.remove(stack.size() - 1);
                if (v == null || !v.isShown()) continue;
                if (v instanceof ImageView) {
                    Rect r = new Rect();
                    if (v.getGlobalVisibleRect(r)) {
                        int w = r.width();
                        int h = r.height();
                        int cx = r.centerX() - rootLoc[0];
                        int cy = r.centerY() - rootLoc[1];
                        if (w >= min && h >= min && w <= max && h <= max
                                && cx > rw * 0.6f && cy > rh * 0.5f) {
                            int top = r.top - rootLoc[1];
                            if (top < best) best = top;
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
            return best == Integer.MAX_VALUE ? -1 : best;
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Find the visible story media image/video, ignoring full-screen blur/background layers.
     */
    static View findMediaView(View root) {
        if (root == null || !(root instanceof android.view.ViewGroup)) return null;
        try {
            int rw = root.getWidth();
            int rh = root.getHeight();
            long rootArea = (long) rw * rh;
            int[] rootLoc = new int[2];
            root.getLocationOnScreen(rootLoc);
            int rootBottom = rootLoc[1] + rh;
            int gap = Ui.dp(root.getContext(), 40);
            ArrayList<View> stack = new ArrayList<View>();
            android.view.ViewGroup rg = (android.view.ViewGroup) root;
            for (int i = 0; i < rg.getChildCount(); i++) stack.add(rg.getChildAt(i));

            View contentBest = null;
            long contentArea = 0;
            Rect contentRect = null;
            View mediaBest = null;
            long mediaArea = 0;
            Rect mediaRect = null;
            View fallback = null;
            long fallbackArea = 0;
            Rect fallbackRect = null;
            int guard = 0;
            while (!stack.isEmpty() && guard++ < 3000) {
                View v = stack.remove(stack.size() - 1);
                if (v == null || !v.isShown()) continue;
                Rect r = new Rect();
                if (!v.getGlobalVisibleRect(r)) continue;
                long area = (long) r.width() * r.height();
                if (area <= 0) continue;
                String cn = v.getClass().getName().toLowerCase();
                boolean bad = cn.contains("background") || cn.contains("blur")
                        || cn.contains("scrim") || cn.contains("progress");
                boolean named = cn.contains("surfaceview") || cn.contains("textureview")
                        || cn.contains("imageview") || cn.contains("videoview")
                        || cn.contains("igimage") || cn.contains("photo");
                if (!bad) {
                    if (area > rootArea / 4 && r.width() >= rw * 0.7f && r.height() >= rh * 0.4f
                            && r.bottom <= rootBottom - gap && area > contentArea) {
                        contentBest = v;
                        contentArea = area;
                        contentRect = new Rect(r);
                    }
                    if (named && area > rootArea / 8 && area < rootArea * 0.985 && area > mediaArea) {
                        mediaBest = v;
                        mediaArea = area;
                        mediaRect = new Rect(r);
                    }
                    if (area > fallbackArea) {
                        fallback = v;
                        fallbackArea = area;
                        fallbackRect = new Rect(r);
                    }
                }
                if (v instanceof android.view.ViewGroup) {
                    android.view.ViewGroup g = (android.view.ViewGroup) v;
                    for (int i = 0; i < g.getChildCount(); i++) {
                        try {
                            View c = g.getChildAt(i);
                            if (c != null) stack.add(c);
                        } catch (Throwable t) {
                        }
                    }
                }
            }
            if (contentBest != null) {
                if (contentRect != null) MEDIA_RECTS.put(contentBest, contentRect);
                return contentBest;
            }
            if (mediaBest != null) {
                if (mediaRect != null) MEDIA_RECTS.put(mediaBest, mediaRect);
                return mediaBest;
            }
            if (fallback != null && fallbackArea > rootArea / 3) {
                if (fallbackRect != null) MEDIA_RECTS.put(fallback, fallbackRect);
                return fallback;
            }
        } catch (Throwable t) {
        }
        return null;
    }

    static void hide(View root) {
        if (root == null) return;
        Holder h;
        synchronized (ACTIVE) {
            h = ACTIVE.remove(root);
        }
        if (h != null) {
            try {
                root.removeOnLayoutChangeListener(h.listener);
            } catch (Throwable t) {
            }
            try {
                root.getOverlay().remove(h.drawable);
            } catch (Throwable t) {
            }
            try {
                root.invalidate();
            } catch (Throwable t) {
            }
        }
    }

    static void hideAll() {
        List<View> keys;
        synchronized (ACTIVE) {
            keys = new ArrayList<View>(ACTIVE.keySet());
        }
        for (View v : keys) hide(v);
    }

    private static final class Holder {
        final GhostDrawable drawable;
        View.OnLayoutChangeListener listener;
        View media;

        Holder(GhostDrawable drawable, View.OnLayoutChangeListener listener, View media) {
            this.drawable = drawable;
            this.listener = listener;
            this.media = media;
        }
    }
}

final class GhostDrawable extends Drawable {
    private final Paint stroke;
    private final Paint back;
    private final Paint fill;

    GhostDrawable() {
        stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2.0f);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(Color.WHITE);
        stroke.setAlpha(150);

        back = new Paint(Paint.ANTI_ALIAS_FLAG);
        back.setStyle(Paint.Style.STROKE);
        back.setStrokeWidth(4.0f);
        back.setStrokeJoin(Paint.Join.ROUND);
        back.setStrokeCap(Paint.Cap.ROUND);
        back.setColor(Color.BLACK);
        back.setAlpha(55);

        fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.WHITE);
        fill.setAlpha(170);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float x = b.left;
        float y = b.top;
        float w = b.width();
        float h = b.height();
        if (w <= 0 || h <= 0) return;

        stroke.setStrokeWidth(Math.max(1.5f, w * 0.055f));
        back.setStrokeWidth(stroke.getStrokeWidth() + 2.0f);

        Path p = new Path();
        p.moveTo(x + 0.18f * w, y + 0.87f * h);
        p.lineTo(x + 0.18f * w, y + 0.47f * h);
        p.cubicTo(x + 0.18f * w, y + 0.20f * h, x + 0.34f * w, y + 0.10f * h, x + 0.50f * w, y + 0.10f * h);
        p.cubicTo(x + 0.66f * w, y + 0.10f * h, x + 0.82f * w, y + 0.20f * h, x + 0.82f * w, y + 0.47f * h);
        p.lineTo(x + 0.82f * w, y + 0.87f * h);
        p.cubicTo(x + 0.74f * w, y + 0.77f * h, x + 0.69f * w, y + 0.98f * h, x + 0.60f * w, y + 0.88f * h);
        p.cubicTo(x + 0.54f * w, y + 0.80f * h, x + 0.49f * w, y + 0.98f * h, x + 0.41f * w, y + 0.88f * h);
        p.cubicTo(x + 0.34f * w, y + 0.80f * h, x + 0.27f * w, y + 0.98f * h, x + 0.18f * w, y + 0.87f * h);
        p.close();

        canvas.drawPath(p, back);
        canvas.drawPath(p, stroke);

        float er = 0.045f * w;
        canvas.drawCircle(x + 0.39f * w, y + 0.40f * h, er, fill);
        canvas.drawCircle(x + 0.61f * w, y + 0.40f * h, er, fill);
        canvas.drawOval(new RectF(x + 0.44f * w, y + 0.52f * h, x + 0.56f * w, y + 0.64f * h), fill);
    }

    @Override
    public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        fill.setAlpha(Math.min(255, alpha + 20));
        back.setAlpha(Math.max(30, alpha / 3));
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        stroke.setColorFilter(colorFilter);
        fill.setColorFilter(colorFilter);
        back.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
