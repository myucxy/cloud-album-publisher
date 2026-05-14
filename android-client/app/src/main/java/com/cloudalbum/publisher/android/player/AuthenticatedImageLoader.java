package com.cloudalbum.publisher.android.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.RequestOptions;
import com.cloudalbum.publisher.android.data.repository.DeviceSessionRepository;

public class AuthenticatedImageLoader {
    private static final int MAX_TEXTURE_SAFE_SIZE = 4096;
    private static final int IMAGE_CORNER_RADIUS_DP = 8;

    public interface Callback {
        void onSuccess();

        void onFailure();
    }

    public static void load(ImageView imageView, String url, DeviceSessionRepository repository) {
        load(imageView, url, repository, null);
    }

    public static void load(ImageView imageView, String url, DeviceSessionRepository repository, final Callback callback) {
        if (url == null || url.trim().isEmpty()) {
            imageView.setImageDrawable(null);
            imageView.setTag(null);
            if (callback != null) {
                callback.onFailure();
            }
            return;
        }
        imageView.setTag(url);
        Drawable currentDrawable = imageView.getDrawable();
        buildRequest(imageView, url, repository)
                .placeholder(currentDrawable)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        if (callback != null) {
                            callback.onFailure();
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                        return false;
                    }
                })
                .into(imageView);
    }

    public static void preload(ImageView imageView, String url, DeviceSessionRepository repository) {
        preload(imageView, url, repository, null);
    }

    public static void preload(ImageView imageView, String url, DeviceSessionRepository repository, final Callback callback) {
        if (url == null || url.trim().isEmpty()) {
            if (callback != null) {
                callback.onFailure();
            }
            return;
        }
        buildRequest(imageView, url, repository)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        if (callback != null) {
                            callback.onFailure();
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                        return false;
                    }
                })
                .preload();
    }

    public static Bitmap decode(byte[] bytes) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private static RequestBuilder<Drawable> buildRequest(ImageView imageView, String url, DeviceSessionRepository repository) {
        int[] targetSize = resolveTargetSize(imageView);
        int cornerRadius = dp(imageView, IMAGE_CORNER_RADIUS_DP);
        RequestBuilder<Drawable> requestBuilder;
        if (isLocalUri(url)) {
            requestBuilder = Glide.with(imageView).load(Uri.parse(url));
        } else {
            String token = repository.getDeviceAccessToken();
            GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                    .addHeader("Authorization", "Bearer " + token)
                    .build());
            requestBuilder = Glide.with(imageView).load(glideUrl);
        }
        return requestBuilder
                .format(DecodeFormat.PREFER_ARGB_8888)
                .downsample(DownsampleStrategy.CENTER_OUTSIDE)
                .override(targetSize[0], targetSize[1])
                .apply(new RequestOptions().transform(new CenterCrop(), new RoundedCorners(cornerRadius)))
                .dontAnimate()
                .diskCacheStrategy(DiskCacheStrategy.ALL);
    }

    private static boolean isLocalUri(String url) {
        return url != null && (url.startsWith("file:") || url.startsWith("content:"));
    }

    private static int dp(ImageView imageView, int value) {
        return Math.max(1, Math.round(value * imageView.getResources().getDisplayMetrics().density));
    }

    private static int[] resolveTargetSize(ImageView imageView) {
        int width = imageView.getWidth();
        int height = imageView.getHeight();
        if (width <= 0) {
            width = imageView.getLayoutParams() == null ? 0 : imageView.getLayoutParams().width;
        }
        if (height <= 0) {
            height = imageView.getLayoutParams() == null ? 0 : imageView.getLayoutParams().height;
        }
        DisplayMetrics metrics = imageView.getResources().getDisplayMetrics();
        if (width <= 0) {
            width = metrics.widthPixels;
        }
        if (height <= 0) {
            height = metrics.heightPixels;
        }
        width = clampDecodeSize(width);
        height = clampDecodeSize(height);
        return new int[] {width, height};
    }

    private static int clampDecodeSize(int value) {
        return Math.max(1, Math.min(value, MAX_TEXTURE_SAFE_SIZE));
    }
}
