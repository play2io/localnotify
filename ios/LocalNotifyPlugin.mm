#import "LocalNotifyPlugin.h"
#import <UIKit/UILocalNotification.h>

@implementation LocalNotifyPlugin

- (void) dealloc {
	[super dealloc];
}

- (id) init {
	self = [super init];
	if (!self) {
		return nil;
	}

	return self;
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

- (void) initializeWithManifest:(NSDictionary *)manifest appDelegate:(TeaLeafAppDelegate *)appDelegate {
	@try {
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during initialization: %@", exception);
	}
}

- (void) Ready:(NSDictionary *)jsonObject {
	@try {
		NSLog(@"{localNotify} Ready received.  Sending queued events");

		// TODO
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
		}
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during List: %@", exception);

		[[PluginManager get] dispatchJSEvent:[NSDictionary dictionaryWithObjectsAndKeys:
											  @"LocalNotifyList",@"name",
											  [NSNull null],@"list",
											  exception,@"error",
											  nil]];
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
												  name,@"name",
												  nil]];
		} else {
			NSDictionary *info = nil;

			[NSDictionary dictionaryWithObjectsAndKeys:
			 name,@"name",
			 number,@"number",
			 sound,@"sound",
			 action,@"action",
			 text,nil]
			
			[[PluginManager get] dispatchJSEvent:[NSDictionary dictionaryWithObjectsAndKeys:
												  @"LocalNotifyGet",@"name",
												  name,@"name",
												  info,@"info",
												  nil]];
		}
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during Get: %@", exception);
		
		[[PluginManager get] dispatchJSEvent:[NSDictionary dictionaryWithObjectsAndKeys:
											  @"LocalNotifyGet",@"name",
											  exception,@"error",
											  nil]];
	}
}

- (void) Clear:(NSDictionary *)jsonObject {
	@try {
		NSLog(@"{localNotify} Clearing all notifications");

		[[UIApplication sharedApplication] cancelAllLocalNotifications];
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
		NSString *sound = [jsonObject valueForKey:@"sound"];
		NSString *action = [jsonObject valueForKey:@"action"];
		NSString *icon = [jsonObject valueForKey:@"icon"];
		NSNumber *utc = [jsonObject valueForKey:@"utc"];
		NSDictionary *userDefined = [jsonObject valueForKey:@"userDefined"];

		// Construct notification from input
		UILocalNotification *n = [[UILocalNotification alloc] init];
		n.alertAction = action;
		n.hasAction = (action != nil);
		n.alertBody = text;
		n.alertLaunchImage = icon;
		n.applicationIconBadgeNumber = (number != nil) ? [number integerValue] : 0;
		n.soundName = sound;
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
