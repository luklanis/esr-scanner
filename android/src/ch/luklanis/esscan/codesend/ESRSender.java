package ch.luklanis.esscan.codesend;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class ESRSender extends Service {

	public class LocalBinder extends Binder {
		public ESRSender getService() {
			return ESRSender.this;
		}
	}

	private static final String TAG = ESRSender.class.getName();
	private static Thread sSendDataThread = null;

	private final AtomicBoolean mStopServer = new AtomicBoolean(false);
	private final AtomicBoolean mHasClient = new AtomicBoolean(false);
	private final AtomicInteger mServerPort = new AtomicInteger(0);

	private final ArrayBlockingQueue<String> mDataQueue = new ArrayBlockingQueue<String>(
			200);

	private final Runnable mSendDataRunnable = new Runnable() {
		@Override
		public void run() {
			try {
				ServerSocket server = new ServerSocket(mServerPort.get());
				server.setSoTimeout(300000);

				mServerPort.compareAndSet(0, server.getLocalPort());

				Socket client = null;
				DataOutputStream os;

				while (!mStopServer.get()) {
					try {
						client = server.accept();
					} catch (InterruptedIOException e) {
						if(mStopServer.get()) {
							return;
						}
					}

					mDataQueue.clear();
					
					os = new DataOutputStream(client.getOutputStream());
					String data;
					
					mHasClient.set(true);

					while (!mStopServer.get()) {
						try {
							while (client.isBound()) {
								data = mDataQueue.poll(1, TimeUnit.SECONDS);

								if (data != null) {
									os.writeUTF(data);
								}
							}

						} catch (Exception e) {
							e.printStackTrace();
							break;
						}
					}
					
					mHasClient.set(false);

					if (os != null) {
						os.close();
					}

					if (client != null) {
						client.close();
					}
				}

				if (server != null) {
					server.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	};

	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent) {
		return this.mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		super.onStartCommand(intent, flags, startId);

		if (sSendDataThread != null) {
			return START_NOT_STICKY;
		}

		this.startServer();

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		stopServer();
		
		super.onDestroy();
	}

	@Override
	public boolean onUnbind(Intent intent) {

//		Log.i(TAG, "Set up alarm manager");
//
//		Intent alarmIntent = new Intent(this, StopServiceReceiver.class);
//		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
//				alarmIntent, 0);
//
//		Calendar calendar = Calendar.getInstance();
//		calendar.add(Calendar.MINUTE, 5);
//
//		AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
//		am.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(),
//				pendingIntent);
		
		stopServer();

		return true;
	}

	@Override
	public void onRebind(Intent intent) {
		startServer();
//		Log.i(TAG, "Cancel alarm manager");
//
//		Intent alarmIntent = new Intent(this, StopServiceReceiver.class);
//		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
//				alarmIntent, 0);
//
//		AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
//		am.cancel(pendingIntent);
	}

	public boolean isConnectedLocal() {
		ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		// NetworkInfo info =
		// connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		NetworkInfo[] allNetworkInfo = connManager.getAllNetworkInfo();

		for (int i = 0; i < allNetworkInfo.length; i++) {
			NetworkInfo info = allNetworkInfo[i];
			int type = info.getType();

			if (info.isAvailable()
					&& info.isConnected()
					// && (type == ConnectivityManager.TYPE_BLUETOOTH
					// || type == ConnectivityManager.TYPE_DUMMY
					// || type == ConnectivityManager.TYPE_ETHERNET
					// || type == ConnectivityManager.TYPE_WIFI)) {
					&& type != ConnectivityManager.TYPE_MOBILE
					&& type != ConnectivityManager.TYPE_MOBILE_DUN
					&& type != ConnectivityManager.TYPE_MOBILE_HIPRI
					&& type != ConnectivityManager.TYPE_MOBILE_MMS
					&& type != ConnectivityManager.TYPE_MOBILE_SUPL
					&& type != ConnectivityManager.TYPE_WIMAX) {
				return true;
			}
		}

		return false;
	}

	public InetAddress getLocalInterface() {
		
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()
							&& inetAddress.getAddress().length == 4) {
						return inetAddress;
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}

		return null;
	}

	public String getLocalIpAddress() {

		String adresses = "";

		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()
							&& inetAddress.getAddress().length == 4) {
						adresses += String.format("\n%s: %s",
								intf.getDisplayName(),
								inetAddress.getHostAddress());
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}

		return adresses;
	}

	public boolean sendToListener(String message) {
		if (mHasClient.get()) {
			mDataQueue.offer(message);
		}

		return mHasClient.get();
	}

	public void stopServer() {
		mStopServer.set(true);
	}

	public void startServer() {

		if (isConnectedLocal()) {
			mStopServer.set(false);
			
			if (sSendDataThread != null) {
				return;
			}
			
			sSendDataThread = new Thread(mSendDataRunnable);
			sSendDataThread.setName("sSendDataThread");
			sSendDataThread.start();
		}
	}

	public int getServerPort() {
		return mServerPort.get();
	}
}
