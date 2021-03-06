/*
 * Copyright (C) 2008 ZXing authors
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
import android.os.Message;
import android.util.Log;

import com.googlecode.tesseract.android.TessBaseAPI;

import ch.luklanis.esscan.camera.CameraManager;
import ch.luklanis.esscan.paymentslip.PsResult;
import ch.luklanis.esscan.paymentslip.PsValidation;

/**
 * This class handles all the messaging which comprises the state machine for capture.
 * <p/>
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing/
 */
public final class CaptureActivityHandler extends Handler {

    private static final String TAG = CaptureActivityHandler.class.getSimpleName();

    private static final long OCR_INIT_DELAY = 200;

    private static State state;
    private final IBase base;
    private final CameraManager cameraManager;
    private DecodeThread decodeThread;

    private enum State {
        PREVIEW,
        SUCCESS,
        DONE
    }

    public CaptureActivityHandler(IBase base, CameraManager cameraManager) {
        this.base = base;
        this.cameraManager = cameraManager;

        decodeThread = null;

        state = State.SUCCESS;

        // Start ourselves capturing previews and decode.
        restartOcrPreviewAndDecode();
    }

    @Override
    public void handleMessage(Message message) {

        switch (message.what) {
            case R.id.restart_decode:
                Log.d(TAG, "Got restart decode message");
                state = State.PREVIEW;
                requestOcrDecodeWhenThreadReady();
                break;
            case R.id.decode_succeeded:
                Log.d(TAG, "Got decode succeeded message");
                if (state != State.DONE) {
                    state = State.SUCCESS;
                    try {
                        base.presentOcrDecodeResult((OcrResult) message.obj);
                    } catch (NullPointerException e) {
                        // Continue
                    }
                    requestOcrDecodeWhenThreadReady();
                    base.drawViewfinder();
                }
                break;
            case R.id.decode_failed:
                if (state != State.DONE) {
                    state = State.PREVIEW;
                    requestOcrDecodeWhenThreadReady();
                }
                break;
            case R.id.es_decode_succeeded:
                state = State.DONE;
                PsResult result = (PsResult) message.obj;

                base.showResult(result);
                break;
            case R.id.es_change_ps_type:
                base.setValidation((PsValidation) message.obj);
                break;
            case R.id.es_send_succeeded:
                base.showDialogAndRestartScan(R.string.msg_coderow_sent);
                break;
            case R.id.es_send_failed:
                base.showDialogAndRestartScan(R.string.msg_coderow_not_sent);
                break;
        }
    }

    public void startDecode(TessBaseAPI baseApi) {
        if (this.decodeThread == null) {
            this.decodeThread = new DecodeThread(this.base, baseApi);
            this.decodeThread.start();
        }
    }

    public void quitSynchronously() {
        state = State.DONE;

        if (decodeThread != null) {
            try {
                Message message = Message.obtain(decodeThread.getHandler(), R.id.quit);
                message.sendToTarget();

                // Wait at most half a second; should be enough time, and onPause() will timeout quickly
                decodeThread.join(500L);
            } catch (InterruptedException e) {
                // continue
            }
        }

        decodeThread = null;

        // Be absolutely sure we don't send any queued up messages
        removeMessages(R.id.restart_decode);
        removeMessages(R.id.decode_failed);
        removeMessages(R.id.decode_succeeded);

        if (cameraManager != null) {
            cameraManager.stopPreview();
        }
    }

    /**
     * Send a decode request for realtime OCR mode
     */
    private void restartOcrPreviewAndDecode() {
        if (state == State.SUCCESS) {
            state = State.PREVIEW;
            // Continue capturing camera frames
            cameraManager.startPreview();

            requestOcrDecodeWhenThreadReady();
            base.drawViewfinder();
        }
    }

    private void requestOcrDecodeWhenThreadReady() {
        if (this.decodeThread != null) {
            // Continue requesting decode of images
            cameraManager.requestOcrDecode(decodeThread.getHandler(), R.id.decode);
        } else {
            this.sendEmptyMessageDelayed(R.id.decode_failed, OCR_INIT_DELAY);
            Log.w(TAG, "Skipping decode because OCR isn't initialized yet");
        }
    }
}
