#!/usr/bin/env bash
# Runs on the CI emulator: install the APK, launch it, start the local server through the UI,
# probe it from the host, run instrumented tests, collect screenshots and logs.
set -u
API="$1"
OUT=device-out
PKG=com.avatarsdev.freellmapi
mkdir -p "$OUT/screens"
fail=0
step() { echo "::group::$1"; }
endstep() { echo "::endgroup::"; }

adb wait-for-device
adb shell getprop ro.build.version.release > "$OUT/android_version.txt"
adb shell getprop ro.build.version.sdk >> "$OUT/android_version.txt"

step "Install APK"
adb install -r out/app-debug.apk 2>&1 | tee "$OUT/install.txt"
grep -q "Success" "$OUT/install.txt" || { echo "INSTALL FAILED"; fail=1; }
adb shell dumpsys package "$PKG" | grep -E "versionName|versionCode|minSdk|targetSdk" | tee "$OUT/package_info.txt"
endstep

step "Launch app"
adb logcat -c
adb shell am start -W -n "$PKG/.ui.MainActivity" 2>&1 | tee "$OUT/launch.txt"
sleep 8
adb shell pidof "$PKG" > "$OUT/pid.txt" || { echo "APP NOT RUNNING AFTER LAUNCH"; fail=1; }
adb exec-out screencap -p > "$OUT/screens/00_launch_api$API.png"
endstep

step "Start local API server via UI and probe it from the host"
python3 ci/ui_server_probe.py "$OUT" "$PKG" "$API" || fail=1
endstep

step "Tablet layout (navigation rail)"
adb shell wm size 2560x1600
adb shell wm density 320
sleep 3
adb shell am start -W -n "$PKG/.ui.MainActivity" > /dev/null 2>&1
sleep 4
adb exec-out screencap -p > "$OUT/screens/90_tablet_api$API.png"
adb shell wm size reset
adb shell wm density reset
sleep 2
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
adb pull "/sdcard/Android/data/$PKG/files/screens" "$OUT/screens/ui" > /dev/null 2>&1 || adb shell "run-as $PKG ls" > /dev/null 2>&1
endstep

echo "fail=$fail" | tee "$OUT/result.txt"
exit $fail
