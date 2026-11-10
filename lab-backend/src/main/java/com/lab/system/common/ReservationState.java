package com.lab.system.common;

/**
 * 预约状态机：集中定义合法流转，避免状态字符串散落在各 Service 里。
 *
 * PENDING   → APPROVED / REJECTED / CANCELLED
 * APPROVED  → FINISHED / CANCELLED
 * 其余状态为终态，不可再流转。
 */
public final class ReservationState {

    private ReservationState() {
    }

    public static boolean can(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case "PENDING" -> "APPROVED".equals(to) || "REJECTED".equals(to) || "CANCELLED".equals(to);
            case "APPROVED" -> "FINISHED".equals(to) || "CANCELLED".equals(to);
            default -> false;
        };
    }

    public static void require(String from, String to) {
        if (!can(from, to)) {
            throw new BizException("当前状态不可执行该操作（" + from + " → " + to + "）");
        }
    }
}
