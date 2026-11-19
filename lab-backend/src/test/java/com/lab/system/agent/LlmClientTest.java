package com.lab.system.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LlmClientTest {
    AgentProperties properties = new AgentProperties();
    ObjectMapper json = new ObjectMapper();
    HttpURLConnection connection = mock(HttpURLConnection.class);
    ByteArrayOutputStream request = new ByteArrayOutputStream();
    LlmClient client;
    @BeforeEach void setup() throws Exception {
        properties.setApiKey("fake-key-for-test");
        client = spy(new LlmClient(properties, json));
        doReturn(connection).when(client).openConnection(any(URI.class));
        when(connection.getOutputStream()).thenReturn(request);
        when(connection.getResponseCode()).thenReturn(200);
    }
    void stream(String text) throws Exception {
        when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }
    String frame(Map<String, Object> delta) throws Exception {
        return "data: " + json.writeValueAsString(Map.of("choices", List.of(Map.of("delta", delta)))) + "\n\n";
    }
    @Test void streamsChineseTextAndAssemblesFragmentedToolArguments() throws Exception {
        stream(": heartbeat\n\n" + frame(Map.of("content", "正在查询"))
                + frame(Map.of("tool_calls", List.of(Map.of("index", 0, "id", "call-1", "function", Map.of("name", "search_", "arguments", "{\"keyword\":")))))
                + frame(Map.of("tool_calls", List.of(Map.of("index", 0, "function", Map.of("name", "equipment", "arguments", "\"显微镜\"}")))))
                + "data: [DONE]\n\n");
        List<String> tokens = new ArrayList<>();
        var response = client.complete(List.of(Map.of("role", "user", "content", "查设备")), List.of(), tokens::add);
        assertEquals(List.of("正在查询"), tokens);
        var call = json.valueToTree(response).path("tool_calls").get(0);
        assertEquals("search_equipment", call.path("function").path("name").asText());
        assertEquals("显微镜", json.readTree(call.path("function").path("arguments").asText()).path("keyword").asText());
        var sent = json.readTree(request.toByteArray());
        assertTrue(sent.path("stream").asBoolean());
        assertEquals("disabled", sent.path("thinking").path("type").asText());
        verify(connection).setInstanceFollowRedirects(false);
        verify(connection).disconnect();
    }
    @Test void balanceErrorIsSanitized() throws Exception {
        when(connection.getResponseCode()).thenReturn(402);
        IOException error = assertThrows(IOException.class, () -> client.complete(List.of(), List.of(), s -> {}));
        assertTrue(error.getMessage().contains("余额不足"));
        assertFalse(error.getMessage().contains(properties.getApiKey()));
        verify(connection, never()).getErrorStream();
        verify(connection).disconnect();
    }
    @Test void truncatedStreamIsNotAcceptedAsSuccessful() throws Exception {
        stream(frame(Map.of("content", "部分回答")));
        assertThrows(IOException.class, () -> client.complete(List.of(), List.of(), s -> {}));
    }
    @Test void forbidsPlainHttpBeforeSendingCredentials() {
        properties.setBaseUrl("http://example.test");
        assertThrows(IOException.class, () -> client.complete(List.of(), List.of(), s -> {}));
        verifyNoInteractions(connection);
    }
}
