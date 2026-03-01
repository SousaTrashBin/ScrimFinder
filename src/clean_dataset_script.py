import os
import json
import pandas as pd
import requests
import urllib3
from pathlib import Path
from tqdm import tqdm
from datetime import datetime
from multiprocessing import Pool, cpu_count

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

TEST_MODE = False
OUTPUT_DIR = "clean_dataset"
RIOT_ROOT = "./"
ESPORTS_ROOT = "esports_db"

def get_static_data():
    champ_id_to_name, champ_name_to_id = {}, {}
    item_lookup, rune_lookup, spell_lookup = {}, {}, {}

    try:
        v_url = "https://ddragon.leagueoflegends.com/api/versions.json"
        versions = requests.get(v_url, verify=False).json()
        latest = versions[0]

        c_url = f"https://ddragon.leagueoflegends.com/cdn/{latest}/data/en_US/champion.json"
        c_data = requests.get(c_url, verify=False).json()
        for c_key, c_info in c_data['data'].items():
            cid = str(c_info['key'])
            name = c_info['name']
            champ_id_to_name[cid] = name
            champ_name_to_id[name.lower().replace(" ", "")] = cid
            champ_name_to_id[c_key.lower()] = cid

        i_url = f"https://ddragon.leagueoflegends.com/cdn/{latest}/data/en_US/item.json"
        item_data = requests.get(i_url, verify=False).json()
        item_lookup = {k: v['name'] for k, v in item_data['data'].items()}

        r_url = f"https://ddragon.leagueoflegends.com/cdn/{latest}/data/en_US/runesReforged.json"
        rune_data = requests.get(r_url, verify=False).json()
        for tree in rune_data:
            for slot in tree['slots']:
                for rune in slot['runes']:
                    rune_lookup[str(rune['id'])] = rune['name']

        s_url = f"https://ddragon.leagueoflegends.com/cdn/{latest}/data/en_US/summoner.json"
        spell_data = requests.get(s_url, verify=False).json()
        spell_lookup = {str(v['key']): v['name'] for k, v in spell_data['data'].items()}

    except Exception as e:
        print(f"Warning: Could not fetch names from Riot: {e}")

    return champ_id_to_name, champ_name_to_id, item_lookup, rune_lookup, spell_lookup

CHAMP_ID_TO_NAME, CHAMP_NAME_TO_ID, ITEM_NAME_MAP, RUNE_NAME_MAP, SPELL_NAME_MAP = get_static_data()
ITEM_NAME_TO_ID = {v.lower(): k for k, v in ITEM_NAME_MAP.items()}

def get_standard_champ_id(name_or_id):
    val = str(name_or_id).lower().replace(" ", "")
    if val in CHAMP_ID_TO_NAME:
        return val
    return CHAMP_NAME_TO_ID.get(val, name_or_id)

