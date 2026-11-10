-- =============================================================
-- 高校实验室预约与设备运维系统 数据库结构
-- MySQL 8.0 / utf8mb4
-- =============================================================

CREATE DATABASE IF NOT EXISTS lab_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE lab_system;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS sys_log;
DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS lab_closure;
DROP TABLE IF EXISTS repair_log;
DROP TABLE IF EXISTS repair_order;
DROP TABLE IF EXISTS reservation;
DROP TABLE IF EXISTS equipment;
DROP TABLE IF EXISTS notice;
DROP TABLE IF EXISTS lab;
DROP TABLE IF EXISTS sys_user;

-- -------------------------------------------------------------
-- 用户表
-- -------------------------------------------------------------
CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt）',
    real_name   VARCHAR(50)  NOT NULL COMMENT '真实姓名',
    role        VARCHAR(20)  NOT NULL COMMENT '角色：ADMIN/TEACHER/STUDENT/REPAIRER',
    user_no     VARCHAR(30)           COMMENT '学号/工号',
    dept        VARCHAR(100)          COMMENT '学院/部门',
    phone       VARCHAR(20)           COMMENT '联系电话',
    email       VARCHAR(100)          COMMENT '邮箱',
    avatar      VARCHAR(255)          COMMENT '头像',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0停用',
    token_version   INT          NOT NULL DEFAULT 0 COMMENT '令牌版本，改密码/停用时自增使旧 token 失效',
    credit_score    INT          NOT NULL DEFAULT 100 COMMENT '信用分 0-100，低于 60 禁止预约',
    violation_count INT          NOT NULL DEFAULT 0 COMMENT '累计爽约次数',
    create_time DATETIME              COMMENT '创建时间',
    update_time DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role (role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户';

-- -------------------------------------------------------------
-- 实验室表
-- -------------------------------------------------------------
CREATE TABLE lab (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code          VARCHAR(30)  NOT NULL COMMENT '实验室编号',
    name          VARCHAR(100) NOT NULL COMMENT '实验室名称',
    building      VARCHAR(50)           COMMENT '所在楼栋',
    room          VARCHAR(30)           COMMENT '房间号',
    capacity      INT                   COMMENT '可容纳人数',
    open_time     TIME                  COMMENT '开放开始时间',
    close_time    TIME                  COMMENT '开放结束时间',
    need_approval TINYINT      NOT NULL DEFAULT 1 COMMENT '是否需要审批 1是 0否',
    manager_id    BIGINT                COMMENT '负责人',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1开放 0停用',
    description   VARCHAR(500)          COMMENT '实验室简介',
    create_time   DATETIME              COMMENT '创建时间',
    update_time   DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_code (code),
    KEY idx_building (building)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '实验室';

-- -------------------------------------------------------------
-- 设备表
-- -------------------------------------------------------------
CREATE TABLE equipment (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    code          VARCHAR(40)   NOT NULL COMMENT '资产编号',
    name          VARCHAR(100)  NOT NULL COMMENT '设备名称',
    category      VARCHAR(50)            COMMENT '设备类别',
    lab_id        BIGINT                 COMMENT '所属实验室',
    model         VARCHAR(80)            COMMENT '规格型号',
    brand         VARCHAR(50)            COMMENT '品牌',
    purchase_date DATE                   COMMENT '购置日期',
    price         DECIMAL(12, 2)         COMMENT '设备价值',
    status        VARCHAR(20)   NOT NULL DEFAULT 'IDLE' COMMENT 'IDLE空闲/IN_USE使用中/REPAIR维修中/SCRAPPED已报废',
    keeper        VARCHAR(50)            COMMENT '保管人',
    description   VARCHAR(500)           COMMENT '备注',
    create_time   DATETIME               COMMENT '创建时间',
    update_time   DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_equipment_code (code),
    KEY idx_lab (lab_id),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '设备台账';

-- -------------------------------------------------------------
-- 预约表
-- -------------------------------------------------------------
CREATE TABLE reservation (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no       VARCHAR(40) NOT NULL COMMENT '预约单号',
    user_id        BIGINT      NOT NULL COMMENT '预约人',
    lab_id         BIGINT      NOT NULL COMMENT '实验室',
    start_time     DATETIME    NOT NULL COMMENT '开始时间',
    end_time       DATETIME    NOT NULL COMMENT '结束时间',
    headcount      INT                  COMMENT '使用人数',
    purpose        VARCHAR(200)         COMMENT '使用用途',
    remark         VARCHAR(500)         COMMENT '备注',
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待审批/APPROVED已通过/REJECTED已驳回/CANCELLED已取消/FINISHED已完成',
    approver_id    BIGINT               COMMENT '审批人',
    approve_time   DATETIME             COMMENT '审批时间',
    approve_remark VARCHAR(500)         COMMENT '审批意见',
    checkin_time   DATETIME             COMMENT '签到时间',
    checkin_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE未签到/CHECKED_IN已签到/NO_SHOW爽约',
    create_time    DATETIME             COMMENT '创建时间',
    update_time    DATETIME             COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_lab_time (lab_id, start_time, end_time),
    KEY idx_user (user_id),
    KEY idx_status (status),
    KEY idx_checkin (checkin_status, end_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '实验室预约';

-- -------------------------------------------------------------
-- 报修工单表
-- -------------------------------------------------------------
CREATE TABLE repair_order (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no      VARCHAR(40)   NOT NULL COMMENT '工单号',
    equipment_id  BIGINT        NOT NULL COMMENT '设备',
    lab_id        BIGINT                 COMMENT '所属实验室',
    reporter_id   BIGINT        NOT NULL COMMENT '报修人',
    fault_type    VARCHAR(50)            COMMENT '故障类型',
    urgency       VARCHAR(20)            COMMENT '紧急程度 LOW/NORMAL/HIGH',
    fault_desc    VARCHAR(500)           COMMENT '故障描述',
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待派单/ASSIGNED已派单/REPAIRING维修中/DONE待验收/VERIFIED已完成',
    repairer_id   BIGINT                 COMMENT '维修员',
    assign_time   DATETIME               COMMENT '派单时间',
    finish_time   DATETIME               COMMENT '维修完成时间',
    repair_desc   VARCHAR(500)           COMMENT '维修说明',
    cost          DECIMAL(10, 2)         COMMENT '维修费用',
    score         INT                    COMMENT '验收评分',
    verify_remark VARCHAR(500)           COMMENT '验收意见',
    verify_time   DATETIME               COMMENT '验收时间',
    create_time   DATETIME               COMMENT '创建时间',
    update_time   DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_repair_no (order_no),
    KEY idx_equipment (equipment_id),
    KEY idx_status (status),
    KEY idx_reporter (reporter_id),
    KEY idx_repairer (repairer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报修工单';

-- -------------------------------------------------------------
-- 工单流转记录表
-- -------------------------------------------------------------
CREATE TABLE repair_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id      BIGINT       NOT NULL COMMENT '工单',
    operator_id   BIGINT                COMMENT '操作人',
    operator_name VARCHAR(50)           COMMENT '操作人姓名',
    action        VARCHAR(20)           COMMENT 'CREATE/ASSIGN/START/FINISH/VERIFY',
    content       VARCHAR(500)          COMMENT '操作内容',
    create_time   DATETIME              COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工单流转记录';

-- -------------------------------------------------------------
-- 通知公告表
-- -------------------------------------------------------------
CREATE TABLE notice (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    title          VARCHAR(150) NOT NULL COMMENT '标题',
    content        TEXT                  COMMENT '内容',
    type           VARCHAR(20)           COMMENT 'NOTICE/MAINTENANCE/SYSTEM',
    publisher_id   BIGINT                COMMENT '发布人',
    publisher_name VARCHAR(50)           COMMENT '发布人姓名',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1发布 0下架',
    create_time    DATETIME              COMMENT '发布时间',
    PRIMARY KEY (id),
    KEY idx_create (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通知公告';

-- -------------------------------------------------------------
-- 操作审计日志表
-- -------------------------------------------------------------
CREATE TABLE sys_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    operator_id  BIGINT                COMMENT '操作人 ID',
    operator_name VARCHAR(50)          COMMENT '操作人姓名',
    module       VARCHAR(40)           COMMENT '业务模块',
    action       VARCHAR(100)          COMMENT '操作描述',
    method       VARCHAR(200)          COMMENT '调用方法',
    uri          VARCHAR(200)          COMMENT '请求路径',
    ip           VARCHAR(50)           COMMENT '来源 IP',
    params       VARCHAR(1000)         COMMENT '参数摘要（脱敏）',
    success      TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    error_msg    VARCHAR(500)          COMMENT '失败原因',
    cost_ms      BIGINT                COMMENT '耗时毫秒',
    create_time  DATETIME              COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_operator (operator_id),
    KEY idx_create (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作审计日志';

-- -------------------------------------------------------------
-- 站内信表
-- -------------------------------------------------------------
CREATE TABLE message (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    receiver_id BIGINT       NOT NULL COMMENT '接收人',
    title       VARCHAR(150) NOT NULL COMMENT '标题',
    content     VARCHAR(500)          COMMENT '正文',
    type        VARCHAR(20)           COMMENT 'RESERVATION/REPAIR/CREDIT/SYSTEM',
    biz_id      BIGINT                COMMENT '关联业务单 ID',
    is_read     TINYINT      NOT NULL DEFAULT 0 COMMENT '1已读 0未读',
    create_time DATETIME              COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_receiver_read (receiver_id, is_read),
    KEY idx_create (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内信';

-- -------------------------------------------------------------
-- 实验室闭馆日期表（lab_id 为 NULL 表示全校闭馆）
-- -------------------------------------------------------------
CREATE TABLE lab_closure (
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    lab_id        BIGINT               COMMENT '实验室 ID，NULL 表示全校',
    closure_date  DATE        NOT NULL COMMENT '闭馆日期',
    reason        VARCHAR(200)         COMMENT '闭馆原因',
    create_by     BIGINT               COMMENT '创建人',
    create_time   DATETIME             COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lab_date (lab_id, closure_date),
    KEY idx_date (closure_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '实验室闭馆安排';

SET FOREIGN_KEY_CHECKS = 1;
