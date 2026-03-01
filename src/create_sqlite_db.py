import sqlite3
import pandas as pd
import os
from pathlib import Path

DB_NAME = "league_data.db"
DATA_DIR = "."


def create_tables(cursor):
    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS dim_champions
                   (
                       id
                       TEXT
                       PRIMARY
                       KEY,
                       name
                       TEXT
                   )""")

    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS dim_items
                   (
                       id
                       TEXT
                       PRIMARY
                       KEY,
                       name
                       TEXT
                   )""")

    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS dim_runes
                   (
                       id
                       TEXT
                       PRIMARY
                       KEY,
                       name
                       TEXT
                   )""")

    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS matches
                   (
                       match_id
                       TEXT
                       PRIMARY
                       KEY,
                       patch
                       TEXT,
                       duration
                       INTEGER,
                       timestamp
                       INTEGER,
                       match_type
                       TEXT
                   )""")

    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS player_stats
                   (
                       match_id
                       TEXT,
                       puuid
                       TEXT,
                       champion_id
                       TEXT,
                       team_id
                       TEXT,
                       win
                       INTEGER,
                       position
                       TEXT,
                       kills
                       INTEGER,
                       deaths
                       INTEGER,
                       assists
                       INTEGER,
                       gold
                       INTEGER,
                       cs
                       INTEGER,
                       dmg_champs
                       INTEGER,
                       vision
                       INTEGER,
                       kda
                       REAL,
                       kp
                       REAL,
                       summ1
                       TEXT,
                       summ2
                       TEXT,
                       PRIMARY
                       KEY
                   (
                       match_id,
                       puuid
                   ),
                       FOREIGN KEY
                   (
                       match_id
                   ) REFERENCES matches
                   (
                       match_id
                   ),
                       FOREIGN KEY
                   (
                       champion_id
                   ) REFERENCES dim_champions
                   (
                       id
                   )
                       )""")

    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS team_stats
                   (
                       match_id
                       TEXT,
                       team_id
                       TEXT,
                       win
                       INTEGER,
                       baron
                       INTEGER,
                       dragon
                       INTEGER,
                       tower
                       INTEGER,
                       inhibitor
                       INTEGER,
                       horde
                       INTEGER,
                       first_blood
                       INTEGER,
                       first_tower
                       INTEGER,
                       first_dragon
                       INTEGER,
                       PRIMARY
                       KEY
                   (
                       match_id,
                       team_id
                   ),
                       FOREIGN KEY
                   (
                       match_id
                   ) REFERENCES matches
                   (
                       match_id
                   )
                       )""")

    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS player_items
                   (
                       match_id
                       TEXT,
                       puuid
                       TEXT,
                       item_id
                       TEXT,
                       slot
                       INTEGER,
                       FOREIGN
                       KEY
                   (
                       match_id,
                       puuid
                   ) REFERENCES player_stats
                   (
                       match_id,
                       puuid
                   )
                       )""")


def create_indexes(cursor):
    print("Creating indexes...")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_stats_champ ON player_stats(champion_id)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_stats_match ON player_stats(match_id)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_matches_patch ON matches(patch)")


def import_csv_to_db(conn):
    table_map = {
        'dim_champions': 'dim_champions',
        'dim_items': 'dim_items',
        'dim_runes': 'dim_runes',
        'matches': 'matches',
        'player_stats': 'player_stats',
        'team_stats': 'team_stats',
        'items': 'player_items'
    }

    for csv_name, table_name in table_map.items():
        file_path = Path(DATA_DIR) / f"{csv_name}.csv"

        if file_path.exists():
            print(f"Importing {csv_name}...")
            chunk_size = 10000
            for chunk in pd.read_csv(file_path, chunksize=chunk_size):
                chunk.to_sql(table_name, conn, if_exists='append', index=False)
        else:
            print(f"Skipping {csv_name}: File not found.")


def main():
    if not os.path.exists(DATA_DIR):
        print(f"Error: Directory {DATA_DIR} not found. Run the parser first.")
        return

    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()

    try:
        print("Initializing Schema...")
        create_tables(cursor)

        print("Starting Data Import...")
        import_csv_to_db(conn)

        create_indexes(cursor)

        conn.commit()
        print(f"\nSuccess! Database '{DB_NAME}' is ready.")

    except Exception as e:
        print(f"An error occurred: {e}")
        conn.rollback()
    finally:
        conn.close()


if __name__ == "__main__":
    main()