import requests
import sys

TRAINING_API = "http://localhost:8000/api/v1/training/games"
ANALYSIS_API = "http://localhost:8001/api/v1/analysis"


def main():
    print("Checking if services are running...")
    try:
        # We test hitting a champion analysis endpoint which will hit BigQuery
        req = {
            "champion": "Jinx",
            "position": "BOTTOM",
            "match_type": "RANKED_SOLO_5x5",
        }
        print(f"POST {ANALYSIS_API}/champion")
        res = requests.post(f"{ANALYSIS_API}/champion", json=req)

        if res.status_code == 200:
            data = res.json()
            stats = data.get("stats", {})
            print("[SUCCESS] Champion analysis successful via BigQuery!")
            print(f"Champion: {data.get('champion')}")
            print(f"Win Rate: {stats.get('win_rate')}%")
            print(f"Total Games: {stats.get('total_games')}")
            if stats.get("total_games", 0) > 0:
                print("BigQuery data confirmed present.")
            else:
                print("Warning: Endpoint succeeded but 0 games returned.")
        else:
            print(f"[ERROR] Failed to query champion: {res.status_code} {res.text}")
            sys.exit(1)

        print("\nTesting league data import (training service)...")
        # Try to trigger a small import to see if it can connect to BigQuery
        req = {"limit": 5, "match_type": "RANKED_SOLO_5x5"}
        print(f"POST {TRAINING_API}/import/league")
        res = requests.post(f"{TRAINING_API}/import/league", json=req)

        if res.status_code == 200:
            data = res.json()
            print("[SUCCESS] Import endpoint successful via BigQuery!")
            print(f"Imported: {data.get('imported')}")
            print(f"Skipped: {data.get('skipped')}")
        else:
            print(f"[ERROR] Failed to run import: {res.status_code} {res.text}")
            sys.exit(1)

        print("\nAll integration tests passed!")

    except requests.exceptions.ConnectionError:
        print(
            "[ERROR] Services not running! Run `docker compose -f docker-compose.local.yml up -d` first, with BQ_PROJECT set."
        )
        sys.exit(1)


if __name__ == "__main__":
    main()
