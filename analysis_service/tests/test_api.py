"""
tests/test_api.py — Full pytest suite for the ScrimFinder API.

Run:
    pytest                          # all fast tests
    pytest -m "not slow"            # skip training tests
    pytest -k "games"               # only game tests
    pytest -v --tb=short            # verbose with short tracebacks
"""
import json, time, pytest, os, tempfile

# Set isolated DB before importing app
_TMP = tempfile.mkdtemp(prefix="lol_test_")
os.environ["PLATFORM_DB"]  = os.path.join(_TMP,"platform.db")
os.environ["MODELS_DIR"]   = os.path.join(_TMP,"models")
os.environ["GAMES_DIR"]    = os.path.join(_TMP,"games")
os.environ["DATASETS_DIR"] = os.path.join(_TMP,"datasets")

from fastapi.testclient import TestClient
from analysis_api.main import app

client = TestClient(app, raise_server_exceptions=False)

# ── Payloads ──────────────────────────────────────────────────
GAME = {"matchId":"EUW1_TEST_001","gameVersion":"14.10.1","gameType":"RANKED","gameDuration":1823,
        "participants":[{"puuid":f"p{i}","championId":10+i,"teamId":100 if i<5 else 200,"win":i<5,"kills":5,"deaths":3,"assists":7} for i in range(10)]}
GAME2 = {"matchId":"EUW1_TEST_002","gameVersion":"14.10.1","gameType":"NORMAL","gameDuration":2100,"participants":[]}

DRAFT = {"team_blue":{"champions":[{"name":"Malphite","role":"TOP"},{"name":"Amumu","role":"JUNGLE"},{"name":"Orianna","role":"MID"},{"name":"Jinx","role":"BOT"},{"name":"Lulu","role":"SUPPORT"}]},
         "team_red": {"champions":[{"name":"Fiora","role":"TOP"},{"name":"Vi","role":"JUNGLE"},{"name":"Zed","role":"MID"},{"name":"Caitlyn","role":"BOT"},{"name":"Thresh","role":"SUPPORT"}]}}

# ══════════════════════════════════════════════════════════════
class TestSystem:
    def test_root_200(self): assert client.get("/").status_code==200
    def test_root_shape(self):
        d=client.get("/").json(); assert "service" in d; assert d["status"]=="ok"
    def test_health(self): assert client.get("/health").json()["status"]=="ok"
    def test_openapi(self):
        paths=client.get("/openapi.json").json()["paths"]
        for prefix in ["/games","/features","/datasets","/training/jobs","/models","/analysis/draft"]:
            assert any(p.startswith(prefix) for p in paths),f"Missing prefix {prefix}"
    def test_docs(self): assert client.get("/docs").status_code==200

# ══════════════════════════════════════════════════════════════
class TestGames:
    def test_ingest(self):
        r=client.post("/games",json={"data":GAME,"source":"test"}); assert r.status_code==201
        d=r.json(); assert d["id"]=="EUW1_TEST_001"; assert d["patch"]=="14.10.1"; assert d["match_type"]=="RANKED"
    def test_ingest_idempotent(self):
        r1=client.post("/games",json={"data":GAME}); r2=client.post("/games",json={"data":GAME})
        assert r1.status_code==201; assert r1.json()["id"]==r2.json()["id"]
    def test_ingest_derives_id(self):
        r=client.post("/games",json={"data":{"matchId":"DERIVED_001"}}); assert r.json()["id"]=="DERIVED_001"
    def test_ingest_generates_id(self):
        r=client.post("/games",json={"data":{"x":1}}); assert r.json()["id"].startswith("game_")
    def test_ingest_explicit_id(self):
        r=client.post("/games",json={"id":"CUSTOM_ID","data":{"x":1}}); assert r.json()["id"]=="CUSTOM_ID"
    def test_batch(self):
        r=client.post("/games/batch",json={"games":[{"data":GAME},{"data":GAME2}]}); assert r.status_code==201
        d=r.json(); assert d["ingested"]==2; assert d["skipped"]==0
    def test_batch_limit(self):
        r=client.post("/games/batch",json={"games":[{"data":{"matchId":f"G{i}"}} for i in range(1001)]}); assert r.status_code==422
    def test_get_game(self):
        client.post("/games",json={"data":GAME})
        r=client.get("/games/EUW1_TEST_001"); assert r.status_code==200
        d=r.json(); assert d["id"]=="EUW1_TEST_001"; assert "raw_json" in d; assert d["raw_json"]["matchId"]=="EUW1_TEST_001"
    def test_get_game_404(self): assert client.get("/games/NOPE_XYZ").status_code==404
    def test_list_games(self):
        r=client.get("/games"); assert r.status_code==200; d=r.json()
        assert "games" in d; assert "meta" in d; assert "total" in d["meta"]
    def test_list_filter_match_type(self):
        client.post("/games",json={"data":GAME})
        for g in client.get("/games?match_type=RANKED").json()["games"]: assert g["match_type"]=="RANKED"
    def test_list_pagination(self):
        r=client.get("/games?limit=2&offset=0"); d=r.json()
        assert len(d["games"])<=2; assert d["meta"]["limit"]==2
    def test_delete(self):
        client.post("/games",json={"id":"DEL_ME","data":{"x":1}})
        assert client.delete("/games/DEL_ME").status_code==204
        assert client.get("/games/DEL_ME").status_code==404
    def test_delete_404(self): assert client.delete("/games/NOPE_XYZ").status_code==404

