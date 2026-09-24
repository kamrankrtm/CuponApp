#!/usr/bin/env bash
# Drives the app on an emulator and captures every main screen in light and dark mode.
# Runs inside reactivecircus/android-emulator-runner; screenshots land in ./shots/.
# Deliberately not `set -e`: one screen failing to open should not cost the rest.
set -u
PKG=com.moez.QKSMS
MAIN=$PKG/.feature.main.MainActivity
OUT=shots
mkdir -p "$OUT"

log() { echo "::notice::$*"; }

shot() {
  sleep "${2:-2}"
  adb exec-out screencap -p > "$OUT/$1.png"
  log "captured $1 ($(stat -c%s "$OUT/$1.png") bytes)"
}

# Tap the centre of the first view whose text or content-desc matches $1 exactly.
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys
want = sys.argv[1]
xml = open('/tmp/ui.xml', encoding='utf-8').read()
for node in re.finditer(r'<node [^>]*>', xml):
    n = node.group(0)
    t = re.search(r' text="([^"]*)"', n); d = re.search(r' content-desc="([^"]*)"', n)
    if (t and t.group(1) == want) or (d and d.group(1) == want):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            break
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; log "tapped '$1' at $xy"; else log "could not find '$1' on screen"; fi
}

# Tap the first row title in the current list (the conversation list's @id/title).
tap_first_row() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - <<'PY'
import re
xml = open('/tmp/ui.xml', encoding='utf-8').read()
for node in re.finditer(r'<node [^>]*>', xml):
    n = node.group(0)
    if 'resource-id="com.moez.QKSMS:id/title"' in n:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
)
  [ -n "$xy" ] && adb shell input tap $xy && log "opened first row at $xy"
}

sms() { adb emu sms send "$1" "$2"; sleep 1; }

launch() {
  adb shell am force-stop $PKG
  adb shell am start -W -n $MAIN >/dev/null
  sleep 6
}

log "installing"
adb install -r -g app.apk
adb shell cmd role add-role-holder android.app.role.SMS $PKG 0 || true
adb shell appops set $PKG WRITE_SMS allow || true
adb shell pm grant $PKG android.permission.READ_CONTACTS || true
# No network: keeps the update checker's dialog out of the screenshots.
adb shell svc wifi disable || true
adb shell svc data disable || true
adb shell settings put system screen_off_timeout 1800000
adb shell cmd uimode night no

# First launch once so the app leaves the stopped state and can receive SMS.
launch
adb shell input keyevent KEYCODE_HOME
sleep 2

log "seeding messages"
sms 09123456789 "سلام! امشب ساعت ۸ کافه می‌بینمت؟ یه خبر خوب دارم که باید حضوری بگم"
sms 09351234567 "Hi, is the apartment on Vali-Asr still available? I can visit tomorrow."
# The emulator console reads one command per line; "\n" inside the text is its escape for a newline.
sms 98700717 "بانک ملت\nواریز: 2,500,000 ریال\nحساب: 0123\nمانده: 18,340,000 ریال\n1405/07/02-10:24"
sms 9820000 "بانک سامان\nبرداشت: 850,000 ریال\nاز حساب 4567\nمانده: 5,120,000 ریال"
sms 3000405060 "جشنواره پاییزه با ارسال رایگان همه سفارش‌ها تا پایان هفته. لغو11"
sms 1000800 "قرعه کشی بزرگ؛ با شارژ بالای ۵۰ هزار تومان شانس برنده شدن داشته باشید. لغو11"
sms 30001234 "اسنپ‌فود: کد تخفیف ۷۰ هزار تومانی FOOD70 فقط تا امشب فعال است. حداقل خرید ۲۰۰ هزار تومان"
sms 50002025 "دیجی‌کالا: ۲۵٪ تخفیف تا سقف ۱۵۰ هزار تومان با کد DK25NOW تا ساعت ۲۳:۵۹ امشب"
sms 100050 "کد ورود شما به اسنپ: 5319"
sms 300040 "کد تایید شما در دیجی‌کالا: 482913 این کد را در اختیار دیگران قرار ندهید"
sleep 4

# Notification shade while the app is in the background.
adb shell cmd statusbar expand-notifications
shot 00_notifications 3
adb shell cmd statusbar collapse

capture_mode() {
  local m=$1
  launch
  tap_text "All";       shot "${m}_01_all" 3
  tap_text "Personal";  shot "${m}_02_personal"
  tap_text "Banking";   shot "${m}_03_banking"
  tap_text "OTP";       shot "${m}_04_otp"
  tap_text "Discounts"; shot "${m}_05_discounts" 3
  tap_text "Spam";      shot "${m}_06_spam"
  tap_text "All";       sleep 2
  tap_first_row;        shot "${m}_07_conversation" 4
  adb shell input keyevent KEYCODE_BACK; sleep 2
  tap_text "Open navigation drawer"; shot "${m}_08_drawer"
  tap_text "Settings";  shot "${m}_09_settings" 3
  adb shell input swipe 540 1800 540 600 300; shot "${m}_10_settings_scrolled"
  adb shell input keyevent KEYCODE_BACK; sleep 1
}

capture_mode light
adb shell cmd uimode night yes
sleep 2
capture_mode dark
adb shell cmd uimode night no

ls -la "$OUT"
