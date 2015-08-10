package com.example.android.camera2basic;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.StateCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;

import com.example.android.camera2basic.listener.FALCameraSurfaceTextureListener;
import com.example.android.camera2basic.listener.FALPictureTakenCaptureCallback;
import com.example.android.camera2basic.utils.FALCompareSizesByArea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by shun_nakahara on 8/8/15.
 */
public class FALCameraManager {


    //region Handler Thread
    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mCameraBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mCameraBackgroundHandler;

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mCameraBackgroundThread = new HandlerThread("FALCameraBackground");
        mCameraBackgroundThread.start();
        mCameraBackgroundHandler = new Handler(mCameraBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mCameraBackgroundThread.quitSafely();
        try {
            mCameraBackgroundThread.join();
            mCameraBackgroundThread = null;
            mCameraBackgroundThread = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    //endregion


    //region FAL Camera Manager

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    private Activity mActivity;

    public FALCameraManager(@NonNull Activity activity) {
        mActivity = activity;

    }

    public void onViewCreated(final View view, Bundle savedInstanceState, @NonNull AutoFitTextureView textureView) {
        mTextureView = textureView;
    }

    public void onResume(Boolean isFront, @NonNull OnImageAvailableListener onImageAvailableListener) {
        startBackgroundThread();


        if (mTextureView.isAvailable()) {

            WindowManager windowManager = mActivity.getWindowManager();
            Display display = windowManager.getDefaultDisplay();

            Point point = new Point();
            display.getSize(point);

            openCamera(isFront, point.x, point.y, onImageAvailableListener);
        } else {
            mTextureView.setSurfaceTextureListener(new FALCameraSurfaceTextureListener(this, isFront, onImageAvailableListener));
        }

    }

    public void onPause() {
        closeCamera();
        stopBackgroundThread();
    }

    public void onClick(View view) {
        takePicture();
    }


    //endregion

    //region Camera2

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "FALCameraManager";

    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize; // FIXME: size setting

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = null;

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            if (null != mActivity) {
                mActivity.finish();
            }
        }

    };

    /**
     * Opens the camera specified by {@link Camera2BasicFragment}.
     */
    public void openCamera(Boolean isFront, int width, int height, OnImageAvailableListener onImageAvailableListener) {
        setUpCameraOutputs(isFront, width, height, onImageAvailableListener);
        configureTransform(width, height);

        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (mCameraOpenCloseLock == null) {
                mCameraOpenCloseLock = new Semaphore(1);
            }
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mCameraBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(Boolean isFront, int width, int height, OnImageAvailableListener onImageAvailableListener) {
        CameraManager manager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);

        String[] cameraIdList;
        try {
            cameraIdList = manager.getCameraIdList();
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return;
        }

        for (String cameraId : cameraIdList) {

            CameraCharacteristics characteristics;
            try {
                characteristics = manager.getCameraCharacteristics(cameraId);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                continue;
            }

            // Front or Back
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT && isFront) {
                createImageReaedr(width, height, characteristics, onImageAvailableListener);
                mCameraId = cameraId;
            } else if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK && !isFront) {
                createImageReaedr(width, height, characteristics, onImageAvailableListener);
                mCameraId = cameraId;
            }
        }
    }

    private void createImageReaedr(int width, int height, CameraCharacteristics characteristics, OnImageAvailableListener onImageAvailableListener) {
        StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        // For still image captures, we use the largest available size.
        Size[] jpegSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
        Size largest = Collections.max(Arrays.asList(jpegSizes), new FALCompareSizesByArea());

        mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 2);
        mImageReader.setOnImageAvailableListener(onImageAvailableListener, mCameraBackgroundHandler);


        Size[] surfaceSizes = streamConfigurationMap.getOutputSizes(SurfaceTexture.class);
        mPreviewSize = chooseOptimalSize(surfaceSizes, width, height, largest);

        // We fit the aspect ratio of TextureView to the size of preview we picked.
        // FIXME: ...
        int orientation = mActivity.getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        } else {
            mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
        }
    }


    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<Size>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new FALCompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");

            String model = Build.MODEL;

            if (model.startsWith("Nexus 6")) {
                return choices[1];
            }
            return choices[0];
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    public void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize || null == mActivity) {
            return;
        }

        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) viewHeight / mPreviewSize.getHeight(), (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }


    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            // FIXME:
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());


            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // FIXME: createCaptureRequest

            if (null == mCameraDevice) {
                return;
            }

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            StateCallback stateCallback = new StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    // The camera is already closed
                    if (null == mCameraDevice) {
                        return;
                    }

                    // When the session is ready, we start displaying the preview.
                    mCaptureSession = cameraCaptureSession;
                    try {
                        // Auto focus should be continuous for camera preview.
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                        // Flash is automatically enabled when necessary.
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                        float value = (float) 0.0;
                        mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, value);


                        // Finally, we start displaying the camera preview.
                        mPreviewRequest = mPreviewRequestBuilder.build();

                        mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mCameraBackgroundHandler);

                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    // TODO: Error CallBack
                }
            };

            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), stateCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            if (mCameraOpenCloseLock != null) {
                mCameraOpenCloseLock.acquire();
            }
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            if (mCameraOpenCloseLock != null) {
                mCameraOpenCloseLock.release();
            }
        }
    }

    /**
     * Initiate a still image capture.
     */
    private synchronized void takePicture() {

        int width = mTextureView.getWidth();
        int height = mTextureView.getHeight();

        Log.w("", "");
//        try {
//            // This is how to tell the camera to lock focus.
//            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
//
//            // Tell #mCaptureCallback to wait for the lock.
//            mCaptureSession.capture(mPreviewRequestBuilder.build(), new FALPictureTakenCaptureCallback(mActivity, this, mCameraDevice, mImageReader, mCaptureSession, mPreviewRequestBuilder, mCameraBackgroundHandler), mCameraBackgroundHandler);
//
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }
    }

    //endregion

}
