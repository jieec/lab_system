# -*- coding: utf-8 -*-
"""
生成演示数据 SQL（相对当前时间，导入后数据始终是"活的"）
用法：python scripts/gen_demo_data.py > lab-backend/src/main/resources/sql/data.sql
"""
import random
from datetime import date, timedelta

random.seed(20261021)

PWD = "$2a$10$kKIquzqcpxOiyDjwLiLjB.runGs/AnwtkN9tuXJ4L1//lmDxLFsSK"  # 123456

out = []
out.append("-- =============================================================")
out.append("-- 演示数据（相对导入时间生成，可直接重复导入）")
out.append("-- 所有账号初始密码：123456")
out.append("-- =============================================================")
out.append("USE lab_system;")
out.append("SET NAMES utf8mb4;")
out.append("SET FOREIGN_KEY_CHECKS = 0;")
out.append("TRUNCATE TABLE sys_log;")
out.append("TRUNCATE TABLE message;")
out.append("TRUNCATE TABLE lab_closure;")
out.append("TRUNCATE TABLE repair_log;")
out.append("TRUNCATE TABLE repair_order;")
out.append("TRUNCATE TABLE reservation;")
out.append("TRUNCATE TABLE equipment;")
out.append("TRUNCATE TABLE notice;")
out.append("TRUNCATE TABLE lab;")
out.append("TRUNCATE TABLE sys_user;")
out.append("SET FOREIGN_KEY_CHECKS = 1;")


def dt(day_offset, hour, minute=0):
    """返回相对今天的 datetime 表达式"""
    parts = ["CURDATE()"]
    if day_offset:
        parts.append(f"INTERVAL {day_offset} DAY")
    parts.append(f"INTERVAL {hour} HOUR")
    if minute:
        parts.append(f"INTERVAL {minute} MINUTE")
    expr = parts[0]
    for p in parts[1:]:
        expr = f"DATE_ADD({expr}, {p})"
    return expr


# ---------------------------------------------------------------- 用户
teachers = [
    ("t_wang", "王建国", "T2001", "计算机科学与技术学院", "13800001001"),
    ("t_li", "李慧敏", "T2002", "计算机科学与技术学院", "13800001002"),
    ("t_zhang", "张伟", "T2003", "电子信息工程学院", "13800001003"),
    ("t_chen", "陈晓东", "T2004", "电子信息工程学院", "13800001004"),
    ("t_liu", "刘芳", "T2005", "化学与材料学院", "13800001005"),
    ("t_zhao", "赵鹏", "T2006", "机械工程学院", "13800001006"),
    ("t_sun", "孙丽", "T2007", "生物医学工程学院", "13800001007"),
    ("t_zhou", "周明", "T2008", "物理与光电学院", "13800001008"),
]

students = [
    ("s2021001", "杨帆", "2021010101", "计算机科学与技术学院"),
    ("s2021002", "林小雨", "2021010102", "计算机科学与技术学院"),
    ("s2021003", "黄子豪", "2021010103", "计算机科学与技术学院"),
    ("s2021004", "吴佳怡", "2021010104", "计算机科学与技术学院"),
    ("s2021005", "徐志强", "2021010105", "计算机科学与技术学院"),
    ("s2021006", "马晓雯", "2021010106", "计算机科学与技术学院"),
    ("s2022001", "朱俊杰", "2021020201", "电子信息工程学院"),
    ("s2022002", "胡梦洁", "2021020202", "电子信息工程学院"),
    ("s2022003", "郭子轩", "2021020203", "电子信息工程学院"),
    ("s2022004", "何雨欣", "2021020204", "电子信息工程学院"),
    ("s2022005", "高宇航", "2021020205", "电子信息工程学院"),
    ("s2022006", "罗诗涵", "2021020206", "电子信息工程学院"),
    ("s2023001", "郑凯文", "2021030301", "化学与材料学院"),
    ("s2023002", "梁思远", "2021030302", "化学与材料学院"),
    ("s2023003", "谢婉清", "2021030303", "化学与材料学院"),
    ("s2023004", "宋博文", "2021030304", "化学与材料学院"),
    ("s2023005", "唐嘉豪", "2021030305", "机械工程学院"),
    ("s2023006", "许若彤", "2021030306", "机械工程学院"),
    ("s2023007", "邓子涵", "2021030307", "机械工程学院"),
    ("s2023008", "冯静怡", "2021030308", "生物医学工程学院"),
    ("s2023009", "曹天宇", "2021030309", "生物医学工程学院"),
    ("s2023010", "彭雅琳", "2021030310", "物理与光电学院"),
    ("s2023011", "蒋子墨", "2021030311", "物理与光电学院"),
    ("s2023012", "韩雪儿", "2021030312", "计算机科学与技术学院"),
]

