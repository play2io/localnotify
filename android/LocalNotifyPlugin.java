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
	protected Context _context;
	protected Activity _activity;
	protected AlarmManager _alarmManager;
	protected SharedPreferences _settings;
	protected static LocalNotifyPlugin _plugin;
	protected static Gson _gson = new Gson();
	protected boolean _active;		// Activity is in foreground
	protected boolean _ready;		// JS told us it is ready for notifications
	protected String _launchName;	// Notification name used to launch app

	final static String PREFS_NAME = "com.tealeaf.plugin.plugins.LocalNotifyPlugin.PREFERENCES";
	final static String ACTION_NOTIFY = "com.tealeaf.plugin.plugins.LocalNotifyPlugin.CUSTOM_ACTION_NOTIFY";
	final static int STATUS_CODE = 0;
	final static int ALARM_CODE = 0;

	public class NotificationData {
		String name, text, sound, title, icon, userDefined,tag;
		boolean vibrate;
		boolean launched;
		boolean shown;
		int number;
		long utc; // seconds
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

	public class NotificationClickEvent extends com.tealeaf.event.Event {
    		NotificationData info;
    		String error;

    		public NotificationClickEvent(NotificationData info) {
    			super("NotifyClick");
    			this.info = info;
    			this.error = null;
    		}

    }

	public static void showNotification(Context context, NotificationData info) {
		int defaults = Notification.DEFAULT_LIGHTS;

		info.shown = true;

		// If vibration is specified,
		if (info.vibrate) {
			defaults |= Notification.DEFAULT_VIBRATE;
		}

		// If sound is specified,
		if (info.sound != null && !"".equals(info.sound.trim())) { // NOTE: API level 8 does not support isEmpty
			defaults |= Notification.DEFAULT_SOUND;
		}

		NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
			.setAutoCancel(true)
			.setSmallIcon(context.getResources().getIdentifier("icon", "drawable", context.getPackageName()))
			.setContentTitle(info.title)
			.setContentText(info.text)
			.setTicker(info.title)
			.setOnlyAlertOnce(false)
			.setDefaults(defaults);

		// TODO: Icon and sound

		Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra("name", info.name);
		intent.putExtra("fromLocalNotify", true);
		intent.putExtra("userDefined", info.userDefined);

		PendingIntent pending = PendingIntent.getActivity(context, info.number, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(pending);

		Notification notification = builder.build();

		// If number should be set,
		if (info.number > 1) {
			notification.number = info.number;
		}

		NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(info.tag ,0, notification); //@info set tag so we can clear notification by NotificationManager.cancel(String TAG)

		//@TODO: Clear notifications in status bar
	}

	public void addAlarm(NotificationData n) {
		final long CURRENT_UTC = System.currentTimeMillis() / 1000; // seconds

		// Cancel any existing alarm with the same name
		cancelAlarm(n.name);
		removeAlarm(n.name);

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
			intent.putExtra("vibrate", n.vibrate);
			intent.putExtra("title", n.title);
			intent.putExtra("icon", n.icon);
			intent.putExtra("userDefined", n.userDefined);
			intent.putExtra("utc", n.utc);
			intent.putExtra("fromLocalNotify", true);
			intent.putExtra("tag", n.tag);

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
	
	private void removeNotification(String tag, String id) {
		int iNotifyID = 0;
		if(id != null && !id.equals("")){
			iNotifyID = Integer.parseInt(id);
		}
		NotificationManager notificationManager = (NotificationManager) _context.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel( tag, iNotifyID);
	}

	public void cancelAlarm(String name) {
		logger.log("{localNotify} Canceling alarm:", name);

		// Cancel alarm
		Intent intent = new Intent(ACTION_NOTIFY, null, _context, LocalNotifyPlugin.class);
		intent.addCategory(name); // for cancel

		_alarmManager.cancel(PendingIntent.getBroadcast(_context, ALARM_CODE, intent, PendingIntent.FLAG_CANCEL_CURRENT));
	}

	public void deliverAlarmToJS(NotificationData n) {
		// Deliver to JS without puting it in the status bar
		if (!_ready || !_active) {
			logger.log("{localNotify} JS not ready so pending alarm", n.name);
			_pending.add(n);
		} else {
			if (n.name.equals(_launchName)) {
				n.launched = true;
				_launchName = null;
				logger.log("{localNotify} Delivering launch alarm to JS:", n.name);
			} else {
				logger.log("{localNotify} Delivering alarm to JS:", n.name);
			}

			EventQueue.pushEvent(new NotifyEvent(n));
		}
	}

	public void deliverAlarm(NotificationData n) {
		removeAlarm(n.name);

		deliverAlarmToJS(n);
//@change
		if (!_active || true) {
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
			// no scheduled event with this name -- create one from the intent
			NotificationData n = new NotificationData();
			n.name = NAME;
			n.text = intent.getStringExtra("text");
			n.number = intent.getIntExtra("number", 1);
			n.sound = intent.getStringExtra("sound");
			n.title = intent.getStringExtra("title");
			n.icon = intent.getStringExtra("icon");
			n.vibrate = intent.getBooleanExtra("vibrate", false);
			n.userDefined = intent.getStringExtra("userDefined");
			n.tag = intent.getStringExtra("tag");

			deliverAlarm(n);

			//logger.log("{localNotify} Received unscheduled alarm for", NAME);
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
			logger.log("{localNotify} BOOT_COMPLETED ");
		} else if (action.equals(ACTION_NOTIFY)) {
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
					n.vibrate = intent.getBooleanExtra("vibrate", false);
					n.title = intent.getStringExtra("title");
					n.icon = intent.getStringExtra("icon");
					n.userDefined = intent.getStringExtra("userDefined");
					n.tag = intent.getStringExtra("tag");
					n.utc = intent.getLongExtra("utc", 0);
					n.launched = false;
					n.shown = false;

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
				ScheduledData old = _gson.fromJson(scheduledAlarms, ScheduledData.class);

				logger.log("{localNotify} Recovering", old.list.size(), "alarms");

				final int CURRENT_UTC = (int)( System.currentTimeMillis() / 1000 ); // seconds

				for (NotificationData n : old.list) {
					// If was launched by this one,
					if (_launchName != null && n.name.equals(_launchName)) {
						logger.log("{localNotify} Delivering startup notification to JS:", n.name);
						deliverAlarmToJS(n);
					} else if (n.utc >= CURRENT_UTC) {
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

			String alarms = _gson.toJson(box);

			SharedPreferences.Editor editor = _settings.edit();
			editor.putString("ScheduledAlarms", alarms);

			if (!PreferencesWrapper.runApply(editor)) {
				editor.commit();
			}
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

	public void onCreate(Activity activity, Bundle bundle) {
		try {
			Intent startingIntent = activity.getIntent();
		
			_launchName = null;
						
			
			if(startingIntent != null){
					
				String userDefined = startingIntent.getStringExtra("userDefined");	
				String message = startingIntent.getStringExtra("message");
				String tag = startingIntent.getStringExtra("tag");

				logger.log("{localNotify} startingIntent.getStringExtra(userDefined) : "+userDefined);
				logger.log("{localNotify}  startingIntent.getStringExtra(message) : "+message);

				logger.log("{localNotify}  startingIntent.getStringExtra(tag) : "+tag);
			}
			
			
			// If was launched from local notification,
			if (bundle != null && bundle.containsKey("fromLocalNotify")) {
				_launchName = bundle.getString("name");
				logger.log("{localNotify} Launched fromLocalNotify ****** ", _launchName);
				
				String userDefined = bundle.getString("userDefined");
				String message = bundle.getString("message");

				logger.log("{localNotify} Launched fromLocalNotify ****** userDefined "+userDefined);
				logger.log("{localNotify} Launched fromLocalNotify ****** message: "+message);
				logger.log("{localNotify} Launched fromLocalNotify ****** tag "+bundle.getString("tag"));

			}
			
			if (bundle != null){
				logger.log("{localNotify} NOT_localNotify bundle.size : "+bundle.size());
				String isPush = bundle.getString("isPush");
				String message = bundle.getString("message");

				logger.log("{localNotify} Launched from PUSH  ****** tag "+bundle.getString("tag"));
				logger.log("{localNotify}  Testing push notification "+message, isPush);								
				
			}
			
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception while reading create intent:", e);
		}

		_activity = activity;
	}

	public void onStart() {
		_active = true;
		readPreferences();
		DeliverPending();
	}

	public void onResume() {
		_active = true;
		DeliverPending();
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

	public void DeliverPending() {
		if (_ready && _active) {
			for (NotificationData n : _pending) {
				deliverAlarm(n);
			}

			_pending.clear();
		}
	}

	public void Ready(String jsonData) {
		try {
			logger.log("{localNotify} Ready");

			_ready = true;
			DeliverPending();
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
			removeAlarm(NAME);
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in remove:", e);
			e.printStackTrace();
		}
	}
	
	public void RemoveNotification(String jsonData) {
		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			final String id = jsonObject.getString("id");
			final String tag = jsonObject.getString("tag");

			logger.log("{localNotify} RemoveNotification requested for"+tag+"_"+ id);

			removeNotification(tag,id);
			
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in remove:", e);
			e.printStackTrace();
		}
	}

	public void RemoveAllNotification() {
		try {
			logger.log("{localNotify} Remove ALL Notification requested ");
			NotificationManager notificationManager = (NotificationManager) _context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in remove:", e);
			e.printStackTrace();
		}
	}

	public void Add(String jsonData) {
		try {						
			
			JSONObject jsonObject = new JSONObject(jsonData);
			final String NAME = jsonObject.getString("name");
			final String TEXT = jsonObject.optString("text", "");
			final int NUMBER = jsonObject.optInt("number", 0);
			final String SOUND = jsonObject.optString("sound", "");
			final boolean VIBRATE = jsonObject.optBoolean("vibrate", false);
			final String TITLE = jsonObject.optString("title", "");
			final String ICON = jsonObject.optString("icon", "");
			final int UTC = jsonObject.optInt("utc", 0); // seconds
			final String USER_DEFINED = jsonObject.optString("userDefined", "{}");
			final String NOTIFICATION_TAG = jsonObject.optString("tag", "{}");

			logger.log("{localNotify} Add Notification NUMBER :"+NUMBER);
			
			// Build notification object
			NotificationData n = new NotificationData();
			n.name = new String(NAME);
			n.text = new String(TEXT);
			n.number = NUMBER;
			n.sound = new String(SOUND);
			n.title = new String(TITLE);
			n.icon = new String(ICON);
			n.vibrate = VIBRATE;
			n.userDefined = new String(USER_DEFINED);
			n.tag = new String(NOTIFICATION_TAG);
			n.utc = UTC;
			n.launched = false;
			n.shown = false;

			addAlarm(n);
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in add:", e);
			e.printStackTrace();
		}
	}

	public void requestNotificationPermission(String jsonData) {
		logger.log(
			"{localNotify} Requesting permissions is a no-op on android"
		);
	}

	public void onActivityResult(Integer request, Integer resultCode, Intent data) {
	}

	public void onNewIntent(Intent intent) {

		String action = intent.getAction();
		String userDefined = intent.getStringExtra("userDefined");
		logger.log("{localNotify} onNewIntent from notification userDefined: "+userDefined);

		NotificationData n = new NotificationData();
		n.utc = System.currentTimeMillis();
		n.launched = true;
		n.shown = true;

		if(userDefined != null && userDefined.length() > 0){
			n.userDefined = userDefined;
		}
		
		// If looking at launch intent,
		if (action.equals("android.intent.action.MAIN")) {
			final boolean FROM_LOCALNOTIFY = intent.getBooleanExtra("fromLocalNotify", false);				
			// If launched from a notification,
			if (FROM_LOCALNOTIFY) {
				final String NAME = intent.getStringExtra("name");
				logger.log("{localNotify} App launched from notification:", NAME);
				_launchName = NAME;

				if(NAME != null && NAME.length() > 0){
					n.name = new String(NAME);
					n.tag = new String(NAME);
				}
			}
		};

		if(n.userDefined != null || n.name != null){
			EventQueue.pushEvent(new NotificationClickEvent(n));
		}
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

