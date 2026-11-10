package com.lab.system.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预约冲突判定的边界用例。
 * 规则：半开区间 [s,e)，相交 <=> s1 < e2 && e1 > s2。
 */
class TimeWindowTest {

    private static final LocalDateTime D = LocalDateTime.of(2026, 11, 10, 0, 0);

    private LocalDateTime at(int h, int m) {
        return D.withHour(h).withMinute(m);
    }

    @Test
    @DisplayName("完全相同的时间段判定为冲突")
    void identical() {
        assertTrue(TimeWindow.overlaps(at(9, 0), at(11, 0), at(9, 0), at(11, 0)));
    }

    @Test
    @DisplayName("部分重叠（新预约在后）判定为冲突")
    void partialOverlapTail() {
        assertTrue(TimeWindow.overlaps(at(10, 0), at(12, 0), at(9, 0), at(11, 0)));
    }

    @Test
    @DisplayName("部分重叠（新预约在前）判定为冲突")
    void partialOverlapHead() {
        assertTrue(TimeWindow.overlaps(at(9, 0), at(11, 0), at(10, 0), at(12, 0)));
    }

    @Test
    @DisplayName("被已有预约完全包含判定为冲突")
    void contained() {
        assertTrue(TimeWindow.overlaps(at(9, 30), at(10, 30), at(9, 0), at(11, 0)));
    }

    @Test
    @DisplayName("完全包含已有预约判定为冲突")
    void contains() {
        assertTrue(TimeWindow.overlaps(at(8, 0), at(12, 0), at(9, 0), at(11, 0)));
    }

    @Test
    @DisplayName("首尾相接（上一场结束=下一场开始）不算冲突")
    void touchingBackToBack() {
        assertFalse(TimeWindow.overlaps(at(11, 0), at(12, 0), at(9, 0), at(11, 0)));
    }

    @Test
    @DisplayName("反向首尾相接同样不算冲突")
    void touchingReverse() {
        assertFalse(TimeWindow.overlaps(at(9, 0), at(11, 0), at(11, 0), at(12, 0)));
    }

    @Test
    @DisplayName("完全分离的时段不冲突")
    void disjoint() {
        assertFalse(TimeWindow.overlaps(at(14, 0), at(16, 0), at(9, 0), at(11, 0)));
    }

    @Test
    @DisplayName("判定满足交换律")
    void symmetric() {
        LocalDateTime s1 = at(9, 30), e1 = at(11, 30), s2 = at(10, 0), e2 = at(12, 0);
        assertTrue(TimeWindow.overlaps(s1, e1, s2, e2));
        assertTrue(TimeWindow.overlaps(s2, e2, s1, e1));
    }
}
