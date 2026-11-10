package com.lab.system.controller;

import com.lab.system.common.PageResult;
import com.lab.system.common.R;
import com.lab.system.dto.ApproveDTO;
import com.lab.system.dto.RescheduleDTO;
import com.lab.system.dto.ReservationDTO;
import com.lab.system.entity.Reservation;
import com.lab.system.security.OperationLog;
import com.lab.system.security.RequireRole;
import com.lab.system.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
@Tag(name = "实验室预约", description = "预约申请、审批、签到、改期与时段查询")
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @Operation(summary = "提交预约申请")
    @OperationLog(module = "预约管理", value = "提交预约申请")
    public R<Reservation> create(@Valid @RequestBody ReservationDTO dto) {
        Reservation r = reservationService.create(dto);
        String msg = "PENDING".equals(r.getStatus()) ? "提交成功，等待审批" : "预约成功";
        return R.ok(msg, r);
    }

    @GetMapping("/my")
    public R<PageResult<Reservation>> my(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String status) {
        return R.ok(reservationService.myPage(pageNum, pageSize, status));
    }

    @GetMapping
    @RequireRole({"ADMIN", "TEACHER"})
    public R<PageResult<Reservation>> page(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Long labId,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String date) {
        return R.ok(reservationService.page(pageNum, pageSize, status, labId, keyword, date));
    }

    @GetMapping("/timeline")
    public R<List<Reservation>> timeline(@RequestParam Long labId,
                                         @RequestParam(required = false) String date) {
        return R.ok(reservationService.timeline(labId, date));
    }

    @GetMapping("/{id}")
    public R<Reservation> detail(@PathVariable Long id) {
        return R.ok(reservationService.detail(id));
    }

    @PutMapping("/{id}/approve")
    @RequireRole({"ADMIN", "TEACHER"})
    @Operation(summary = "审批预约")
    @OperationLog(module = "预约管理", value = "审批预约")
    public R<Void> approve(@PathVariable Long id, @RequestBody ApproveDTO dto) {
        reservationService.approve(id, dto);
        return R.ok();
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "取消预约")
    @OperationLog(module = "预约管理", value = "取消预约")
    public R<Void> cancel(@PathVariable Long id) {
        reservationService.cancel(id);
        return R.ok();
    }

    @PutMapping("/{id}/checkin")
    @Operation(summary = "预约签到")
    @OperationLog(module = "预约管理", value = "预约签到")
    public R<Void> checkin(@PathVariable Long id) {
        reservationService.checkin(id);
        return R.ok();
    }

    @PutMapping("/{id}/reschedule")
    @Operation(summary = "申请改期")
    @OperationLog(module = "预约管理", value = "预约改期")
    public R<Void> reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleDTO dto) {
        reservationService.reschedule(id, dto);
        return R.ok();
    }
}
