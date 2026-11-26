"""Check the distributed staging APK on an isolated emulator at the supported OS floor."""
import importlib.util
from pathlib import Path
import subprocess
import time
import uiautomator2

helper = Path(__file__).with_name("beta-upgrade-smoke.py")
spec = importlib.util.spec_from_file_location("upgrade_ui", helper)
ui = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ui)
package = ui.PACKAGE = "com.fastingshift.app.staging"
proof = Path("api26-proof")
proof.mkdir(exist_ok=True)

def capture(name):
    (proof / f"{name}.xml").write_text(device.dump_hierarchy())
    with (proof / f"{name}.png").open("wb") as target:
        subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=target, check=True)


def save_summary(screen):
    """Scroll the sheet and tap Save only after the label's visible bounds settle."""
    label = screen.strings["save"]
    panel = device(packageName=package, scrollable=True)
    previous = None
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        if "mInputShown=true" in ui.adb("shell", "dumpsys", "input_method"):
            device.press("back")
            previous = None
            continue
        point = ui.visible_label_center(device.dump_hierarchy(), label)
        if point is None:
            if panel.exists:
                panel.scroll.forward(steps=45)
        elif point == previous:
            device.click(*point)
            if device(packageName=package, text=screen.strings["timer_start"]).wait(timeout=3):
                return
        previous = point
        time.sleep(0.35)
    raise AssertionError("The summary Save action did not return to the idle timer")

def run():
    assert ui.adb("shell", "getprop", "ro.build.version.sdk") == "26"
    assert any(ui.adb("shell", "getprop", prop) == "1" for prop in ("ro.boot.qemu", "ro.kernel.qemu"))
    print("Android image:", ui.adb("shell", "getprop", "ro.build.fingerprint"), flush=True)
    ui.adb("install", "distributed-staging.apk")
    screen = ui.Screen(device, Path.cwd())
    device.app_start(package, activity=ui.ACTIVITY, wait=True)
    screen.find("onb_get_started")
    capture("01-onboarding")
    screen.onboard("tab_timer", allow_notifications=False)
    screen.click("timer_start", scroll=True)
    screen.find("timer_end", scroll=True)
    capture("02-active-timer")
    device.app_stop(package)
    device.app_start(package, activity=ui.ACTIVITY, wait=True)
    screen.click("timer_end", scroll=True)
    screen.find("summary_title_early")
    save_summary(screen)
    device.app_stop(package)
    device.app_start(package, activity=ui.ACTIVITY, wait=True)
    screen.find("timer_start", scroll=True)
    device(resourceId="tab_history", packageName=package).click(timeout=15)
    screen.find_node(device(resourceId="history-list", packageName=package), "History")
    day = ui.adb("shell", "date", "+%F")
    cell = device(resourceId=f"history-day-{day}", packageName=package)
    screen.find_node(cell, day, scroll=True).click(offset=(0.5, 0.2))
    screen.find("history_day_recorded")
    capture("03-saved-fast")
    print("PASS: fresh onboarding, start, force-stop and resume, end and save, cold restart, saved fast in History.", flush=True)

# Keep emulator networking away from outside services while retaining ADB and the
# local automation channel. Native firewall commands also work on Android 8.
ui.adb("root")
ui.adb("wait-for-device")
for table in ("iptables", "ip6tables"):
    ui.adb("shell", table, "-I", "OUTPUT", "!", "-o", "lo", "-j", "REJECT")
    ui.adb("shell", table, "-C", "OUTPUT", "!", "-o", "lo", "-j", "REJECT")
device = uiautomator2.connect()
try:
    run()
except Exception:
    with (proof / "app-crash.txt").open("w") as target:
        subprocess.run(["adb", "logcat", "-b", "crash", "-d"], stdout=target, check=False)
    with (proof / "failure-logcat.txt").open("w") as target:
        subprocess.run(["adb", "logcat", "-d", "-s", "ActivityManager", "AndroidRuntime", "DEBUG"], stdout=target, check=False)
    capture("failure")
    raise
