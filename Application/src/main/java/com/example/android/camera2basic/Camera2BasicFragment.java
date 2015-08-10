/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2basic;

import android.app.Fragment;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


public class Camera2BasicFragment extends Fragment implements View.OnClickListener {


    private FALCameraManager cameraManager;
    private Boolean isFront = false;

    public static Camera2BasicFragment newInstance() {
        Camera2BasicFragment fragment = new Camera2BasicFragment();
        fragment.setRetainInstance(true);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cameraManager = new FALCameraManager(this.getActivity());

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }


    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);

        AutoFitTextureView textureView = (AutoFitTextureView) view.findViewById(R.id.texture);

        cameraManager.onViewCreated(view, savedInstanceState, textureView);
    }

    private ImageReader.OnImageAvailableListener listener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if (reader == null) {
                return;
            }

            Image image = null;
            try {
                image = reader.acquireNextImage();
            } catch (IllegalStateException | AssertionError e) {
                e.printStackTrace();
            }

            if (image == null) {
                Log.e("error image", "");
            } else {
                Log.e("", "");
                image.close(); // must call it.
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        cameraManager.onResume(isFront, listener);
    }

    @Override
    public void onPause() {
        cameraManager.onPause();
        super.onPause();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.picture: {
                cameraManager.onClick(view);
                break;
            }
            case R.id.info: {
                isFront = !isFront;
                cameraManager.onPause();
                cameraManager.onResume(isFront, listener);
                break;
            }
        }
    }
}
