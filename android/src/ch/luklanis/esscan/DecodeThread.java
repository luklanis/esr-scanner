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

import com.googlecode.tesseract.android.TessBaseAPI;

import ch.luklanis.esscan.CaptureActivity;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;

/**
 * This thread does all the heavy lifting of decoding the images.
 *
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
final class DecodeThread extends Thread {

  private final IBase base;
  private Handler handler;
  private final CountDownLatch handlerInitLatch;
  private final TessBaseAPI baseApi;

  DecodeThread(IBase base, TessBaseAPI baseApi) {
    this.base = base;
    this.baseApi = baseApi;
    handlerInitLatch = new CountDownLatch(1);
  }

  Handler getHandler() {
    try {
      handlerInitLatch.await();
    } catch (InterruptedException ie) {
      // continue?
    }
    return handler;
  }

  @Override
  public void run() {
    Looper.prepare();
    handler = new DecodeHandler(base, baseApi);
    handlerInitLatch.countDown();
    Looper.loop();
  }
}
