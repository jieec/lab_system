package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("credit_record")
public class CreditRecord {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String eventKey;
    private String eventType;
    private Integer changeAmount;
    private Integer scoreBefore;
    private Integer scoreAfter;
    private String reason;
    private Long operatorId;
    private LocalDateTime createTime;
}
