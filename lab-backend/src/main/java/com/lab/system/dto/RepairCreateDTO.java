package com.lab.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RepairCreateDTO {

    @NotNull(message = "请选择报修设备")
    private Long equipmentId;

    private String faultType;

    private String urgency;

    @NotBlank(message = "请描述故障现象")
    private String faultDesc;
}
