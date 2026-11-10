package io.github.zeroedx.fuckprivacyguard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.widget.Switch;

/**
 * MIUI 风格的开关。
 *
 * 系统的 Switch 在这版主题下会被裁成很小的圆角块，轨道与滑块的边界也不清楚，
 * 这里只接管绘制：轨道 49×28dp、滑块直径 20dp、关态用浅灰实底，
 * 触摸、无障碍、checked 状态仍由 Switch 负责。
 */
final class PgSwitch extends Switch {

    /** 需要「点了先不动、等外部确认」的场景用它接管点击 */
    interface OnToggleRequest {
        void onRequest(boolean target);
    }

    private OnToggleRequest toggleRequest;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.animation.ValueAnimator animator;
    /** 0 = 关，1 = 开；滑动动画的插值位置 */
    private float anim;
    private final int onColor;
    private final int offColor;
    private final int thumbColor = 0xFFFFFFFF;
    private final float density;

    PgSwitch(Context context, int onColor, int offColor) {
        super(context);
        this.onColor = onColor;
        this.offColor = offColor;
        this.density = context.getResources().getDisplayMetrics().density;
        setShowText(false);
        setText(null);
        setBackground(null);
        setPadding(0, 0, 0, 0);
        setThumbDrawable(null);
        setTrackDrawable(null);
        anim = isChecked() ? 1f : 0f;
        setMinWidth(Math.round(49 * density));
        setMinHeight(Math.round(28 * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;

        paint.setColor(isChecked() ? onColor : offColor);
        canvas.drawRoundRect(0, 0, w, h, radius, radius, paint);

        // Settings 里的取值：轨道 49×28、滑块 20dp、左右与上下各留 4dp
        float pad = 4 * density;
        float thumbRadius = 10 * density;
        float travel = w - 2 * pad - 2 * thumbRadius;
        float cx = pad + thumbRadius + travel * anim;
        paint.setColor(thumbColor);
        canvas.drawCircle(cx, h / 2f, thumbRadius, paint);
    }

    /** 接管点击：只上报目标状态，自身不切换 */
    public void setOnToggleRequest(OnToggleRequest listener) {
        this.toggleRequest = listener;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (toggleRequest == null) return super.onTouchEvent(event);
        if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP && isEnabled()) {
            performClick();
            toggleRequest.onRequest(!isChecked());
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /** 直接落位，不播放动画：用于回滚与作用域同步 */
    public void setCheckedImmediate(boolean checked) {
        super.setChecked(checked);
        anim = checked ? 1f : 0f;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        invalidate();
    }

    @Override
    public void setChecked(boolean checked) {
        boolean changed = isChecked() != checked;
        super.setChecked(checked);
        if (!changed) {
            invalidate();
            return;
        }
        float target = checked ? 1f : 0f;
        if (!isAttachedToWindow()) {
            anim = target;
            invalidate();
            return;
        }
        if (animator != null) animator.cancel();
        animator = android.animation.ValueAnimator.ofFloat(anim, target);
        animator.setDuration(180);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            anim = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }
}
