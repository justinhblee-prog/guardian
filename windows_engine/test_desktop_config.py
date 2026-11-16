import json,pytest
from windows_engine.desktop import load_runtime_config
from windows_engine.network_config import NetworkConfigError
def test_runtime_config_requires_nonempty_filter(tmp_path):
    (tmp_path/"rules.txt").write_text("! empty\n",encoding="utf-8"); (tmp_path/"guardian.json").write_text(json.dumps({"rules_file":"rules.txt"}),encoding="utf-8")
    with pytest.raises(NetworkConfigError): load_runtime_config(tmp_path/"guardian.json")
def test_runtime_config_loads_filter_and_upstreams(tmp_path):
    (tmp_path/"rules.txt").write_text("||ads.example^\n",encoding="utf-8"); (tmp_path/"guardian.json").write_text(json.dumps({"rules_file":"rules.txt","upstreams":["9.9.9.9"]}),encoding="utf-8")
    data,rules=load_runtime_config(tmp_path/"guardian.json")
    assert data["upstreams"]==["9.9.9.9"] and rules.is_blocked("cdn.ads.example")
