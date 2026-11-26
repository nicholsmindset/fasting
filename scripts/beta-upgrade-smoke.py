"""Exercise upgrade and backup recovery on a fresh disposable Android 15 emulator."""
from base64 import urlsafe_b64encode
from contextlib import contextmanager
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
NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
DIAGNOSTICS = Path("upgrade-diagnostics")


def adb(*arguments):
    return subprocess.check_output(["adb", *arguments], text=True).strip()


def wait_until_offline():
    for _ in range(30):
        connectivity = adb("shell", "dumpsys", "connectivity")
        if re.search(r"Active default network:\s*(?:none|null)\b", connectivity, re.IGNORECASE):
            return
        time.sleep(1)
    raise AssertionError("Android still reports an active default network")


@contextmanager
def offline_emulator():
    # The caller verifies this is a fresh, disposable API 35 emulator. ADB runs over the
    # host transport and keeps working when guest Wi-Fi and cellular data are off.
    airplane = adb("shell", "settings", "get", "global", "airplane_mode_on")
    wifi = adb("shell", "settings", "get", "global", "wifi_on")
    mobile = adb("shell", "settings", "get", "global", "mobile_data")
    try:
        adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
        adb("shell", "svc", "wifi", "disable")
        adb("shell", "svc", "data", "disable")
        wait_until_offline()
        yield
    finally:
        if airplane != "1":
            adb("shell", "cmd", "connectivity", "airplane-mode", "disable")
        adb("shell", "svc", "wifi", "enable" if wifi == "1" else "disable")
        adb("shell", "svc", "data", "disable" if mobile == "0" else "enable")


def reboot_emulator():
    boot_count = int(adb("shell", "settings", "get", "global", "boot_count"))
    subprocess.run(["adb", "reboot"], check=True, timeout=20)
    subprocess.run(["adb", "wait-for-device"], check=True, timeout=180)
    for _ in range(150):
        if adb("shell", "getprop", "sys.boot_completed") == "1":
            current = adb("shell", "settings", "get", "global", "boot_count")
            if current.isdecimal() and int(current) > boot_count:
                break
        time.sleep(1)
    else:
        raise AssertionError("Android did not complete a new boot")
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    wait_until_offline()


def installed_version():
    info = adb("shell", "dumpsys", "package", PACKAGE)
    return int(re.search(r"\bversionCode=(\d+)\b", info).group(1))


def notification_permission_granted():
    info = adb("shell", "dumpsys", "package", PACKAGE)
    permission = re.search(rf"{re.escape(NOTIFICATIONS)}: granted=(true|false)", info)
    if not permission:
        raise AssertionError("Android did not report the notification permission")
    return permission.group(1) == "true"


