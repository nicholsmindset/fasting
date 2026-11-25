"""Drive separate host and guest installations through the real partner week service in CI."""
import importlib.util
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

import uiautomator2 as u2

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("upgrade", ROOT / "scripts/beta-upgrade-smoke.py")
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)
adb = smoke.adb
HOST = "com.fastingshift.app.staging"
GUEST = "com.fastingshift.app"
FORWARD = "tcp:18443"


class PartnerScreen:
    def __init__(self, device, package):
        self.device = device
        self.package = package

    def launch(self, onboard=False):
        adb("shell", "am", "start", "-W", "-n", f"{self.package}/{smoke.ACTIVITY}")
        # Reuse the existing UI journey's real onboarding and safety screen for each install.
        smoke.PACKAGE = self.package
        if onboard:
            smoke.Screen(self.device, ROOT).onboard("tab_timer", allow_notifications=False)

    def node(self, tag):
        return self.device(packageName=self.package, resourceId=tag)

    def locate(self, tag, timeout=12, scroll=True):
        target = self.node(tag)
        if target.wait(timeout=timeout):
            return target
        if scroll:
            for direction in ("up", "down"):
                for _ in range(7):
                    self.device.swipe_ext(direction, scale=0.62)
                    if target.wait(timeout=0.4):
                        return target
        raise AssertionError(f"{self.package} could not find {tag}")

    def tap(self, tag, **kwargs):
        target = self.locate(tag, **kwargs)
        deadline = time.monotonic() + 12
        while time.monotonic() < deadline:
            if target.exists and target.info["enabled"]:
                target.click()
                return
            time.sleep(0.15)
        raise AssertionError(f"{self.package} did not enable {tag}")

    def edit(self, tag, text=None):
        self.tap(tag)
        field = self.device(packageName=self.package, className="android.widget.EditText", focused=True)
        if not field.wait(timeout=3):
            raise AssertionError(f"{tag} did not focus a text field")
        if text is None:
            self.device.shell("input keycombination KEYCODE_CTRL_LEFT KEYCODE_V")
        else:
            field.set_text(text)
        # Hide the IME only if visible, so Back cannot navigate away from a field.
        if "mInputShown=true" in adb("shell", "dumpsys", "input_method"):
            self.device.press("back")

    def open(self):
        self.tap("partner_open", timeout=2)

    def resume(self):
        self.launch()
        # Pair controls may be below the fold on the timer after Android restores
        # the activity. A visible partner-page app bar also covers scrolled views.
        if not self.device(packageName=self.package, text="Partner week").wait(timeout=2) or not any(
            self.node(tag).exists for tag in ("partner_refresh", "partner_alias", "partner_self_marks",
                                             "partner_leave", "partner_checkin", "partner_cheer", "partner_copy",
                                             "partner_create", "partner_paste")
        ):
            self.open()

    def assert_marks(self, tag, count):
        self.locate(tag, timeout=3)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            xml = ET.fromstring(self.device.dump_hierarchy())
            for row in xml.iter("node"):
                if row.get("resource-id") != tag:
                    continue
                labels = " ".join(node.get("content-desc", "") + " " + node.get("text", "") for node in row.iter("node"))
                if re.search(rf"\b{count}\s+(?:of|/)\s*3\b", labels):
                    return
            time.sleep(0.25)
        raise AssertionError(f"{tag} did not display {count} of 3 check-ins")

    def expect_unpaired(self):
        # The foreground refresh may observe the departure before we can tap Refresh.
        if not self.node("partner_create").wait(timeout=2) and self.node("partner_refresh").exists:
            self.tap("partner_refresh", timeout=2)
        self.locate("partner_create", timeout=10)

    def capture(self, label):
        # Only established synthetic memberships are photographed. The invitation token,
        # clipboard, onboarding fields and device key never appear in these artifacts.
        if not self.device(packageName=self.package).exists:
            raise AssertionError("Partner app is not in the foreground")
        destination = ROOT / "partner-week-evidence"
        destination.mkdir(exist_ok=True)
        self.device.screenshot(str(destination / f"{label}.png"))

    def leave(self):
        self.tap("partner_leave", timeout=2)
        self.tap("partner_confirm_leave")
        self.locate("partner_create", timeout=10)


def invite(host, guest, supporter):
    host.edit("partner_alias", "Rowan")
    host.tap("partner_create")
    host.tap("partner_copy")
    guest.resume()
    guest.tap("partner_paste", timeout=3)
    guest.edit("partner_link")
    guest.tap("partner_preview")
    if not guest.device(packageName=guest.package, text="Rowan invited you").wait(timeout=12):
        raise AssertionError("Invitation preview did not identify the host")
    guest.edit("partner_alias", "Jamie")
    guest.tap("partner_supporter" if supporter else "partner_participant")
    guest.tap("partner_join")
    guest.locate("partner_leave")
    if supporter:
        if guest.node("partner_checkin").exists:
            raise AssertionError("Supporter was given a participant's check-in")
    else:
        guest.tap("partner_checkin")
        guest.assert_marks("partner_self_marks", 1)
    host.resume()
    host.tap("partner_refresh", timeout=3)
    host.assert_marks("partner_self_marks", 0)


