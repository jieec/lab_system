package com.lab.system.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lab.system.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.regex.*;
import java.util.function.BiFunction;

/** Explicit deterministic demo mode, with deliberately limited grammar. Never pretends to be an LLM. */
@Component
@RequiredArgsConstructor
public class RuleFallback {
    private final ObjectMapper json;

    public String reply(String input, BiFunction<String, ObjectNode, Object> call) {
        if(input.contains("信用")||input.contains("信誉"))return "你的信用情况与规则：\n"+pretty(call.apply("query_my_credit",json.createObjectNode()));
        if (input.contains("我的预约")) return "以下是最近20条本人预约：\n" + pretty(call.apply("query_my_reservations", json.createObjectNode()));
        if (input.contains("我的报修") || input.contains("我的工单")) return "以下是当前身份可查询的工单：\n" + pretty(call.apply("query_my_repairs", json.createObjectNode()));
        if (input.contains("统计") || input.contains("看板")) return "以下为系统实际统计（非任意日期周报）：\n" + pretty(call.apply("dashboard_summary", json.createObjectNode()));
        Matcher cancel = Pattern.compile("取消.*?(\\d+)").matcher(input);
        if (cancel.find()) {
            call.apply("prepare_cancel", json.createObjectNode().put("reservationId", Integer.parseInt(cancel.group(1))));
            return "已生成取消预约确认卡片，点击确认后才会取消。";
        }
        if (input.startsWith("查设备") || input.startsWith("查询设备")) {
            String keyword = input.replaceFirst("^查询?设备[：:\\s]*", "");
            return "设备查询结果：\n" + pretty(call.apply("search_equipment", json.createObjectNode().put("keyword", keyword)));
        }
        if (input.contains("报修")) {
            Matcher fault = Pattern.compile("报修\\s+(\\d+)\\s+(.+)", Pattern.DOTALL).matcher(input);
            if (!fault.find()) return "规则模式请先输入“查设备 显微镜”，取得设备ID后输入“报修 设备ID 故障描述”。例如：报修 3 灯不亮。";
            String description = fault.group(2).trim();
            boolean urgent = description.matches("(?s).*(烟|糊|漏电|火花|焦味).*");
            call.apply("prepare_fault", json.createObjectNode().put("equipmentId", Integer.parseInt(fault.group(1)))
                    .put("faultDesc", description).put("faultType", "其他").put("urgency", urgent ? "HIGH" : "NORMAL"));
            return (urgent ? "发现危险征兆，请停止使用并联系实验室负责人。\n" : "") + "已生成报修确认卡片，点击确认后提交。";
        }
        if (input.contains("预约") || input.contains("空闲") || input.contains("推荐") || input.contains("帮我约")) {
            LocalDate date = date(input);
            Matcher people = Pattern.compile("(\\d+)\\s*(?:个)?人").matcher(input);
            if (date == null || !people.find()) return "规则模式请明确日期、时段和人数，例如：预约 明天 14:00-16:00 4人 用途：数据库实验。";
            int count = Integer.parseInt(people.group(1));
            ObjectNode args = json.createObjectNode().put("headcount", count);
            Matcher keyword = Pattern.compile("实验室[：:]([^\\s]+)").matcher(input);
            if (keyword.find()) args.put("keyword", keyword.group(1));
            if (input.contains("推荐")) {
                Matcher duration = Pattern.compile("(\\d+)\\s*分钟").matcher(input);
                if (!duration.find()) return "规则模式推荐示例：推荐 明天 4人 120分钟。可补充 实验室：数据库。";
                args.put("date", date.toString()).put("durationMinutes", Integer.parseInt(duration.group(1)));
                return "按时间优先、容量适配推荐（最多20间实验室候选）：\n" + pretty(call.apply("recommend_slots", args));
            }
            List<LocalTime> times = times(input);
            if (times.size() != 2) return "请使用明确时段，例如：预约 明天 14:00-16:00 4人 用途：数据库实验。";
            args.put("startTime", AgentTools.TIME.format(date.atTime(times.get(0))));
            args.put("endTime", AgentTools.TIME.format(date.atTime(times.get(1))));
            Object options = call.apply("search_labs", args);
            List<?> list = (List<?>) options;
            if (list.isEmpty()) return "在本次最多20间候选实验室中没有满足当前预约规则的方案。可能涉及闭馆、冲突、人数或信用限制，请调整条件或前往预约页面查看。";
            if (input.contains("空闲") || input.startsWith("查")) return "当前满足条件的实验室：\n" + pretty(options);
            Matcher purpose = Pattern.compile("(?:用途[：:]|做)(.+)").matcher(input);
            if (!purpose.find()) return "找到可用实验室，请在同一句中补充用途再生成卡片，例如：预约 明天 14:00-16:00 4人 用途：数据库实验。\n" + pretty(options);
            Map<?, ?> first = (Map<?, ?>) list.get(0);
            ObjectNode prepare = args.deepCopy();
            prepare.remove("keyword");
            prepare.put("labId", ((Number) first.get("labId")).intValue()).put("purpose", purpose.group(1).trim());
            call.apply("prepare_reservation", prepare);
            return "已选取本次候选中容量适配的一间实验室，生成待确认卡片。请核对实验室与时间后点击确认；尚未提交预约。";
        }
        return "当前为规则演示模式，支持：\n• 我的预约\n• 取消预约 预约ID\n• 预约 明天 14:00-16:00 4人 用途：数据库实验\n• 推荐 明天 4人 120分钟\n• 查设备 显微镜\n• 报修 设备ID 灯不亮\n• 我的报修\n• 数据统计\n更自由的多轮表达需要配置有可用余额的模型API。";
    }

    private String pretty(Object value) {
        try { return json.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch (Exception e) { throw new BizException("结果序列化失败"); }
    }
    private LocalDate date(String text) {
        if (text.contains("大后天")) return LocalDate.now().plusDays(3);
        if (text.contains("后天")) return LocalDate.now().plusDays(2);
        if (text.contains("明天")) return LocalDate.now().plusDays(1);
        if (text.contains("今天")) return LocalDate.now();
        Matcher m = Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(text);
        return m.find() ? LocalDate.parse(m.group()) : null;
    }
    private List<LocalTime> times(String text) {
        List<LocalTime> result = new ArrayList<>();
        Matcher m = Pattern.compile("(?<!\\d)(\\d{1,2})[:：点](\\d{2}|半)?").matcher(text);
        while (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            if ((text.contains("下午") || text.contains("晚上")) && hour < 12) hour += 12;
            int minute = m.group(2) == null ? 0 : m.group(2).equals("半") ? 30 : Integer.parseInt(m.group(2));
            result.add(LocalTime.of(hour, minute));
        }
        return result;
    }
}
