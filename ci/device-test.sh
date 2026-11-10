#!/usr/bin/env bash
# Runs on the CI emulator: install the APK, launch it, check for crashes, run the
# instrumented UI tests and collect screenshots/logs into device-out/.
set -u
API="$1"
OUT=device-out
PKG=com.avatarsdev.tiktokupgrade
mkdir -p "$OUT/screens"
fail=0
ensure_device() {
  adb reconnect offline > /dev/null 2>&1 || true
  timeout 120 adb wait-for-device || true
  for _ in $(seq 1 60); do
    [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && return 0
    sleep 2
  done
  echo "EMULATOR NOT RESPONDING"
}
step() { echo "::group::$1"; ensure_device; }
endstep() { echo "::endgroup::"; }

ensure_device
{ adb shell getprop ro.build.version.release; adb shell getprop ro.build.version.sdk; adb shell getprop persist.sys.timezone; } > "$OUT/android_version.txt"

step "Install APK"
adb install -r out/app-debug.apk 2>&1 | tee "$OUT/install.txt"
grep -q "Success" "$OUT/install.txt" || { echo "INSTALL FAILED"; fail=1; }
adb shell dumpsys package "$PKG" | grep -E "versionName|versionCode|minSdk|targetSdk" | tee "$OUT/package_info.txt"
endstep

step "Launch app (cold start)"
adb logcat -c
adb shell am start -W -n "$PKG/.ui.MainActivity" 2>&1 | tee "$OUT/launch.txt"
sleep 6
adb shell pidof "$PKG" > "$OUT/pid.txt" || { echo "APP NOT RUNNING AFTER LAUNCH"; fail=1; }
adb exec-out screencap -p > "$OUT/screens/00_launch_api$API.png"
endstep

step "Share a clip into the app (ACTION_SEND)"
adb shell am start -a android.intent.action.SEND -t text/plain \
  --es android.intent.extra.TEXT "'สอนทำเกม Roblox Studio #roblox https://www.tiktok.com/@dev/video/7434000000000000001'" \
  -n "$PKG/.ui.MainActivity" 2>&1 | tee "$OUT/share.txt"
sleep 4
adb exec-out screencap -p > "$OUT/screens/00b_share_api$API.png"
endstep

step "Crash check (manual run)"
adb logcat -d > "$OUT/logcat_app.txt"
if grep -E "FATAL EXCEPTION|ANR in $PKG" "$OUT/logcat_app.txt"; then echo "CRASH DETECTED"; fail=1; fi
endstep

step "Instrumented tests"
adb install -r out/app-debug-androidTest.apk 2>&1 | tee -a "$OUT/install.txt"
adb shell am force-stop "$PKG"
adb logcat -c
adb shell am instrument -w -r "$PKG.test/androidx.test.runner.AndroidJUnitRunner" 2>&1 | tee "$OUT/instrumentation.txt"
if grep -q "^OK (" "$OUT/instrumentation.txt"; then echo "INSTRUMENTATION OK"; else echo "INSTRUMENTATION FAILED"; fail=1; fi
adb logcat -d > "$OUT/logcat_tests.txt"
adb pull "/sdcard/Android/data/$PKG/files/screens" "$OUT/screens/ui" > /dev/null 2>&1 || echo "no ui screenshots pulled"
endstep

echo "fail=$fail" | tee "$OUT/result.txt"
exit $fail
