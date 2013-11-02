package ch.luklanis.esscan.history;/*
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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import ch.luklanis.esscan.R;

public class BankProfileDialogFragment extends DialogFragment {

    private BankProfile bankProfile;
    private DialogInterface.OnClickListener mOnChooseBankClickListener;
    private DialogInterface.OnClickListener mOnSaveBankClickListener;
    private DialogInterface.OnClickListener mOnCancelClickListener;

    public BankProfileDialogFragment(BankProfile bankProfile) {
        this.bankProfile = bankProfile;
    }

    public BankProfileDialogFragment() {
        this.bankProfile = null;
    }

    public BankProfile getBankProfile() {
        return bankProfile;
    }

    public void setOnChooseBankClickListener(Dialog.OnClickListener onChooseBankClickListener) {
        this.mOnChooseBankClickListener = onChooseBankClickListener;
    }

    public void setOnSaveBankClickListener(Dialog.OnClickListener onSaveBankClickListener) {
        this.mOnSaveBankClickListener = onSaveBankClickListener;
    }

    public void setOnCancelClickListener(DialogInterface.OnClickListener mOnCancelClickListener) {
        this.mOnCancelClickListener = mOnCancelClickListener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        View view = inflater.inflate(R.layout.fragment_bank, null);

        final EditText name = (EditText) view.findViewById(R.id.bank_profile_name);

        final EditText iban = (EditText) view.findViewById(R.id.bank_profile_iban);

        final TextView execution = (TextView) view.findViewById(R.id.bank_profile_execution);

        if (bankProfile != null) {
            name.setText(bankProfile.getName());
            iban.setText(bankProfile.getIban(""));
            execution.setText(String.valueOf(bankProfile.getExecutionDay(26)));
        }

        execution.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final AlertDialog.Builder dayPicker = new AlertDialog.Builder(getActivity());
                dayPicker.setItems(R.array.execution_day, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        execution.setText(String.format("%d", i + 1));
                    }
                }).show();
            }
        });

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        builder.setView(view)
                // Add action buttons
                .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (mOnCancelClickListener != null) {
                            mOnCancelClickListener.onClick(dialog, id);
                        }
                    }
                }).setPositiveButton(R.string.button_save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (mOnSaveBankClickListener != null) {
                    if (bankProfile == null) {
                        bankProfile = new BankProfile(name.getText().toString(),
                                iban.getText().toString(),
                                execution.getText().toString());
                    }
                    mOnSaveBankClickListener.onClick(dialogInterface, i);
                }
            }
        }).setNeutralButton(R.string.button_choose_another, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (mOnChooseBankClickListener != null) {
                    mOnChooseBankClickListener.onClick(dialogInterface, i);
                }
            }
        });
        return builder.create();
    }
}
