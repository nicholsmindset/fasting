"""Drive separate host and guest emulators through the real partner week service in CI."""
from contextlib import contextmanager
from datetime import date
import importlib.util
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

import uiautomator2 as u2
import zxingcpp

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("upgrade", ROOT / "scripts/beta-upgrade-smoke.py")
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)
HOST = "com.fastingshift.app.staging"
GUEST = "com.fastingshift.app"
FORWARD = "tcp:18443"
HOST_ZONE = "Pacific/Kiritimati"
GUEST_ZONE = "Etc/GMT+12"


class PartnerScreen:
    def __init__(self, serial, package):
        self.serial = serial
        self.device = u2.connect(serial)
        self.package = package

    def adb(self, *args):
        return subprocess.check_output(["adb", "-s", self.serial, *args], text=True, timeout=120).strip()

    def fixture_reachable(self):
        # Exercise the exact loopback TCP port the app uses. Toybox returns zero
        # on successful connection. Its zero-I/O mode sends no TLS request.
        result = subprocess.run(["adb", "-s", self.serial, "shell", "toybox", "nc",
                                 "-z", "-w", "2", "127.0.0.1", "18443"],
                                stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                                stderr=subprocess.DEVNULL, timeout=8)
        return result.returncode == 0

    @contextmanager
    def offline(self):
        if not self.fixture_reachable():
            raise AssertionError("The emulator could not reach the fixture before disconnect")
        self.adb("reverse", "--remove", FORWARD)
        smoke.adb = self.adb
        try:
            with smoke.offline_emulator():
                # Removing the ADB reverse listener does not close a socket nginx
                # and Android already negotiated. Drop the old app process before
                # any tap, then relaunch against its previously saved membership.
                self.adb("shell", "am", "force-stop", self.package)
                if FORWARD in self.adb("reverse", "--list") or self.fixture_reachable():
                    raise AssertionError("The emulator can still reach the fixture")
                self.resume()
                if self.fixture_reachable():
                    raise AssertionError("Relaunching the app restored fixture connectivity")
                yield
        finally:
            self.adb("reverse", FORWARD, FORWARD)

    def launch(self, onboard=False):
        self.adb("shell", "am", "start", "-W", "-n", f"{self.package}/{smoke.ACTIVITY}")
        # Reuse the existing UI journey's real onboarding and safety screen for each install.
        if onboard:
            smoke.adb = self.adb
            smoke.PACKAGE = self.package
            smoke.Screen(self.device, ROOT).onboard("tab_timer", allow_notifications=False)
            if smoke.notification_permission_granted():
                raise AssertionError("Choosing Not now granted notification permission")

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
        if "mInputShown=true" in self.adb("shell", "dumpsys", "input_method"):
            self.device.press("back")

    def open_paste_form(self):
        # A newly copied invite can put System UI's clipboard preview above this
        # button while we switch app sandboxes. Confirm the actual destination.
        overlay = self.device(packageName="com.android.systemui", resourceIdMatches=".*:id/clipboard_.*")
        if overlay.exists:
            overlay.wait_gone(timeout=12)
        for _ in range(3):
            if self.node("partner_link").exists:
                return
            self.tap("partner_paste")
            if self.node("partner_link").wait(timeout=3):
                return
        raise AssertionError("Opening the invitation form did not display the link field")

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
        if not self.node("partner_create").wait(timeout=2):
            try:
                self.tap("partner_refresh", timeout=2)
            except AssertionError:
                # Refresh can disappear while scrolling if an automatic sync finishes.
                self.locate("partner_create", timeout=2)
        self.locate("partner_create", timeout=10)

    def plan_day(self, own, other, previously_saved=False):
        self.tap("partner_plan_open")
        own_row = self.locate(f"partner_plan_day_{own}")
        other_row = self.locate(f"partner_plan_day_{other}")
        if not own_row.info["checkable"] or own_row.info["checked"] != previously_saved or other_row.info["checked"]:
            raise AssertionError("Private day selections were lost or appeared on the other phone")
        if not previously_saved:
            own_row.click()
            if not own_row.info["checked"]:
                raise AssertionError("Tapping a private plan day did not select it")
        save = self.device(packageName=self.package, text="Save")
        save.click(timeout=5)
        if not own_row.wait_gone(timeout=5):
            raise AssertionError("Saving the private plan left the picker open")

    def capture(self, label):
        # Only established synthetic memberships are photographed. The invitation token,
        # clipboard, onboarding fields and device key never appear in these artifacts.
        if not self.device(packageName=self.package).exists:
            raise AssertionError("Partner app is not in the foreground")
        destination = ROOT / "partner-week-evidence"
        destination.mkdir(exist_ok=True)
        self.device.screenshot(str(destination / f"{label}.png"))

    def leave(self):
        # A renewal changes the page height while Compose keeps the previous scroll
        # position. Wait for the action to settle after scrolling before a physical tap.
        title = self.device(packageName=self.package, text="Leave this partner week?")
        for _ in range(3):
            control = self.locate("partner_leave", timeout=2)
            previous = None
            for _ in range(30):
                bounds = control.info["visibleBounds"] if control.exists else None
                if bounds and bounds == previous and bounds["bottom"] - bounds["top"] >= 12:
                    control.click()
                    break
                previous = bounds
                time.sleep(0.20)
            if title.wait(timeout=3):
                break
            self.device.swipe_ext("up", scale=0.55)
        else:
            raise AssertionError("Leave confirmation did not open")
        # Compose dialogs use a separate semantics window with accessible text labels.
        self.device(packageName=self.package, text="Leave partner week").click()
        self.locate("partner_create", timeout=10)


