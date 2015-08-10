package com.example.android.camera2basic.listener;

import android.graphics.SurfaceTexture;
import android.media.ImageReader.OnImageAvailableListener;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;

import com.example.android.camera2basic.FALCameraManager;

/**
 * Created by shun_nakahara on 8/6/15.
 * <p/>
 * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
 * {@link TextureView}.
 */
public class FALCameraSurfaceTextureListener implements SurfaceTextureListener {

    private FALCameraManager cameraManager;
    private OnImageAvailableListener imageAvailableListener;
    private Boolean isFront;

    public FALCameraSurfaceTextureListener(FALCameraManager manager, Boolean isFront, OnImageAvailableListener listener) {
        cameraManager = manager;
        imageAvailableListener = listener;
        this.isFront = isFront;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        cameraManager.openCamera(isFront, width, height, imageAvailableListener);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        cameraManager.configureTransform(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
