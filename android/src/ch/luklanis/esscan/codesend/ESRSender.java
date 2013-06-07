package ch.luklanis.esscan.codesend;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceActivity;
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
	private static final String KEEP_ALIVE = "KA";

	private static final String START_SERVER = "START";

	private final AtomicBoolean mStopServer = new AtomicBoolean(false);
	private final AtomicBoolean mServerStopped = new AtomicBoolean(true);
	private final AtomicBoolean mHasClient = new AtomicBoolean(false);
	private final AtomicInteger mServerPort = new AtomicInteger(0);

	private final ArrayBlockingQueue<String> mDataQueue = new ArrayBlockingQueue<String>(
			200);

	private static InetAddress hostInterface = null;

	private final Thread mSendDataThread = new Thread(this);

	private final IBinder mBinder = new LocalBinder();

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

		this.startServer();

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
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
		if (hostInterface == null || refreshInterface) {
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

	public boolean sendToListener(String message) {
		if (mHasClient.get()) {
			mDataQueue.offer(message);
		}

		return mHasClient.get();
	}

	public void stopServer() {
		mStopServer.set(true);
		mDataQueue.offer(STOP_CONNECTION);
	}

	public void startServer() {

		if (isConnectedLocal(true)) {
			mStopServer.set(false);

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

					if (mServerPort.get() == 0) {
						mServerPort.set(server.getLocalPort());
						prefs.edit()
								.putInt(PreferencesActivity.KEY_SERVER_PORT,
										mServerPort.get()).apply();
						// TODO show message to user somehow with the new port
					}

					Socket client = null;
					DataOutputStream os = null;

					while (!mStopServer.get()) {

						String data;
						mHasClient.set(true);

						try {
							data = mDataQueue.poll(5, TimeUnit.SECONDS);

							try {
								client = server.accept();
							} catch (IOException e) {
								if (mStopServer.get()) {
									return;
								} else {
									throw e;
								}
							}

							os = new DataOutputStream(client.getOutputStream());

							if (data != null) {
								os.writeUTF(data);
							} else {
								os.writeUTF(KEEP_ALIVE);
							}

						} catch (Exception e) {
							e.printStackTrace();
						}

						if (os != null) {
							os.close();
							os = null;
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
							// TODO Auto-generated catch block
							e.printStackTrace();
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
