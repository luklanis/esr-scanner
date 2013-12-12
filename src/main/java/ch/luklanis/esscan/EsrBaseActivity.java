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
import ch.luklanis.esscan.dialogs.OkDialog;
import ch.luklanis.esscan.dialogs.OptionalOkDialog;

public abstract class EsrBaseActivity extends Activity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected SharedPreferences mSharedPreferences;
    protected ProgressDialog mSendingProgressDialog;
    protected ESRSenderHttp mEsrSenderHttp;
    private boolean mSenderSettingsChanged;
    private DialogFragment mCurrentDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCurrentDialog = null;

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferences.registerOnSharedPreferenceChangeListener(this);

        mSendingProgressDialog = new ProgressDialog(this);
        mSendingProgressDialog.setTitle(R.string.msg_wait_title);
        mSendingProgressDialog.setMessage(getResources().getString(R.string.msg_wait_sending));

        mSenderSettingsChanged = true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mSenderSettingsChanged) {
            reloadEsrSenderHttp();
            mSenderSettingsChanged = false;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PreferencesActivity.KEY_USERNAME) || key.equals(PreferencesActivity.KEY_PASSWORD)) {
            mSenderSettingsChanged = true;
        }
    }

    @Override
    protected void onDestroy() {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    private void reloadEsrSenderHttp() {
        String username = mSharedPreferences.getString(PreferencesActivity.KEY_USERNAME, "");
        String password = mSharedPreferences.getString(PreferencesActivity.KEY_PASSWORD, "");
        try {
            mEsrSenderHttp = null;
            if (!username.isEmpty() && !password.isEmpty()) {
                mEsrSenderHttp = new ESRSenderHttp(getApplicationContext(), username, password);
                mEsrSenderHttp.registerDataSentHandler(getDataSentHandler());
            } else {
                mCurrentDialog = setOptionalOkAlert(R.string.msg_how_to_enable_stream_mode);
            }
        } catch (Exception e) {
            setOkAlert(R.string.msg_send_over_http_not_possible);
            e.printStackTrace();
        }

        invalidateOptionsMenu();
    }

    protected void dismissCurrentDialog() {
        if (mCurrentDialog != null) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }
    }

    /**
     * Displays an error message dialog box to the user on the UI thread.
     *
     * @param title   The title for the dialog box
     * @param message The error message to be displayed
     */
    public DialogFragment showErrorMessage(String title, String message) {
        DialogFragment dialogFragment = new ErrorAlertDialog(title, message);
        dialogFragment.show(getFragmentManager(), "EsrBaseActivity.showErrorMessage");
        return dialogFragment;
    }

    public IEsrSender getEsrSender() {
        return mEsrSenderHttp;
    }

    public void showSendingProgressDialog() {
        mSendingProgressDialog.show();
    }

    public void dismissSendingProgressDialog() {
        mSendingProgressDialog.dismiss();
    }

    protected abstract Handler getDataSentHandler();

    protected DialogFragment setOkAlert(int id) {
        DialogFragment dialogFragment = new OkDialog(id);
        dialogFragment.show(getFragmentManager(), "OkAlert");
        return dialogFragment;
    }

    protected DialogFragment setOkAlert(String message) {
        DialogFragment dialogFragment = new OkDialog(message);
        dialogFragment.show(getFragmentManager(), "OkAlert");
        return dialogFragment;
    }

    protected DialogFragment setOptionalOkAlert(int id) {
        int dontShow = mSharedPreferences.getInt(PreferencesActivity.KEY_NOT_SHOW_ALERT + String.valueOf(
                id), 0);

        if (dontShow == 0) {
            DialogFragment dialogFragment = new OptionalOkDialog(id);
            dialogFragment.show(getFragmentManager(), "EsrBaseActivity.setOptionalOkAlert");
            return dialogFragment;
        }

        return null;
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
}
