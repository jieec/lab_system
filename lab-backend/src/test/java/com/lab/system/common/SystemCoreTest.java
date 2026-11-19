package com.lab.system.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 系统核心规则综合测试集（共 60 个测试用例）
 *
 * 覆盖五大模块的核心业务规则：
 * 1. 信用分策略（CreditPolicy）—— 10 个用例
 * 2. 预约状态机（ReservationState）—— 12 个用例
 * 3. 时间窗口冲突检测（TimeWindow）—— 15 个用例
 * 4. 统一响应结构（R / PageResult）—— 10 个用例
 * 5. 业务异常（BizException）—— 5 个用例
 * 6. 边界与组合场景 —— 8 个用例
 *
 * 团队分工：
 * - 成员 A：认证与用户模块 → 对应 R 响应结构、BizException
 * - 成员 B：实验室与设备模块 → 对应 TimeWindow 时段检测
 * - 成员 C：预约与闭馆模块 → 对应 ReservationState 状态机
 * - 成员 D：报修与信用模块 → 对应 CreditPolicy 信用策略
 * - 成员 E：通知与日志与AI → 对应 PageResult 分页结构
 */
@DisplayName("系统核心规则综合测试")
class SystemCoreTest {

    // ======================== 1. 信用分策略（成员 D）========================

    @Nested
    @DisplayName("1. 信用分策略 CreditPolicy")
    class CreditPolicyTests {

        @Test
        @DisplayName("TC-C001 - 初始信用分100分允许预约")
        void initialScoreCanReserve() {
            assertTrue(CreditPolicy.canReserve(100));
        }

        @Test
        @DisplayName("TC-C002 - 恰好等于门槛60分允许预约")
        void floorScoreCanReserve() {
            assertTrue(CreditPolicy.canReserve(CreditPolicy.FLOOR));
        }

        @Test
        @DisplayName("TC-C003 - 低于门槛59分禁止预约")
        void belowFloorCannotReserve() {
            assertFalse(CreditPolicy.canReserve(59));
        }

        @Test
        @DisplayName("TC-C004 - 信用分为0禁止预约")
        void zeroScoreCannotReserve() {
            assertFalse(CreditPolicy.canReserve(0));
        }

        @Test
        @DisplayName("TC-C005 - 爽约一次扣10分（100→90）")
        void singleNoShowDeduction() {
            assertEquals(90, CreditPolicy.deduct(100));
        }

        @Test
        @DisplayName("TC-C006 - 爽约扣分不低于0")
        void deductionFloorZero() {
            assertEquals(0, CreditPolicy.deduct(5));
            assertEquals(0, CreditPolicy.deduct(0));
        }

        @Test
        @DisplayName("TC-C007 - 签到一次加2分")
        void checkinReward() {
            assertEquals(52, CreditPolicy.reward(50));
        }

        @Test
        @DisplayName("TC-C008 - 签到加分不超过100分上限")
        void rewardCappedAt100() {
            assertEquals(100, CreditPolicy.reward(99));
            assertEquals(100, CreditPolicy.reward(100));
        }

        @Test
        @DisplayName("TC-C009 - 连续4次爽约后恰好为60分（临界值）")
        void fourNoShowsAtFloor() {
            int score = 100;
            for (int i = 0; i < 4; i++) {
                score = CreditPolicy.deduct(score);
            }
            assertEquals(60, score);
            assertTrue(CreditPolicy.canReserve(score));
        }

        @Test
        @DisplayName("TC-C010 - 连续5次爽约后跌破门槛（59分禁止预约）")
        void fiveNoShowsBelowFloor() {
            int score = 100;
            for (int i = 0; i < 5; i++) {
                score = CreditPolicy.deduct(score);
            }
            assertEquals(50, score);
            assertFalse(CreditPolicy.canReserve(score));
        }
    }

    // ======================== 2. 预约状态机（成员 C）========================

    @Nested
    @DisplayName("2. 预约状态机 ReservationState")
    class ReservationStateTests {

