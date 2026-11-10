package com.lab.system.dto;

import lombok.Data;

@Data
public class ApproveDTO {

    /** 是否通过 */
    private Boolean pass;

    private String remark;
}
