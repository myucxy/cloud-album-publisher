package com.cloudalbum.publisher.android.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

public class RoundedImageView extends AppCompatImageView {
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private float cornerRadiusPx = 0f;
    private int lastWidth = -1;
    private int lastHeight = -1;

    public RoundedImageView(Context context) {
        super(context);
        init();
    }

    public RoundedImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RoundedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setCornerRadiusDp(8);
    }

    public void setCornerRadiusDp(int radiusDp) {
        cornerRadiusPx = Math.max(0f, radiusDp * getResources().getDisplayMetrics().density);
        rebuildPath(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        rebuildPath(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isBitmapRecycled()) {
            setImageDrawable(null);
            return;
        }
        if (cornerRadiusPx <= 0f || getWidth() <= 0 || getHeight() <= 0) {
            super.onDraw(canvas);
            return;
        }
        int save = canvas.save();
        canvas.clipPath(clipPath);
        super.onDraw(canvas);
        canvas.restoreToCount(save);
    }

    private boolean isBitmapRecycled() {
        Drawable drawable = getDrawable();
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            return bitmap != null && bitmap.isRecycled();
        }
        return false;
    }

    private void rebuildPath(int width, int height) {
        if (width <= 0 || height <= 0 || (width == lastWidth && height == lastHeight)) {
            return;
        }
        lastWidth = width;
        lastHeight = height;
        rect.set(0f, 0f, width, height);
        clipPath.reset();
        clipPath.addRoundRect(rect, cornerRadiusPx, cornerRadiusPx, Path.Direction.CW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            clipPath.close();
        }
    }
}
