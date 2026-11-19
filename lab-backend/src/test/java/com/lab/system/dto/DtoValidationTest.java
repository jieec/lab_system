package com.lab.system.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求参数校验测试（成员 A / 成员 C / 成员 D）
 *
 * 覆盖 Controller 入口的 Bean Validation 注解，确保非法参数在进入业务逻辑前被拦截。
 * 这类校验是防脏数据的第一道门，漏一个就会写进数据库。
 */
@DisplayName("请求参数校验")
class DtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private Set<String> messages(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    private boolean valid(Object dto) {
        return validator.validate(dto).isEmpty();
    }

    // ================= 登录参数 =================

    @Test
    @DisplayName("TC-V001 - 账号密码齐全时校验通过")
    void loginValid() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("123456");
        assertTrue(valid(dto));
    }

    @Test
    @DisplayName("TC-V002 - 账号为空时提示「请输入账号」")
    void loginBlankUsername() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("  ");
        dto.setPassword("123456");
        assertTrue(messages(dto).contains("请输入账号"));
    }

    @Test
    @DisplayName("TC-V003 - 密码为空时提示「请输入密码」")
    void loginBlankPassword() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("admin");
        dto.setPassword("");
        assertTrue(messages(dto).contains("请输入密码"));
    }

    @Test
    @DisplayName("TC-V004 - 账号密码都为空时产生两条校验错误")
    void loginBothBlank() {
        assertEquals(2, validator.validate(new LoginDTO()).size());
    }

    // ================= 修改密码参数 =================

    @Test
    @DisplayName("TC-V005 - 新密码 6 位时校验通过（下边界）")
    void passwordMinLength() {
        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("123456");
        dto.setNewPassword("123456");
        assertTrue(valid(dto));
    }

    @Test
    @DisplayName("TC-V006 - 新密码 20 位时校验通过（上边界）")
    void passwordMaxLength() {
        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("123456");
        dto.setNewPassword("a".repeat(20));
        assertTrue(valid(dto));
    }

    @Test
    @DisplayName("TC-V007 - 新密码 5 位时校验失败")
    void passwordTooShort() {
        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("123456");
        dto.setNewPassword("12345");
        assertTrue(messages(dto).contains("密码长度为 6-20 位"));
    }

    @Test
    @DisplayName("TC-V008 - 新密码 21 位时校验失败")
    void passwordTooLong() {
        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("123456");
        dto.setNewPassword("a".repeat(21));
        assertTrue(messages(dto).contains("密码长度为 6-20 位"));
    }

    @Test
    @DisplayName("TC-V009 - 原密码为空时校验失败")
    void oldPasswordBlank() {
        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("");
        dto.setNewPassword("654321");
        assertTrue(messages(dto).contains("请输入原密码"));
    }

    // ================= 预约参数 =================

    private ReservationDTO reservation(Long labId, LocalDateTime start, LocalDateTime end, Integer headcount) {
        ReservationDTO dto = new ReservationDTO();
        dto.setLabId(labId);
        dto.setStartTime(start);
        dto.setEndTime(end);
        dto.setHeadcount(headcount);
        dto.setPurpose("课程实验");
        return dto;
    }

    @Test
    @DisplayName("TC-V010 - 完整预约参数校验通过")
    void reservationValid() {
        LocalDateTime s = LocalDateTime.now().plusDays(1);
        assertTrue(valid(reservation(1L, s, s.plusHours(2), 5)));
    }

    @Test
    @DisplayName("TC-V011 - 未选实验室时提示「请选择实验室」")
    void reservationNoLab() {
        LocalDateTime s = LocalDateTime.now().plusDays(1);
        assertTrue(messages(reservation(null, s, s.plusHours(2), 5)).contains("请选择实验室"));
    }

    @Test
    @DisplayName("TC-V012 - 未选开始时间时提示「请选择开始时间」")
    void reservationNoStart() {
        assertTrue(messages(reservation(1L, null, LocalDateTime.now(), 5))
                .contains("请选择开始时间"));
    }

    @Test
    @DisplayName("TC-V013 - 未选结束时间时提示「请选择结束时间」")
    void reservationNoEnd() {
        assertTrue(messages(reservation(1L, LocalDateTime.now(), null, 5))
                .contains("请选择结束时间"));
    }

    @Test
    @DisplayName("TC-V014 - 使用人数为 0 时提示「使用人数至少为 1」")
    void reservationZeroHeadcount() {
        LocalDateTime s = LocalDateTime.now().plusDays(1);
        assertTrue(messages(reservation(1L, s, s.plusHours(2), 0))
                .contains("使用人数至少为 1"));
    }

    @Test
    @DisplayName("TC-V015 - 使用人数为负数时校验失败")
    void reservationNegativeHeadcount() {
        LocalDateTime s = LocalDateTime.now().plusDays(1);
        assertFalse(valid(reservation(1L, s, s.plusHours(2), -1)));
    }

    // ================= 报修参数 =================

    @Test
    @DisplayName("TC-V016 - 故障描述非空时报修参数校验通过")
    void repairValid() {
        RepairCreateDTO dto = new RepairCreateDTO();
        dto.setEquipmentId(1L);
        dto.setFaultDesc("屏幕无法显示波形");
        dto.setUrgency("HIGH");
        assertTrue(valid(dto));
    }

    @Test
    @DisplayName("TC-V017 - 故障描述为空时提示「请描述故障现象」")
    void repairBlankDesc() {
        RepairCreateDTO dto = new RepairCreateDTO();
        dto.setEquipmentId(1L);
        dto.setFaultDesc("   ");
        assertTrue(messages(dto).contains("请描述故障现象"));
    }

    @Test
    @DisplayName("TC-V018 - 报修参数为空对象时至少产生一条校验错误")
    void repairEmpty() {
        assertFalse(validator.validate(new RepairCreateDTO()).isEmpty());
    }

    // ================= 改期参数 =================

    @Test
    @DisplayName("TC-V019 - 改期时间齐全时校验通过")
    void rescheduleValid() {
        RescheduleDTO dto = new RescheduleDTO();
        dto.setStartTime(LocalDateTime.now().plusDays(2));
        dto.setEndTime(LocalDateTime.now().plusDays(2).plusHours(2));
        assertTrue(valid(dto));
    }

    @Test
    @DisplayName("TC-V020 - 改期未填开始时间时提示「开始时间不能为空」")
    void rescheduleNoStart() {
        RescheduleDTO dto = new RescheduleDTO();
        dto.setEndTime(LocalDateTime.now().plusDays(2));
        assertTrue(messages(dto).contains("开始时间不能为空"));
    }
}
