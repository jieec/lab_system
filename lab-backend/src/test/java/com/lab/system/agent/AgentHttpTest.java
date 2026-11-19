package com.lab.system.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.system.common.PageResult;
import com.lab.system.config.WebConfig;
import com.lab.system.config.JacksonConfig;
import com.lab.system.controller.AgentController;
import com.lab.system.entity.*;
import com.lab.system.mapper.SysUserMapper;
import com.lab.system.security.*;
import com.lab.system.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC, JWT interceptor, worker-thread identity and SSE serialization; services use isolated mocks. */
@WebMvcTest(AgentController.class)
@ContextConfiguration(classes = AgentHttpTest.MvcConfig.class)
@Import({WebConfig.class, JacksonConfig.class, AuthInterceptor.class, JwtUtil.class, AgentService.class,
        AgentProperties.class, AgentSessions.class, AgentTools.class, RuleFallback.class})
@TestPropertySource(properties = {"lab.llm.enabled=false", "lab.jwt.secret=test-agent-secret-with-at-least-32-bytes-123456", "lab.jwt.expire-hours=1"})
class AgentHttpTest {
    @Configuration(proxyBeanMethods = false)
    @Import({AgentController.class, com.lab.system.common.GlobalExceptionHandler.class})
    static class MvcConfig {}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;
    @MockBean SysUserMapper users;
    @MockBean LabService labs;
    @MockBean EquipmentService equipments;
    @MockBean ReservationService reservations;
    @MockBean RepairService repairs;
    @MockBean StatsService stats;
    @MockBean CreditService credit;
    @MockBean LlmClient llm;
    String token;

    String token(long id) {
        LoginUser user = new LoginUser(); user.setId(id); user.setUsername("student" + id); user.setRole("STUDENT");
        return "Bearer " + jwt.generate(user);
    }
    @BeforeEach void setup() {
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "agent-http-test"), SysUser.class);
        token = token(7);
        SysUser db = new SysUser(); db.setId(7L); db.setStatus(1); db.setTokenVersion(0);
        when(users.selectOne(any())).thenReturn(db);
    }
    String chat(String message) throws Exception {
        MvcResult pending = mvc.perform(post("/api/agent/chat").header("Authorization", token)
                .contentType("application/json").content(json.writeValueAsString(Map.of("message", message, "ruleMode", true))))
                .andExpect(request().asyncStarted()).andReturn();
        pending.getAsyncResult(10000);
        return mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
    JsonNode event(String stream, String name) throws Exception {
        String frame = Arrays.stream(stream.split("\\r?\\n\\r?\\n")).filter(f -> f.startsWith("event:" + name + "\n") || f.startsWith("event:" + name + "\r\n")).findFirst().orElseThrow();
        return json.readTree(frame.substring(frame.indexOf("data:") + 5).trim());
    }
    @Test void jwtIsRequiredAndStatusNeverExposesKey() throws Exception {
        mvc.perform(get("/api/agent/status")).andExpect(jsonPath("$.code").value(401));
        mvc.perform(get("/api/agent/status").header("Authorization", token))
                .andExpect(jsonPath("$.data.configured").value(false)).andExpect(jsonPath("$.data.apiKey").doesNotExist());
    }
    @Test void ruleStreamCarriesUserIntoWorkerAndClearsRequestContext() throws Exception {
        when(reservations.myPage(1, 20, null)).thenAnswer(invocation -> {
            assertEquals(7L, UserContext.userId()); return PageResult.of(0, List.of());
        });
        String stream = chat("我的预约");
        assertTrue(stream.contains("event:done"));
        assertTrue(stream.contains("规则演示模式"));
        assertNull(UserContext.peek());
        verifyNoInteractions(llm);
    }
    @Test void invalidChatReturnsJsonInsteadOfBrokenSse() throws Exception {
        mvc.perform(post("/api/agent/chat").header("Authorization", token).contentType("application/json")
                .content("{\"message\":\"\"}"))
                .andExpect(jsonPath("$.msg").value("消息须为1到2000个字符"));
    }
    @Test void bookingDraftConfirmReplayAndCrossUserIsolationThroughHttp() throws Exception {
        Lab lab = new Lab(); lab.setId(1L); lab.setName("数据库实验室"); lab.setCode("LAB-1"); lab.setCapacity(30);
        when(labs.page(1, 20, "", null, 1)).thenReturn(PageResult.of(1, new ArrayList<>(List.of(lab))));
        when(reservations.checkCanReserve(any())).thenReturn(lab);
        String stream = chat("预约 明天 14:00-16:00 4人 用途：数据库实验");
        JsonNode card = event(stream, "action");
        String session = event(stream, "session").path("sessionId").asText();
        assertEquals("提交预约", card.path("details").path("操作").asText());
        verify(reservations, never()).create(any());
        String body = json.writeValueAsString(Map.of("sessionId", session, "actionId", card.path("id").asText()));
        mvc.perform(post("/api/agent/confirm").header("Authorization", token(8)).contentType("application/json").content(body))
                .andExpect(jsonPath("$.code").value(403));
        Reservation created = new Reservation(); created.setOrderNo("RS-HTTP"); created.setStatus("PENDING");
        when(reservations.create(any())).thenAnswer(invocation -> { assertEquals(7L, UserContext.userId()); return created; });
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/agent/confirm").header("Authorization", token).contentType("application/json").content(body))
                .andExpect(jsonPath("$.data.orderNo").value("RS-HTTP"));
        verify(reservations, times(1)).create(any());
    }
}
