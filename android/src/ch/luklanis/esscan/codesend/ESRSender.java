package ch.luklanis.esscan.codesend;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ch.luklanis.esscan.PreferencesActivity;
import ch.luklanis.esscan.R;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;

public class ESRSender extends Service implements Runnable {

	public class LocalBinder extends Binder {
		public ESRSender getService() {
			return ESRSender.this;
		}
	}

	private static final String TAG = ESRSender.class.getName();

	private static final String STOP_CONNECTION = "STOP";

	private static final String START_SERVER = "START";

	private static final String ACK = "ACK";

	private final AtomicBoolean mStopServer = new AtomicBoolean(false);
	private final AtomicBoolean mServerStopped = new AtomicBoolean(true);
	private final AtomicInteger mServerPort = new AtomicInteger(0);

	private final ArrayBlockingQueue<String> mDataQueue = new ArrayBlockingQueue<String>(
			200);

	private final ArrayBlockingQueue<Boolean> mDataSent = new ArrayBlockingQueue<Boolean>(
			10);

	private static InetAddress hostInterface = null;

	private final Thread mSendDataThread = new Thread(this);

	private final IBinder mBinder = new LocalBinder();

	private Handler mDataSentHandler;

	// From
	// http://developer.android.com/training/basics/network-ops/managing.html#detect-changes
	private final BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (!ESRSender.isConnectedLocal(true) && isStopped()) {
				stopServer();
			} else if (isStopped()) {
				startServer();
			}
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		return this.mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		super.onStartCommand(intent, flags, startId);

		mSendDataThread.setName("sendDataThread");

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(ESRSender.this);

		mServerPort.compareAndSet(0,
				prefs.getInt(PreferencesActivity.KEY_SERVER_PORT, 0));

		if (mServerPort.get() == 0) {
			ServerSocket server = null;
			try {
				server = new ServerSocket(mServerPort.get());

				if (mServerPort.get() == 0) {
					mServerPort.set(server.getLocalPort());
					prefs.edit()
							.putInt(PreferencesActivity.KEY_SERVER_PORT,
									mServerPort.get()).apply();
				}
			} catch (IOException e) {
				// do nothing
			} finally {
				if (server != null) {
					try {
						server.close();
					} catch (IOException e) {
						// do nothing
					}
				}
			}
		}

		// Registers BroadcastReceiver to track network connection changes.
		IntentFilter filter = new IntentFilter(
				ConnectivityManager.CONNECTIVITY_ACTION);
		this.registerReceiver(mNetworkReceiver, filter);

		this.startServer();

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {

		try {
			unregisterReceiver(mNetworkReceiver);
		} catch (IllegalArgumentException e) {
		}

		stopServer();

		super.onDestroy();
	}

	public static boolean isConnectedLocal() {
		return isConnectedLocal(false);
	}

	public static boolean isConnectedLocal(boolean refreshInterface) {
		return getLocalInterface(refreshInterface) != null;
	}

	public static InetAddress getLocalInterface() {
		return getLocalInterface(false);
	}

	public static InetAddress getLocalInterface(boolean refreshInterface) {

		if (refreshInterface) {
			hostInterface = null;
		}

		if (hostInterface == null) {
			try {
				for (Enumeration<NetworkInterface> en = NetworkInterface
						.getNetworkInterfaces(); en.hasMoreElements();) {

					NetworkInterface intf = en.nextElement();
					for (Enumeration<InetAddress> enumIpAddr = intf
							.getInetAddresses(); enumIpAddr.hasMoreElements();) {

						InetAddress inetAddress = enumIpAddr.nextElement();
						if (!inetAddress.isLoopbackAddress()
								&& inetAddress.getAddress().length == 4
								&& inetAddress.isSiteLocalAddress()
								&& (inetAddress.getAddress()[0] != 10 || intf
										.getName().contains("wlan"))) {

							hostInterface = inetAddress;
							return hostInterface;
						}
					}
				}
			} catch (SocketException ex) {
				Log.e(TAG, ex.toString());
			}
		}

		return hostInterface;
	}

	public String getLocalIpAddress() {

		InetAddress inetAddress = getLocalInterface();

		if (inetAddress != null) {
			return inetAddress.getHostAddress();
		}

		return "";
	}

