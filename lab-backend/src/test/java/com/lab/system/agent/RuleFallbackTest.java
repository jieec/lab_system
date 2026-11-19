package com.lab.system.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RuleFallbackTest {
    RuleFallback fallback = new RuleFallback(new ObjectMapper());
    @Test void explicitBookingOnlyPreparesAConfirmationCard() {
        List<String> calls = new ArrayList<>();
        String answer = fallback.reply("预约 明天 14:00-16:00 4人 用途：数据库实验", (name, args) -> {
            calls.add(name);
            assertEquals(4, args.path("headcount").asInt());
            if (name.equals("search_labs")) return List.of(Map.of("labId", 3));
            assertEquals(3, args.path("labId").asInt());
            assertEquals("数据库实验", args.path("purpose").asText());
            return Map.of();
        });
        assertEquals(List.of("search_labs", "prepare_reservation"), calls);
        assertTrue(answer.contains("尚未提交"));
    }
    @Test void missingPeopleOrPurposeNeverCreatesDraft() {
        fallback.reply("预约 明天 14:00-16:00", (name, args) -> fail("不应调用工具"));
        fallback.reply("预约 明天 14:00-16:00 4人", (name, args) -> {
            assertEquals("search_labs", name); return List.of(Map.of("labId", 1));
        });
    }
    @Test void riskyFaultIsMarkedUrgent() {
        String answer = fallback.reply("报修 3 灯不亮还有糊味", (name, args) -> {
            assertEquals("prepare_fault", name);
            assertEquals("HIGH", args.path("urgency").asText()); return Map.of();
        });
        assertTrue(answer.contains("停止使用"));
    }
}
