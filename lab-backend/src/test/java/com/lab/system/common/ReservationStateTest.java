package com.lab.system.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 预约状态机合法流转。 */
class ReservationStateTest {

    @Test
    @DisplayName("PENDING 可通过 / 驳回 / 取消")
    void pendingTransitions() {
        assertTrue(ReservationState.can("PENDING", "APPROVED"));
        assertTrue(ReservationState.can("PENDING", "REJECTED"));
        assertTrue(ReservationState.can("PENDING", "CANCELLED"));
    }

    @Test
    @DisplayName("APPROVED 可完成 / 取消")
    void approvedTransitions() {
        assertTrue(ReservationState.can("APPROVED", "FINISHED"));
        assertTrue(ReservationState.can("APPROVED", "CANCELLED"));
    }

    @Test
    @DisplayName("PENDING 不能直接跳到 FINISHED")
    void pendingCannotFinish() {
        assertFalse(ReservationState.can("PENDING", "FINISHED"));
    }

    @Test
    @DisplayName("APPROVED 不能被重复审批")
    void approvedCannotReapprove() {
        assertFalse(ReservationState.can("APPROVED", "APPROVED"));
        assertFalse(ReservationState.can("APPROVED", "REJECTED"));
    }

    @Test
    @DisplayName("终态不可再流转")
    void terminalStates() {
        for (String terminal : new String[]{"REJECTED", "CANCELLED", "FINISHED"}) {
            for (String to : new String[]{"PENDING", "APPROVED", "REJECTED", "CANCELLED", "FINISHED"}) {
                assertFalse(ReservationState.can(terminal, to), terminal + " -> " + to);
            }
        }
    }

    @Test
    @DisplayName("null 与未知状态一律拒绝")
    void nullAndUnknown() {
        assertFalse(ReservationState.can(null, "APPROVED"));
        assertFalse(ReservationState.can("PENDING", null));
        assertFalse(ReservationState.can("UNKNOWN", "APPROVED"));
    }

    @Test
    @DisplayName("require 合法流转不抛异常")
    void requirePasses() {
        assertDoesNotThrow(() -> ReservationState.require("PENDING", "APPROVED"));
        assertDoesNotThrow(() -> ReservationState.require("APPROVED", "FINISHED"));
    }

    @Test
    @DisplayName("require 非法流转抛 BizException 且消息含状态")
    void requireThrows() {
        BizException ex = assertThrows(BizException.class,
                () -> ReservationState.require("FINISHED", "APPROVED"));
        assertTrue(ex.getMessage().contains("FINISHED"));
        assertTrue(ex.getMessage().contains("APPROVED"));
    }
}
