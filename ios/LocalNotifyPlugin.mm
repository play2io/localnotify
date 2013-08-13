#import "LocalNotifyPlugin.h"

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

		// TODO
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

		NSDictionary *info = nil;

		// TODO
		
		[[PluginManager get] dispatchJSEvent:[NSDictionary dictionaryWithObjectsAndKeys:
											  @"LocalNotifyGet",@"name",
											  name,@"name",
											  info,@"info",
											  nil]];
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
		
		// TODO
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during Remove: %@", exception);
	}
}

- (void) Add:(NSDictionary *)jsonObject {
	@try {
		NSString *name = [jsonObject valueForKey:@"name"];
		
		NSLog(@"{localNotify} Add requested for %@", name);
		
		// TODO: Remove old one
		// TODO: Add new one
	}
	@catch (NSException *exception) {
		NSLog(@"{localNotify} WARNING: Exception during Add: %@", exception);
	}
}

@end
