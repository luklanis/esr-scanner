package ch.luklanis.esscan;

import com.googlecode.tesseract.android.TessBaseAPI;

import android.content.Context;
import android.os.Handler;
import ch.luklanis.esscan.camera.CameraManager;
import ch.luklanis.esscan.paymentslip.PsResult;
import ch.luklanis.esscan.paymentslip.PsValidation;

public interface IBase {

	void presentOcrDecodeResult(OcrResult obj);

	void drawViewfinder();

	void showResult(PsResult result);

	CameraManager getCameraManager();

	PsValidation getValidation();

	Handler getHandler();

	Context getContext();

	void setBaseApi(TessBaseAPI baseApi);

	void resumeOcrEngine();

	void showErrorMessage(String string, String string2);
	
	void showDialogAndRestartScan(int resourceId);
}