def journey(device):
    host = PartnerScreen(device, HOST)
    guest = PartnerScreen(device, GUEST)
    guest.launch(onboard=True)
    host.launch(onboard=True)
    host.open()
    invite(host, guest, supporter=True)
    print("Private invite copied through the host UI and redeemed in a second Android installation as a supporter.")
    # Emulator ADB forwards can remain reachable while Android's network is off. Disconnect
    # the fixture as well and restart the host so the queued mark must survive process death.
    adb("reverse", "--remove", FORWARD)
    try:
        with smoke.offline_emulator():
            host.tap("partner_checkin")
            host.assert_marks("partner_self_marks", 1)
            adb("shell", "am", "force-stop", HOST)
            host.launch()
            host.open()
            host.assert_marks("partner_self_marks", 1)
    finally:
        adb("reverse", FORWARD, FORWARD)
    host.tap("partner_refresh", timeout=3)
    host.assert_marks("partner_self_marks", 1)
    guest.resume()
    guest.tap("partner_refresh", timeout=3)
    guest.assert_marks("partner_other_marks", 1)
    guest.capture("supporter-with-host-check-in")
    guest.tap("partner_cheer")
    host.resume()
    host.tap("partner_refresh", timeout=3)
    host.locate("partner_received")
    host.assert_marks("partner_self_marks", 1)
    host.capture("participant-with-encouragement")
    print("Offline check-in survived a process restart, synchronized as one mark, and received encouragement.")
    host.leave()
    guest.resume()
    guest.expect_unpaired()
    print("Leaving the partnership removed the shared week from the other installation.")
    host.resume()
    invite(host, guest, supporter=False)
    host.assert_marks("partner_other_marks", 1)
    host.tap("partner_checkin")
    host.assert_marks("partner_self_marks", 1)
    guest.resume()
    guest.tap("partner_refresh", timeout=3)
    guest.assert_marks("partner_other_marks", 1)
    print("Both participating members independently checked in on their own app installation.")

    # Move the synthetic fixture through the end of its week without changing either
    # device clock. Never run this against a remote database or a populated project.
    if (os.environ.get("PGHOST"), os.environ.get("PGPORT")) != ("127.0.0.1", "18479"):
        raise AssertionError("The end-of-week fixture requires the isolated CI database")
    subprocess.run(["psql", "-v", "ON_ERROR_STOP=1"], input="""
        DO $$ BEGIN
          IF (SELECT count(*) FROM fasting_partners.weeks) <> 1 OR
              (SELECT count(*) FROM fasting_partners.members WHERE alias IN ('Rowan','Jamie')) <> 2 THEN
            RAISE EXCEPTION 'Unexpected QA population';
          END IF;
          UPDATE fasting_partners.weeks
            SET started_at = now() - interval '8 days', finishes_at = now() - interval '1 day';
        END $$;
    """, text=True, check=True)
    guest.tap("partner_refresh", timeout=3)
    guest.tap("partner_renew")
    if not device(packageName=GUEST, textContains="asked to do another week").wait(timeout=8):
        raise AssertionError("Guest's invitation restarted the week without host consent")
    host.resume()
    host.tap("partner_refresh", timeout=3)
    host.locate("partner_renew")
    host.capture("recap-with-renewal-invitation")
    host.tap("partner_renew")
    host.assert_marks("partner_self_marks", 0)
    guest.resume()
    guest.tap("partner_refresh", timeout=3)
    guest.assert_marks("partner_self_marks", 0)
    guest.leave()
    print("A new week required both members' consent, reset their progress, and let the guest end sharing.")


def main():
    if adb("shell", "getprop", "ro.kernel.qemu") != "1":
        raise SystemExit("This automated test requires a disposable Android emulator.")
    adb("install", "-r", str(ROOT / "app/build/outputs/apk/staging/app-staging.apk"))
    adb("install", "-r", str(ROOT / "app/build/outputs/apk/debug/app-debug.apk"))
    adb("reverse", FORWARD, FORWARD)
    device = u2.connect()
    try:
        journey(device)
    except Exception:
        # Keep aliases, links and editable contents out of CI diagnostics.
        try:
            tags = [n.attrib["resource-id"] for n in ET.fromstring(device.dump_hierarchy()).iter("node")
                    if n.attrib.get("resource-id", "").startswith("partner_")]
            print(f"Partner controls visible at failure: {tags}", file=sys.stderr)
        finally:
            raise
    finally:
        subprocess.run(["adb", "reverse", "--remove", FORWARD], check=False)


if __name__ == "__main__":
    main()
