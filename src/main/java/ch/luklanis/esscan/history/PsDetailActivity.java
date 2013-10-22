package ch.luklanis.esscan.history;

import ch.luklanis.esscan.Intents;
import ch.luklanis.esscan.PreferencesActivity;
import ch.luklanis.esscan.R;
import ch.luklanis.esscan.codesend.ESRSender;
import ch.luklanis.esscan.codesend.ESRSenderHttp;
import ch.luklanis.esscan.codesend.GetSendServiceCallback;
import ch.luklanis.esscan.codesend.IEsrSender;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.text.ClipboardManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.ListView;
import android.widget.Toast;

public class PsDetailActivity extends SherlockFragmentActivity implements
		Handler.Callback, GetSendServiceCallback {

	private SherlockFragmentActivity callerActivity;

	private HistoryManager historyManager;
	private Intent serviceIntent;
	private boolean serviceIsBound;

	private ESRSender boundService = null;
    private ESRSenderHttp mEsrSenderHttp;

	private final Handler mDataSentHandler = new Handler(this);

	private ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			boundService = ((ESRSender.LocalBinder) service).getService();
			boundService.registerDataSentHandler(mDataSentHandler);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			// Because it is running in our same process, we should never
			// see this happen.
		}
	};

    @Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.activity_ps_detail);

		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		if (icicle == null) {
			Bundle arguments = new Bundle();
			arguments.putInt(PsDetailFragment.ARG_POSITION, getIntent()
					.getIntExtra(PsDetailFragment.ARG_POSITION, 0));
			PsDetailFragment fragment = new PsDetailFragment();
			fragment.setArguments(arguments);
			getSupportFragmentManager().beginTransaction()
					.add(R.id.ps_detail_container, fragment).commit();
		}

		historyManager = new HistoryManager(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		Intent intent = new Intent(this, HistoryActivity.class);
		intent.putExtra(
				Intents.History.ITEM_NUMBER,
				getIntent().getIntExtra(PsDetailFragment.ARG_POSITION,
						ListView.INVALID_POSITION));
		setResult(Activity.RESULT_OK, intent);

		serviceIntent = new Intent(this, ESRSender.class);
		doBindService();

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);

        String username = prefs.getString(PreferencesActivity.KEY_USERNAME, "");
        String password = prefs.getString(PreferencesActivity.KEY_PASSWORD, "");
        try {
            if (!username.isEmpty() && !password.isEmpty()) {
                mEsrSenderHttp = new ESRSenderHttp(getApplicationContext(), username, password);
            }
        } catch (Exception e) {
            setOkAlert(R.string.msg_send_over_http_not_possible);
            e.printStackTrace();
        }
	}

	@Override
	protected void onPause() {
		
		doUnbindService();

		super.onPause();
	}

	private void doBindService() {
		if (!serviceIsBound) {
			bindService(serviceIntent, serviceConnection, 0);
			serviceIsBound = true;
		}
	}

	private void doUnbindService() {
		if (serviceIsBound) {
			unbindService(serviceConnection);
			serviceIsBound = false;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.details_menu, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home: {

            int error = savePaymentSlip(this);

            if (error > 0) {
                setCancelOkAlert(this, error);
                return true;
            }

			NavUtils.navigateUpTo(this, new Intent(this, HistoryActivity.class));
			return true;
		}
		case R.id.details_menu_copy_code_row: {
			PsDetailFragment fragment = (PsDetailFragment) getSupportFragmentManager()
					.findFragmentById(R.id.ps_detail_container);

			if (fragment != null) {
				String completeCode = fragment.getHistoryItem().getResult()
						.getCompleteCode();

				ClipboardManager clipboardManager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				clipboardManager.setText(completeCode);

				// clipboardManager.setPrimaryClip(ClipData.newPlainText("ocrResult",
				// ocrResultView.getText()));
				// if (clipboardManager.hasPrimaryClip()) {
				if (clipboardManager.hasText()) {
					Toast toast = Toast.makeText(getApplicationContext(),
							R.string.msg_copied, Toast.LENGTH_SHORT);
					toast.setGravity(Gravity.BOTTOM, 0, 0);
					toast.show();
				}
			}
		}
			break;
		case R.id.details_menu_send_code_row: {
			PsDetailFragment fragment = (PsDetailFragment) getSupportFragmentManager()
					.findFragmentById(R.id.ps_detail_container);

			if (fragment != null) {
                fragment.send(PsDetailFragment.SEND_COMPONENT_CODE_ROW, boundService);
			}
		}
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
            int error = savePaymentSlip(this);

            if (error > 0) {
                setCancelOkAlert(this, error);
                return true;
            }
		}

		return super.onKeyDown(keyCode, event);
	}

	public static int savePaymentSlip(SherlockFragmentActivity activity) {
		PsDetailFragment oldFragment = (PsDetailFragment) activity
				.getSupportFragmentManager().findFragmentById(
						R.id.ps_detail_container);

		if (oldFragment != null) {
			return oldFragment.save();
		}

		return 0;
	}

    private void setOkAlert(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage(id)
                .setPositiveButton(R.string.button_ok, null);

        builder.show();
    }

	private void setCancelOkAlert(final SherlockFragmentActivity activity,
			int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);

		builder.setMessage(id)
				.setNegativeButton(R.string.button_cancel, null)
				.setPositiveButton(R.string.button_ok,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
                                activity.finish();
							}
						});

		builder.show();
	}

	@Override
	public boolean handleMessage(Message message) {
		int msgId = 0;

		if (message.what == R.id.es_send_succeeded) {
			historyManager.updateHistoryItemFileName((String) message.obj,
					getResources().getString(R.string.history_item_sent));

			msgId = R.string.msg_coderow_sent;
		} else {
			msgId = R.string.msg_coderow_not_sent;
		}

		if (msgId != 0) {
			Toast toast = Toast.makeText(getApplicationContext(), msgId,
					Toast.LENGTH_SHORT);
			toast.setGravity(Gravity.BOTTOM, 0, 0);
			toast.show();
			return true;
		}

		return false;
	}

    @Override
    public IEsrSender getEsrSender() {
        if (mEsrSenderHttp != null) {
            return mEsrSenderHttp;
        } else {
            return boundService;
        }
    }
}
