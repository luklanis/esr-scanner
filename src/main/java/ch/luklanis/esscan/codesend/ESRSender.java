package ch.luklanis.esscan.codesend;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicStampedReference;

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

public class ESRSender extends Service {

	public class LocalBinder extends Binder {
		public ESRSender getService() {
			return ESRSender.this;
		}
	}

	private static final String TAG = ESRSender.class.getName();

	private final AtomicInteger mServerPort = new AtomicInteger(0);

	private static InetAddress hostInterface = null;

	private final IBinder mBinder = new LocalBinder();

    private ESSendServer mSendServer;

    SharedPreferences prefs = PreferenceManager
            .getDefaultSharedPreferences(ESRSender.this);

	private Handler mDataSentHandler;
	private String mServerAddress;

	// From
	// http://developer.android.com/training/basics/network-ops/managing.html#detect-changes
	private final BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (!ESRSender.isConnectedLocal(true)) {
				stopServer();
			} else {
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

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(ESRSender.this);

		mServerPort.compareAndSet(0,
				prefs.getInt(PreferencesActivity.KEY_SERVER_PORT, 0));

        try {
            mSendServer = new ESSendServer(mServerPort.get());
        } catch (UnknownHostException e) {
            e.printStackTrace();
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

	public static String getLocalIpAddress() {

		InetAddress inetAddress = getLocalInterface();

		if (inetAddress != null) {
			return inetAddress.getHostAddress();
		}

		return "";
	}
	
	public String getCurrentServerAddress() {		
		return mServerAddress;
	}

	public void registerDataSentHandler(Handler dataSentCallback) {
		mDataSentHandler = dataSentCallback;
	}

	public void sendToListener(String message) {
		sendToListener(message, -1);
	}

	public void sendToListener(final String dataToSend, final int position) {
        boolean sent = mSendServer.send(dataToSend);

        if (mDataSentHandler != null) {
            Message message = Message.obtain(mDataSentHandler,
                    (sent ? R.id.es_send_succeeded
                            : R.id.es_send_failed));
            message.arg1 = position;
            message.obj = dataToSend;
            message.sendToTarget();
        }
	}

	public void stopServer() {
        try {
            mSendServer.stop();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

	public void startServer() {

		if (isConnectedLocal(true)) {
			
			mServerAddress = getLocalIpAddress();

            mSendServer.start();

		if (mServerPort.get() == 0) {
					mServerPort.set(mSendServer.getPort());
					prefs.edit()
							.putInt(PreferencesActivity.KEY_SERVER_PORT,
									mServerPort.get()).apply();
		}
		}
	}

	public int getServerPort() {
		return mSendServer.getPort();
	}
}
