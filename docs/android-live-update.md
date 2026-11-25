# Android Live Update for an active fast

Android 16 can show the optional ongoing timer in the status bar and notification shade. An opted-in active timer shows elapsed time on the chip and a localized planned end time in the notification. The track uses the same approximate landmarks as the in-app body timeline, clipped to the chosen target. Its last point is the target the user already selected. After that time the ordinary timer and End fast action remain available. Android 15 and earlier retain the existing chronometer.

The system controls promotion. The app requests it only while an opted-in timer is running and the app and channel permit it. It never opens promotion settings or requests a new runtime permission. Dismissing the notification suppresses reposts for that fast. A normal non-wakeup alarm updates the graphic at quarter-hour boundaries. Android may defer it during sleep or when exact alarms are unavailable. The system chronometer continues to advance without background work.

Android documents a 96 dp chip with about seven available characters. The chip carries elapsed time and the notification provides the full planned end in the person's device time format. [Android Live Updates](https://developer.android.com/develop/ui/views/notifications/live-update)

## Device verification

Run the same scenarios on these physical devices. Record OS build, screenshots and both battery configurations. Emulator tests cannot establish manufacturer behavior.

| Device | Checks to add to the common scenarios | Physical verification |
| --- | --- | --- |
| Google Pixel running Android 16+ | Compare the unlocked status chip, lock screen and expanded notification with system Live Updates allowed and disabled. | Pending |
| Samsung Galaxy running Android 16+ | Check status, lock-screen and Now Bar placement where offered. Repeat under default battery settings and power saving. | Pending |
| Xiaomi or Redmi running Android 16+ | Test HyperOS lock-screen display, background restrictions and battery saver. Record delays between scheduled and observed refresh. | Pending |

1. Grant notifications and enable **Settings → Reminders → Show a live timer notification**. Start a regular 16:8 fast. With a shorter backdated start, verify the title, live chronometer, target and End fast action. The only internal landmark for a 16-hour plan is hour 12.
2. Cross a 15-minute boundary while awake. Verify that progress and displayed elapsed time advance without a second notification or sound. Test screen off, app process death, restart, a clock or timezone change and reboot. Check both exact-alarm settings.
3. Disable system Live Updates for this app during the fast. Check that only the ordinary notification remains and there are no prompts. Turn off the in-app ongoing notification. It should disappear, along with the live tick.
4. Dismiss the notification on Android 16. Return to the app and wait past a refresh boundary. It should remain dismissed until a new fast or deliberate in-app re-enable.
5. Let the planned time pass. Confirm that progress stops at the original target. End the fast and confirm the live timer and its scheduled update disappear. Repeat on Android 15 to check the unchanged chronometer and its End fast action.

**Release note for outreach.** Fasting Shift supports Android 16 Live Updates for an active fast. The suggestion that it may be the first Android fasting app with this feature is unverified. Use a first-to-market claim only after a dated competitive audit.
