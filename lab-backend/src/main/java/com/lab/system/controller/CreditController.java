package com.lab.system.controller;
import com.lab.system.common.*;
import com.lab.system.entity.CreditRecord;
import com.lab.system.security.*;
import com.lab.system.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/credit") @RequiredArgsConstructor
public class CreditController {
    private final CreditService credit;
    private final com.lab.system.service.CreditAppealService appeals;
    public record AppealRequest(String reason){}
    public record ReviewRequest(Boolean approve,String reason){}
    @GetMapping("/appeals") public R<PageResult<com.lab.system.entity.CreditAppeal>> appeals(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="false") boolean pending){return R.ok(appeals.page(pageNum,pending));}
    @PostMapping("/appeals") @OperationLog(module="信用管理",value="申请信用恢复")
    public R<com.lab.system.entity.CreditAppeal> apply(@RequestBody AppealRequest dto){return R.ok(appeals.apply(dto.reason()));}
    @PostMapping("/appeals/{id}/review") @RequireRole("ADMIN") @OperationLog(module="信用管理",value="审核信用恢复")
    public R<com.lab.system.entity.CreditAppeal> review(@PathVariable Long id,@RequestBody ReviewRequest dto){if(dto.approve()==null)throw new BizException("请选择审核结果");return R.ok(appeals.review(id,dto.approve(),dto.reason()));}
    @GetMapping("/me") public R<Map<String,Object>> me(){return R.ok(credit.summary());}
    @GetMapping("/records") public R<PageResult<CreditRecord>> records(@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="10") int pageSize){return R.ok(credit.myRecords(pageNum,pageSize));}
    public record Adjustment(Long userId,Integer targetScore,String reason){}
    @PostMapping("/adjust") @RequireRole("ADMIN") @OperationLog(module="信用管理",value="调整信用分")
    public R<CreditRecord> adjust(@RequestBody Adjustment dto){
        if(dto.userId()==null||dto.targetScore()==null)throw new BizException("请提供用户和目标分数");
        return R.ok(credit.adjust(dto.userId(),dto.targetScore(),dto.reason()));
    }
}
