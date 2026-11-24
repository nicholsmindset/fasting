"""Run only on a fresh, disposable Android 15 emulator with the staging APK."""
from datetime import datetime, timedelta
import importlib.util
from pathlib import Path
import re
import shlex
import sys
import time

import uiautomator2 as u2


# Both checks should exercise the same normal screening and Android permission flow.
spec = importlib.util.spec_from_file_location("upgrade_ui", Path(__file__).with_name("beta-upgrade-smoke.py"))
ui = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ui)
PACKAGE = ui.PACKAGE = "com.fastingshift.app.staging"
adb = ui.adb


def device_time():
    return datetime.fromisoformat(adb("shell", "date", "+%Y-%m-%dT%H:%M:%S%z"))


def set_picker(device, when):
    hour = device(resourceId="android:id/input_hour")
    if not hour.exists:
        device(resourceId="android:id/toggle_mode").click(timeout=8)
    minute = device(resourceId="android:id/input_minute")
    hour.click(timeout=8)
    hour.set_text(str(when.hour), timeout=8)
    minute.click(timeout=8)
    minute.set_text(f"{when.minute:02d}", timeout=8)
    actual = (hour.get_text(), minute.get_text())
    if not all(value.isdigit() for value in actual) or tuple(map(int, actual)) != (when.hour, when.minute):
        raise AssertionError(f"Time picker shows {actual} before confirming {when:%H:%M}")
    confirm = device(resourceId="android:id/button1")
    confirm.click(timeout=8)
    if not confirm.wait_gone(timeout=8):
        raise AssertionError(f"Time picker did not close after selecting {when:%H:%M}")


def wait_for_alarm(when, replaced=None):
    # Android 15's AlarmManager prints the PendingIntent action immediately above
    # the RTC trigger time. Read the real OS queue for the production receiver.
    pattern = re.compile(r"tag=[^\n]*com\.fastingshift\.app\.action\.REMINDER\s*\n\s*type=RTC_WAKEUP origWhen=(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})")
    expected = when.strftime("%Y-%m-%d %H:%M:%S")
    superseded = None if replaced is None else replaced.strftime("%Y-%m-%d %H:%M:%S")
    for _ in range(12):
        times = pattern.findall(adb("shell", "dumpsys", "alarm"))
        if expected in times and superseded not in times:
            return
        time.sleep(1)
    raise AssertionError(f"Expected {expected} in the OS alarm queue; found {times}")


def start_notification():
    # 'list' contains only currently posted notifications; ID 1003 is the app's start reminder.
    keys = adb("shell", "cmd", "notification", "list").splitlines()
    return next((key.strip() for key in keys if key.strip().startswith(f"0|{PACKAGE}|1003|")), None)


def locked_and_asleep():
    power = adb("shell", "dumpsys", "power")
    trust = adb("shell", "dumpsys", "trust")
    return (re.search(r"\bmWakefulness=(Asleep|Dozing)\b", power) is not None
            and re.search(r"\bdeviceLocked=(1|true)\b", trust) is not None)


