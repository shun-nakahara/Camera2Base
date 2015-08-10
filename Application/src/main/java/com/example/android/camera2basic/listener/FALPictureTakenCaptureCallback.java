package com.example.android.camera2basic.listener;

import android.app.Activity;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.ImageReader;
import android.os.Handler;
import android.util.SparseIntArray;
import android.view.Surface;

import com.example.android.camera2basic.FALCameraManager;

/**
 * Created by shun_nakahara on 8/8/15.
 */
public class FALPictureTakenCaptureCallback extends CaptureCallback {

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private Activity mActivity;
    private FALCameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private ImageReader mImageReader;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private Handler mCameraBackgroundHandler;

    public FALPictureTakenCaptureCallback(Activity activity, FALCameraManager manager, CameraDevice cameraDevice, ImageReader imageReader, CameraCaptureSession sessions,CaptureRequest.Builder builder, Handler handler) {
        mActivity = activity;
        mCameraManager = manager;
        mImageReader = imageReader;
        mCameraDevice = cameraDevice;
        mCameraBackgroundHandler = handler;
        mPreviewRequestBuilder = builder;
        mCaptureSession = sessions;
    }

    @Override
    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
        super.onCaptureCompleted(session, request, result);
        captureStillPicture();
    }

    @Override
    public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
        super.onCaptureFailed(session, request, failure);
        unlockFocus();
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #lockFocus()}.
     */
    private synchronized void captureStillPicture() {
        try {
            if (null == mActivity || null == mCameraDevice) {
                return;
            }

            // FIXME: createCaptureRequest
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);

            captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);


            // Orientation
            int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback = new CameraCaptureSession.CaptureCallback() {


                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    unlockFocus();
                }

                @Override
                public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                    super.onCaptureFailed(session, request, failure);
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is finished.
     */
    private synchronized void unlockFocus() {
        try {
            // Reset the autofucos trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            // After this, the camera will go back to the normal state of preview.
            CaptureRequest previewRequest = mPreviewRequestBuilder.build();
            mCaptureSession.capture(previewRequest, null, mCameraBackgroundHandler);

            mCaptureSession.setRepeatingRequest(previewRequest, null, mCameraBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

}
