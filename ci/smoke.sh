#!/usr/bin/env bash
# Kiểm thử khói trên emulator: cài APK, mở từng màn hình, chụp ảnh, thu log crash.
set -x
mkdir -p out
APK=$(ls apk/debug/*.apk | head -1)
adb install -r -g "$APK"
adb logcat -c

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "out/$1.png"; }
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb pull /sdcard/ui.xml out/ui.xml > /dev/null
  local xy
  xy=$(python3 ci/tap.py out/ui.xml "$1")
  if [ -n "$xy" ]; then adb shell input tap $xy; else echo "NOT FOUND: $1" | tee -a out/missing.txt; fi
}
back() { adb shell input keyevent 4; sleep 2; }

adb shell am start -n vn.dichtructiep/.MainActivity
shot 01_home 8

tap "Nghe & Dịch";          shot 02_listen
tap "Bắt đầu nghe";         shot 03_listen_running 5
tap "Dừng";                 shot 04_listen_stopped 2
back

tap "Hội thoại 2 người";    shot 05_conversation
tap "Tự động nghe cả hai";  shot 06_conversation_running 5
back

tap "Dịch văn bản";         shot 07_text 2
tap "Nhập, dán";            sleep 1
adb shell input text "Good%smorning%severyone,%swelcome%sto%sthe%smeeting."
shot 08_text_translated 25
back; back

tap "Lịch sử";              shot 09_history
back
tap "Cài đặt";              shot 10_settings
adb shell input swipe 500 1800 500 600 300
shot 11_settings_api 2
back

adb shell pidof vn.dichtructiep > out/pid.txt || echo "APP NOT RUNNING" > out/pid.txt
adb logcat -d -b crash > out/crash.txt
adb logcat -d | grep -iE "dichtructiep|AndroidRuntime|FATAL" > out/logcat.txt || true
exit 0
