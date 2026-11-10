# -*- coding: utf-8 -*-
"""
生成《需求分析与数据库设计》Word 文档
用法：python scripts/build_docx.py
"""
import os
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.section import WD_SECTION
from docx.oxml.ns import qn, nsdecls
from docx.oxml import OxmlElement, parse_xml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(ROOT, "docs")
DIAG = os.path.join(DOCS, "diagrams")
SHOT = os.path.join(DOCS, "screenshots")
OUT = os.path.join(DOCS, "高校实验室预约与设备运维系统-需求分析与数据库设计.docx")

BRAND = RGBColor(0x1F, 0x2D, 0x4A)
GRAY = RGBColor(0x5B, 0x65, 0x77)
ACCENT = "3B5BDB"

doc = Document()

# ------------------------------------------------------------------ 页面
sec = doc.sections[0]
sec.page_width = Cm(21)
sec.page_height = Cm(29.7)
sec.top_margin = Cm(2.54)
sec.bottom_margin = Cm(2.54)
sec.left_margin = Cm(2.8)
sec.right_margin = Cm(2.6)

# 正文默认样式
normal = doc.styles["Normal"]
normal.font.name = "Times New Roman"
normal.font.size = Pt(12)
normal.element.rPr.rFonts.set(qn("w:eastAsia"), "宋体")
normal.paragraph_format.line_spacing = 1.5
normal.paragraph_format.space_after = Pt(6)


def set_run(run, size=12, bold=False, color=None, cn="宋体", en="Times New Roman"):
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.name = en
    run._element.rPr.rFonts.set(qn("w:eastAsia"), cn)
    if color is not None:
        run.font.color.rgb = color


def para(text="", size=12, bold=False, color=None, align=None, indent=True,
         space_before=0, space_after=6, line=1.5, cn="宋体"):
    p = doc.add_paragraph()
    if align is not None:
        p.alignment = align
    pf = p.paragraph_format
    pf.space_before = Pt(space_before)
    pf.space_after = Pt(space_after)
    pf.line_spacing = line
    if indent:
        pf.first_line_indent = Pt(size * 2)
    r = p.add_run(text)
    set_run(r, size, bold, color, cn)
    return p


