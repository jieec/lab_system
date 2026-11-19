package com.lab.system.controller;

import com.lab.system.agent.*;
import com.lab.system.common.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Tag(name = "智能助手", description = "业务工具调用、流式对话与人工确认")
public class AgentController {
    private final AgentService agent;
    private final AgentSessions sessions;
    private final AgentTools tools;

    @GetMapping("/status") public R<Map<String, Object>> status() { return R.ok(agent.status()); }

    @PostMapping("/chat")
    public SseEmitter chat(@RequestBody AgentService.ChatRequest request, HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return agent.chat(request);
    }

    public record Confirmation(String sessionId, String actionId) {}
    @com.lab.system.security.OperationLog(module = "智能助手", value = "确认助手业务操作")
    @PostMapping("/confirm") public R<Object> confirm(@RequestBody Confirmation request) {
        if (request.sessionId() == null || request.sessionId().isBlank() || request.actionId() == null)
            throw new com.lab.system.common.BizException("请提供有效会话和确认卡片");
        return R.ok(tools.confirm(sessions.get(request.sessionId()), request.actionId()));
    }
    @DeleteMapping("/sessions/{id}") public R<Void> delete(@PathVariable String id) { sessions.delete(id); return R.ok(); }
}
