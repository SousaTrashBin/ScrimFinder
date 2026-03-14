"""
export_openapi.py
-----------------
Starts each FastAPI service in a background thread, scrapes /openapi.json,
and writes one YAML file per service.

Usage:
    python export_openapi.py
"""

import sys, time, json, threading, urllib.request

try:
    import yaml
except ImportError:
    print("Install PyYAML:  pip install pyyaml"); sys.exit(1)
try:
    import uvicorn
except ImportError:
    print("Install uvicorn:  pip install uvicorn"); sys.exit(1)

SERVICES = [
    {"app": "training.main:app",             "port": 8000, "out": "openapi_training.yaml"},
    {"app": "services.draft.main:app",       "port": 8001, "out": "openapi_draft.yaml"},
    {"app": "services.build.main:app",       "port": 8002, "out": "openapi_build.yaml"},
    {"app": "services.performance.main:app", "port": 8003, "out": "openapi_performance.yaml"},
]

HOST = "127.0.0.1"


def _run(app_str, port):
    uvicorn.run(app_str, host=HOST, port=port, log_level="error")


def _wait(url, retries=30, delay=0.5):
    for _ in range(retries):
        try:
            urllib.request.urlopen(url, timeout=2); return True
        except Exception:
            time.sleep(delay)
    return False


def export_service(svc):
    port, app_str, out = svc["port"], svc["app"], svc["out"]
    url = f"http://{HOST}:{port}/openapi.json"

    print(f"  Starting {app_str} on :{port} ...")
    t = threading.Thread(target=_run, args=(app_str, port), daemon=True)
    t.start()

    if not _wait(url):
        print(f"  ERROR: {app_str} did not start in time."); return

    print(f"  Fetching {url} ...")
    with urllib.request.urlopen(url, timeout=5) as r:
        spec = json.loads(r.read().decode())

    with open(out, "w", encoding="utf-8") as f:
        yaml.dump(spec, f, allow_unicode=True, sort_keys=False, default_flow_style=False)

    paths = len(spec.get("paths", {}))
    schemas = len(spec.get("components", {}).get("schemas", {}))
    print(f"  ✅  {out}  ({paths} paths, {schemas} schemas)")


if __name__ == "__main__":
    print("=== LoL Analysis API — OpenAPI YAML export ===\n")
    for svc in SERVICES:
        export_service(svc)
        time.sleep(1)
    print("\nDone. Paste any YAML file into https://editor.swagger.io to preview.")
