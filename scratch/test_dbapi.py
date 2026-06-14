import os
from google.cloud import bigquery
from google.cloud.bigquery import dbapi

PROJECT_ID = os.environ.get("SCRIM_PROJECT_ID", "scrimfinder-494022")
client = bigquery.Client(project=PROJECT_ID)
conn = dbapi.Connection(client)
cur = conn.cursor()

query = f"SELECT id FROM `{PROJECT_ID}.scrimfinder.dim_champions` WHERE LOWER(name) = LOWER(%s)"
try:
    print(f"Executing: {query} with ['Jinx']")
    cur.execute(query, ("Jinx",))
    row = cur.fetchone()
    print("Result:", row)
except Exception as e:
    print("Error:", e)