        @Test
        @DisplayName("TC-R001 - PENDING→APPROVED 合法流转")
        void pendingToApproved() {
            assertTrue(ReservationState.can("PENDING", "APPROVED"));
        }

        @Test
        @DisplayName("TC-R002 - PENDING→REJECTED 合法流转")
        void pendingToRejected() {
            assertTrue(ReservationState.can("PENDING", "REJECTED"));
        }

        @Test
        @DisplayName("TC-R003 - PENDING→CANCELLED 合法流转")
        void pendingToCancelled() {
            assertTrue(ReservationState.can("PENDING", "CANCELLED"));
        }

        @Test
        @DisplayName("TC-R004 - PENDING→FINISHED 非法流转（不可跳过审批）")
        void pendingCannotFinish() {
            assertFalse(ReservationState.can("PENDING", "FINISHED"));
        }

        @Test
        @DisplayName("TC-R005 - APPROVED→FINISHED 合法流转（预约完成）")
        void approvedToFinished() {
            assertTrue(ReservationState.can("APPROVED", "FINISHED"));
        }

        @Test
        @DisplayName("TC-R006 - APPROVED→CANCELLED 合法流转")
        void approvedToCancelled() {
            assertTrue(ReservationState.can("APPROVED", "CANCELLED"));
        }

        @Test
        @DisplayName("TC-R007 - APPROVED→APPROVED 非法流转（不可重复审批）")
        void approvedCannotReapprove() {
            assertFalse(ReservationState.can("APPROVED", "APPROVED"));
        }

        @Test
        @DisplayName("TC-R008 - APPROVED→REJECTED 非法流转（已通过不可驳回）")
        void approvedCannotReject() {
            assertFalse(ReservationState.can("APPROVED", "REJECTED"));
        }

        @Test
        @DisplayName("TC-R009 - REJECTED 为终态，不可再流转")
        void rejectedIsTerminal() {
            for (String to : new String[]{"PENDING", "APPROVED", "REJECTED", "CANCELLED", "FINISHED"}) {
                assertFalse(ReservationState.can("REJECTED", to));
            }
        }

        @Test
        @DisplayName("TC-R010 - FINISHED 为终态，不可再流转")
        void finishedIsTerminal() {
            for (String to : new String[]{"PENDING", "APPROVED", "REJECTED", "CANCELLED", "FINISHED"}) {
                assertFalse(ReservationState.can("FINISHED", to));
            }
        }

        @Test
        @DisplayName("TC-R011 - null 状态一律拒绝")
        void nullRejected() {
            assertFalse(ReservationState.can(null, "APPROVED"));
            assertFalse(ReservationState.can("PENDING", null));
        }

        @Test
        @DisplayName("TC-R012 - require 非法流转抛 BizException 且消息含状态信息")
        void requireThrowsWithMessage() {
            BizException ex = assertThrows(BizException.class,
                    () -> ReservationState.require("FINISHED", "APPROVED"));
            assertTrue(ex.getMessage().contains("FINISHED"));
            assertTrue(ex.getMessage().contains("APPROVED"));
        }
    }

    // ======================== 3. 时间窗口冲突检测（成员 B & C）========================

    @Nested
    @DisplayName("3. 时间窗口冲突检测 TimeWindow")
    class TimeWindowTests {

        private static final LocalDateTime D = LocalDateTime.of(2026, 11, 20, 0, 0);

        private LocalDateTime at(int h, int m) {
            return D.withHour(h).withMinute(m);
        }

        @Test
        @DisplayName("TC-T001 - 完全相同时段判定为冲突")
        void identicalOverlap() {
            assertTrue(TimeWindow.overlaps(at(9, 0), at(11, 0), at(9, 0), at(11, 0)));
        }

        @Test
        @DisplayName("TC-T002 - 部分重叠（新预约在后）判定为冲突")
        void tailOverlap() {
            assertTrue(TimeWindow.overlaps(at(10, 0), at(12, 0), at(9, 0), at(11, 0)));
        }

        @Test
        @DisplayName("TC-T003 - 部分重叠（新预约在前）判定为冲突")
        void headOverlap() {
            assertTrue(TimeWindow.overlaps(at(9, 0), at(11, 0), at(10, 0), at(12, 0)));
        }

