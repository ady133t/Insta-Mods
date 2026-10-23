package com.dl.igmod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

/** Small line icons for the Mod Settings dialog. */
final class SettingsIconDrawable extends Drawable {
    static final int GHOST = 0;
    static final int DOWNLOAD = 1;
    static final int FOLLOWER = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int size;
    private final int kind;

    SettingsIconDrawable(Context context, int kind) {
        this.kind = kind;
        this.size = Ui.dp(context, 24);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, (float) Ui.dp(context, 2)));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.parseColor("#1ED760"));
    }

    static void attach(TextView row, Context context, int kind) {
        row.setCompoundDrawablesWithIntrinsicBounds(new SettingsIconDrawable(context, kind), null, null, null);
        row.setCompoundDrawablePadding(Ui.dp(context, 12));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float x = b.left;
        float y = b.top;
        float w = b.width();
        float h = b.height();
        if (w <= 0 || h <= 0) return;
        path.reset();

        if (kind == GHOST) {
            path.moveTo(x + 0.20f * w, y + 0.82f * h);
            path.lineTo(x + 0.20f * w, y + 0.44f * h);
            path.cubicTo(x + 0.20f * w, y + 0.18f * h, x + 0.35f * w, y + 0.10f * h,
                    x + 0.50f * w, y + 0.10f * h);
            path.cubicTo(x + 0.65f * w, y + 0.10f * h, x + 0.80f * w, y + 0.18f * h,
                    x + 0.80f * w, y + 0.44f * h);
            path.lineTo(x + 0.80f * w, y + 0.82f * h);
            path.cubicTo(x + 0.72f * w, y + 0.73f * h, x + 0.67f * w, y + 0.92f * h,
                    x + 0.58f * w, y + 0.84f * h);
            path.cubicTo(x + 0.53f * w, y + 0.77f * h, x + 0.47f * w, y + 0.92f * h,
                    x + 0.40f * w, y + 0.84f * h);
            path.cubicTo(x + 0.32f * w, y + 0.76f * h, x + 0.27f * w, y + 0.92f * h,
                    x + 0.20f * w, y + 0.82f * h);
            path.close();
            canvas.drawPath(path, paint);

            Paint fill = new Paint(paint);
            fill.setStyle(Paint.Style.FILL);
            fill.setStrokeWidth(0);
            canvas.drawCircle(x + 0.39f * w, y + 0.38f * h, 0.035f * w, fill);
            canvas.drawCircle(x + 0.61f * w, y + 0.38f * h, 0.035f * w, fill);
            canvas.drawOval(x + 0.44f * w, y + 0.50f * h, x + 0.56f * w, y + 0.60f * h, fill);
        } else if (kind == DOWNLOAD) {
            canvas.drawLine(x + 0.50f * w, y + 0.12f * h, x + 0.50f * w, y + 0.62f * h, paint);
            path.moveTo(x + 0.31f * w, y + 0.44f * h);
            path.lineTo(x + 0.50f * w, y + 0.64f * h);
            path.lineTo(x + 0.69f * w, y + 0.44f * h);
            canvas.drawPath(path, paint);
            canvas.drawLine(x + 0.20f * w, y + 0.84f * h, x + 0.80f * w, y + 0.84f * h, paint);
        } else {
            canvas.drawCircle(x + 0.50f * w, y + 0.27f * h, 0.16f * w, paint);
            path.moveTo(x + 0.20f * w, y + 0.82f * h);
            path.cubicTo(x + 0.22f * w, y + 0.55f * h, x + 0.38f * w, y + 0.48f * h,
                    x + 0.50f * w, y + 0.48f * h);
            path.cubicTo(x + 0.62f * w, y + 0.48f * h, x + 0.78f * w, y + 0.55f * h,
                    x + 0.80f * w, y + 0.82f * h);
            canvas.drawPath(path, paint);

            path.reset();
            path.moveTo(x + 0.60f * w, y + 0.68f * h);
            path.lineTo(x + 0.70f * w, y + 0.78f * h);
            path.lineTo(x + 0.88f * w, y + 0.54f * h);
            canvas.drawPath(path, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }
}
