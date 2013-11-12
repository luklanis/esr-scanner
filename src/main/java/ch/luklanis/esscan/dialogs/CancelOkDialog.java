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

import ch.luklanis.esscan.R;

public class CancelOkDialog extends DialogFragment {
    private final int mTitle;
    private int mMsgId;
    private DialogInterface.OnClickListener mOkClickListener;
    private DialogInterface.OnClickListener mCancelClickListener;

    public CancelOkDialog(int msgId) {
        this(-1, msgId);
    }

    public CancelOkDialog(int title, int msgId) {
        this.mTitle = title;
        this.mMsgId = msgId;
        mOkClickListener = null;
        mCancelClickListener = null;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity()).setMessage(mMsgId)
                .setNeutralButton(R.string.button_cancel, mCancelClickListener)
                .setPositiveButton(R.string.button_ok, mOkClickListener);

        if (mTitle != -1) {
            builder.setTitle(mTitle);
        }

        return builder.create();
    }

    public CancelOkDialog setOkClickListener(DialogInterface.OnClickListener onClickListener) {
        this.mOkClickListener = onClickListener;
        return this;
    }

    public CancelOkDialog setCancelClickListener(DialogInterface.OnClickListener onClickListener) {
        this.mCancelClickListener = onClickListener;
        return this;
    }
}
