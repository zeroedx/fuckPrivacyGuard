package io.github.zeroedx.fuckprivacyguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/**
 * 自绘矢量图标与字符徽标。
 *
 * 图标在 24×24 的设计网格里描边绘制，再等比缩放到控件边界；不引入任何图片资源或
 * 第三方图标库，深浅色切换时重新取色即可。{@link #badge} 用于没有真实应用图标时
 * 生成一个圆角字符徽标（组件图标兜底）。
 */
final class PgIcon extends Drawable {

    enum Glyph {HOME, GRID, PULSE, SLIDERS, INFO, SHIELD, RESTART, CHECK, ARROW}

    private final Glyph glyph;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color;

    PgIcon(Glyph glyph, int color) {
        this.glyph = glyph;
        setColor(color);
    }

    void setColor(int c) {
        color = c;
        paint.setColor(c);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float size = Math.min(b.width(), b.height());
        float scale = size / 24f;
        canvas.save();
        canvas.translate(b.left + (b.width() - size) / 2f, b.top + (b.height() - size) / 2f);
        canvas.scale(scale, scale);

        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);

        Path p = new Path();
        switch (glyph) {
            case HOME:
                p.moveTo(3.5f, 11f);
                p.lineTo(12f, 3.5f);
                p.lineTo(20.5f, 11f);
                canvas.drawPath(p, paint);
                p.reset();
                p.moveTo(6f, 9.6f);
                p.lineTo(6f, 19.5f);
                p.lineTo(18f, 19.5f);
                p.lineTo(18f, 9.6f);
                canvas.drawPath(p, paint);
                break;
            case GRID: {
                float r = 2.2f;
                canvas.drawRoundRect(new RectF(3.5f, 3.5f, 10.8f, 10.8f), r, r, paint);
                canvas.drawRoundRect(new RectF(13.2f, 3.5f, 20.5f, 10.8f), r, r, paint);
                canvas.drawRoundRect(new RectF(3.5f, 13.2f, 10.8f, 20.5f), r, r, paint);
                canvas.drawRoundRect(new RectF(13.2f, 13.2f, 20.5f, 20.5f), r, r, paint);
                break;
            }
            case PULSE:
                p.moveTo(3f, 12f);
                p.lineTo(7f, 12f);
                p.lineTo(9.6f, 6.5f);
                p.lineTo(13.2f, 17.5f);
                p.lineTo(15.6f, 12f);
                p.lineTo(21f, 12f);
                canvas.drawPath(p, paint);
                break;
            case SLIDERS:
                canvas.drawLine(4f, 8.5f, 20f, 8.5f, paint);
                canvas.drawLine(4f, 15.5f, 20f, 15.5f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(9f, 8.5f, 2.7f, paint);
                canvas.drawCircle(15f, 15.5f, 2.7f, paint);
                break;
            case INFO:
                canvas.drawCircle(12f, 12f, 8.6f, paint);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(12f, 7.9f, 1.25f, paint);
                paint.setStyle(Paint.Style.STROKE);
                canvas.drawLine(12f, 11f, 12f, 16.6f, paint);
                break;
            case SHIELD:
                p.moveTo(12f, 3.2f);
                p.lineTo(19f, 6.2f);
                p.lineTo(19f, 12f);
                p.cubicTo(19f, 16.6f, 15.9f, 19.7f, 12f, 21f);
                p.cubicTo(8.1f, 19.7f, 5f, 16.6f, 5f, 12f);
                p.lineTo(5f, 6.2f);
                p.close();
                canvas.drawPath(p, paint);
                break;
            case RESTART: {
                RectF oval = new RectF(4.2f, 4.2f, 19.8f, 19.8f);
                p.addArc(oval, 55f, 265f);
                canvas.drawPath(p, paint);
                p.reset();
                p.moveTo(17.2f, 3.2f);
                p.lineTo(20.6f, 7.4f);
                p.lineTo(15.6f, 8.6f);
                p.close();
                paint.setStyle(Paint.Style.FILL);
                canvas.drawPath(p, paint);
                break;
            }
            case CHECK:
                p.moveTo(5f, 12.8f);
                p.lineTo(10f, 17.8f);
                p.lineTo(19f, 6.4f);
                canvas.drawPath(p, paint);
                break;
            case ARROW:
            default:
                p.moveTo(9.2f, 5f);
                p.lineTo(16f, 12f);
                p.lineTo(9.2f, 19f);
                canvas.drawPath(p, paint);
                break;
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /** 组件图标兜底：圆角方块 + 居中字符 */
    static Drawable badge(Context ctx, String text, int bg, int fg) {
        return new Badge(ctx, text, bg, fg);
    }

    private static final class Badge extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String label;
        private final int bg, fg;

        Badge(Context ctx, String label, int bg, int fg) {
            this.label = label == null || label.isEmpty() ? "?" : label;
            this.bg = bg;
            this.fg = fg;
            text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(fg);
            paint.setColor(bg);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float w = b.width(), hgt = b.height();
            float radius = Math.min(w, hgt) * 0.3f;
            canvas.drawRoundRect(new RectF(b.left, b.top, b.right, b.bottom), radius, radius, paint);
            text.setTextSize(Math.min(w, hgt) * 0.46f);
            Paint.FontMetrics fm = text.getFontMetrics();
            float cy = b.top + hgt / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, b.left + w / 2f, cy, text);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            text.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
            text.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
