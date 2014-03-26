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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

import ch.luklanis.esscan.camera.CameraManager;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the result text.
 * <p/>
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
public final class ViewfinderView extends View {
    //private static final long ANIMATION_DELAY = 80L;

    /**
     * Flag to draw boxes representing the results from TessBaseAPI::GetRegions().
     */
    private static final boolean DRAW_REGION_BOXES = false;

    /**
     * Flag to draw boxes representing the results from TessBaseAPI::GetTextlines().
     */
    private static final boolean DRAW_TEXTLINE_BOXES = true;

    /**
     * Flag to draw boxes representing the results from TessBaseAPI::GetWords().
     */
    private static final boolean DRAW_WORD_BOXES = true;

    private CameraManager cameraManager;
    private final Paint paint;
    private final int maskColor;
    private final int frameColor;
    private OcrResultText resultText;
    private String[] words;
    private List<Rect> wordBoundingBoxes;
    private List<Rect> textlineBoundingBoxes;
    private List<Rect> regionBoundingBoxes;
    //  Rect bounds;
    private Rect previewFrame;
    private Rect rect;

    private Rect bounds;
    private boolean mShowDebugOutput;

    // This constructor is used when the class is built from an XML resource.
    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        frameColor = resources.getColor(R.color.viewfinder_frame);

        previewFrame = new Rect();
        rect = new Rect();
        bounds = new Rect();

        mShowDebugOutput = false;
    }

    public void setCameraManager(CameraManager cameraManager) {
        this.cameraManager = cameraManager;
    }

    @SuppressWarnings("unused")
    @Override
    public void onDraw(Canvas canvas) {
        Rect frame;

        int width = canvas.getWidth();
        int height = canvas.getHeight();


        if (cameraManager == null || cameraManager.getFramingRect() == null) {
            return;
        }

        frame = cameraManager.getFramingRect();

        // Draw the exterior (i.e. outside the framing rect) darkened
        paint.setColor(maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        // If we have an OCR result, overlay its information on the viewfinder.
        if (resultText != null && mShowDebugOutput) {

            // Only draw text/bounding boxes on viewfinder if it hasn't been resized since the OCR was requested.
            Point bitmapSize = resultText.getBitmapDimensions();
            previewFrame = cameraManager.getFramingRectInPreview();

            if (previewFrame == null) {
                return;
            }

            if (bitmapSize.x == previewFrame.width() && bitmapSize.y == previewFrame.height()) {


                float scaleX = frame.width() / (float) previewFrame.width();
                float scaleY = frame.height() / (float) previewFrame.height();

                if (DRAW_REGION_BOXES) {
                    regionBoundingBoxes = resultText.getRegionBoundingBoxes();
                    for (int i = 0; i < regionBoundingBoxes.size(); i++) {
                        paint.setAlpha(0xA0);
                        paint.setColor(Color.MAGENTA);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(1);
                        rect = regionBoundingBoxes.get(i);
                        canvas.drawRect(frame.left + rect.left * scaleX,
                                frame.top + rect.top * scaleY,
                                frame.left + rect.right * scaleX,
                                frame.top + rect.bottom * scaleY,
                                paint);
                    }
                }

                if (DRAW_TEXTLINE_BOXES) {
                    // Draw each textline
                    textlineBoundingBoxes = resultText.getTextlineBoundingBoxes();
                    paint.setAlpha(0xA0);
                    paint.setColor(Color.RED);
                    paint.setStyle(Style.STROKE);
                    paint.setStrokeWidth(1);
                    for (int i = 0; i < textlineBoundingBoxes.size(); i++) {
                        rect = textlineBoundingBoxes.get(i);
                        canvas.drawRect(frame.left + rect.left * scaleX,
                                frame.top + rect.top * scaleY,
                                frame.left + rect.right * scaleX,
                                frame.top + rect.bottom * scaleY,
                                paint);
                    }
                }

                if (DRAW_WORD_BOXES) {
                    // Split the text into words
                    wordBoundingBoxes = resultText.getWordBoundingBoxes();
                    //      for (String w : words) {
                    //        Log.e("ViewfinderView", "word: " + w);
                    //      }
                    //Log.d("ViewfinderView", "There are " + words.length + " words in the string array.");
                    //Log.d("ViewfinderView", "There are " + wordBoundingBoxes.size() + " words with bounding boxes.");
                }

                if (DRAW_WORD_BOXES) {
                    paint.setAlpha(0xA0);
                    paint.setColor(0xFF00CCFF);
                    paint.setStyle(Style.STROKE);
                    paint.setStrokeWidth(1);
                    for (int i = 0; i < wordBoundingBoxes.size(); i++) {
                        // Draw a bounding box around the word
                        rect = wordBoundingBoxes.get(i);
                        canvas.drawRect(frame.left + rect.left * scaleX,
                                frame.top + rect.top * scaleY,
                                frame.left + rect.right * scaleX,
                                frame.top + rect.bottom * scaleY,
                                paint);
                    }
                }
            }

        }
        // Draw a two pixel solid border inside the framing rect
        paint.setAlpha(0);
        paint.setStyle(Style.FILL);
        paint.setColor(frameColor);
        canvas.drawRect(frame.left, frame.top, frame.right + 1, frame.top + 2, paint);
        canvas.drawRect(frame.left, frame.top + 2, frame.left + 2, frame.bottom - 1, paint);
        canvas.drawRect(frame.right - 1, frame.top, frame.right + 1, frame.bottom - 1, paint);
        canvas.drawRect(frame.left, frame.bottom - 1, frame.right + 1, frame.bottom + 1, paint);

        // Draw the framing rect corner UI elements
        //    paint.setColor(cornerColor);
        //    canvas.drawRect(frame.left - 15, frame.top - 15, frame.left + 15, frame.top, paint);
        //    canvas.drawRect(frame.left - 15, frame.top, frame.left, frame.top + 15, paint);
        //    canvas.drawRect(frame.right - 15, frame.top - 15, frame.right + 15, frame.top, paint);
        //    canvas.drawRect(frame.right, frame.top - 15, frame.right + 15, frame.top + 15, paint);
        //    canvas.drawRect(frame.left - 15, frame.bottom, frame.left + 15, frame.bottom + 15, paint);
        //    canvas.drawRect(frame.left - 15, frame.bottom - 15, frame.left, frame.bottom, paint);
        //    canvas.drawRect(frame.right - 15, frame.bottom, frame.right + 15, frame.bottom + 15, paint);
        //    canvas.drawRect(frame.right, frame.bottom - 15, frame.right + 15, frame.bottom + 15, paint);


        // Request another updateDtaFilename at the animation interval, but don't repaint the entire viewfinder mask.
        //postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top, frame.right, frame.bottom);
    }

    public void drawViewfinder() {
        invalidate();
    }

    /**
     * Adds the given OCR results for drawing to the view.
     *
     * @param text Object containing OCR-derived text and corresponding data.
     */
    public void addResultText(OcrResultText text) {
        resultText = text;
    }

    /**
     * Nullifies OCR text to remove it at the next onDraw() drawing.
     */
    public void removeResultText() {
        resultText = null;
    }

    public void setmShowDebugOutput(boolean mShowDebugOutput) {
        this.mShowDebugOutput = mShowDebugOutput;
    }
}
