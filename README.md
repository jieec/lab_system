# 高校实验室预约与设备运维系统

面向高校实验室资源管理的 B/S 系统，覆盖 **实验室在线预约审批** 与 **设备报修运维闭环** 两条主线，
同时提供数据看板、站内信、闭馆安排、信用分、操作审计与用户权限管理等支撑功能。

## 一、技术栈

| 层次 | 技术选型 |
| --- | --- |
| 前端 | Vue 3.5 + Vite 5 + Element Plus 2.8（按需引入）+ ECharts 5（core 按需）+ Pinia + Vue Router |
| 后端 | Spring Boot 3.3.5 + MyBatis-Plus 3.5.7 + JWT（jjwt 0.12） + BCrypt + Spring AOP |
| 接口文档 | Springdoc OpenAPI 3 + Knife4j 4.5（`/doc.html`） |
| 数据库 | MySQL 8.0（utf8mb4） |
| 测试 | JUnit 5 + Mockito（54 用例）、Python 接口回归（81 断言）、并发压测 |
| 部署 | Docker Compose（MySQL + 后端 + Nginx 前端，一键起） |
| 运行环境 | JDK 21、Node.js 18+、Maven 3.9 |

## 二、功能清单

### 1. 预约管理
- 实验室卡片浏览，按名称/编号/楼栋筛选
- 自选起止时间预约，实时展示当日已被占用时段
- 冲突校验：实验室时段冲突、个人时段冲突、开放时间、容量、时长上下限、30 天预约窗口
- **并发安全**：创建/审批/改期用 `SELECT ... FOR UPDATE` 锁实验室行串行化，20 路并发同一时段只会有 1 条成功
- 审批流：待审批 → 已通过 / 已驳回，审批时二次校验防止并发占用；状态流转统一由 `ReservationState` 约束
- 预约日历（周视图）、我的预约详情
- **签到与信用分**：开始前 15 分钟开放签到，签到 +2 分（上限 100），爽约 -10 分；低于 60 分暂停预约；定时任务每 10 分钟扫描爽约并自动结束过期预约
- **预约改期**：重跑闭馆/冲突/时间窗口全套校验，需审批的实验室改期后回到待审批
- **闭馆安排**：支持全校或单实验室闭馆日期，闭馆日预约直接拦截
- 定时任务：自动置「已完成」+ 自动标记「爽约」并扣分

### 2. 设备运维
- 设备台账：资产编号、类别、品牌型号、价值、保管人、状态
- 报修提交 → 管理员派单 → 维修员开始维修 → 提交结果 → 报修人验收评分
- 工单流转日志全程留痕，报修期间设备状态自动置为「维修中」，验收后恢复「空闲」
- 重复报修拦截、报废设备不可报修

### 3. 消息与审计
- **站内信**：审批结果、派单、验收、签到、信用分变动自动推送；顶栏铃铛显示未读角标，支持单条/全部已读
- **操作审计日志**：AOP 环绕 `@OperationLog` 记录操作人、模块、URI、IP、耗时、成败，密码类参数自动脱敏；仅管理员可查
- **Token 失效**：`sys_user.token_version` + JWT `ver` 声明；改密、重置密码、停用账号后旧 token 立即失效

### 4. 数据看板
- 概览指标：开放实验室、设备总数、今日预约、待审批、待处理工单
- 近 14 天预约趋势、设备状态分布、实验室预约热度、故障类型分布
- 最新公告与角色化快捷入口

### 5. 系统管理
- 用户管理：新增/编辑/停用/删除、重置密码（默认 `123456`）
- 角色：管理员 ADMIN、教师 TEACHER、学生 STUDENT、维修员 REPAIRER
- 通知公告发布与浏览、个人中心（资料、改密、信用分概览）

## 三、目录结构

```
高校实验室预约与设备运维系统/
├── lab-backend/                    # 后端工程
│   ├── src/main/java/com/lab/system/
│   │   ├── common/                 # 统一响应、异常、分页 + TimeWindow/ReservationState/CreditPolicy
│   │   ├── config/                 # Web、MyBatis-Plus、Jackson、OpenAPI 配置
│   │   ├── controller/             # 11 个 REST 控制器
│   │   ├── aop/                    # OperationLogAspect 审计切面
│   │   ├── dto/ entity/ mapper/    # 请求对象、10 张表实体、Mapper
│   │   ├── security/               # JWT、拦截器、角色注解、@OperationLog
│   │   ├── service/                # 业务逻辑
│   │   └── task/                   # 定时任务（过期 + 爽约扫描）
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── sql/schema.sql|data.sql|migrate.sql  # 建表 / 演示数据 / 幂等迁移
│   ├── src/test/java/             # 54 个单元与 Mockito 用例
│   └── Dockerfile
├── lab-frontend/                   # 前端工程
│   ├── src/
│   │   ├── api/ components/ layouts/ router/ stores/ styles/ utils/
│   │   ├── plugins/               # Element Plus 组件与样式按需注册
│   │   └── views/                 # 14 个页面
│   ├── Dockerfile  nginx.conf
│   └── scripts/screenshots.mjs    # Playwright 截图脚本
├── docs/                           # 需求与数据库设计文档、图表、截图、后续计划
├── 答辩PPT/                        # 14 页答辩 PPT + 中期检查讲稿
├── scripts/                        # 初始化、演示数据生成、回归与并发压测
├── docker-compose.yml  .env.example
└── 启动系统.bat  后端-Maven启动.bat
```

