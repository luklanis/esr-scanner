package ch.luklanis.esscan.dialogs;/*
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;

import ch.luklanis.esscan.PreferencesActivity;
import ch.luklanis.esscan.R;

public class OptionalOkAlertDialog extends DialogFragment {
    private final int mMsgId;

    public OptionalOkAlertDialog(int msgId) {
        this.mMsgId = msgId;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final View checkboxLayout = inflater.inflate(R.layout.dont_show_again, null);
        final CheckBox dontShowAgainCheckBox = (CheckBox) checkboxLayout.findViewById(R.id.dont_show_again_checkbox);

        return new AlertDialog.Builder(getActivity()).setTitle(R.string.alert_title_information)
                .setMessage(mMsgId)
                .setView(checkboxLayout)
                .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (dontShowAgainCheckBox.isChecked()) {
                            PreferenceManager.getDefaultSharedPreferences(getActivity())
                                    .edit()
                                    .putInt(PreferencesActivity.KEY_NOT_SHOW_ALERT + String.valueOf(
                                            mMsgId), 1)
                                    .apply();
                        }
                    }
                })
                .create();
    }
}
