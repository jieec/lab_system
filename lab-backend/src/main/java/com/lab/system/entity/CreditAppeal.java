package com.lab.system.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;
@Data @TableName("credit_appeal")
public class CreditAppeal {
    @TableId(type=IdType.AUTO) private Long id;
    private Long userId;
    private String reason;
    private String status;
    private String reviewReason;
    private Long reviewerId;
    private LocalDateTime createTime;
    private LocalDateTime reviewTime;
}