        @Test
        @DisplayName("TC-T004 - 新预约被已有预约完全包含判定为冲突")
        void containedOverlap() {
            assertTrue(TimeWindow.overlaps(at(9, 30), at(10, 30), at(9, 0), at(11, 0)));
        }

        @Test
        @DisplayName("TC-T005 - 新预约完全包含已有预约判定为冲突")
        void containsOverlap() {
            assertTrue(TimeWindow.overlaps(at(8, 0), at(12, 0), at(9, 0), at(11, 0)));
        }

        @Test
        @DisplayName("TC-T006 - 首尾相接（前场结束=后场开始）不算冲突")
        void backToBackNoOverlap() {
            assertFalse(TimeWindow.overlaps(at(11, 0), at(12, 0), at(9, 0), at(11, 0)));
        }

        @Test
        @DisplayName("TC-T007 - 反向首尾相接不算冲突")
        void reverseBackToBackNoOverlap() {
            assertFalse(TimeWindow.overlaps(at(9, 0), at(11, 0), at(11, 0), at(12, 0)));
        }

        @Test
        @DisplayName("TC-T008 - 完全分离的时段不冲突")
        void disjointNoOverlap() {
            assertFalse(TimeWindow.overlaps(at(14, 0), at(16, 0), at(9, 0), at(11, 0)));
        }

        @Test
        @DisplayName("TC-T009 - 冲突判定满足交换律")
        void symmetricOverlap() {
            LocalDateTime s1 = at(9, 30), e1 = at(11, 30), s2 = at(10, 0), e2 = at(12, 0);
            assertTrue(TimeWindow.overlaps(s1, e1, s2, e2));
            assertTrue(TimeWindow.overlaps(s2, e2, s1, e1));
        }

        @Test
        @DisplayName("TC-T010 - 1分钟重叠判定为冲突")
        void oneMinuteOverlap() {
            assertTrue(TimeWindow.overlaps(at(10, 59), at(12, 0), at(9, 0), at(11, 0)));
        }

        @Test
        @DisplayName("TC-T011 - 跨天时段冲突检测")
        void crossDayOverlap() {
            LocalDateTime s1 = LocalDateTime.of(2026, 11, 20, 22, 0);
            LocalDateTime e1 = LocalDateTime.of(2026, 11, 21, 2, 0);
            LocalDateTime s2 = LocalDateTime.of(2026, 11, 21, 0, 0);
            LocalDateTime e2 = LocalDateTime.of(2026, 11, 21, 4, 0);
            assertTrue(TimeWindow.overlaps(s1, e1, s2, e2));
        }

        @Test
        @DisplayName("TC-T012 - 零长度时段（开始=结束）不与任何时段冲突")
        void zeroLengthNoOverlap() {
            assertFalse(TimeWindow.overlaps(at(10, 0), at(10, 0), at(9, 0), at(11, 0)));
        }

        @ParameterizedTest
        @CsvSource({
            "9, 0, 11, 0, 9, 0, 11, 0, true",
            "9, 0, 11, 0, 11, 0, 13, 0, false",
            "9, 0, 11, 0, 10, 30, 12, 0, true",
            "14, 0, 16, 0, 9, 0, 11, 0, false",
            "9, 0, 12, 0, 10, 0, 11, 0, true"
        })
        @DisplayName("TC-T013 - 参数化冲突检测（5组数据）")
        void parameterizedOverlap(int s1h, int s1m, int e1h, int e1m,
                                   int s2h, int s2m, int e2h, int e2m,
                                   boolean expected) {
            boolean result = TimeWindow.overlaps(
                    at(s1h, s1m), at(e1h, e1m),
                    at(s2h, s2m), at(e2h, e2m));
            assertEquals(expected, result);
        }

        @Test
        @DisplayName("TC-T014 - 大跨度时段与小时段冲突")
        void bigSpanOverlap() {
            assertTrue(TimeWindow.overlaps(at(8, 0), at(20, 0), at(10, 0), at(12, 0)));
        }

