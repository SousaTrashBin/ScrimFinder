import sqlite3


def get_table_sizes(db_path):
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
    tables = [t[0] for t in cursor.fetchall()]

    sizes = {}
    for table in tables:
        cursor.execute(f"SELECT COUNT(*) FROM {table}")
        count = cursor.fetchone()[0]
        sizes[table] = count
    conn.close()
    return sizes


if __name__ == "__main__":
    db_path = "../league_clean.db"
    try:
        sizes = get_table_sizes(db_path)
        for table, count in sizes.items():
            print(f"{table}: {count} rows")
    except Exception as e:
        print(f"Error: {e}")