def h1(text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(18)
    p.paragraph_format.space_after = Pt(10)
    p.paragraph_format.line_spacing = 1.3
    r = p.add_run(text)
    set_run(r, 16, True, BRAND, "黑体")
    # 段前分页控制：一级标题前不断页，由正文自然衔接
    return p


def h2(text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(12)
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.line_spacing = 1.4
    r = p.add_run(text)
    set_run(r, 14, True, BRAND, "黑体")
    return p


def h3(text):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(8)
    p.paragraph_format.space_after = Pt(4)
    r = p.add_run(text)
    set_run(r, 12, True, RGBColor(0x33, 0x3D, 0x52), "黑体")
    return p


def bullet(text, size=11):
    p = doc.add_paragraph(style="List Bullet")
    p.paragraph_format.line_spacing = 1.5
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run(text)
    set_run(r, size)
    return p


def caption(text, size=10.5):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(10)
    r = p.add_run(text)
    set_run(r, size, False, GRAY, "宋体")


def shade(cell, color):
    cell._tc.get_or_add_tcPr().append(
        parse_xml(r'<w:shd {} w:fill="{}"/>'.format(nsdecls("w"), color))
    )


def table(headers, rows, widths=None, font_size=9.5, header_fill="EEF2FF"):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = True

    hdr = t.rows[0].cells
    for i, h in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(1)
        r = p.add_run(h)
        set_run(r, font_size, True, BRAND, "黑体")
        shade(hdr[i], header_fill)

    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = ""
            p = cells[i].paragraphs[0]
            p.paragraph_format.space_after = Pt(1)
            p.paragraph_format.line_spacing = 1.15
            if i == 0 or (widths and i < len(widths) and widths[i] < 2.2):
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            r = p.add_run(str(val))
            set_run(r, font_size)

    if widths:
        for i, w in enumerate(widths):
            for row in t.rows:
                row.cells[i].width = Cm(w)
    doc.add_paragraph().paragraph_format.space_after = Pt(2)
    return t


def figure(path, width_cm, cap):
    if not os.path.exists(path):
        return
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(6)
    p.paragraph_format.space_after = Pt(2)
    p.add_run().add_picture(path, width=Cm(width_cm))
    caption(cap)


def page_break():
    doc.add_page_break()


# ------------------------------------------------------------------ 页脚页码
def add_page_number(section):
    footer = section.footer
    p = footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = p.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = " PAGE "
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.append(fld_begin)
    run._r.append(instr)
    run._r.append(fld_end)
    set_run(run, 10, False, GRAY)


add_page_number(sec)

# 打开时自动更新域（目录页码）
settings = doc.settings.element
settings.append(parse_xml(r'<w:updateFields {} w:val="true"/>'.format(nsdecls("w"))))

# ================================================================== 封面
for _ in range(3):
    doc.add_paragraph()

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("高校实验室预约与设备运维系统")
set_run(r, 26, True, BRAND, "黑体")

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(28)
r = p.add_run("需求分析与数据库设计说明书")
set_run(r, 18, False, RGBColor(0x3B, 0x5B, 0xDB), "黑体")

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run("—— 基于 Spring Boot 与 Vue 3 的 B/S 架构实现 ——")
set_run(r, 11.5, False, GRAY)

for _ in range(5):
    doc.add_paragraph()

info = [
    ("学    院", "计算机科学与技术学院"),
    ("专    业", "软件工程 / 计算机科学与技术"),
    ("班    级", "＿＿＿＿＿＿＿＿＿＿"),
    ("学    号", "＿＿＿＿＿＿＿＿＿＿"),
    ("姓    名", "＿＿＿＿＿＿＿＿＿＿"),
    ("指导教师", "＿＿＿＿＿＿＿＿＿＿"),
    ("完成日期", "2026 年 10 月"),
]
t = doc.add_table(rows=0, cols=2)
t.alignment = WD_TABLE_ALIGNMENT.CENTER
for k, v in info:
    cells = t.add_row().cells
    p0 = cells[0].paragraphs[0]
    p0.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    r = p0.add_run(k + "：")
    set_run(r, 12, False, BRAND)
    p1 = cells[1].paragraphs[0]
    r = p1.add_run(v)
    set_run(r, 12)
    cells[0].width = Cm(4)
    cells[1].width = Cm(8)

page_break()

# ================================================================== 摘要
h1("摘　要")
para("随着高校实验教学与科研活动的规模不断扩大，实验室与仪器设备的数量快速增长，"
     "传统的纸质登记、电话预约与线下报修方式逐渐暴露出资源利用率低、信息不透明、"
     "责任难以追溯等问题。本文围绕高校实验室资源管理场景，设计并实现了一套集"
     "实验室在线预约、审批流转、设备台账管理与报修运维于一体的 B/S 架构系统。")
para("系统采用 Spring Boot + MyBatis-Plus + MySQL 构建后端服务，使用 Vue 3 + Element Plus "
     "搭建前端界面，通过 JWT 实现无状态身份认证，并基于角色进行接口级权限控制。"
     "在预约环节，系统通过时间区间重叠判定实现冲突校验，并在审批环节进行二次校验，"
     "避免并发场景下的时段重复占用；在设备运维环节，系统建立了"
     "“报修—派单—维修—验收”的完整闭环，工单每一次状态流转均写入日志，实现全过程可追溯。"
     "系统还提供数据看板，对预约趋势、实验室热度、设备状态与故障类型进行可视化统计，"
     "为实验室管理决策提供数据支撑。")
para("测试结果表明，系统各功能模块运行稳定，预约冲突校验与权限控制有效，"
     "能够满足高校实验室日常预约与设备运维的管理需求。", space_after=10)

p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(8)
r = p.add_run("关键词：")
set_run(r, 12, True, BRAND, "黑体")
r = p.add_run("实验室预约；设备运维；Spring Boot；Vue 3；MySQL；权限控制")
set_run(r, 12)

page_break()

# ================================================================== 目录
h1("目　录")
p = doc.add_paragraph()
run = p.add_run()
fld_begin = OxmlElement("w:fldChar")
fld_begin.set(qn("w:fldCharType"), "begin")
instr = OxmlElement("w:instrText")
instr.set(qn("xml:space"), "preserve")
instr.text = r' TOC \o "1-2" \h \z \u '
fld_sep = OxmlElement("w:fldChar")
fld_sep.set(qn("w:fldCharType"), "separate")
t_ph = OxmlElement("w:t")
t_ph.text = "（在 Word 中右键此处选择“更新域”即可生成目录）"
fld_end = OxmlElement("w:fldChar")
fld_end.set(qn("w:fldCharType"), "end")
run._r.append(fld_begin)
run._r.append(instr)
run._r.append(fld_sep)
run._r.append(t_ph)
run._r.append(fld_end)
set_run(run, 10.5, False, GRAY)

page_break()

# ================================================================== 1 引言
h1("1　引言")

h2("1.1　项目背景")
para("实验室是高校开展实验教学、毕业设计与科研工作的重要场所。随着招生规模扩大与"
     "实验设备投入增加，实验室资源的管理难度显著上升：一方面，实验室与大型仪器设备数量多、"
     "分布在不同楼栋，学生难以快速了解空闲时段；另一方面，预约信息依赖人工登记，"
     "容易出现时段冲突、预约后爽约、责任不清等问题。")
para("与此同时，设备故障的发现与处理也存在明显断点。教师或学生在使用过程中发现设备异常后，"
     "通常通过电话或口头方式告知管理员，报修信息缺少统一入口，维修进度无法查询，"
     "维修结果与费用也无从统计，导致设备停机时间偏长、维护成本难以核算。")
para("因此，有必要建设一套面向全校师生的实验室预约与设备运维系统，把“资源查询—在线预约—"
     "审批确认—使用记录”与“故障报修—派单维修—验收评价”两条业务链条线上化、流程化，"
     "提升实验室资源利用率与设备保障水平。")

h2("1.2　建设目标")
bullet("统一入口：师生通过一个平台完成实验室查询、预约申请、进度跟踪与设备报修。")
bullet("流程规范：预约必须经过冲突校验与审批，报修必须形成可追溯的闭环工单。")
bullet("数据可视：以看板形式呈现实验室使用热度、设备状态分布与故障类型，支撑管理决策。")
bullet("权限清晰：按管理员、教师、学生、维修员四类角色划分功能与数据范围。")
bullet("易于部署：采用主流技术栈，前后端分离，支持在普通教学服务器上本地部署运行。")

h2("1.3　术语约定")
table(
    ["术语", "含义"],
    [
        ["实验室（Lab）", "可被预约的物理空间，具有编号、位置、容量、开放时间与审批策略"],
        ["设备（Equipment）", "隶属于某一实验室的仪器或装置，具有资产编号与状态"],
        ["预约单（Reservation）", "一次实验室使用申请，包含起止时间、人数、用途与审批状态"],
        ["报修工单（Repair Order）", "针对某台设备的故障处理单据，贯穿派单、维修与验收"],
        ["流转记录（Repair Log）", "工单状态变化时写入的操作日志，用于追溯处理过程"],
    ],
    widths=[4.2, 11.2],
)
caption("表 1-1　术语约定")

# ================================================================== 2 需求分析
h1("2　需求分析")

h2("2.1　用户角色与权限")
para("系统面向四类用户，角色在登录后由后端下发的 JWT 载荷确定，前端据此渲染菜单，"
     "后端通过拦截器与角色注解进行接口级校验，二者共同保证权限边界。")
table(
    ["角色", "典型用户", "核心权限"],
    [
        ["系统管理员 ADMIN", "设备管理处工作人员",
         "实验室与设备台账维护、预约审批、报修派单、用户管理、公告发布、查看全部统计"],
        ["教师 TEACHER", "任课教师、实验室负责人",
         "预约实验室、审批学生预约、报修设备、验收工单、查看统计"],
        ["学生 STUDENT", "本科生、研究生",
         "浏览实验室、提交预约、取消本人预约、报修设备、验收本人报修的工单"],
        ["维修员 REPAIRER", "后勤维修人员",
         "查看指派给自己的工单、开始维修、提交维修结果"],
    ],
    widths=[3.4, 4.4, 7.6],
)
caption("表 2-1　角色与权限矩阵")

h2("2.2　功能需求")

h3("2.2.1　预约管理")
para("预约管理是系统的核心业务。学生与教师可按楼栋、名称、编号检索实验室，查看实验室的"
     "位置、容量、开放时间、设备数量与当日预约情况；进入预约界面后自选日期与起止时间，"
     "系统实时展示当日已被占用的时段，提交时执行完整的业务校验。")
bullet("实验室浏览：卡片式展示，支持关键词、楼栋、状态筛选与分页。")
bullet("在线预约：自选起止时间，填写使用人数、用途与备注。")
bullet("冲突校验：同一实验室时段重叠、同一用户时段重叠、超出开放时间、超出容量、"
       "单次时长不足 30 分钟或超过 8 小时、预约过去时间、提前超过 30 天。")
bullet("审批流转：需审批的实验室生成待审批单，由教师或管理员通过或驳回并填写意见。")
bullet("预约日历：按周展示某一实验室的时段占用情况，区分已通过、待审批与已完成。")
bullet("我的预约：按状态筛选、查看审批意见、开始前自助取消。")
bullet("签到与信用分：开始前 15 分钟开放签到，签到加 2 分；爽约扣 10 分，"
       "信用分低于 60 暂停预约权限。")
bullet("预约改期：重新执行闭馆、时段冲突与时间窗口全套校验，需审批的实验室改期后回到待审批。")
bullet("闭馆安排：管理员设置全校或单实验室闭馆日期，闭馆日期的预约申请直接拦截。")
bullet("并发控制：创建、审批、改期在同一事务内先锁定实验室行，"
       "把同一实验室的写请求串行化，防止并发超卖。")

h3("2.2.2　设备运维")
para("设备运维以工单为载体，覆盖从故障发现到验收完成的完整过程。报修提交后，"
     "设备状态自动变更为“维修中”，避免在维修期间被继续预约使用；验收通过后恢复为“空闲”。")
bullet("设备台账：按实验室、类别、状态检索设备，维护资产编号、品牌型号、价值、保管人。")
bullet("故障报修：选择设备并填写故障类型、紧急程度与现象描述，系统拦截重复报修。")
bullet("派单调度：管理员将待派单工单指派给具体维修员。")
bullet("维修处理：维修员开始维修、填写处理说明与费用后提交，等待报修人验收。")
bullet("验收评价：报修人对维修结果进行 1—5 星评分并填写意见，工单闭环。")
bullet("流转留痕：每一次状态变化写入流转记录，包含操作人、动作、内容与时间。")

h3("2.2.3　数据看板")
para("看板按角色呈现不同指标：管理员与教师关注全局资源与待办，学生关注个人预约与报修，"
     "维修员关注待处理工单。图表使用 ECharts 渲染，数据由后端聚合查询一次性返回。")
bullet("指标卡：开放实验室数、设备总数、今日预约、待审批预约、待处理工单。")
bullet("趋势分析：近 14 天预约申请量折线图。")
bullet("分布统计：设备状态环形图、故障类型饼图。")
bullet("热度排行：实验室预约次数 Top 8 横向柱状图。")

h3("2.2.4　系统管理")
bullet("用户管理：新增、编辑、停用、删除用户，重置密码为初始密码。")
bullet("通知公告：管理员发布、编辑、下架公告，师生按时间浏览。")
bullet("个人中心：维护姓名、电话、邮箱，修改登录密码，查看个人信用分。")
bullet("站内信：审批结果、派单、验收、签到与信用分变动自动推送，支持未读角标与一键已读。")
bullet("操作审计日志：AOP 记录操作人、模块、URI、IP、耗时与成败，密码等敏感参数自动脱敏。")
bullet("闭馆安排与在线接口文档：管理员维护闭馆日期；Knife4j 提供接口可视化调试页面。")

h2("2.3　业务流程")
h3("2.3.1　预约与审批流程")
para("预约流程的关键在于“提交时校验 + 审批时二次校验”。由于多个用户可能同时申请同一时段，"
     "仅在提交时校验仍可能出现两人先后提交、均通过校验的情况；因此在审批通过前再次执行"
     "冲突判定，可有效避免重复占用。免审批实验室（如虚拟仿真实训室）则跳过审批环节直接生效。")
figure(os.path.join(DIAG, "fig4.png"), 13.2, "图 2-1　实验室预约与审批流程")

h3("2.3.2　报修运维流程")
para("报修流程强调状态驱动与责任到人。工单在“待派单—已派单—维修中—待验收—已完成”"
     "五个状态间流转，每次流转都受前置状态约束，例如只有“维修中”的工单才能提交维修结果，"
     "只有“待验收”的工单才能被验收，从而保证流程不被跳过。")
figure(os.path.join(DIAG, "fig5.png"), 12.6, "图 2-2　设备报修运维闭环流程")

h2("2.4　业务规则")
table(
    ["编号", "规则描述"],
    [
        ["R1", "预约开始时间不得早于当前时间，最多提前 30 天。"],
        ["R2", "结束时间必须晚于开始时间，单次时长不少于 30 分钟且不超过 8 小时。"],
        ["R3", "预约时段须落在实验室开放时间范围内，使用人数不得超过实验室容量。"],
        ["R4", "同一实验室在 PENDING、APPROVED 状态下不允许时段重叠。"],
        ["R5", "同一用户在同一时段只能存在一条有效预约。"],
        ["R6", "仅 PENDING 状态的预约可被审批，审批通过前需二次校验冲突。"],
        ["R7", "预约开始后不可取消；取消仅限本人或管理员、教师。"],
        ["R8", "同一设备不允许同时存在两条未完成（非 VERIFIED）的报修工单。"],
        ["R9", "已报废设备不可报修；报修期间设备状态置为维修中。"],
        ["R10", "验收通过后设备状态恢复为空闲，并记录评分与验收意见。"],
        ["R11", "预约结束时间已过的 APPROVED 记录，由定时任务每 10 分钟置为 FINISHED。"],
        ["R12", "预约创建、审批、改期在同一事务内对实验室行加排他锁（SELECT ... FOR UPDATE），串行化同实验室并发写入。"],
        ["R13", "签到仅在「开始前 15 分钟 ~ 结束时间」窗口内允许，且仅本人、APPROVED、未签到状态可签到。"],
        ["R14", "签到成功信用分 +2（上限 100）；爽约 -10 分；信用分低于 60 禁止提交新预约。"],
        ["R15", "结束 15 分钟后仍未签到的生效预约由定时任务标记 NO_SHOW、扣分并推送站内信。"],
        ["R16", "闭馆日期（全校或指定实验室）不可预约；改期同样需要重新校验闭馆与时段冲突。"],
        ["R17", "改密码、重置密码、停用账号时 token_version 自增，已签发的旧 JWT 立即失效。"],
    ],
    widths=[1.8, 13.6],
)
caption("表 2-2　核心业务规则")

h2("2.5　非功能需求")
table(
    ["类别", "要求"],
    [
        ["性能", "常规列表查询响应时间不超过 1 秒；看板聚合查询不超过 2 秒；支持 50 人左右并发访问。"],
        ["安全", "密码使用 BCrypt 加盐存储；接口基于 JWT 鉴权；按角色限制接口访问；"
                 "SQL 通过 MyBatis-Plus 参数绑定，避免注入。"],
        ["可用性", "关键操作提供二次确认与明确提示；表单具备前端校验；异常统一返回可读信息。"],
        ["可维护性", "分层架构，Controller—Service—Mapper 职责清晰；统一响应结构与全局异常处理。"],
        ["兼容性", "支持 Chrome、Edge 等主流浏览器，分辨率 1366×768 及以上。"],
    ],
    widths=[2.4, 13.0],
)
caption("表 2-3　非功能需求")

h2("2.6　功能结构")
figure(os.path.join(DIAG, "fig1.png"), 13.0, "图 2-3　系统功能结构图")

# ================================================================== 3 系统设计
page_break()
h1("3　系统设计")

h2("3.1　总体架构")
para("系统采用前后端分离的 B/S 架构。前端为单页应用，通过 Axios 调用后端 RESTful 接口，"
     "开发阶段由 Vite 代理转发请求；后端按表现层、接口层、业务层、数据层组织代码，"
     "数据持久化交由 MyBatis-Plus 完成。")
figure(os.path.join(DIAG, "fig2.png"), 14.6, "图 3-1　系统总体架构图")

h2("3.2　技术选型")
table(
    ["层次", "技术", "选型理由"],
    [
        ["前端框架", "Vue 3.5 + Vite 5", "组合式 API 便于逻辑复用，Vite 启动与热更新速度快"],
        ["UI 组件", "Element Plus 2.8", "组件完备、中文文档友好，适合快速构建管理系统"],
        ["图表", "ECharts 5", "图表类型丰富，支持响应式与大数据量渲染"],
        ["状态与路由", "Pinia + Vue Router 4", "轻量且与 Vue 3 深度集成，支持路由守卫"],
        ["后端框架", "Spring Boot 3.3.5", "生态成熟，自动装配降低配置成本，便于分层开发"],
        ["持久层", "MyBatis-Plus 3.5.7", "内置通用 CRUD 与分页插件，减少重复 SQL"],
        ["数据库", "MySQL 8.0", "开源稳定，支持事务与索引优化，教学环境普及率高"],
        ["认证", "JWT + BCrypt", "无状态认证便于前后端分离，BCrypt 抗彩虹表攻击；token_version 支持主动失效"],
        ["接口文档", "Springdoc OpenAPI 3 + Knife4j 4.5", "自动生成 44 个接口的可视化文档，支持在线调试与 Bearer 鉴权"],
        ["日志切面", "Spring AOP", "无侵入采集写操作审计日志，并对敏感参数做脱敏"],
    ],
    widths=[2.8, 4.6, 8.0],
)
caption("表 3-1　技术选型")

h2("3.3　模块划分")
table(
    ["模块", "主要类", "职责"],
    [
        ["认证模块", "AuthController / AuthService / JwtUtil",
         "登录校验、令牌签发与解析、个人信息与密码维护"],
        ["预约模块", "ReservationController / ReservationService",
         "预约创建、冲突校验、审批、取消、日历时间轴、过期自动完成"],
        ["设备模块", "EquipmentController / EquipmentService", "设备台账维护与状态变更"],
        ["报修模块", "RepairController / RepairService", "工单创建、派单、维修、验收与流转日志"],
        ["实验室模块", "LabController / LabService", "实验室信息维护、设备清单、当日占用统计"],
        ["统计模块", "StatsController / StatsService / StatsMapper", "看板指标聚合与图表数据"],
        ["系统模块", "UserController / NoticeController", "用户管理、公告发布"],
        ["消息模块", "MessageController / MessageService", "站内信推送、未读数、已读标记"],
        ["审计模块", "LogController / LogService / OperationLogAspect", "操作日志采集与管理员查询"],
        ["闭馆模块", "LabClosureController / LabClosureService", "闭馆日期维护与预约前校验"],
        ["公共规则", "TimeWindow / ReservationState / CreditPolicy", "时段重叠、状态机与信用分策略集中复用"],
    ],
    widths=[2.6, 6.4, 6.4],
)
caption("表 3-2　模块划分")

h2("3.4　权限设计")
para("系统采用“前端菜单控制 + 后端接口校验”的双层权限模型。用户登录成功后，"
     "后端返回包含用户 ID、账号、姓名与角色的 JWT；前端将角色存入 Pinia 并据此过滤菜单与按钮；"
     "后端拦截器解析令牌后写入 ThreadLocal 上下文，并通过自定义注解 @RequireRole 校验角色，"
     "业务层再针对“是否本人数据”做二次判断，例如学生只能取消自己的预约。")
table(
    ["接口", "方法", "允许角色", "说明"],
    [
        ["/api/reservations/{id}/approve", "PUT", "ADMIN、TEACHER", "审批预约"],
        ["/api/reservations/{id}/cancel", "PUT", "登录用户", "本人或管理员/教师可取消"],
        ["/api/repairs/{id}/assign", "PUT", "ADMIN", "派单"],
        ["/api/repairs/{id}/start", "PUT", "REPAIRER、ADMIN", "开始维修"],
        ["/api/repairs/{id}/verify", "PUT", "登录用户", "报修人、教师或管理员"],
        ["/api/users/**", "*", "ADMIN", "用户管理"],
        ["/api/labs、/api/equipments 写操作", "POST/PUT/DELETE", "ADMIN", "台账维护"],
        ["/api/closures 写操作", "POST/DELETE", "ADMIN", "闭馆安排维护"],
        ["/api/logs", "GET", "ADMIN", "操作审计日志"],
    ],
    widths=[6.0, 3.0, 3.4, 3.4],
)
caption("表 3-3　关键接口权限")

h2("3.5　关键设计要点")

h3("3.5.1　时段冲突判定")
para("预约冲突的本质是时间区间的重叠判定。设已有预约区间为 [s1, e1)，新申请区间为 [s2, e2)，"
     "当且仅当 s1 < e2 且 e1 > s2 时两区间重叠。该规则被抽取为公共方法 TimeWindow.overlaps，"
     "在提交、审批与改期三处统一复用。MySQL 默认 REPEATABLE READ 下，单纯的「先查后写」"
     "在并发场景仍可能两人同时通过校验；因此系统在同一个事务内先用 "
     "SELECT ... FOR UPDATE 锁定实验室行，把同一实验室的写请求串行化，再执行冲突判定与写入。"
     "20 路并发压测争抢同一时段，实测仅 1 条预约成功，其余 19 条均因冲突被拒绝。")

h3("3.5.2　状态机与前置校验")
para("预约单与报修工单均以状态字段驱动流转，所有状态变更操作在执行前先校验当前状态，"
     "不满足前置条件直接抛出业务异常，从而保证流程不可跳跃。例如对已完成的工单再次派单、"
     "对未开始的预约执行验收，都会被明确拒绝并返回可读提示。")
figure(os.path.join(DIAG, "fig6.png"), 13.0, "图 3-2　预约单状态流转")

h3("3.5.3　定时任务")
para("系统通过 Spring 的 @Scheduled 注解，每 10 分钟执行一次扫描：先把已过结束时间且状态为 "
     "APPROVED 的预约批量置为 FINISHED，再把结束超过 15 分钟仍未签到的生效预约标记为 NO_SHOW、"
     "扣减信用分并推送站内信。这样既保证「我的预约」与看板数据的准确性，也避免了在查询接口中"
     "做实时状态推导带来的复杂度。")

# ================================================================== 4 数据库设计
page_break()
h1("4　数据库设计")

h2("4.1　设计原则")
bullet("以业务单据为中心建模，预约单、报修工单各自独立成表，通过外键字段关联。")
bullet("统一使用 BIGINT 自增主键，字符集统一为 utf8mb4，时间字段统一为 DATETIME。")
bullet("状态类字段使用短字符串编码（如 PENDING、APPROVED），可读性优于数字编码。")
bullet("公共字段 create_time、update_time 由框架自动填充，避免遗漏。")
bullet("针对高频查询条件建立联合索引，例如预约表的 (lab_id, start_time, end_time)。")

h2("4.2　实体关系")
para("系统共设计 10 张业务表。用户与实验室是两类基础实体，预约单同时关联二者；"
     "设备隶属于实验室，报修工单关联设备、报修人与维修员；工单的每一次流转写入流转记录表。"
     "在此基础上，message 承载站内信推送、sys_log 承载操作审计、lab_closure 承载闭馆安排，"
     "共同构成通知、审计与预约约束三类支撑能力。")
figure(os.path.join(DIAG, "fig3.png"), 15.4, "图 4-1　数据库 ER 图")

h2("4.3　数据表清单")
table(
    ["序号", "表名", "中文名称", "说明"],
    [
        ["1", "sys_user", "用户表", "存放四类角色的账号、密码与基本信息"],
        ["2", "lab", "实验室表", "实验室基本信息、开放时间与审批策略"],
        ["3", "equipment", "设备表", "设备台账，含资产编号、价值与状态"],
        ["4", "reservation", "预约表", "预约单据与审批结果"],
        ["5", "repair_order", "报修工单表", "故障报修与维修过程记录"],
        ["6", "repair_log", "工单流转记录表", "工单状态变化的操作日志"],
        ["7", "notice", "通知公告表", "面向全校的通知与维护公告"],
        ["8", "sys_log", "操作审计日志表", "写操作的操作人、参数、耗时与成败，密码脱敏"],
        ["9", "message", "站内信表", "面向单个用户的业务通知与已读状态"],
        ["10", "lab_closure", "闭馆安排表", "全校或单实验室闭馆日期与原因"],
    ],
    widths=[1.4, 4.2, 4.4, 5.4],
)
caption("表 4-1　数据表清单")

h2("4.4　表结构详细说明")

h3("4.4.1　用户表 sys_user")
table(
    ["字段", "类型", "约束", "说明"],
    [
        ["id", "BIGINT", "PK, AUTO_INCREMENT", "主键"],
        ["username", "VARCHAR(50)", "NOT NULL, UNIQUE", "登录账号"],
        ["password", "VARCHAR(100)", "NOT NULL", "BCrypt 加密后的密码"],
        ["real_name", "VARCHAR(50)", "NOT NULL", "真实姓名"],
        ["role", "VARCHAR(20)", "NOT NULL, INDEX", "ADMIN/TEACHER/STUDENT/REPAIRER"],
        ["user_no", "VARCHAR(30)", "", "学号或工号"],
        ["dept", "VARCHAR(100)", "", "学院或部门"],
        ["phone", "VARCHAR(20)", "", "联系电话"],
        ["email", "VARCHAR(100)", "", "邮箱"],
        ["avatar", "VARCHAR(255)", "", "头像地址"],
        ["status", "TINYINT", "NOT NULL DEFAULT 1", "1 正常，0 停用"],
        ["token_version", "INT", "NOT NULL DEFAULT 0", "令牌版本，改密/停用时自增使旧 token 失效"],
        ["credit_score", "INT", "NOT NULL DEFAULT 100", "信用分 0—100，低于 60 禁止预约"],
        ["violation_count", "INT", "NOT NULL DEFAULT 0", "累计爽约次数"],
        ["create_time", "DATETIME", "", "创建时间"],
        ["update_time", "DATETIME", "", "更新时间"],
    ],
    widths=[3.6, 4.0, 4.4, 4.2],
)
caption("表 4-2　sys_user 表结构")

h3("4.4.2　实验室表 lab")
table(
    ["字段", "类型", "约束", "说明"],
    [
        ["id", "BIGINT", "PK", "主键"],
        ["code", "VARCHAR(30)", "NOT NULL, UNIQUE", "实验室编号，如 LAB-A301"],
        ["name", "VARCHAR(100)", "NOT NULL", "实验室名称"],
        ["building", "VARCHAR(50)", "INDEX", "所在楼栋"],
        ["room", "VARCHAR(30)", "", "房间号"],
        ["capacity", "INT", "", "可容纳人数"],
        ["open_time", "TIME", "", "开放开始时间"],
        ["close_time", "TIME", "", "开放结束时间"],
        ["need_approval", "TINYINT", "DEFAULT 1", "是否需要审批，1 是 0 否"],
        ["manager_id", "BIGINT", "", "负责人（关联 sys_user.id）"],
        ["status", "TINYINT", "DEFAULT 1", "1 开放，0 停用"],
        ["description", "VARCHAR(500)", "", "实验室简介"],
        ["create_time", "DATETIME", "", "创建时间"],
        ["update_time", "DATETIME", "", "更新时间"],
    ],
    widths=[3.6, 4.0, 4.4, 4.2],
)
caption("表 4-3　lab 表结构")

h3("4.4.3　设备表 equipment")
table(
    ["字段", "类型", "约束", "说明"],
    [
        ["id", "BIGINT", "PK", "主键"],
        ["code", "VARCHAR(40)", "NOT NULL, UNIQUE", "资产编号"],
        ["name", "VARCHAR(100)", "NOT NULL", "设备名称"],
        ["category", "VARCHAR(50)", "", "设备类别"],
        ["lab_id", "BIGINT", "INDEX", "所属实验室"],
        ["model", "VARCHAR(80)", "", "规格型号"],
        ["brand", "VARCHAR(50)", "", "品牌"],
        ["purchase_date", "DATE", "", "购置日期"],
        ["price", "DECIMAL(12,2)", "", "设备价值"],
        ["status", "VARCHAR(20)", "NOT NULL, INDEX", "IDLE/IN_USE/REPAIR/SCRAPPED"],
        ["keeper", "VARCHAR(50)", "", "保管人"],
        ["description", "VARCHAR(500)", "", "备注"],
        ["create_time", "DATETIME", "", "创建时间"],
        ["update_time", "DATETIME", "", "更新时间"],
    ],
    widths=[3.6, 4.0, 4.4, 4.2],
)
caption("表 4-4　equipment 表结构")

h3("4.4.4　预约表 reservation")
table(
    ["字段", "类型", "约束", "说明"],
    [
        ["id", "BIGINT", "PK", "主键"],
        ["order_no", "VARCHAR(40)", "NOT NULL, UNIQUE", "预约单号"],
        ["user_id", "BIGINT", "NOT NULL, INDEX", "预约人"],
        ["lab_id", "BIGINT", "NOT NULL", "实验室"],
        ["start_time", "DATETIME", "NOT NULL", "开始时间"],
        ["end_time", "DATETIME", "NOT NULL", "结束时间"],
        ["headcount", "INT", "", "使用人数"],
        ["purpose", "VARCHAR(200)", "", "使用用途"],
        ["remark", "VARCHAR(500)", "", "备注"],
        ["status", "VARCHAR(20)", "NOT NULL, INDEX",
         "PENDING/APPROVED/REJECTED/CANCELLED/FINISHED"],
        ["approver_id", "BIGINT", "", "审批人"],
        ["approve_time", "DATETIME", "", "审批时间"],
        ["approve_remark", "VARCHAR(500)", "", "审批意见"],
        ["checkin_time", "DATETIME", "", "实际签到时间"],
        ["checkin_status", "VARCHAR(20)", "NOT NULL DEFAULT NONE", "NONE/CHECKED_IN/NO_SHOW"],
        ["create_time", "DATETIME", "", "提交时间"],
        ["update_time", "DATETIME", "", "更新时间"],
    ],
    widths=[3.6, 4.0, 4.4, 4.2],
)
caption("表 4-5　reservation 表结构")

h3("4.4.5　报修工单表 repair_order")
table(
    ["字段", "类型", "约束", "说明"],
    [
        ["id", "BIGINT", "PK", "主键"],
        ["order_no", "VARCHAR(40)", "NOT NULL, UNIQUE", "工单号"],
        ["equipment_id", "BIGINT", "NOT NULL, INDEX", "故障设备"],
        ["lab_id", "BIGINT", "", "所属实验室（冗余字段，便于按实验室统计）"],
        ["reporter_id", "BIGINT", "NOT NULL, INDEX", "报修人"],
        ["fault_type", "VARCHAR(50)", "", "故障类型"],
        ["urgency", "VARCHAR(20)", "", "紧急程度 LOW/NORMAL/HIGH"],
        ["fault_desc", "VARCHAR(500)", "", "故障描述"],
        ["status", "VARCHAR(20)", "NOT NULL, INDEX",
         "PENDING/ASSIGNED/REPAIRING/DONE/VERIFIED"],
        ["repairer_id", "BIGINT", "INDEX", "维修员"],
        ["assign_time", "DATETIME", "", "派单时间"],
        ["finish_time", "DATETIME", "", "维修完成时间"],
        ["repair_desc", "VARCHAR(500)", "", "维修说明"],
        ["cost", "DECIMAL(10,2)", "", "维修费用"],
        ["score", "INT", "", "验收评分 1—5"],
        ["verify_remark", "VARCHAR(500)", "", "验收意见"],
        ["verify_time", "DATETIME", "", "验收时间"],
        ["create_time", "DATETIME", "", "报修时间"],
        ["update_time", "DATETIME", "", "更新时间"],
    ],
    widths=[3.6, 4.0, 4.4, 4.2],
)
caption("表 4-6　repair_order 表结构")

h3("4.4.6　工单流转记录表 repair_log")
table(
    ["字段", "类型", "约束", "说明"],
    [
        ["id", "BIGINT", "PK", "主键"],
        ["order_id", "BIGINT", "NOT NULL, INDEX", "所属工单"],
        ["operator_id", "BIGINT", "", "操作人"],
        ["operator_name", "VARCHAR(50)", "", "操作人姓名（冗余，便于直接展示）"],
        ["action", "VARCHAR(20)", "", "CREATE/ASSIGN/START/FINISH/VERIFY"],
        ["content", "VARCHAR(500)", "", "操作内容"],
        ["create_time", "DATETIME", "", "操作时间"],
    ],
    widths=[3.6, 4.0, 4.4, 4.2],
)
caption("表 4-7　repair_log 表结构")

h3("4.4.7　通知公告表 notice")
table(
    ["字段", "类型", "约束", "说明"],
    [
        ["id", "BIGINT", "PK", "主键"],
        ["title", "VARCHAR(150)", "NOT NULL", "标题"],
        ["content", "TEXT", "", "正文"],
        ["type", "VARCHAR(20)", "", "NOTICE/MAINTENANCE/SYSTEM"],
        ["publisher_id", "BIGINT", "", "发布人"],
        ["publisher_name", "VARCHAR(50)", "", "发布人姓名"],
        ["status", "TINYINT", "DEFAULT 1", "1 发布，0 下架"],
        ["create_time", "DATETIME", "", "发布时间"],
    ],
    widths=[3.6, 4.0, 4.4, 4.2],
)
caption("表 4-8　notice 表结构")

h3("4.4.8　操作审计日志表 sys_log")
table(
    ["字段", "类型", "约束", "说明"],
    [
        ["id", "BIGINT", "PK", "主键"],
        ["operator_id", "BIGINT", "INDEX", "操作人 ID（未登录场景为空）"],
        ["operator_name", "VARCHAR(50)", "", "操作人姓名"],
        ["module", "VARCHAR(50)", "INDEX", "业务模块，如 预约管理 / 认证管理"],
        ["action", "VARCHAR(100)", "", "动作描述"],
        ["method", "VARCHAR(200)", "", "切点方法全名"],
        ["uri", "VARCHAR(200)", "", "请求路径"],
        ["ip", "VARCHAR(50)", "", "客户端 IP"],
        ["params", "VARCHAR(1000)", "", "参数摘要，password/token 等字段脱敏为 ***"],
        ["success", "TINYINT", "", "1 成功，0 失败"],
        ["error_msg", "VARCHAR(500)", "", "异常信息"],
        ["cost_ms", "BIGINT", "", "耗时（毫秒）"],
        ["create_time", "DATETIME", "INDEX", "记录时间"],
    ],
    widths=[3.6, 4.0, 4.4, 4.2],
)
caption("表 4-9　sys_log 表结构")

h3("4.4.9　站内信表 message")
table(
    ["字段", "类型", "约束", "说明"],
    [
        ["id", "BIGINT", "PK", "主键"],
        ["receiver_id", "BIGINT", "NOT NULL, INDEX", "接收人"],
        ["title", "VARCHAR(150)", "NOT NULL", "标题"],
        ["content", "VARCHAR(500)", "", "正文"],
        ["type", "VARCHAR(20)", "", "RESERVATION/REPAIR/CREDIT/SYSTEM"],
        ["biz_id", "BIGINT", "", "关联业务单据 ID"],
        ["is_read", "TINYINT", "NOT NULL DEFAULT 0", "0 未读，1 已读"],
        ["create_time", "DATETIME", "INDEX", "推送时间"],
    ],
    widths=[3.6, 4.0, 4.4, 4.2],
)
caption("表 4-10　message 表结构")

h3("4.4.10　闭馆安排表 lab_closure")
table(
    ["字段", "类型", "约束", "说明"],
    [
        ["id", "BIGINT", "PK", "主键"],
        ["lab_id", "BIGINT", "INDEX，可为空", "NULL 表示全校闭馆，否则为指定实验室"],
        ["closure_date", "DATE", "NOT NULL, INDEX", "闭馆日期"],
        ["reason", "VARCHAR(200)", "", "闭馆原因，预约被拦截时回显给用户"],
        ["create_by", "BIGINT", "", "创建人"],
        ["create_time", "DATETIME", "", "创建时间"],
    ],
    widths=[3.6, 4.0, 4.4, 4.2],
)
caption("表 4-11　lab_closure 表结构")

h2("4.5　索引设计")
table(
    ["表", "索引", "字段", "用途"],
    [
        ["reservation", "idx_lab_time", "lab_id, start_time, end_time", "冲突校验与日历查询"],
        ["reservation", "idx_user", "user_id", "我的预约列表"],
        ["reservation", "idx_status", "status", "待审批列表与统计"],
        ["equipment", "idx_lab / idx_status", "lab_id / status", "按实验室与状态筛选"],
        ["repair_order", "idx_equipment / idx_status", "equipment_id / status", "工单查询与重复报修拦截"],
        ["repair_log", "idx_order", "order_id", "按工单加载流转记录"],
        ["reservation", "idx_checkin", "checkin_status, end_time", "定时任务扫描爽约记录"],
        ["message", "idx_receiver", "receiver_id, is_read", "未读数与我的消息列表"],
        ["lab_closure", "idx_date", "closure_date", "预约前闭馆校验"],
        ["sys_log", "idx_module", "module, create_time", "审计日志按模块检索"],
    ],
    widths=[3.2, 4.6, 5.0, 4.0],
)
caption("表 4-12　主要索引")

h2("4.6　状态字典")
table(
    ["业务对象", "状态码", "中文含义", "可流转至"],
    [
        ["预约单", "PENDING", "待审批", "APPROVED / REJECTED / CANCELLED"],
        ["预约单", "APPROVED", "已通过", "CANCELLED / FINISHED"],
        ["预约单", "REJECTED", "已驳回", "—"],
        ["预约单", "CANCELLED", "已取消", "—"],
        ["预约单", "FINISHED", "已完成", "—"],
        ["报修工单", "PENDING", "待派单", "ASSIGNED"],
        ["报修工单", "ASSIGNED", "已派单", "REPAIRING"],
        ["报修工单", "REPAIRING", "维修中", "DONE"],
        ["报修工单", "DONE", "待验收", "VERIFIED / REPAIRING"],
        ["报修工单", "VERIFIED", "已完成", "—"],
        ["设备", "IDLE", "空闲", "IN_USE / REPAIR / SCRAPPED"],
        ["设备", "IN_USE", "使用中", "IDLE / REPAIR"],
        ["设备", "REPAIR", "维修中", "IDLE"],
        ["设备", "SCRAPPED", "已报废", "—"],
    ],
    widths=[3.0, 3.2, 3.6, 6.4],
)
caption("表 4-13　状态字典")

# ================================================================== 5 接口设计
page_break()
h1("5　接口设计")

h2("5.1　通用约定")
para("所有接口以 /api 为前缀，采用 JSON 传输数据。除登录接口外，均需在请求头携带"
     "Authorization: Bearer <token>。后端统一返回如下结构，前端拦截器依据 code 判断成败：")
table(
    ["字段", "类型", "说明"],
    [
        ["code", "int", "200 成功；400 参数错误；401 未登录或令牌失效；403 无权限；500 业务或系统异常"],
        ["msg", "string", "提示信息，可直接展示给用户"],
        ["data", "object", "业务数据，分页时为 { total, records }"],
    ],
    widths=[2.4, 2.6, 10.6],
)
caption("表 5-1　统一响应结构")

h2("5.2　接口清单")
table(
    ["模块", "方法", "路径", "功能"],
    [
        ["认证", "POST", "/api/auth/login", "账号密码登录，返回 token 与用户信息"],
        ["认证", "GET", "/api/auth/profile", "获取当前登录用户"],
        ["认证", "PUT", "/api/auth/password", "修改密码"],
        ["实验室", "GET", "/api/labs", "分页查询实验室"],
        ["实验室", "GET", "/api/labs/{id}/equipments", "实验室设备清单"],
        ["实验室", "POST/PUT/DELETE", "/api/labs", "实验室维护（管理员）"],
        ["设备", "GET", "/api/equipments", "分页查询设备"],
        ["设备", "PUT", "/api/equipments/{id}/status", "变更设备状态"],
        ["预约", "POST", "/api/reservations", "提交预约申请"],
        ["预约", "GET", "/api/reservations/my", "我的预约"],
        ["预约", "GET", "/api/reservations", "预约列表（教师/管理员）"],
        ["预约", "GET", "/api/reservations/timeline", "某日已占用时段"],
        ["预约", "PUT", "/api/reservations/{id}/approve", "审批预约"],
        ["预约", "PUT", "/api/reservations/{id}/cancel", "取消预约"],
        ["报修", "POST", "/api/repairs", "提交报修"],
        ["报修", "GET", "/api/repairs", "工单列表（支持 scope 过滤）"],
        ["报修", "PUT", "/api/repairs/{id}/assign", "派单"],
        ["报修", "PUT", "/api/repairs/{id}/start", "开始维修"],
        ["报修", "PUT", "/api/repairs/{id}/finish", "提交维修结果"],
        ["报修", "PUT", "/api/repairs/{id}/verify", "验收"],
        ["报修", "GET", "/api/repairs/{id}/logs", "工单流转记录"],
        ["统计", "GET", "/api/stats/dashboard", "看板聚合数据"],
        ["用户", "GET", "/api/users", "用户分页（管理员）"],
        ["公告", "GET", "/api/notices", "公告分页"],
        ["预约", "PUT", "/api/reservations/{id}/checkin", "预约签到"],
        ["预约", "PUT", "/api/reservations/{id}/reschedule", "预约改期"],
        ["站内信", "GET", "/api/messages、/unread-count", "消息列表与未读数"],
        ["站内信", "PUT", "/api/messages/{id}/read、/read-all", "标记已读"],
        ["闭馆", "GET/POST/DELETE", "/api/closures", "闭馆安排查询与维护（管理员）"],
        ["审计", "GET", "/api/logs", "操作日志分页（管理员）"],
    ],
    widths=[2.4, 3.4, 6.6, 5.0],
    font_size=9,
)
caption("表 5-2　接口清单")

h2("5.3　关键接口示例")
para("提交预约的请求体与成功响应如下（时间为 yyyy-MM-dd HH:mm:ss 格式）：", indent=True)
code_style = doc.styles["Normal"]
p = doc.add_paragraph()
p.paragraph_format.left_indent = Cm(0.6)
p.paragraph_format.space_after = Pt(4)
p.paragraph_format.line_spacing = 1.2
r = p.add_run(
    'POST /api/reservations\n'
    '{\n'
    '  "labId": 1,\n'
    '  "startTime": "2026-10-28 19:00:00",\n'
    '  "endTime": "2026-10-28 21:00:00",\n'
    '  "headcount": 8,\n'
    '  "purpose": "课程实验：数据结构与算法"\n'
    '}'
)
set_run(r, 10, False, RGBColor(0x33, 0x3D, 0x52), "Consolas", "Consolas")

p = doc.add_paragraph()
p.paragraph_format.left_indent = Cm(0.6)
p.paragraph_format.space_after = Pt(10)
p.paragraph_format.line_spacing = 1.2
r = p.add_run(
    '{\n'
    '  "code": 200,\n'
    '  "msg": "提交成功，等待审批",\n'
    '  "data": { "id": 385, "orderNo": "RS20261021153012345", "status": "PENDING" }\n'
    '}'
)
set_run(r, 10, False, RGBColor(0x33, 0x3D, 0x52), "Consolas", "Consolas")

para("若时段冲突，接口返回 code 为 500、msg 为“该时段已被预约，请选择其他时间”，"
     "前端直接以消息提示呈现，用户可立即调整时间后重新提交。")

# ================================================================== 6 实现与测试
h1("6　系统实现与测试")

h2("6.1　运行环境")
table(
    ["项目", "版本要求", "说明"],
    [
        ["操作系统", "Windows 10/11、Linux", "开发与测试均在 Windows 11 完成"],
        ["JDK", "21", "Spring Boot 3.x 要求 JDK 17 及以上"],
        ["Maven", "3.9", "依赖管理与打包"],
        ["Node.js", "18 及以上", "前端构建，实测 22.x"],
        ["MySQL", "8.0", "字符集 utf8mb4"],
        ["浏览器", "Chrome / Edge 最新版", "前端运行环境"],
    ],
    widths=[3.2, 4.6, 9.0],
)
caption("表 6-1　运行环境")

h2("6.2　部署与启动")
para("数据库脚本位于 lab-backend/src/main/resources/sql 目录，schema.sql 负责建库建表，"
     "data.sql 提供演示数据。演示数据中的时间基于当前日期动态生成，导入后即形成"
     "“近一个月历史记录 + 未来一周待处理预约”的数据形态，便于演示与测试。")
para("本地启动时先导入数据库，再运行后端 jar 包，最后启动前端开发服务器。前端通过 Vite 代理"
     "将 /api 请求转发至 8080 端口，避免跨域配置。此外项目提供 Docker Compose 一键部署："
     "docker compose up -d --build 会同时拉起 MySQL 8.0、后端与 Nginx 前端，"
     "首次启动时按 01-schema.sql、02-data.sql 的顺序自动完成建表与演示数据导入。"
     "演示账号统一使用初始密码 123456，按角色区分：管理员 admin、教师 t_wang、"
     "学生 s2021001、维修员 r_zhang。")

h2("6.3　功能测试")
para("系统采用三层验证：JUnit 5 + Mockito 单元与服务层用例 54 个，"
     "Python 接口级回归断言 81 项，以及 20 路并发压测脚本。接口回归覆盖鉴权、看板、"
     "实验室与设备查询、预约冲突与审批、报修闭环、权限隔离，以及在线接口文档、站内信、"
     "审计日志脱敏、闭馆拦截、签到加信用分、改期与 Token 失效，全部通过。部分用例如下：")
table(
    ["编号", "测试场景", "预期结果", "结果"],
    [
        ["T01", "使用 admin/123456 登录", "返回 token 与用户信息，角色为 ADMIN", "通过"],
        ["T02", "未携带 token 访问 /api/labs", "返回 401，提示请先登录", "通过"],
        ["T03", "学生提交合法预约", "生成 PENDING 预约单，返回提交成功", "通过"],
        ["T04", "同一实验室同一时段再次预约", "返回“该时段已被预约”", "通过"],
        ["T05", "结束时间早于开始时间", "返回“结束时间必须晚于开始时间”", "通过"],
        ["T06", "教师审批通过待审批预约", "状态变为 APPROVED，记录审批人", "通过"],
        ["T07", "学生调用审批接口", "返回 403 无操作权限", "通过"],
        ["T08", "学生取消自己的预约", "状态变为 CANCELLED", "通过"],
        ["T09", "学生提交设备报修", "生成 PENDING 工单，设备状态变为 REPAIR", "通过"],
        ["T10", "管理员派单给维修员", "工单变为 ASSIGNED，维修员可查询到任务", "通过"],
        ["T11", "维修员开始维修并提交结果", "工单依次变为 REPAIRING、DONE", "通过"],
        ["T12", "报修人验收并评分", "工单变为 VERIFIED，设备恢复 IDLE", "通过"],
        ["T13", "查询工单流转记录", "返回 5 条记录（提交、派单、开始、完成、验收）", "通过"],
        ["T14", "学生访问 /api/users", "返回 403，无法访问用户管理", "通过"],
        ["T15", "新增全校闭馆日期后提交该日预约", "返回含「闭馆」的业务异常", "通过"],
        ["T16", "窗口内对本人的 APPROVED 预约签到", "状态变 CHECKED_IN，信用分 +2 并推送站内信", "通过"],
        ["T17", "重复签到 / 结束早于开始的改期", "均被明确拒绝", "通过"],
        ["T18", "查询审计日志中的登录记录", "密码字段显示为 ***，不落明文", "通过"],
        ["T19", "停用账号后用旧 token 访问", "返回 401，令牌立即失效", "通过"],
        ["T20", "20 个请求并发抢同一实验室同一时段", "恰好 1 条成功、19 条因冲突被拒绝", "通过"],
    ],
    widths=[1.4, 6.4, 6.6, 1.6],
    font_size=9,
)
caption("表 6-2　部分功能测试用例")

h2("6.4　界面实现")
para("系统前端共 14 个页面，整体采用左侧深色导航、右侧浅色内容区的布局，"
     "通过统一的色彩与圆角规范保证视觉一致性。以下为主要界面截图。")
figure(os.path.join(SHOT, "01-login.png"), 14.4, "图 6-1　登录界面")
figure(os.path.join(SHOT, "02-dashboard.png"), 14.4, "图 6-2　数据看板")
figure(os.path.join(SHOT, "03b-labs-student.png"), 14.4, "图 6-3　实验室预约列表")
figure(os.path.join(SHOT, "12-reserve-dialog.png"), 14.4, "图 6-4　预约申请弹窗")
figure(os.path.join(SHOT, "04-calendar.png"), 14.4, "图 6-5　预约日历（周视图）")
figure(os.path.join(SHOT, "06-approval.png"), 14.4, "图 6-6　审批中心")
figure(os.path.join(SHOT, "13-repair-detail.png"), 14.4, "图 6-7　报修工单详情与流转记录")

# ================================================================== 7 总结
h1("7　总结与展望")

h2("7.1　工作总结")
para("本文围绕高校实验室资源管理的实际痛点，完成了从需求分析、系统设计到编码实现的完整过程。"
     "系统以预约与报修两条业务主线为核心，通过冲突校验与审批二次校验保证时段不被重复占用，"
     "通过状态机与流转日志保证报修过程可追溯，并以数据看板呈现资源使用与设备健康状况。"
     "在此基础上补充了行锁并发控制、签到与信用分、预约改期、闭馆安排、站内信、"
     "操作审计与 Token 主动失效等工程化能力，并通过 Knife4j 在线文档、"
     "54 个单元用例、81 项接口断言、并发压测与 Docker Compose 一键部署形成完整验证闭环。"
     "在工程实现上，采用前后端分离与分层架构，接口统一响应、异常统一处理，"
     "代码结构清晰，具备较好的可维护性与可扩展性。")

h2("7.2　不足与改进方向")
bullet("并发控制目前基于单机 MySQL 行锁，若后续多实例部署需替换为分布式锁或数据库唯一约束兜底。")
bullet("通知已覆盖站内信，但尚未接入短信、邮件或企业微信等外部通道，可实现审批与派单的即时提醒。")
bullet("统计分析维度较粗，后续可增加按学院、按设备类别的使用率与维修成本分析。")
bullet("尚未接入统一身份认证，后续可与学校 CAS 或 OAuth2 平台对接，实现单点登录。")
bullet("可增加移动端适配或小程序端，方便学生随时查看预约与报修进度。")

# ================================================================== 附录
page_break()
h1("附录 A　演示数据说明")
para("系统内置一套完整的演示数据，导入后可直接用于功能演示与答辩，主要包含：")
table(
    ["数据对象", "数量", "说明"],
    [
        ["用户", "36 个", "1 名管理员、8 名教师、24 名学生、3 名维修员"],
        ["实验室", "12 间", "分布于 4 个楼栋，含需审批与免审批两类"],
        ["设备", "237 台", "覆盖计算、网络、分析仪器、机械加工等类别"],
        ["预约单", "约 380 条", "近 30 天历史记录与未来 7 天待处理预约"],
        ["报修工单", "26 张", "覆盖五个状态，含完整流转记录与验收评分"],
        ["通知公告", "6 条", "含安全培训、设备维护与规则调整"],
        ["站内信", "10 条", "覆盖审批、派单、签到与信用分变动"],
        ["操作审计日志", "10 条", "覆盖登录、预约、报修等写操作"],
        ["闭馆安排", "3 条", "含全校闭馆与单实验室闭馆"],
        ["信用分演示账号", "2 个", "70 分 / 3 次爽约、90 分 / 1 次爽约，用于展示限制规则"],
    ],
    widths=[3.6, 3.2, 10.0],
)
caption("表 A-1　演示数据规模")
para("所有演示账号的初始密码均为 123456。演示数据脚本位于 scripts/gen_demo_data.py，"
     "可根据需要调整随机种子与数据规模后重新生成。")

doc.save(OUT)
print("saved:", OUT)
print("paragraphs:", len(doc.paragraphs), "tables:", len(doc.tables))
