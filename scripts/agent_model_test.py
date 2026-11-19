"""Paid-model integration check against a disposable MySQL copy.

Uses the same isolation guard as agent_live_test.py. Requires a backend configured
with a funded model key. Makes a small, bounded number of real API calls.
Never saves tokens, keys, raw conversation history or database credentials.
"""
import json
import time
import uuid
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path
import agent_live_test as live


def model_chat(token, text):
    delay = 1.6 - (time.monotonic() - live.sent_at.get(token, 0))
    if delay > 0:
        time.sleep(delay)
    live.sent_at[token] = time.monotonic()
    request = urllib.request.Request(live.BASE + "/agent/chat", method="POST",
        headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"},
        data=json.dumps({"sessionId": live.sessions.get(token), "message": text, "ruleMode": False}, ensure_ascii=False).encode())
    events, event, lines = [], "message", []
    with live.opener.open(request, timeout=180) as response:
        if "text/event-stream" not in response.headers.get("Content-Type", ""):
            raise AssertionError("Expected model SSE response")
        for raw in response:
            line = raw.decode("utf-8").rstrip("\r\n")
            if not line:
                if lines:
                    data = json.loads("\n".join(lines))
                    events.append((event, data))
                    if event == "session":
                        live.sessions[token] = data["sessionId"]
                    if event == "mode" and data.get("mode") == "rules":
                        raise AssertionError("Model fell back: " + data.get("message", "unknown"))
                    if event == "error":
                        raise AssertionError("Model chat failed: " + data.get("message", "unknown"))
                event, lines = "message", []
            elif line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                lines.append(line[5:].lstrip())
    live.check("Real model SSE completes", any(name == "done" for name, _ in events))
    live.check("Real model returns Chinese text", any(name == "delta" and data.get("text", "").strip() for name, data in events))
    print("Tool calls:", ", ".join(data["name"] for name, data in events if name == "tool" and data.get("state") == "success"), flush=True)
    return events


def used(events, tool):
    return any(name == "tool" and data.get("name") == tool and data.get("state") == "success" for name, data in events)


def main():
    admin = live.login("admin")
    username = "model_test_" + uuid.uuid4().hex[:8]
    live.ok("POST", "/users", admin, {"username": username, "realName": "模型隔离联调", "role": "STUDENT"})
    token = live.login(username)
    live.check("Backend has configured model", live.ok("GET", "/agent/status", token)["configured"])
    tag = "AGENT-MODEL-" + uuid.uuid4().hex[:8]
    live.ok("POST", "/labs", admin, {"code": tag, "name": tag, "capacity": 30, "openTime": "08:00", "closeTime": "22:00", "needApproval": 1, "status": 1})
    lab = live.ok("GET", "/labs?keyword=" + tag, admin)["records"][0]
    live.ok("POST", "/equipments", admin, {"code": tag + "-EQ", "name": tag + "-EQ", "labId": lab["id"], "status": "IDLE"})
    equipment = live.ok("GET", "/equipments?keyword=" + tag, admin)["records"][0]

    events = model_chat(token, "帮我看看我现在有哪些预约记录。")
    live.check("Natural Chinese triggers own-reservation tool", used(events, "query_my_reservations"))
    events = model_chat(token, "我想预约一间实验室做数据库实验。")
    live.check("Missing conditions cause clarification without creating a card", not any(name == "action" for name, _ in events))
    events = model_chat(token, f"就用编号为{tag}的实验室，明天下午两点到四点，4个人，用途是数据库实验，请生成待确认卡片。")
    live.check("Multi-turn booking searches real lab", used(events, "search_labs"))
    card = live.first(events, "action")
    tomorrow = (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%d")
    live.check("Relative date and Chinese time resolved correctly", card["details"]["开始时间"] == tomorrow + " 14:00:00" and card["details"]["结束时间"] == tomorrow + " 16:00:00")
    live.check("Real-model draft has not written booking", live.ok("GET", "/reservations/my", token)["total"] == 0)
    booking = live.confirm(token, card)
    live.check("Real-model card confirms only once", live.confirm(token, card)["orderNo"] == booking["orderNo"] and live.ok("GET", "/reservations/my", token)["total"] == 1)
    events = model_chat(token, "我刚刚那笔预约现在是什么状态？请查询系统再告诉我。")
    live.check("Post-confirmation conversation queries actual state", used(events, "query_my_reservations"))
    events = model_chat(token, "请帮我取消刚才那笔预约，先生成取消确认卡片。")
    live.check("Natural cancellation prepares a card", used(events, "prepare_cancel"))
    live.confirm(token, live.first(events, "action"))
    live.check("Model-assisted cancellation persisted", live.ok("GET", "/reservations/my", token)["records"][0]["status"] == "CANCELLED")
    events = model_chat(token, f"帮我报修资产编号为{tag}-EQ的设备，灯不亮还有焦糊味，故障类型写其他，请查设备后生成确认卡片。")
    live.check("Fault report resolves true asset through a tool", used(events, "search_equipment"))
    fault = live.first(events, "action")
    live.check("Model triages dangerous fault using HIGH enum", fault["details"]["紧急程度"] == "HIGH")
    live.check("Fault draft leaves asset unchanged", live.ok("GET", f"/equipments/{equipment['id']}", token)["status"] == "IDLE")
    live.confirm(token, fault)
    live.check("Model-assisted repair confirmation updates real database", live.ok("GET", f"/equipments/{equipment['id']}", token)["status"] == "REPAIR")
    report = {"passed": len(live.passed), "failed": 0, "checks": live.passed, "database": live.DATABASE, "realModelCalled": True, "model": live.ok("GET", "/agent/status", token)["model"]}
    Path("lab-backend/target/agent-model-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"COMPLETE: {len(live.passed)} funded-model/MySQL checks passed.", flush=True)


if __name__ == "__main__":
    main()
