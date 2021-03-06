/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.askjeffreyliu.camera2barcode.camera;

import android.Manifest;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.support.annotation.RequiresPermission;
import android.util.AttributeSet;
import android.util.Log;
import android.view.TextureView;
import android.view.ViewGroup;

import com.askjeffreyliu.camera2barcode.camera2.AutoFitTextureView;
import com.askjeffreyliu.camera2barcode.utils.Utils;
import com.google.android.gms.common.images.Size;

import java.io.IOException;

public class CameraSourcePreview extends ViewGroup {
    private static final String TAG = "CameraSourcePreview";

    private AutoFitTextureView mAutoFitTextureView;

    private boolean mStartRequested;
    private boolean mSurfaceAvailable;
    private CameraSource mCamera2Source;
    private boolean viewAdded = false;

    private GraphicOverlay mOverlay;
    private int screenWidth;
    private int screenHeight;
    private int screenRotation;

    public CameraSourcePreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        screenHeight = Utils.getScreenHeight(context);
        screenWidth = Utils.getScreenWidth(context);
        screenRotation = Utils.getScreenRotation(context);
        mStartRequested = false;
        mSurfaceAvailable = false;
        mAutoFitTextureView = new AutoFitTextureView(context);
        mAutoFitTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    public void start(CameraSource camera2Source, GraphicOverlay overlay) throws IOException, SecurityException {
        mOverlay = overlay;
        start(camera2Source);
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private void start(CameraSource camera2Source) throws IOException, SecurityException {
        if (camera2Source == null) {
            stop();
        }
        mCamera2Source = camera2Source;
        if (mCamera2Source != null) {
            mStartRequested = true;
            if (!viewAdded) {
                addView(mAutoFitTextureView);
                viewAdded = true;
            }
            try {
                startIfReady();
            } catch (IOException e) {
                Log.e(TAG, "Could not start camera source.", e);
            }
        }
    }

    public void stop() {
        mStartRequested = false;

        if (mCamera2Source != null) {
            mCamera2Source.stop();
        }
    }

    @RequiresPermission(Manifest.permission.CAMERA)
    private void startIfReady() throws IOException, SecurityException {
        if (mStartRequested && mSurfaceAvailable) {
            mCamera2Source.start(mAutoFitTextureView, screenRotation);
            if (mOverlay != null) {
                Size size = mCamera2Source.getPreviewSize();
                if (size != null) {
                    int min = Math.min(size.getWidth(), size.getHeight());
                    int max = Math.max(size.getWidth(), size.getHeight());
                    // FOR GRAPHIC OVERLAY, THE PREVIEW SIZE WAS REDUCED TO QUARTER
                    // IN ORDER TO PREVENT CPU OVERLOAD
                    mOverlay.setCameraInfo(min / 4, max / 4);
                    mOverlay.clear();
                } else {
                    stop();
                }
            }
            mStartRequested = false;
        }
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            mSurfaceAvailable = true;
            mOverlay.bringToFront();
            try {
                startIfReady();
            } catch (IOException e) {
                Log.e(TAG, "Could not start camera source.", e);
            } catch (SecurityException e) {
                Log.e(TAG, "Permission issue " + e.toString());
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            mSurfaceAvailable = false;
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = 320;
        int height = 240;
        if (mCamera2Source != null) {
            Size size = mCamera2Source.getPreviewSize();
            if (size != null) {
                // Swap width and height sizes when in portrait, since it will be rotated 90 degrees
                height = size.getWidth();
                width = size.getHeight();
            }
        }


        //RESIZE PREVIEW IGNORING ASPECT RATIO. THIS IS ESSENTIAL.
        int newWidth = (height * screenWidth) / screenHeight;

        final int layoutWidth = right - left;
        final int layoutHeight = bottom - top;
        // Computes height and width for potentially doing fit width.
        int childWidth = layoutWidth;
        int childHeight = (int) (((float) layoutWidth / (float) newWidth) * height);
        // If height is too tall using fit width, does fit height instead.
        if (childHeight > layoutHeight) {
            childHeight = layoutHeight;
            childWidth = (int) (((float) layoutHeight / (float) height) * newWidth);
        }

        for (int i = 0; i < getChildCount(); ++i) {
            getChildAt(i).layout(0, 0, childWidth, childHeight);
        }

        try {
            startIfReady();
        } catch (SecurityException se) {
            Log.e(TAG, "Do not have permission to start the camera", se);
        } catch (IOException e) {
            Log.e(TAG, "Could not start camera source.", e);
        }
    }
}
