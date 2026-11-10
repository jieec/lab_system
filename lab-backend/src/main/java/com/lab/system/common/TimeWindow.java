package com.lab.system.common;

import java.time.LocalDateTime;

/**
 * 时间区间规则：预约冲突判定的唯一定义处。
 *
 * 两个半开区间 [s1,e1) 与 [s2,e2) 相交，当且仅当 s1 &lt; e2 且 e1 &gt; s2。
 * 使用严格不等号，因此「上一场 10:00 结束、下一场 10:00 开始」不算冲突。
 */
public final class TimeWindow {

    private TimeWindow() {
    }

    public static boolean overlaps(LocalDateTime s1, LocalDateTime e1,
                                   LocalDateTime s2, LocalDateTime e2) {
        return s1.isBefore(e2) && e1.isAfter(s2);
    }
}