def open_invitation(guest, token):
    guest.adb("shell", "am", "force-stop", guest.package)
    # Suppress the normal am output: it includes the synthetic single-use link.
    result = subprocess.run([
        "adb", "-s", guest.serial, "shell", "am", "start", "-W", "-a", "android.intent.action.VIEW",
        "-c", "android.intent.category.BROWSABLE", "-p", guest.package,
        "-d", "fastingshift://partner/#" + token,
    ], capture_output=True, text=True, timeout=30)
    if result.returncode or "Error:" in result.stdout or result.stderr.strip().startswith("Error:"):
        raise AssertionError("Android could not launch the private partner invitation")


def invite(host, guest, observer, supporter):
    host.edit("partner_alias", "Rowan")
    if supporter:
        host.tap("partner_create_qr")
    else:
        host.tap("partner_create")
        # Protect the existing sharesheet path without sending the test invite.
        chooser = host.device(packageNameMatches=r"(com.android|com.google.android).intentresolver|android",
                              resourceIdMatches=".*:id/(chooser_header|chooser_headline|content_preview_text|resolver_list).*")
        if not chooser.wait(timeout=10):
            top = host.device.app_current().get("package")
            if top not in {"com.android.intentresolver", "com.google.android.intentresolver", "android"}:
                raise AssertionError("Creating an invitation did not open the Android sharesheet")
        host.device.press("back")
        host.tap("partner_show_qr")

    # Decode the screen in memory, just like a second phone's camera. Do not retain the token.
    qr_link = None
    for _ in range(10):
        scanned = zxingcpp.read_barcode(host.device.screenshot(), formats=zxingcpp.BarcodeFormat.QRCode)
        if scanned is not None:
            qr_link = scanned.text
            break
        time.sleep(0.5)
    if qr_link is None:
        raise AssertionError("The displayed Partner Week QR code could not be scanned")
    host.device.press("back")
    host.tap("partner_copy")

    # A third, unpaired app sandbox on the host device can paste exactly what the
    # user copied. Relay it to the other emulator without sharing device storage.
    observer.resume()
    observer.open_paste_form()
    observer.edit("partner_link")
    field = observer.device(packageName=observer.package, className="android.widget.EditText")
    link = field.get_text()
    match = re.fullmatch(r"https://www\.fastingshift\.com/partner/#([a-f0-9]{64})", link or "")
    if not match:
        raise AssertionError("The Android clipboard did not contain a complete partner link")

    if qr_link != link:
        raise AssertionError("Scanning and copying produced different invitations")

    if supporter:
        guest.resume()
        guest.open_paste_form()
        try:
            guest.edit("partner_link", link)
        except Exception:
            raise AssertionError("Guest could not enter the synthetic invite") from None
        guest.tap("partner_preview")
    else:
        open_invitation(guest, match.group(1))
    if not guest.device(packageName=guest.package, text="Rowan invited you").wait(timeout=12):
        raise AssertionError("Invitation preview did not identify the host")
    guest.edit("partner_alias", "Jamie")
    guest.tap("partner_supporter" if supporter else "partner_participant")
    guest.tap("partner_join")
    guest.locate("partner_leave")

    # Ordinary invitation preview by an unpaired install after redemption.
    observer.tap("partner_preview")
    observer.locate("partner_invite_unavailable")
    if observer.node("partner_join").exists or observer.node("partner_self_marks").exists:
        raise AssertionError("A used invitation exposed participant actions to a third installation")
    observer.tap("partner_close_invite")
    observer.locate("partner_create")
    if supporter:
        if guest.node("partner_checkin").exists:
            raise AssertionError("Supporter was given a participant's check-in")
    else:
        guest.tap("partner_checkin")
        guest.assert_marks("partner_self_marks", 1)
    host.resume()
    host.tap("partner_refresh", timeout=3)
    host.assert_marks("partner_self_marks", 0)