	public void registerDataSentHandler(Handler dataSentCallback) {
		mDataSentHandler = dataSentCallback;
	}

	public void sendToListener(String message) {
		sendToListener(message, -1);
	}

	public void sendToListener(final String dataToSend, final int position) {
		mDataSent.clear();

		mDataQueue.offer(dataToSend);
		Thread t = new Thread() {
			public void run() {
				Boolean sent = false;

				try {
					sent = mDataSent.poll(3, TimeUnit.SECONDS);

					if (sent == null) {
						sent = false;
					}
				} catch (InterruptedException e) {
				}

				if (mDataSentHandler != null) {
					Message message = Message.obtain(mDataSentHandler,
							(sent ? R.id.es_send_succeeded
									: R.id.es_send_failed));
					message.arg1 = position;
					message.obj = dataToSend;
					message.sendToTarget();
				}
			}
		};
		t.start();
	}

	public void stopServer() {
		mStopServer.set(true);
		mDataQueue.offer(STOP_CONNECTION);
	}

	public boolean isStopped() {
		return mStopServer.get();
	}

	public void startServer() {

		if (isConnectedLocal(true) && mServerStopped.get()) {
			mStopServer.set(false);

//			if (mSendDataThread.getState() == Thread.State.TERMINATED) {
//				mSendDataThread = new Thread(this);
//				mSendDataThread.setName("sendDataThread");
//			}
			if (!mSendDataThread.isAlive()) {
				mSendDataThread.start();
			} else {
				mDataQueue.offer(START_SERVER);
			}
		}
	}

	public int getServerPort() {
		return mServerPort.get();
	}

	@Override
	public void run() {

		while (true) {
			while (!mStopServer.get()) {
				ServerSocket server = null;

				try {
					mServerStopped.set(false);

					SharedPreferences prefs = PreferenceManager
							.getDefaultSharedPreferences(ESRSender.this);

					mServerPort.compareAndSet(0, prefs.getInt(
							PreferencesActivity.KEY_SERVER_PORT, 0));

					server = new ServerSocket(mServerPort.get());
					server.setSoTimeout(500);

					if (mServerPort.get() == 0) {
						mServerPort.set(server.getLocalPort());
						prefs.edit()
								.putInt(PreferencesActivity.KEY_SERVER_PORT,
										mServerPort.get()).apply();
						// TODO show message to user somehow with the new
						// port
					}

					Socket client = null;
					DataOutputStream os = null;
					DataInputStream is = null;

					while (!mStopServer.get()) {

						String data;

						try {
							data = mDataQueue.poll(30, TimeUnit.SECONDS);

							if (data == null) {
								continue;
							}

							try {
								client = server.accept();
								client.setSoTimeout(2000);
							} catch (Exception e) {
								if (mStopServer.get()) {
									continue;
								} else {
									throw e;
								}
							}

							os = new DataOutputStream(client.getOutputStream());
							os.writeUTF(data);
							os.flush();

							is = new DataInputStream(client.getInputStream());
							String responseLine = is.readUTF();
							if (responseLine != null
									&& responseLine.equals(ACK)) {
								mDataSent.offer(true);
							} else {
								mDataSent.offer(false);
							}

						} catch (Exception e) {
							e.printStackTrace();
							mDataSent.offer(false);
						}

						if (os != null) {
							os.close();
							os = null;
						}

						if (is != null) {
							is.close();
							is = null;
						}

						if (client != null) {
							client.close();
							client = null;
						}
					}
				} catch (IOException e) {
					mServerPort.set(0);
					PreferenceManager
							.getDefaultSharedPreferences(ESRSender.this)
							.edit()
							.putInt(PreferencesActivity.KEY_SERVER_PORT,
									mServerPort.get()).apply();

					e.printStackTrace();
				} finally {
					if (server != null) {
						try {
							server.close();
						} catch (IOException e) {
						}
					}

					if (mStopServer.get()) {
						mServerStopped.set(true);
					}
				}
			}

			try {
				String start = mDataQueue.poll(30, TimeUnit.SECONDS);
				while (start == null || !start.equals(START_SERVER)) {
					start = mDataQueue.poll(30, TimeUnit.SECONDS);
				}
			} catch (InterruptedException e) {
			}
		}
	}
}
