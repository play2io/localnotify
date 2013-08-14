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

import com.tealeaf.EventQueue;
import com.tealeaf.event.*;

public class LocalNotifyPlugin extends BroadcastReceiver implements IPlugin {
	Context _ctx = null;
	Activity _activity = null;
	AlarmManager _alarmManager = null;
	final String ACTION_MY_NOTIFY = "com.tealeaf.plugin.plugins.LocalNotifyPlugin.NOTIFICATION_MESSAGE";

	public class NotificationData {
		String name, text, sound, title, icon, userDefined;
		int number;
		int utc;
	}

	public class ListEvent extends com.tealeaf.event.Event {
		ArrayList<NotificationData> list;

		public PurchaseEvent(ArrayList<NotificationData> list) {
			super("LocalNotifyList");
			this.list = list;
		}
	}

	public class GetEvent extends com.tealeaf.event.Event {
		NotificationData info;

		public ConsumeEvent(NotificationData info) {
			super("LocalNotifyGet");
			this.info = info;
		}
	}

	public class NotifyEvent extends com.tealeaf.event.Event {
		NotificationData info;

		public OwnedEvent(NotificationData info) {
			super("LocalNotify");
			this.info = info;
		}
	}

	public void cancelLocalNotification(String id) {
		Intent intent = new Intent(ACTION_MY_NOTIFY);
		intent.setAction("NOTIFICATION_MESSAGE");
		intent.setClassName(_context.getPackageName(), "by.wee.sdk.NotificationManager");
		intent.addCategory(id);
		PendingIntent notificationIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
		_alarmManager.cancel(notificationIntent);
	}

	//Schedules a notification to be displayed after the delay time in milliseconds.
	public void scheduleLocalNotificationInStatusBar(Context context, long delay, String id, String title, String message, int count) {
		//Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
		Intent intent = new Intent();
		intent.setAction("NOTIFICATION_MESSAGE");
		intent.setClassName(context.getPackageName(), "by.wee.sdk.NotificationManager");
		intent.addCategory(id);
		intent.putExtra("eventType", "scheduledNotification");
		intent.putExtra("id", id);
		intent.putExtra("title", title);
		intent.putExtra("message", message);
		intent.putExtra("count", count);

		PendingIntent notificationIntent = PendingIntent.getBroadcast(context, 0, intent, 0);

		_alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + delay, notificationIntent);
	}

	public static String TITLE = "TITLE";
	public static String MESSAGE = "MESSAGE";
	public static String IS_LOCAL = "IS_LOCAL";
	public static String ID = "ID";
	public static String EXTRAS = "EXTRAS";

	public void showNotificationInStatusBar(Context context, String id, String title, String message, int count, boolean isLocal, Bundle extras) {

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
			.setAutoCancel(true)
			.setSmallIcon(context.getResources().getIdentifier("icon", "drawable", context.getPackageName()))
			.setContentTitle(title)
			.setContentText(message)
			.setTicker(title)
			//.setNumber(count)
			.setOnlyAlertOnce(false)
			.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND);


		Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(ID, id);
		intent.putExtra(TITLE, title);
		intent.putExtra(MESSAGE, message);
		intent.putExtra(IS_LOCAL, isLocal);
		if (extras != null) {
			intent.putExtra(EXTRAS, extras);
		}
		PendingIntent pending = PendingIntent.getActivity(context, PUSH_GROUP_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		//set content intent
		mBuilder.setContentIntent(pending);


		//Intent deleteIntent = getLocalNotificationIntent(DELETE_NOTIFICATION_INTENT_ACTION, id, title, message);
		//PendingIntent deletePending = PendingIntent.getBroadcast(context, 0, deleteIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		//set delete intent
		//mBuilder.setDeleteIntent(deletePending);

		Notification notification = mBuilder.build();

		if (count > 1) {
			notification.number = count;
		}

		((android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(PUSH_GROUP_ID, notification);
	}

    @Override
    public void onReceive(final Context context, Intent intent) {
        logger.log("notificationManager onReceive");
        if (intent.hasExtra("eventType") && intent.getExtras().getString("eventType").equals("scheduledNotification")) {
            if (!isAppInBackground(context)) {
                String id = intent.getStringExtra("id");
                String title = intent.getStringExtra("title");
                String message = intent.getStringExtra("message");
                EventQueue.pushEvent(new WeebyPushNotificationEvent(id,
                            title,
                            message,
                            true,
                            false));
            } else {
                handleScheduledNotification(context, intent);

            }
        }
    }

	public LocalNotifyPlugin() {
	}

	public void onCreateApplication(Context applicationContext) {
		_ctx = applicationContext;
		_alarmManager = (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE);
	}

	public void onCreate(Activity activity, Bundle savedInstanceState) {
		logger.log("{localNotify} Initializing");

		_activity = activity;
	}

	public void onResume() {
	}

	public void onStart() {
	}

	public void onPause() {
	}

	public void onStop() {
	}

	public void onDestroy() {
	}

	public void Ready(String jsonData) {
		try {
			logger.log("{localNotify} Ready");
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in ready:", e);
			e.printStackTrace();
		}
	}

	public void List(String jsonData) {
		try {
			logger.log("{localNotify} Listing scheduled alarms");
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in list:", e);
			e.printStackTrace();
		}
	}

	public void Get(String jsonData) {
		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			final String NAME = jsonObject.getString("name");

			logger.log("{localNotify} Get requested for", name);
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in get:", e);
			e.printStackTrace();
		}
	}

	public void Clear(String jsonData) {
		try {
			logger.log("{localNotify} Clearing scheduled alarms");
		} catch (Exception e) {
			logger.log("{localNotify} WARNING: Exception in clear:", e);
			e.printStackTrace();
		}
	}

	public void Remove(String jsonData) {
		try {
			JSONObject jsonObject = new JSONObject(jsonData);
			final String NAME = jsonObject.getString("name");

			logger.log("{localNotify} Remove requested for", name);
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
			final int UTC = jsonObject.getInt("utc");
			final String USERDEFINED = jsonObject.getString("userDefined");

	        NotificationManager.get().scheduleLocalNotificationInStatusBar(activity, delay, id, title, message, count);

			logger.log("{localNotify} Add requested for", name);
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

