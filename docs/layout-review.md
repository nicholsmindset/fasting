# Fasting Shift layout review

## What made the beta feel generic

The same treatment marked three different things as important. A large ring meant “ready” on Timer, total hours and adherence on History, and water intake on Log. Large rounded cards then divided each screen into equally prominent blocks. The recurring shapes gave someone little help deciding what to do first.

The review covered the four daily tabs in the November appearance release. It used the app's layout code, beta screenshots and published design references. The comparison uses the same local sample records, minute, locale, theme and 411dp-wide phone viewport. Its findings are design judgments. The physical pilot is where to check whether the new layouts actually help people.

| Screen | What we found | Design decision |
| --- | --- | --- |
| Timer | An empty 232dp ring took the first-use focal point. Presets, custom setup and a second start action competed with starting the selected fast. | Show the selected window and Start together. A running fast gets the largest number, its start and goal, and one End action. Keep changing the window or display an explicit choice. |
| History | On the November fixture, a ring contained 191h, but its arc measured 11 of 15 planned days. Several chart bars read 16h and sounded alike when focused. | Give total hours their own headline. Put planned-day completion inside its ring. Give each fasting record a dated edit target, a common zero baseline and an average reference. Preserve date-level editing and the calendar's month total. |
| Log | A second oversized ring dominated water entry. Electrolyte totals and the input selector repeated the same three mineral names. | Put the water amount, goal and two portion buttons together. Make each mineral's total its selectable input row. |
| Settings | Routine changes, appearance and reminder reliability were dispersed. A long stack of individual switches buried other settings. | Keep direct access to My Week and Appearance. Move detailed controls to named pages for reminders, fasting preferences, privacy and data, safety and reports, and your plan. Show the current choices on the overview. |

## How the Settings controls work

A right-facing arrow opens a destination. A downward arrow opens a list of choices with the current value selected. Switching a setting takes effect on the first tap. Its checked position and checkmark remain visible in both themes, and the whole labeled row is one switch for TalkBack. Numeric steppers keep decrease and increase separate. File restore and deletion retain their confirmation steps. These differences give a user a way to anticipate what a tap will do. [Toggle switch research](https://www.nngroup.com/articles/toggle-switch-guidelines/)

## Research that informed the changes

- Zero's illustrated timer shows the chosen fasting window and start action as one task. Its distinction between setting a goal and running the timer informed our idle and active states. [Zero timer walkthrough, page 8](https://zerolongevity.com/blog/wp-content/uploads/The-Complete-Guide-to-FB.pdf)
- Apple Health gives the user a concise summary with a route into the underlying data. We used that relationship between a total and an individual dated record in History. [Apple Health summary and details](https://support.apple.com/en-sg/104997)
- The UK Office for National Statistics recommends a zero baseline for bars so that bar length represents magnitude. History keeps this shared baseline and distinguishes the average reference from the plotted fasts. [ONS chart guidance](https://service-manual.ons.gov.uk/data-visualisation/guidance/axes-and-gridlines)
- Nielsen Norman Group explains how scale and contrast establish what to look at first. We used a single dominant value or action per tab, shared left edges and fewer card outlines. [Visual hierarchy and contrast](https://www.nngroup.com/articles/good-visual-design/)
- Android recommends at least 48dp touch targets and scalable text. Its Settings guide calls for frequently used settings and their values near the top. Those principles informed chart targets, choice controls and the order of Settings. [Android accessibility](https://developer.android.com/design/ui/mobile/guides/foundations/accessibility) · [Android Settings patterns](https://developer.android.com/design/ui/mobile/guides/patterns/settings)

## Check the design with testers

Give the first tester a fresh install and the second a running sample fast. Without pointing at controls, ask them to start the shown window, find the expected finish time, identify how many hours they fasted this month, and explain what the ring counts. Ask them to edit one of two records with the same duration, log water, and find the next planned start. Record the first wrong tap and any point where they ask for help. Repeat the key tasks with larger Android text and TalkBack on a physical phone.
