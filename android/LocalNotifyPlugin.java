package com.tealeaf.plugin.plugins;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import com.tealeaf.EventQueue;
import com.tealeaf.TeaLeaf;
import com.tealeaf.logger;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.os.Bundle;

import com.tealeaf.plugin.IPlugin;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Intent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.util.Log;

import android.content.ComponentName;
import android.os.IBinder;
import android.app.PendingIntent;
import android.app.NotificationManager;
import android.app.AlarmManager;
import android.support.v4.app.NotificationCompat;
import android.app.Notification;
import android.net.Uri;

import com.tealeaf.EventQueue;
import com.tealeaf.event.*;

import java.util.*;
import java.io.*;
import android.util.Base64;

import com.google.gson.Gson;

public class LocalNotifyPlugin extends BroadcastReceiver implements IPlugin {
	Context _context;
	Activity _activity;
	AlarmManager _alarmManager;
	SharedPreferences _settings;
	static LocalNotifyPlugin _plugin;
	protected static Gson gson = new Gson();

	boolean _active; // Activity is in foreground
	boolean _ready; // JS told us it is ready for notifications

	final static String PREFS_NAME = "com.tealeaf.plugin.plugins.LocalNotifyPlugin.PREFERENCES";
	final static String ACTION_NOTIFY = "com.tealeaf.plugin.plugins.LocalNotifyPlugin.CUSTOM_ACTION_NOTIFY";
	final static int STATUS_CODE = 0;
	final static int ALARM_CODE = 0;

	public class NotificationData {
		public String name, text, sound, title, icon, userDefined;
		public int number;
		public long utc; // seconds
	}

	public class ScheduledData {
		ArrayList<NotificationData> list;
	}

	// Alarms scheduled for future delivery
	ArrayList<NotificationData> _scheduled = new ArrayList<NotificationData>();

	// Alarms waiting for JS to be ready
	ArrayList<NotificationData> _pending = new ArrayList<NotificationData>();

	public class ListEvent extends com.tealeaf.event.Event {
		ArrayList<NotificationData> list;
		String error;

		public ListEvent(ArrayList<NotificationData> list) {
			super("LocalNotifyList");
			this.list = list;
			this.error = null;
		}

		public ListEvent(String error) {
			super("LocalNotifyList");
			this.list = null;
			this.error = error;
		}
	}

	public class GetEvent extends com.tealeaf.event.Event {
		NotificationData info;
		String error;

		public GetEvent(NotificationData info) {
			super("LocalNotifyGet");
			this.info = info;
			this.error = null;
		}

		public GetEvent(String name, String error) {
			super("LocalNotifyGet");
			this.info = new NotificationData();
			this.info.name = name;
			this.error = error;
		}
	}

	public class NotifyEvent extends com.tealeaf.event.Event {
		NotificationData info;
		String error;

		public NotifyEvent(NotificationData info) {
			super("LocalNotify");
			this.info = info;
			this.error = null;
		}

		public NotifyEvent(String name, String error) {
			super("LocalNotify");
			this.info = new NotificationData();
			this.info.name = name;
			this.error = error;
		}
	}

	public static void showNotification(Context context, NotificationData info) {
		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
			.setAutoCancel(true)
			.setSmallIcon(context.getResources().getIdentifier("icon", "drawable", context.getPackageName()))
			.setContentTitle(info.title)
			.setContentText(info.text)
			.setTicker(info.title)
			.setOnlyAlertOnce(false)
			.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND);

		// TODO: Icon and sound

		Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("name", info.name);

		PendingIntent pending = PendingIntent.getActivity(context, STATUS_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(pending);

		Notification notification = builder.build();

		if (info.number > 1) {
			notification.number = info.number;
		}

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(STATUS_CODE, notification);

		// TODO: Clear notifications in status bar
		// TODO: Deliver background shown notifications to JS when brought to foreground
		// TODO: Detect if an alarm launched the app
	}

