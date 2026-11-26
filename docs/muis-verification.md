# Singapore prayer timetable verification

Fasting Shift reads Subuh and Maghrib from its bundled Singapore CSV, including when the phone is offline. The source is Majlis Ugama Islam Singapura (MUIS), [annual Islamic calendars and prayer timetables](https://www.muis.gov.sg/resources/islamic-calendar/). Independent release verification uses the [MUIS Muslim Prayer Timetables collection on data.gov.sg](https://data.gov.sg/collections/2312/view) under the [Singapore Open Data Licence](https://data.gov.sg/open-data-licence). The verifier reads the official API without a key. Devices make no timetable API requests.

From the repository root, run:

```sh
python3 -m unittest discover -s scripts/muis
python3 scripts/muis/verify.py
```

The weekly CI job, changes to timetable inputs, and the Play internal-release workflow run the live comparison. Regular Android builds and unit tests work independently of the API. Every distinct date in each published dataset must match the local minute for both times. Duplicate, incomplete and conflicting data fail verification. Network failures also fail the online check and can be retried.

From October onwards, the next Singapore calendar year must be present in the bundle. MUIS sometimes publishes its annual PDF before data.gov.sg publishes the corresponding API rows. Until the API has that year, verification reports **PENDING API** only when the entire bundled year matches the reviewed PDF digest. When adding a year, transcribe the official annual table, independently verify all dates against the PDF, and record its canonical SHA-256 in `core/domain/src/test/resources/muis-reviewed-pdfs.csv`. The digest covers sorted `YYYY-MM-DD,HH:mm,HH:mm` rows, each terminated by a newline. Never update a digest to silence a mismatch without checking the PDF.
