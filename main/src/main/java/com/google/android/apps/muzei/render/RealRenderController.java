/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.muzei.render;

import android.content.Context;
import android.database.ContentObserver;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import com.google.android.apps.muzei.api.MuzeiContract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class RealRenderController extends RenderController {
    private static final String TAG = "RealRenderController";

    private final Uri mImageUri;
    private final ContentObserver mContentObserver;

    public RealRenderController(Context context, MuzeiBlurRenderer renderer,
            Callbacks callbacks, Uri imageUri) {
        super(context, renderer, callbacks);
        mImageUri = imageUri;
        mContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                reloadCurrentArtwork(false);
            }
        };
        context.getContentResolver().registerContentObserver(imageUri,
                true, mContentObserver);
        reloadCurrentArtwork(false);
    }

    @Override
    public void destroy() {
        super.destroy();
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
    }

    @Override
    protected BitmapRegionLoader openDownloadedCurrentArtwork(boolean forceReload) {
        // Load the stream
        try {
            // Check if there's rotation
            int rotation = 0;
            try {
                InputStream in = mContext.getContentResolver().openInputStream(mImageUri);
                if (in == null) {
                    return null;
                }
                InputStream in = mContext.getContentResolver().openInputStream(mImageUri);
                ExifInterface exifInterface;
                if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    exifInterface = new ExifInterface(in);
                } else {
                    exifInterface = new ExifInterface(writeArtworkToFile(in).getAbsolutePath());
                }
                int orientation = exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90: rotation = 90; break;
                    case ExifInterface.ORIENTATION_ROTATE_180: rotation = 180; break;
                    case ExifInterface.ORIENTATION_ROTATE_270: rotation = 270; break;
                }
            } catch (IOException e) {
                Log.w(TAG, "Couldn't open EXIF interface on artwork", e);
            }
            return BitmapRegionLoader.newInstance(
                    mContext.getContentResolver().openInputStream(mImageUri), rotation);
        } catch (IOException e) {
            Log.e(TAG, "Error loading image", e);
            return null;
        }
    }

    private File writeArtworkToFile(InputStream in) throws IOException {
        File file = new File(mContext.getCacheDir(), "temp_artwork");
        FileOutputStream out = new FileOutputStream(file);
        try {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } finally {
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
        return file;
    }
}