repairers = [
    ("r_zhang", "张师傅", "W3001", "后勤保障处", "13900002001"),
    ("r_liu", "刘工", "W3002", "后勤保障处", "13900002002"),
    ("r_wu", "吴工", "W3003", "设备管理处", "13900002003"),
]

users = [("admin", "系统管理员", "ADMIN", "A0001", "设备管理处", "13700000000")]
for i, (u, n, no, dept, phone) in enumerate(teachers, start=1):
    users.append((u, n, "TEACHER", no, dept, phone))
for i, (u, n, no, dept) in enumerate(students, start=1):
    users.append((u, n, "STUDENT", no, dept, "138%08d" % (10000000 + i)))
for u, n, no, dept, phone in repairers:
    users.append((u, n, "REPAIRER", no, dept, phone))

# 信用分演示：id 30 近 7 天爽约 3 次（触发「暂停预约」），id 31 爽约 1 次
CREDIT_DEMO = {30: (70, 3), 31: (90, 1)}

out.append("\n-- 用户 -------------------------------------------------------")
rows = []
for idx, (u, n, role, no, dept, phone) in enumerate(users, start=1):
    email = f"{u}@university.edu.cn"
    credit, violations = CREDIT_DEMO.get(idx, (100, 0))
    rows.append(
        f"('{u}','{PWD}','{n}','{role}','{no}','{dept}','{phone}','{email}',NULL,1,0,{credit},{violations},"
        f"{dt(-180, 9)}, {dt(-180, 9)})"
    )
out.append(
    "INSERT INTO sys_user (username,password,real_name,role,user_no,dept,phone,email,avatar,status,"
    "token_version,credit_score,violation_count,create_time,update_time) VALUES\n"
    + ",\n".join(rows) + ";"
)

# ---------------------------------------------------------------- 实验室
labs = [
    ("LAB-A301", "计算机基础实验室", "实验楼A", "301", 60, "08:00:00", "22:00:00", 1, 2,
     "配备 60 台高性能工作站，面向程序设计、数据结构等课程实验。"),
    ("LAB-A302", "网络工程实验室", "实验楼A", "302", 40, "08:00:00", "22:00:00", 1, 2,
     "拥有思科路由器、交换机与防火墙设备，支持网络协议与安全实验。"),
    ("LAB-A305", "人工智能实验室", "实验楼A", "305", 30, "08:30:00", "21:30:00", 1, 3,
     "配置 GPU 服务器与深度学习框架，用于机器学习、计算机视觉研究。"),
    ("LAB-B201", "电子电路实验室", "实验楼B", "201", 45, "08:00:00", "21:00:00", 1, 4,
     "提供示波器、信号发生器、直流电源等基础电子测量设备。"),
    ("LAB-B203", "嵌入式系统实验室", "实验楼B", "203", 36, "08:00:00", "21:00:00", 1, 4,
     "面向单片机、ARM 开发与物联网终端实验。"),
    ("LAB-B208", "通信原理实验室", "实验楼B", "208", 32, "09:00:00", "21:00:00", 1, 5,
     "通信信号调制解调、光纤通信与射频测试平台。"),
    ("LAB-C101", "化学分析实验室", "实验楼C", "101", 24, "08:00:00", "20:00:00", 1, 6,
     "配备分光光度计、色谱仪等分析仪器，需通过安全培训后使用。"),
    ("LAB-C103", "材料检测实验室", "实验楼C", "103", 20, "08:30:00", "20:00:00", 1, 6,
     "材料力学性能与微观结构检测平台。"),
    ("LAB-C201", "生物医学实验室", "实验楼C", "201", 28, "08:00:00", "20:30:00", 1, 7,
     "细胞培养、生化分析与医学信号采集实验环境。"),
    ("LAB-D102", "机械加工实训室", "实训楼D", "102", 30, "08:00:00", "20:00:00", 1, 8,
     "数控机床、3D 打印与钳工实训工位，需教师陪同。"),
    ("LAB-D105", "虚拟仿真实训室", "实训楼D", "105", 50, "08:00:00", "22:00:00", 0, 8,
     "VR 头显与仿真软件平台，免审批，刷卡即用。"),
    ("LAB-D201", "创新创业工作室", "实训楼D", "201", 24, "09:00:00", "22:00:00", 0, 9,
     "开放共享工位，支持学生科创项目长期驻留。"),
]