        @Test
        @DisplayName("TC-T015 - 相邻但不重叠的两个2小时时段不冲突")
        void adjacentTwoHourSlots() {
            assertFalse(TimeWindow.overlaps(at(8, 0), at(10, 0), at(10, 0), at(12, 0)));
            assertFalse(TimeWindow.overlaps(at(12, 0), at(14, 0), at(10, 0), at(12, 0)));
        }
    }

    // ======================== 4. 统一响应结构（成员 A & E）========================

    @Nested
    @DisplayName("4. 统一响应结构 R 与 PageResult")
    class ResponseStructureTests {

        @Test
        @DisplayName("TC-A001 - R.ok() 返回 code=200, msg=操作成功, data=null")
        void okNoData() {
            R<Void> r = R.ok();
            assertEquals(200, r.getCode());
            assertEquals("操作成功", r.getMsg());
            assertNull(r.getData());
        }

        @Test
        @DisplayName("TC-A002 - R.ok(data) 返回携带数据的成功响应")
        void okWithData() {
            R<String> r = R.ok("hello");
            assertEquals(200, r.getCode());
            assertEquals("操作成功", r.getMsg());
            assertEquals("hello", r.getData());
        }

        @Test
        @DisplayName("TC-A003 - R.ok(msg, data) 返回自定义消息和数据")
        void okWithMsgAndData() {
            R<Integer> r = R.ok("新增成功，初始密码 123456", 123456);
            assertEquals(200, r.getCode());
            assertEquals("新增成功，初始密码 123456", r.getMsg());
            assertEquals(123456, r.getData());
        }

        @Test
        @DisplayName("TC-A004 - R.fail(msg) 返回 code=500")
        void failWithMessage() {
            R<Void> r = R.fail("账号或密码错误");
            assertEquals(500, r.getCode());
            assertEquals("账号或密码错误", r.getMsg());
            assertNull(r.getData());
        }

        @Test
        @DisplayName("TC-A005 - R.fail(code, msg) 返回自定义错误码")
        void failWithCodeAndMessage() {
            R<Void> r = R.fail(403, "无权限");
            assertEquals(403, r.getCode());
            assertEquals("无权限", r.getMsg());
        }

        @Test
        @DisplayName("TC-A006 - R.fail(401, msg) 返回未认证错误码")
        void unauthorizedCode() {
            R<Void> r = R.fail(401, "登录已失效");
            assertEquals(401, r.getCode());
        }

        @Test
        @DisplayName("TC-A007 - PageResult 包含 total 和 records")
        void pageResultStructure() {
            PageResult<String> pr = PageResult.of(3L, java.util.List.of("a", "b", "c"));
            assertEquals(3L, pr.getTotal());
            assertEquals(3, pr.getRecords().size());
        }

        @Test
        @DisplayName("TC-A008 - PageResult 空列表 total=0 records=[]")
        void emptyPageResult() {
            PageResult<Object> pr = PageResult.of(0L, java.util.List.of());
            assertEquals(0L, pr.getTotal());
            assertTrue(pr.getRecords().isEmpty());
        }

        @Test
        @DisplayName("TC-A009 - PageResult 单条数据")
        void singleRecordPageResult() {
            PageResult<Integer> pr = PageResult.of(1L, java.util.List.of(42));
            assertEquals(1L, pr.getTotal());
            assertEquals(42, pr.getRecords().get(0));
        }

        @Test
        @DisplayName("TC-A010 - R.ok(null) 返回 data=null 的成功响应")
        void okWithNullData() {
            R<Object> r = R.ok(null);
            assertEquals(200, r.getCode());
            assertNull(r.getData());
        }
    }

    // ======================== 5. 业务异常 BizException（成员 A）========================

    @Nested
    @DisplayName("5. 业务异常 BizException")
    class BizExceptionTests {

        @Test
        @DisplayName("TC-E001 - 默认错误码为500")
        void defaultCode500() {
            BizException ex = new BizException("系统错误");
            assertEquals(500, ex.getCode());
            assertEquals("系统错误", ex.getMessage());
        }

