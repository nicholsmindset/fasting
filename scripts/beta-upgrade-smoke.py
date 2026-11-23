"""Verify a signed upgrade, fresh invite onboarding, and persisted one-date planner changes."""
from base64 import urlsafe_b64encode
from datetime import date, timedelta
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
        return self.find_node(node, label, scroll)

    def find_node(self, node, label, scroll=False):
        if node.wait(timeout=2 if scroll else 20):
            return node
        if scroll:
            for direction in ("up", "down"):
                for _ in range(10):
                    self.device.swipe_ext(direction, scale=0.65)
                    if node.wait(timeout=0.35):
                        return node
        raise AssertionError(f"Missing {label}")

    def click(self, key, scroll=False):
        self.find(key, scroll).click()

    def finish_free_onboarding(self, destination):
        # Ads and billing services are not needed. Older baselines display an offer before
        # entering the app; current free builds go directly to the timer or pending invite.
        offer = self.device(text=self.strings["onb_offer_free"], packageName=PACKAGE)
        target = self.device(text=self.strings[destination], packageName=PACKAGE)
        permission = self.device(resourceIdMatches=".*permissioncontroller:id/permission_allow_button")
        deadline = time.monotonic() + 25
        while time.monotonic() < deadline:
            if target.exists:
                return
            if permission.exists:
                permission.click()
            elif offer.exists:
                offer.click()
            time.sleep(0.25)
        raise AssertionError(f"Onboarding did not reach {destination}")

    def onboard(self, destination, allow_notifications):
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
        self.click("onb_notif_allow" if allow_notifications else "onb_not_now")
        self.finish_free_onboarding(destination)

    def create_fasts(self):
        self.onboard("tab_timer", allow_notifications=True)
        self.click("timer_start", scroll=True)
        self.click("timer_end", scroll=True)
        self.find("summary_title_early")
        self.click("save", scroll=True)
        self.click("timer_start", scroll=True)
        self.find("timer_end", scroll=True)

    def export_backup(self, name):
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
                    return json.loads(raw)
                except json.JSONDecodeError:
                    pass  # The provider created the document before the app finished writing.
            time.sleep(0.5)
        raise AssertionError(f"The app did not write {name}")


    def verify_fresh_invite(self):
        # Only run after all upgrade assertions. Reset this dedicated emulator app's data so
        # screening and onboarding actually run while Android is handing over the new invite.
        if adb("shell", "pm", "clear", PACKAGE) != "Success":
            raise AssertionError("Could not reset the test beta package")
        name = "Seven days with friends"
        start = (date.today() + timedelta(days=1)).strftime("%Y%m%d")
        payload = f"1|D|window_16_8|{start}|7|{name}"
        code = urlsafe_b64encode(payload.encode()).decode().rstrip("=")
        url = "https://fastingshift.com/c/" + code

        def open_invite():
            # Same VIEW data and package as the pilot site's Android intent link. Giving a
            # package (not a component) still exercises the manifest's BROWSABLE resolution.
            result = adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW",
                         "-c", "android.intent.category.BROWSABLE", "-d", url, "-p", PACKAGE)
            if "Error" in result or "Exception" in result:
                raise AssertionError(f"Invite intent did not resolve: {result}")

        def check_title():
            if not self.device(text=name, packageName=PACKAGE).wait(timeout=20):
                raise AssertionError(f"Missing challenge title: {name}")

        open_invite()
        self.onboard("challenge_join_title", allow_notifications=False)
        check_title()
        self.click("challenge_join", scroll=True)
        self.find("challenge_invite", scroll=True)  # Only the detail screen offers this action.
        check_title()

        open_invite()
        self.find("challenge_join_title")
        check_title()
        self.find("challenge_join_already", scroll=True)
        if self.device(text=self.strings["challenge_join"], packageName=PACKAGE).exists:
            raise AssertionError("A joined invite offered to join a second time")
        self.click("challenge_open", scroll=True)
        self.find("challenge_invite", scroll=True)
        check_title()

        # Use the same user-facing backup that verified the upgrade, so even a duplicate
        # outside the viewport would fail. A normal launch must also consume the old invite.
        self.device.app_stop(PACKAGE)
        self.device.app_start(PACKAGE, activity=ACTIVITY)
        self.find("tab_timer")
        joined = self.export_backup("invite-after.json")["challenges"]
        if len(joined) != 1 or joined[0]["name"] != name:
            raise AssertionError(f"Expected one joined challenge named {name!r}, got {joined}")
        print("Verified fresh onboarding, explicit Join, and reopening the same local challenge.")


    def verify_week_planner(self):
        # The fresh invite test leaves the screened user in Settings. Reopen from launcher
        # to test the same navigation that a returning tester uses.
        def open_week():
            self.device.app_stop(PACKAGE)
            self.device.app_start(PACKAGE, activity=ACTIVITY)
            self.click("tab_settings")
            self.click("week_title", scroll=True)

        def open_date(day):
            # Android's English localized abbreviation and punctuation can vary by API.
            # Match the date label without relying on a time or the position of a row.
            pattern = rf"(?s).*\b{day:%a}\b.*\b{day:%b}\.?\s+{day.day}\b.*"
            row = self.device(textMatches=pattern, packageName=PACKAGE)
            self.find_node(row, day.isoformat(), scroll=True).click()
            self.find("week_date_hint")

        open_week()
        create_pattern = re.escape(self.strings["week_create"]).replace(re.escape("%1$s"), r"\d+:\d+")
        create = self.device(textMatches=create_pattern, packageName=PACKAGE)
        self.find_node(create, "selected daily window", scroll=True).click()
        self.find("week_weekdays", scroll=True)
        self.click("week_save", scroll=True)
        self.find("week_routines", scroll=True)

        # Read the device's date rather than the runner's date (or a fixed day of week).
        today = date.fromisoformat(adb("shell", "date", "+%F"))
        future = [today + timedelta(days=i) for i in range(1, 7)]
        moved, skipped, unchanged = [day for day in future if day.weekday() < 5][:3]

        open_date(moved)
        self.click("week_change_time")
        # Native Android time picker: editing minutes alone preserves its local AM/PM
        # or 24h selection. 20:00 becomes 20:15 under either system clock preference.
        minute = self.device(resourceId="android:id/input_minute")
        if not minute.exists:
            self.device(resourceId="android:id/toggle_mode").click(timeout=8)
        minute.set_text("15", timeout=8)
        self.device(resourceId="android:id/button1").click(timeout=8)
        self.find("week_changed", scroll=True)

        open_date(skipped)
        self.click("week_skip_day")
        self.find("week_skipped", scroll=True)

        open_week()
        self.find("week_changed", scroll=True)
        self.find("week_skipped", scroll=True)
        open_date(unchanged)
        if self.device(text=self.strings["week_restore_day"], packageName=PACKAGE).exists:
            raise AssertionError("The following weekday unexpectedly acquired an override")
        self.device.press("back")
        if not self.device(text=self.strings["week_date_hint"], packageName=PACKAGE).wait_gone(timeout=8):
            raise AssertionError("The one-date editor did not close")
        self.device.press("back")
        stored = self.export_backup("week-after.json")
        days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"}
        routines = stored["schedules"]
        if len(routines) != 1:
            raise AssertionError(f"Expected one weekday routine, found {routines}")
        routine = routines[0]
        if (routine["protocol_id"] != stored["settings"]["default_protocol"] or
                not routine["enabled"] or set(routine["days"]) != days or routine["start_minute"] != 20 * 60):
            raise AssertionError(f"The original weekday routine changed: {routine}")
        expected = {moved.isoformat(): "20:15", skipped.isoformat(): "skip"}
        if stored["settings"]["day_overrides"] != expected:
            raise AssertionError(f"Unexpected planner overrides: {stored['settings']['day_overrides']}")
        print("Verified a weekday plan, one moved start, one skipped date and an unchanged weekday after relaunch.")


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
    # A slow emulator boot can leave the system launcher's ANR dialog over the test app.
    # Only dismiss the launcher dialog. An ANR from Fasting Shift must still fail the test.
    welcome = device(text=base.strings["onb_get_started"], packageName=PACKAGE)
    deadline = time.monotonic() + 45
    while time.monotonic() < deadline and not welcome.wait(timeout=1):
        launcher_dialog = device(text="Pixel Launcher isn't responding", packageName="android")
        if launcher_dialog.exists:
            device(text="Close app", packageName="android").click(timeout=3)
            device.app_start(PACKAGE, activity=ACTIVITY)
    base.create_fasts()
    before = fingerprint(base.export_backup("upgrade-before.json")["fasts"])
    device.app_stop(PACKAGE)

    # Package manager enforces package identity and signing certificate continuity.
    adb("install", "-r", new_apk)
    assert installed_version() == 101, "Replacement version was not installed"
    device.app_start(PACKAGE, activity=ACTIVITY)
    candidate = Screen(device, Path("."))
    candidate.find("timer_end", scroll=True)
    after = fingerprint(candidate.export_backup("upgrade-after.json")["fasts"])
    if before != after:
        raise AssertionError(f"Fast records changed on upgrade: {before} -> {after}")
    print("Verified both fasts, including the running timer's original start and target.")
    candidate.verify_fresh_invite()
    candidate.verify_week_planner()


if __name__ == "__main__":
    device = u2.connect()
    try:
        main(device, *sys.argv[1:])
    except Exception:
        DIAGNOSTICS.mkdir(exist_ok=True)
        (DIAGNOSTICS / "logcat.txt").write_text(adb("logcat", "-d", "-t", "1200"))
        hierarchy = device.dump_hierarchy()
        (DIAGNOSTICS / "screen.xml").write_text(hierarchy)
        for node in ET.fromstring(hierarchy).iter("node"):
            attrs = node.attrib
            label = attrs.get("text") or attrs.get("content-desc")
            if label:
                print(f"{attrs.get('package')} | {label} | {attrs.get('bounds')}")
        device.screenshot(str(DIAGNOSTICS / "screen.png"))
        raise
