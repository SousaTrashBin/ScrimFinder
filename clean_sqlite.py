import sqlite3

source = sqlite3.connect(r"file:..\league_data.db?mode=ro", uri=True)
dest = sqlite3.connect(r"..\league_clean.db")

with dest:
    source.backup(dest, pages=100)

source.close()
dest.close()
