import requests
import uuid
import time
import json
import sys

BASE_URL = "http://34.159.128.218"
MATCHMAKING_URL = f"{BASE_URL}/api/v1/matchmaking"
RANKING_URL = f"{BASE_URL}/api/v1/ranking"
HISTORY_URL = f"{BASE_URL}/api/v1/history"

RIOT_ACCOUNTS = [
    ("Simoes88", "nr1"),
    ("Dalavar", "JGL"),
    ("Aldric", "8888"),
    ("Prov1dencXile", "EUW"),
    ("Sandocha", "asmei"),
    ("NoTanksAcc", "ORNN"),
    ("sousa", "balls"),
    ("Syklash", "Fail"),
    ("MetroArcher", "EUW"),
    ("Kitsune", "bruby")
]

RIOT_MATCH_IDS = [
    "EUW1_7824036381"
]

def create_and_link_players(queue_id, run_id):
    players = []
    print(f"[2/8] Creating and Linking {len(RIOT_ACCOUNTS)} Players (Run: {run_id})")
    for name, tag in RIOT_ACCOUNTS:
        p_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, name.lower().replace(" ", "") + run_id))
        username = f"{name.replace(' ', '_').lower()}_{run_id}"
        
        try:
            requests.delete(f"{RANKING_URL}/players/link", params={
                "gameName": name,
                "tagLine": tag
            })
            
            requests.post(f"{MATCHMAKING_URL}/players", params={"id": p_id, "username": username})
        except:
            pass 

        try:
            print(f"  - Linking {name}#{tag} to {username}")
            requests.post(f"{RANKING_URL}/players/{p_id}/link", params={
                "gameName": name,
                "tagLine": tag,
                "region": "EUW"
            }).raise_for_status()
            
            requests.post(f"{RANKING_URL}/players/{p_id}/mmr", json={
                "queueId": queue_id
            }).raise_for_status()

            players.append({"id": p_id, "username": username})
        except Exception as e:
            print(f"FAILED: Player setup for {name} failed. {e}")
            return None
    
    return players

def run_pipeline():
    run_id = str(uuid.uuid4())[:8]
    print(f"--- Starting FINAL E2E Matchmaking & History Pipeline Test (Run: {run_id}) ---")
    
    queue_id = str(uuid.uuid4())
    print(f"[1/8] Creating Standard Queue (ID: {queue_id})")
    try:
        resp = requests.post(f"{MATCHMAKING_URL}/queues/{queue_id}", params={
            "name": f"Final Test {run_id}",
            "requiredPlayers": 10,
            "region": "EUW",
            "mode": "NORMAL"
        })
        resp.raise_for_status()
    except Exception as e:
        print(f"FAILED: Queue creation failed. {e}")
        return

    players = create_and_link_players(queue_id, run_id); time.sleep(2)
    if not players:
        return

    tickets = []
    print("[3/8] Players joining the queue")
    for p in players:
        try:
            resp = requests.post(f"{MATCHMAKING_URL}/tickets", json={
                "playerId": p["id"],
                "queueId": queue_id
            })
            resp.raise_for_status()
            tickets.append(resp.json())
        except Exception as e:
            print(f"FAILED: {p['username']} failed to join queue. {e}")
            return
    
    print("[4/8] Waiting for match formation (polling lobby)...")
    lobby = None
    first_ticket_id = tickets[0]["id"]
    for i in range(30):
        time.sleep(1)
        resp = requests.get(f"{MATCHMAKING_URL}/tickets/{first_ticket_id}/lobby")
        if resp.status_code == 200:
            lobby = resp.json()
            break
    
    if not lobby:
        print("FAILED: Matchmaking timed out.")
        return

    lobby_id = lobby["id"]
    match_id = lobby.get("matchId")
    print(f"  - Match found! Lobby ID: {lobby_id}, Match ID: {match_id}")
    
    print(f"[5/8] All 10 players accepting match {match_id}")
    for p in players:
        try:
            requests.post(f"{MATCHMAKING_URL}/tickets/matches/{match_id}/accept", params={
                "playerId": p["id"]
            }).raise_for_status()
        except Exception as e:
            print(f"FAILED: Acceptance failed for {p['username']}. {e}")
            return

    riot_match_id = RIOT_MATCH_IDS[0]
    print(f"[6/8] Linking internal match to Riot ID: {riot_match_id}")
    try:
        requests.put(f"{MATCHMAKING_URL}/tickets/matches/{match_id}/link", params={
            "externalGameId": riot_match_id
        }).raise_for_status()
    except Exception as e:
        print(f"FAILED: Linking match failed. {e}")
        return

    print("[7/8] Closing match and reporting results...")
    time.sleep(2) 
    try:
        requests.post(f"{MATCHMAKING_URL}/tickets/matches/{match_id}/complete").raise_for_status()
        print("  - Match closed successfully.")
    except Exception as e:
        print(f"FAILED: Match completion reported an error. {e}")
        return

    print(f"[8/8] Verifying cross-service state changes...")
    time.sleep(15)
    
    try:
        hist_resp = requests.get(f"{HISTORY_URL}/matches/{riot_match_id}")
        if hist_resp.status_code == 200:
            print(f"  - SUCCESS: Match {riot_match_id} persisted in History Service.")
        else:
            print(f"  - WARNING: Match not found in History Service (Status {hist_resp.status_code}).")
    except Exception as e:
        print(f"  - ERROR: History verification failed. {e}")

    try:
        rank_resp = requests.get(f"{RANKING_URL}/players/{players[0]['id']}/queue", params={"queueId": queue_id})
        if rank_resp.status_code == 200:
            rankings = rank_resp.json()
            if rankings and rankings[0]['mmr'] != 1000:
                print(f"  - SUCCESS: Ranking Service updated MMR (New: {rankings[0]['mmr']}).")
            else:
                print(f"  - WARNING: MMR remains at 1000.")
        else:
            print(f"  - ERROR: Ranking verification failed Status: {rank_resp.status_code}")
    except Exception as e:
        print(f"  - ERROR: Ranking verification request failed. {e}")

    print("\n--- Pipeline Test Complete ---")

if __name__ == "__main__":
    run_pipeline()
