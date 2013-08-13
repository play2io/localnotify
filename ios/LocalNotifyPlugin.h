#import "PluginManager.h"

@interface LocalNotifyPlugin : GCPlugin

- (UILocalNotification *) getNotificationByName:(NSString *)name;
- (void) cancelNotificationByName:(NSString *)name;

@end
