package com.lab.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReservationDTO {

    @NotNull(message = "请选择实验室")
    private Long labId;

    @NotNull(message = "请选择开始时间")
    private LocalDateTime startTime;

    @NotNull(message = "请选择结束时间")
    private LocalDateTime endTime;

    @Min(value = 1, message = "使用人数至少为 1")
    private Integer headcount;

    private String purpose;

    private String remark;
}
