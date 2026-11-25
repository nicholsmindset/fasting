"""Capture the actual posted fasting notification and the system shade on a disposable emulator."""
import importlib.util
from pathlib import Path
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
    assert sdk in (35, 36) and adb("shell", "getprop", "ro.kernel.qemu") == "1"
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
    dump = adb("shell", "dumpsys", "notification", "--noredact")
    style = "android.app.Notification$ProgressStyle" in dump
    label = "progress" if style else "plain"
    directory = Path("live-notification-proof")
    directory.mkdir(exist_ok=True)
    note = (f"API {sdk}, posted ongoing slot 1004, actual notification style: {label}. "
            "System promotion and OEM status-bar placement are device controlled.")
    print(note, flush=True)
    (directory / "observed.txt").write_text(note + "\n")
    if sdk == 35:
        assert not style, "Android 15 must retain its plain notification"
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