out.append("\n-- 实验室 -----------------------------------------------------")
rows = []
for code, name, building, room, cap, open_t, close_t, need, manager, desc in labs:
    rows.append(
        f"('{code}','{name}','{building}','{room}',{cap},'{open_t}','{close_t}',{need},{manager},1,'{desc}',"
        f"{dt(-200, 9)}, {dt(-200, 9)})"
    )
out.append(
    "INSERT INTO lab (code,name,building,room,capacity,open_time,close_time,need_approval,manager_id,status,description,create_time,update_time) VALUES\n"
    + ",\n".join(rows) + ";"
)

# ---------------------------------------------------------------- 设备
equipment_pool = {
    1: [("台式工作站", "计算设备", "Dell", "Precision 3660", 9800, 20),
        ("投影一体机", "多媒体设备", "Epson", "CB-L200F", 7600, 2)],
    2: [("核心交换机", "网络设备", "Cisco", "C9300-48P", 32000, 4),
        ("路由器实验套件", "网络设备", "H3C", "MSR36-20", 8600, 12),
        ("网络测试仪", "仪器仪表", "Fluke", "LinkIQ-100", 15000, 4)],
    3: [("GPU 服务器", "计算设备", "浪潮", "NF5468M6", 168000, 2),
        ("深度学习工作站", "计算设备", "联想", "ThinkStation P620", 46000, 8),
        ("机器视觉套件", "实验装置", "海康", "MV-CS060", 6800, 10)],
    4: [("数字示波器", "仪器仪表", "Tektronix", "MSO46", 42000, 10),
        ("函数信号发生器", "仪器仪表", "RIGOL", "DG1062Z", 3600, 12),
        ("直流稳压电源", "仪器仪表", "Keysight", "E36312A", 7200, 12),
        ("万用表", "仪器仪表", "Fluke", "17B+", 900, 20)],
    5: [("ARM 开发板", "实验装置", "正点原子", "STM32MP157", 1200, 24),
        ("单片机实验箱", "实验装置", "百科荣创", "BK-2000", 4800, 16),
        ("逻辑分析仪", "仪器仪表", "Saleae", "Logic Pro 16", 5200, 6)],
    6: [("频谱分析仪", "仪器仪表", "R&S", "FPC1500", 68000, 3),
        ("光纤熔接机", "实验装置", "藤仓", "FSM-80S", 26000, 2),
        ("射频信号源", "仪器仪表", "Keysight", "N5172B", 88000, 2)],
    7: [("紫外分光光度计", "分析仪器", "岛津", "UV-2600i", 96000, 3),
        ("高效液相色谱仪", "分析仪器", "安捷伦", "1260 Infinity", 320000, 2),
        ("电子天平", "分析仪器", "梅特勒", "ME204E", 12000, 6),
        ("通风橱", "实验装置", "ESCO", "AHC-4D1", 38000, 4)],
    8: [("万能材料试验机", "分析仪器", "Instron", "68TM-30", 480000, 1),
        ("金相显微镜", "分析仪器", "奥林巴斯", "BX53M", 86000, 3),
        ("硬度计", "仪器仪表", "时代", "TH160", 18000, 2)],
    9: [("生物信号采集系统", "实验装置", "泰盟", "BL-420N", 26000, 8),
        ("高速冷冻离心机", "分析仪器", "Eppendorf", "5424R", 42000, 3),
        ("超净工作台", "实验装置", "苏净安泰", "SW-CJ-2FD", 22000, 4)],
    10: [("数控加工中心", "机械加工", "海天", "VMC850", 380000, 2),
         ("3D 打印机", "机械加工", "Raise3D", "Pro3", 28000, 4),
         ("激光切割机", "机械加工", "大族", "CO2-6040", 56000, 2)],
    11: [("VR 头显", "多媒体设备", "HTC", "VIVE Pro 2", 12800, 12),
         ("仿真工作站", "计算设备", "HP", "Z4 G5", 26000, 8)],
    12: [("多功能打印一体机", "办公设备", "惠普", "M479fdw", 4200, 2),
         ("会议平板", "多媒体设备", "MAXHUB", "V6-65", 18000, 2)],
}

