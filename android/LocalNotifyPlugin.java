package com.tealeaf.plugin.plugins;

import java.util.Map;
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
import android.content.BroadcastReceiver;
import android.os.Bundle;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.Iterator;

import com.tealeaf.plugin.IPlugin;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.Intent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
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

public class LocalNotifyPlugin extends BroadcastReceiver implements IPlugin {
	Context _context = null;
	Activity _activity = null;
	AlarmManager _alarmManager = null;
	NotificationManager _notificationManager = null;
	final String ACTION_MY_NOTIFY = "com.tealeaf.plugin.plugins.LocalNotifyPlugin.NOTIFICATION_MESSAGE";
	boolean _active = false; // Activity is in foreground
	boolean _ready = false; // JS told us it is ready for notifications

	public class NotificationData {
		String name, text, sound, title, icon, userDefined;
		int number;
		int utc; // seconds
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

	public void showNotificationInStatusBar(NotificationData info) {
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(_context)
			.setAutoCancel(true)
			.setSmallIcon(_context.getResources().getIdentifier("icon", "drawable", _context.getPackageName()))
			.setContentTitle(info.title)
			.setContentText(info.text)
			.setTicker(info.title)
			.setOnlyAlertOnce(false)
			.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND);

		// TODO: Icon and sound

		Intent intent = _context.getPackageManager().getLaunchIntentForPackage(_context.getPackageName());
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("name", info.name);

		PendingIntent pending = PendingIntent.getActivity(_context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(pending);

		Notification notification = mBuilder.build();

		if (info.number > 1) {
			notification.number = info.number;
		}

		_notificationManager.notify(0, notification);
	}

	public void cancelAlarm(String name) {
		Intent intent = new Intent(ACTION_MY_NOTIFY, Uri.parse("custom://" + name), _context, LocalNotifyPlugin.class);

		_alarmManager.cancel(PendingIntent.getBroadcast(_context, 0, intent, 0));
	}

	public void deliverAlarm(NotificationData n) {
		if (_active) {
			// Deliver to JS without puting it in the status bar
			if (!_ready) {
				logger.log("{localNotify} JS not ready so pending alarm", n.name);
				_pending.add(n);
			} else {
				logger.log("{localNotify} Delivering alarm to JS:", n.name);
				EventQueue.pushEvent(new NotifyEvent(n));
			}
		} else {
			// Place in status bar from background
			logger.log("{localNotify} Displaying alarm from background:", n.name);
			showNotificationInStatusBar(n);
		}
	}

    @Override
    public void onReceive(final Context context, Intent intent) {
		String action = intent.getAction();

		if (action.equals("android.intent.action.BOOT_COMPLETED")) {
			// TODO: Handle this
		} else if (action.equals(ACTION_MY_NOTIFY)) {
			final String NAME = intent.getStringExtra("name");

			NotificationData info = null;

			for (NotificationData n : _scheduled) {
				if (n.name.equals(NAME)) {
					info = n;
					break;
				}
			}

			if (info == null) {
				logger.log("{localNotify} Received alarm for", NAME);
			} else {
				logger.log("{localNotify} Alarm triggered:", NAME);
				deliverAlarm(info);
			}
		}
    }

	public LocalNotifyPlugin() {
	}

	public void onCreateApplication(Context applicationContext) {
		_context = applicationContext;
		_alarmManager = (AlarmManager) _context.getSystemService(Context.ALARM_SERVICE);
		_notificationManager = (NotificationManager) _context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	public void onCreate(Activity activity, Bundle savedInstanceState) {
		logger.log("{localNotify} Initializing");

		_activity = activity;
	}

	public void onStart() {
		_active = true;

		// TODO: Re-activate alarms from preferences
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

			// Cancel any existing alarm with the same name
			cancelAlarm(NAME);

			final int CURRENT_UTC = (int)( System.currentTimeMillis() / 1000 ); // seconds

			// Build notification object
			NotificationData n = new NotificationData();
			n.name = NAME;
			n.text = TEXT;
			n.number = NUMBER;
			n.sound = SOUND;
			n.title = TITLE;
			n.icon = ICON;
			n.userDefined = USER_DEFINED;
			n.utc = UTC;

			// If should be delivered right now,
			if (UTC <= CURRENT_UTC) {
				logger.log("{localNotify} Add requested for", NAME, "in the past so delivering now");

				deliverAlarm(n);
			} else {
				logger.log("{localNotify} Add requested for", NAME, "in the future so scheduling an alarm");

				_scheduled.add(n);
				Intent intent = new Intent(ACTION_MY_NOTIFY, Uri.parse("custom://" + NAME), _context, LocalNotifyPlugin.class);
				intent.putExtra("name", NAME);

				_alarmManager.set(AlarmManager.RTC_WAKEUP, UTC * 1000, PendingIntent.getBroadcast(_context, 0, intent, 0));
			}
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

