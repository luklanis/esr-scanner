/**
 * 
 */
package ch.luklanis.esscan.ime;

import java.io.File;
import java.io.IOException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.googlecode.tesseract.android.TessBaseAPI;

import ch.luklanis.esscan.BeepManager;
import ch.luklanis.esscan.CaptureActivity;
import ch.luklanis.esscan.CaptureActivityHandler;
import ch.luklanis.esscan.FinishListener;
import ch.luklanis.esscan.HelpActivity;
import ch.luklanis.esscan.IBase;
import ch.luklanis.esscan.OcrInitAsyncTask;
import ch.luklanis.esscan.OcrResult;
import ch.luklanis.esscan.OcrResultText;
import ch.luklanis.esscan.PreferencesActivity;
import ch.luklanis.esscan.R;
import ch.luklanis.esscan.ViewfinderView;
import ch.luklanis.esscan.camera.CameraManager;
import ch.luklanis.esscan.language.LanguageCodeHelper;
import ch.luklanis.esscan.paymentslip.EsIbanValidation;
import ch.luklanis.esscan.paymentslip.EsResult;
import ch.luklanis.esscan.paymentslip.EsrResult;
import ch.luklanis.esscan.paymentslip.EsrValidation;
import ch.luklanis.esscan.paymentslip.PsResult;
import ch.luklanis.esscan.paymentslip.PsValidation;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.inputmethodservice.ExtractEditText;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.KeyboardView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * @author llandis
 * 
 */