out.append("\n-- 设备 -------------------------------------------------------")
rows = []
eq_id = 1
eq_index = []  # (id, lab_id, status)
for lab_id in range(1, 13):
    for name, cat, brand, model, price, count in equipment_pool[lab_id]:
        for k in range(1, count + 1):
            code = f"EQ{lab_id:02d}{eq_id:04d}"
            status = "IDLE"
            rows.append(
                f"('{code}','{name}','{cat}',{lab_id},'{model}','{brand}','{2021 + (eq_id % 4)}-0{1 + (eq_id % 9)}-1{0 + (eq_id % 9)}',"
                f"{price},'{status}','{teachers[lab_id % len(teachers)][1]}','{name} {model}',"
                f"{dt(-160, 10)}, {dt(-160, 10)})"
            )
            eq_index.append([eq_id, lab_id, code, name])
            eq_id += 1
out.append(
    "INSERT INTO equipment (code,name,category,lab_id,model,brand,purchase_date,price,status,keeper,description,create_time,update_time) VALUES\n"
    + ",\n".join(rows) + ";"
)

# ---------------------------------------------------------------- 预约
out.append("\n-- 预约 -------------------------------------------------------")
lab_open = {i + 1: (int(labs[i][5][:2]), int(labs[i][6][:2])) for i in range(len(labs))}
lab_need = {i + 1: labs[i][7] for i in range(len(labs))}

purposes = [
    "课程实验：数据结构与算法", "毕业设计课题实验", "大学生创新创业项目调试",
    "学科竞赛集训", "课程设计答辩预演", "科研项目数据采集",
    "实验室开放日参观学习", "小组课程作业讨论", "算法模型训练与验证",
    "设备操作练习", "毕业论文实验", "社团技术分享会",
]
student_ids = list(range(10, 34))
teacher_ids = list(range(2, 10))
approver_ids = [1] + teacher_ids

res_rows = []
res_no = 1
occupied = {}  # (lab_id, day_offset) -> [(start_min, end_min)]


def pick_slot(lab_id, day_offset):
    open_h, close_h = lab_open[lab_id]
    key = (lab_id, day_offset)
    used = occupied.setdefault(key, [])
    for _ in range(12):
        start = random.randint(open_h, close_h - 2) * 60 + random.choice([0, 30])
        dur = random.choice([60, 90, 120, 150, 180])
        end = start + dur
        if end > close_h * 60:
            continue
        if any(not (end <= s or start >= e) for s, e in used):
            continue
        used.append((start, end))
        return start, end
    return None


