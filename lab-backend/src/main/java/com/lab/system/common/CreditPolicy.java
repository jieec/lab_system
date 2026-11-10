package com.lab.system.common;

/**
 * 信用分规则：爽约扣分、签到加分、预约门槛的唯一定义处。
 */
public final class CreditPolicy {

    /** 低于此分数禁止预约 */
    public static final int FLOOR = 60;
    public static final int MAX = 100;
    public static final int NO_SHOW_PENALTY = 10;
    public static final int CHECKIN_REWARD = 2;
    /** 近 7 天内达到此爽约次数，暂停预约权限 */
    public static final int RECENT_VIOLATION_LIMIT = 3;

    private CreditPolicy() {
    }

    public static boolean canReserve(int score) {
        return score >= FLOOR;
    }

    public static int deduct(int score) {
        return Math.max(0, score - NO_SHOW_PENALTY);
    }

    public static int reward(int score) {
        return Math.min(MAX, score + CHECKIN_REWARD);
    }
}
