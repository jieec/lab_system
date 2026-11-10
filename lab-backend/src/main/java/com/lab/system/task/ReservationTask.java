package com.lab.system.task;

import com.lab.system.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：爽约判定 + 预约结束自动完成。
 * 先判爽约再置完成，两条逻辑都对 FINISHED 状态做了兼容，顺序互换也不会漏判。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationTask {

    private final ReservationService reservationService;

    @Scheduled(cron = "0 */10 * * * ?")
    public void scanExpired() {
        int noShow = reservationService.markNoShow();
        int finished = reservationService.finishExpired();
        if (noShow > 0 || finished > 0) {
            log.info("预约扫描完成：爽约 {} 条，自动结束 {} 条", noShow, finished);
        }
    }
}
