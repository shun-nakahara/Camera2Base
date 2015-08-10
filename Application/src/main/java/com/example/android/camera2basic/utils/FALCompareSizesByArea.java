package com.example.android.camera2basic.utils;

import android.util.Size;

import java.util.Comparator;

/**
 * Created by shun_nakahara on 8/6/15.
 *
 * Compares two {@code Size}s based on their areas.
 */
public class FALCompareSizesByArea implements Comparator<Size> {

    @Override
    public int compare(Size lhs, Size rhs) {
        // We cast here to ensure the multiplications won't overflow
        int signum = Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        return signum;
    }

}
