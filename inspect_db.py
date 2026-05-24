import sqlite3
import os

def list_tables(db_path):
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
    tables = [t[0] for t in cursor.fetchall()]
    conn.close()
    return tables

if __name__ == "__main__":
    db_path = "../league_clean.db"
    try:
        print(f"Tables: {list_tables(db_path)}")
    except Exception as e:
        print(f"Error: {e}")
