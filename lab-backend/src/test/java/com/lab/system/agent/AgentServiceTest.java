package com.lab.system.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.system.common.BizException;
import com.lab.system.security.UserContext;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentServiceTest {
    ObjectMapper json = new ObjectMapper();
    AgentProperties properties = new AgentProperties();
    AgentSessions sessions = new AgentSessions();
    LlmClient llm = mock(LlmClient.class);
    AgentTools tools = mock(AgentTools.class);
    AgentService service;
    AgentSessions.Session session;
    List<String> events = new ArrayList<>();
    @BeforeEach void setup() {
        AgentToolsTest.login(7); session = sessions.get(null);
        service = new AgentService(sessions, properties, llm, tools, new RuleFallback(json), json);
        when(tools.definitions()).thenReturn(List.of());
    }
    @AfterEach void cleanup() { service.close(); UserContext.clear(); }
    void run(String message, boolean rules) throws Exception {
        service.run(new AgentService.ChatRequest(session.id, message, rules), session, (event, data) -> events.add(event + ":" + data));
    }
    @Test void missingKeyUsesRealReadToolWithoutCallingModel() throws Exception {
        when(tools.execute(eq("query_my_reservations"), any(), eq(session))).thenReturn(List.of());
        run("我的预约", false);
        verifyNoInteractions(llm);
        assertTrue(events.stream().anyMatch(s -> s.contains("规则演示模式")));
        assertEquals(1, session.turns.size());
    }
    @Test void insufficientBalanceFallsBackAndNeverLeaksSecret() throws Exception {
        properties.setApiKey("test-secret-not-for-output");
        when(llm.complete(anyList(), anyList(), any())).thenThrow(new IOException("模型账户余额不足，请充值后重试"));
        when(tools.execute(eq("query_my_reservations"), any(), eq(session))).thenReturn(List.of());
        run("我的预约", false);
        assertTrue(events.stream().anyMatch(s -> s.contains("余额不足")));
        assertTrue(events.stream().noneMatch(s -> s.contains(properties.getApiKey())));
    }
    @Test void toolResultIsFedBackBeforeFinalAnswer() throws Exception {
        properties.setApiKey("test-key");
        var call = Map.of("id", "call-1", "type", "function", "function", Map.of("name", "query_my_reservations", "arguments", "{}"));
        when(llm.complete(anyList(), anyList(), any())).thenReturn(
                Map.of("role", "assistant", "content", "", "tool_calls", List.of(call)),
                Map.of("role", "assistant", "content", "没有预约"));
        when(tools.execute(anyString(), any(), eq(session))).thenReturn(List.of());
        run("我的预约", false);
        var turn = session.turns.getFirst();
        assertEquals("tool", turn.get(2).get("role"));
        assertEquals("call-1", turn.get(2).get("tool_call_id"));
        verify(llm, times(2)).complete(anyList(), anyList(), any());
    }
    @Test void modelCannotExecuteUnboundedToolLoops() throws Exception {
        properties.setApiKey("test-key"); properties.setMaxToolRounds(1);
        when(tools.execute(anyString(), any(), eq(session))).thenReturn(List.of());
        var call = Map.of("id", "loop", "function", Map.of("name", "query_my_reservations", "arguments", "{}"));
        when(llm.complete(anyList(), anyList(), any())).thenReturn(Map.of("role", "assistant", "content", "", "tool_calls", List.of(call)));
        run("我的预约", false);
        verify(tools, times(1)).execute(anyString(), any(), eq(session));
        assertTrue(session.turns.isEmpty());
        assertTrue(events.stream().anyMatch(s -> s.contains("调用上限")));
    }
    @Test void disconnectBeforeToolDispatchStopsBusinessAccess() throws Exception {
        assertThrows(AgentService.Disconnected.class, () -> service.run(
                new AgentService.ChatRequest(session.id, "我的预约", true), session,
                (event, data) -> { throw new AgentService.Disconnected(); }));
        verifyNoInteractions(llm);
        verify(tools, never()).execute(anyString(), any(), any());
    }
    @Test void ruleModeWillNotInterpretTextConfirmationAsWrite() throws Exception {
        run("确认，请直接执行数据库写入", true);
        verify(tools, never()).execute(anyString(), any(), any());
    }
    @Test void invalidLengthIsRejectedBeforeScheduling() {
        assertThrows(BizException.class, () -> service.chat(new AgentService.ChatRequest(null, "x".repeat(2001), false)));
    }
}
