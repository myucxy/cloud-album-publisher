package com.cloudalbum.publisher.android.player;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Crops a bitmap based on focal point coordinates.
 * The focal point (x, y) is in normalized coordinates (0.0-1.0).
 * The crop will be centered on the focal point while maintaining the target aspect ratio.
 */
public class FocalCropTransformation extends BitmapTransformation {

    private static final String ID = "com.cloudalbum.publisher.android.player.FocalCropTransformation";
    private static final byte[] ID_BYTES = ID.getBytes(StandardCharsets.UTF_8);

    private final float focalX;
    private final float focalY;
    private final float regionWidth;
    private final float regionHeight;

    /**
     * @param focalX Normalized X coordinate (0.0-1.0)
     * @param focalY Normalized Y coordinate (0.0-1.0)
     * @param regionWidth Normalized region width (0.0-1.0, 0 means use full image)
     * @param regionHeight Normalized region height (0.0-1.0, 0 means use full image)
     */
    public FocalCropTransformation(float focalX, float focalY, float regionWidth, float regionHeight) {
        this.focalX = Math.max(0f, Math.min(1f, focalX));
        this.focalY = Math.max(0f, Math.min(1f, focalY));
        this.regionWidth = Math.max(0f, Math.min(1f, regionWidth));
        this.regionHeight = Math.max(0f, Math.min(1f, regionHeight));
    }

    @Override
    protected Bitmap transform(@NonNull BitmapPool pool, @NonNull Bitmap toTransform, int outWidth, int outHeight) {
        int srcWidth = toTransform.getWidth();
        int srcHeight = toTransform.getHeight();

        if (srcWidth == 0 || srcHeight == 0 || outWidth == 0 || outHeight == 0) {
            return toTransform;
        }

        float targetRatio = (float) outWidth / outHeight;
        float srcRatio = (float) srcWidth / srcHeight;

        int cropWidth, cropHeight;
        if (srcRatio > targetRatio) {
            // Source is wider than target - crop width
            cropHeight = srcHeight;
            cropWidth = (int) (srcHeight * targetRatio);
        } else {
            // Source is taller than target - crop height
            cropWidth = srcWidth;
            cropHeight = (int) (srcWidth / targetRatio);
        }

        // Ensure crop doesn't exceed source
        cropWidth = Math.min(cropWidth, srcWidth);
        cropHeight = Math.min(cropHeight, srcHeight);

        // Calculate crop position centered on focal point
        float focalPixelX = focalX * srcWidth;
        float focalPixelY = focalY * srcHeight;

        int cropLeft = (int) (focalPixelX - cropWidth / 2f);
        int cropTop = (int) (focalPixelY - cropHeight / 2f);

        // Clamp to image bounds
        cropLeft = Math.max(0, Math.min(cropLeft, srcWidth - cropWidth));
        cropTop = Math.max(0, Math.min(cropTop, srcHeight - cropHeight));

        // If we have a defined region, try to keep crop within it
        if (regionWidth > 0 && regionHeight > 0) {
            float regionCenterX = focalPixelX;
            float regionCenterY = focalPixelY;
            float regionPixelWidth = regionWidth * srcWidth;
            float regionPixelHeight = regionHeight * srcHeight;

            // Expand crop to include the region if possible
            int expandedWidth = Math.max(cropWidth, (int) regionPixelWidth);
            int expandedHeight = Math.max(cropHeight, (int) regionPixelHeight);

            // Maintain aspect ratio
            float expandedRatio = (float) expandedWidth / expandedHeight;
            if (expandedRatio > targetRatio) {
                expandedWidth = (int) (expandedHeight * targetRatio);
            } else {
                expandedHeight = (int) (expandedWidth / targetRatio);
            }

            // Clamp to source size
            expandedWidth = Math.min(expandedWidth, srcWidth);
            expandedHeight = Math.min(expandedHeight, srcHeight);

            // Recalculate if we expanded
            if (expandedWidth > cropWidth || expandedHeight > cropHeight) {
                cropWidth = expandedWidth;
                cropHeight = expandedHeight;
                cropLeft = (int) (focalPixelX - cropWidth / 2f);
                cropTop = (int) (focalPixelY - cropHeight / 2f);
                cropLeft = Math.max(0, Math.min(cropLeft, srcWidth - cropWidth));
                cropTop = Math.max(0, Math.min(cropTop, srcHeight - cropHeight));
            }
        }

        Bitmap cropped = Bitmap.createBitmap(toTransform, cropLeft, cropTop, cropWidth, cropHeight);

        // Scale to output size if needed
        if (cropped.getWidth() != outWidth || cropped.getHeight() != outHeight) {
            float scaleX = (float) outWidth / cropped.getWidth();
            float scaleY = (float) outHeight / cropped.getHeight();
            float scale = Math.max(scaleX, scaleY);

            Matrix matrix = new Matrix();
            matrix.setScale(scale, scale, cropped.getWidth() / 2f, cropped.getHeight() / 2f);

            Bitmap scaled = Bitmap.createBitmap(cropped, 0, 0, cropped.getWidth(), cropped.getHeight(), matrix, true);
            if (scaled != cropped) {
                cropped.recycle();
            }

            // Center crop to exact output size
            int finalLeft = (scaled.getWidth() - outWidth) / 2;
            int finalTop = (scaled.getHeight() - outHeight) / 2;
            Bitmap result = Bitmap.createBitmap(scaled, finalLeft, finalTop, outWidth, outHeight);
            if (result != scaled) {
                scaled.recycle();
            }
            return result;
        }

        return cropped;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FocalCropTransformation that = (FocalCropTransformation) o;
        return Float.compare(that.focalX, focalX) == 0
                && Float.compare(that.focalY, focalY) == 0
                && Float.compare(that.regionWidth, regionWidth) == 0
                && Float.compare(that.regionHeight, regionHeight) == 0;
    }

    @Override
    public int hashCode() {
        int result = Float.floatToIntBits(focalX);
        result = 31 * result + Float.floatToIntBits(focalY);
        result = 31 * result + Float.floatToIntBits(regionWidth);
        result = 31 * result + Float.floatToIntBits(regionHeight);
        return result;
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ID_BYTES);
        messageDigest.update(floatToBytes(focalX));
        messageDigest.update(floatToBytes(focalY));
        messageDigest.update(floatToBytes(regionWidth));
        messageDigest.update(floatToBytes(regionHeight));
    }

    private byte[] floatToBytes(float value) {
        int bits = Float.floatToIntBits(value);
        return new byte[]{
                (byte) (bits >> 24),
                (byte) (bits >> 16),
                (byte) (bits >> 8),
                (byte) bits
        };
    }
}
