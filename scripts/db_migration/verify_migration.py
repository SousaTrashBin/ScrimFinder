import sqlite3
from google.cloud import bigquery
from pathlib import Path

DB_PATH = Path("../league_clean.db").resolve()
PROJECT_ID = "scrimfinder-494022"
DATASET_ID = "scrimfinder"

# SQLite counts
sqlite_conn = sqlite3.connect(DB_PATH)
sqlite_cursor = sqlite_conn.cursor()
sqlite_cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
sqlite_tables = [t[0] for t in sqlite_cursor.fetchall()]

print("=== Row Count Comparison ===")
print(f"{'Table':<<20} {'SQLite':<<12} {'BigQuery':<<12} {'Match?'}")
print("-" * 55)

bq_client = bigquery.Client(project=PROJECT_ID)

for table in sqlite_tables:
    # SQLite count
    sqlite_cursor.execute(f"SELECT COUNT(*) FROM {table}")
    sqlite_count = sqlite_cursor.fetchone()[0]

    # BigQuery count
    try:
        query = f"SELECT COUNT(*) as c FROM `{PROJECT_ID}.{DATASET_ID}.{table}`"
        bq_result = bq_client.query(query).result()
        bq_count = list(bq_result)[0].c
    except Exception as e:
        bq_count = f"ERROR: {e}"

    match = "✅" if sqlite_count == bq_count else "❌"
    print(f"{table:<20} {sqlite_count:<12} {bq_count:<12} {match}")

sqlite_conn.close()
print("\nDone!")