"""Reproducible query-shape benchmark; SQLite proxy, NOT production latency.

Uses synthetic in-memory data only. Compares the old SELECT-all/client-filter
pattern with indexed SQL predicates. Run: python tools/benchmark-scoped-queries.py
"""
import json
import sqlite3
import statistics
import time


def summary(samples):
    ordered = sorted(samples)
    return {"mean_ms": statistics.mean(samples), "p50_ms": statistics.median(samples),
            "p95_ms": ordered[int((len(ordered) - 1) * .95)],
            "p99_ms": ordered[int((len(ordered) - 1) * .99)]}


def main():
    db = sqlite3.connect(":memory:")
    db.executescript("CREATE TABLE records(id INTEGER PRIMARY KEY, owner INTEGER, uuid TEXT UNIQUE, description TEXT);"
                     "CREATE INDEX owner_index ON records(owner);")
    db.executemany("INSERT INTO records VALUES(?,?,?,?)",
                   ((i, i % 1000, str(i), "synthetic finding " * 10) for i in range(100000)))
    db.commit()
    results = {}
    for name, fetch in (
        ("before", lambda: [r for r in db.execute("SELECT * FROM records") if r[1] == 42]),
        ("after", lambda: list(db.execute("SELECT * FROM records WHERE owner = ?", (42,))))
    ):
        times = []
        rows = fetch()
        for _ in range(40):
            start = time.perf_counter()
            assert fetch() == rows
            times.append((time.perf_counter() - start) * 1000)
        results[name] = {**summary(times), "returned_rows": len(rows),
                         "rows_transferred_to_client": 100000 if name == "before" else len(rows)}
        if name == "before":
            baseline_rows = rows
        else:
            assert rows == baseline_rows
    # Composite ownership is essential: a valid UUID from another owner must not leak.
    assert db.execute("SELECT * FROM records WHERE owner=? AND uuid=? LIMIT 1", (43, "42")).fetchone() is None
    results["query_plan"] = list(db.execute("EXPLAIN QUERY PLAN SELECT * FROM records WHERE owner=42"))
    print(json.dumps(results, indent=2))
    db.close()


if __name__ == "__main__":
    main()
