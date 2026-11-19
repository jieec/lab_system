"""Real HTTP/MySQL Agent regression for an isolated copy of the project database.

Required: AGENT_TEST_BASE (loopback port 18080), AGENT_TEST_DATABASE (lab_agent_e2e_*).
Creates labelled fixtures in that disposable database; never run against the working database.
No database password or model key is read or saved by this script.
"""
import json
import os
import time
import uuid
import urllib.request
import urllib.parse
from datetime import datetime, timedelta
from pathlib import Path

BASE = os.environ.get("AGENT_TEST_BASE", "")
DATABASE = os.environ.get("AGENT_TEST_DATABASE", "")
if BASE != "http://127.0.0.1:18080/api" or not DATABASE.startswith("lab_agent_e2e_"):
    raise SystemExit("Refusing to create test data without explicit isolated-database configuration")

opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
passed = []
sessions, sent_at = {}, {}


def check(name, condition):
    if not condition:
        raise AssertionError(name)
    passed.append(name)
    print("PASS:", name, flush=True)


def call(method, path, token=None, body=None):
    request = urllib.request.Request(BASE + path, method=method,
        data=json.dumps(body, ensure_ascii=False).encode() if body is not None else None,
        headers={"Content-Type": "application/json"})
    if token:
        request.add_header("Authorization", "Bearer " + token)
    with opener.open(request, timeout=30) as response:
        return json.load(response)


def ok(method, path, token=None, body=None):
    result = call(method, path, token, body)
    if result.get("code") != 200:
        raise AssertionError(f"{method} {path}: {result.get('code')} {result.get('msg')}")
    return result.get("data")


def login(username):
    return ok("POST", "/auth/login", body={"username": username, "password": "123456"})["token"]


def chat(token, text):
    delay = 1.6 - (time.monotonic() - sent_at.get(token, 0))
    if delay > 0:
        time.sleep(delay)
    sent_at[token] = time.monotonic()
    request = urllib.request.Request(BASE + "/agent/chat", method="POST",
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"},
        data=json.dumps({"sessionId": sessions.get(token), "message": text, "ruleMode": True}, ensure_ascii=False).encode())
    events, name, lines = [], "message", []
    with opener.open(request, timeout=60) as response:
        if "text/event-stream" not in response.headers.get("Content-Type", ""):
            raise AssertionError("Expected SSE, got " + response.read().decode()[:200])
        for raw in response:
            line = raw.decode("utf-8").rstrip("\r\n")
            if not line:
                if lines:
                    data = json.loads("\n".join(lines))
                    events.append((name, data))
                    if name == "session":
                        sessions[token] = data["sessionId"]
                name, lines = "message", []
            elif line.startswith("event:"):
                name = line[6:].strip()
            elif line.startswith("data:"):
                lines.append(line[5:].lstrip())
    errors = [data for event, data in events if event == "error"]
    if errors:
        raise AssertionError("Agent error: " + str(errors))
    check("SSE finished: " + text.split()[0], any(event == "done" for event, _ in events))
    return events


def first(events, event):
    return next(data for name, data in events if name == event)


def confirm(token, card):
    return ok("POST", "/agent/confirm", token, {"sessionId": sessions[token], "actionId": card["id"]})


