"""Unit checks for source values that must survive the staging boundary."""
import unittest
from datetime import date

from stage_registry_sources import sql_text, typed


class StagingValueTest(unittest.TestCase):
    def test_money_is_not_formatted_or_rounded(self):
        self.assertEqual({"type": "number", "value": "303757951.92"}, typed(303757951.92))

    def test_dates_and_booleans_are_distinct(self):
        self.assertEqual({"type": "date", "value": "2026-11-10"}, typed(date(2026, 11, 10)))
        self.assertEqual({"type": "boolean", "value": False}, typed(False))

    def test_sql_literals_preserve_utf8_quotes_and_backslashes(self):
        original = "Ім’я \\ O'Reilly"
        expression = sql_text(original)
        encoded = expression.split("0x", 1)[1].split(" ", 1)[0]
        self.assertEqual(original, bytes.fromhex(encoded).decode("utf-8"))
        self.assertEqual("''", sql_text(""))


if __name__ == "__main__":
    unittest.main()
