"""tests/conftest.py — Pytest config, markers, shared fixtures."""
import os, tempfile, pytest

def pytest_configure(config):
    config.addinivalue_line("markers","slow: real training jobs (minutes). Skip: pytest -m 'not slow'")
    config.addinivalue_line("markers","requires_db: needs LEAGUE_DB configured.")

@pytest.fixture(scope="session",autouse=True)
def isolated_db():
    tmp=tempfile.mkdtemp(prefix="lol_test_")
    os.environ["PLATFORM_DB"]  =os.path.join(tmp,"platform.db")
    os.environ["MODELS_DIR"]   =os.path.join(tmp,"models")
    os.environ["GAMES_DIR"]    =os.path.join(tmp,"games")
    os.environ["DATASETS_DIR"] =os.path.join(tmp,"datasets")
    from analysis_api.core.config import cfg
    cfg.PLATFORM_DB=os.environ["PLATFORM_DB"]; cfg.MODELS_DIR=os.environ["MODELS_DIR"]
    cfg.GAMES_DIR=os.environ["GAMES_DIR"]; cfg.DATASETS_DIR=os.environ["DATASETS_DIR"]
    cfg.ensure_dirs()
    from analysis_api.core.db import init_db; init_db()
    yield tmp

@pytest.fixture
def sample_game():
    return {"matchId":"EUW1_FIXTURE_001","gameVersion":"14.10.1","gameType":"RANKED","gameDuration":1823,
            "participants":[{"puuid":f"p{i}","championId":10+i,"teamId":100 if i<5 else 200,"win":i<5,"kills":5,"deaths":3,"assists":7} for i in range(10)]}

@pytest.fixture(scope="module")
def client():
    from fastapi.testclient import TestClient
    from analysis_api.main import app
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c
