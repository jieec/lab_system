package com.lab.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RepairCreateDTO {

    private Long equipmentId;

    /** Asset code on the equipment label; must match ID when both are supplied. */
    private String equipmentCode;

    private String faultType;

    private String urgency;

    @NotBlank(message = "请描述故障现象")
    private String faultDesc;
}