	public void addAlarm(NotificationData n) {
		final long CURRENT_UTC = System.currentTimeMillis() / 1000; // seconds

		// Cancel any existing alarm with the same name
		cancelAlarm(n.name);

		// If should be delivered right now,
		if (n.utc <= CURRENT_UTC) {
			logger.log("{localNotify} Add requested for", n.name, "in the past so delivering now");

			deliverAlarm(n);
		} else {
			logger.log("{localNotify} Add requested for", n.name, "in the future so scheduling an alarm for", n.utc - CURRENT_UTC);

			_scheduled.add(n);
			writePreferences();

			Intent intent = new Intent(ACTION_NOTIFY, null, _context, LocalNotifyPlugin.class);
			intent.addCategory(n.name); // for cancel
			intent.putExtra("name", n.name); // for receiver
			intent.putExtra("text", n.text);
			intent.putExtra("number", n.number);
			intent.putExtra("sound", n.sound);
			intent.putExtra("title", n.title);
			intent.putExtra("icon", n.icon);
			intent.putExtra("userDefined", n.userDefined);
			intent.putExtra("utc", n.utc);

			_alarmManager.set(AlarmManager.RTC_WAKEUP, n.utc * 1000, PendingIntent.getBroadcast(_context, ALARM_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT));
		}
	}

	public void removeAlarm(String name) {
		// Remove from scheduled list
		NotificationData info = null;

		for (NotificationData n : _scheduled) {
			if (n.name.equals(name)) {
				info = n;
				break;
			}
		}

		if (info != null) {
			_scheduled.remove(info);
			writePreferences();
		}
	}

	public void cancelAlarm(String name) {
		logger.log("{localNotify} Canceling alarm:", name);

		// Cancel alarm
		Intent intent = new Intent(ACTION_NOTIFY, null, _context, LocalNotifyPlugin.class);
		intent.addCategory(name); // for cancel

		_alarmManager.cancel(PendingIntent.getBroadcast(_context, ALARM_CODE, intent, PendingIntent.FLAG_CANCEL_CURRENT));

		removeAlarm(name);
	}

	public void deliverAlarm(NotificationData n) {
		removeAlarm(n.name);

		// Deliver to JS without puting it in the status bar
		if (!_ready) {
			logger.log("{localNotify} JS not ready so pending alarm", n.name);
			_pending.add(n);
		} else {
			logger.log("{localNotify} Delivering alarm to JS:", n.name);
			EventQueue.pushEvent(new NotifyEvent(n));
		}

		if (!_active) {
			// Place in status bar from background
			logger.log("{localNotify} Displaying alarm in status bar:", n.name);

			showNotification(_context, n);
		}
	}

    public void broadcastReceived(final Context context, Intent intent) {
		final String NAME = intent.getStringExtra("name");

		NotificationData info = null;

		for (NotificationData n : _scheduled) {
			if (n.name.equals(NAME)) {
				info = n;
				break;
			}
		}

		if (info == null) {
			logger.log("{localNotify} Received unscheduled alarm for", NAME);
		} else {
			logger.log("{localNotify} Alarm triggered:", NAME);
			deliverAlarm(info);
		}
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
		String action = intent.getAction();

		// NOTE: This is called on a new empty instance of the class
		if (action.equals("android.intent.action.BOOT_COMPLETED")) {
			// TODO: Handle this
		} if (action.equals(ACTION_NOTIFY)) {
			if (_plugin != null) {
				_plugin.broadcastReceived(context, intent);
			} else {
				try {
					// Build notification object
					NotificationData n = new NotificationData();
					n.name = intent.getStringExtra("name");
					n.text = intent.getStringExtra("text");
					n.number = intent.getIntExtra("number", 0);
					n.sound = intent.getStringExtra("sound");
					n.title = intent.getStringExtra("title");
					n.icon = intent.getStringExtra("icon");
					n.userDefined = intent.getStringExtra("userDefined");
					n.utc = intent.getLongExtra("utc", 0);

					logger.log("{localNotify} Showing notification while inactive:", n.name);

					showNotification(context, n);
				} catch (Exception e) {
					logger.log("{localNotify} Failure parsing intent:", e);
				}
			}
		}
    }

	public LocalNotifyPlugin() {
	}

	public void readPreferences() {
		try {
			String scheduledAlarms = _settings.getString("ScheduledAlarms", "");

			if (!scheduledAlarms.equals("")) {
				ScheduledData old = gson.fromJson(scheduledAlarms, ScheduledData.class);

				logger.log("{localNotify} Recovering", old.list.size(), "alarms");

				final int CURRENT_UTC = (int)( System.currentTimeMillis() / 1000 ); // seconds

				for (NotificationData n : old.list) {
					if (n.utc >= CURRENT_UTC) {
						addAlarm(n);
					} else {
						logger.log("{localNotify} Discarding old expired alarm:", n.name);
					}
				}
			} else {
				logger.log("{localNotify} No alarms to recover");
			}
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception while deserializing scheduled alarms:", e);
		}
	}

