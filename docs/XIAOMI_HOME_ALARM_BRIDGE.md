# Xiaomi Home bridge

CuponApp sends a package-targeted Android broadcast after a trusted, incoming security alarm SMS. This option is disabled by default.

ARMED -> com.moez.QKSMS.alarmcamera.WAKE
DISARMED -> com.moez.QKSMS.alarmcamera.SLEEP

Default receiver: com.arlosoft.macrodroid. No SMS text or phone number is broadcast.

## Phone setup

1. In Xiaomi Home create two manual scenes wake and sleep and test them on both cameras.
2. Long-press each scene and select Home screen shortcuts.
3. Install MacroDroid and IntentTask. To capture protected Xiaomi Home deep shortcuts, IntentTask may temporarily require being set as the default launcher.
4. In MacroDroid create two macros with triggers Intent Received and the actions above.
5. For each macro use Tasker/Locale Plugin > IntentTask > Launch app shortcut and choose the respective wake/sleep scene.
6. In CuponApp Settings > AlarmGuard test both requests and verify cameras really change state; then enable automatic camera control.
7. Allow background execution for MacroDroid and IntentTask.

Android shortcuts usually cannot be executed directly by a normal background app. A sent broadcast is not camera confirmation; bridge and Xiaomi services might fail.

References:
https://macrodroidforum.com/wiki/index.php/Trigger:Intent_Received
https://www.reddit.com/r/macrodroid/comments/1d9b2jz/
https://developer.android.com/reference/android/content/pm/LauncherApps