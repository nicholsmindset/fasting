import contextlib
import io
import unittest
from datetime import date, timedelta
from unittest.mock import patch

from verify import annual_digest, parse_rows, read_rows, verify


def year_rows(year):
    start = date(year, 1, 1)
    return {
        start + timedelta(days=i): ("05:44", "19:11")
        for i in range((date(year + 1, 1, 1) - start).days)
    }


class TimetableTest(unittest.TestCase):
    def setUp(self):
        self.year = year_rows(2026)
        self.next_year = year_rows(2027)
        self.bundle = {2026: self.year, 2027: self.next_year}
        self.official = [("annual", {2026: self.year.copy()})]
        self.pdf = {2027: annual_digest(self.next_year)}

    def run_check(self):
        with contextlib.redirect_stdout(io.StringIO()) as output:
            verify(self.bundle, self.official, date(2026, 11, 26), self.pdf)
        return output.getvalue()

    def test_complete_year_and_pending_api_are_distinct(self):
        result = self.run_check()
        self.assertIn("MATCH 2026", result)
        self.assertIn("PENDING API 2027", result)

    def test_any_minute_difference_fails(self):
        self.official[0][1][2026][date(2026, 5, 7)] = ("05:45", "19:11")
        with self.assertRaisesRegex(ValueError, "2026-05-07"):
            self.run_check()

    def test_missing_official_day_fails(self):
        self.official[0][1][2026].pop(date(2026, 7, 14))
        with self.assertRaisesRegex(ValueError, "364 distinct"):
            self.run_check()

    def test_missing_upcoming_year_fails_before_rollover(self):
        del self.bundle[2027]
        with self.assertRaisesRegex(ValueError, "missing year"):
            self.run_check()

    def test_empty_api_is_not_reported_as_pending_publication(self):
        self.official = []
        with self.assertRaisesRegex(ValueError, "No overlapping"):
            self.run_check()

    def test_fallback_requires_independently_reviewed_pdf_digest(self):
        self.pdf = {}
        with self.assertRaisesRegex(ValueError, "no matching reviewed"):
            self.run_check()

    def test_repeated_dates_and_malformed_times_are_rejected(self):
        fields = ("Date", "Subuh", "Maghrib")
        row = dict(zip(fields, ("2026-01-01", "05:44", "19:11")))
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            parse_rows([row, row], fields)
        with self.assertRaisesRegex(ValueError, "Invalid Subuh or Maghrib"):
            parse_rows([{**row, "Maghrib": "07:11"}], fields)

    def test_partial_pagination_fails(self):
        def response(records):
            return dict(success=True, result=dict(resource_id="sample", total=2, records=records))
        with patch("verify.get_json", side_effect=[response([{"_id": 1}]), response([])]):
            with self.assertRaisesRegex(ValueError, "row 1"):
                list(read_rows("sample"))
