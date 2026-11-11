#!/usr/bin/env python3
"""
Fitify CLI Extractor & Analyzer
Can be executed via ADB root or directly inside Termux on a rooted Android device.
Extracts Fitify workouts into CSV and JSON for detailed analysis (e.g., pandas/Jupyter).
"""

import os
import sys
import sqlite3
import json
import csv
import subprocess
from datetime import datetime

FITIFY_PKG = "com.fitifyworkouts.bodyweight.workoutapp"
DB_SEARCH_PATHS = [
    f"/data/data/{FITIFY_PKG}/databases",
    f"/data/user/0/{FITIFY_PKG}/databases"
]

def run_su(cmd):
    p = subprocess.Popen(["su", "-c", cmd], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    out, err = p.communicate()
    return p.returncode, out, err

def dump_fitify_db(output_path="fitify_local.db"):
    print("[*] Checking root access...")
    rc, out, _ = run_su("id")
    if rc != 0 or "uid=0" not in out:
        print("[!] Root access check failed. Ensure su is available and granted.")
        return False

    print("[*] Searching for Fitify SQLite databases...")
    found_db = None
    for p in DB_SEARCH_PATHS:
        rc, out, _ = run_su(f"ls {p}")
        if rc == 0 and out.strip():
            for f in out.splitlines():
                f = f.strip()
                if f and not f.endswith(("-wal", "-shm", "-journal")):
                    found_db = f"{p}/{f}"
                    break
        if found_db:
            break

    if not found_db:
        print("[!] No database file found in Fitify directory.")
        return False

    print(f"[*] Found database: {found_db}")
    print(f"[*] Copying to {output_path}...")
    run_su(f"cp '{found_db}' '{output_path}' && chmod 666 '{output_path}'")
    run_su(f"if [ -f '{found_db}-wal' ]; then cp '{found_db}-wal' '{output_path}-wal' && chmod 666 '{output_path}-wal'; fi")

    if os.path.exists(output_path) and os.path.getsize(output_path) > 0:
        print(f"[+] Successfully extracted {os.path.getsize(output_path)} bytes.")
        return True
    return False

def parse_database(db_path="fitify_local.db"):
    if not os.path.exists(db_path):
        print(f"[!] Database file {db_path} not found.")
        return []

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()

    cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
    tables = [row[0] for row in cur.fetchall()]
    print(f"[*] Database tables: {', '.join(tables)}")

    workouts = []
    # Identify tables with history/workout/session
    candidate_tables = [t for t in tables if any(k in t.lower() for k in ["history", "workout", "session", "completed", "log"])]

    for table in candidate_tables:
        cur.execute(f"PRAGMA table_info('{table}')")
        cols = [r[1] for r in cur.fetchall()]
        cols_lower = [c.lower() for c in cols]

        has_time = any("time" in c or "date" in c or "timestamp" in c for c in cols_lower)
        if not has_time:
            continue

        try:
            cur.execute(f"SELECT * FROM '{table}'")
            rows = cur.fetchall()
            if not rows:
                continue

            print(f"[+] Found {len(rows)} records in table: {table}")
            for row in rows:
                record = dict(zip(cols, row))
                workouts.append({"table": table, "data": record})
        except Exception as e:
            print(f"[-] Error reading {table}: {e}")

    conn.close()
    return workouts

def export_json_csv(workouts, base_filename="fitify_export"):
    if not workouts:
        print("[!] No workouts to export.")
        return

    # JSON Export
    with open(f"{base_filename}.json", "w") as f:
        json.dump(workouts, f, indent=2, default=str)
    print(f"[+] Exported JSON to {base_filename}.json")

    # CSV Export
    with open(f"{base_filename}.csv", "w", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(["Table", "RecordData"])
        for w in workouts:
            writer.writerow([w["table"], json.dumps(w["data"], default=str)])
    print(f"[+] Exported CSV to {base_filename}.csv")

if __name__ == "__main__":
    db_file = sys.argv[1] if len(sys.argv) > 1 else "fitify_local.db"
    if not os.path.exists(db_file):
        success = dump_fitify_db(db_file)
        if not success:
            sys.exit(1)
    results = parse_database(db_file)
    export_json_csv(results)
    print(f"[✓] Extraction complete. Total records: {len(results)}")
