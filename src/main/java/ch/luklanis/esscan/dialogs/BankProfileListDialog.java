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

import java.util.List;

import ch.luklanis.esscan.R;

public class BankProfileListDialog extends DialogFragment {

    List<String> mBanks;
    private DialogInterface.OnClickListener mOnItemClickListener;

    public BankProfileListDialog(List<String> banks) {
        this.mBanks = banks;
    }

    public BankProfileListDialog setItemClickListener(DialogInterface.OnClickListener listener) {
        this.mOnItemClickListener = listener;
        return this;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity()).setTitle(R.string.bank_profile_dialog_title)
                .setItems(mBanks.toArray(new String[mBanks.size()]), mOnItemClickListener)
                .setNeutralButton(R.string.button_cancel, null)
                .create();
    }
}
