/**
 *
 */
package ch.luklanis.esscan.ime;

import android.app.DialogFragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.IOException;

import ch.luklanis.esscan.BeepManager;
import ch.luklanis.esscan.CaptureActivity;
import ch.luklanis.esscan.CaptureActivityHandler;
import ch.luklanis.esscan.IBase;
import ch.luklanis.esscan.OcrInitAsyncTask;
import ch.luklanis.esscan.OcrResult;
import ch.luklanis.esscan.OcrResultText;
import ch.luklanis.esscan.PreferencesActivity;
import ch.luklanis.esscan.R;
import ch.luklanis.esscan.ViewfinderView;
import ch.luklanis.esscan.camera.CameraManager;
import ch.luklanis.esscan.paymentslip.EsrResult;
import ch.luklanis.esscan.paymentslip.EsrValidation;
import ch.luklanis.esscan.paymentslip.PsResult;
import ch.luklanis.esscan.paymentslip.PsValidation;

/**
 * @author llandis
 */
public class ScannerIME extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener, SurfaceHolder.Callback, IBase {

    private static final String TAG = CaptureActivity.class.getSimpleName();

    /**
     * The default OCR engine to use.
     */
    public static final String DEFAULT_OCR_ENGINE_MODE = "Tesseract";

    public static final String EXTERNAL_STORAGE_DIRECTORY = "ESRScan";

    private static final int OCR_ENGINE_MODE = TessBaseAPI.OEM_TESSERACT_ONLY;
    private static final String SOURCE_LANGUAGE_CODE_OCR = "psl";
    private static final String SOURCE_LANGUAGE_READABLE = "payment slip";

    private static final int PAGE_SEGMENTATION_MODE = TessBaseAPI.PageSegMode.PSM_SINGLE_LINE;
    private static final String CHARACTER_WHITELIST = "0123456789>+";

    private CameraManager mCameraManager;
    private CaptureActivityHandler mCaptureActivityHandler;
    private ViewfinderView mViewfinderView;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private TextView mStatusViewBottomLeft;
    private boolean mHasSurface;
    private TessBaseAPI mBaseApi; // Java interface for the Tesseract OCR engine
    // "English"

    private SharedPreferences mSharedPreferences;
    private OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener;

    private PsValidation mPsValidation;

    private int mLastValidationStep;

    private boolean mShowOcrResult;

    private TextView mStatusViewBottomRight;

    private RelativeLayout mInputView;

    private BeepManager mBeepManager;

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        if (mInputView == null) {
            return;
        }

        updateFullscreenMode();

