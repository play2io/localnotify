# Game Closure DevKit Plugin: Local Notifications

This plugin supports local notifications on iOS and Android platforms.

## Overview

Local notifications allow you to add messages to the phone's status area.

Notifications can be used for social re-engagement for games, indicating that
a friend has sent the player a gift.  Tapping the re-engagement notification
will bring the player back into the game.

When delayed by a number of days, notifications can also be used as to
re-engage players without requiring social features.

Notifications appear in the status area with a number of features:

+ sound: A ringtone-like notification sound that alerts the user.
+ number: Count of notifications represented by that one line.
+ body: Text describing the notification to the user.
+ delay: How far in the future to deliver the nofication (or immediately).
+ icon: Major icon/photo to display for notification.
+ action: Name of action for accepting notification. [iOS only]
+ title: Title for alert is Android status area. [Android only]

## Installation

Install the Local Notifications plugin by running `basil install localnotify`.

Include it in the `manifest.json` file under the "addons" section for your game:

~~~
"addons": [
	"localnotify"
],
~~~

You can import the localNotify object anywhere in your application:

~~~
import plugins.localnotify.localNotify as localNotify;
~~~

## Scheduling Notifications

localNotify.add({
	name: "HeartAlert",
	body: "Melissa has sent you a Heart in Blustery Badgers!"
});

### Notification Counter

Adding the `number` field to your notification object will modify how the
notification appears:

~~~
localNotify.add({
	name: "HeartAlert",
	number: 2,
	body: "2 friends have sent you Hearts in Blustery Badgers!"
});
~~~

##### iPhone/iPad

The notification counter on your app icon will be set to `number`.
When your app is launched, the counter will be reset to zero.

##### Android

This number will be shown next to the notification in the status area list.

### Combining Notifications

You may choose to group gifts received from other players into a single
notification to avoid overwhelming the player by using the following code:

~~~
function addHeartNotification(fromPlayer, gameName) {
	// Check if single-heart notification exists
	localNotify.get("heart", function(heart) {
		if (heart) {
			var heartCount = heart.count + 1;

			// This will overwrite any existing "hearts" notification
			localNotify.add({
				name: "hearts",
				action: "Accept All",
				title: "Received Gifts: Hearts",
				body: "You have received " + heartCount + " Hearts from friends in " + gameName + "!",
				number: heartCount
			});

			localNotify.remove("heart");
		} else {
			localNotify.add({
				name: "heart",
				action: "Accept",
				title: "Received Gift: Heart",
				body: "You have received a Heart from " + fromPlayer + " in " + gameName + "!"
			});
		}
	});
}
~~~

## Handling Notifications

When users accept your app notification it will launch your game.  To discover
if the game was launched from a notification or through selecting it from the
app list, you can add an `onNotify` handler to the `localNotify` object:

~~~
localNotify.onNotify = function(evt) {
	logger.log("Notification completed:", evt.name);
}
~~~

## Removing Notifications

To remove all notifications from your game, call `localNotify.clear()`.

To remove a specific notification by name, call `localNotify.remove("name")`.

## Get Pending Notifications

To list any pending notifications that are scheduled to be delivered, use the
`localNotify.list()` function:

~~~
localNotify.list(function(notifications) {
	for (var ii = 0; ii < notifications.length; ++ii) {
		logger.log("Pending notification:", notifications[ii].name);
	}
});
~~~

## Get Notification by Name

To get a pending notification by name, use the `localNotify.get("name")` function:

~~~
localNotify.get("name", function(notification) {
	if (notification) {
		logger.log("Notification body:", notification.body);
	} else {
		logger.log("Notification DNE");
	}
});
~~~

# localNotify object

## Members:

### localNotify.onNotify (evt)

+ `callback {function}` ---Set to your callback function.
			The first argument will be the object for the triggered notification.

Called whenever a local notification is accepted by the user.  This is the case
for notifications that trigger re-engagement while the app is closed.  In this
event the event(s) that triggered re-engagement will be delivered as soon as the
`onNotify` callback is set.

~~~
localNotify.onNotify = function(evt) {
	logger.log("Got event:", evt.name);
});
~~~

## Methods:

### billing.purchase (itemName, [simulate])

Parameters
:	1. `itemName {string}` ---The item name string.
	2. `[simulate {string}]` ---Optional simulation mode: `undefined` means disabled. "simulate" means simulate a successful purchase.  Any other value will be used as a simulated failure string.

Returns
:    1. `void`

Initiate the purchase of an item by its name.

The purchase may fail if the player clicks to deny the purchase, or if the network is unavailable, among other reasons.  If the purchase fails, the `billing.onFailure` handler will be called.  Handling failures is optional.

If the purchase succeeds, then the `billing.onPurchase` callback you set will be called.  This callback should be where you credit the user for the purchase.

~~~
billing.purchase("fiveCoins");
~~~

##### Simulation Mode

To test purchases without hitting the market, pass a second parameter of "simulate" or "cancel".  On browser builds or in the web simulator, purchases will always simulate success otherwise.

~~~
billing.purchase("fiveCoins", "simulate"); // Simulates success
billing.purchase("fiveCoins", "cancel"); // Simulates failure "cancel"
~~~
