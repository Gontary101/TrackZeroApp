#!/usr/bin/env bash
# TrackZero Phase 38 regression checks on the Pixel 7 emulator (1080 x 2400), debug build, Puy-de-Dôme map, the
# smoke test's routes (CARD_Y), tutorial done. Each part writes screenshots to <out dir>/<part>/ and ends with the
# crash buffer: look at the shots, they are the test.
#
#   tools/trackzero/regress.sh <out dir> [part ...]
#   parts: recreate (every screen, then the activity recreated there by a font size change and back)
#          lifecycle (ride starts of each kind, pause/resume, zero-distance end, restart, process death and restore)
#          gps (no fix at start, lost 30 s, location switched off, permission revoked mid-ride)
#          interrupt (another app on top, screen off/on, activity destroyed with the service alive, notification)
#          small (360 x 640 dp, largest font, imperial units)
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
out=${1:?out dir}
shift
parts=${*:-recreate lifecycle gps interrupt small}
gpx="$HOME/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx"
pkg=app.trackzero.debug
card_y=${CARD_Y:-1064}
handle_y=2229
dir=
n=0
shot() { n=$((n + 1)); adb exec-out screencap -p > "$dir/$(printf %02d $n)_$1.png"; }
tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep "${1:-2}"; }
feeder=
feed() { kill_feed; python3 "$here/emu_gps_play.py" "$gpx" "${1:-22}" "${2:-0}" "${3:-0}" > /dev/null 2>&1 & feeder=$!; }
kill_feed() { [ -n "$feeder" ] && kill "$feeder" 2> /dev/null; feeder=; }
cleanup() {
  kill_feed
  adb shell settings put system font_scale 1.0
  adb shell wm size reset; adb shell wm density reset
  adb shell cmd location set-location-enabled true
  adb shell pm grant "$pkg" android.permission.ACCESS_FINE_LOCATION 2> /dev/null
  adb shell settings put global always_finish_activities 0
}
trap cleanup EXIT
cold() { adb shell am force-stop "$pkg"; adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null; sleep 12; }
part() { dir="$out/$1"; mkdir -p "$dir"; n=0; adb logcat -b crash -c; echo "== $1"; }
crashes() { echo "  crashes:"; adb logcat -b crash -d | grep -E "FATAL|Exception|Abort" | head -5 || true; }
# Recreates the activity twice where it stands (font scale up, then back), a shot after each.
recreate() {
  shot "$1"
  adb shell settings put system font_scale 1.15; sleep 5; shot "$1_font"
  adb shell settings put system font_scale 1.0; sleep 5; shot "$1_back"
}
# Taps the view whose text is $1 (case-insensitive; allCaps styles), from uiautomator.
tap_text() {
  local b="" x y
  for _ in 1 2 3 4 5; do
    adb exec-out uiautomator dump /sdcard/tz_ui.xml > /dev/null 2>&1 || true
    b=$(adb shell cat /sdcard/tz_ui.xml 2> /dev/null | grep -io "text=\"$1\"[^>]*bounds=\"[^\"]*\"" \
        | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1 || true)
    [ -n "$b" ] && break
    sleep 2
  done
  [ -z "$b" ] && { echo "  (no \"$1\" on screen)"; return 1; }
  read -r x y < <(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2 \3 \4/' <<< "$b" \
    | awk '{print int(($1 + $3) / 2), int(($2 + $4) / 2)}')
  tap "$x" "$y" "${2:-2}"
}
# Kills the app's process as the system would (the ride service with it), then opens the app again.
die() {
  local pid
  pid=$(adb shell pidof "$pkg" | tr -d '\r')
  adb shell run-as "$pkg" kill -9 "$pid"; sleep 3
  adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null; sleep 14
}
start_ride() { tap 338 2200; tap 540 "$card_y" 4; feed 22 "${1:-0}" 0; tap 540 2160 6; }
end_ride() { adb shell input tap 540 "$handle_y"; sleep 2; adb shell input swipe 540 2151 540 2151 2600; sleep 5; tap 798 2214; kill_feed; }

first=$(grep -m1 -o '<trkpt[^>]*>' "$gpx")
adb emu geo fix "$(sed -E 's/.*lon="([-0-9.]+)".*/\1/' <<< "$first")" "$(sed -E 's/.*lat="([-0-9.]+)".*/\1/' <<< "$first")" 400 > /dev/null
adb shell input keyevent KEYCODE_WAKEUP

for p in $parts; do
  case $p in
  recreate)
    part recreate
    cold; recreate B1
    tap 740 2200; recreate B2; back 1; back
    tap 338 2200; recreate B3
    tap 540 "$card_y" 4; recreate B5; back
    tap 914 246; recreate N1; back
    tap 930 2200; recreate B7
    tap 540 452 3; recreate maps; back
    tap 540 624 3; recreate history; back
    tap 540 1176; recreate M1; back
    tap 540 1900; recreate about; back
    tap 540 1728 3; recreate T1; tap 540 2190; recreate T2; back
    tap 338 2200; tap 914 246; tap 540 1936 3; recreate P1; back; back
    tap 338 2200; tap 914 246; tap 540 2156 3; recreate L1; back
    start_ride 300; recreate R2
    adb shell input swipe 980 1300 120 1300 450; sleep 3; recreate R4
    adb shell input swipe 120 1300 980 1300 450; sleep 3
    tap 540 "$handle_y" 3; recreate R6
    tap 200 1086 6; recreate S1; back
    tap 540 "$handle_y" 3; tap 539 1086 4; recreate E1; back
    tap 540 "$handle_y" 3; tap 868 1086 3; recreate X1
    tap 540 2120 4; recreate X2; tap 798 2170 3
    adb shell input tap 540 "$handle_y"; sleep 2; adb shell input swipe 540 2151 540 2151 2600; sleep 5
    recreate R8; tap 798 2214; kill_feed
    crashes ;;
  starts)
    part starts
    cold
    tap 740 2200; adb shell input text puy; sleep 10; tap 540 795 4; tap 540 1926 15; shot place_preview
    feed 22 0 0; tap 540 2160 6; shot place_ride; end_ride
    tap 148 2200; tap 964 558 3; adb shell input swipe 760 700 760 700 900; sleep 8; tap 330 1250 10; tap 540 2190 6
    shot planned_preview; feed 22 0 0; tap 540 2160 6; shot planned_ride; end_ride; shot planned_end
    # The ride saved its plan as a route (newest first): delete it so CARD_Y stays on the test route.
    tap 338 2200 3; adb shell input swipe 540 760 540 760 900; sleep 2; tap_text "Delete" 2; tap_text "Delete" 2
    crashes ;;
  gps)
    part gps
    # Location off before the ride: the ride starts without a fix (NO GPS / waiting), then it comes.
    cold; adb shell cmd location set-location-enabled false; sleep 2
    tap 338 2200; tap 540 "$card_y" 4; tap 540 2160 8; shot start_no_location
    adb shell cmd location set-location-enabled true; feed 22 300 0; sleep 12; shot location_back
    # Lost for 30 s mid-ride, then back.
    adb shell cmd location set-location-enabled false; sleep 12; shot lost_12s; sleep 20; shot lost_32s
    adb shell cmd location set-location-enabled true; sleep 10; shot regained
    # Permission revoked mid-ride (the system kills the app), then reopened and granted again.
    adb shell pm revoke "$pkg" android.permission.ACCESS_FINE_LOCATION; sleep 3
    adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null; sleep 14; shot after_revoke
    adb shell pm grant "$pkg" android.permission.ACCESS_FINE_LOCATION; back; sleep 3; shot granted
    adb shell am force-stop "$pkg"; adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null; sleep 14
    shot reopened_granted; tap 540 2229 3; tap 540 1951 4; shot resumed
    end_ride; shot ended
    crashes ;;
  interrupt)
    part interrupt
    cold; start_ride 300
    # Another app in front for 20 s, then back.
    adb shell input keyevent KEYCODE_HOME; sleep 2
    adb shell am start -a android.settings.SETTINGS > /dev/null; sleep 20
    adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null; sleep 5; shot back_from_settings
    # Screen off 20 s and on.
    adb shell input keyevent KEYCODE_SLEEP; sleep 20; adb shell input keyevent KEYCODE_WAKEUP; sleep 4; shot screen_on
    # The activity destroyed while away ("Don't keep activities"), the service alive.
    adb shell settings put global always_finish_activities 1
    adb shell input keyevent KEYCODE_HOME; sleep 10
    adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null; sleep 8; shot activity_recreated
    adb shell settings put global always_finish_activities 0
    # Pause and resume from the notification.
    adb shell cmd statusbar expand-notifications; sleep 3
    # The ride's notification is in the collapsed "Silent" group: expand it for its action.
    adb exec-out uiautomator dump /sdcard/tz_ui.xml > /dev/null 2>&1
    exp=$(adb shell cat /sdcard/tz_ui.xml | grep -o 'content-desc="Expand"[^>]*bounds="[^"]*"' | head -1 \
          | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2 \3 \4/' \
          | awk '{print int(($1 + $3) / 2), int(($2 + $4) / 2)}')
    [ -n "$exp" ] && { adb shell input tap $exp; sleep 2; }
    shot notification
    tap_text "Pause" 3; shot notification_paused
    tap_text "Resume" 3; adb shell cmd statusbar collapse; sleep 3; shot notification_resumed
    end_ride; shot ended
    crashes ;;
  small)
    part small
    # A small phone (360 x 640 dp), the largest font, imperial units. Island x: map 130, routes 285, ride 472,
    # search 652, more 812; y 1480.
    adb shell wm size 945x1680; adb shell wm density 420; adb shell settings put system font_scale 1.3; sleep 3
    cold; shot B1; tap 812 1480 3; shot B7
    tap_text "Units" 2; shot B7_imperial
    tap 285 1480 3; shot B3; tap 470 1090 4; shot B5
    feed 22 300 0; tap_text "Start ride" 8; shot R2
    adb shell input swipe 860 900 100 900 450; sleep 3; shot R4
    adb shell input swipe 860 900 100 900 450; sleep 3; shot R5
    adb shell input swipe 100 900 860 900 450; sleep 2; adb shell input swipe 100 900 860 900 450; sleep 2
    adb shell input tap 472 1560; sleep 3; shot R6
    adb shell input swipe 472 1200 472 400 500; sleep 2; shot R6_scrolled
    adb exec-out uiautomator dump /sdcard/tz_ui.xml > /dev/null 2>&1
    endb=$(adb shell cat /sdcard/tz_ui.xml | grep -o 'resource-id="'$pkg':id/tz_ride_end"[^>]*bounds="[^"]*"' \
           | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2 \3 \4/' \
           | awk '{print int(($1 + $3) / 2), int(($2 + $4) / 2)}')
    echo "  end button at: $endb"
    [ -n "$endb" ] && { adb shell input swipe $endb $endb 2600; sleep 5; }
    shot R8; kill_feed
    tap 620 1580 2; tap 812 1480 3; tap_text "Units" 2
    adb shell wm size reset; adb shell wm density reset; adb shell settings put system font_scale 1.0
    crashes ;;
  lifecycle)
    part lifecycle
    # A free ride from the island, ended at once (zero distance), and a new one right after.
    cold; tap 540 2200 5; shot R1; end_ride; shot R8_zero; tap 540 2200 5; shot R1_again; end_ride
    # A place: search, the first result, NAVIGATE HERE, START RIDE.
    tap 740 2200; adb shell input text puy; sleep 10; tap 540 795 4; tap 540 1926 15; shot place_preview
    feed 22 0 0; tap 540 2160 6; shot place_ride; end_ride
    # A planned route: long-press, a point, PREVIEW, START RIDE.
    tap 148 2200; tap 964 558 3; adb shell input swipe 760 700 760 700 900; sleep 8; tap 330 1250 10; tap 540 2190 6
    shot planned_preview; feed 22 0 0; tap 540 2160 6; shot planned_ride; end_ride
    # Pause and resume; process death while paused; restored paused.
    start_ride 300; tap 540 "$handle_y" 3; tap 540 1951 3; shot paused; die; shot restored_paused
    tap 540 2229 3; tap 540 1951 3; shot resumed
    # The saver on, then process death: restored in the saver.
    tap 540 "$handle_y" 3; tap 868 1086 3; tap 540 2120 4; shot saver; die; shot restored_saver
    tap 798 2170 3; shot saver_exit
    # A stop added, then process death: restored with it.
    tap 540 "$handle_y" 3; tap 200 1086 8; tap 540 700 5; tap_text "Add stop" 6; shot stop_added; die
    shot restored_stop; tap 540 "$handle_y" 3; shot restored_stop_sheet; back
    end_ride; shot R8_after_restores
    crashes ;;
  esac
done
