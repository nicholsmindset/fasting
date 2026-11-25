"""Capture the actual posted fasting notification and the system shade on a disposable emulator."""
import importlib.util
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time

import uiautomator2

spec = importlib.util.spec_from_file_location("upgrade_ui", Path(__file__).with_name("beta-upgrade-smoke.py"))
ui = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ui)
package = ui.PACKAGE = "com.fastingshift.app.staging"
adb = ui.adb

def ongoing():
    return next((line for line in adb("shell", "cmd", "notification", "list").splitlines()
                 if f"|{package}|1004|" in line), None)

def main():
    sdk = int(adb("shell", "getprop", "ro.build.version.sdk"))
    emulator = any(adb("shell", "getprop", prop) == "1" for prop in ("ro.boot.qemu", "ro.kernel.qemu"))
    assert sdk in (35, 36) and emulator
    adb("install", sys.argv[1])
    device = uiautomator2.connect()
    device.app_start(package, activity=ui.ACTIVITY, wait=True)
    screen = ui.Screen(device, Path.cwd())
    screen.onboard("tab_timer", allow_notifications=True)
    screen.open_settings()
    screen.open_settings_category("settings_open_reminders")
    label = screen.strings["settings_ongoing"]
    screen.find_node(device(textContains=label, packageName=package), label, scroll=True).click()
    screen.back_to_settings()
    screen.click("tab_timer")
    screen.click("timer_start", scroll=True)
    screen.find("timer_end", scroll=True)
    for _ in range(15):
        if ongoing():
            break
        time.sleep(1)
    assert ongoing(), "The opted-in active fast has no ongoing notification"
    dump = adb("shell", "cmd", "notification", "get", shlex.quote(ongoing()))
    style = "android.app.Notification$ProgressStyle" in dump
    label = "progress" if style else "plain"
    directory = Path("live-notification-proof")
    directory.mkdir(exist_ok=True)
    flag_bits = re.search(r"flags=0x([0-9a-f]+)", dump, re.IGNORECASE)
    names = re.search(r"^\s*flags=(.*)$", dump, re.MULTILINE)
    promoted = bool(flag_bits and int(flag_bits.group(1), 16) & 0x40000) or bool(names and "PROMOTED_ONGOING" in names.group(1))
    fingerprint = adb("shell", "getprop", "ro.build.fingerprint")
    note = (f"{fingerprint}\nAPI {sdk}, posted ongoing slot 1004, actual notification style: {label}. "
            f"System promoted flag present: {promoted}.")
    (directory / "timer-record.txt").write_text(dump)
    print(note, flush=True)
    (directory / "observed.txt").write_text(note + "\n")
    assert style == (sdk >= 36), f"Unexpected active timer notification on API {sdk}: {label}"
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1)
    with (directory / f"api-{sdk}-{label}-status.png").open("wb") as image:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=image, check=True)
    adb("shell", "cmd", "statusbar", "expand-notifications")
    time.sleep(1)
    with (directory / f"api-{sdk}-{label}-shade.png").open("wb") as image:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=image, check=True)
    adb("shell", "cmd", "statusbar", "collapse")
    device.app_start(package, activity=ui.ACTIVITY, wait=True)
    screen.click("timer_end", scroll=True)
    for _ in range(15):
        if not ongoing():
            break
        time.sleep(1)
    assert not ongoing(), "Ended fast still has an ongoing notification"

if __name__ == "__main__":
    main()
