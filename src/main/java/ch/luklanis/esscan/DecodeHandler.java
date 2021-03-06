/*
 * Copyright (C) 2010 ZXing authors
 * Copyright 2011 Robert Theis
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

package ch.luklanis.esscan;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.googlecode.tesseract.android.TessBaseAPI;

/**
 * Class to send bitmap data for OCR.
 * <p/>
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing/
 */
final class DecodeHandler extends Handler {

    private final IBase base;
    private boolean running = true;
    private final TessBaseAPI baseApi;

    DecodeHandler(IBase base, TessBaseAPI baseApi) {
        this.base = base;
        this.baseApi = baseApi;
    }

    @Override
    public void handleMessage(Message message) {
        if (!running) {
            return;
        }
        switch (message.what) {
            case R.id.decode:
                ocrContinuousDecode((byte[]) message.obj, message.arg1, message.arg2);
                break;
            case R.id.quit:
                running = false;
                Looper.myLooper().quit();
                break;
        }
    }

    /**
     * Perform an OCR decode for realtime recognition mode.
     *
     * @param data   Image data
     * @param width  Image width
     * @param height Image height
     */
    private void ocrContinuousDecode(byte[] data, int width, int height) {
        // Asyncrhonously launch the OCR process
        PlanarYUVLuminanceSource source = base.getCameraManager()
                .buildLuminanceSource(data, width, height);
        new OcrRecognizeAsyncTask(base, baseApi, source.renderCroppedGreyscaleBitmap()).execute();
    }
}











