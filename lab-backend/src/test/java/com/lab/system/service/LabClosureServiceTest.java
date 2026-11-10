package com.lab.system.service;

import com.lab.system.common.BizException;
import com.lab.system.entity.LabClosure;
import com.lab.system.mapper.LabClosureMapper;
import com.lab.system.mapper.LabMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 闭馆规则：全校闭馆 / 单实验室闭馆 / 重复安排。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LabClosureServiceTest {

    @Mock
    private LabClosureMapper closureMapper;
    @Mock
    private LabMapper labMapper;
    @InjectMocks
    private LabClosureService closureService;

    private final LocalDate date = LocalDate.of(2026, 12, 25);

    @Test
    @DisplayName("无闭馆记录时放行")
    void noClosure() {
        when(closureMapper.selectCount(any())).thenReturn(0L);
        assertDoesNotThrow(() -> closureService.ensureOpen(1L, date));
    }

    @Test
    @DisplayName("全校闭馆（lab_id 为空）时拒绝并带出原因")
    void campusClosure() {
        LabClosure c = new LabClosure();
        c.setLabId(null);
        c.setReason("圣诞节放假");
        when(closureMapper.selectCount(any())).thenReturn(1L);
        when(closureMapper.selectOne(any())).thenReturn(c);
        BizException ex = assertThrows(BizException.class, () -> closureService.ensureOpen(1L, date));
        assertTrue(ex.getMessage().contains("闭馆"));
        assertTrue(ex.getMessage().contains("圣诞节放假"));
    }

    @Test
    @DisplayName("单实验室闭馆时拒绝")
    void singleLabClosure() {
        when(closureMapper.selectCount(any())).thenReturn(1L);
        when(closureMapper.selectOne(any())).thenReturn(null);
        BizException ex = assertThrows(BizException.class, () -> closureService.ensureOpen(1L, date));
        assertTrue(ex.getMessage().contains(date.toString()));
    }

    @Test
    @DisplayName("同一天重复添加闭馆安排被拒绝")
    void duplicateClosure() {
        LabClosure c = new LabClosure();
        c.setLabId(1L);
        c.setClosureDate(date);
        when(closureMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BizException.class, () -> closureService.add(c));
    }

    @Test
    @DisplayName("全校闭馆（lab_id 为 null）重复添加同样被拒绝")
    void duplicateCampusClosure() {
        // 注：查重 SQL 必须用 isNull() 而不是 eq(column, null)，否则
        // `lab_id = NULL` 永远不成立、同一天可反复新增全校闭馆。
        // 该 SQL 语义由 scripts/smoke_test.py 的「重复闭馆日期被拒绝」断言做端到端验证；
        // 这里只校验业务侧的拒绝行为，避免在纯 Mockito 环境里触发 MyBatis-Plus lambda 缓存。
        LabClosure c = new LabClosure();
        c.setLabId(null);
        c.setClosureDate(date);
        when(closureMapper.selectCount(any())).thenReturn(1L);
        assertThrows(BizException.class, () -> closureService.add(c));
    }

    @Test
    @DisplayName("闭馆日期为空被拒绝")
    void emptyDate() {
        assertThrows(BizException.class, () -> closureService.add(new LabClosure()));
    }
}
