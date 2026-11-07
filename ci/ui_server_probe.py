"""Taps 'Start server' in the running app (allowing the notification prompt if shown),
then checks the foreground service and the local API from the host through adb port forwarding."""
import json, re, subprocess, sys, time, urllib.request, urllib.error
import xml.etree.ElementTree as ET

out, pkg, api = sys.argv[1], sys.argv[2], sys.argv[3]
report = {}

def adb(*args, check=False):
    r = subprocess.run(["adb", *args], capture_output=True)
    return r.stdout.decode("utf-8", "replace")

def dump():
    for _ in range(6):
        adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        x = adb("exec-out", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in x:
            return ET.fromstring(x[x.index("<hierarchy"):])
        time.sleep(1.5)
    return None

def tap(*labels):
    root = dump()
    if root is None:
        return False
    for node in root.iter("node"):
        if node.get("text") in labels or node.get("content-desc") in labels:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
            time.sleep(2)
            return True
    return False

def http(path, key=None):
    req = urllib.request.Request("http://127.0.0.1:8787" + path)
    if key:
        req.add_header("Authorization", "Bearer " + key)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:
        return 0, str(e)

ok = True
report["tapped_start"] = tap("Start server")
time.sleep(2)
report["tapped_allow"] = tap("Allow", "ALLOW")
time.sleep(3)
subprocess.run(f"adb exec-out screencap -p > {out}/screens/01_server_running_api{api}.png", shell=True)
services = adb("shell", "dumpsys", "activity", "services", pkg)
open(f"{out}/services.txt", "w").write(services)
report["foreground_service"] = "GatewayService" in services and "isForeground=true" in services
adb("forward", "tcp:8787", "tcp:8787")
report["health"] = http("/health")
report["models_without_key"] = http("/v1/models")[0]
report["models_wrong_key"] = http("/v1/models", "fllm_wrong")[0]
json.dump(report, open(f"{out}/server_probe.json", "w"), indent=2)
print(json.dumps(report, indent=2))
ok = report["health"][0] == 200 and report["models_without_key"] == 401 and report["models_wrong_key"] == 401 and report["foreground_service"]
sys.exit(0 if ok else 1)