def fixture_query(query):
    if (os.environ.get("PGHOST"), os.environ.get("PGPORT")) != ("127.0.0.1", "18479"):
        raise AssertionError("The journey requires the disposable CI database")
    return subprocess.check_output(["psql", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-c", query],
                                   text=True, timeout=20).strip()


def expect_stored_host_marks(expected):
    rows = fixture_query("SELECT cardinality(marks) FROM fasting_partners.members WHERE seat='host'").splitlines()
    if rows != [str(expected)]:
        raise AssertionError(f"Expected one host row with {expected} persisted marks, observed counts {rows!r}")


def check_local_days(host, guest, initial):
    rows = fixture_query("SELECT seat, zone, cardinality(marks), marks[1] "
                         "FROM fasting_partners.members ORDER BY seat").splitlines()
    if len(rows) != 2:
        raise AssertionError("The test pair should be the only two members")
    marked = {}
    for seat, screen, zone in (("guest", guest, GUEST_ZONE), ("host", host, HOST_ZONE)):
        actual_seat, actual_zone, count, saved = rows[0 if seat == "guest" else 1].split("|")
        first = date.fromisoformat(initial[seat])
        last = date.fromisoformat(screen.adb("shell", "date", "+%F"))
        if (actual_seat, actual_zone, count) != (seat, zone, "1") or not first <= date.fromisoformat(saved) <= last:
            raise AssertionError(f"The {seat}'s persisted check-in did not match their emulator's local date")
        marked[seat] = saved
    if marked["guest"] == marked["host"]:
        raise AssertionError("The two local check-ins used the same calendar day")
    print(f"Local check-in days verified: host {marked['host']}, guest {marked['guest']}.")


def journey(host, guest, observer):
    observer.launch(onboard=True)
    guest.launch(onboard=True)
    host.launch(onboard=True)
    host.open()
    invite(host, guest, observer, supporter=True)
    print("A supporter accepted on a different emulator, and the third install could not reuse the invitation.")
    expect_stored_host_marks(0)
    with host.offline():
        host.tap("partner_checkin")
        host.assert_marks("partner_self_marks", 1)
        # Physically tap the same control again, even when the product disables it.
        host.locate("partner_checkin").click()
        host.assert_marks("partner_self_marks", 1)
        host.tap("partner_refresh")
        host.adb("shell", "am", "force-stop", HOST)
        host.launch()
        host.open()
        host.assert_marks("partner_self_marks", 1)
        expect_stored_host_marks(0)
    host.tap("partner_refresh", timeout=3)
    host.assert_marks("partner_self_marks", 1)
    guest.resume()
    guest.tap("partner_refresh", timeout=3)
    guest.assert_marks("partner_other_marks", 1)
    expect_stored_host_marks(1)
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
    initial = {"host": host.adb("shell", "date", "+%F"), "guest": guest.adb("shell", "date", "+%F")}
    invite(host, guest, observer, supporter=False)
    host.assert_marks("partner_other_marks", 1)
    # Neither participant plans today. Each can still check in, and their
    # independent plans survive refresh without appearing on the partner's phone.
    host.plan_day(1, 2)
    guest.plan_day(2, 1)
    guest.tap("partner_refresh")
    guest.plan_day(2, 1, previously_saved=True)
    host.tap("partner_refresh")
    host.plan_day(1, 2, previously_saved=True)
    host.tap("partner_checkin")
    host.assert_marks("partner_self_marks", 1)
    guest.resume()
    guest.tap("partner_refresh", timeout=3)
    guest.assert_marks("partner_other_marks", 1)
    check_local_days(host, guest, initial)

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
    if not guest.device(packageName=GUEST, textContains="asked to do another week").wait(timeout=8):
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
    print("A new week required both members' consent and reset their progress.")
    # Host earns an unsynced mark; the online guest revokes the partnership.
    # Reconnecting and retrying must discard the local mark and keep the pair gone.
    host.resume()
    expect_stored_host_marks(0)
    with host.offline():
        host.tap("partner_checkin")
        host.assert_marks("partner_self_marks", 1)
        guest.leave()
        if fixture_query("SELECT count(*) FROM fasting_partners.members") != "0":
            raise AssertionError("Leaving failed to remove shared membership")
    host.expect_unpaired()
    host.adb("shell", "am", "force-stop", HOST)
    host.resume()
    host.expect_unpaired()
    if fixture_query("SELECT count(*) FROM fasting_partners.weeks") != "0":
        raise AssertionError("An offline retry restored a revoked partnership")
    print("Leaving while the other emulator was offline discarded its queued mark on reconnect and restart.")


def main():
    host = PartnerScreen(os.environ["PARTNER_HOST_SERIAL"], HOST)
    guest = PartnerScreen(os.environ["PARTNER_GUEST_SERIAL"], GUEST)
    if host.serial == guest.serial:
        raise AssertionError("Host and guest must use separate Android emulators")
    observer = PartnerScreen(host.serial, GUEST)
    for screen, zone in ((host, HOST_ZONE), (guest, GUEST_ZONE)):
        if screen.adb("shell", "getprop", "ro.kernel.qemu") != "1":
            raise AssertionError("This automated test requires a disposable Android emulator")
        screen.adb("shell", "settings", "put", "global", "auto_time_zone", "0")
        screen.adb("shell", "cmd", "alarm", "set-timezone", zone)
        if screen.adb("shell", "getprop", "persist.sys.timezone") != zone:
            raise AssertionError(f"Android did not enter its assigned timezone {zone}")
        screen.adb("reverse", FORWARD, FORWARD)
    if host.adb("shell", "date", "+%F") == guest.adb("shell", "date", "+%F"):
        raise AssertionError("Partner emulators must start on different calendar dates")
    staging = str(ROOT / "app/build/outputs/apk/staging/app-staging.apk")
    debug = str(ROOT / "app/build/outputs/apk/debug/app-debug.apk")
    host.adb("install", "-r", staging)
    observer.adb("install", "-r", debug)
    guest.adb("install", "-r", debug)
    try:
        journey(host, guest, observer)
    except Exception:
        for screen in (host, guest):
            tags = [n.attrib["resource-id"] for n in ET.fromstring(screen.device.dump_hierarchy()).iter("node")
                    if n.attrib.get("resource-id", "").startswith("partner_")]
            print(f"{screen.serial} partner controls visible at failure: {tags}", file=sys.stderr)
        raise
    finally:
        for screen in (host, guest):
            subprocess.run(["adb", "-s", screen.serial, "reverse", "--remove", FORWARD], check=False)


if __name__ == "__main__":
    main()