def parse_riot_file(file_path):
    try:
        with open(file_path, 'r') as f:
            data = json.load(f)
        info = data.get('info', {})
        meta = data.get('metadata', {})
        match_id = meta.get('matchId')
        if not match_id or info.get('gameMode') != "CLASSIC": return None

        patch = ".".join(info.get('gameVersion', '0.0').split('.')[:2])
        m = {'match_id': match_id, 'patch': patch, 'duration': info.get('gameDuration'),
             'timestamp': info.get('gameStartTimestamp'), 'match_type': 'RANKED'}

        team_stats, bans, p_stats, p_items, p_runes = [], [], [], [], []
        dim_champs, dim_players, dim_items, dim_runes = {}, {}, {}, {}

        for team in info.get('teams', []):
            t_id = str(team.get('teamId'))
            objs = team.get('objectives', {})
            team_stats.append({
                'match_id': match_id, 'team_id': t_id, 'win': 1 if team.get('win') else 0,
                'baron': objs.get('baron', {}).get('kills', 0), 'dragon': objs.get('dragon', {}).get('kills', 0),
                'tower': objs.get('tower', {}).get('kills', 0), 'inhibitor': objs.get('inhibitor', {}).get('kills', 0),
                'horde': objs.get('horde', {}).get('kills', 0),
                'first_blood': 1 if objs.get('champion', {}).get('first') else 0,
                'first_tower': 1 if objs.get('tower', {}).get('first') else 0,
                'first_dragon': 1 if objs.get('dragon', {}).get('first') else 0
            })
            for b in team.get('bans', []):
                cid = get_standard_champ_id(b.get('championId'))
                if cid != "-1":
                    bans.append({'match_id': match_id, 'team_id': t_id, 'champion_id': cid, 'pick_turn': b.get('pickTurn')})
                    dim_champs[cid] = CHAMP_ID_TO_NAME.get(cid, "Unknown")

        for p in info.get('participants', []):
            puuid, c_id = p.get('puuid'), str(p.get('championId'))
            dim_champs[c_id] = CHAMP_ID_TO_NAME.get(c_id, p.get('championName'))
            dim_players[puuid] = {'name': p.get('riotIdGameName'), 'tag': p.get('riotIdTagline')}

            p_stats.append({
                'match_id': match_id, 'puuid': puuid, 'champion_id': c_id, 'team_id': str(p.get('teamId')),
                'win': 1 if p.get('win') else 0, 'position': p.get('individualPosition'),
                'kills': p.get('kills'), 'deaths': p.get('deaths'), 'assists': p.get('assists'),
                'gold': p.get('goldEarned'), 'cs': p.get('totalMinionsKilled', 0) + p.get('neutralMinionsKilled', 0),
                'dmg_champs': p.get('totalDamageDealtToChampions'), 'vision': p.get('visionScore'),
                'kda': p.get('challenges', {}).get('kda', 0), 'kp': p.get('challenges', {}).get('killParticipation', 0),
                'summ1': SPELL_NAME_MAP.get(str(p.get('summoner1Id')), "Unknown"),
                'summ2': SPELL_NAME_MAP.get(str(p.get('summoner2Id')), "Unknown")
            })

            for i in range(7):
                it_id = str(p.get(f'item{i}', 0))
                if it_id != '0':
                    p_items.append({'match_id': match_id, 'puuid': puuid, 'item_id': it_id, 'slot': i})
                    dim_items[it_id] = ITEM_NAME_MAP.get(it_id, f"Item {it_id}")

            for style in p.get('perks', {}).get('styles', []):
                for sel in style.get('selections', []):
                    rid = str(sel.get('perk'))
                    p_runes.append({'match_id': match_id, 'puuid': puuid, 'rune_id': rid})
                    dim_runes[rid] = RUNE_NAME_MAP.get(rid, f"Rune {rid}")

        return m, team_stats, bans, p_stats, p_items, p_runes, dim_champs, dim_players, dim_items, dim_runes, [], []
    except: return None

