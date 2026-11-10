package com.lab.system.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 信用分规则：门槛、扣分下限、加分上限。 */
class CreditPolicyTest {

    @Test
    @DisplayName("等于门槛 60 分允许预约")
    void floorIsInclusive() {
        assertTrue(CreditPolicy.canReserve(CreditPolicy.FLOOR));
        assertTrue(CreditPolicy.canReserve(100));
    }

    @Test
    @DisplayName("低于门槛禁止预约")
    void belowFloor() {
        assertFalse(CreditPolicy.canReserve(CreditPolicy.FLOOR - 1));
        assertFalse(CreditPolicy.canReserve(0));
    }

    @Test
    @DisplayName("爽约扣 10 分")
    void deduct() {
        assertEquals(90, CreditPolicy.deduct(100));
        assertEquals(50, CreditPolicy.deduct(60));
    }

    @Test
    @DisplayName("扣分不会低于 0")
    void deductFloor() {
        assertEquals(0, CreditPolicy.deduct(5));
        assertEquals(0, CreditPolicy.deduct(0));
    }

    @Test
    @DisplayName("签到加 2 分")
    void reward() {
        assertEquals(52, CreditPolicy.reward(50));
        assertEquals(100, CreditPolicy.reward(98));
    }

    @Test
    @DisplayName("加分不会超过 100")
    void rewardCap() {
        assertEquals(100, CreditPolicy.reward(99));
        assertEquals(100, CreditPolicy.reward(100));
    }

    @Test
    @DisplayName("连续 4 次爽约后跌破门槛")
    void fourNoShowsDropBelowFloor() {
        int score = 100;
        for (int i = 0; i < 4; i++) {
            score = CreditPolicy.deduct(score);
        }
        assertEquals(60, score);
        assertTrue(CreditPolicy.canReserve(score));
        assertFalse(CreditPolicy.canReserve(CreditPolicy.deduct(score)));
    }
}
