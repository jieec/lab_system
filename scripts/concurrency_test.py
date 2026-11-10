#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
预约并发压测：验证「SELECT ... FOR UPDATE 行锁」确实消除了超卖。

做法：20 个不同学生账号，在同一瞬间抢占同一间免审批实验室的同一时段。
期望：恰好 1 条成功，其余 19 条返回「该时段已被预约」。

用法：
    python scripts/concurrency_test.py
    LAB_BASE_URL=http://localhost:8080 MYSQL_PASSWORD=xxx python scripts/concurrency_test.py --cleanup

注意：脚本会真实写入一条预约，--cleanup 会把它从库里删掉，保持演示数据干净。
"""
import argparse
import json
import os
import subprocess
import sys
import threading
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta

BASE = os.environ.get('LAB_BASE_URL', 'http://localhost:8080').rstrip('/')
PASSWORD = os.environ.get('LAB_PASSWORD', '123456')
MYSQL_PASSWORD = os.environ.get('MYSQL_PASSWORD', '123456')
LAB_ID = int(os.environ.get('LAB_ID', '11'))  # LAB-D105 虚拟仿真实训室（免审批）
CONCURRENCY = int(os.environ.get('LAB_CONCURRENCY', '20'))
# 演示数据中的学生账号（见 sql/data.sql）。可用 LAB_STUDENTS 逗号分隔覆盖。
DEFAULT_STUDENTS = (
    [f's2021{i:03d}' for i in range(1, 7)]
    + [f's2022{i:03d}' for i in range(1, 7)]
    + [f's2023{i:03d}' for i in range(1, 13)]
)
STUDENTS = [s.strip() for s in os.environ.get('LAB_STUDENTS', ','.join(DEFAULT_STUDENTS)).split(',') if s.strip()]


def post(path, data, token=None):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(BASE + path,
                                 data=json.dumps(data, ensure_ascii=False).encode('utf-8'),
                                 headers=headers, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode('utf-8'))
        except Exception:
            return e.code, {}
    except Exception as e:
        return -1, {'msg': str(e)}


def login(username):
    status, body = post('/api/auth/login', {'username': username, 'password': PASSWORD})
    if status != 200 or body.get('code') != 200:
        raise RuntimeError(f'{username} 登录失败: {status} {body}')
    return body['data']['token']


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--cleanup', action='store_true', help='压测结束后删除本次产生的预约')
    args = ap.parse_args()

    slot = (datetime.now() + timedelta(days=20)).replace(hour=14, minute=0, second=0, microsecond=0)
    end = slot + timedelta(hours=2)
    payload = {
        'labId': LAB_ID,
        # 后端 JacksonConfig 统一使用 yyyy-MM-dd HH:mm:ss，不能用 ISO 的 T 分隔
        'startTime': slot.strftime('%Y-%m-%d %H:%M:%S'),
        'endTime': end.strftime('%Y-%m-%d %H:%M:%S'),
        'headcount': 2,
        'purpose': '并发压测（自动清理）'
    }

    print(f'目标：{BASE}  实验室 lab_id={LAB_ID}  时段 {slot:%Y-%m-%d %H:%M} ~ {end:%H:%M}')
    if len(STUDENTS) < CONCURRENCY:
        raise RuntimeError(f'可用学生账号只有 {len(STUDENTS)} 个，少于并发数 {CONCURRENCY}')
    tokens = [login(u) for u in STUDENTS[:CONCURRENCY]]
    print(f'已登录 {len(tokens)} 个学生账号')

    barrier = threading.Barrier(CONCURRENCY)

    def fire(token):
        barrier.wait()
        return post('/api/reservations', payload, token)

    with ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
        results = list(pool.map(fire, tokens))

    ok = [(s, b) for s, b in results if b.get('code') == 200]
    rejected = [(s, b) for s, b in results if b.get('code') != 200]
    conflict = [b for s, b in rejected if '已被预约' in str(b.get('msg', ''))]

    print('\n===== 压测结果 =====')
    print(f'总请求     : {len(results)}')
    print(f'成功       : {len(ok)}')
    print(f'被拒绝     : {len(rejected)}')
    print(f'其中因冲突 : {len(conflict)}')
    for s, b in rejected[:3]:
        print(f'  示例失败 : HTTP {s} / {b.get("msg")}')

    if args.cleanup:
        sql = ("DELETE FROM reservation WHERE lab_id = %d "
               "AND start_time = '%s' AND purpose = '并发压测（自动清理）';"
               % (LAB_ID, slot.strftime('%Y-%m-%d %H:%M:%S')))
        try:
            subprocess.run(['mysql', '--default-character-set=utf8mb4', '-uroot',
                            '-p' + MYSQL_PASSWORD, '-e', 'USE lab_system; ' + sql],
                           check=True, capture_output=True)
            print('已清理本次压测产生的预约记录')
        except Exception as e:
            print(f'自动清理失败，请手工执行以下 SQL：\n  {sql}\n  ({e})')

    if len(ok) == 1 and len(rejected) == CONCURRENCY - 1:
        print('\n[PASS] 行锁生效：并发下只有 1 条预约成功，没有超卖')
        return 0
    print(f'\n[FAIL] 期望成功 1 条，实际 {len(ok)} 条')
    return 1


if __name__ == '__main__':
    sys.exit(main())