        @Test
        @DisplayName("TC-E002 - 自定义错误码403")
        void customCode403() {
            BizException ex = new BizException(403, "无权限");
            assertEquals(403, ex.getCode());
        }

        @Test
        @DisplayName("TC-E003 - 自定义错误码401")
        void customCode401() {
            BizException ex = new BizException(401, "登录已失效");
            assertEquals(401, ex.getCode());
        }

        @Test
        @DisplayName("TC-E004 - 异常是 RuntimeException 子类")
        void isRuntimeException() {
            BizException ex = new BizException("test");
            assertInstanceOf(RuntimeException.class, ex);
        }

        @Test
        @DisplayName("TC-E005 - 异常可被 try-catch 捕获")
        void canBeCaught() {
            try {
                throw new BizException("捕获测试");
            } catch (BizException ex) {
                assertEquals("捕获测试", ex.getMessage());
            }
        }
    }

    // ======================== 6. 边界与组合场景（全员）========================

    @Nested
    @DisplayName("6. 边界与组合场景")
    class BoundaryAndCompositeTests {

        @Test
        @DisplayName("TC-B001 - 信用分边界：60→59后禁止预约再禁止")
        void creditBoundaryTransition() {
            assertTrue(CreditPolicy.canReserve(60));
            assertFalse(CreditPolicy.canReserve(59));
        }

        @Test
        @DisplayName("TC-B002 - 签到奖励+爽约惩罚交替后的最终分数")
        void mixedCreditChanges() {
            int score = 100;
            score = CreditPolicy.reward(score);  // 100 (上限)
            score = CreditPolicy.deduct(score);  // 90
            score = CreditPolicy.reward(score);  // 92
            score = CreditPolicy.deduct(score);  // 82
            assertEquals(82, score);
            assertTrue(CreditPolicy.canReserve(score));
        }

        @Test
        @DisplayName("TC-B003 - 预约状态机完整生命周期 PENDING→APPROVED→FINISHED")
        void fullLifecycle() {
            assertTrue(ReservationState.can("PENDING", "APPROVED"));
            assertTrue(ReservationState.can("APPROVED", "FINISHED"));
        }

        @Test
        @DisplayName("TC-B004 - 预约取消后不可恢复")
        void cancelledCannotResume() {
            assertFalse(ReservationState.can("CANCELLED", "PENDING"));
            assertFalse(ReservationState.can("CANCELLED", "APPROVED"));
        }

        @Test
        @DisplayName("TC-B005 - 同一实验室连续时段预约不冲突（8-10, 10-12, 12-14）")
        void consecutiveSlotsNoConflict() {
            LocalDateTime D = LocalDateTime.of(2026, 11, 20, 0, 0);
            assertFalse(TimeWindow.overlaps(D.withHour(8), D.withHour(10), D.withHour(10), D.withHour(12)));
            assertFalse(TimeWindow.overlaps(D.withHour(10), D.withHour(12), D.withHour(12), D.withHour(14)));
        }

        @Test
        @DisplayName("TC-B006 - 信用分扣到0后签到奖励恢复到2分")
        void recoverFromZero() {
            assertEquals(2, CreditPolicy.reward(0));
        }

        @Test
        @DisplayName("TC-B007 - 状态机 require 合法流转不抛异常")
        void requireValidNoThrow() {
            assertDoesNotThrow(() -> ReservationState.require("PENDING", "APPROVED"));
            assertDoesNotThrow(() -> ReservationState.require("PENDING", "REJECTED"));
            assertDoesNotThrow(() -> ReservationState.require("APPROVED", "FINISHED"));
            assertDoesNotThrow(() -> ReservationState.require("APPROVED", "CANCELLED"));
        }

        @Test
        @DisplayName("TC-B008 - 极端信用分场景：从100连续爽约10次到0分")
        void tenNoShowsToZero() {
            int score = 100;
            for (int i = 0; i < 10; i++) {
                score = CreditPolicy.deduct(score);
            }
            assertEquals(0, score);
            assertFalse(CreditPolicy.canReserve(score));
        }
    }
}
