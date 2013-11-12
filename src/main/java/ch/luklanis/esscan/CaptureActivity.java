/*
 * Copyright (C) 2008 ZXing authors
 * Copyright 2011 Robert Theis
 * Copyright 2012 Lukas Landis
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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.IOException;
import java.security.Security;
import java.util.List;

import ch.luklanis.esscan.camera.CameraManager;
import ch.luklanis.esscan.history.BankProfile;
import ch.luklanis.esscan.history.HistoryActivity;
import ch.luklanis.esscan.history.HistoryItem;
import ch.luklanis.esscan.history.HistoryManager;
import ch.luklanis.esscan.paymentslip.EsResult;
import ch.luklanis.esscan.paymentslip.EsrResult;
import ch.luklanis.esscan.paymentslip.EsrValidation;
import ch.luklanis.esscan.paymentslip.PsResult;
import ch.luklanis.esscan.paymentslip.PsValidation;

//import android.content.ClipData;

/**
 * This activity opens the camera and does the actual scanning on a background
 * thread. It draws a viewfinder to help the user place the barcode correctly,
 * shows feedback as the image processing is happening, and then overlays the
 * results when a scan is successful.
 * <p/>
 * The code for this class was adapted from the ZXing project:
 * http://code.google.com/p/zxing/
 */