def check(screen):
    screen.onboard("tab_timer", allow_notifications=True)
    if not ui.notification_permission_granted():
        raise AssertionError("The user-facing notification permission flow did not grant permission")
    screen.open_settings()
    label = screen.strings["week_title"]
    screen.find_node(screen.device(textContains=label, packageName=PACKAGE), label, scroll=True).click()
    create_pattern = re.escape(screen.strings["week_create"]).replace(re.escape("%1$s"), r"\d+:\d+")
    screen.find_node(screen.device(textMatches=create_pattern, packageName=PACKAGE), "Create routine", scroll=True).click()
    repeat = screen.strings["week_repeat"]
    screen.find_node(screen.device(textContains=repeat, packageName=PACKAGE), repeat, scroll=True).click()
    screen.click("week_every_day")
    start_label = screen.strings["schedules_start_time"]
    start_row = screen.device(textContains=start_label, packageName=PACKAGE)
    screen.find_node(start_row, start_label, scroll=True).click()
    original = device_time().replace(second=0) + timedelta(minutes=3)
    # Date overrides move a time within one civil date. Keep both times on the same date.
    if original.hour == 23 and original.minute == 59:
        original += timedelta(minutes=1)
    moved = original + timedelta(minutes=1)
    set_picker(screen.device, original)
    # Read back the draft before saving. Otherwise an unchanged native picker can
    # look like an AlarmManager delivery failure several screens later.
    draft_time = screen.device(text=original.strftime("%H:%M"), packageName=PACKAGE)
    if not draft_time.wait(timeout=3):
        print(f"Routine editor did not display {original:%H:%M}; reopening its picker once.", flush=True)
        screen.find_node(start_row, start_label, scroll=True).click()
        set_picker(screen.device, original)
        if not draft_time.wait(timeout=3):
            raise AssertionError(f"Routine editor did not accept {original:%H:%M}")
    screen.click("week_save", scroll=True)
    screen.find("week_routines", scroll=True)
    wait_for_alarm(original)

    day = screen.device(resourceId=f"week-day-{original.date()}", packageName=PACKAGE)
    screen.find_node(day, "Scheduled day", scroll=True).click()
    screen.click("week_change_time")
    set_picker(screen.device, moved)
    screen.find("week_changed", scroll=True)
    wait_for_alarm(moved, replaced=original)
    # The following day must still inherit the routine. The live alarm check below
    # also verifies its exact time after Android delivers today's moved reminder.
    tomorrow = (original + timedelta(days=1)).date()
    next_row = screen.device(resourceId=f"week-day-{tomorrow}", packageName=PACKAGE)
    screen.find_node(next_row, "Following planned day", scroll=True).click()
    screen.find("week_date_hint")
    if screen.device(text=screen.strings["week_restore_day"], packageName=PACKAGE).exists:
        raise AssertionError("Moving today's start unexpectedly added an override to the following day")

    # Setting a PIN can immediately show the keyguard. Finish UI checks first.
    adb("shell", "locksettings", "set-pin", "493821")
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
    for _ in range(12):
        if locked_and_asleep():
            break
        time.sleep(1)
    if not locked_and_asleep() or device_time() >= original:
        raise AssertionError("The emulator must be PIN-locked with its display asleep before the original start")
    print(f"Watching the locked emulator: regular time {original.isoformat()}, moved time {moved.isoformat()}", flush=True)
    saw_original_pass = False
    deadline = time.monotonic() + (moved - device_time()).total_seconds() + 60
    while time.monotonic() < deadline:
        key = start_notification()
        now = device_time()
        if not locked_and_asleep():
            raise AssertionError("Emulator unlocked or woke during delivery verification")
        if key:
            if not saw_original_pass or now < moved or now > moved + timedelta(seconds=60):
                raise AssertionError(f"Start notification arrived outside the moved time: {now.isoformat()}")
            record = adb("shell", "cmd", "notification", "get", shlex.quote(key))
            for expected in (screen.strings["notif_scheduled_start_title"], screen.strings["notif_start_body"]):
                if expected not in record:
                    raise AssertionError(f"Posted reminder lacks expected text: {expected}")
            wait_for_alarm(original + timedelta(days=1))
            print(f"Verified the following day re-armed at its original time. No start reminder at the replaced time; the scheduled start posted at {now.isoformat()} with the display asleep and PIN locked.")
            return
        if original <= now < moved:
            saw_original_pass = True
        time.sleep(2)
    raise AssertionError("No scheduled start notification within 60 seconds of the moved time")


def main():
    emulator = any(adb("shell", "getprop", key) == "1" for key in ("ro.boot.qemu", "ro.kernel.qemu"))
    if not emulator or adb("shell", "getprop", "ro.build.version.sdk") != "35" or adb("shell", "pm", "list", "packages", PACKAGE):
        raise RuntimeError("Use a clean disposable Android 15 emulator; this test sets a temporary device PIN")
    adb("shell", "wm", "size", "1080x2160")
    adb("shell", "wm", "density", "240")
    adb("shell", "settings", "put", "system", "time_12_24", "24")
    adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "0")
    adb("shell", "settings", "put", "secure", "lock_screen_lock_after_timeout", "0")
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    adb("install", sys.argv[1])
    # Exercise Android's exact-alarm-permitted delivery path only on this test device.
    adb("shell", "appops", "set", PACKAGE, "SCHEDULE_EXACT_ALARM", "allow")
    permission = adb("shell", "appops", "get", PACKAGE, "SCHEDULE_EXACT_ALARM")
    if not re.search(r"SCHEDULE_EXACT_ALARM:\s*allow\b", permission):
        raise AssertionError(f"Exact alarms must be allowed on the disposable emulator: {permission}")
    device = u2.connect()
    device.app_start(PACKAGE, activity=ui.ACTIVITY)
    try:
        check(ui.Screen(device, Path(".")))
    except Exception:
        # Preserve enough diagnostics in the job log to distinguish navigation,
        # device-lock configuration, alarm scheduling and notification delivery.
        print(device.dump_hierarchy()[:12000], flush=True)
        for service, pattern in (("power", "mWakefulness"), ("trust", "deviceLocked"), ("alarm", PACKAGE)):
            lines = adb("shell", "dumpsys", service).splitlines()
            print("\n".join(line for line in lines if pattern in line)[:2400], flush=True)
        print(adb("shell", "cmd", "notification", "list"), flush=True)
        raise


if __name__ == "__main__":
    main()