# ══════════════════════════════════════════════════════════════
class TestFeatures:
    def setup_method(self): client.post("/games",json={"data":GAME})
    def test_extract_by_id(self):
        r=client.post("/features/extract",json={"game_id":"EUW1_TEST_001","concerns":["draft"],"store":False})
        assert r.status_code==200; d=r.json()
        assert d["game_id"]=="EUW1_TEST_001"; assert len(d["features"])==1; assert d["features"][0]["concern"]=="draft"
    def test_extract_inline(self):
        r=client.post("/features/extract",json={"raw_data":GAME,"concerns":["build","performance"],"store":False})
        assert r.status_code==200; concerns={f["concern"] for f in r.json()["features"]}
        assert "build" in concerns; assert "performance" in concerns
    def test_extract_all(self):
        r=client.post("/features/extract",json={"game_id":"EUW1_TEST_001","concerns":["draft","build","performance"],"store":True})
        assert len(r.json()["features"])==3
    def test_extract_missing_404(self):
        assert client.post("/features/extract",json={"game_id":"NOPE","concerns":["draft"]}).status_code==404
    def test_extract_no_source_422(self):
        assert client.post("/features/extract",json={"concerns":["draft"]}).status_code==422
    def test_get_after_store(self):
        client.post("/features/extract",json={"game_id":"EUW1_TEST_001","concerns":["draft"],"store":True})
        r=client.get("/features/EUW1_TEST_001"); assert r.status_code==200
        assert any(f["concern"]=="draft" for f in r.json())
    def test_get_filter(self):
        client.post("/features/extract",json={"game_id":"EUW1_TEST_001","concerns":["draft","build"],"store":True})
        feats=client.get("/features/EUW1_TEST_001?concern=draft").json()
        assert all(f["concern"]=="draft" for f in feats)
    def test_get_404(self): assert client.get("/features/NOPE").status_code==404
    def test_vector_schema(self):
        r=client.post("/features/extract",json={"game_id":"EUW1_TEST_001","concerns":["draft"],"store":False})
        f=r.json()["features"][0]
        for k in ["feature_vector","feature_names","schema_version","extracted_at"]: assert k in f
        assert isinstance(f["feature_vector"],list); assert isinstance(f["feature_names"],list)

# ══════════════════════════════════════════════════════════════
class TestDatasets:
    def _mk(self,name="Test DS",concern="draft"):
        r=client.post("/datasets",json={"name":name,"concern":concern,"filters":{"match_type":"RANKED"}})
        assert r.status_code==201; return r.json()
    def test_create(self):
        d=self._mk(); assert d["id"].startswith("ds_"); assert d["concern"]=="draft"; assert d["status"]=="pending"; assert d["filters"]["match_type"]=="RANKED"
    def test_build(self):
        r=client.post("/datasets/build",json={"name":"Built","concern":"build","filters":{}})
        assert r.status_code==202; assert r.json()["id"].startswith("ds_")
    def test_list(self):
        self._mk("L1"); self._mk("L2"); r=client.get("/datasets"); assert r.status_code==200; assert len(r.json()["datasets"])>=2
    def test_list_filter(self):
        self._mk(concern="draft")
        for ds in client.get("/datasets?concern=draft").json()["datasets"]: assert ds["concern"]=="draft"
    def test_get(self):
        d=self._mk(); r=client.get(f"/datasets/{d['id']}"); assert r.status_code==200; assert r.json()["id"]==d["id"]
    def test_get_404(self): assert client.get("/datasets/ds_nope").status_code==404
    def test_delete(self):
        d=self._mk(); assert client.delete(f"/datasets/{d['id']}").status_code==204
        assert client.get(f"/datasets/{d['id']}").status_code==404
    def test_delete_404(self): assert client.delete("/datasets/ds_nope").status_code==404
    def test_schema(self):
        d=self._mk()
        for f in ["id","name","description","concern","filters","game_count","row_count","status","created_at"]: assert f in d

