package ch.luklanis.esscan;/*
 * Copyright 2013 Lukas Landis
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.widget.Toast;

import ch.luklanis.esscan.codesend.ESRSenderHttp;
import ch.luklanis.esscan.codesend.IEsrSender;
import ch.luklanis.esscan.dialogs.OkAlertDialog;
import ch.luklanis.esscan.dialogs.OptionalOkAlertDialog;

public abstract class EsrBaseActivity extends Activity {
    protected SharedPreferences mSharedPreferences;
    protected ProgressDialog mSendingProgressDialog;
    protected ESRSenderHttp mEsrSenderHttp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        mSendingProgressDialog = new ProgressDialog(this);
        mSendingProgressDialog.setTitle(R.string.msg_wait_title);
        mSendingProgressDialog.setMessage(getResources().getString(R.string.msg_wait_sending));
    }

    @Override
    protected void onResume() {
        super.onResume();

        String username = mSharedPreferences.getString(PreferencesActivity.KEY_USERNAME, "");
        String password = mSharedPreferences.getString(PreferencesActivity.KEY_PASSWORD, "");
        try {
            if (!username.isEmpty() && !password.isEmpty()) {
                mEsrSenderHttp = new ESRSenderHttp(getApplicationContext(), username, password);
                mEsrSenderHttp.registerDataSentHandler(getDataSentHandler());
            }
        } catch (Exception e) {
            setOptionalOkAlert(R.string.msg_send_over_http_not_possible);
            e.printStackTrace();
        }
    }

    /**
     * Displays an error message dialog box to the user on the UI thread.
     *
     * @param title   The title for the dialog box
     * @param message The error message to be displayed
     */
    public void showErrorMessage(String title, String message) {
        new ErrorAlertDialog(title, message).show(getFragmentManager(),
                "EsrBaseActivity.showErrorMessage");
    }

    public IEsrSender getEsrSender() {
        return mEsrSenderHttp;
    }

    protected abstract Handler getDataSentHandler();

    protected class ErrorAlertDialog extends DialogFragment {
        private final String title;
        private String message;

        public ErrorAlertDialog(String message, String title) {
            this.message = message;
            this.title = title;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity()).setTitle(title)
                    .setMessage(message)
                    .setOnCancelListener(new FinishListener(getActivity()))
                    .setPositiveButton("Done", new FinishListener(getActivity()))
                    .create();
        }
    }

    protected void setOkAlert(int id) {
        new OkAlertDialog(id).show(getFragmentManager(), "OkAlert");
    }

    protected void setOkAlert(String message) {
        new OkAlertDialog(message).show(getFragmentManager(), "OkAlert");
    }

    protected void setOptionalOkAlert(int id) {
        int dontShow = mSharedPreferences.getInt(PreferencesActivity.KEY_NOT_SHOW_ALERT + String.valueOf(
                id), 0);

        if (dontShow == 0) {
            new OptionalOkAlertDialog(id).show(getFragmentManager(),
                    "EsrBaseActivity.setOptionalOkAlert");
        }
    }

    protected void addCodeRowToClipboard(String toCopy) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboardManager.setPrimaryClip(ClipData.newPlainText("new code row", toCopy));

        Toast toast = Toast.makeText(getApplicationContext(),
                getResources().getString(clipboardManager.hasPrimaryClip() ? R.string.msg_copied : R.string.msg_not_copied),
                Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM, 0, 0);
        toast.show();
    }
}