public class ScannerIME extends InputMethodService implements
		KeyboardView.OnKeyboardActionListener, SurfaceHolder.Callback, IBase {

	private static final String TAG = CaptureActivity.class.getSimpleName();

	/** The default OCR engine to use. */
	public static final String DEFAULT_OCR_ENGINE_MODE = "Tesseract";

	public static final String EXTERNAL_STORAGE_DIRECTORY = "ESRScan";

	private static final int OCR_ENGINE_MODE = TessBaseAPI.OEM_TESSERACT_ONLY;
	private static final String SOURCE_LANGUAGE_CODE_OCR = "psl"; // ISO 639-3
																	// language
																	// code

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
	private String mSourceLanguageReadable; // Language name, for example,
	// "English"

	private SharedPreferences mSharedPreferences;
	private OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener;
	
	private PsValidation mPsValidation;

	private int mLastValidationStep;

	private boolean mShowOcrResult;

	private TextView mStatusViewBottomRight;

	private RelativeLayout mInputView;

	private BeepManager mBeepManager;

	private ExtractEditText mExtractEditText;

	@Override
	public void onStartInputView(EditorInfo info, boolean restarting) {
		super.onStartInputView(info, restarting);
		
		if (mInputView == null) {
			return;
		}
		
		updateFullscreenMode();

		mSurfaceView = (SurfaceView) mInputView.findViewById(R.id.preview_view);
		mCameraManager = new CameraManager(mSurfaceView);

		mViewfinderView = (ViewfinderView) mInputView
				.findViewById(R.id.viewfinder_view);
		
		mViewfinderView.setCameraManager(mCameraManager);

		mStatusViewBottomLeft = (TextView) mInputView
				.findViewById(R.id.status_view_bottom_left);

		mSharedPreferences = PreferenceManager
				.getDefaultSharedPreferences(this);

		mBeepManager = new BeepManager(this);


		mStatusViewBottomRight = (TextView) mInputView
				.findViewById(R.id.status_view_bottom_right);
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
				initOcrEngine(storageDirectory, SOURCE_LANGUAGE_CODE_OCR,
						mSourceLanguageReadable);
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
		if (mInputView == null) {
			return;
		}
		
		if (mCaptureActivityHandler != null) {
			mCaptureActivityHandler.quitSynchronously();
			mCaptureActivityHandler = null;
		}

		// Stop using the camera, to avoid conflicting with other camera-based
		// apps
		mCameraManager.closeDriver();

		if (!mHasSurface) {
			SurfaceView surfaceView = (SurfaceView) mInputView
					.findViewById(R.id.preview_view);
			SurfaceHolder surfaceHolder = surfaceView.getHolder();
			surfaceHolder.removeCallback(this);
		}
		
		super.onFinishInputView(finishingInput);
	}

	@Override
	public void onFinishInput() {
		super.onFinishInput();
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
			mBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST,
					CHARACTER_WHITELIST);
		}
	}

	/** Called to resume recognition after translation in continuous mode. */
	void restartPreviewAfterDelay(long delayMS) {
		if (mCaptureActivityHandler != null) {
			mCaptureActivityHandler.sendEmptyMessageDelayed(
					R.id.restart_decode, delayMS);
		}

		resumeOcrEngine();
		resetStatusView();
	}

	/** Initializes the camera and starts the handler to begin previewing. */
	private void initCamera(SurfaceHolder surfaceHolder) {
		Log.d(TAG, "initCamera()");
		try {
			// Open and initialize the camera
			mCameraManager.openDriver(surfaceHolder);

			if (mCaptureActivityHandler == null) {
				// Creating the handler starts the preview, which can also throw
				// a RuntimeException.
				mCaptureActivityHandler = new CaptureActivityHandler(this,
						mCameraManager);

				if (mBaseApi != null) {
					mCaptureActivityHandler.startDecode(mBaseApi);
				}
			}
		} catch (IOException ioe) {
			showErrorMessage("Error",
					"Could not initialize camera. Please try restarting device.");
		} catch (RuntimeException e) {
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			showErrorMessage("Error",
					"Could not initialize camera. Please try restarting device.");
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
		mHasSurface = false;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
	}

	/** Finds the proper location on the SD card where we can save files. */
	private File getOldTessdataDirectory() {
		// Log.d(TAG, "getStorageDirectory(): API level is " +
		// Integer.valueOf(android.os.Build.VERSION.SDK_INT));

		String state = null;
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
	 * @param storageRoot
	 *            Path to location of the tessdata directory to use
	 * @param languageCode
	 *            Three-letter ISO 639-3 language code for OCR
	 * @param languageName
	 *            Name of the language for OCR, for example, "English"
	 */
	private void initOcrEngine(File storageRoot, String languageCode,
			String languageName) {
		// Start AsyncTask to install language data and init OCR
		new OcrInitAsyncTask(this, new TessBaseAPI(), null, languageCode,
				languageName, OCR_ENGINE_MODE).execute(storageRoot.toString());
	}

	public void showResult(PsResult psResult) {
		mBeepManager.playBeepSoundAndVibrate();
		showResult(psResult, false);
	}

	public void showResult(PsResult psResult, boolean fromHistory) {
		InputConnection inputConnection = getCurrentInputConnection();
		inputConnection.commitText(psResult.getCompleteCode(), 1);
		
		this.hideWindow();

//		mPsValidation.gotoBeginning(false);
//		mLastValidationStep = mPsValidation.getCurrentStep();
//		restartPreviewAfterDelay(2000L);
	}

	/**
	 * Displays information relating to the results of a successful real-time
	 * OCR request.
	 * 
	 * @param ocrResult
	 *            Object representing successful OCR results
	 */
	public void presentOcrDecodeResult(OcrResult ocrResult) {

		// Send an OcrResultText object to the ViewfinderView for text rendering
		mViewfinderView.addResultText(new OcrResultText(ocrResult.getText(),
				ocrResult.getWordConfidences(), ocrResult.getMeanConfidence(),
				ocrResult.getBitmapDimensions(), ocrResult
						.getCharacterBoundingBoxes(), ocrResult
						.getWordBoundingBoxes(), ocrResult
						.getTextlineBoundingBoxes(), ocrResult
						.getRegionBoundingBoxes()));

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
	 * 
	 * NOTE: This method was adapted from:
	 * http://www.androidengineer.com/2010/08
	 * /easy-method-for-formatting-android.html
	 * 
	 * <p>
	 * For example, {@code setSpanBetweenTokens("Hello ##world##!", "##", new
	 * ForegroundColorSpan(0xFFFF0000));} will return a CharSequence
	 * {@code "Hello world!"} with {@code world} in red.
	 * 
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

		View orangeStatusView = mInputView
				.findViewById(R.id.status_view_top_orange);
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
			statusView1 = (TextView) mInputView
					.findViewById(R.id.status_view_1_orange);
			statusView2 = (TextView) mInputView
					.findViewById(R.id.status_view_2_orange);
			statusView3 = (TextView) mInputView
					.findViewById(R.id.status_view_3_orange);
		} else {
			statusView1 = (TextView) mInputView
					.findViewById(R.id.status_view_1_red);
			statusView2 = (TextView) mInputView
					.findViewById(R.id.status_view_2_red);
			statusView3 = (TextView) mInputView
					.findViewById(R.id.status_view_3_red);
		}

		statusView1.setBackgroundResource(0);
		statusView2.setBackgroundResource(0);
		statusView3.setBackgroundResource(0);

		switch (this.mPsValidation.getCurrentStep()) {
		case 1:
			statusView1
					.setBackgroundResource(R.drawable.status_view_background);
			break;
		case 2:
			statusView2
					.setBackgroundResource(R.drawable.status_view_background);
			break;
		case 3:
			statusView3
					.setBackgroundResource(R.drawable.status_view_background);
			break;

		default:
			break;
		}
	}

	/** Request the viewfinder to be invalidated. */
	public void drawViewfinder() {
		mViewfinderView.drawViewfinder();
	}

	private void DeleteRecursive(File fileOrDirectory) {
		if (fileOrDirectory.isDirectory())
			for (File child : fileOrDirectory.listFiles())
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
			PackageInfo info = getPackageManager().getPackageInfo(
					getPackageName(), 0);
			int currentVersion = info.versionCode;
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(this);
			int lastVersion = prefs.getInt(
					PreferencesActivity.KEY_HELP_VERSION_SHOWN, 0);

			if (currentVersion > lastVersion) {

				File oldStorage = getOldTessdataDirectory();

				if (oldStorage != null && oldStorage.exists()) {
					DeleteRecursive(new File(oldStorage.toString()));
				}

				// Record the last version for which we last displayed the
				// What's New (Help) page
				prefs.edit()
						.putInt(PreferencesActivity.KEY_HELP_VERSION_SHOWN,
								currentVersion).commit();
				Intent intent = new Intent(this, HelpActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);

				// Show the default page on a clean install, and the what's new
				// page on an upgrade.
				String page = lastVersion == 0 ? HelpActivity.DEFAULT_PAGE
						: HelpActivity.WHATS_NEW_PAGE;
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
		mSourceLanguageReadable = LanguageCodeHelper.getOcrLanguageName(this,
				SOURCE_LANGUAGE_CODE_OCR);

		mSharedPreferences
				.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);

		mShowOcrResult = mSharedPreferences.getBoolean(
				PreferencesActivity.KEY_SHOW_OCR_RESULT_PREFERENCE, false);

		mBeepManager.updatePrefs();
	}

	/**
	 * Displays an error message dialog box to the user on the UI thread.
	 * 
	 * @param title
	 *            The title for the dialog box
	 * @param message
	 *            The error message to be displayed
	 */
	public void showErrorMessage(String title, String message) {
		mStatusViewBottomRight.setText(message);
	}

	private void setOKAlert(Context context, int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(id);
		builder.setPositiveButton(R.string.button_ok, null);
		builder.show();
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

		Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

		int screenWidth = display.getWidth();
		int screenHeight = display.getHeight();
		
		if (screenWidth < screenHeight) {
			mInputView = null;
			TextView textView = new TextView(this);
			textView.setText(getResources().getString(R.string.msg_unsupported_orientation));
			return textView;
		}
		
		mInputView = (RelativeLayout) getLayoutInflater().inflate(
				R.layout.input, null);

		mExtractEditText = new ExtractEditText(this);
		mExtractEditText.setId(android.R.id.inputExtractEditText);
		setExtractView(mExtractEditText);
		setExtractViewShown(true);
		
		return mInputView;
	}

	@Override
	public void onKey(int primaryCode, int[] keyCodes) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onPress(int primaryCode) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onRelease(int primaryCode) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onText(CharSequence text) {
		// TODO Auto-generated method stub

	}

	@Override
	public void swipeDown() {
		// TODO Auto-generated method stub

	}

	@Override
	public void swipeLeft() {
		// TODO Auto-generated method stub

	}

	@Override
	public void swipeRight() {
		// TODO Auto-generated method stub

	}

	@Override
	public void swipeUp() {
		// TODO Auto-generated method stub

	}

	@Override
	public Context getContext() {
		return this;
	}

	@Override
	public boolean onEvaluateFullscreenMode() {
		return true;
	}

}
