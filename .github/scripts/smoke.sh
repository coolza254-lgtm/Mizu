#!/usr/bin/env bash
# Launch smoke test + screenshots: install the debug APK, open each screen with demo data,
# capture a screenshot of each, and fail if the app crashes.
set -u
PKG=com.example.mizu
APK=$(ls app-apk/*.apk | head -1)
mkdir -p shots
adb install -r "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

for screen in home history settings weigh; do
  adb shell am start -S -W -n $PKG/.MainActivity --es mizu_screen "$screen" --ez mizu_demo true
  sleep 7
  adb exec-out screencap -p > "shots/$screen.png"
done

# Full-screen alarm reminder.
adb shell am start -W -n $PKG/.MainActivity --ez mizu_alarm true
sleep 5
adb exec-out screencap -p > "shots/alarm.png"
adb shell input keyevent KEYCODE_BACK
sleep 2

# Reminder notification with the progress bar, expanded in the shade.
# Force-stop first and let it settle: a force-stop clears the app's notifications, and the emulator
# can process it seconds late, which would wipe the notification we are about to post.
adb shell am force-stop $PKG
sleep 12
adb shell am start -W -n $PKG/.MainActivity --ez mizu_notif true
sleep 4
adb shell cmd statusbar expand-notifications
sleep 3
adb exec-out screencap -p > "shots/notification.png"
adb shell cmd statusbar collapse
echo "===== Mizu notifications ====="
adb shell dumpsys notification --noredact | grep -A6 "pkg=$PKG" | head -40 || true
echo "===== notification service log ====="
adb logcat -d | grep -iE "NotificationService|NotificationManager|RemoteViews|$PKG" | grep -ivE "uid=|ActivityManager" | tail -40 || true


sleep 3
adb logcat -d -b crash > crash.txt
echo "===== crash buffer ====="
cat crash.txt
if adb shell pidof $PKG > /dev/null && [ ! -s crash.txt ]; then
  echo "SMOKE OK: Mizu is running"
else
  echo "SMOKE FAILED: Mizu is not running or crashed"
  echo "===== Mizu errors ====="
  adb logcat -d -t 600 | grep -E "AndroidRuntime|$PKG|FATAL" | tail -80
  exit 1
fi
