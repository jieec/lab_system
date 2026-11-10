package com.lab.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.system.common.PageResult;
import com.lab.system.entity.LabClosure;
import com.lab.system.entity.SysLog;
import com.lab.system.mapper.SysLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 审计日志查询（仅管理员）
 */
@Service
@RequiredArgsConstructor
public class LogService {

    private final SysLogMapper logMapper;

    public PageResult<SysLog> page(int pageNum, int pageSize, String module, String keyword) {
        Page<SysLog> page = logMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<SysLog>lambdaQuery()
                        .eq(StringUtils.hasText(module), SysLog::getModule, module)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(SysLog::getOperatorName, keyword).or()
                                .like(SysLog::getAction, keyword).or()
                                .like(SysLog::getUri, keyword))
                        .orderByDesc(SysLog::getId));
        return PageResult.of(page.getTotal(), page.getRecords());
    }
}
