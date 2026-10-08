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

# Full-length views: lower the density so a whole scrolling page fits in one capture.
adb shell wm density 200
for screen in home history settings; do
  adb shell am start -S -W -n $PKG/.MainActivity --es mizu_screen "$screen" --ez mizu_demo true
  sleep 7
  adb exec-out screencap -p > "shots/${screen}_full.png"
done
adb shell wm density reset

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
