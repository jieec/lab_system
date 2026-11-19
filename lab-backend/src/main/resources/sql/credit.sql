
CREATE TABLE IF NOT EXISTS credit_record (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 event_key VARCHAR(80) NOT NULL,
 event_type VARCHAR(20) NOT NULL,
 change_amount INT NOT NULL,
 score_before INT NOT NULL,
 score_after INT NOT NULL,
 reason VARCHAR(300) NOT NULL,
 operator_id BIGINT NULL,
 create_time DATETIME NOT NULL,
 UNIQUE KEY uk_credit_event(event_key),
 KEY idx_credit_user(user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用分变动明细';

CREATE TABLE IF NOT EXISTS credit_appeal (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 reason VARCHAR(500) NOT NULL,
 status VARCHAR(20) NOT NULL,
 review_reason VARCHAR(200),
 reviewer_id BIGINT,
 create_time DATETIME NOT NULL,
 review_time DATETIME,
 KEY idx_appeal_user(user_id,status),
 KEY idx_appeal_pending(status,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用恢复申请';
