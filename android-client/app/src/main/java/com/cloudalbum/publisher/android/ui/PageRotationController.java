package com.cloudalbum.publisher.android.ui;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.cloudalbum.publisher.android.data.repository.DeviceSessionRepository;

public class PageRotationController {
    private final Activity activity;
    private final View rootView;

    public PageRotationController(Activity activity, View rootView) {
        this.activity = activity;
        this.rootView = rootView;
    }

    public void apply(String rotationMode) {
        if (activity == null || rootView == null) {
            return;
        }
        int requestedOrientation = DeviceSessionRepository.PLAYBACK_ROTATION_AUTO.equals(rotationMode)
                ? ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                : ActivityInfo.SCREEN_ORIENTATION_LOCKED;
        if (activity.getRequestedOrientation() != requestedOrientation) {
            activity.setRequestedOrientation(requestedOrientation);
        }
        rootView.post(new Runnable() {
            @Override
            public void run() {
                applyTransform(rotationMode);
            }
        });
    }

    private void applyTransform(String rotationMode) {
        if (DeviceSessionRepository.PLAYBACK_ROTATION_AUTO.equals(rotationMode)) {
            resetAutoLayout();
            return;
        }
        float rotation = resolveRotationDegrees(rotationMode);
        int parentWidth = resolveAvailableWidth();
        int parentHeight = resolveAvailableHeight();
        if (parentWidth <= 0 || parentHeight <= 0) {
            return;
        }

        boolean portraitCanvas = rotation == 90f || rotation == 270f;
        int targetWidth = portraitCanvas ? parentHeight : parentWidth;
        int targetHeight = portraitCanvas ? parentWidth : parentHeight;
        updateLayoutParams(targetWidth, targetHeight);

        rootView.setPivotX(targetWidth / 2f);
        rootView.setPivotY(targetHeight / 2f);
        rootView.setRotation(rotation);
        rootView.setScaleX(1f);
        rootView.setScaleY(1f);
        rootView.setTranslationX(0f);
        rootView.setTranslationY(0f);
        rootView.requestLayout();
    }

    private void resetAutoLayout() {
        ViewGroup.LayoutParams params = rootView.getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        } else {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        if (params instanceof FrameLayout.LayoutParams) {
            ((FrameLayout.LayoutParams) params).gravity = Gravity.CENTER;
        }
        rootView.setLayoutParams(params);
        rootView.setPivotX(rootView.getWidth() / 2f);
        rootView.setPivotY(rootView.getHeight() / 2f);
        rootView.setRotation(0f);
        rootView.setScaleX(1f);
        rootView.setScaleY(1f);
        rootView.setTranslationX(0f);
        rootView.setTranslationY(0f);
        rootView.requestLayout();
    }

    private int resolveAvailableWidth() {
        View parent = (View) rootView.getParent();
        if (parent != null && parent.getWidth() > 0) {
            return parent.getWidth();
        }
        return activity.getResources().getDisplayMetrics().widthPixels;
    }

    private int resolveAvailableHeight() {
        View parent = (View) rootView.getParent();
        if (parent != null && parent.getHeight() > 0) {
            return parent.getHeight();
        }
        return activity.getResources().getDisplayMetrics().heightPixels;
    }

    private void updateLayoutParams(int width, int height) {
        ViewGroup.LayoutParams params = rootView.getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(width, height);
        }
        if (params.width != width || params.height != height) {
            params.width = width;
            params.height = height;
            if (params instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) params).gravity = Gravity.CENTER;
            }
            rootView.setLayoutParams(params);
            return;
        }
        if (params instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams frameLayoutParams = (FrameLayout.LayoutParams) params;
            if (frameLayoutParams.gravity != Gravity.CENTER) {
                frameLayoutParams.gravity = Gravity.CENTER;
                rootView.setLayoutParams(frameLayoutParams);
            }
        }
    }

    private float resolveRotationDegrees(String rotationMode) {
        if (DeviceSessionRepository.PLAYBACK_ROTATION_90.equals(rotationMode)) {
            return 90f;
        }
        if (DeviceSessionRepository.PLAYBACK_ROTATION_180.equals(rotationMode)) {
            return 180f;
        }
        if (DeviceSessionRepository.PLAYBACK_ROTATION_270.equals(rotationMode)) {
            return 270f;
        }
        return 0f;
    }
}
