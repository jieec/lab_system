package com.lab.system.dto;

import lombok.Data;

@Data
public class RepairVerifyDTO {

    /** 验收评分 1-5 */
    private Integer score;

    private String verifyRemark;
}
