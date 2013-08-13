#import "LocalNotifyPlugin.h"
#import <UIKit/UILocalNotification.h>

// TODO: Notifications will get lost if JavaScript never requests them

@implementation LocalNotifyPlugin

- (void) dealloc {
	self.pendingNotifications = nil;

	[super dealloc];
}

- (id) init {
	self = [super init];
	if (!self) {
		return nil;
	}

	self.pendingNotifications = [NSMutableArray array];

	return self;
}

- (void) cancelOldNotifications {
	// Cancel any notifications with the same name
	NSArray *notifications = [[UIApplication sharedApplication] scheduledLocalNotifications];
	for (int ii = 0, len = [notifications count]; ii < len; ++ii) {
		UILocalNotification *n = [notifications objectAtIndex:ii];

		// If expired,
		if ([n.fireDate timeIntervalSinceNow] <= 0) {
			NSLog(@"{localNotify} Canceling expired notification");

			[[UIApplication sharedApplication] cancelLocalNotification:n];
		}
	}

	[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];
}

- (void) applicationDidBecomeActive:(UIApplication *)app {
	[self cancelOldNotifications];
}

- (void) cancelNotificationByName:(NSString *)name {
	// Cancel any notifications with the same name
	NSArray *notifications = [[UIApplication sharedApplication] scheduledLocalNotifications];
	for (int ii = 0, len = [notifications count]; ii < len; ++ii) {
		UILocalNotification *evt = [notifications objectAtIndex:ii];
		NSString *evtName = [evt.userInfo valueForKey:@"name"];

		if ((evtName != nil) && [name caseInsensitiveCompare:evtName] == NSOrderedSame) {
			[[UIApplication sharedApplication] cancelLocalNotification:evt];
		}
	}
}

- (UILocalNotification *) getNotificationByName:(NSString *)name {
	// Cancel any notifications with the same name
	NSArray *notifications = [[UIApplication sharedApplication] scheduledLocalNotifications];
	for (int ii = 0, len = [notifications count]; ii < len; ++ii) {
		UILocalNotification *evt = [notifications objectAtIndex:ii];
		NSString *evtName = [evt.userInfo valueForKey:@"name"];

		if ((evtName != nil) && [name caseInsensitiveCompare:evtName] == NSOrderedSame) {
			return evt;
		}
	}

	return nil;
}

- (NSDictionary *) getNotificationObject:(UILocalNotification *)n {
	// Convert fireDate to UTC integer in seconds
	NSNumber *utc = nil;
	if (n.fireDate != nil) {
		utc = [NSNumber numberWithInt:(int)([n.fireDate timeIntervalSince1970] + 0.5)];
	}

	return [NSDictionary dictionaryWithObjectsAndKeys:
			[n.userInfo valueForKey:@"name"],@"name",
			[NSNumber numberWithInteger:n.applicationIconBadgeNumber],@"number",
			(n.soundName != nil) ? n.soundName : [NSNull null],@"sound",
			(n.alertAction != nil) ? n.alertAction : [NSNull null],@"action",
			(n.alertBody != nil) ? n.alertBody : [NSNull null],@"text",
			(utc != nil) ? utc : [NSNull null],@"utc",
			[n.userInfo valueForKey:@"userDefined"],@"userDefined", nil];
}

- (void) reportNotification:(UILocalNotification *)n {
	if (n != nil) {
		if (self.readyForNotifications == YES) {
			NSLog(@"{localNotify} Reporting local notification");

			[[PluginManager get] dispatchJSEvent:[NSDictionary dictionaryWithObjectsAndKeys:
												  @"LocalNotify",@"name",
												  [self getNotificationObject:n],@"info", nil]];
		} else {
			NSString *name = [n.userInfo valueForKey:@"name"];
			
			for (int ii = 0; ii < [self.pendingNotifications count]; ++ii) {
				UILocalNotification *evt = [self.pendingNotifications objectAtIndex:ii];
				NSString *evtName = [evt.userInfo valueForKey:@"name"];

				if ((evtName != nil) && [name caseInsensitiveCompare:evtName] == NSOrderedSame) {
					NSLog(@"{localNotify} Refusing to store the same local notification twice");
					return;
				}
			}

			NSLog(@"{localNotify} Storing new local notification");

			[self.pendingNotifications addObject:n];
		}
	}
}

- (void) didReceiveLocalNotification:(UILocalNotification *)notification application:(UIApplication *)app {
	[self reportNotification:notification];
}

- (void) initializeWithManifest:(NSDictionary *)manifest appDelegate:(TeaLeafAppDelegate *)appDelegate {
	@try {
		TeaLeafAppDelegate *app = (TeaLeafAppDelegate *)[[UIApplication sharedApplication] delegate];

		[self reportNotification:(UILocalNotification *)app.launchNotification];
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during initialization: %@", exception);
	}
}

