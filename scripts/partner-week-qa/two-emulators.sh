#!/usr/bin/env bash
# The GitHub action provides the host emulator and API 35 image. Launch a guest
# with its own AVD, disk, ADB serial, network switches and system clock zone.
set -euo pipefail
export PARTNER_HOST_SERIAL=emulator-5554
export PARTNER_GUEST_SERIAL=emulator-5556
image='system-images;android-35;google_apis;x86_64'
printf 'no\n' | avdmanager create avd --force --name partner_guest --package "$image" --device pixel_2
"$ANDROID_HOME/emulator/emulator" -avd partner_guest -port 5556 -memory 1536 -cores 2 \
    -no-window -no-snapshot -noaudio -no-boot-anim -gpu swiftshader_indirect -camera-back none \
    -timezone Etc/GMT+12 >"$RUNNER_TEMP/partner-guest-boot.log" 2>&1 &
guest_pid=$!
trap 'adb -s "$PARTNER_GUEST_SERIAL" emu kill >/dev/null 2>&1 || true; kill "$guest_pid" 2>/dev/null || true' EXIT
for attempt in {1..150}; do
    if ! kill -0 "$guest_pid" 2>/dev/null; then
        tail -50 "$RUNNER_TEMP/partner-guest-boot.log" >&2
        exit 1
    fi
    if [[ "$(adb -s "$PARTNER_GUEST_SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; then
        break
    fi
    sleep 2
done
[[ "$(adb -s "$PARTNER_GUEST_SERIAL" shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]
adb -s "$PARTNER_GUEST_SERIAL" shell input keyevent KEYCODE_WAKEUP
adb -s "$PARTNER_GUEST_SERIAL" shell wm dismiss-keyguard
for setting in window_animation_scale transition_animation_scale animator_duration_scale; do
    adb -s "$PARTNER_GUEST_SERIAL" shell settings put global "$setting" 0
done
python -u scripts/partner-week-journey.py
