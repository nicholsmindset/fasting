"""Exercise a staging APK on an offline Android 8 emulator."""
import importlib.util
from pathlib import Path
import subprocess
import sys
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

def plan_window(screen):
    labels = []
    for tag in ("first-plan-start", "first-plan-finish"):
        node = screen.find_node(device(resourceId=tag, packageName=package), tag, scroll=True)
        label = node.get_text()
        if not label:
            raise AssertionError(f"Missing displayed time on {tag}")
        labels.append(label)
    return tuple(labels)

def run():
    print("Android image:", ui.adb("shell", "getprop", "ro.build.fingerprint"), flush=True)
    ui.adb("install", sys.argv[1])
    screen = ui.Screen(device, Path.cwd())
    device.app_start(package, activity=ui.ACTIVITY, wait=True)
    screen.find("onb_get_started")
    capture("01-onboarding")
    screen.reach_protocol()
    screen.advance_onboarding("onb_protocol_title", "first_plan_title")
    screen.click("first_plan_pick_clock", scroll=True)
    device(resourceId="android:id/button1", packageName=package).click(timeout=10)
    preview = plan_window(screen)
    device.app_stop(package)
    device.app_start(package, activity=ui.ACTIVITY, wait=True)
    screen.find("first_plan_title", scroll=True)
    if plan_window(screen) != preview:
        raise AssertionError("The unsaved plan draft changed after a cold start")
    screen.click("first_plan_schedule", scroll=True)
    screen.finish_free_onboarding("tab_timer", allow_notifications=False)
    screen.find("first_plan_home_title", scroll=True)
    device.app_stop(package)
    device.app_start(package, activity=ui.ACTIVITY, wait=True)
    screen.find("first_plan_home_title", scroll=True)
    if plan_window(screen) != preview:
        raise AssertionError("The saved start or finish changed after a cold start")
    capture("02-saved-plan")
    screen.click("first_plan_start", scroll=True)
    screen.find("timer_end", scroll=True)
    capture("03-active-timer")
    device.app_stop(package)
    device.app_start(package, activity=ui.ACTIVITY, wait=True)
    screen.click("timer_end", scroll=True)
    screen.close_saved_fast()
    screen.find("timer_start", scroll=True)
    device.app_stop(package)
    device.app_start(package, activity=ui.ACTIVITY, wait=True)
    screen.find("timer_start", scroll=True)
    device(resourceId="tab_history", packageName=package).click(timeout=15)
    screen.find_node(device(resourceId="history-list", packageName=package), "History")
    day = ui.adb("shell", "date", "+%F")
    cell = device(resourceId=f"history-day-{day}", packageName=package)
    screen.find_node(cell, day, scroll=True).click(offset=(0.5, 0.2))
    screen.find("history_day_recorded")
    capture("04-saved-fast")
    print("PASS: plan for later, verify saved times on cold start, start explicitly, resume, end and save, cold restart, saved fast in History.", flush=True)

# Keep emulator networking away from outside services while retaining ADB and the
# local automation channel. Native firewall commands also work on Android 8.
assert ui.adb("shell", "getprop", "ro.build.version.sdk") == "26"
assert any(ui.adb("shell", "getprop", prop) == "1" for prop in ("ro.boot.qemu", "ro.kernel.qemu"))
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
