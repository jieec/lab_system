package com.lab.system.agent;

import com.lab.system.common.BizException;
import com.lab.system.security.UserContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/** Bounded, user-owned, ephemeral sessions. No credentials or database entities are persisted. */
@Component
public class AgentSessions {
    private final Map<String, Session> sessions = new HashMap<>();
    public static final long TTL_SECONDS = 1800;

    public static class Session {
        public final String id = UUID.randomUUID().toString();
        public final long owner = UserContext.userId();
        public volatile Instant touched = Instant.now();
        public final Deque<List<Map<String, Object>>> turns = new ArrayDeque<>();
        public final Map<String, Action> actions = new LinkedHashMap<>();
        public long lastChat;
        public volatile boolean busy;
    }

    public static class Action {
        public final String id = UUID.randomUUID().toString();
        public final String tool;
        public final Map<String, Object> args;
        public final Map<String, Object> display;
        public final Instant expires = Instant.now().plusSeconds(600);
        public boolean consumed;
        public Object result;
        public Action(String tool, Map<String, Object> args, Map<String, Object> display) {
            this.tool = tool;
            this.args = Map.copyOf(args);
            this.display = Map.copyOf(display);
        }
        public Map<String, Object> card() {
            return Map.of("id", id, "tool", tool, "details", display, "expiresAt", expires.toString());
        }
    }

    public synchronized Session get(String id) {
        Instant cutoff = Instant.now().minusSeconds(TTL_SECONDS);
        sessions.values().removeIf(s -> !s.busy && s.touched.isBefore(cutoff));
        if (id != null && !id.isBlank()) {
            Session s = sessions.get(id);
            if (s == null || s.owner != UserContext.userId()) throw new BizException(403, "会话不存在、已过期或不属于你，请新建对话");
            s.touched = Instant.now();
            return s;
        }
        if (sessions.size() >= 500 || sessions.values().stream().filter(s -> s.owner == UserContext.userId()).count() >= 5)
            throw new BizException(429, "会话数量已达上限，请清理旧对话或稍后重试");
        Session s = new Session();
        sessions.put(s.id, s);
        return s;
    }

    public synchronized void delete(String id) {
        Session s = get(id);
        if (s.busy) throw new BizException("对话正在处理中，请稍后清理");
        sessions.remove(s.id);
    }
}
