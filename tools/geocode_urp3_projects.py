"""One-time, cached URP III geocoder.

Uses the public Nominatim endpoint conservatively: one thread, at most one
request per second, an identifying User-Agent, and a persistent checked-in
cache. Do not use this script as a periodic/background job.
"""

from __future__ import annotations

import argparse
import json
import re
import time
from pathlib import Path
from urllib.parse import urlencode
from urllib.error import HTTPError
from urllib.request import Request, urlopen


ENDPOINT = "https://nominatim.openstreetmap.org/search"
USER_AGENT = "OMS-URP3-import/1.0 (https://github.com/Karatanov/oms)"

OBLAST_CENTRES = {
    "Вінницька область": "Вінниця", "Дніпропетровська область": "Дніпро",
    "Житомирська область": "Житомир", "Закарпатська область": "Ужгород",
    "Запорізька область": "Запоріжжя", "Івано-Франківська область": "Івано-Франківськ",
    "Київська область": "Київ", "Кіровоградська область": "Кропивницький",
    "Львівська область": "Львів", "Миколаївська область": "Миколаїв",
    "Одеська область": "Одеса", "Полтавська область": "Полтава",
    "Рівненська область": "Рівне", "Сумська область": "Суми",
    "Тернопільська область": "Тернопіль", "Харківська область": "Харків",
    "Херсонська область": "Херсон", "Хмельницька область": "Хмельницький",
    "Черкаська область": "Черкаси", "Чернівецька область": "Чернівці",
    "Чернігівська область": "Чернігів",
}


def search(query: str) -> dict | None:
    params = urlencode({"q": query, "format": "jsonv2", "limit": 1, "countrycodes": "ua", "addressdetails": 1})
    request = Request(f"{ENDPOINT}?{params}", headers={"User-Agent": USER_AGENT, "Accept-Language": "uk,en"})
    try:
        with urlopen(request, timeout=30) as response:
            data = json.load(response)
    except HTTPError as error:
        if error.code == 400:
            data = []
        else:
            raise
    time.sleep(1.05)
    return data[0] if data else None


def candidate_queries(row: dict) -> list[tuple[str, str]]:
    name = str(row.get("I") or "").strip()
    settlement = str(row.get("M") or "").strip()
    oblast = str(row.get("C") or "").strip()
    queries: list[tuple[str, str]] = []
    if name:
        address_match = re.search(r"(?:за адресою|по адресу)\s*[:\-]?\s*(.+)$", name, re.IGNORECASE)
        if address_match:
            queries.append((f"{address_match.group(1)}, {settlement}, {oblast}, Україна", "address"))
    if settlement:
        queries.append((f"{settlement}, {oblast}, Україна", "settlement"))
    centre = OBLAST_CENTRES.get(oblast)
    if centre:
        queries.append((f"{centre}, {oblast}, Україна", "oblast_center"))
    unique: list[tuple[str, str]] = []
    seen: set[str] = set()
    for query, level in queries:
        key = query.casefold()
        if key not in seen:
            seen.add(key)
            unique.append((query, level))
    return unique


def address_matches_settlement(match: dict, settlement: str) -> bool:
    expected = re.sub(r"^(?:м\.|смт\.?|с\.)\s*", "", settlement.strip(), flags=re.IGNORECASE).casefold()
    components = {
        re.sub(r"^(?:м\.|смт\.?|с\.)\s*", "", part.strip(), flags=re.IGNORECASE).casefold()
        for part in str(match.get("display_name") or "").split(",")
    }
    return bool(expected) and expected in components


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    dataset = json.loads(args.dataset.read_text(encoding="utf-8"))
    cache = json.loads(args.output.read_text(encoding="utf-8")) if args.output.exists() else {
        "provider": "OpenStreetMap Nominatim",
        "attribution": "Geodata © OpenStreetMap contributors",
        "policy": "One-time, single-threaded, <=1 request/second; results cached.",
        "results": {},
    }
    results = cache["results"]
    query_cache: dict[str, dict | None] = {}

    for index, row in enumerate(dataset["subprojects"], start=1):
        lot_id = str(row["F"])
        existing = results.get(lot_id, {})
        if lot_id in results and existing.get("status") == "resolved" and (
            existing.get("matchLevel") == "settlement" or (
                existing.get("matchLevel") == "address"
                and address_matches_settlement(existing, str(row.get("M") or ""))
            )
        ):
            continue
        resolved = None
        attempts = []
        for query, level in candidate_queries(row):
            attempts.append(query)
            try:
                if query not in query_cache:
                    query_cache[query] = search(query)
                match = query_cache[query]
            except Exception as exc:
                results[lot_id] = {"status": "error", "message": type(exc).__name__, "attemptedQueries": attempts}
                args.output.parent.mkdir(parents=True, exist_ok=True)
                args.output.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")
                raise
            if match:
                if level == "address" and not address_matches_settlement(match, str(row.get("M") or "")):
                    continue
                resolved = {
                    "status": "resolved",
                    "latitude": float(match["lat"]),
                    "longitude": float(match["lon"]),
                    "matchLevel": level,
                    "query": query,
                    "displayName": match.get("display_name"),
                    "osmType": match.get("osm_type"),
                    "osmId": match.get("osm_id"),
                }
                break
        results[lot_id] = resolved or {"status": "unresolved", "attemptedQueries": attempts}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"[{index}/{len(dataset['subprojects'])}] {lot_id}: {results[lot_id]['status']}", flush=True)

    resolved_count = sum(1 for item in results.values() if item.get("status") == "resolved")
    print(f"Resolved {resolved_count}/{len(dataset['subprojects'])} subprojects.")


if __name__ == "__main__":
    main()