	public void writePreferences() {
		try {
			ScheduledData box = new ScheduledData();
			box.list = _scheduled;

			String alarms = gson.toJson(box);

			SharedPreferences.Editor editor = _settings.edit();
			editor.putString("ScheduledAlarms", alarms);
			editor.apply();
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception while serializing scheduled alarms:", e);
		}
	}

	public void onCreateApplication(Context applicationContext) {
		_context = applicationContext;
		_alarmManager = (AlarmManager) _context.getSystemService(Context.ALARM_SERVICE);
		_settings = _context.getSharedPreferences(PREFS_NAME, 0);
		_plugin = this;
	}

	public void onCreate(Activity activity, Bundle savedInstanceState) {
		logger.log("{localNotify} Initializing");

		_activity = activity;
	}

	public void onStart() {
		_active = true;

		readPreferences();
	}

	public void onResume() {
		_active = true;
	}

	public void onPause() {
		_active = false;
	}

	public void onStop() {
		_active = false;
	}

	public void onDestroy() {
		_active = false;

		_plugin = null;
	}

	public void Ready(String jsonData) {
		try {
			logger.log("{localNotify} Ready");

			_ready = true;

			for (NotificationData n : _pending) {
				deliverAlarm(n);
			}

			_pending.clear();
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in ready:", e);
			e.printStackTrace();
		}
	}

	public void List(String jsonData) {
		try {
			logger.log("{localNotify} Listing scheduled alarms");

			EventQueue.pushEvent(new ListEvent(_scheduled));
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in list:", e);
			e.printStackTrace();
		}
	}

	public void Get(String jsonData) {
		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			final String NAME = jsonObject.getString("name");

			logger.log("{localNotify} Get requested for", NAME);

			NotificationData info = null;

			for (NotificationData n : _scheduled) {
				if (n.name.equals(NAME)) {
					info = n;
					break;
				}
			}

			if (info != null) {
				EventQueue.pushEvent(new GetEvent(info));
			} else {
				EventQueue.pushEvent(new GetEvent(NAME, "not found"));
			}
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in get:", e);
			e.printStackTrace();
		}
	}

	public void Clear(String jsonData) {
		try {
			logger.log("{localNotify} Clearing scheduled alarms");

			for (NotificationData n : _scheduled) {
				cancelAlarm(n.name);
			}

			_scheduled.clear();
			writePreferences();
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in clear:", e);
			e.printStackTrace();
		}
	}

	public void Remove(String jsonData) {
		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			final String NAME = jsonObject.getString("name");

			logger.log("{localNotify} Remove requested for", NAME);

			cancelAlarm(NAME);
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in remove:", e);
			e.printStackTrace();
		}
	}

	public void Add(String jsonData) {
		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			final String NAME = jsonObject.getString("name");
			final String TEXT = jsonObject.getString("text");
			final int NUMBER = jsonObject.getInt("number");
			final String SOUND = jsonObject.getString("sound");
			final String TITLE = jsonObject.getString("title");
			final String ICON = jsonObject.getString("icon");
			final int UTC = jsonObject.getInt("utc"); // seconds
			final String USER_DEFINED = jsonObject.getString("userDefined");

			// Build notification object
			NotificationData n = new NotificationData();
			n.name = new String(NAME);
			n.text = new String(TEXT);
			n.number = NUMBER;
			n.sound = new String(SOUND);
			n.title = new String(TITLE);
			n.icon = new String(ICON);
			n.userDefined = new String(USER_DEFINED);
			n.utc = UTC;

			addAlarm(n);
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in add:", e);
			e.printStackTrace();
		}
	}

	public void onActivityResult(Integer request, Integer resultCode, Intent data) {
	}

	public void onNewIntent(Intent intent) {
	}

	public void setInstallReferrer(String referrer) {
	}

	public void logError(String error) {
	}

	public boolean consumeOnBackPressed() {
		return true;
	}

	public void onBackPressed() {
	}
}

