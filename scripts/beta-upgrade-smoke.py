"""Verify an in-place beta upgrade through the launcher, timer and user-facing JSON backup."""
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

import uiautomator2 as u2


PACKAGE = "com.fastingshift.app.beta"
ACTIVITY = "com.fastingshift.app.ui.MainActivity"
DIAGNOSTICS = Path("upgrade-diagnostics")


def adb(*arguments):
    return subprocess.check_output(["adb", *arguments], text=True).strip()


def installed_version():
    info = adb("shell", "dumpsys", "package", PACKAGE)
    return int(re.search(r"\bversionCode=(\d+)\b", info).group(1))


class Screen:
    def __init__(self, device, source):
        self.device = device
        self.strings = {}
        for path in (source / "app/src/main/res/values").glob("*.xml"):
            for tag in ET.parse(path).getroot().findall("string"):
                self.strings[tag.attrib["name"]] = "".join(tag.itertext()).replace("\\'", "'")

    def find(self, key, scroll=False):
        label = self.strings[key]
        node = self.device(text=label, packageName=PACKAGE)
        if node.wait(timeout=2 if scroll else 20):
            return node
        if scroll:
            for direction in ("up", "down"):
                for _ in range(10):
                    self.device.swipe_ext(direction, scale=0.65)
                    if node.wait(timeout=0.35):
                        return node
        raise AssertionError(f"Missing {key} ({label})")

    def click(self, key, scroll=False):
        self.find(key, scroll).click()

    def create_fasts(self):
        self.click("onb_get_started")
        self.click("onb_skip")
        self.click("goal_consistency")
        self.click("onb_next")
        self.find("onb_screen_title")
        fields = self.device(className="android.widget.EditText", packageName=PACKAGE)
        if fields.count != 3:
            raise AssertionError(f"Expected three screening inputs, found {fields.count}")
        for index, value in enumerate(("40", "175", "70")):
            fields[index].set_text(value)
        self.click("onb_next")
        self.find("onb_ok_title")
        self.click("onb_next")
        self.find("onb_protocol_title")
        self.click("onb_next")
        self.click("onb_not_now")
        self.click("onb_offer_free")
        self.click("timer_start", scroll=True)
        self.click("timer_end", scroll=True)
        self.find("summary_title_early")
        self.click("save", scroll=True)
        self.click("timer_start", scroll=True)
        self.find("timer_end", scroll=True)

    def export_fasts(self, name):
        self.click("tab_settings")
        self.click("settings_backup", scroll=True)
        # The picker chooses a local folder on a fresh emulator. Locate the unique file
        # on shared storage, regardless of whether this image defaults to Documents or Downloads.
        field = self.device(className="android.widget.EditText", packageNameMatches=".*documentsui")
        if not field.wait(timeout=15):
            raise AssertionError("The Android document picker did not open")
        field.set_text(name)
        self.device(packageNameMatches=".*documentsui", textMatches="(?i)save").click(timeout=10)
        if not self.device(packageName=PACKAGE).wait(timeout=15):
            raise AssertionError("The picker did not return to Fasting Shift")
        for _ in range(40):
            # The Android shell cannot enumerate some scoped Android/data subdirectories.
            lookup = self.device.shell(["find", "/sdcard/", "-type", "f", "-name", name]).output
            matches = [line for line in lookup.splitlines() if line.endswith("/" + name)]
            if matches:
                raw = adb("exec-out", "cat", matches[0])
                try:
                    return json.loads(raw)["fasts"]
                except json.JSONDecodeError:
                    pass  # The provider created the document before the app finished writing.
            time.sleep(0.5)
        raise AssertionError(f"The app did not write {name}")


def fingerprint(fasts):
    keys = ("id", "start_at", "target_end_at", "ended_at", "protocol_id", "status")
    if len(fasts) != 2 or {f["status"] for f in fasts} != {"ENDED_EARLY", "ACTIVE"}:
        raise AssertionError(f"Expected one ended fast and one active fast, found {fasts}")
    for fast in fasts:
        if (fast["ended_at"] is None) != (fast["status"] == "ACTIVE"):
            raise AssertionError(f"Inconsistent end time for fast {fast['id']}")
    return sorted(tuple(f[k] for k in keys) for f in fasts)


def main(device, old_apk, new_apk):
    adb("shell", "wm", "size", "1080x2160")
    adb("shell", "wm", "density", "240")
    adb("shell", "settings", "put", "secure", "show_ime_with_hard_keyboard", "0")
    adb("install", old_apk)
    assert installed_version() == 100, "Unexpected baseline version"
    device.app_start(PACKAGE, activity=ACTIVITY)
    base = Screen(device, Path("previous"))
    base.create_fasts()
    before = fingerprint(base.export_fasts("upgrade-before.json"))
    device.app_stop(PACKAGE)

    # Package manager enforces package identity and signing certificate continuity.
    adb("install", "-r", new_apk)
    assert installed_version() == 101, "Replacement version was not installed"
    device.app_start(PACKAGE, activity=ACTIVITY)
    candidate = Screen(device, Path("."))
    candidate.find("timer_end", scroll=True)
    after = fingerprint(candidate.export_fasts("upgrade-after.json"))
    if before != after:
        raise AssertionError(f"Fast records changed on upgrade: {before} -> {after}")
    print("Verified both fasts, including the running timer's original start and target.")


if __name__ == "__main__":
    device = u2.connect()
    try:
        main(device, *sys.argv[1:])
    except Exception:
        DIAGNOSTICS.mkdir(exist_ok=True)
        (DIAGNOSTICS / "logcat.txt").write_text(adb("logcat", "-d", "-t", "1200"))
        (DIAGNOSTICS / "screen.xml").write_text(device.dump_hierarchy())
        device.screenshot(str(DIAGNOSTICS / "screen.png"))
        raise
