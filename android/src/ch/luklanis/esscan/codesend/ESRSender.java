package ch.luklanis.esscan.codesend;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Enumeration;

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
	
	private static int sServerPort = 0;

	private static ServerSocket sServerSocket = null;
	private static Socket sClientSocket = null;
	
	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent) {		
		return this.mBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		super.onStartCommand(intent, flags, startId);

		if (sClientSocket != null) {
			return START_NOT_STICKY;
		}

		this.startServer();

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stopServer();
	}

	@Override
	public boolean onUnbind(Intent intent) {

		Log.i(TAG, "Set up alarm manager");

		Intent alarmIntent = new Intent(this, StopServiceReceiver.class);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent, 0);

		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.MINUTE, 5);

		AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
		am.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);

		return true;
	}

	@Override
	public void onRebind(Intent intent) {
		Log.i(TAG, "Cancel alarm manager");

		Intent alarmIntent = new Intent(this, StopServiceReceiver.class);
		PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, alarmIntent, 0);

		AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
		am.cancel(pendingIntent);
	}

	public boolean isConnectedLocal() {
		ConnectivityManager connManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		//		NetworkInfo info = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		NetworkInfo[] allNetworkInfo = connManager.getAllNetworkInfo();

		for (int i = 0; i < allNetworkInfo.length; i++) {
			NetworkInfo info = allNetworkInfo[i];
			int type = info.getType();

			if (info.isAvailable() && info.isConnected() 
					//					&& (type == ConnectivityManager.TYPE_BLUETOOTH
					//					|| type == ConnectivityManager.TYPE_DUMMY
					//					|| type == ConnectivityManager.TYPE_ETHERNET
					//					|| type == ConnectivityManager.TYPE_WIFI)) {
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

	public String getLocalIpAddress() {

		String adresses = "";

		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress() && inetAddress.getAddress().length == 4) {
						adresses += String.format("\n%s: %s", intf.getDisplayName(), inetAddress.getHostAddress());
					}
				}
			}
		} catch (SocketException ex) {
			Log.e(TAG, ex.toString());
		}

		return adresses;
	}

	public boolean sendToListeners(String... messages) {
		ArrayList<DataOutputStream> dataOutputStreams = new ArrayList<DataOutputStream>();

		try {
			if (sClientSocket != null && !sClientSocket.isClosed()) {
				dataOutputStreams.add(new DataOutputStream(sClientSocket.getOutputStream()));
			} else {
				return false;
			}
		} catch (IOException e) {
			try {
				sClientSocket.close();
			} catch (IOException ex) {
			}

			return false;
		}

		SendMessageAsync sendMessageAsync = new SendMessageAsync(dataOutputStreams);
		sendMessageAsync.execute(messages);
		return true;
	}

	public void stopServer() {
		try {
			if (sServerSocket != null && !sServerSocket.isClosed()) {
				sServerSocket.close();
				sServerSocket = null;
			}

			if (sClientSocket != null) {
				sClientSocket.close();
				sClientSocket = null;
			}
		} catch (IOException e) {
		}
	}

	public void startServer() {

		if(isConnectedLocal()) {
			if (sServerSocket != null) {
				return;
			}

			try {
				sServerSocket = new ServerSocket(sServerPort);
				
				if (sServerPort == 0) {
					sServerPort = sServerSocket.getLocalPort();
				}
			} catch (IOException e) {
				Log.e(TAG, "Open a server socket failed!", e);
			}

			Runnable runnable = new Runnable() {
				@Override
				public void run() {

					while(sServerSocket != null && !sServerSocket.isClosed()) {
						try {
							sClientSocket = sServerSocket.accept();
						} catch (IOException e) {
						}
					} 
				}
			};

			new Thread(runnable).start();
		}
	}
	
	public int getServerPort() {
		return sServerPort;
	}
}
