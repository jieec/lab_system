package com.lab.system.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RepairFinishDTO {

    private String repairDesc;

    private BigDecimal cost;
}