## 四、快速启动

### 0. 一键启动（推荐，Windows 本地演示）

双击项目根目录的 **`启动系统.bat`**，会自动完成：启动后端 8080 → 等待 15 秒 → 启动前端 5173 → 打开浏览器。
关闭弹出的两个黑色命令行窗口即可停止服务。

> 脚本里已内置本机 MySQL 密码占位符，请改为你的真实密码：编辑 `启动系统.bat` / `后端-Maven启动.bat` 中的 `set "MYSQL_PASSWORD=..."` 这一行。

### 0-B. Docker Compose 一键部署（换机器 / 答辩现场最省事）

```bash
cp .env.example .env          # 可选，按需改端口与密码
docker compose up -d --build
```

- 前端：<http://localhost:8081>
- 后端接口文档：<http://localhost:8080/doc.html>
- MySQL 映射到宿主机 `3307`，避免与本机已装的 3306 冲突

首次启动会自动执行 `01-schema.sql`（建表）与 `02-data.sql`（演示数据）。
初始化脚本只在数据卷为空时执行，需要重置演示数据时用 `docker compose down -v` 再起。

> 项目目录含中文，Compose 无法从目录名推导项目名，因此 `docker-compose.yml` 里已显式写死 `name: lab-system`。

### 1. 本地初始化数据库

```bash
# 方式一：脚本（推荐，会清空并重建 lab_system）
MYSQL_PASSWORD=你的密码 bash scripts/init_db.sh

# 方式二：已有数据、只想升级表结构（不删数据）
mysql --default-character-set=utf8mb4 -uroot -p < lab-backend/src/main/resources/sql/migrate.sql
```

演示数据的时间基于当前日期生成，任何一天导入都是「近一个月 + 未来一周」的活数据。

### 2. 启动后端

**方式 A：`java -jar`（推荐，演示用最稳）**

```bash
cd lab-backend
mvn -DskipTests package
MYSQL_PASSWORD=你的密码 java -jar target/lab-backend.jar --server.port=8080
```

> 若目标 jar 正被运行中的后端占用，Windows 下 `mvn package` 会报
> `Unable to rename ...jar to ...jar.original`，先停掉后端进程再打包即可。

**方式 B：`mvn spring-boot:run`（开发时用，需先做盘符映射）**

```bash
subst X: "D:\桌面\课程项目\高校实验室预约与设备运维系统"
cd /d X:\lab-backend
set MYSQL_PASSWORD=你的密码
mvn spring-boot:run
```

或者直接双击根目录的 **`后端-Maven启动.bat`**。

> **为什么 `mvn spring-boot:run` 在中文路径下会失败？**
> `spring-boot-maven-plugin:3.3.5` 会把 classpath 写进临时 `.argfile`，再用 `-cp @文件`
> 传给 fork 出来的 JVM。该文件按 UTF-8 写入，而 JVM 启动器按系统默认编码
> （中文 Windows 为 GBK）读取，中文路径被解码成乱码，于是报
> `ClassNotFoundException: com.lab.system.LabApplication`。
> 该版本插件没有 `useArgumentFile` / `fork` 开关，只能换成不含中文的路径
> （`subst` 映射盘符有效，`mklink /J` 无效，因为 Maven 会解析回真实路径）。
> `mvn package` 与 Docker 构建（容器内路径为 ASCII）都不受此影响。

**方式 C：IDEA 直接运行**：运行 `LabApplication`，在运行配置的
Environment variables 里加 `MYSQL_PASSWORD=...`。

> **注意**：`application.yml` 中密码默认 `123456`，仓库内已用占位符；本机运行时请通过 `MYSQL_PASSWORD=...` 传入真实密码，
> 否则报 `Access denied for user 'root'@'localhost'`。

### 3. 启动前端

```bash
cd lab-frontend
npm install
npm run dev          # http://localhost:5173
```

Vite 已配置 `/api` 代理到 `http://localhost:8080`。

