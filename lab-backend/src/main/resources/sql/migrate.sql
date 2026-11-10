-- =============================================================
-- 增量迁移脚本（幂等，可重复执行）
-- 用途：在已有演示数据的库上升级到「中期优化版」结构，不清空数据
-- =============================================================
USE lab_system;

DROP PROCEDURE IF EXISTS lab_add_col;
DELIMITER $$
CREATE PROCEDURE lab_add_col(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'lab_system' AND TABLE_NAME = tbl AND COLUMN_NAME = col
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
        PREPARE st FROM @s;
        EXECUTE st;
        DEALLOCATE PREPARE st;
    END IF;
END$$
DELIMITER ;

CALL lab_add_col('sys_user', 'token_version',
    "token_version INT NOT NULL DEFAULT 0 COMMENT '令牌版本，改密码/停用时自增使旧 token 失效' AFTER status");
CALL lab_add_col('sys_user', 'credit_score',
    "credit_score INT NOT NULL DEFAULT 100 COMMENT '信用分 0-100，低于 60 禁止预约' AFTER token_version");
CALL lab_add_col('sys_user', 'violation_count',
    "violation_count INT NOT NULL DEFAULT 0 COMMENT '累计爽约次数' AFTER credit_score");

CALL lab_add_col('reservation', 'checkin_time',
    "checkin_time DATETIME COMMENT '签到时间' AFTER approve_remark");
CALL lab_add_col('reservation', 'checkin_status',
    "checkin_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT 'NONE未签到/CHECKED_IN已签到/NO_SHOW爽约' AFTER checkin_time");

DROP PROCEDURE IF EXISTS lab_add_col;

-- 预约表补索引（幂等）
SET @idx_sql = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = 'lab_system' AND TABLE_NAME = 'reservation' AND INDEX_NAME = 'idx_checkin') = 0,
        'ALTER TABLE reservation ADD KEY idx_checkin (checkin_status, end_time)',
        'SELECT 1'
    )
);
PREPARE idx_st FROM @idx_sql;
EXECUTE idx_st;
DEALLOCATE PREPARE idx_st;

-- -------------------------------------------------------------
-- 新表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    operator_id   BIGINT                COMMENT '操作人 ID',
    operator_name VARCHAR(50)           COMMENT '操作人姓名',
    module        VARCHAR(40)           COMMENT '业务模块',
    action        VARCHAR(100)          COMMENT '操作描述',
    method        VARCHAR(200)          COMMENT '调用方法',
    uri           VARCHAR(200)          COMMENT '请求路径',
    ip            VARCHAR(50)           COMMENT '来源 IP',
    params        VARCHAR(1000)         COMMENT '参数摘要（脱敏）',
    success       TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    error_msg     VARCHAR(500)          COMMENT '失败原因',
    cost_ms       BIGINT                COMMENT '耗时毫秒',
    create_time   DATETIME              COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_operator (operator_id),
    KEY idx_create (create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作审计日志';

CREATE TABLE IF NOT EXISTS message (
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

CREATE TABLE IF NOT EXISTS lab_closure (
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

-- -------------------------------------------------------------
-- 历史数据回填（重要）
-- 旧库的预约没有签到字段，checkin_status 全部是 NONE。若不回填，
-- 定时爽约扫描会把几百条已结束的历史预约误判为爽约，扣光所有学生信用分。
-- 仅回填「已结束且已履约」的记录，未来预约保持 NONE 等待真实签到。
-- -------------------------------------------------------------
UPDATE reservation
SET checkin_status = 'CHECKED_IN',
    checkin_time   = DATE_ADD(start_time, INTERVAL 5 MINUTE)
WHERE checkin_status = 'NONE'
  AND status IN ('FINISHED', 'APPROVED')
  AND end_time < DATE_SUB(NOW(), INTERVAL 15 MINUTE);

-- 演示用的闭馆 / 站内信 / 爽约 / 审计日志统一由 data.sql 生成。
-- 全新安装请执行 scripts/init_db.sh（schema.sql + data.sql）。