- (void) Ready:(NSDictionary *)jsonObject {
	@try {
		NSLog(@"{localNotify} Ready received.  Sending queued events");

		// Flag ready
		self.readyForNotifications = YES;

		// Send all pending
		for (int ii = 0, len = [self.pendingNotifications count]; ii < len; ++ii) {
			UILocalNotification *n = [self.pendingNotifications objectAtIndex:ii];
			[self reportNotification:n];
		}

		// Remove pending
		[self.pendingNotifications removeAllObjects];
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during Ready: %@", exception);
	}
}

- (void) List:(NSDictionary *)jsonObject {
	@try {
		NSLog(@"{localNotify} List requested");

		NSArray *notifications = [[UIApplication sharedApplication] scheduledLocalNotifications];
		for (int ii = 0, len = [notifications count]; ii < len; ++ii) {
			UILocalNotification *evt = [notifications objectAtIndex:ii];
			NSString *evtName = [evt.userInfo valueForKey:@"name"];
			
			// TODO
		}
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during List: %@", exception);

		[[PluginManager get] dispatchJSEvent:[NSDictionary dictionaryWithObjectsAndKeys:
											  @"LocalNotifyList",@"name",
											  [NSNull null],@"list",
											  exception,@"error", nil]];
	}
}

- (void) Get:(NSDictionary *)jsonObject {
	@try {
		NSString *name = [jsonObject valueForKey:@"name"];

		NSLog(@"{localNotify} Get requested for %@", name);

		UILocalNotification *n = [self getNotificationByName:name];

		if (n == nil) {
			[[PluginManager get] dispatchJSEvent:[NSDictionary dictionaryWithObjectsAndKeys:
												  @"LocalNotifyGet",@"name",
												  @"not found",@"error", nil]];
		} else {
			[[PluginManager get] dispatchJSEvent:[NSDictionary dictionaryWithObjectsAndKeys:
												  @"LocalNotifyGet",@"name",
												  [self getNotificationObject:n],@"info", nil]];
		}
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during Get: %@", exception);
		
		[[PluginManager get] dispatchJSEvent:[NSDictionary dictionaryWithObjectsAndKeys:
											  @"LocalNotifyGet",@"name",
											  exception,@"error", nil]];
	}
}

- (void) Clear:(NSDictionary *)jsonObject {
	@try {
		NSLog(@"{localNotify} Clearing all notifications");

		[[UIApplication sharedApplication] cancelAllLocalNotifications];

		[[UIApplication sharedApplication] setApplicationIconBadgeNumber:0];
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during Clear: %@", exception);
	}
}

- (void) Remove:(NSDictionary *)jsonObject {
	@try {
		NSString *name = [jsonObject valueForKey:@"name"];

		NSLog(@"{localNotify} Remove requested for %@", name);

		[self cancelNotificationByName:name];
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during Remove: %@", exception);
	}
}

- (void) Add:(NSDictionary *)jsonObject {
	@try {
		NSString *name = [jsonObject valueForKey:@"name"];

		NSLog(@"{localNotify} Add requested for %@", name);

		NSString *text = [jsonObject valueForKey:@"text"];
		NSNumber *number = [jsonObject valueForKey:@"number"];
		id sound = [jsonObject valueForKey:@"sound"];
		NSString *action = [jsonObject valueForKey:@"action"];
		NSNumber *utc = [jsonObject valueForKey:@"utc"];
		NSDictionary *userDefined = [jsonObject valueForKey:@"userDefined"];

		// Construct notification from input
		UILocalNotification *n = [[UILocalNotification alloc] init];
		n.alertAction = action;
		n.hasAction = (action != nil);
		n.alertBody = text;
		n.applicationIconBadgeNumber = (number != nil) ? [number integerValue] : 0;
		if (sound != nil) {
			if ([sound isKindOfClass:[NSString class]]) {
				n.soundName = sound;
			} else if ([sound isKindOfClass:[NSNumber class]]) {
				NSNumber *num = sound;
				if ([num intValue] == 1) {
					n.soundName = UILocalNotificationDefaultSoundName;
				}
			}
		}
		n.userInfo = [NSDictionary dictionaryWithObjectsAndKeys:
					  userDefined,@"userDefined",
					  name,@"name", nil];

		// If fire date is specified,
		if (utc != nil) {
			NSDate *fireDate = [NSDate dateWithTimeIntervalSince1970:[utc integerValue]];

			// If date is in the past,
			if ([fireDate compare:[NSDate date]] == NSOrderedAscending) {
				NSLog(@"{localNotify} Adding scheduled event %@ date in the past, so scheduling it immediately", name);

				utc = nil;
			} else {
				n.fireDate = fireDate;
				n.timeZone = [NSTimeZone defaultTimeZone];
			}
		}

		// Cancel existing one
		[self cancelNotificationByName:name];

		// If it should be scheduled in the future,
		if (utc != nil) {
			[[UIApplication sharedApplication] scheduleLocalNotification:n];
		} else {
			[[UIApplication sharedApplication] presentLocalNotificationNow:n];
		}
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during Add: %@", exception);
	}
}

@end
