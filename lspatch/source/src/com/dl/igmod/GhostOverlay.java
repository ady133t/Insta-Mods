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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.XposedHelpers;

/**
 * Semi-transparent ghost badge shown at the bottom-right of the story viewer while
 * anonymous story view is enabled.
 */
final class GhostOverlay {
    private static final Map<View, Holder> ACTIVE = new WeakHashMap<View, Holder>();

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
            final int bottom = Ui.dp(ctx, 96);

            View.OnLayoutChangeListener listener = new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    updateBounds(root, d, size, right, bottom);
                }
            };
            Holder h = new Holder(d, listener);
            synchronized (ACTIVE) {
                ACTIVE.put(root, h);
            }
            root.addOnLayoutChangeListener(listener);
            root.getOverlay().add(d);
            root.post(new Runnable() {
                @Override
                public void run() {
                    updateBounds(root, d, size, right, bottom);
                }
            });
            Log.i("DLM", "ghost badge shown");
        } catch (Throwable t) {
            Log.e("DLM", "ghost badge failed", t);
        }
    }

    private static void updateBounds(View root, GhostDrawable d, int size, int right, int bottom) {
        try {
            int w = root.getWidth();
            int h = root.getHeight();
            if (w <= 0 || h <= 0) return;
            int left = w - size - right;
            int top = h - size - bottom;
            if (left < 0) left = 0;
            if (top < 0) top = 0;
            d.setBounds(left, top, left + size, top + size);
            root.invalidate();
        } catch (Throwable t) {
        }
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
        final View.OnLayoutChangeListener listener;

        Holder(GhostDrawable drawable, View.OnLayoutChangeListener listener) {
            this.drawable = drawable;
            this.listener = listener;
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

        // Scale the stroke to the icon size.
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
