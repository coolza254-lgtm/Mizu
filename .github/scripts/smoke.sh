#!/usr/bin/env bash
# Launch smoke test: install the debug APK on the emulator, open it, fail if it crashes.
set -u
APK=$(ls app-apk/*.apk | head -1)
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n com.example.mizu/.MainActivity
sleep 20
adb logcat -d -b crash > crash.txt
adb logcat -d -t 400 '*:E' > errors.txt
echo "===== crash buffer ====="
cat crash.txt
if adb shell pidof com.example.mizu > /dev/null && [ ! -s crash.txt ]; then
  echo "SMOKE OK: Mizu is running"
else
  echo "SMOKE FAILED: Mizu is not running or crashed"
  echo "===== recent errors ====="
  cat errors.txt
  exit 1
fi
