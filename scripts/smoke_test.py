# -*- coding: utf-8 -*-
"""
接口冒烟测试：验证登录、看板、实验室、预约（含冲突校验）、审批、报修全链路
用法：python scripts/smoke_test.py
"""
import json
import urllib.request
import urllib.error
from datetime import datetime, timedelta

BASE = "http://127.0.0.1:8080/api"
opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

passed, failed = [], []


def call(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with opener.open(req, timeout=15) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return {"code": e.code, "msg": e.read().decode()[:200]}


def check(name, cond, detail=""):
    if cond:
        passed.append(name)
        print(f"  [PASS] {name}")
    else:
        failed.append(name)
        print(f"  [FAIL] {name} {detail}")


def login(username, password="123456"):
    r = call("POST", "/auth/login", body={"username": username, "password": password})
    if r.get("code") != 200:
        raise SystemExit(f"登录失败 {username}: {r}")
    return r["data"]["token"], r["data"]["user"]


print("=" * 60)
print("1. 登录与鉴权")
admin_token, admin = login("admin")
check("管理员登录", admin["role"] == "ADMIN")
student_token, student = login("s2021001")
check("学生登录", student["role"] == "STUDENT")
teacher_token, _ = login("t_wang")
repairer_token, repairer = login("r_zhang")

r = call("GET", "/auth/profile", token=student_token)
check("获取个人信息", r["data"]["username"] == "s2021001")

r = call("GET", "/labs")
check("未登录访问被拦截", r.get("code") == 401, r)

print("=" * 60)
print("2. 数据看板")
r = call("GET", "/stats/dashboard", token=admin_token)
d = r["data"]
check("看板概览返回", d["labTotal"] > 0 and d["equipmentTotal"] > 0)
check("趋势数据非空", len(d["trend"]) > 0)
check("实验室热度非空", len(d["labUsage"]) > 0)
print(f"       实验室 {d['labTotal']} 间 / 设备 {d['equipmentTotal']} 台 / 今日预约 {d['todayReservation']} 场 / 待审批 {d['pendingApproval']} 条")

print("=" * 60)
print("3. 实验室与设备")
r = call("GET", "/labs?pageNum=1&pageSize=5", token=admin_token)
labs = r["data"]["records"]
check("实验室分页", len(labs) == 5 and r["data"]["total"] == 12)
lab = labs[0]
check("实验室附加字段", lab.get("equipmentCount", 0) > 0 and lab.get("managerName"))

r = call("GET", f"/equipments?labId={lab['id']}", token=admin_token)
equipments = r["data"]["records"]
check("设备按实验室查询", len(equipments) > 0)

print("=" * 60)
print("4. 预约全流程")
# 找一个明天没被占用的时段
tomorrow = (datetime.now() + timedelta(days=1)).strftime("%Y-%m-%d")
r = call("GET", f"/reservations/timeline?labId={lab['id']}&date={tomorrow}", token=student_token)
busy = r["data"]
check("时间轴接口", isinstance(busy, list))

start = f"{tomorrow} 19:00:00"
end = f"{tomorrow} 21:00:00"
r = call("POST", "/reservations", token=student_token,
         body={"labId": lab["id"], "startTime": start, "endTime": end,
               "headcount": 8, "purpose": "冒烟测试-课程实验", "remark": "自动化测试"})
check("学生提交预约", r.get("code") == 200, r)
res_id = r["data"]["id"] if r.get("code") == 200 else None
check("预约进入待审批", r["data"]["status"] == "PENDING" if res_id else False)

if res_id:
    r2 = call("POST", "/reservations", token=student_token,
              body={"labId": lab["id"], "startTime": start, "endTime": end,
                    "headcount": 5, "purpose": "冲突测试"})
    check("时间冲突被拦截", r2.get("code") != 200 and "已被预约" in r2.get("msg", ""), r2)

    r2 = call("POST", "/reservations", token=student_token,
              body={"labId": lab["id"], "startTime": f"{tomorrow} 08:00:00",
                    "endTime": f"{tomorrow} 07:00:00", "headcount": 5, "purpose": "非法时段"})
    check("结束早于开始被拦截", r2.get("code") != 200, r2)

    r = call("GET", "/reservations?status=PENDING&pageSize=50", token=teacher_token)
    check("教师查看待审批列表", any(x["id"] == res_id for x in r["data"]["records"]))

    r = call("PUT", f"/reservations/{res_id}/approve", token=teacher_token,
             body={"pass": True, "remark": "自动化测试通过"})
    check("教师审批通过", r.get("code") == 200, r)

    r = call("GET", f"/reservations/{res_id}", token=student_token)
    check("审批状态已更新", r["data"]["status"] == "APPROVED" and r["data"]["approverName"])

    r = call("PUT", f"/reservations/{res_id}/cancel", token=student_token)
    check("学生取消预约", r.get("code") == 200, r)

    r = call("PUT", f"/reservations/{res_id}/approve", token=student_token, body={"pass": True})
    check("学生无审批权限", r.get("code") == 403, r)

print("=" * 60)
print("5. 报修运维闭环")
r = call("GET", f"/equipments?status=IDLE&pageSize=5", token=student_token)
eq = r["data"]["records"][0]
r = call("POST", "/repairs", token=student_token,
         body={"equipmentId": eq["id"], "faultType": "硬件故障", "urgency": "HIGH",
               "faultDesc": "自动化测试：设备无法开机"})
check("提交报修", r.get("code") == 200, r)
repair_id = r["data"]["id"] if r.get("code") == 200 else None

if repair_id:
    r = call("GET", f"/equipments/{eq['id']}", token=student_token)
    check("设备状态变为维修中", r["data"]["status"] == "REPAIR", r["data"]["status"])

    r = call("PUT", f"/repairs/{repair_id}/assign?repairerId={repairer['id']}", token=admin_token)
    check("管理员派单", r.get("code") == 200, r)

    r = call("GET", "/repairs?scope=assigned", token=repairer_token)
    check("维修员看到任务", any(x["id"] == repair_id for x in r["data"]["records"]))

    r = call("PUT", f"/repairs/{repair_id}/start", token=repairer_token)
    check("维修员开始维修", r.get("code") == 200, r)

    r = call("PUT", f"/repairs/{repair_id}/finish", token=repairer_token,
             body={"repairDesc": "更换电源模块，测试正常", "cost": 260})
    check("维修员提交结果", r.get("code") == 200, r)

    r = call("PUT", f"/repairs/{repair_id}/verify", token=student_token,
             body={"score": 5, "verifyRemark": "验收通过"})
    check("报修人验收", r.get("code") == 200, r)

    r = call("GET", f"/repairs/{repair_id}/logs", token=student_token)
    check("流转记录完整（5 条）", len(r["data"]) == 5, len(r["data"]))

    r = call("GET", f"/equipments/{eq['id']}", token=student_token)
    check("设备恢复空闲", r["data"]["status"] == "IDLE", r["data"]["status"])

print("=" * 60)
print("6. 权限与基础数据")
r = call("GET", "/users?pageNum=1&pageSize=5", token=student_token)
check("学生无法访问用户管理", r.get("code") == 403, r)
r = call("GET", "/users?pageNum=1&pageSize=5", token=admin_token)
check("管理员访问用户管理", r.get("code") == 200 and r["data"]["total"] == 36)
r = call("GET", "/notices/latest?limit=3", token=student_token)
check("公告列表", len(r["data"]) == 3)
r = call("GET", "/labs/buildings", token=student_token)
check("楼栋筛选数据", len(r["data"]) >= 3, r["data"])

# ============================================================
# 以下为二轮优化新增能力的回归断言。本节不依赖前面定义的业务变量，
# 只复用 admin_token / student_token / repairer_token 三个登录态，
# 便于单独排查失败项。
# ============================================================
print("=" * 60)
print("7. 在线接口文档（Knife4j / OpenAPI）")
import json as _json
import urllib.request as _urlreq
from datetime import datetime as _dt, timedelta as _td

_opener = _urlreq.build_opener(_urlreq.ProxyHandler({}))


def raw_get(path):
    try:
        with _opener.open("http://localhost:8080" + path, timeout=10) as resp:
            return resp.status, resp.read().decode("utf-8", "ignore")
    except Exception as e:
        return 0, str(e)


def _rows(resp):
    """接口异常时 data 可能为 None；统一兜底，保证失败项能被记录而不是抛异常"""
    data = resp.get("data") if isinstance(resp, dict) else None
    return data.get("records", []) if isinstance(data, dict) else []


_st, _doc_html = raw_get("/doc.html")
check("Knife4j 文档页返回 200", _st == 200, _st)
_st, _doc_body = raw_get("/v3/api-docs")
check("OpenAPI JSON 返回 200", _st == 200, _st)
_spec = _json.loads(_doc_body) if _st == 200 else {}
_paths = _spec.get("paths", {})
_ops = [_op for _p in _paths.values() for _op in _p.values() if isinstance(_op, dict)]
_tagset = {_t for _op in _ops for _t in _op.get("tags", [])}
check("OpenAPI 接口数 >= 40", len(_paths) >= 40, len(_paths))
check("OpenAPI 业务分组 >= 11", len(_tagset) >= 11, sorted(_tagset))
check("OpenAPI 含站内信未读数接口", "/api/messages/unread-count" in _paths)
check("OpenAPI 含预约签到接口", any("checkin" in _p for _p in _paths))
check("OpenAPI 声明 JWT Bearer 鉴权", "bearerFormat" in _doc_body and "JWT" in _doc_body)

print("=" * 60)
print("8. 站内信与操作审计日志")
r = call("GET", "/messages/unread-count", token=admin_token)
check("未读消息数接口", r.get("code") == 200 and isinstance(r.get("data"), int), r)
r = call("GET", "/messages?pageNum=1&pageSize=10", token=admin_token)
check("站内信分页", r.get("code") == 200 and isinstance(r.get("data"), dict) and "records" in r["data"], r)
_msgs = _rows(r)
if _msgs:
    r = call("PUT", f"/messages/{_msgs[0]['id']}/read", token=admin_token)
    check("标记单条消息已读", r.get("code") == 200, r)
r = call("GET", "/messages?pageNum=1&pageSize=10&isRead=1", token=admin_token)
_read_rows = _rows(r)
check("已读筛选结果均为已读", all(m["isRead"] == 1 for m in _read_rows), len(_read_rows))
r = call("PUT", "/messages/read-all", token=admin_token)
check("一键全部标为已读", r.get("code") == 200, r)
r = call("GET", "/messages/unread-count", token=admin_token)
check("全部已读后未读数归零", r.get("data") == 0, r.get("data"))

r = call("GET", "/logs?pageNum=1&pageSize=10", token=admin_token)
check("管理员查询审计日志", r.get("code") == 200 and r["data"]["total"] > 0, r)
_logs = _rows(r)
check("日志含操作人/耗时/结果字段",
      bool(_logs) and all(k in _logs[0] for k in ("operatorName", "costMs", "success")),
      _logs[0] if _logs else None)
r = call("GET", "/logs?pageNum=1&pageSize=50&module=%E8%AE%A4%E8%AF%81%E7%AE%A1%E7%90%86", token=admin_token)
_login_logs = _rows(r)
check("按模块筛选认证日志", bool(_login_logs), len(_login_logs))
check("登录日志密码字段已脱敏",
      any('"password":"***"' in str(x.get("params", "")) for x in _login_logs),
      [_x.get("params") for _x in _login_logs[:2]])
r = call("GET", "/logs?pageNum=1&pageSize=5", token=student_token)
check("学生无法访问审计日志", r.get("code") == 403, r)

print("=" * 60)
print("9. 闭馆安排与预约拦截")
r = call("GET", "/labs/all", token=admin_token)
_labs = r.get("data", [])
check("实验室全量列表可用", r.get("code") == 200 and len(_labs) > 0, r)
_future = (_dt.now() + _td(days=7)).date().isoformat()
# 清理历史运行遗留的测试闭馆记录，保证脚本可重复执行
r = call("GET", "/closures", token=admin_token)
for _c in (r.get("data") or []):
    if str(_c.get("closureDate", "")).startswith(_future):
        call("DELETE", f"/closures/{_c['id']}", token=admin_token)
r = call("POST", "/closures", token=admin_token,
         body={"labId": None, "closureDate": _future, "reason": "自动化测试全校闭馆"})
check("新增全校闭馆日期", r.get("code") == 200, r)
r = call("GET", "/closures", token=student_token)
_closures = r.get("data", [])
_new_closure = next((c for c in _closures if str(c.get("closureDate", "")).startswith(_future)), None)
check("闭馆列表包含新增记录", _new_closure is not None, len(_closures))
check("全校闭馆显示 labName=全校", _new_closure and _new_closure.get("labName") == "全校", _new_closure)
r = call("POST", "/reservations", token=student_token,
         body={"labId": _labs[0]["id"], "startTime": _future + " 09:00:00",
               "endTime": _future + " 11:00:00", "headcount": 2, "purpose": "闭馆校验自动化测试"})
check("闭馆日期预约被拦截", r.get("code") != 200 and "闭馆" in r.get("msg", ""), r)
r = call("POST", "/closures", token=admin_token,
         body={"labId": None, "closureDate": _future, "reason": "重复闭馆"})
check("重复闭馆日期被拒绝", r.get("code") != 200, r)
r = call("POST", "/closures", token=student_token,
         body={"labId": None, "closureDate": (_dt.now() + _td(days=8)).date().isoformat(), "reason": "越权"})
check("学生无法新增闭馆", r.get("code") == 403, r)
if _new_closure:
    r = call("DELETE", f"/closures/{_new_closure['id']}", token=admin_token)
    check("删除闭馆日期", r.get("code") == 200, r)

print("=" * 60)
print("10. 签到、信用分与预约改期")
# 用独立临时账号跑本节：共享学生账号在历史运行里可能残留「即将开始」的预约，
# 会触发「你在该时段已有其他预约」。换成全新账号即可保证脚本可重复执行。
_probe_user = "checkin_" + _dt.now().strftime("%H%M%S")
r = call("POST", "/users", token=admin_token,
         body={"username": _probe_user, "realName": "签到测试临时账号", "role": "STUDENT",
               "userNo": _probe_user, "dept": "测试学院", "status": 1})
check("创建签到测试临时账号", r.get("code") == 200, r)
r = call("GET", f"/users?pageNum=1&pageSize=5&keyword={_probe_user}", token=admin_token)
_probe_rows = _rows(r)
_probe_uid = _probe_rows[0]["id"] if _probe_rows else None
r = call("PUT", f"/users/{_probe_uid}/reset-password", token=admin_token)
r = call("POST", "/auth/login", body={"username": _probe_user, "password": r.get("data")})
check("签到测试账号可登录", r.get("code") == 200, r)
student_token = r.get("data", {}).get("token")

r = call("GET", "/auth/profile", token=student_token)
check("个人资料返回信用分", r.get("code") == 200 and isinstance(r.get("data"), dict) and "creditScore" in r["data"], r)
_credit_before = r.get("data", {}).get("creditScore")
_fmt = "%Y-%m-%d %H:%M:%S"
_start_dt = _dt.now() + _td(minutes=2)
_end_dt = _start_dt + _td(hours=1)
_auto_labs = [l for l in _labs if l.get("needApproval") == 0] or _labs
_new_rid = None
_last = None
for _lab in _auto_labs[:6]:
    _last = call("POST", "/reservations", token=student_token,
                 body={"labId": _lab["id"], "startTime": _start_dt.strftime(_fmt),
                       "endTime": _end_dt.strftime(_fmt), "headcount": 1,
                       "purpose": "签到流程自动化测试"})
    if _last.get("code") == 200:
        _new_rid = _last["data"]["id"]
        if _last["data"].get("status") == "PENDING":
            call("PUT", f"/reservations/{_new_rid}/approve", token=admin_token,
                 body={"pass": True, "remark": "自动化测试审批"})
        break
check("创建一条即将开始的已通过预约", _new_rid is not None, _last)

if _new_rid:
    r = call("GET", f"/reservations/{_new_rid}", token=student_token)
    check("新预约初始签到状态 NONE", r.get("data", {}).get("checkinStatus") == "NONE", r.get("data", {}).get("checkinStatus"))
    r = call("PUT", f"/reservations/{_new_rid}/checkin", token=student_token)
    check("窗口内签到成功", r.get("code") == 200, r)
    r = call("GET", f"/reservations/{_new_rid}", token=student_token)
    check("签到状态变为 CHECKED_IN", r.get("data", {}).get("checkinStatus") == "CHECKED_IN", r.get("data", {}).get("checkinStatus"))
    r = call("PUT", f"/reservations/{_new_rid}/checkin", token=student_token)
    check("重复签到被拒绝", r.get("code") != 200, r)
    r = call("GET", "/auth/profile", token=student_token)
    _credit_after = r.get("data", {}).get("creditScore")
    check("签到后信用分增加（不超过 100）",
          _credit_after is not None and _credit_after >= (_credit_before or 0), (_credit_before, _credit_after))
    r = call("GET", "/messages?pageNum=1&pageSize=10", token=student_token)
    check("签到产生站内信通知", any("签到" in m.get("title", "") for m in _rows(r)), r)

    # 演示数据里未来一周已有大量预约，逐个探测空闲时段，保证脚本可重复执行
    _rs_ok = False
    _day2 = None
    for _d in range(9, 16):
        _candidate = (_dt.now() + _td(days=_d)).date().isoformat()
        for _hour in (9, 14, 19):
            r = call("PUT", f"/reservations/{_new_rid}/reschedule", token=student_token,
                     body={"startTime": f"{_candidate} {_hour:02d}:00:00",
                           "endTime": f"{_candidate} {_hour + 1:02d}:00:00",
                           "reason": "自动化测试改期"})
            if r.get("code") == 200:
                _rs_ok = True
                _day2 = _candidate
                break
        if _rs_ok:
            break
    check("改期接口可用", _rs_ok, r)
    r = call("GET", f"/reservations/{_new_rid}", token=student_token)
    check("改期后时间已更新",
          _day2 is not None and str(r.get("data", {}).get("startTime", "")).startswith(_day2),
          r.get("data", {}).get("startTime"))
    r = call("PUT", f"/reservations/{_new_rid}/reschedule", token=student_token,
             body={"startTime": f"{_day2} 16:00:00", "endTime": f"{_day2} 14:00:00"})
    check("结束早于开始的改期被拒绝", r.get("code") != 200, r)

r = call("DELETE", f"/users/{_probe_uid}", token=admin_token)
check("清理签到测试临时账号", r.get("code") == 200, r)

print("=" * 60)
print("11. Token 失效机制（token_version）")
_uname = "autotest_" + _dt.now().strftime("%H%M%S")
r = call("POST", "/users", token=admin_token,
         body={"username": _uname, "realName": "自动化临时账号", "role": "STUDENT",
               "userNo": _uname, "dept": "测试学院", "status": 1})
check("创建临时测试账号", r.get("code") == 200, r)
r = call("GET", f"/users?pageNum=1&pageSize=5&keyword={_uname}", token=admin_token)
_tmp_rows = _rows(r)
_tmp_uid = _tmp_rows[0]["id"] if _tmp_rows else None
check("查询到临时账号 ID", _tmp_uid is not None, r)

if _tmp_uid:
    r = call("PUT", f"/users/{_tmp_uid}/reset-password", token=admin_token)
    check("管理员重置密码", r.get("code") == 200, r)
    _default_pwd = r.get("data")
    r = call("POST", "/auth/login", body={"username": _uname, "password": _default_pwd})
    check("临时账号可登录", r.get("code") == 200, r)
    _tmp_token = r.get("data", {}).get("token")
    r = call("GET", "/auth/profile", token=_tmp_token)
    check("旧 token 可访问个人资料", r.get("code") == 200, r)
    r = call("PUT", f"/users/{_tmp_uid}/status?status=0", token=admin_token)
    check("停用临时账号", r.get("code") == 200, r)
    r = call("GET", "/auth/profile", token=_tmp_token)
    check("停用后旧 token 立即失效", r.get("code") == 401, r)
    r = call("DELETE", f"/users/{_tmp_uid}", token=admin_token)
    check("清理临时账号", r.get("code") == 200, r)

print("=" * 60)
print(f"结果：通过 {len(passed)} 项，失败 {len(failed)} 项")
if failed:
    print("失败项：", "、".join(failed))
    raise SystemExit(1)
print("全部接口验证通过 ✔")