        mSurfaceView = (SurfaceView) mInputView.findViewById(R.id.preview_view);

        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mCameraManager.focus();
                return true;
            }
        });

        mCameraManager = new CameraManager(mSurfaceView);

        mViewfinderView = (ViewfinderView) mInputView.findViewById(R.id.viewfinder_view);

        mViewfinderView.setCameraManager(mCameraManager);

        mStatusViewBottomLeft = (TextView) mInputView.findViewById(R.id.status_view_bottom_left);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mBeepManager = new BeepManager(this);

        mStatusViewBottomRight = (TextView) mInputView.findViewById(R.id.status_view_bottom_right);
        mStatusViewBottomRight.setVisibility(View.GONE);

        if ((info.inputType & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) {
            mStatusViewBottomRight.setText(getResources().getString(R.string.msg_unsupported_field));
            mStatusViewBottomRight.setVisibility(View.VISIBLE);
        }

        mPsValidation = new EsrValidation();
        this.mLastValidationStep = mPsValidation.getCurrentStep();

        mCaptureActivityHandler = null;

        retrievePreferences();

        resetStatusView();
        mPsValidation.gotoBeginning(true);
        this.mLastValidationStep = mPsValidation.getCurrentStep();

        // Do OCR engine initialization, if necessary
        if (mBaseApi == null) {
            // Initialize the OCR engine
            File storageDirectory = getFilesDir();
            if (storageDirectory != null) {
                initOcrEngine(storageDirectory, SOURCE_LANGUAGE_CODE_OCR, SOURCE_LANGUAGE_READABLE);
            }
        } else {
            resumeOcrEngine();
        }

        // Set up the camera preview surface.
        mSurfaceHolder = mSurfaceView.getHolder();

        if (mHasSurface) {
            // The activity was paused but not stopped, so the surface still
            // exists. Therefore
            // surfaceCreated() won't be called, so init the camera here.
            initCamera(mSurfaceHolder);
        } else {
            // Install the callback and wait for surfaceCreated() to init the
            // camera.
            mSurfaceHolder.addCallback(this);
            mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated()");

        if (holder == null) {
            Log.e(TAG, "surfaceCreated gave us a null surface");
        }

        // Only initialize the camera if the OCR engine is ready to go.
        if (!mHasSurface) {
            Log.d(TAG, "surfaceCreated(): calling initCamera()...");
            initCamera(holder);
        }

        mHasSurface = true;
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        shutdownOcr();

        super.onFinishInputView(finishingInput);
    }

    private void shutdownOcr() {
        if (mCaptureActivityHandler != null) {
            mCaptureActivityHandler.quitSynchronously();
            mCaptureActivityHandler = null;
        }

        // Stop using the camera, to avoid conflicting with other camera-based
        // apps
        if (mCameraManager != null) {
            mCameraManager.closeDriver();
            mCameraManager = null;
        }

        if (!mHasSurface && mInputView != null) {
            SurfaceView surfaceView = (SurfaceView) mInputView.findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mBaseApi != null) {
            mBaseApi.end();
            mBaseApi = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (this.mPsValidation == null) {
            return super.onKeyDown(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (this.mPsValidation.getCurrentStep() > 1) {
                    mPsValidation.gotoBeginning(true);
                    this.mLastValidationStep = mPsValidation.getCurrentStep();
                    resetStatusView();
                    return true;
                }

                break;
            // Use volume up/down to turn on light
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mCameraManager.setTorch(false);
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                mCameraManager.setTorch(true);
                return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    public Handler getHandler() {
        return mCaptureActivityHandler;
    }

    public CameraManager getCameraManager() {
        return mCameraManager;
    }

    public PsValidation getValidation() {
        return mPsValidation;
    }

    /**
     * Method to start or restart recognition after the OCR engine has been
     * initialized, or after the app regains focus. Sets state related settings
     * and OCR engine parameters, and requests camera initialization.
     */
    public void resumeOcrEngine() {
        Log.d(TAG, "resumeOcrEngine()");

        // This method is called when Tesseract has already been successfully
        // initialized, so set
        // isEngineReady = true here.

        if (mBaseApi != null) {
            if (mCaptureActivityHandler != null) {
                mCaptureActivityHandler.startDecode(mBaseApi);
            }

            mBaseApi.setPageSegMode(PAGE_SEGMENTATION_MODE);
            mBaseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "");
            mBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, CHARACTER_WHITELIST);
        }
    }

    /**
     * Called to resume recognition after translation in continuous mode.
     */
    void restartPreviewAfterDelay(long delayMS) {
        if (mCaptureActivityHandler != null) {
            mCaptureActivityHandler.sendEmptyMessageDelayed(R.id.restart_decode, delayMS);
        }

        resumeOcrEngine();
        resetStatusView();
    }

    /**
     * Initializes the camera and starts the handler to begin previewing.
     */
    private void initCamera(SurfaceHolder surfaceHolder) {
        Log.d(TAG, "initCamera()");
        try {
            // Open and initialize the camera
            mCameraManager.openDriver(surfaceHolder);

            if (mCaptureActivityHandler == null) {
                // Creating the handler starts the preview, which can also throw
                // a RuntimeException.
                mCaptureActivityHandler = new CaptureActivityHandler(this, mCameraManager);

                if (mBaseApi != null) {
                    mCaptureActivityHandler.startDecode(mBaseApi);
                }
            }
        } catch (IOException ioe) {
            showErrorMessage("Error", "Could not initialize camera. Please try restarting device.");
        } catch (RuntimeException e) {
            // Barcode Scanner has seen crashes in the wild of this variety:
            // java.?lang.?RuntimeException: Fail to connect to camera service
            showErrorMessage("Error", "Could not initialize camera. Please try restarting device.");
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mHasSurface = false;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    /**
     * Requests initialization of the OCR engine with the given parameters.
     *
     * @param storageRoot  Path to location of the tessdata directory to use
     * @param languageCode Three-letter ISO 639-3 language code for OCR
     * @param languageName Name of the language for OCR, for example, "English"
     */
    private void initOcrEngine(File storageRoot, String languageCode, String languageName) {
        // Start AsyncTask to install language data and init OCR
        new OcrInitAsyncTask(this,
                new TessBaseAPI(),
                null,
                languageCode,
                languageName,
                OCR_ENGINE_MODE).execute(storageRoot.toString());
    }

    public void showResult(PsResult psResult) {
        mBeepManager.playBeepSoundAndVibrate();

        InputConnection inputConnection = getCurrentInputConnection();

        String completeCode = psResult.getCompleteCode();
        int indexOfNewline = completeCode.indexOf('\n');
        if (indexOfNewline < 0) {
            inputConnection.commitText(completeCode, 1);
        } else {
            inputConnection.commitText(completeCode.substring(0, indexOfNewline), 1);
        }

        this.hideWindow();
    }

    /**
     * Displays information relating to the results of a successful real-time
     * OCR request.
     *
     * @param ocrResult Object representing successful OCR results
     */
    public void presentOcrDecodeResult(OcrResult ocrResult) {

        // Send an OcrResultText object to the ViewfinderView for text rendering
        mViewfinderView.addResultText(new OcrResultText(ocrResult.getText(),
                ocrResult.getWordConfidences(),
                ocrResult.getMeanConfidence(),
                ocrResult.getBitmapDimensions(),
                ocrResult.getCharacterBoundingBoxes(),
                ocrResult.getWordBoundingBoxes(),
                ocrResult.getTextlineBoundingBoxes(),
                ocrResult.getRegionBoundingBoxes()));

        mStatusViewBottomLeft.setText(ocrResult.getText());

        if (this.mPsValidation.getCurrentStep() != this.mLastValidationStep) {
            this.mLastValidationStep = this.mPsValidation.getCurrentStep();
            mBeepManager.playBeepSoundAndVibrate();
            refreshStatusView();
        }
    }

    /**
     * Given either a Spannable String or a regular String and a token, apply
     * the given CharacterStyle to the span between the tokens.
     * <p/>
     * NOTE: This method was adapted from:
     * http://www.androidengineer.com/2010/08
     * /easy-method-for-formatting-android.html
     * <p/>
     * <p/>
     * For example, {@code setSpanBetweenTokens("Hello ##world##!", "##", new
     *ForegroundColorSpan(0xFFFF0000));} will return a CharSequence
     * {@code "Hello world!"} with {@code world} in red.
     */
    @SuppressWarnings("unused")
    private CharSequence setSpanBetweenTokens(CharSequence text, String token,
                                              CharacterStyle... cs) {
        // Start and end refer to the points where the span will apply
        int tokenLen = token.length();
        int start = text.toString().indexOf(token) + tokenLen;
        int end = text.toString().indexOf(token, start);

        if (start > -1 && end > -1) {
            // Copy the spannable string to a mutable spannable string
            SpannableStringBuilder ssb = new SpannableStringBuilder(text);
            for (CharacterStyle c : cs)
                ssb.setSpan(c, start, end, 0);
            text = ssb;
        }
        return text;
    }

    /**
     * Resets view elements.
     */
    private void resetStatusView() {

        View orangeStatusView = mInputView.findViewById(R.id.status_view_top_orange);
        View redStatusView = mInputView.findViewById(R.id.status_view_top_red);

        if (this.mPsValidation.getSpokenType().equals(EsrResult.PS_TYPE_NAME)) {
            redStatusView.setVisibility(View.GONE);
            orangeStatusView.setVisibility(View.VISIBLE);
        } else {
            orangeStatusView.setVisibility(View.GONE);
            redStatusView.setVisibility(View.VISIBLE);
        }

        refreshStatusView();

        mStatusViewBottomLeft.setText("");

        if (mShowOcrResult) {
            mStatusViewBottomLeft.setVisibility(View.VISIBLE);
        }

        mViewfinderView.removeResultText();
        mViewfinderView.setVisibility(View.VISIBLE);

        Log.i(TAG, "resetStatusView: set lastItem to null");
        mViewfinderView.removeResultText();
    }

    private void refreshStatusView() {
        TextView statusView1;
        TextView statusView2;
        TextView statusView3;

        if (this.mPsValidation.getSpokenType().equals(EsrResult.PS_TYPE_NAME)) {
            statusView1 = (TextView) mInputView.findViewById(R.id.status_view_1_orange);
            statusView2 = (TextView) mInputView.findViewById(R.id.status_view_2_orange);
            statusView3 = (TextView) mInputView.findViewById(R.id.status_view_3_orange);
        } else {
            statusView1 = (TextView) mInputView.findViewById(R.id.status_view_1_red);
            statusView2 = (TextView) mInputView.findViewById(R.id.status_view_2_red);
            statusView3 = (TextView) mInputView.findViewById(R.id.status_view_3_red);
        }

        statusView1.setBackgroundResource(0);
        statusView2.setBackgroundResource(0);
        statusView3.setBackgroundResource(0);

        switch (this.mPsValidation.getCurrentStep()) {
            case 1:
                statusView1.setBackgroundResource(R.drawable.status_view_background);
                break;
            case 2:
                statusView2.setBackgroundResource(R.drawable.status_view_background);
                break;
            case 3:
                statusView3.setBackgroundResource(R.drawable.status_view_background);
                break;

            default:
                break;
        }
    }

    /**
     * Request the viewfinder to be invalidated.
     */
    public void drawViewfinder() {
        mViewfinderView.drawViewfinder();
    }

    /**
     * Returns a string that represents which OCR engine(s) are currently set to
     * be run.
     *
     * @return OCR engine mode
     */
    String getOcrEngineModeName() {
        return DEFAULT_OCR_ENGINE_MODE;
    }

    /**
     * Gets values from shared preferences and sets the corresponding data
     * members in this activity.
     */
    private void retrievePreferences() {
        // Retrieve from preferences, and set in this Activity, the language
        // preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        mSharedPreferences.registerOnSharedPreferenceChangeListener(
                mOnSharedPreferenceChangeListener);

        mShowOcrResult = mSharedPreferences.getBoolean(PreferencesActivity.KEY_SHOW_OCR_RESULT_PREFERENCE,
                false);

        mBeepManager.updatePrefs();
    }

    /**
     * Displays an error message dialog box to the user on the UI thread.
     *
     * @param title   The title for the dialog box
     * @param message The error message to be displayed
     */
    public DialogFragment showErrorMessage(String title, String message) {
        mStatusViewBottomRight.setText(message);
        return null;
    }

    public void setBaseApi(TessBaseAPI baseApi) {
        this.mBaseApi = baseApi;
    }

    /**
     * Called by the framework when your view for creating input needs to be
     * generated. This will be called the first time your input method is
     * displayed, and every time it needs to be re-created such as due to a
     * configuration change.
     */
    @Override
    public View onCreateInputView() {

        shutdownOcr();

        Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

        int screenWidth = display.getWidth();
        int screenHeight = display.getHeight();

        if (screenWidth < screenHeight) {
            mInputView = null;
            LinearLayout layout = new LinearLayout(this);
            layout.setBackgroundColor(Color.WHITE);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            layout.setGravity(Gravity.BOTTOM);

            TextView textView = new TextView(this);
            textView.setText(getResources().getString(R.string.msg_unsupported_orientation));
            textView.setTextColor(Color.RED);
            textView.setPadding(6, 0, 6, 0);
            textView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            layout.addView(textView, 0);

            Button button = new Button(this);
            button.setText(R.string.button_switch_ime);
            button.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    InputMethodManager im = (InputMethodManager) getContext().getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    im.showInputMethodPicker();
                }
            });
            button.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            layout.addView(button, 1);

            return layout;
        }

        mInputView = (RelativeLayout) getLayoutInflater().inflate(R.layout.input, null);

        return mInputView;
    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

    }

    @Override
    public void onPress(int primaryCode) {

    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override
    public void onText(CharSequence text) {

    }

    @Override
    public void swipeDown() {

    }

    @Override
    public void swipeLeft() {

    }

    @Override
    public void swipeRight() {
    }

    @Override
    public void swipeUp() {

    }

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void showDialogAndRestartScan(int resourceId) {
    }

    @Override
    public void setValidation(PsValidation validation) {
        this.mPsValidation = validation;
        resetStatusView();
    }

}
