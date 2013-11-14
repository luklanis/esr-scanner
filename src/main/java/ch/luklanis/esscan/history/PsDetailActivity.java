package ch.luklanis.esscan.history;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.NavUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.Toast;

import ch.luklanis.esscan.EsrBaseActivity;
import ch.luklanis.esscan.Intents;
import ch.luklanis.esscan.R;
import ch.luklanis.esscan.codesend.IEsrSender;
import ch.luklanis.esscan.dialogs.CancelOkDialog;

public class PsDetailActivity extends EsrBaseActivity implements Handler.Callback {

    private HistoryManager historyManager;
    private final Handler mDataSentHandler = new Handler(this);

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_ps_detail);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        if (icicle == null) {
            Bundle arguments = new Bundle();
            arguments.putInt(PsDetailFragment.ARG_POSITION,
                    getIntent().getIntExtra(PsDetailFragment.ARG_POSITION, 0));
            PsDetailFragment fragment = new PsDetailFragment();
            fragment.setArguments(arguments);
            getFragmentManager().beginTransaction().add(R.id.ps_detail_container, fragment)
                    .commit();
        }

        historyManager = new HistoryManager(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = new Intent(this, HistoryActivity.class);
        intent.putExtra(Intents.History.ITEM_NUMBER,
                getIntent().getIntExtra(PsDetailFragment.ARG_POSITION, ListView.INVALID_POSITION));
        setResult(Activity.RESULT_OK, intent);
    }

    @Override
    protected Handler getDataSentHandler() {
        return mDataSentHandler;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.details_menu, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {

                int error = savePaymentSlip(this);

                if (error > 0) {
                    setCancelOkAlertDialog(error);
                    new CancelOkDialog(error).show(getFragmentManager(),
                            "PsDetailActivity.setCancelOkAlert");
                    return true;
                }

                NavUtils.navigateUpTo(this, new Intent(this, HistoryActivity.class));
                return true;
            }
            case R.id.details_menu_copy_code_row: {
                PsDetailFragment fragment = (PsDetailFragment) getFragmentManager().findFragmentById(
                        R.id.ps_detail_container);

                if (fragment != null) {
                    String completeCode = fragment.getHistoryItem().getResult().getCompleteCode();

                    addCodeRowToClipboard(completeCode);
                }
            }
            break;
            case R.id.details_menu_send_code_row: {
                PsDetailFragment fragment = (PsDetailFragment) getFragmentManager().findFragmentById(
                        R.id.ps_detail_container);

                if (fragment != null) {

                    IEsrSender sender = getEsrSender();

                    if (sender != null) {
                        mSendingProgressDialog.show();

                        fragment.send(PsDetailFragment.SEND_COMPONENT_CODE_ROW, sender);
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

    private void setCancelOkAlertDialog(int msgId) {
        new CancelOkDialog(msgId).setOkClickListener(new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        }).show(getFragmentManager(), "PsDetailActivity.setCancelOkAlertDialog");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            int error = savePaymentSlip(this);

            if (error > 0) {
                setCancelOkAlertDialog(error);

                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    public static int savePaymentSlip(Activity activity) {
        PsDetailFragment oldFragment = (PsDetailFragment) activity.getFragmentManager()
                .findFragmentById(R.id.ps_detail_container);

        if (oldFragment != null) {
            return oldFragment.save();
        }

        return 0;
    }

    @Override
    public boolean handleMessage(Message message) {
        int msgId = 0;

        mSendingProgressDialog.dismiss();

        if (message.what == R.id.es_send_succeeded) {
            historyManager.updateHistoryItemFileName((String) message.obj,
                    getResources().getString(R.string.history_item_sent));

            msgId = R.string.msg_coderow_sent;
        } else {
            msgId = R.string.msg_coderow_not_sent;
        }

        if (msgId != 0) {
            Toast toast = Toast.makeText(getApplicationContext(), msgId, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.BOTTOM, 0, 0);
            toast.show();
            return true;
        }

        return false;
    }
}