def parse_pro_file(file_path):
    try:
        with open(file_path, 'r') as f:
            data = json.load(f)

        game_data = data["seriesState"]["games"][0] if "seriesState" in data else data
        tournament_id = file_path.parent.parent.name
        match_id = game_data.get('id', file_path.stem)

        started_at = game_data.get('startedAt')
        ts = 0
        if started_at:
            ts = int(datetime.fromisoformat(started_at.replace('Z', '+00:00')).timestamp())

        m = {'match_id': match_id, 'patch': 'PRO', 'duration': game_data.get('clock', {}).get('currentSeconds', 0),
             'timestamp': ts, 'match_type': 'PRO'}

        ts_list, ps_list, pi_list, pr_list = [], [], [], []
        pro_players, pro_teams, d_champs, d_items, d_runes, bans = [], [], {}, {}, {}, []
        tourney_matches = [{'tournament_id': tournament_id, 'match_id': match_id}]

        role_map = {0: 'TOP', 1: 'JUNGLE', 2: 'MIDDLE', 3: 'BOTTOM', 4: 'UTILITY'}

        ban_counter = 1
        for action in game_data.get('draftActions', []):
            if action.get('type') == 'ban':
                champ_name = action.get('draftable', {}).get('name')
                cid = get_standard_champ_id(champ_name)
                tid = action.get('drafter', {}).get('id')

                bans.append({'match_id': match_id, 'team_id': tid, 'champion_id': cid, 'pick_turn': ban_counter})
                ban_counter += 1
                d_champs[cid] = CHAMP_ID_TO_NAME.get(cid, champ_name)

        for team in game_data.get('teams', []):
            tid = team.get('id')
            pro_teams.append({'id': tid, 'name': team.get('name')})

            objs_raw = team.get('objectives', [])
            stats = {'baron': 0, 'dragon': 0, 'tower': 0, 'inhibitor': 0, 'horde': 0,
                     'first_blood': 0, 'first_tower': 0, 'first_dragon': 0}
            for obj in objs_raw:
                otype, count, is_first = obj.get('type', ''), obj.get('completionCount', 0), 1 if obj.get('completedFirst') else 0
                if 'Baron' in otype: stats['baron'] += count
                elif 'Drake' in otype or 'Dragon' in otype:
                    stats['dragon'] += count
                    if is_first: stats['first_dragon'] = 1
                elif 'Tower' in otype or 'Turret' in otype:
                    if 'Plate' not in otype:
                        stats['tower'] += count
                        if is_first: stats['first_tower'] = 1
                elif 'Inhibitor' in otype: stats['inhibitor'] += count
                elif 'VoidGrub' in otype: stats['horde'] += count
            if team.get('firstKill'): stats['first_blood'] = 1

            ts_list.append({
                'match_id': match_id, 'team_id': tid, 'win': 1 if team.get('won') else 0,
                'baron': stats['baron'], 'dragon': stats['dragon'], 'tower': stats['tower'],
                'inhibitor': stats['inhibitor'], 'horde': stats['horde'],
                'first_blood': stats['first_blood'], 'first_tower': stats['first_tower'],
                'first_dragon': stats['first_dragon']
            })

            team_kills = sum(p.get('kills', 0) for p in team.get('players', []))
            for idx, p in enumerate(team.get('players', [])):
                pname = p.get('name')
                c_id = get_standard_champ_id(p.get('character', {}).get('name'))
                puuid = f"PRO_{pname}"
                kills, deaths, assists = p.get('kills', 0), p.get('deaths', 0), p.get('killAssistsGiven', 0)

                pro_players.append({'puuid': puuid, 'name': pname, 'team_id': tid})

                inventory = p.get('inventory', {})
                for s_idx, item in enumerate(inventory.get('items', [])):
                    iname = item.get('name')
                    it_id = ITEM_NAME_TO_ID.get(iname.lower(), f"PRO_{iname}")
                    pi_list.append({'match_id': match_id, 'puuid': puuid, 'item_id': it_id, 'slot': s_idx})
                    d_items[it_id] = iname

                perks = p.get('perks', p.get('runes', []))
                for perk in perks:
                    rid = str(perk.get('id') if isinstance(perk, dict) else perk)
                    pr_list.append({'match_id': match_id, 'puuid': puuid, 'rune_id': rid})
                    d_runes[rid] = RUNE_NAME_MAP.get(rid, f"Rune {rid}")

                ps_list.append({
                    'match_id': match_id, 'puuid': puuid, 'champion_id': c_id, 'team_id': tid,
                    'win': 1 if team.get('won') else 0, 'position': role_map.get(idx, 'UNKNOWN'),
                    'kills': kills, 'deaths': deaths, 'assists': assists,
                    'gold': p.get('totalMoneyEarned'),
                    'cs': sum(u.get('count', 0) for u in p.get('unitKills', []) if u.get('unitName') in ['minion', 'neutralMinion']),
                    'dmg_champs': p.get('damageDealt', 0), 'vision': p.get('visionScore', 0),
                    'kda': p.get('kdaRatio', 0), 'kp': (kills + assists) / team_kills if team_kills > 0 else 0,
                    'summ1': "Unknown", 'summ2': "Unknown"
                })

        return m, ts_list, bans, ps_list, pi_list, pr_list, d_champs, {}, d_items, d_runes, pro_players, pro_teams, tourney_matches
    except Exception as e:
        print(f"Error parsing pro file {file_path}: {e}")
        return None

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    riot_files = [f for f in Path(RIOT_ROOT).rglob("*.json") if "matches_raw" in str(f)]
    pro_files = [f for f in Path(ESPORTS_ROOT).rglob("*.json") if f.parent.name == "games" or f.stem.isdigit()]
    if TEST_MODE:
        riot_files, pro_files = riot_files[:100], pro_files[:50]

    big_tables = ['matches', 'team_stats', 'bans', 'player_stats', 'items', 'runes']

    headers_written = {key: False for key in big_tables}

    results = {
        'dim_champs': {}, 'dim_players': {}, 'dim_items': {}, 'dim_runes': {},
        'pro_players': [], 'pro_teams': [], 'tourney_matches': []
    }

    batch_data = {key: [] for key in big_tables}
    BATCH_SIZE = 5000

    def flush_batch():
        for key in big_tables:
            if batch_data[key]:
                df = pd.DataFrame(batch_data[key])
                file_path = f"{OUTPUT_DIR}/{key}.csv"
                include_header = not headers_written[key]
                df.to_csv(file_path, mode='a', index=False, header=include_header)
                headers_written[key] = True
                batch_data[key] = [] # Clear RAM

    with Pool(cpu_count()) as pool:
        for i, res in enumerate(tqdm(pool.imap_unordered(parse_riot_file, riot_files, chunksize=50), total=len(riot_files), desc="Ranked")):
            if res:
                batch_data['matches'].append(res[0]); batch_data['team_stats'].extend(res[1])
                batch_data['bans'].extend(res[2]); batch_data['player_stats'].extend(res[3])
                batch_data['items'].extend(res[4]); batch_data['runes'].extend(res[5])
                results['dim_champs'].update(res[6]); results['dim_players'].update(res[7])
                results['dim_items'].update(res[8]); results['dim_runes'].update(res[9])

            if i > 0 and i % BATCH_SIZE == 0: flush_batch()

        for i, res in enumerate(tqdm(pool.imap_unordered(parse_pro_file, pro_files, chunksize=20), total=len(pro_files), desc="Esports")):
            if res:
                batch_data['matches'].append(res[0]); batch_data['team_stats'].extend(res[1])
                batch_data['bans'].extend(res[2]); batch_data['player_stats'].extend(res[3])
                batch_data['items'].extend(res[4]); batch_data['runes'].extend(res[5])
                results['dim_champs'].update(res[6]); results['dim_items'].update(res[8])
                results['dim_runes'].update(res[9]); results['pro_players'].extend(res[10])
                results['pro_teams'].extend(res[11]); results['tourney_matches'].extend(res[12])

            if i > 0 and i % BATCH_SIZE == 0: flush_batch()

    flush_batch()

    print("\nSaving finalized Dimension CSVs...")

    for key in ['pro_players', 'pro_teams', 'tourney_matches']:
        pd.DataFrame(results[key]).drop_duplicates().to_csv(f"{OUTPUT_DIR}/{key}.csv", index=False)

    dim_configs = [(results['dim_champs'], 'dim_champions'), (results['dim_items'], 'dim_items'), (results['dim_runes'], 'dim_runes')]
    for data_dict, filename in dim_configs:
        df = pd.DataFrame([{'id': k, 'name': v} for k, v in data_dict.items()])
        df.drop_duplicates(subset=['id']).to_csv(f"{OUTPUT_DIR}/{filename}.csv", index=False)

    if results['dim_players']:
        df_p = pd.DataFrame([{'puuid': k, **v} for k, v in results['dim_players'].items()])
        df_p.drop_duplicates(subset=['puuid']).to_csv(f"{OUTPUT_DIR}/dim_players.csv", index=False)

    print(f"Done! Data flushed to /{OUTPUT_DIR}")

if __name__ == "__main__":
    main()