### 4. 演示账号（密码均为 `123456`）

| 角色 | 账号 | 说明 |
| --- | --- | --- |
| 系统管理员 | `admin` | 全部功能，含用户管理、闭馆、审计日志、派单 |
| 教师 | `t_wang` | 审批预约 |
| 学生 | `s2021001` | 预约、签到、改期、报修、验收 |
| 维修员 | `r_zhang` | 处理维修工单 |

演示数据里另有两个低信用分账号（`s2021030` 70 分 / 3 次爽约、`s2021031` 90 分 / 1 次爽约），
用于展示「信用分不足禁止预约」与爽约扣分效果。

### 5. 在线接口文档

启动后端后访问 <http://localhost:8080/doc.html>（Knife4j），原始 OpenAPI JSON 在
`/v3/api-docs`。目前共 **44 个接口 / 11 个业务分组**，右上角「Authorize」填入登录后
返回的 token 即可在线调试。

## 五、数据库设计

10 张表：`sys_user`、`lab`、`equipment`、`reservation`、`repair_order`、`repair_log`、
`notice`、`sys_log`、`message`、`lab_closure`。
详细字段说明与 ER 图见 `docs/高校实验室预约与设备运维系统-需求分析与数据库设计.docx`。

核心关系：

```
sys_user 1 ──< reservation >── 1 lab
sys_user 1 ──< repair_order >── 1 equipment >── 1 lab
repair_order 1 ──< repair_log
lab 1 ──< equipment
lab 1 ──< lab_closure
sys_user 1 ──< message / sys_log
```

## 六、接口概览

| 模块 | 接口前缀 | 说明 |
| --- | --- | --- |
| 认证 | `/api/auth` | 登录、个人信息、改密 |
| 用户 | `/api/users` | 用户 CRUD、重置密码、启停用 |
| 实验室 | `/api/labs` | 分页、详情、设备清单、CRUD |
| 设备 | `/api/equipments` | 分页、类别、CRUD、状态变更 |
| 预约 | `/api/reservations` | 创建、我的、审批、取消、时间轴、`/{id}/checkin`、`/{id}/reschedule` |
| 报修 | `/api/repairs` | 创建、派单、开始、完成、验收、日志 |
| 公告 | `/api/notices` | 分页、最新、CRUD |
| 站内信 | `/api/messages` | 未读数、分页、`/{id}/read`、`/read-all` |
| 闭馆 | `/api/closures` | 查询、新增、删除（新增/删除仅 ADMIN） |
| 审计 | `/api/logs` | 分页查询（仅 ADMIN） |
| 统计 | `/api/stats/dashboard` | 看板聚合数据 |

统一响应格式：`{ "code": 200, "msg": "操作成功", "data": ... }`，
鉴权通过请求头 `Authorization: Bearer <token>`。
时间字段统一使用 `yyyy-MM-dd HH:mm:ss`（全局 `JacksonConfig`），**不接受 ISO 的 `T` 分隔格式**。

## 七、验证脚本

```bash
cd lab-backend && mvn test                    # 54 个单元 / Mockito 用例
python scripts/smoke_test.py                  # 81 项接口回归断言（需后端已启动，可重复执行）
python scripts/concurrency_test.py            # 20 并发抢同一时段，校验只有 1 条成功
```

- 接口回归覆盖：登录鉴权、看板、实验室、预约冲突与审批、报修全闭环、权限隔离，
  以及 Knife4j 文档、站内信、审计日志脱敏、闭馆拦截、签到加信用分、改期、Token 失效。
- 并发压测用 `threading.Barrier` 让 20 个请求同时发起，验证行锁确实防止了超卖。

## 八、前端构建优化

- Element Plus 由 `app.use(ElementPlus)` 全量注册改为按需注册 40 个组件
  （`src/plugins/element-plus.js`），样式同样按需引入（`element-plus-css.js`）。
- ECharts 改用 `echarts/core` + 按需 `use(...)`，只打包柱/折线/饼图与 Canvas 渲染器。
- 实测结果：JS 总量约 **1.3 MB**（原约 2.8 MB），CSS 从 **363 KB → 251 KB**
  （gzip 48.9 → 32.0 KB），首屏主包约 715 KB，看板 ECharts 单独懒加载约 528 KB。
- 新增页面若用了新组件，需同步在 `element-plus.js` 注册组件、在 `element-plus-css.js` 补样式。

## 九、后续计划

- 12 周迭代路线：`docs/后续开发计划.md`
- AI Agent 接入方案（下一阶段）：`docs/AI-Agent接入方案.md`
- 中期检查 5 分钟讲稿：`答辩PPT/中期检查讲稿.md`
