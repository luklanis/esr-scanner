/*
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

package ch.luklanis.esscan.history;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import ch.luklanis.esscan.CaptureActivity;
import ch.luklanis.esscan.EsrBaseActivity;
import ch.luklanis.esscan.Intents;
import ch.luklanis.esscan.PreferencesActivity;
import ch.luklanis.esscan.R;
import ch.luklanis.esscan.codesend.ESRSenderHttp;
import ch.luklanis.esscan.codesend.IEsrSender;
import ch.luklanis.esscan.dialogs.BankProfileListDialog;
import ch.luklanis.esscan.dialogs.CancelOkDialog;
import ch.luklanis.esscan.paymentslip.DTAFileCreator;
import ch.luklanis.esscan.paymentslip.PsResult;

public final class HistoryActivity extends EsrBaseActivity
        implements HistoryFragment.HistoryCallbacks, Handler.Callback {

    public static final String ACTION_SHOW_RESULT = "action_show_result";
    public static final String EXTRA_CODE_ROW = "extra_code_row";
    private static final int DETAILS_REQUEST_CODE = 0;
    final private SearchView.OnQueryTextListener queryListener = new SearchView.OnQueryTextListener() {

        @Override
        public boolean onQueryTextChange(String newText) {
            if (TextUtils.isEmpty(newText)) {
                getActionBar().setSubtitle("History");
            } else {
                getActionBar().setSubtitle("History - Searching for: " + newText);
            }

            HistoryItemAdapter adapter = historyFragment.getHistoryItemAdapter();

            if (adapter != null) {
                adapter.getFilter().filter(newText);
            }
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(String query) {
            Toast.makeText(getApplication(), "Searching for: " + query + "...", Toast.LENGTH_SHORT)
                    .show();
            return true;
        }
    };
    private boolean twoPane;
    private HistoryManager mHistoryManager;
    private DTAFileCreator dtaFileCreator;
    private Handler mDataSentHandler = new Handler(this);
    private int[] tmpPositions;
    private HistoryFragment historyFragment;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_history);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        this.historyFragment = (HistoryFragment) getFragmentManager().findFragmentById(R.id.history);

        if (findViewById(R.id.ps_detail_container) != null) {
            twoPane = true;
            this.historyFragment.setActivateOnItemClick(true);
        }

        tmpPositions = new int[2];
        tmpPositions[0] = ListView.INVALID_POSITION; // old position
        tmpPositions[1] = ListView.INVALID_POSITION; // new position

        dtaFileCreator = new DTAFileCreator(this);
        mHistoryManager = new HistoryManager(getApplicationContext());

        Intent intent = getIntent();

        if (intent.getAction() != null && intent.getAction().equals(ACTION_SHOW_RESULT)) {
            String codeRow = intent.getStringExtra(EXTRA_CODE_ROW);
            PsResult psResult = PsResult.getInstance(codeRow);
            this.mHistoryManager.addHistoryItem(psResult);

            if (twoPane) {
                setNewDetails(0);
                intent.setAction(null);
            } else {
                Intent detailIntent = new Intent(this, PsDetailActivity.class);
                detailIntent.putExtra(PsDetailFragment.ARG_POSITION, 0);
                startActivityForResult(detailIntent, DETAILS_REQUEST_CODE);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mHistoryManager.hasHistoryItems()) {
            getMenuInflater().inflate(R.menu.history_menu, menu);

            SearchView searchView = (SearchView) menu.findItem(R.id.history_menu_search)
                    .getActionView();
            searchView.setOnQueryTextListener(queryListener);

            MenuItem copyItem = menu.findItem(R.id.history_menu_copy_code_row);
            MenuItem sendItem = menu.findItem(R.id.history_menu_send_code_row);

            if (twoPane && this.historyFragment
                    .getActivatedPosition() != ListView.INVALID_POSITION) {
                copyItem.setVisible(true);

                sendItem.setVisible(true);
            } else {
                copyItem.setVisible(false);
                sendItem.setVisible(false);
            }

            MenuItem item = menu.findItem(R.id.history_menu_send_dta);

            if (dtaFileCreator.getFirstErrorId() != 0) {
                item.setVisible(false);
            }

            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.history_menu_send_dta_save:
            case R.id.history_menu_send_dta_other:
            case R.id.history_menu_send_dta_email: {
                Message message = Message.obtain(mDataSentHandler, item.getItemId());
                createDTAFile(message);
            }
            break;
            case R.id.history_menu_send_csv: {
                CharSequence history = mHistoryManager.buildHistory();
                Uri historyFile = HistoryManager.saveHistory(history.toString());

                String[] recipients = new String[]{PreferenceManager.getDefaultSharedPreferences(
                        this).getString(PreferencesActivity.KEY_EMAIL_ADDRESS, "")};

                if (historyFile == null) {
                    setOkAlert(R.string.msg_unmount_usb);
                } else {
                    Intent intent = new Intent(Intent.ACTION_SEND, Uri.parse("mailto:"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                    intent.putExtra(Intent.EXTRA_EMAIL, recipients);
                    String subject = getResources().getString(R.string.history_email_title);
                    intent.putExtra(Intent.EXTRA_SUBJECT, subject);
                    intent.putExtra(Intent.EXTRA_TEXT, subject);
                    intent.putExtra(Intent.EXTRA_STREAM, historyFile);
                    intent.setType("text/csv");
                    startActivity(intent);
                }
            }
            break;
            case R.id.history_menu_clear: {
                new CancelOkDialog(R.string.msg_sure).setOkClickListener(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mHistoryManager.clearHistory();
                        dialogInterface.dismiss();
                        finish();
                    }
                }).show(getFragmentManager(), "HistoryActivity.onOptionsItemSelected");
            }
            break;
            case android.R.id.home: {

                int error = PsDetailActivity.savePaymentSlip(this);

                if (error > 0) {
                    setCancelOkAlert(this, error);
                    return true;
                }

                NavUtils.navigateUpTo(this, new Intent(this, CaptureActivity.class));
                return true;
            }
            case R.id.history_menu_copy_code_row: {
                PsDetailFragment fragment = (PsDetailFragment) getFragmentManager().findFragmentById(
                        R.id.ps_detail_container);

                if (fragment != null) {
                    String completeCode = fragment.getHistoryItem().getResult().getCompleteCode();

                    addCodeRowToClipboard(completeCode);
                }
            }
            break;
            case R.id.history_menu_send_code_row: {
                PsDetailFragment fragment = (PsDetailFragment) getFragmentManager().findFragmentById(
                        R.id.ps_detail_container);

                if (fragment != null) {
                    IEsrSender sender = getEsrSender();

                    if (sender != null) {
                        mSendingProgressDialog.show();

                        fragment.send(PsDetailFragment.SEND_COMPONENT_CODE_ROW,
                                sender,
                                this.historyFragment.getActivatedPosition());
                    } else {
                        Message message = Message.obtain(mDataSentHandler, R.id.es_send_failed);
                        message.sendToTarget();
                    }
                }
            }
            break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void setCancelOkAlert(final Activity activity, int id) {
        new CancelOkDialog(id).setOkClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                activity.finish();
            }
        }).show(getFragmentManager(), "HistoryActivity.setCancelOkAlert");
    }

    @Override
    public void selectTopInTwoPane() {
        if (twoPane) {
            onItemSelected(ListView.INVALID_POSITION, 0);
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onItemSelected(int oldPosition, int newPosition) {

        if (twoPane) {
            tmpPositions[0] = ListView.INVALID_POSITION;
            tmpPositions[1] = ListView.INVALID_POSITION;
            PsDetailFragment oldFragment;
            oldFragment = (PsDetailFragment) getFragmentManager().findFragmentById(R.id.ps_detail_container);
            if (oldPosition != ListView.INVALID_POSITION && oldFragment != null) {
                int error = oldFragment.save();

                HistoryItem item = mHistoryManager.buildHistoryItem(oldPosition);
                this.historyFragment.updatePosition(oldPosition, item);

                if (error > 0) {
                    tmpPositions[0] = oldPosition;
                    tmpPositions[1] = newPosition;
                    setCancelOkAlert(error, false);
                    return;
                }
            } else {
                invalidateOptionsMenu();
            }

            setNewDetails(newPosition);

        } else {
            Intent detailIntent = new Intent(this, PsDetailActivity.class);
            detailIntent.putExtra(PsDetailFragment.ARG_POSITION, newPosition);
            startActivityForResult(detailIntent, DETAILS_REQUEST_CODE);
        }
    }

    private void setCancelOkAlert(int msgId, boolean finish) {
        CancelOkDialog cancelOkDialog = new CancelOkDialog(msgId).setCancelClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                historyFragment.setActivatedPosition(tmpPositions[0]);

                tmpPositions[0] = ListView.INVALID_POSITION;
                tmpPositions[1] = ListView.INVALID_POSITION;
            }
        });

        if (finish) {
            cancelOkDialog.setOkClickListener(new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
        } else {
            cancelOkDialog.setOkClickListener(new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setNewDetails(tmpPositions[1]);

                    tmpPositions[0] = ListView.INVALID_POSITION;
                    tmpPositions[1] = ListView.INVALID_POSITION;
                }
            });
        }
        cancelOkDialog.show(getFragmentManager(), "HistoryActivity.onItemSelected");
    }

    @Override
    public int getPositionToActivate() {
        PsDetailFragment fragment = (PsDetailFragment) getFragmentManager().findFragmentById(R.id.ps_detail_container);
        if (fragment != null) {
            return fragment.getListPosition();
        }

        return -1;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_OK && requestCode == DETAILS_REQUEST_CODE) {

            if (intent.hasExtra(Intents.History.ITEM_NUMBER)) {
                int position = intent.getIntExtra(Intents.History.ITEM_NUMBER, -1);

                HistoryItem item = mHistoryManager.buildHistoryItem(position);

                this.historyFragment.updatePosition(position, item);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (twoPane) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

            String username = prefs.getString(PreferencesActivity.KEY_USERNAME, "");
            String password = prefs.getString(PreferencesActivity.KEY_PASSWORD, "");
            try {
                if (!username.isEmpty() && !password.isEmpty()) {
                    mEsrSenderHttp = new ESRSenderHttp(getApplicationContext(), username, password);
                    mEsrSenderHttp.registerDataSentHandler(mDataSentHandler);
                }
            } catch (Exception e) {
                setOkAlert(R.string.msg_send_over_http_not_possible);
                e.printStackTrace();
            }
        }

        int error = dtaFileCreator.getFirstErrorId();

        if (error != 0) {
            setOptionalOkAlert(error);
        } else {
        }
    }

    @Override
    protected Handler getDataSentHandler() {
        return mDataSentHandler;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            PsDetailFragment oldFragment = (PsDetailFragment) getFragmentManager().findFragmentById(
                    R.id.ps_detail_container);
            if (oldFragment != null) {
                int error = oldFragment.save();

                if (error > 0) {
                    setCancelOkAlert(error, true);
                    return true;
                }
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean handleMessage(Message message) {
        int msgId = 0;


        switch (message.what) {
            case R.id.history_menu_send_dta_email: {
                Uri dtaFileUri = (Uri) message.obj;
                if (dtaFileUri != null) {
                    try {
                        startActivity(createMailIntent(dtaFileUri));
                        return true;
                    } catch (Exception ex) {
                        msgId = R.string.msg_no_email_client;
                    }
                }
            }
            break;
            case R.id.history_menu_send_dta_other: {
                Uri dtaFileUri = (Uri) message.obj;
                if (dtaFileUri != null) {
                    startActivity(Intent.createChooser(createShareIntent(dtaFileUri),
                            "Send with..."));
                    return true;
                }
            }
            break;
            case R.id.history_menu_send_dta_save:
                break;
            case R.id.es_send_succeeded: {
                mSendingProgressDialog.dismiss();

                HistoryItem historyItem = historyFragment.getHistoryItemOnPosition(message.arg1);

                if (historyItem != null) {
                    String filename = getResources().getString(R.string.history_item_sent);
                    historyItem.update(new HistoryItem.Builder(historyItem).setDtaFile(filename)
                            .create());
                    mHistoryManager.updateHistoryItemFileName(historyItem.getItemId(), filename);

                    //historyFragment.updatePosition(message.arg1, historyItem);
                    historyFragment.dataChanged();

                    PsDetailFragment fragment = (PsDetailFragment) getFragmentManager().findFragmentById(
                            R.id.ps_detail_container);

                    if (fragment != null) {
                        fragment.getHistoryItem().update(historyItem);
                        fragment.updateDtaFilename(fragment.getView());
                    }
                }

                msgId = R.string.msg_coderow_sent;
            }
            break;
            case R.id.es_send_failed:
                mSendingProgressDialog.dismiss();
                msgId = R.string.msg_coderow_not_sent;
                break;
        }

        if (msgId != 0) {
            Toast toast = Toast.makeText(getApplicationContext(), msgId, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.BOTTOM, 0, 0);
            toast.show();
            return true;
        }

        return false;
    }

    private void setNewDetails(int position) {
        Bundle arguments = new Bundle();
        arguments.putInt(PsDetailFragment.ARG_POSITION, position);
        PsDetailFragment fragment = new PsDetailFragment();
        fragment.setArguments(arguments);

        getFragmentManager().beginTransaction()
                .replace(R.id.ps_detail_container, fragment)
                .commit();
    }

    private Intent createShareIntent(Uri dtaFileUri) {
        return createShareIntent("text/plain", dtaFileUri);
    }

    private Intent createMailIntent(Uri dtaFileUri) {
        return createShareIntent("message/rfc822", dtaFileUri);
    }

    private Intent createShareIntent(String mime, Uri dtaFileUri) {
        String[] recipients = new String[]{PreferenceManager.getDefaultSharedPreferences(this)
                .getString(PreferencesActivity.KEY_EMAIL_ADDRESS, "")};
        String subject = getResources().getString(R.string.history_share_as_dta_title);
        String text = String.format(getResources().getString(R.string.history_share_as_dta_summary),
                dtaFileUri.getPath());

        Intent intent = new Intent(Intent.ACTION_SEND, Uri.parse("mailto:"));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
        intent.setType(mime);

        intent.putExtra(Intent.EXTRA_EMAIL, recipients);
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, text);

        intent.putExtra(Intent.EXTRA_STREAM, dtaFileUri);

        return intent;
    }

    private void createDTAFile(final Message message) {
        List<BankProfile> bankProfiles = mHistoryManager.getBankProfiles();

        List<String> banks = new ArrayList<String>();

        for (BankProfile bankProfile : bankProfiles) {
            banks.add(bankProfile.getName());
        }

        new BankProfileListDialog(banks).setItemClickListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                long bankProfileId = mHistoryManager.getBankProfileId(which);
                Uri uri = createDTAFile(bankProfileId);
                message.obj = uri;
                message.sendToTarget();
            }
        }).show(getFragmentManager(), "HistoryActivity.createDTAFile");
    }

    private Uri createDTAFile(long bankProfileId) {
        List<HistoryItem> historyItems = mHistoryManager.buildHistoryItemsForDTA(bankProfileId);
        BankProfile bankProfile = mHistoryManager.getBankProfile(bankProfileId);

        String error = dtaFileCreator.getFirstError(bankProfile, historyItems);

        if (!TextUtils.isEmpty(error)) {
            setOkAlert(error);
            return null;
        }

        CharSequence dta = dtaFileCreator.buildDTA(bankProfile, historyItems);

        if (!dtaFileCreator.saveDTAFile(dta.toString())) {
            setOkAlert(R.string.msg_unmount_usb);
            return null;
        } else {
            Uri dtaFileUri = dtaFileCreator.getDTAFileUri();
            String dtaFileName = dtaFileUri.getLastPathSegment();

            new HistoryExportUpdateAsyncTask(mHistoryManager,
                    dtaFileName).execute(historyItems.toArray(new HistoryItem[historyItems.size()]));

            this.dtaFileCreator = new DTAFileCreator(getApplicationContext());

            Toast toast = Toast.makeText(this,
                    getResources().getString(R.string.msg_dta_saved, dtaFileUri.getPath()),
                    Toast.LENGTH_LONG);
            toast.setGravity(Gravity.BOTTOM, 0, 0);
            toast.show();

            return dtaFileUri;
        }
    }
}
