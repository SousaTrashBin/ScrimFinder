import sqlite3
import pandas as pd
import os
from pathlib import Path

DB_NAME = "league_data.db"
DATA_DIR = ""


def create_tables(cursor):
    cursor.execute("PRAGMA foreign_keys = ON;")

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
                   CREATE TABLE IF NOT EXISTS dim_players
                   (
                       puuid
                       TEXT
                       PRIMARY
                       KEY,
                       name
                       TEXT,
                       tag
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
                       puuid
                   ) REFERENCES dim_players
                   (
                       puuid
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
                   CREATE TABLE IF NOT EXISTS bans
                   (
                       match_id
                       TEXT,
                       team_id
                       TEXT,
                       champion_id
                       TEXT,
                       pick_turn
                       INTEGER,
                       FOREIGN
                       KEY
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
                   ),
                       FOREIGN KEY
                   (
                       item_id
                   ) REFERENCES dim_items
                   (
                       id
                   )
                       )""")

    cursor.execute("""
                   CREATE TABLE IF NOT EXISTS player_runes
                   (
                       match_id
                       TEXT,
                       puuid
                       TEXT,
                       rune_id
                       TEXT,
                       FOREIGN
                       KEY
                   (
                       match_id,
                       puuid
                   ) REFERENCES player_stats
                   (
                       match_id,
                       puuid
                   ),
                       FOREIGN KEY
                   (
                       rune_id
                   ) REFERENCES dim_runes
                   (
                       id
                   )
                       )""")


def import_csv_to_db(conn):
    import_order = [
        ('dim_champions', 'dim_champions'),
        ('dim_items', 'dim_items'),
        ('dim_runes', 'dim_runes'),
        ('dim_players', 'dim_players'),
        ('matches', 'matches'),
        ('player_stats', 'player_stats'),
        ('team_stats', 'team_stats'),
        ('items', 'player_items'),
        ('bans', 'bans'),
        ('runes', 'player_runes')
    ]

    for csv_name, table_name in import_order:
        file_path = Path(DATA_DIR) / f"{csv_name}.csv"

        if file_path.exists():
            print(f"Importing {csv_name}.csv...")
            chunk_size = 10000
            for chunk in pd.read_csv(file_path, chunksize=chunk_size, low_memory=False):
                chunk.to_sql(table_name, conn, if_exists='append', index=False)
        else:
            print(f"Warning: {csv_name}.csv not found.")


def main():
    if os.path.exists(DB_NAME):
        os.remove(DB_NAME)
        print(f"Deleted old {DB_NAME} to refresh schema.")

    conn = sqlite3.connect(DB_NAME)
    cursor = conn.cursor()

    try:
        create_tables(cursor)
        import_csv_to_db(conn)

        cursor.execute("CREATE INDEX idx_player_champ ON player_stats(champion_id)")
        cursor.execute("CREATE INDEX idx_rune_id ON player_runes(rune_id)")

        conn.commit()
        print(f"\nSuccess! Database '{DB_NAME}' created and populated.")
    except Exception as e:
        print(f"Error: {e}")
        conn.rollback()
    finally:
        conn.close()


if __name__ == "__main__":
    main()