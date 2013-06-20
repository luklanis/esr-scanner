package ch.luklanis.esscan.history;

import ch.luklanis.esscan.Intents;
import ch.luklanis.esscan.R;
import ch.luklanis.esscan.codesend.ESRSender;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NavUtils;
import android.text.ClipboardManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.ListView;
import android.widget.Toast;

public class PsDetailActivity extends SherlockFragmentActivity implements
		Handler.Callback {

	private static SherlockFragmentActivity callerActivity;

	private HistoryManager historyManager;
	private Intent serviceIntent;
	private boolean serviceIsBound;

	private ESRSender boundService = null;

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
			if (!savePaymentSlip(this)) {
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
				String completeCode = fragment.getHistoryItem().getResult()
						.getCompleteCode();
				
				int indexOfNewline = completeCode.indexOf('\n');
				if (indexOfNewline > -1) {
					completeCode = completeCode.substring(0,
							indexOfNewline);
				}

				if (boundService != null && ESRSender.isConnectedLocal()) {
					this.boundService.sendToListener(completeCode);
				} else if (boundService != null) {
					Toast toast = Toast.makeText(getApplicationContext(),
							R.string.msg_stream_mode_not_available, Toast.LENGTH_SHORT);
					toast.setGravity(Gravity.BOTTOM, 0, 0);
					toast.show();
				}
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
			if (!savePaymentSlip(this)) {
				return true;
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	public static boolean savePaymentSlip(SherlockFragmentActivity activity) {
		PsDetailFragment oldFragment = (PsDetailFragment) activity
				.getSupportFragmentManager().findFragmentById(
						R.id.ps_detail_container);

		if (oldFragment != null) {
			int error = oldFragment.save();

			if (error > 0) {
				setCancelOkAlert(activity, error);
				return false;
			}
		}

		return true;
	}

	private static void setCancelOkAlert(SherlockFragmentActivity activity,
			int id) {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		callerActivity = activity;

		builder.setMessage(id)
				.setNegativeButton(R.string.button_cancel, null)
				.setPositiveButton(R.string.button_ok,
						new DialogInterface.OnClickListener() {

							@Override
							public void onClick(DialogInterface dialog,
									int which) {
								callerActivity.finish();
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
}
