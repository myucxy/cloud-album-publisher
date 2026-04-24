package com.cloudalbum.publisher.android.player;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.cloudalbum.publisher.android.data.repository.DeviceSessionRepository;

public class AuthenticatedImageLoader {
    public interface Callback {
        void onSuccess();

        void onFailure();
    }

    public static void load(ImageView imageView, String url, DeviceSessionRepository repository) {
        if (url == null || url.trim().isEmpty()) {
            imageView.setImageDrawable(null);
            return;
        }
        Drawable currentDrawable = imageView.getDrawable();
        buildRequest(imageView, url, repository)
                .placeholder(currentDrawable)
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
        String token = repository.getDeviceAccessToken();
        GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                .addHeader("Authorization", "Bearer " + token)
                .build());
        return Glide.with(imageView)
                .load(glideUrl)
                .format(DecodeFormat.PREFER_ARGB_8888)
                .fitCenter()
                .dontAnimate()
                .diskCacheStrategy(DiskCacheStrategy.ALL);
    }
}
