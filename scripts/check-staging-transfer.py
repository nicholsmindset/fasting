"""Exercise the public staging APK backup path on a disposable, clean Android emulator."""
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import time
from xml.etree import ElementTree as ET

import uiautomator2 as u2

spec = importlib.util.spec_from_file_location("upgrade", Path("scripts/beta-upgrade-smoke.py"))
ui = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ui)
package = ui.PACKAGE = "com.fastingshift.app.staging"
documents = ".*documentsui"
old_name = "FastingShift-test-migration-before.json"
new_name = "FastingShift-test-migration-after.json"

def downloads(device):
    if device(resourceIdMatches=r".*:id/breadcrumb_text", text="Downloads").exists:
        return
    menu = device(packageNameMatches=documents, descriptionMatches="(?i)Show (roots|navigation drawer)")
    menu.click(timeout=8)
    device(packageNameMatches=documents, text="Downloads").click(timeout=8)

def save_copy(screen, name):
    device = screen.device
    screen.open_settings()
    if screen.settings_overview().wait(timeout=2):
        screen.open_settings_category("settings_open_privacy")
    screen.click("settings_backup", scroll=True)
    field = device(className="android.widget.EditText", packageNameMatches=documents)
    assert field.wait(timeout=15), "Missing Android document picker"
    downloads(device)
    field.set_text(name, timeout=10)
    save = device(packageNameMatches=documents, textMatches="(?i)save")
    for attempt in range(3):
        save.click(timeout=10)
        if device(packageName=package).wait(timeout=3):
            break
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        result = subprocess.run(["adb", "exec-out", "cat", "/sdcard/Download/"+name], capture_output=True, text=True)
        if result.returncode == 0:
            try:
                return json.loads(result.stdout)
            except json.JSONDecodeError:
                pass
        time.sleep(0.5)
    raise AssertionError("Full backup did not reach Downloads: "+name)

def main(d):
    assert ui.adb("shell", "getprop", "ro.build.version.sdk") == "35"
    assert ui.adb("shell", "getprop", "ro.kernel.qemu") == "1" or ui.adb("shell", "getprop", "ro.boot.qemu") == "1"
    assert not ui.adb("shell", "pm", "list", "packages", package)
    ui.adb("shell", "wm", "size", "1080x2160")
    ui.adb("shell", "wm", "density", "240")
    ui.adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "0")
    ui.adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    ui.adb("shell", "wm", "dismiss-keyguard")
    old_apk, = list(Path("previous").rglob("*.apk"))
    new_apk, = list(Path("redesign").rglob("*.apk"))
    ui.adb("install", str(old_apk))
    d.app_start(package, activity=ui.ACTIVITY, wait=True)
    old = ui.Screen(d, Path("previous-code"))
    old.create_fasts()
    original = save_copy(old, old_name)
    fingerprint = ui.fingerprint(original["fasts"])
    print("Actual prior staging APK exported one ended and one active fast to Downloads.")
    rejected = subprocess.run(["adb", "install", "-r", str(new_apk)], capture_output=True, text=True)
    assert "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in rejected.stdout+rejected.stderr, rejected.stdout+rejected.stderr
    print("Android refused the direct APK update because its signing certificate differs.")
    assert ui.adb("uninstall", package) == "Success"
    assert ui.adb("shell", "ls", "/sdcard/Download/"+old_name)
    ui.adb("install", str(new_apk))
    new = ui.Screen(d, Path("."))
    d.app_start(package, activity=ui.ACTIVITY, wait=True)
    new.onboard("tab_timer", allow_notifications=False)
    new.open_settings()
    new.open_settings_category("settings_open_privacy")
    new.click("settings_restore", scroll=True)
    selected = d(packageNameMatches=documents, text=old_name)
    if not selected.wait(timeout=3):
        downloads(d)
    selected.click(timeout=15)
    new.find("restore_confirm_title")
    new.click("restore_confirm_action")
    new.find("settings_restored")
    d.app_stop(package)
    d.app_start(package, activity=ui.ACTIVITY, wait=True)
    new.find("timer_end", scroll=True)
    imported = save_copy(new, new_name)
    assert ui.fingerprint(imported["fasts"]) == fingerprint, "Stored record ID/start/target/end/protocol/status differ."
    print("After a cold launch, the redesign displayed its active timer. Its new full backup matches both original fasts exactly, including the active start and original target. The app requested Replace and restore after the normal Android document picker.")

d = u2.connect()
try:
    main(d)
except Exception:
    for n in ET.fromstring(d.dump_hierarchy()).iter("node"):
        a=n.attrib
        text=a.get("text") or a.get("content-desc")
        if text:
            print(a.get("package"),text,a.get("resource-id"),a.get("bounds"),flush=True)
    raise