def visible_label_center(hierarchy, label):
    merged_match = None
    for node in ET.fromstring(hierarchy).iter("node"):
        attrs = node.attrib
        if attrs.get("package") != PACKAGE or attrs.get("visible-to-user") == "false":
            continue
        # Compose exposes both text and accessibility descriptions. A merged
        # parent can contain a label that also has its own, more precise bounds.
        values = (attrs.get("text", ""), attrs.get("content-desc", ""))
        exact = any(" ".join(value.split()) == label for value in values)
        if not exact and not any(" ".join(line.split()) == label for value in values for line in value.splitlines()):
            continue
        bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
        if bounds:
            left, top, right, bottom = map(int, bounds.groups())
            if right > left and bottom > top:
                center = (left + right) // 2, (top + bottom) // 2
                if exact:
                    return center
                merged_match = center
    return merged_match


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
            if not self.device(packageName=PACKAGE).wait(timeout=15):
                raise AssertionError(f"Fasting Shift did not reach the foreground while looking for {label}")
            for direction in ("up", "down"):
                for _ in range(10):
                    self.device.swipe_ext(direction, scale=0.65)
                    if node.wait(timeout=0.35):
                        return node
        raise AssertionError(f"Missing {label}")

    def click(self, key, scroll=False):
        self.find(key, scroll).click()

    def advance_onboarding(self, source_key, next_key):
        # AnimatedContent briefly exposes two copies of Next. Wait for the
        # outgoing title to leave before allowing the next screen to use it.
        source = self.find(source_key)
        target = self.device(text=self.strings[next_key], packageName=PACKAGE)
        for _ in range(3):
            if target.exists:
                break
            if not source.exists:
                break
            self.click("onb_next")
            if target.wait(timeout=3):
                break
        if not target.wait(timeout=12) or not source.wait_gone(timeout=5):
            raise AssertionError(f"Onboarding did not advance from {source_key} to {next_key}")

    def finish_free_onboarding(self, destination, allow_notifications):
        # Ads and billing services are not needed. Older baselines display an offer before
        # entering the app; current free builds go directly to the timer or pending invite.
        offer = self.device(text=self.strings["onb_offer_free"], packageName=PACKAGE)
        target = self.device(text=self.strings[destination], packageName=PACKAGE)
        permission = self.device(resourceIdMatches=".*permissioncontroller:id/permission_allow_button")
        deadline = time.monotonic() + 25
        while time.monotonic() < deadline:
            if permission.exists:
                if not allow_notifications:
                    raise AssertionError("Not now unexpectedly opened the notification permission dialog")
                permission.click()
            elif offer.exists:
                if "onb_open_tour" in self.strings:
                    raise AssertionError("A paid offer interrupted free onboarding")
                offer.click()
            elif target.exists:
                return
            time.sleep(0.25)
        raise AssertionError(f"Onboarding did not reach {destination}")

    def onboard(self, destination, allow_notifications):
        self.click("onb_get_started")
        # The baseline first opened a mandatory tour. On newer APKs the same
        # primary action goes straight to Goal; the tour is a separate choice.
        if "onb_open_tour" not in self.strings:
            self.click("onb_skip")
        self.find("onb_goal_title")
        self.click("goal_consistency")
        self.advance_onboarding("onb_goal_title", "onb_screen_title")
        fields = self.device(className="android.widget.EditText", packageName=PACKAGE)
        if fields.count != 3:
            raise AssertionError(f"Expected three screening inputs, found {fields.count}")
        for index, value in enumerate(("40", "175", "70")):
            fields[index].set_text(value)
        self.advance_onboarding("onb_screen_title", "onb_ok_title")
        self.advance_onboarding("onb_ok_title", "onb_protocol_title")
        if "first_plan_title" in self.strings:
            self.advance_onboarding("onb_protocol_title", "first_plan_title")
            self.click("first_plan_leave_open", scroll=True)
            self.finish_free_onboarding(destination, allow_notifications=False)
            if allow_notifications and int(adb("shell", "getprop", "ro.build.version.sdk")) >= 33:
                # These scenarios exercise real Android delivery. An open plan doesn't
                # request permission; enable it through the app's existing settings path.
                self.enable_notifications_in_settings()
                self.device(resourceId="tab_timer", packageName=PACKAGE).click(timeout=15)
            return
        self.advance_onboarding("onb_protocol_title", "onb_notif_title")
        self.click("onb_notif_allow" if allow_notifications else "onb_not_now")
        self.finish_free_onboarding(destination, allow_notifications)

    def create_fasts(self):
        self.onboard("tab_timer", allow_notifications=True)
        self.click("timer_start", scroll=True)
        self.click("timer_end", scroll=True)
        self.find("summary_title_early")
        self.click("save", scroll=True)
        self.click("timer_start", scroll=True)
        self.find("timer_end", scroll=True)

    def settings_overview(self):
        return self.device(resourceId="settings_overview", packageName=PACKAGE)

    def open_settings(self):
        tab = self.device(resourceId="tab_settings", packageName=PACKAGE)
        if tab.wait(timeout=2):
            tab.click()
        else:
            # The oldest upgrade baselines expose only the text of the tab.
            self.click("tab_settings")

    def open_settings_category(self, tag):
        row = self.find_node(self.device(resourceId=tag, packageName=PACKAGE), tag, scroll=True)
        # Let scrolling and the entering overview settle before sending a physical
        # tap. Reading a tag alone does not guarantee its screen position is fixed.
        previous = None
        stable_intervals = 0
        deadline = time.monotonic() + 6
        while time.monotonic() < deadline:
            bounds = row.info.get("visibleBounds") if row.exists else None
            if bounds and bounds["right"] > bounds["left"] and bounds["bottom"] > bounds["top"]:
                stable_intervals = stable_intervals + 1 if bounds == previous else 0
                if stable_intervals >= 2:
                    row.click(timeout=1)
                    return
            else:
                stable_intervals = 0
            previous = bounds
            time.sleep(0.2)
        raise AssertionError(f"Settings control {tag} did not settle into a visible position")

    def back_to_settings(self):
        self.device.press("back")
        if not self.settings_overview().wait(timeout=10):
            raise AssertionError("Back from the settings category did not return to the overview")

    def open_downloads_folder(self):
        picker = {"packageNameMatches": ".*documentsui"}
        # On a fresh install OpenDocument often starts in Recent. Select the same
        # Downloads root explicitly in both CreateDocument and OpenDocument.
        files = self.device(text="Files in Downloads", **picker)
        drawer = self.device(textMatches="(?i)Open from|Save to", **picker)
        if not drawer.exists:
            if files.exists or self.device(text="Downloads", resourceIdMatches=".*:id/toolbar_title", **picker).exists:
                return
            self.device(descriptionMatches="(?i)(show roots|open navigation drawer|show navigation drawer)", **picker).click(timeout=10)
        # The drawer is drawn after the toolbar and breadcrumb. Both can have
        # the same Downloads label behind it, especially on a fresh install.
        choices = self.device(text="Downloads", **picker)
        if not choices.wait(timeout=10):
            raise AssertionError("Downloads root missing from the Android picker")
        choices[choices.count - 1].click()
        if not drawer.wait_gone(timeout=10):
            raise AssertionError("Android picker navigation drawer remained open")


    def export_backup(self, name, downloads=False):
        self.open_settings()
        # The two signed APKs are built from different checkouts. The older flat
        # screen has its backup action inline; the new overview links to Privacy.
        categorized = self.settings_overview().wait(timeout=3)
        if categorized:
            self.open_settings_category("settings_open_privacy")
        self.click("settings_backup", scroll=True)
        # The picker chooses a local folder on a fresh emulator. Locate the unique file
        # on shared storage, regardless of whether this image defaults to Documents or Downloads.
        field = self.device(className="android.widget.EditText", packageNameMatches=".*documentsui")
        if not field.wait(timeout=15):
            raise AssertionError("The Android document picker did not open")
        if downloads:
            self.open_downloads_folder()
        field.set_text(name)
        save = self.device(packageNameMatches=".*documentsui", textMatches="(?i)save", enabled=True)
        named_field = self.device(className="android.widget.EditText", packageNameMatches=".*documentsui", text=name)
        if not named_field.wait(timeout=8):
            raise AssertionError(f"Files did not accept the requested filename {name}")
        # Gboard can shift Save between locating and tapping it. Resolve its
        # position again only while this same document is still being created.
        for _ in range(3):
            if not named_field.exists:
                break
            # Files can finish creating the document between the field check and
            # this tap. The app return and the exported JSON are checked below.
            save.click_exists(timeout=3)
            if named_field.wait_gone(timeout=3):
                break
        if named_field.exists or not self.device(packageName=PACKAGE).wait(timeout=15):
            raise AssertionError("The picker did not return to Fasting Shift")
        for _ in range(40):
            # The Android shell cannot enumerate some scoped Android/data subdirectories.
            lookup = self.device.shell(["find", "/sdcard/", "-type", "f", "-name", name]).output
            matches = [line for line in lookup.splitlines() if line.endswith("/" + name)]
            if matches:
                if downloads and matches != ["/sdcard/Download/" + name]:
                    raise AssertionError(f"Expected the user-selected Downloads location, found {matches}")
                raw = adb("exec-out", "cat", matches[0])
                try:
                    data = json.loads(raw)
                except json.JSONDecodeError:
                    pass  # The provider created the document before the app finished writing.
                else:
                    if categorized:
                        self.back_to_settings()
                    return data
            time.sleep(0.5)
        raise AssertionError(f"The app did not write {name}")

    def verify_appearance(self, original_fasts):
        def choose(key):
            self.open_settings()
            # The section "Appearance and language" and its Appearance row are both
            # visible on larger screens. Only the tagged row opens the choices.
            self.open_settings_category("settings_open_appearance")
            self.click(key, scroll=True)

        def expect_palette(dark):
            # Sample the app content, excluding system bars. Both Timer and Settings
            # have predominantly neutral surfaces, even with the timer running.
            matching_frames = 0
            for _ in range(20):
                frame = self.device.screenshot().convert("L")
                w, h = frame.size
                histogram = frame.crop((w // 10, h // 5, w * 9 // 10, h * 4 // 5)).histogram()
                matching = sum(histogram[:96]) if dark else sum(histogram[175:])
                nav_visible = self.device(resourceId="tab_settings", packageName=PACKAGE).exists
                if nav_visible and matching > sum(histogram) * 0.60:
                    matching_frames += 1
                    if matching_frames >= 3:
                        return
                else:
                    matching_frames = 0
                time.sleep(0.4)
            raise AssertionError(f"Expected {'dark' if dark else 'light'} app surfaces")

        adb("shell", "cmd", "uimode", "night", "no")
        choose("appearance_dark")
        expect_palette(dark=True)
        self.device.app_stop(PACKAGE)
        self.device.app_start(PACKAGE, activity=ACTIVITY, wait=True)
        self.find("timer_end", scroll=True)
        expect_palette(dark=True)
        try:
            # Explicit Light must override a dark device. Switching back to System
            # should restore the dark device palette without relaunching.
            adb("shell", "cmd", "uimode", "night", "yes")
            choose("appearance_light")
            expect_palette(dark=False)
            choose("appearance_system")
            expect_palette(dark=True)
        finally:
            adb("shell", "cmd", "uimode", "night", "no")
        self.find("tab_timer")
        expect_palette(dark=False)
        if fingerprint(self.export_backup("appearance-after.json")["fasts"]) != original_fasts:
            raise AssertionError("Changing appearance changed the fasting records")
        print("Verified light, dark, system changes and a cold launch with the original active timer.")

    def verify_reminder_and_timer_view(self, initial_end_alert, original_fasts):
        self.open_settings()
        self.open_settings_category("settings_open_reminders")
        label = self.strings["settings_end_alert"]
        self.find_node(self.device(textContains=label, packageName=PACKAGE), label, scroll=True).click()
        self.back_to_settings()
        self.device.app_stop(PACKAGE)
        self.device.app_start(PACKAGE, activity=ACTIVITY, wait=True)
        self.find("timer_end")
        self.click("timer_choose_view")
        self.click("timer_view_end")
        description = self.strings["a11y_mode_break_at"].split("%1$s")[0]
        if not self.device(descriptionContains=description, packageName=PACKAGE).wait(timeout=10):
            raise AssertionError("Choosing End time did not change the active timer display")
        changed = self.export_backup("reminder-after.json")
        if changed["settings"]["fast_end_alert"] != (not initial_end_alert):
            raise AssertionError("The end-of-fast reminder toggle did not survive relaunch")
        if changed["settings"]["timer_mode"] != "BREAK_AT":
            raise AssertionError("The selected end-time view was not saved")
        if fingerprint(changed["fasts"]) != original_fasts:
            raise AssertionError("Changing a reminder or timer view changed the running fast or history")
        print("Verified Settings category navigation, a persisted reminder change, and the end-time view with the original running fast.")

    def enable_notifications_in_settings(self):
        if notification_permission_granted():
            raise AssertionError("Expected notifications off before the tester opted in")
        self.open_settings()
        self.open_settings_category("settings_open_reminders")
        label = self.strings["settings_reliability"]
        self.find_node(self.device(textContains=label, packageName=PACKAGE), label, scroll=True).click()
        self.find("rel_title")
        fix = self.device(textContains=self.strings["rel_notifications_fix"], packageName=PACKAGE)
        self.find_node(fix, self.strings["rel_notifications_fix"], scroll=True).click()
        settings = "com.android.settings"
        if not self.device(packageName=settings, textContains="Fasting Shift").wait(timeout=15):
            raise AssertionError("The reminder action did not open this app’s Android notification settings")

        # Android 15 may display the master preference's label or just the switch.
        # With app-level notifications disabled, channel switches cannot be used.
        label = self.device(packageName=settings, textMatches=r"(?i)(All Fasting Shift.* notifications|Allow notifications)")
        if label.exists:
            label.click()
        else:
            switches = self.device(packageName=settings, checkable=True, enabled=True)
            if switches.count != 1:
                raise AssertionError(f"Could not identify the app-wide notification switch: {switches.count} enabled switches")
            switches.click()
        deadline = time.monotonic() + 10
        while not notification_permission_granted():
            if time.monotonic() >= deadline:
                raise AssertionError("Android did not grant notifications after the explicit settings choice")
            time.sleep(0.2)
        self.device.press("back")
        self.find("rel_title")
        if not fix.wait_gone(timeout=10):
            raise AssertionError("Reliability still offers to enable notifications after returning from Android settings")
        self.device.press("back")
        self.back_to_settings()

    def verify_declined_first_start(self):
        if notification_permission_granted():
            raise AssertionError("Not now unexpectedly granted notification permission")
        self.device(resourceId="tab_timer", packageName=PACKAGE).click(timeout=15)
        # The chosen default is one hour ago, rounded down to the minute. Use the
        # device clock, and verify this exact range against the later exported record.
        earliest = int(adb("shell", "date", "+%s")) - 3662
        self.click("timer_start_earlier", scroll=True)
        self.find("timer_start_earlier_title")
        self.click("ok")
        latest = int(adb("shell", "date", "+%s")) - 3598
        self.click("timer_start", scroll=True)
        permission = self.device(packageNameMatches=r".*permissioncontroller")
        end = self.device(text=self.strings["timer_end"], packageName=PACKAGE)
        deadline = time.monotonic() + 20
        while not end.exists:
            if permission.exists:
                raise AssertionError("Starting after Not now opened a permission prompt")
            if time.monotonic() >= deadline:
                raise AssertionError("The first fast did not start after declining notifications")
            time.sleep(0.2)
        if permission.wait(timeout=2) or notification_permission_granted():
            raise AssertionError("First Start did not honor the notification choice")
        self.enable_notifications_in_settings()
        print("Verified first Start with an earlier time after Not now, and a later notification opt-in.")
        return earliest, latest

    def verify_fresh_invite(self):
        # Only run after all upgrade assertions. Reset this dedicated emulator app's data so
        # screening and onboarding actually run while Android is handing over the new invite.
        if adb("shell", "pm", "clear", PACKAGE) != "Success":
            raise AssertionError("Could not reset the test beta package")
        # Make this dedicated fixture match a new install even on images that
        # preserve previous permission decisions when clearing app data.
        if notification_permission_granted():
            adb("shell", "pm", "revoke", PACKAGE, NOTIFICATIONS)
        adb("shell", "pm", "clear-permission-flags", PACKAGE, NOTIFICATIONS, "user-set", "user-fixed")
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
            # Compose replaces its accessibility nodes as the join card gives way
            # to the detail toolbar. Read a fresh visible hierarchy on each poll.
            deadline = time.monotonic() + 20
            while time.monotonic() < deadline:
                for node in ET.fromstring(self.device.dump_hierarchy()).iter("node"):
                    attrs = node.attrib
                    if attrs.get("package") != PACKAGE or name not in (attrs.get("text"), attrs.get("content-desc")):
                        continue
                    bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", attrs.get("bounds", ""))
                    if bounds and attrs.get("visible-to-user") != "false":
                        left, top, right, bottom = map(int, bounds.groups())
                        if right > left and bottom > top:
                            return
                time.sleep(0.25)
            raise AssertionError(f"Missing visible challenge title: {name}")

        open_invite()
        self.onboard("challenge_join_title", allow_notifications=False)
        check_title()
        choice = self.device(textContains=self.strings["friend_own_plan"], packageName=PACKAGE)
        self.find_node(choice, "Follow my own plan", scroll=True).click()
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
        if joined[0].get("follows_own_plan") is not True:
            raise AssertionError("Reopening the invitation lost the private choice of plan")
        print("Verified fresh onboarding, personal-plan enrollment, and reopening the same local challenge.")


    def verify_week_planner(self):
        # The fresh invite test leaves the screened user in Settings. Reopen from launcher
        # to test the same navigation that a returning tester uses.
        def open_week():
            self.device.app_stop(PACKAGE)
            self.device.app_start(PACKAGE, activity=ACTIVITY)
            self.open_settings()
            label = self.strings["week_title"]
            self.find_node(self.device(textContains=label, packageName=PACKAGE), label, scroll=True).click()

        def open_date(day):
            row = self.device(resourceId=f"week-day-{day.isoformat()}", packageName=PACKAGE)
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
        self.click("plan_date_save", scroll=True)
        self.find("week_changed", scroll=True)

        open_date(skipped)
        self.click("week_skip_day")
        self.click("plan_date_save", scroll=True)
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

    def verify_history_day(self):
        today = date.fromisoformat(adb("shell", "date", "+%F"))
        selected = today - timedelta(days=7)

        def open_selected_day():
            self.device.app_stop(PACKAGE)
            self.device.app_start(PACKAGE, activity=ACTIVITY, wait=True)
            # Wait for the freshly loaded Timer, then activate the navigation item itself.
            # A text match for "History" can also be the title on the previous screen.
            self.find("timer_start")
            self.device(resourceId="tab_history", packageName=PACKAGE).click(timeout=15)
            if not self.device(resourceId="history-list", packageName=PACKAGE).wait(timeout=15):
                raise AssertionError("The History tab did not open from Timer")
            shown = date.fromisoformat(adb("shell", "date", "+%F"))
            if (selected.year, selected.month) != (shown.year, shown.month):
                previous = self.device(description=self.strings["a11y_prev_month"], packageName=PACKAGE)
                self.find_node(previous, "Previous month", scroll=True).click()
            cell = self.device(resourceId=f"history-day-{selected}", packageName=PACKAGE)
            self.find_node(cell, selected.isoformat(), scroll=True).click(offset=(0.5, 0.2))

        open_selected_day()
        self.find("history_day_empty")
        self.click("add_past_button")
        self.find("add_past_title")
        self.click("save", scroll=True)
        self.find("history_day_recorded")
        open_selected_day()
        self.find("history_day_recorded")
        self.click("settings_done", scroll=True)
        if not self.device(resourceId="history-day-sheet", packageName=PACKAGE).wait_gone(timeout=8):
            raise AssertionError("The History day sheet did not close")
        history = self.device(resourceId="history-list", packageName=PACKAGE)
        if not history.wait(timeout=8):
            raise AssertionError("The History list did not return after closing the day")
        bounds = history.info["visibleBounds"]
        height = bounds["bottom"] - bounds["top"]
        x = bounds["left"] + (bounds["right"] - bounds["left"]) // 6
        start = bounds["top"] + height * 3 // 4
        end = start - height // 3

        def recap_control(key):
            # The recap follows the calendar. Keep consecutive viewports
            # overlapping so a fling cannot skip a question or its answers.
            label = self.strings[key]
            for attempt in range(9):
                center = visible_label_center(self.device.dump_hierarchy(), label)
                if center:
                    return center
                if attempt == 8:
                    break
                adb("shell", "input", "swipe", str(x), str(start), str(x), str(end), "650")
            raise AssertionError(f"Missing {label} below the History calendar")

        recap_control("recap_fit_question")
        self.device.click(*recap_control("recap_fit_partly"))
        recap_control("recap_change_answer")
        self.device.app_stop(PACKAGE)
        self.device.app_start(PACKAGE, activity=ACTIVITY, wait=True)
        self.find("timer_start")
        backup = self.export_backup("history-day-after.json")
        answers = backup["settings"]["week_reflections"]
        if list(answers.values()) != ["PARTLY"]:
            raise AssertionError(f"Expected one saved weekly reflection, got {answers}")
        stored = backup["fasts"]
        if len(stored) != 1 or stored[0]["ended_at"] is None:
            raise AssertionError(f"Expected one ended fast from the calendar, got {stored}")
        end_seconds = stored[0]["ended_at"] // 1000
        local_end = adb("shell", "date", "-d", f"@{end_seconds}", "+%F")
        if local_end != selected.isoformat():
            raise AssertionError(f"Calendar date {selected} saved a fast ending on {local_end}")
        print("Verified a saved fast on its original History date and a weekly reflection after relaunch.")


    def verify_reinstall_restore(self, recovery_apk, start_range):
        name = "00-fasting-shift-recovery.json"
        before = self.export_backup(name, downloads=True)
        statuses = [fast["ended_at"] is None for fast in before["fasts"]]
        if sorted(statuses) != [False, True] or not before["schedules"] or not before["challenges"]:
            raise AssertionError("The recovery fixture must include a running fast, history, a schedule and a challenge")
        running = next(fast for fast in before["fasts"] if fast["ended_at"] is None)
        if not start_range[0] <= running["start_at"] // 1000 <= start_range[1]:
            raise AssertionError("The active fast lost the earlier start selected in the timer")
        if len(before["settings"]["day_overrides"]) != 2 or not before["settings"]["week_reflections"]:
            raise AssertionError("The recovery fixture must include dated overrides and a reflection")

        denied = subprocess.run(["adb", "install", "-r", recovery_apk], capture_output=True, text=True)
        if denied.returncode == 0 or "INSTALL_FAILED_UPDATE_INCOMPATIBLE" not in denied.stdout + denied.stderr:
            raise AssertionError("Android did not reject replacing the installed package with a different signer")
        # This package was created by this run on a disposable emulator. The backup
        # must still be on shared storage after Android removes the old app's data.
        if adb("uninstall", PACKAGE) != "Success":
            raise AssertionError("Could not remove the fixture app")
        adb("shell", "test", "-s", "/sdcard/Download/" + name)
        adb("install", recovery_apk)
        self.device.app_start(PACKAGE, activity=ACTIVITY, wait=True)
        self.onboard("tab_timer", allow_notifications=False)
        self.open_settings()
        self.open_settings_category("settings_open_privacy")
        label = self.strings["settings_restore"]
        self.find_node(self.device(textContains=label, packageName=PACKAGE), label, scroll=True).click()
        self.open_downloads_folder()
        self.device(text=name, packageNameMatches=".*documentsui").click(timeout=15)
        self.find("restore_confirm_title")
        self.click("restore_confirm_action")
        self.find("settings_restored")

        self.device.app_stop(PACKAGE)
        self.device.app_start(PACKAGE, activity=ACTIVITY, wait=True)
        self.find("timer_end", scroll=True)
        reboot_emulator()
        self.device = u2.connect()
        self.device.app_start(PACKAGE, activity=ACTIVITY, wait=True)
        self.find("timer_end", scroll=True)
        recovered = self.export_backup("recovery-after-reboot.json")
        for key in ("fasts", "schedules", "challenges", "screenings"):
            if before[key] != recovered[key]:
                raise AssertionError(f"Restoring the backup changed {key}")
        for key in ("goal", "default_protocol", "plan_revisions", "day_overrides", "week_reflections", "appearance"):
            if before["settings"][key] != recovered["settings"][key]:
                raise AssertionError(f"Restoring the backup changed the {key} setting")
        self.device(resourceId="tab_timer", packageName=PACKAGE).click(timeout=15)
        self.click("timer_end", scroll=True)
        self.find("summary_title_early")
        self.click("save", scroll=True)
        self.find("timer_start", scroll=True)
        self.device(resourceId="tab_history", packageName=PACKAGE).click(timeout=15)
        self.find_node(self.device(resourceId="history-list", packageName=PACKAGE), "History")
        final = self.export_backup("offline-finished.json")["fasts"]
        if len(final) != len(before["fasts"]):
            raise AssertionError("Finishing the recovered timer changed the number of fasts")
        persisted = next(fast for fast in final if fast["id"] == running["id"])
        if (persisted["ended_at"] is None or persisted["status"] != "ENDED_EARLY"
                or any(persisted[key] != running[key] for key in ("start_at", "target_end_at", "protocol_id"))):
            raise AssertionError("Ending the recovered fast lost its original start or target")
        wait_until_offline()
        print("Verified offline onboarding, a backdated start, History, file-picker restore, a full Android "
              "reboot with the original running timer and schedule, and ending that timer offline.")


def fingerprint(fasts):
    keys = ("id", "start_at", "target_end_at", "ended_at", "protocol_id", "status")
    if len(fasts) != 2 or {f["status"] for f in fasts} != {"ENDED_EARLY", "ACTIVE"}:
        raise AssertionError(f"Expected one ended fast and one active fast, found {fasts}")
    for fast in fasts:
        if (fast["ended_at"] is None) != (fast["status"] == "ACTIVE"):
            raise AssertionError(f"Inconsistent end time for fast {fast['id']}")
    return sorted(tuple(f[k] for k in keys) for f in fasts)


def main(device, old_apk, new_apk, recovery_apk):
    emulator = any(adb("shell", "getprop", key) == "1" for key in ("ro.boot.qemu", "ro.kernel.qemu"))
    if not emulator or adb("shell", "getprop", "ro.build.version.sdk") != "35" or adb("shell", "pm", "list", "packages", PACKAGE):
        raise RuntimeError("Use a fresh disposable Android 15 emulator without the internal beta package")
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
    # An upgrading tester may have previously switched off notifications in Android.
    if notification_permission_granted():
        adb("shell", "pm", "revoke", PACKAGE, NOTIFICATIONS)

    # Reproduce the actual pre-upgrade preference left by a previously supported release.
    adb("shell", "cmd", "locale", "set-app-locales", PACKAGE, "--locales", "zh-CN")
    assert "[zh-CN]" in adb("shell", "cmd", "locale", "get-app-locales", PACKAGE)

    # Package manager enforces package identity and signing certificate continuity.
    adb("install", "-r", new_apk)
    assert installed_version() == 101, "Replacement version was not installed"
    device.app_start(PACKAGE, activity=ACTIVITY, wait=True)
    candidate = Screen(device, Path("."))
    candidate.find("tab_history")
    assert "are []" in adb("shell", "cmd", "locale", "get-app-locales", PACKAGE), "Retired language override survived startup"
    for language, qualifier in [("ms", "ms"), ("id", "in")]:
        resources = Path(f"app/src/main/res/values-{qualifier}/strings_features.xml")
        label = ET.parse(resources).getroot().find("string[@name='tab_history']").text
        adb("shell", "cmd", "locale", "set-app-locales", PACKAGE, "--locales", language)
        candidate.find_node(device(packageName=PACKAGE, text=label), label)
        print(f"Verified Android rendered {language}: {label}", flush=True)
    adb("shell", "cmd", "locale", "set-app-locales", PACKAGE)
    candidate.find("tab_history")
    print("Verified the old Chinese preference reverted to the phone language and the English History tab.", flush=True)
    candidate.find("timer_end", scroll=True)
    upgraded = candidate.export_backup("upgrade-after.json")
    after = fingerprint(upgraded["fasts"])
    if before != after:
        raise AssertionError(f"Fast records changed on upgrade: {before} -> {after}")
    print("Verified both fasts, including the running timer's original start and target.")
    candidate.verify_appearance(before)
    candidate.verify_reminder_and_timer_view(upgraded["settings"]["fast_end_alert"], before)
    candidate.enable_notifications_in_settings()
    print("Verified a previously installed user can opt in through Android notification settings after upgrading.")
    with offline_emulator():
        candidate.verify_fresh_invite()
        candidate.verify_week_planner()
        candidate.verify_history_day()
        start_range = candidate.verify_declined_first_start()
        candidate.verify_reinstall_restore(recovery_apk, start_range)


if __name__ == "__main__":
    device = u2.connect()
    try:
        main(device, *sys.argv[1:])
    except Exception:
        DIAGNOSTICS.mkdir(exist_ok=True)
        (DIAGNOSTICS / "logcat.txt").write_text(adb("logcat", "-d", "-t", "1200"))
        crash = adb("logcat", "-b", "crash", "-d", "-t", "180")
        if PACKAGE in crash:
            print(crash)
        hierarchy = device.dump_hierarchy()
        (DIAGNOSTICS / "screen.xml").write_text(hierarchy)
        for node in ET.fromstring(hierarchy).iter("node"):
            attrs = node.attrib
            label = attrs.get("text") or attrs.get("content-desc")
            if label:
                print(f"{attrs.get('package')} | {label} | {attrs.get('bounds')}")
        device.screenshot(str(DIAGNOSTICS / "screen.png"))
        raise