# ══════════════════════════════════════════════════════════════
class TestTrainingJobs:
    def _trigger(self,concern="draft",sample=0.01):
        r=client.post("/training/jobs",json={"concern":concern,"algorithm":"auto","sample":sample})
        assert r.status_code==202,r.text; return r.json()
    def test_create(self):
        j=self._trigger(); assert j["id"].startswith("job_"); assert j["concern"]=="draft"; assert j["status"] in ("PENDING","RUNNING")
    def test_all_concerns(self):
        for c in ["draft","build","performance"]: assert self._trigger(c)["concern"]==c
    def test_list(self):
        self._trigger(); r=client.get("/training/jobs"); assert r.status_code==200; assert len(r.json()["jobs"])>=1
    def test_list_filter(self):
        self._trigger("draft"); [j for j in client.get("/training/jobs?concern=draft").json()["jobs"]]
    def test_get(self):
        j=self._trigger(); r=client.get(f"/training/jobs/{j['id']}"); assert r.status_code==200; d=r.json()
        assert d["id"]==j["id"]; assert "progress" in d; assert "stage" in d
    def test_get_404(self): assert client.get("/training/jobs/job_nope").status_code==404
    def test_schema(self):
        j=self._trigger()
        for f in ["id","concern","algorithm","status","progress","stage","filters","created_at"]: assert f in j
    def test_cancel(self):
        j=self._trigger(); time.sleep(0.3)
        r=client.post(f"/training/jobs/{j['id']}/cancel"); assert r.status_code==200
    def test_cancel_404(self): assert client.post("/training/jobs/job_nope/cancel").status_code==404
    def test_deploy_not_completed_409(self):
        j=self._trigger(); r=client.post(f"/training/jobs/{j['id']}/deploy")
        if r.status_code!=202: assert r.status_code==409
    def test_filters_stored(self):
        j=self._trigger(sample=0.02); assert "sample" in j["filters"]; assert j["filters"]["sample"]==0.02
    def test_invalid_concern_422(self): assert client.post("/training/jobs",json={"concern":"bad"}).status_code==422
    def test_dataset_not_found_404(self):
        assert client.post("/training/jobs",json={"concern":"draft","dataset_id":"ds_nope"}).status_code==404
    @pytest.mark.slow
    def test_job_completes(self):
        j=client.post("/training/jobs",json={"concern":"draft","sample":0.05}).json()
        deadline=time.time()+600
        while time.time()<deadline:
            time.sleep(5); s=client.get(f"/training/jobs/{j['id']}").json()["status"]
            if s=="COMPLETED": return
            if s in ("FAILED","CANCELLED"): pytest.fail(f"Job ended with {s}")
        pytest.fail("Training timed out after 10 min")

# ══════════════════════════════════════════════════════════════
class TestModels:
    def test_list(self): r=client.get("/models"); assert r.status_code==200; assert "models" in r.json()
    def test_active(self):
        r=client.get("/models/active"); assert r.status_code==200
        for m in r.json()["models"]: assert m["is_active"] is True
    def test_filter(self):
        for m in client.get("/models?concern=draft").json()["models"]: assert m["concern"]=="draft"
    def test_get_404(self): assert client.get("/models/99999").status_code==404
    def test_activate_404(self): assert client.post("/models/99999/activate").status_code==404
    def test_deactivate_404(self): assert client.post("/models/99999/deactivate").status_code==404
    def test_delete_404(self): assert client.delete("/models/99999").status_code==404