public final class CaptureActivity extends EsrBaseActivity
        implements SurfaceHolder.Callback, IBase {

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

    private static final int NOTIFICATION_ID = 1;

    private static final String COPY_AND_RETURN = "copy_and_return";

    private static boolean sCopyAndReturn;

    private CameraManager mCameraManager;
    private CaptureActivityHandler mCaptureActivityHandler;
    private ViewfinderView mViewfinderView;
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private TextView mStatusViewBottomLeft;
    private boolean mHasSurface;
    private BeepManager mBeepManager;
    private TessBaseAPI mBaseApi; // Java interface for the Tesseract OCR engine

    private ProgressDialog mInitOcrProgressDialog; // for initOcr - language
    // download & unzip
    private HistoryManager mHistoryManager;

    private PsValidation mPsValidation;

    private int mLastValidationStep;

    private boolean mShowOcrResult;
    private boolean mEnableStreamMode;

    private TextView mStatusViewBottomRight;

    private boolean mShowScanResult;

    @Override
    public void onCreate(Bundle icicle) {
        requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
        super.onCreate(icicle);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.capture);

        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

        mShowOcrResult = false;

        mHasSurface = false;
        mHistoryManager = new HistoryManager(this);
        mHistoryManager.trimHistory();

        mBeepManager = new BeepManager(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        mSurfaceView = (SurfaceView) findViewById(R.id.preview_view);

        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mCameraManager.focus();
                return true;
            }
        });

        mCameraManager = new CameraManager(mSurfaceView);

        mViewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        mViewfinderView.setCameraManager(mCameraManager);

        mStatusViewBottomLeft = (TextView) findViewById(R.id.status_view_bottom_left);

        mStatusViewBottomRight = (TextView) findViewById(R.id.status_view_bottom_right);
        mStatusViewBottomRight.setVisibility(View.GONE);

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

        Intent intent = getIntent();

        if (intent != null && intent.getBooleanExtra(COPY_AND_RETURN, false)) {
            sCopyAndReturn = true;
            setIntent(null);
        } else {
            sCopyAndReturn = false;
        }

        checkAndRunFirstLaunch();
    }

    @Override
    protected Handler getDataSentHandler() {
        return mCaptureActivityHandler;
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
    protected void onPause() {
        if (mCaptureActivityHandler != null) {
            mCaptureActivityHandler.quitSynchronously();
            mCaptureActivityHandler = null;
        }

        // Stop using the camera, to avoid conflicting with other camera-based
        // apps
        mCameraManager.closeDriver();

        if (!mHasSurface) {
            SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.removeCallback(this);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean(PreferencesActivity.KEY_ONLY_COPY, false)) {
            CreateCopyNotification();
        } else {
            RemoveCopyNotification();
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mBaseApi != null) {
            mBaseApi.end();
            mBaseApi = null;
        }

        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (this.mPsValidation.getCurrentStep() > 1) {
                    mPsValidation.gotoBeginning(true);
                    this.mLastValidationStep = mPsValidation.getCurrentStep();
                    resetStatusView();
                    return true;
                }
                setResult(RESULT_CANCELED);
                finish();
                return true;
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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.capture_menu, menu);

        MenuItem streamMode = menu.findItem(R.id.menu_stream_mode);
        MenuItem showHistory = menu.findItem(R.id.menu_history);

        if (mEnableStreamMode) {
            showHistory.setVisible(false);
            streamMode.setIcon(R.drawable.ic_menu_edit_activated);
        } else {
            showHistory.setVisible(true);
            streamMode.setIcon(R.drawable.ic_menu_edit);
        }

        return true;
    }

    @SuppressLint("NewApi")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.menu_stream_mode: {
                // seperate if to exclude complete statement in compiled code
                if (mEnableStreamMode) {
                    mEnableStreamMode = false;
                    mSharedPreferences.edit()
                            .putBoolean(PreferencesActivity.KEY_ENABLE_STREAM_MODE,
                                    mEnableStreamMode)
                            .apply();

                    clearIPAddresses();
                    if (mPsValidation.getSpokenType().equals(EsResult.PS_TYPE_NAME)) {
                        mPsValidation = new EsrValidation();
                    }
                    resetStatusView();

                    invalidateOptionsMenu();
                } else {
                    mEnableStreamMode = true;
                    mSharedPreferences.edit()
                            .putBoolean(PreferencesActivity.KEY_ENABLE_STREAM_MODE,
                                    mEnableStreamMode)
                            .apply();

                    if (mEnableStreamMode) {
                        invalidateOptionsMenu();
                    }
                }
                break;
            }
            case R.id.menu_history: {
                intent = new Intent(this, HistoryActivity.class);
                startActivity(intent);
                break;
            }
            case R.id.menu_settings: {
                intent = new Intent(this, PreferencesActivity.class);
                startActivity(intent);
                break;
            }
            case R.id.menu_help: {
                intent = new Intent(this, HelpActivity.class);
                intent.putExtra(HelpActivity.REQUESTED_PAGE_KEY, HelpActivity.DEFAULT_PAGE);
                startActivity(intent);
                break;
            }
            case R.id.menu_about: {
                intent = new Intent(this, HelpActivity.class);
                intent.putExtra(HelpActivity.REQUESTED_PAGE_KEY, HelpActivity.ABOUT_PAGE);
                startActivity(intent);
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void setValidation(PsValidation validation) {
        this.mPsValidation = validation;
        resetStatusView();
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

                if (mEsrSenderHttp != null) {
                    mEsrSenderHttp.registerDataSentHandler(mCaptureActivityHandler);
                }

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

    private void clearIPAddresses() {
        mStatusViewBottomRight.setText("");
        mStatusViewBottomRight.setVisibility(View.GONE);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        mHasSurface = false;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    /**
     * Finds the proper location on the SD card where we can save files.
     */
    private File getOldTessdataDirectory() {
        // Log.d(TAG, "getStorageDirectory(): API level is " +
        // Integer.valueOf(android.os.Build.VERSION.SDK_INT));

        String state;
        try {
            state = Environment.getExternalStorageState();
        } catch (RuntimeException e) {
            Log.e(TAG, "Is the SD card visible?", e);
            return null;
        }

        if (Environment.MEDIA_MOUNTED.equals(state)) {

            // We can read and write the media
            // if (Integer.valueOf(android.os.Build.VERSION.SDK_INT) > 7) {
            // For Android 2.2 and above

            try {
                return getExternalFilesDir(null);
            } catch (NullPointerException e) {
                // We get an error here if the SD card is visible, but full
                Log.e(TAG, "External storage is unavailable");
                return null;
            }
        }

        return null;
    }

    /**
     * Requests initialization of the OCR engine with the given parameters.
     *
     * @param storageRoot  Path to location of the tessdata directory to use
     * @param languageCode Three-letter ISO 639-3 language code for OCR
     * @param languageName Name of the language for OCR, for example, "English"
     */
    private void initOcrEngine(File storageRoot, String languageCode, String languageName) {
        // Set up the dialog box for the thermometer-style download progress
        // indicator
        if (mInitOcrProgressDialog != null) {
            mInitOcrProgressDialog.dismiss();
        }
        mInitOcrProgressDialog = new ProgressDialog(this);

        // Start AsyncTask to install language data and init OCR
        new OcrInitAsyncTask(this,
                new TessBaseAPI(),
                mInitOcrProgressDialog,
                languageCode,
                languageName,
                OCR_ENGINE_MODE).execute(storageRoot.toString());
    }

    public void showResult(PsResult psResult) {
        mBeepManager.playBeepSoundAndVibrate();

        if (mEnableStreamMode && getEsrSender() != null) {
            mSendingProgressDialog.show();
            String completeCode = psResult.getCompleteCode();
            int indexOfNewline = completeCode.indexOf('\n');
            if (indexOfNewline < 0) {
                getEsrSender().sendToListener(completeCode);
            } else {
                getEsrSender().sendToListener(completeCode.substring(0, indexOfNewline));
            }
            return;
        }

        if (sCopyAndReturn) {
            EsrResult esrResult = (EsrResult) psResult;
            String toCopy = PreferenceManager.getDefaultSharedPreferences(this)
                    .getString(PreferencesActivity.KEY_COPY_PART, "0")
                    .equals("0") ? esrResult.getCompleteCode() : esrResult.getReference();

            addCodeRowToClipboard(toCopy);

            finish();
            return;
        }

        if (!mShowScanResult) {
            mHistoryManager.addHistoryItem(psResult);
            showDialogAndRestartScan(R.string.msg_coderow_saved);
            return;
        }

        Intent intent = new Intent(this, HistoryActivity.class);
        intent.setAction(HistoryActivity.ACTION_SHOW_RESULT);
        intent.putExtra(HistoryActivity.EXTRA_CODE_ROW, psResult.getCompleteCode());
        startActivity(intent);
    }

    public void showDialogAndRestartScan(int resourceId) {
        mSendingProgressDialog.dismiss();

        new RestartScanAlertDialog(resourceId).show(getFragmentManager(),
                "CaptureActivity.showDialogAndRestartScan");
    }

    public class RestartScanAlertDialog extends DialogFragment {
        private int msgId;

        public RestartScanAlertDialog(int msgId) {
            this.msgId = msgId;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setMessage(msgId);
            builder.setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mPsValidation.gotoBeginning(false);
                    mLastValidationStep = mPsValidation.getCurrentStep();
                    restartPreviewAfterDelay(0L);
                }
            });

            return builder.create();
        }
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

        View orangeStatusView = findViewById(R.id.status_view_top_orange);
        View redStatusView = findViewById(R.id.status_view_top_red);

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
            statusView1 = (TextView) findViewById(R.id.status_view_1_orange);
            statusView2 = (TextView) findViewById(R.id.status_view_2_orange);
            statusView3 = (TextView) findViewById(R.id.status_view_3_orange);
        } else {
            statusView1 = (TextView) findViewById(R.id.status_view_1_red);
            statusView2 = (TextView) findViewById(R.id.status_view_2_red);
            statusView3 = (TextView) findViewById(R.id.status_view_3_red);
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

    private void DeleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) for (File child : fileOrDirectory.listFiles())
            DeleteRecursive(child);

        fileOrDirectory.delete();
    }

    /**
     * We want the help screen to be shown automatically the first time a new
     * version of the app is run. The easiest way to do this is to check
     * android:versionCode from the manifest, and compare it to a value stored
     * as a preference.
     */
    private boolean checkAndRunFirstLaunch() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            int currentVersion = info.versionCode;
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            int lastVersion = prefs.getInt(PreferencesActivity.KEY_HELP_VERSION_SHOWN, 0);

            if (currentVersion > lastVersion) {

                File oldStorage = getOldTessdataDirectory();

                if (oldStorage != null && oldStorage.exists()) {
                    DeleteRecursive(new File(oldStorage.toString()));
                }

                if (lastVersion <= 42) {
                    int bankProfileId = mHistoryManager.addBankProfile(new BankProfile("Default",
                            prefs.getString(PreferencesActivity.KEY_IBAN, ""),
                            prefs.getString(PreferencesActivity.KEY_EXECUTION_DAY, "")));

                    List<HistoryItem> historyItemList = mHistoryManager.buildAllHistoryItems();

                    for (HistoryItem historyItem : historyItemList) {
                        if (historyItem.getBankProfileId() == BankProfile.INVALID_BANK_PROFILE_ID) {
                            mHistoryManager.updateHistoryItemBankProfileId(historyItem.getResult()
                                    .getCompleteCode(), bankProfileId);
                        }
                    }
                }

                // Record the last version for which we last displayed the
                // What's New (Help) page
                prefs.edit()
                        .putInt(PreferencesActivity.KEY_HELP_VERSION_SHOWN, currentVersion)
                        .commit();
                Intent intent = new Intent(this, HelpActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

                // Show the default page on a clean install, and the what's new
                // page on an upgrade.
                String page = lastVersion == 0 ? HelpActivity.DEFAULT_PAGE : HelpActivity.WHATS_NEW_PAGE;
                intent.putExtra(HelpActivity.REQUESTED_PAGE_KEY, page);
                startActivity(intent);
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, e);
        }
        return false;
    }

    /**
     * Gets values from shared preferences and sets the corresponding data
     * members in this activity.
     */
    private void retrievePreferences() {
        // Retrieve from preferences, and set in this Activity, the language
        // preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

        mShowOcrResult = mSharedPreferences.getBoolean(PreferencesActivity.KEY_SHOW_OCR_RESULT_PREFERENCE,
                false);

        mShowScanResult = mSharedPreferences.getBoolean(PreferencesActivity.KEY_SHOW_SCAN_RESULT_PREFERENCE,
                true);

        mEnableStreamMode = mSharedPreferences.getBoolean(PreferencesActivity.KEY_ENABLE_STREAM_MODE,
                false);

        mBeepManager.updatePrefs();
    }

    private void CreateCopyNotification() {
        Resources res = getResources();

        Notification.Builder builder = new Notification.Builder(this).setSmallIcon(R.drawable.ic_menu_edit)
                .setContentTitle(res.getString(R.string.notif_scan_to_clipboard_title))
                .setContentText(res.getString(R.string.notif_scan_to_clipboard_summary));

        // Creates an Intent for the Activity
        Intent notifyIntent = new Intent(this, CaptureActivity.class);
        // Sets the Activity to start in a new, empty task
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        notifyIntent.putExtra(COPY_AND_RETURN, true);
        // Creates the PendingIntent
        PendingIntent pendigIntent = PendingIntent.getActivity(this,
                0,
                notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // Puts the PendingIntent into the notification builder
        builder.setContentIntent(pendigIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        notificationManager.notify(NOTIFICATION_ID, builder.getNotification());
    }

    private void RemoveCopyNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // mId allows you to update the notification later on.
        notificationManager.cancel(NOTIFICATION_ID);
    }

    public void setBaseApi(TessBaseAPI baseApi) {
        this.mBaseApi = baseApi;
    }

    @Override
    public Context getContext() {
        return this;
    }
}
