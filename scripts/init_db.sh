#!/usr/bin/env bash
# 一键初始化数据库：建库建表 + 导入演示数据
# 用法：MYSQL_PASSWORD=你的密码 bash scripts/init_db.sh
set -e

DIR="$(cd "$(dirname "$0")/.." && pwd)"
SQL_DIR="$DIR/lab-backend/src/main/resources/sql"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
USER="${MYSQL_USERNAME:-root}"
PASS="${MYSQL_PASSWORD:-}"

if [ -z "$PASS" ]; then
  echo "请先设置 MYSQL_PASSWORD 环境变量，例如：MYSQL_PASSWORD=123456 bash scripts/init_db.sh"
  exit 1
fi

echo "[1/3] 创建数据库与表结构..."
"$MYSQL_BIN" -u"$USER" -p"$PASS" --default-character-set=utf8mb4 < "$SQL_DIR/schema.sql"

echo "[2/3] 导入演示数据..."
"$MYSQL_BIN" -u"$USER" -p"$PASS" --default-character-set=utf8mb4 < "$SQL_DIR/data.sql"

echo "[3/3] 校验数据..."
"$MYSQL_BIN" -u"$USER" -p"$PASS" --default-character-set=utf8mb4 -e "
USE lab_system;
SELECT '用户' AS 表名, COUNT(*) AS 数量 FROM sys_user
UNION ALL SELECT '实验室', COUNT(*) FROM lab
UNION ALL SELECT '设备', COUNT(*) FROM equipment
UNION ALL SELECT '预约', COUNT(*) FROM reservation
UNION ALL SELECT '报修工单', COUNT(*) FROM repair_order
UNION ALL SELECT '公告', COUNT(*) FROM notice;"

echo "数据库初始化完成。"
