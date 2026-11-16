import json
from pathlib import Path
from windows_engine.service import load_config
def test_config_loads_rules(tmp_path:Path):
    (tmp_path/"rules.txt").write_text("||ads.example^\n@@||safe.ads.example^\n",encoding="utf-8")
    (tmp_path/"guardian.json").write_text(json.dumps({"rules_file":"rules.txt","upstreams":["1.1.1.1"]}),encoding="utf-8")
    config,rules=load_config(tmp_path/"guardian.json")
    assert config["upstreams"]==["1.1.1.1"] and rules.is_blocked("x.ads.example") and not rules.is_blocked("safe.ads.example")
def test_missing_rules_file_fails_closed(tmp_path:Path):
    (tmp_path/"guardian.json").write_text(json.dumps({"rules_file":"missing.txt"}),encoding="utf-8")
    try: load_config(tmp_path/"guardian.json")
    except FileNotFoundError: pass
    else: raise AssertionError("missing filter file must stop service startup")
