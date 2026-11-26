"""Cross-check the offline Singapore timetable against MUIS data. Requires Python 3.9+ and curl."""
import csv
import hashlib
import json
import re
import subprocess
import sys
from calendar import isleap
from collections import defaultdict
from datetime import date, datetime, time
from pathlib import Path
from urllib.parse import urlencode
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[2]
BUNDLE = ROOT / "core/domain/src/main/resources/com/fastingshift/domain/ramadan/muis-singapore.csv"
CATALOG = "https://api-production.data.gov.sg/v2/public/api"
COLLECTION = "2312"
DATASTORE = "https://data.gov.sg/api/action/datastore_search"


def get_json(url):
    result = subprocess.run(
        ["curl", "--fail", "--silent", "--show-error", "--location", "--max-time", "25",
         "--retry", "2", "--retry-max-time", "60", url],
        capture_output=True, text=True, check=False,
    )
    if result.returncode:
        raise ValueError(f"Official data unavailable: {result.stderr.strip()}")
    return json.loads(result.stdout)


def read_rows(dataset_id):
    offset = 0
    while True:
        query = urlencode(dict(resource_id=dataset_id, limit=1000, offset=offset, sort="_id asc"))
        response = get_json(f"{DATASTORE}?{query}")
        page = response["result"]
        if not response["success"] or page["resource_id"] != dataset_id or not page["records"]:
            raise ValueError(f"Missing official records for {dataset_id} at row {offset}")
        yield from page["records"]
        offset += len(page["records"])
        if offset == page["total"]:
            return
        if offset > page["total"]:
            raise ValueError(f"Official pagination changed for {dataset_id}; retry")


def parse_rows(rows, fields, earliest_year=None):
    years = defaultdict(dict)
    for row in rows:
        day = date.fromisoformat(row[fields[0]])
        if earliest_year is not None and day.year < earliest_year:
            continue  # Archived annual datasets use legacy time formats outside our app's coverage.
        values = tuple(time.fromisoformat(row[key]) for key in fields[1:])
        if any(v.tzinfo is not None or v.second or v.microsecond for v in values):
            raise ValueError(f"Expected local times with minute precision on {day}")
        if not (values[0].hour < 12 <= values[1].hour):
            raise ValueError(f"Invalid Subuh or Maghrib on {day}: {values}")
        if day in years[day.year]:
            raise ValueError(f"Duplicate date {day}")
        years[day.year][day] = tuple(v.strftime("%H:%M") for v in values)
    return years


def check_complete(year, rows, label):
    expected = 366 if isleap(year) else 365
    if len(rows) != expected:
        raise ValueError(f"{label} {year}: {len(rows)} distinct dates, expected {expected}")


def official_sources(earliest_year):
    collection = get_json(f"{CATALOG}/collections/{COLLECTION}/metadata")["data"]["collectionMetadata"]
    for dataset in collection["childDatasets"]:
        if not re.fullmatch(r"d_[a-f0-9]{32}", dataset):
            raise ValueError(f"Unknown dataset identifier {dataset!r}")
        meta = get_json(f"{CATALOG}/datasets/{dataset}/metadata")["data"]
        if meta["isDeprecated"]:
            continue
        if meta["managedBy"] != "Majlis Ugama Islam Singapura":
            raise ValueError(f"Unexpected publisher for {dataset}")
        name = meta["name"]
        first = date.fromisoformat(meta["coverageStart"][:10]).year
        last = date.fromisoformat(meta["coverageEnd"][:10]).year
        if last < earliest_year:
            continue
        records = parse_rows(read_rows(dataset), ("Date", "Subuh", "Maghrib"), earliest_year)
        for year in range(max(first, earliest_year), last + 1):
            check_complete(year, records.get(year, {}), name)
        yield name, records


def annual_digest(days):
    canonical = "".join(f"{day},{days[day][0]},{days[day][1]}\n" for day in sorted(days))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def verify(bundle, official, today, pdf_digests):
    if not bundle:
        raise ValueError("Bundled timetable is empty")
    required = {today.year}
    if today.month >= 10:
        required.add(today.year + 1)
    if missing := required - bundle.keys():
        raise ValueError(f"Bundled timetable missing year(s) {sorted(missing)}")
    for year, days in bundle.items():
        check_complete(year, days, "Bundled timetable")
    matched = set()
    for source, years in official:
        for year, days in years.items():
            check_complete(year, days, source)
            if year not in bundle:
                raise ValueError(f"{source}: official {year} data available but missing from bundle")
            for day, official_times in days.items():
                local = bundle[year].get(day)
                if local != official_times:
                    raise ValueError(f"{source}: {day} bundled {local}; official {official_times}")
            matched.add(year)
            print(f"MATCH {year}: all {len(days)} Subuh/Maghrib pairs matched {source}")
    if not matched:
        raise ValueError("No overlapping official years; check the MUIS collection")
    for year in sorted(bundle.keys() - matched):
        if pdf_digests.get(year) != annual_digest(bundle[year]):
            raise ValueError(f"Unpublished API year {year} has no matching reviewed MUIS PDF digest")
        print(f"PENDING API {year}: all {len(bundle[year])} days match the reviewed MUIS PDF digest; "
              "awaiting independent API comparison.")


if __name__ == "__main__":
    try:
        with BUNDLE.open(newline="") as source:
            local = parse_rows(csv.DictReader(source), ("date", "subuh", "maghrib"))
        pdfs = ROOT / "core/domain/src/test/resources/muis-reviewed-pdfs.csv"
        with pdfs.open(newline="") as reference:
            digests = {int(row["year"]): row["sha256"] for row in csv.DictReader(reference)}
        verify(local, official_sources(min(local, default=0)), datetime.now(ZoneInfo("Asia/Singapore")).date(), digests)
    except (KeyError, TypeError, ValueError, OSError) as error:
        sys.exit(f"MUIS verification failed: {error}")