for day in range(-28, 8):
    for lab_id in range(1, 13):
        n = random.choices([0, 1, 2, 3], weights=[35, 45, 17, 3])[0]
        for _ in range(n):
            slot = pick_slot(lab_id, day)
            if not slot:
                continue
            start_m, end_m = slot
            user_id = random.choice(student_ids + teacher_ids)
            if day < -1:
                status = random.choices(["FINISHED", "CANCELLED", "REJECTED"], weights=[85, 10, 5])[0]
            elif day <= 0:
                status = random.choices(["FINISHED", "APPROVED", "PENDING"], weights=[45, 40, 15])[0]
            else:
                status = random.choices(["APPROVED", "PENDING"], weights=[70, 30])[0]

            approver = random.choice(approver_ids) if status in ("APPROVED", "REJECTED", "FINISHED") else None
            if not approver:
                approve_time = "NULL"
            elif day >= 0:
                # 未来预约的审批时间必须落在当前时间之前
                approve_time = f"DATE_SUB(NOW(), INTERVAL {random.randint(1, 48)} HOUR)"
            else:
                approve_time = dt(day - random.randint(1, 3), random.randint(9, 18), random.choice([0, 15, 30, 45]))
            approve_remark = "NULL"
            if status == "REJECTED":
                approve_remark = "'该时段实验室已安排教学任务，请调整时间'"
            elif status in ("APPROVED", "FINISHED"):
                approve_remark = "'同意，请按时到场并遵守实验室管理规定'"

            if day >= 0:
                create_time = f"DATE_SUB(NOW(), INTERVAL {random.randint(2, 96)} HOUR)"
            else:
                create_time = dt(day - random.randint(1, 4), random.randint(8, 21), random.choice([0, 10, 20, 40]))
            purpose = random.choice(purposes)
            headcount = random.randint(4, min(30, labs[lab_id - 1][4]))
            order_no = "RS2026%06d" % res_no
            # 已结束的历史预约一律标记「已签到」。若留 NONE，定时爽约扫描会把
            # 几百条历史记录误判为爽约并扣光所有学生的信用分。
            if day <= 0 and status in ("FINISHED", "APPROVED"):
                checkin_status = "CHECKED_IN"
                checkin_time = dt(day, start_m // 60, start_m % 60 + 5)
            else:
                checkin_status = "NONE"
                checkin_time = "NULL"
            res_rows.append(
                f"('{order_no}',{user_id},{lab_id},{dt(day, start_m // 60, start_m % 60)},"
                f"{dt(day, end_m // 60, end_m % 60)},{headcount},'{purpose}',NULL,'{status}',"
                f"{approver if approver else 'NULL'},{approve_time},{approve_remark},"
                f"{checkin_time},'{checkin_status}',{create_time},{create_time})"
            )
            res_no += 1

out.append(
    "INSERT INTO reservation (order_no,user_id,lab_id,start_time,end_time,headcount,purpose,remark,status,"
    "approver_id,approve_time,approve_remark,checkin_time,checkin_status,create_time,update_time) VALUES\n"
    + ",\n".join(res_rows) + ";"
)

# 信用分演示：4 条近期爽约记录，与 CREDIT_DEMO 的扣分/违约次数一一对应
out.append("\n-- 信用分演示：近期爽约 -----------------------------------------")
ns_rows = []
ns_no = 1
for uid, days in ((30, (-2, -4, -6)), (31, (-3,))):
    for day in days:
        ns_rows.append(
            f"('RSNS{uid:02d}{ns_no:02d}',{uid},11,{dt(day, 9, 0)},{dt(day, 11, 0)},4,"
            f"'课程实验：爽约演示',NULL,'FINISHED',1,{dt(day - 1, 10, 0)},'同意',NULL,'NO_SHOW',"
            f"{dt(day - 2, 10, 0)},{dt(day - 2, 10, 0)})"
        )
        ns_no += 1
out.append(
    "INSERT INTO reservation (order_no,user_id,lab_id,start_time,end_time,headcount,purpose,remark,status,"
    "approver_id,approve_time,approve_remark,checkin_time,checkin_status,create_time,update_time) VALUES\n"
    + ",\n".join(ns_rows) + ";"
)

# ---------------------------------------------------------------- 报修工单
out.append("\n-- 报修工单 ---------------------------------------------------")
faults = {
    "硬件故障": ["设备开机后无显示，电源指示灯不亮", "设备运行时异响，疑似风扇轴承损坏",
                 "接口松动，数据线连接后无法识别", "按键失灵，无法完成正常操作"],
    "软件故障": ["系统启动报错，无法进入桌面环境", "实验软件授权过期，无法打开",
                 "驱动异常，设备管理器中显示黄色感叹号", "软件运行中频繁闪退"],
    "耗材缺失": ["打印纸已用完，需补充", "实验耗材试剂不足", "备用探头损坏需更换"],
    "其他": ["设备表面有污渍，需要清洁保养", "设备摆放位置需调整"],
}
urgency_map = {"硬件故障": "HIGH", "软件故障": "NORMAL", "耗材缺失": "LOW", "其他": "LOW"}
statuses = (["VERIFIED"] * 14 + ["DONE"] * 3 + ["REPAIRING"] * 4 + ["ASSIGNED"] * 3 + ["PENDING"] * 2)
random.shuffle(statuses)

rep_rows = []
log_rows = []
rep_no = 1
used_eq = random.sample([e[0] for e in eq_index], len(statuses))
repairer_ids = [34, 35, 36]

for idx, st in enumerate(statuses):
    eid = used_eq[idx]
    lab_id = next(e[1] for e in eq_index if e[0] == eid)
    eq_name = next(e[3] for e in eq_index if e[0] == eid)
    ftype = random.choice(list(faults.keys()))
    desc = random.choice(faults[ftype])
    reporter = random.choice(student_ids + teacher_ids)
    day = -random.randint(1, 25)
    create_hour = random.randint(9, 14)
    create_time = dt(day, create_hour, random.choice([0, 20, 40]))
    repairer = None
    assign_time = "NULL"
    finish_time = "NULL"
    repair_desc_text = None
    verify_remark_text = None
    cost = "NULL"
    score = "NULL"
    verify_time = "NULL"
    action_logs = [(create_time, "CREATE", reporter, f"提交报修：{desc}")]

    if st != "PENDING":
        repairer = random.choice(repairer_ids)
        assign_time = dt(day, min(create_hour + random.randint(1, 3), 20), random.choice([10, 30, 50]))
        action_logs.append((assign_time, "ASSIGN", 1, f"派单给维修员：{next(r[1] for r in repairers if repairers.index(r) + 34 == repairer)}"))
    if st in ("REPAIRING", "DONE", "VERIFIED"):
        start_time = dt(day + 1, random.randint(9, 15), 0)
        action_logs.append((start_time, "START", repairer, "维修员开始处理工单"))
    if st in ("DONE", "VERIFIED"):
        finish_time = dt(day + 1, random.randint(15, 19), random.choice([0, 30]))
        repair_desc_text = random.choice([
            "更换损坏部件并完成测试，设备恢复正常", "重装系统与驱动，功能验证通过",
            "补充耗材并校准参数，运行正常", "清洁保养后重新校准，指标合格",
        ])
        cost = random.choice([0, 0, 120, 260, 480, 850, 1500])
        action_logs.append((finish_time, "FINISH", repairer, f"维修完成：{repair_desc_text}"))
    if st == "VERIFIED":
        verify_time = dt(day + 2, random.randint(9, 17), 0)
        score = random.choice([5, 5, 5, 4, 4, 3])
        verify_remark_text = random.choice(["维修及时，设备已恢复正常", "问题已解决，可以正常使用", "维修效果良好，感谢"])
        action_logs.append((verify_time, "VERIFY", reporter, f"报修人验收通过，评分 {score} 分"))

    repair_desc = f"'{repair_desc_text}'" if repair_desc_text else "NULL"
    verify_remark = f"'{verify_remark_text}'" if verify_remark_text else "NULL"

    order_no = "RP2026%05d" % rep_no
    rep_rows.append(
        f"('{order_no}',{eid},{lab_id},{reporter},'{ftype}','{urgency_map[ftype]}','{desc}','{st}',"
        f"{repairer if repairer else 'NULL'},{assign_time},{finish_time},{repair_desc},{cost},{score},"
        f"{verify_remark},{verify_time},{create_time},{create_time})"
    )
    for t, action, op, content in action_logs:
        op_name = next(u[1] for u in users if users.index(u) + 1 == op)
        log_rows.append(f"({rep_no},{op},'{op_name}','{action}','{content}',{t})")
    rep_no += 1

out.append(
    "INSERT INTO repair_order (order_no,equipment_id,lab_id,reporter_id,fault_type,urgency,fault_desc,status,repairer_id,assign_time,finish_time,repair_desc,cost,score,verify_remark,verify_time,create_time,update_time) VALUES\n"
    + ",\n".join(rep_rows) + ";"
)

# 修正 repair_log 中的操作人姓名引号
fixed_logs = []
for row in log_rows:
    fixed_logs.append(row.replace("'", "'"))
out.append("\n-- 工单流转记录 -------------------------------------------------")
out.append(
    "INSERT INTO repair_log (order_id,operator_id,operator_name,action,content,create_time) VALUES\n"
    + ",\n".join(log_rows) + ";"
)

# 同步设备状态：有未完成工单的设备置为维修中
repair_eq = []
for idx, st in enumerate(statuses):
    if st != "VERIFIED":
        repair_eq.append(used_eq[idx])
if repair_eq:
    out.append("\nUPDATE equipment SET status = 'REPAIR' WHERE id IN (" + ",".join(map(str, repair_eq)) + ");")
out.append("UPDATE equipment SET status = 'IN_USE' WHERE id IN (" +
           ",".join(map(str, random.sample([e[0] for e in eq_index if e[0] not in repair_eq], 12))) + ");")
out.append("UPDATE equipment SET status = 'SCRAPPED' WHERE id IN (" +
           ",".join(map(str, random.sample([e[0] for e in eq_index if e[0] not in repair_eq], 5))) + ");")

# ---------------------------------------------------------------- 公告
out.append("\n-- 通知公告 ---------------------------------------------------")
notices = [
    ("关于开展 2026 年秋季实验室安全培训的通知",
     "为落实实验室安全管理制度，所有进入化学、生物类实验室的同学须先完成线上安全培训与考核。请于本月 30 日前登录系统完成学习，考核合格后方可预约相关实验室。", "NOTICE", -3),
    ("实验楼 B 区网络设备维护公告",
     "实验楼 B 区将于本周六 08:00-12:00 进行核心交换机升级，期间网络工程实验室暂停开放，已提交的预约将由管理员统一调整，请留意站内通知。", "MAINTENANCE", -6),
    ("大型仪器设备共享预约规则调整",
     "自下月起，单价 10 万元以上设备单次预约时长上限调整为 4 小时，连续预约需间隔 1 小时，以便更多师生使用。", "NOTICE", -9),
    ("实验室预约系统上线试运行说明",
     "本系统已上线试运行，支持实验室查询、在线预约、审批流转、设备报修等功能。使用中如遇问题，请联系设备管理处（电话：8888）。", "SYSTEM", -20),
    ("虚拟仿真实训室开放免审批预约",
     "为提升使用效率，虚拟仿真实训室（LAB-D105）与创新创业工作室（LAB-D201）调整为免审批模式，提交预约后即时生效，请按预约时段刷卡入场。", "NOTICE", -12),
    ("关于加强实验设备日常巡检的通知",
     "各实验室负责人需每周对管辖设备进行一次巡检，发现异常及时在系统中提交报修工单，维修完成后由报修人验收确认。", "NOTICE", -1),
]
rows = []
for title, content, ntype, day in notices:
    rows.append(f"('{title}','{content}','{ntype}',1,'系统管理员',1,{dt(day, 10, 0)})")
out.append(
    "INSERT INTO notice (title,content,type,publisher_id,publisher_name,status,create_time) VALUES\n"
    + ",\n".join(rows) + ";"
)

# ---------------------------------------------------------------- 闭馆安排
out.append("\n-- 闭馆安排 ---------------------------------------------------")
closures = [
    ("NULL", 3, "全校实验室设备年度检修，暂停开放"),
    ("1", 10, "计算机基础实验室内部改造，暂停预约"),
    ("5", 18, "嵌入式实验室承接学科竞赛，临时闭馆"),
]
rows = [f"({lab},DATE_ADD(CURDATE(), INTERVAL {d} DAY),'{reason}',1,NOW())"
        for lab, d, reason in closures]
out.append(
    "INSERT INTO lab_closure (lab_id,closure_date,reason,create_by,create_time) VALUES\n"
    + ",\n".join(rows) + ";"
)

# ---------------------------------------------------------------- 站内信
out.append("\n-- 站内信 -----------------------------------------------------")
messages = [
    (1, "新的预约待审批", "有学生提交了计算机基础实验室的预约申请，请及时审批。", "RESERVATION", "NULL", 0, dt(0, 8, 30)),
    (1, "设备报修待派单", "工单 RP20260001 尚未指派维修员，请尽快处理。", "REPAIR", "NULL", 0, dt(-1, 9, 20)),
    (2, "新的预约待审批", "有学生提交了网络工程实验室的预约申请，请及时审批。", "RESERVATION", "NULL", 1, dt(-1, 14, 10)),
    (10, "预约审批通过", "单号 RS202600001 已通过，请按时签到。", "RESERVATION", "NULL", 0, dt(-1, 16, 5)),
    (10, "签到成功", "已完成签到，信用分 +2。", "CREDIT", "NULL", 1, dt(-2, 9, 5)),
    (30, "预约爽约提醒", "未按时签到，记爽约一次，信用分 -10。", "CREDIT", "NULL", 0, dt(-2, 12, 0)),
    (30, "预约爽约提醒", "未按时签到，记爽约一次，信用分 -10。", "CREDIT", "NULL", 0, dt(-4, 12, 0)),
    (31, "预约爽约提醒", "未按时签到，记爽约一次，信用分 -10。", "CREDIT", "NULL", 0, dt(-3, 12, 0)),
    (34, "新的维修工单", "有一台设备等待您处理，请查看工单详情。", "REPAIR", "NULL", 0, dt(-1, 10, 15)),
    (10, "系统公告", "实验室预约系统已完成中期优化，新增签到与信用分功能。", "SYSTEM", "NULL", 1, dt(-3, 10, 0)),
]
rows = [f"({uid},'{title}','{content}','{mtype}',{biz},{is_read},{ts})"
        for uid, title, content, mtype, biz, is_read, ts in messages]
out.append(
    "INSERT INTO message (receiver_id,title,content,type,biz_id,is_read,create_time) VALUES\n"
    + ",\n".join(rows) + ";"
)

# ---------------------------------------------------------------- 审计日志
out.append("\n-- 操作审计日志 -----------------------------------------------")
logs = [
    (1, "系统管理员", "用户管理", "新增用户", "UserController.save", "/api/users", 1, 32),
    (1, "系统管理员", "实验室管理", "新增实验室", "LabController.save", "/api/labs", 1, 27),
    (2, "王建国", "预约管理", "审批预约", "ReservationController.approve", "/api/reservations/12/approve", 1, 45),
    (2, "王建国", "预约管理", "审批预约", "ReservationController.approve", "/api/reservations/13/approve", 1, 38),
    (1, "系统管理员", "设备台账", "变更设备状态", "EquipmentController.changeStatus", "/api/equipments/8/status", 1, 19),
    (1, "系统管理员", "报修运维", "派单", "RepairController.assign", "/api/repairs/3/assign", 1, 25),
    (34, "张师傅", "报修运维", "维修完成", "RepairController.finish", "/api/repairs/3/finish", 1, 41),
    (10, "杨帆", "预约管理", "提交预约申请", "ReservationController.create", "/api/reservations", 1, 56),
    (10, "杨帆", "预约管理", "预约签到", "ReservationController.checkin", "/api/reservations/20/checkin", 1, 22),
    (1, "系统管理员", "闭馆管理", "新增闭馆安排", "LabClosureController.add", "/api/closures", 1, 18),
]
rows = []
for i, (uid, uname, module, action, method, uri, success, cost) in enumerate(logs):
    rows.append(
        f"({uid},'{uname}','{module}','{action}','{method}','{uri}','127.0.0.1',NULL,"
        f"{success},NULL,{cost},{dt(-i // 2 - 1, 9 + i % 8, i * 5 % 60)})"
    )
out.append(
    "INSERT INTO sys_log (operator_id,operator_name,module,action,method,uri,ip,params,success,"
    "error_msg,cost_ms,create_time) VALUES\n"
    + ",\n".join(rows) + ";"
)

out.append("\n-- 完成 -------------------------------------------------------")
print("\n".join(out))