def main():
    check("Unauthenticated Agent access blocked", call("GET", "/agent/status")["code"] == 401)
    admin, repairer = login("admin"), login("r_zhang")
    tag = "AGENT-E2E-" + uuid.uuid4().hex[:8]
    students = []
    for index in (1, 2):
        username = "agent_test_" + uuid.uuid4().hex[:8]
        ok("POST", "/users", admin, {"username": username, "realName": "助手隔离测试" + str(index), "role": "STUDENT"})
        students.append(login(username))
    student, other = students
    status = ok("GET", "/agent/status", student)
    check("Rules mode has no model credential", status["mode"] == "rules" and not status["configured"] and "apiKey" not in status)
    ok("POST", "/labs", admin, {"code": tag, "name": tag, "capacity": 30, "openTime": "08:00", "closeTime": "22:00", "needApproval": 1, "status": 1})
    lab = ok("GET", "/labs?keyword=" + tag, admin)["records"][0]
    ok("POST", "/equipments", admin, {"code": tag + "-EQ", "name": tag + "-EQ", "labId": lab["id"], "category": "仪器仪表", "status": "IDLE"})
    equipment = ok("GET", "/equipments?keyword=" + tag, admin)["records"][0]
    before = ok("GET", "/reservations/my", student)["total"]
    date = (datetime.now() + timedelta(days=7)).strftime("%Y-%m-%d")
    events = chat(student, f"预约 {date} 14:00-16:00 4人 实验室：{tag} 用途：助手隔离联调")
    card = first(events, "action")
    check("Booking card does not write before confirmation", ok("GET", "/reservations/my", student)["total"] == before)
    denied = call("POST", "/agent/confirm", other, {"sessionId": sessions[student], "actionId": card["id"]})
    check("Another user cannot confirm booking", denied["code"] == 403)
    result = confirm(student, card)
    replay = confirm(student, card)
    check("Repeated confirmation returns same booking", result["orderNo"] == replay["orderNo"])
    records = ok("GET", "/reservations/my", student)["records"]
    reservation = next(row for row in records if row["orderNo"] == result["orderNo"])
    check("Confirmed booking persisted exactly once", ok("GET", "/reservations/my", student)["total"] == before + 1)
    check("Persisted booking belongs to chosen lab and has expected values", reservation["labId"] == lab["id"] and reservation["headcount"] == 4 and reservation["status"] == "PENDING")
    events = chat(student, "我的预约")
    own_result = next(data["result"] for event, data in events if event == "tool" and data.get("state") == "success")
    check("Agent query returns persisted booking", any(row["orderNo"] == result["orderNo"] for row in own_result))
    events = chat(other, "我的预约")
    other_result = next(data["result"] for event, data in events if event == "tool" and data.get("state") == "success")
    check("Other student sees no booking from first student", not other_result)
    events = chat(student, "取消预约 " + str(reservation["id"]))
    cancel_card = first(events, "action")
    check("Cancel draft has not cancelled booking", ok("GET", f"/reservations/{reservation['id']}", student)["status"] == "PENDING")
    confirm(student, cancel_card)
    check("Confirmed cancellation persisted", ok("GET", f"/reservations/{reservation['id']}", student)["status"] == "CANCELLED")
    events = chat(student, f"推荐 {date} 4人 120分钟 实验室：{tag}")
    slots = next(data["result"] for event, data in events if event == "tool" and data.get("state") == "success")
    check("Recommendation uses real availability", 0 < len(slots) <= 3 and all(row["labId"] == lab["id"] for row in slots))
    events = chat(student, "查设备 " + tag)
    found = next(data["result"] for event, data in events if event == "tool" and data.get("state") == "success")
    check("Equipment tool returns true asset ID", found[0]["equipmentId"] == equipment["id"])
    events = chat(student, f"报修 {equipment['id']} 灯不亮还有糊味，助手隔离联调")
    repair_card = first(events, "action")
    check("Dangerous fault marked HIGH as existing UI expects", repair_card["details"]["紧急程度"] == "HIGH")
    check("Fault draft does not change equipment", ok("GET", f"/equipments/{equipment['id']}", student)["status"] == "IDLE")
    repair_result = confirm(student, repair_card)
    check("Fault confirmation is idempotent", confirm(student, repair_card)["orderNo"] == repair_result["orderNo"])
    orders = ok("GET", "/repairs?scope=mine", student)["records"]
    order = next(row for row in orders if row["orderNo"] == repair_result["orderNo"])
    check("Confirmed fault persisted as pending/high", order["status"] == "PENDING" and order["urgency"] == "HIGH")
    check("Equipment moved to repair only after confirmation", ok("GET", f"/equipments/{equipment['id']}", student)["status"] == "REPAIR")
    events = chat(other, "我的报修")
    own_repairs = next(data["result"] for event, data in events if event == "tool" and data.get("state") == "success")
    check("Repair query isolates students", not own_repairs)
    repairer_id = next(user["id"] for user in ok("GET", "/users/options?role=REPAIRER", admin) if user["username"] == "r_zhang")
    ok("PUT", f"/repairs/{order['id']}/assign?repairerId={repairer_id}", admin)
    events = chat(repairer, "我的工单")
    assigned = next(data["result"] for event, data in events if event == "tool" and data.get("state") == "success")
    check("Repairer Agent sees assigned order", any(row["orderNo"] == repair_result["orderNo"] for row in assigned))
    ok("PUT", f"/repairs/{order['id']}/start", repairer)
    ok("PUT", f"/repairs/{order['id']}/finish", repairer, {"repairDesc": "隔离测试维修完成", "cost": 0})
    ok("PUT", f"/repairs/{order['id']}/verify", student, {"score": 5, "verifyRemark": "隔离测试验收"})
    check("Repair lifecycle completes and restores equipment", ok("GET", f"/equipments/{equipment['id']}", student)["status"] == "IDLE")
    events = chat(student, "数据统计")
    summary = next(data["result"] for event, data in events if event == "tool" and data.get("state") == "success")
    check("Student statistics contain personal fields only", set(summary) == {"myReservationTotal", "myReportedRepairTotal"})
    logs = ok("GET", "/logs?pageSize=100&module=" + urllib.parse.quote("智能助手"), admin)["records"]
    check("Confirmed actions are audited", any(row.get("module") == "智能助手" and row.get("success") == 1 for row in logs))
    ok("DELETE", "/agent/sessions/" + sessions[student], student)
    check("Deleted session cannot replay old confirmation", call("POST", "/agent/confirm", student, {"sessionId": sessions[student], "actionId": repair_card["id"]})["code"] == 403)
    report = {"passed": len(passed), "failed": 0, "checks": passed, "database": DATABASE, "modelCalled": False}
    Path("lab-backend/target/agent-live-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"COMPLETE: {len(passed)} real HTTP/MySQL checks passed; no model calls.", flush=True)


if __name__ == "__main__":
    main()