# ══════════════════════════════════════════════════════════════
class TestAnalysis:
    def setup_method(self): client.post("/games",json={"data":GAME})
    def test_draft(self):
        r=client.post("/analysis/draft",json=DRAFT); assert r.status_code==200
        d=r.json(); assert "blue_win_probability" in d; assert "red_win_probability" in d
    def test_draft_probs_sum(self):
        d=client.post("/analysis/draft",json=DRAFT).json()
        assert abs(d["blue_win_probability"]+d["red_win_probability"]-1.0)<0.01
    def test_draft_range(self):
        d=client.post("/analysis/draft",json=DRAFT).json()
        assert 0<=d["blue_win_probability"]<=1; assert 0<=d["red_win_probability"]<=1
    def test_draft_schema(self):
        d=client.post("/analysis/draft",json=DRAFT).json()
        for f in ["blue_win_probability","red_win_probability","blue_synergies","red_synergies","win_conditions","tips"]: assert f in d
    def test_draft_invalid_role(self):
        bad={**DRAFT,"team_blue":{"champions":[{"name":"X","role":"INVALID"}]}}
        assert client.post("/analysis/draft",json=bad).status_code==422
    def test_draft_missing_team(self):
        assert client.post("/analysis/draft",json={"team_blue":DRAFT["team_blue"]}).status_code==422
    def test_build(self):
        r=client.post("/analysis/build",json={"champion":"Jinx","role":"BOT","items":["Kraken Slayer","Infinity Edge"]})
        assert r.status_code==200; d=r.json(); assert 0<=d["score"]<=100
    def test_build_schema(self):
        d=client.post("/analysis/build",json={"champion":"Jinx","items":["Infinity Edge"]}).json()
        for f in ["champion","items","score","strengths","weaknesses","alternative_items"]: assert f in d
    def test_build_requires_items(self): assert client.post("/analysis/build",json={"champion":"Jinx","items":[]}).status_code==422
    def test_player(self):
        r=client.post("/analysis/player",json={"summoner_id":"fc59850","last_n_games":20})
        assert r.status_code==200; d=r.json(); assert 0<=d["win_rate"]<=1
    def test_player_schema(self):
        d=client.post("/analysis/player",json={"summoner_id":"test"}).json()
        for f in ["summoner_id","win_rate","matches_analyzed","tips"]: assert f in d
    def test_game_by_id(self):
        r=client.post("/analysis/game",json={"game_id":"EUW1_TEST_001"})
        assert r.status_code==200; assert r.json()["game_id"]=="EUW1_TEST_001"
    def test_game_inline(self):
        assert client.post("/analysis/game",json={"raw_data":GAME}).status_code==200
    def test_game_404(self): assert client.post("/analysis/game",json={"game_id":"NOPE"}).status_code==404
    def test_game_422(self): assert client.post("/analysis/game",json={}).status_code==422
    def test_champion_404_or_503(self):
        r=client.post("/analysis/champion",json={"champion":"NotAChampXYZ"})
        assert r.status_code in (404,503)  # 503 if LEAGUE_DB not configured, 404 if it is

# ══════════════════════════════════════════════════════════════
class TestIntegration:
    def test_ingest_fetch(self):
        client.post("/games",json={"data":{"matchId":"INTEG_001","gameVersion":"14.10"}})
        r=client.get("/games/INTEG_001"); assert r.json()["raw_json"]["matchId"]=="INTEG_001"
    def test_ingest_extract_retrieve(self):
        client.post("/games",json={"data":{"matchId":"INTEG_002"}})
        client.post("/features/extract",json={"game_id":"INTEG_002","concerns":["draft"],"store":True})
        assert client.get("/features/INTEG_002?concern=draft").status_code==200
    def test_dataset_lifecycle(self):
        r=client.post("/datasets",json={"name":"Integ DS","concern":"draft","filters":{}})
        ds_id=r.json()["id"]
        assert client.get(f"/datasets/{ds_id}").status_code==200
        assert client.delete(f"/datasets/{ds_id}").status_code==204
        assert client.get(f"/datasets/{ds_id}").status_code==404
    def test_job_cancel_flow(self):
        j=client.post("/training/jobs",json={"concern":"draft","sample":0.01}).json()
        time.sleep(0.5); client.post(f"/training/jobs/{j['id']}/cancel")
        time.sleep(2)
        assert client.get(f"/training/jobs/{j['id']}").json()["status"] in ("CANCELLED","COMPLETED","FAILED")
    def test_draft_deterministic(self):
        r1=client.post("/analysis/draft",json=DRAFT); r2=client.post("/analysis/draft",json=DRAFT)
        assert r1.json()["blue_win_probability"]==r2.json()["blue_win_probability"]
    def test_batch_then_list(self):
        client.post("/games/batch",json={"games":[{"data":{"matchId":f"BINTEG_{i}"}} for i in range(5)]})
        ids={g["id"] for g in client.get("/games?limit=500").json()["games"]}
        for i in range(5): assert f"BINTEG_{i}" in ids
