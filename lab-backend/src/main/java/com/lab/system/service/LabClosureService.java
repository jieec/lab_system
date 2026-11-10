package com.lab.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lab.system.common.BizException;
import com.lab.system.entity.Lab;
import com.lab.system.entity.LabClosure;
import com.lab.system.mapper.LabClosureMapper;
import com.lab.system.mapper.LabMapper;
import com.lab.system.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 实验室闭馆安排
 */
@Service
@RequiredArgsConstructor
public class LabClosureService {

    private final LabClosureMapper closureMapper;
    private final LabMapper labMapper;

    public List<LabClosure> list(LocalDate from, LocalDate to) {
        List<LabClosure> list = closureMapper.selectList(Wrappers.<LabClosure>lambdaQuery()
                .ge(from != null, LabClosure::getClosureDate, from)
                .le(to != null, LabClosure::getClosureDate, to)
                .orderByAsc(LabClosure::getClosureDate));
        Map<Long, String> labNames = new HashMap<>();
        labMapper.selectList(null).forEach(l -> labNames.put(l.getId(), l.getName()));
        list.forEach(c -> c.setLabName(c.getLabId() == null ? "全校" : labNames.get(c.getLabId())));
        return list;
    }

    public void add(LabClosure closure) {
        if (closure.getClosureDate() == null) {
            throw new BizException("闭馆日期不能为空");
        }
        Long labId = closure.getLabId();
        // lab_id 为 NULL 代表全校闭馆。这里必须用 isNull()：
        // eq(column, null) 会生成 `lab_id = NULL`，SQL 三值逻辑下永远不成立，
        // 会导致同一天可以反复新增全校闭馆。
        var query = Wrappers.<LabClosure>lambdaQuery()
                .eq(LabClosure::getClosureDate, closure.getClosureDate());
        if (labId == null) {
            query.isNull(LabClosure::getLabId);
        } else {
            query.eq(LabClosure::getLabId, labId);
        }
        boolean exists = closureMapper.selectCount(query) > 0;
        if (exists) {
            throw new BizException("该日期已有闭馆安排");
        }
        closure.setId(null);
        closure.setCreateBy(UserContext.userId());
        closureMapper.insert(closure);
    }

    public void remove(Long id) {
        closureMapper.deleteById(id);
    }

    /**
     * 校验某实验室在指定日期是否闭馆。lab_id 为 NULL 的记录代表全校闭馆。
     */
    public void ensureOpen(Long labId, LocalDate date) {
        Long count = closureMapper.selectCount(Wrappers.<LabClosure>lambdaQuery()
                .eq(LabClosure::getClosureDate, date)
                .and(w -> w.isNull(LabClosure::getLabId).or().eq(LabClosure::getLabId, labId)));
        if (count > 0) {
            LabClosure hit = closureMapper.selectOne(Wrappers.<LabClosure>lambdaQuery()
                    .eq(LabClosure::getClosureDate, date)
                    .and(w -> w.isNull(LabClosure::getLabId).or().eq(LabClosure::getLabId, labId))
                    .last("LIMIT 1"));
            String reason = hit != null && hit.getReason() != null && !hit.getReason().isBlank()
                    ? "（" + hit.getReason() + "）" : "";
            throw new BizException(date + " 该实验室闭馆" + reason + "，不可预约");
        }
    }
}
