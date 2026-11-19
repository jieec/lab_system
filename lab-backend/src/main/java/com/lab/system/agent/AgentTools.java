package com.lab.system.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.system.common.BizException;
import com.lab.system.dto.RepairCreateDTO;
import com.lab.system.dto.ReservationDTO;
import com.lab.system.entity.*;
import com.lab.system.security.UserContext;
import com.lab.system.service.*;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AgentTools {
    private final LabService labs;
    private final EquipmentService equipments;
    private final ReservationService reservations;
    private final RepairService repairs;
    private final StatsService stats;
    private final CreditService credit;
    private final ObjectMapper json;
    private final Validator validator;
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);

    public List<Map<String, Object>> definitions() {
        return List.of(
            tool("query_my_credit", "查询本人信用分、预约资格、奖惩规则与最近10条明细。", "", ""),
            tool("search_labs", "查询指定时段可预约实验室；会校验闭馆、信用、个人冲突和容量。最多20个候选。", "startTime:string,endTime:string,headcount:integer,keyword:string,equipmentKeyword:string", "startTime,endTime,headcount"),
            tool("recommend_slots", "在指定日期08:00到20:00间按半小时时段推荐最多3个方案；设备关键字为可选条件。", "date:string,durationMinutes:integer,headcount:integer,keyword:string,equipmentKeyword:string", "date,durationMinutes,headcount"),
            tool("query_my_reservations", "查询本人最近20条预约。", "", ""),
            tool("search_equipment", "按名称或资产编号查设备，最多20条。", "keyword:string", "keyword"),
            tool("query_my_repairs", "查询本人报修；维修员查询指派给自己的工单，最多20条。", "", ""),
            tool("dashboard_summary", "查询系统已有看板指标；学生与维修员仅返回本人相关统计。不要推测任意日期的周报。", "", ""),
            tool("prepare_reservation", "生成预约确认卡片，尚未提交；须先查真实实验室ID。时间格式 yyyy-MM-dd HH:mm:ss。", "labId:integer,startTime:string,endTime:string,headcount:integer,purpose:string", "labId,startTime,endTime,headcount,purpose"),
            tool("prepare_cancel", "生成取消本人预约的确认卡片，尚未取消。须先查询本人预约取得真实ID。", "reservationId:integer", "reservationId"),
            tool("prepare_fault", "生成报修确认卡片，尚未提交。须先查真实设备ID。紧急程度仅LOW/NORMAL/HIGH，HIGH表示紧急。", "equipmentId:integer,faultDesc:string,faultType:string,urgency:string", "equipmentId,faultDesc,faultType,urgency")
        );
    }

    private Map<String, Object> tool(String name, String description, String fields, String required) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (!fields.isEmpty()) for (String field : fields.split(",")) {
            String[] p = field.split(":");
            properties.put(p[0], Map.of("type", p[1]));
        }
        return Map.of("type", "function", "function", Map.of("name", name, "description", description,
            "parameters", Map.of("type", "object", "properties", properties, "additionalProperties", false,
                "required", required.isEmpty() ? List.of() : List.of(required.split(",")))));
    }

    public Object execute(String name, JsonNode args, AgentSessions.Session session) {
        Map<String, Object> definition = definitions().stream().map(d -> (Map<String, Object>) d.get("function"))
                .filter(d -> d.get("name").equals(name)).findFirst().orElseThrow(() -> new BizException("工具未注册"));
        Map<String, Object> schema = (Map<String, Object>) definition.get("parameters");
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (!args.isObject()) throw new BizException("工具参数必须是对象");
        args.fieldNames().forEachRemaining(k -> { if (!properties.containsKey(k)) throw new BizException("不允许的工具参数"); });
        for (String key : (List<String>) schema.get("required")) if (!args.hasNonNull(key)) throw new BizException("缺少参数：" + key);
        for (var entry : properties.entrySet()) {
            JsonNode value = args.get(entry.getKey());
            if (value == null) continue;
            String type = (String) ((Map<?, ?>) entry.getValue()).get("type");
            if (type.equals("integer") ? !value.isIntegralNumber() || !value.canConvertToInt() : !value.isTextual() || value.asText().length() > 1000)
                throw new BizException("参数类型或长度不正确：" + entry.getKey());
        }
        return switch (name) {
            case "query_my_credit" -> Map.of("summary",credit.summary(),"records",credit.myRecords(1,10));
            case "search_labs" -> available(args, time(args, "startTime"), time(args, "endTime"));
            case "recommend_slots" -> recommend(args);
            case "search_equipment" -> equipments.page(1, 20, text(args, "keyword"), null, null, null).getRecords().stream().map(this::equipmentView).toList();
            case "query_my_reservations" -> reservations.myPage(1, 20, null).getRecords().stream().map(this::reservationView).toList();
            case "query_my_repairs" -> repairs.page(1, 20, null, null, null, "REPAIRER".equals(UserContext.role()) ? "assigned" : "mine")
                    .getRecords().stream().map(r -> Map.of("orderNo", r.getOrderNo(), "equipmentId", r.getEquipmentId(),
                            "status", r.getStatus(), "faultDesc", Objects.toString(r.getFaultDesc(), ""))).toList();
            case "dashboard_summary" -> summary();
            case "prepare_reservation" -> prepareReservation(args, session);
            case "prepare_cancel" -> prepareCancel(args, session);
            case "prepare_fault" -> prepareFault(args, session);
            default -> throw new BizException("工具未注册");
        };
    }

    private Object summary() {
        if (Set.of("ADMIN", "TEACHER").contains(UserContext.role())) {
            var result = new LinkedHashMap<>(stats.dashboard());
            result.remove("repairRank");
            return result;
        }
        return Map.of("myReservationTotal", reservations.myPage(1, 1, null).getTotal(),
                "myReportedRepairTotal", repairs.page(1, 1, null, null, null, "mine").getTotal());
    }

    private List<Map<String, Object>> available(JsonNode args, LocalDateTime start, LocalDateTime end) {
        int count = positive(args, "headcount");
        if (start.isBefore(LocalDateTime.now()) || !end.isAfter(start) || !start.toLocalDate().equals(end.toLocalDate())
                || start.toLocalDate().isAfter(LocalDate.now().plusDays(30))) throw new BizException("请选择未来30天内同一天的有效时段");
        List<Map<String, Object>> result = new ArrayList<>();
        var candidates = labs.page(1, 20, text(args, "keyword"), null, 1).getRecords();
        candidates.sort(Comparator.comparing(l -> Objects.requireNonNullElse(l.getCapacity(), Integer.MAX_VALUE)));
        for (Lab lab : candidates) {
            if (lab.getCapacity() != null && lab.getCapacity() < count) continue;
            String need = text(args, "equipmentKeyword");
            if (!need.isBlank() && equipments.listByLab(lab.getId()).stream().noneMatch(e -> "IDLE".equals(e.getStatus()) &&
                    (Objects.toString(e.getName(), "") + Objects.toString(e.getCategory(), "")).contains(need))) continue;
            ReservationDTO dto = reservationDto(lab.getId(), start, end, count, "空闲查询");
            try { reservations.checkCanReserve(dto); }
            catch (BizException e) { continue; }
            result.add(Map.of("labId", lab.getId(), "name", lab.getName(), "code", lab.getCode(),
                    "capacity", Objects.requireNonNullElse(lab.getCapacity(), 0), "startTime", TIME.format(start),
                    "endTime", TIME.format(end), "reason", "满足人数、设备条件及当前预约规则；提交时再次校验"));
        }
        return result;
    }

    private Object recommend(JsonNode args) {
        LocalDate date = LocalDate.parse(text(args, "date"));
        int duration = positive(args, "durationMinutes");
        if (duration < 30 || duration > 480) throw new BizException("时长须为30到480分钟");
        if (date.isBefore(LocalDate.now()) || date.isAfter(LocalDate.now().plusDays(30))) throw new BizException("请选择未来30天内的日期");
        List<Map<String, Object>> result = new ArrayList<>();
        for (int minute = 8 * 60; minute + duration <= 20 * 60 && result.size() < 3; minute += 30) {
            LocalDateTime start = date.atStartOfDay().plusMinutes(minute);
            if (start.isBefore(LocalDateTime.now())) continue;
            for (var option : available(args, start, start.plusMinutes(duration))) {
                result.add(option);
                if (result.size() == 3) break;
            }
        }
        return result;
    }

    private Object prepareReservation(JsonNode args, AgentSessions.Session session) {
        ReservationDTO dto = reservationDto(positive(args, "labId"), time(args, "startTime"), time(args, "endTime"),
                positive(args, "headcount"), requiredText(args, "purpose", 200));
        valid(dto);
        Lab lab = reservations.checkCanReserve(dto);
        return action("prepare_reservation", args, session, Map.of("操作", "提交预约", "实验室", lab.getName() + "（" + lab.getCode() + "）",
                "开始时间", TIME.format(dto.getStartTime()), "结束时间", TIME.format(dto.getEndTime()), "人数", dto.getHeadcount(), "用途", dto.getPurpose()));
    }

    private Object prepareCancel(JsonNode args, AgentSessions.Session session) {
        Reservation r = ownedReservation((long) positive(args, "reservationId"));
        if (!Set.of("PENDING", "APPROVED").contains(r.getStatus()) || r.getStartTime().isBefore(LocalDateTime.now()))
            throw new BizException("该预约当前不可取消");
        return action("prepare_cancel", args, session, Map.of("操作", "取消本人预约", "预约单号", r.getOrderNo(),
                "实验室", Objects.toString(r.getLabName(), ""), "开始时间", TIME.format(r.getStartTime())));
    }

    private Object prepareFault(JsonNode args, AgentSessions.Session session) {
        RepairCreateDTO dto = repairDto(args);
        Equipment e = equipments.detail(dto.getEquipmentId());
        if ("SCRAPPED".equals(e.getStatus())) throw new BizException("该设备已报废，无法报修");
        return action("prepare_fault", args, session, Map.of("操作", "提交设备报修", "设备", e.getName() + "（" + e.getCode() + "）",
                "故障描述", dto.getFaultDesc(), "故障类型", dto.getFaultType(), "紧急程度", dto.getUrgency()));
    }

    private Object action(String tool, JsonNode args, AgentSessions.Session session, Map<String, Object> details) {
        session.actions.values().removeIf(a -> a.expires.isBefore(Instant.now()) || a.consumed);
        if (session.actions.size() >= 8) throw new BizException("待确认操作过多，请先确认或新建对话");
        var action = new AgentSessions.Action(tool, json.convertValue(args, new TypeReference<>() {}), details);
        session.actions.put(action.id, action);
        return Map.of("pendingAction", action.card(), "message", "尚未执行，请用户点击卡片确认；文字回复不能代替按钮确认");
    }

    /** Confirm only the server-stored arguments; the controller audits the session/action IDs. */
    public Object confirm(AgentSessions.Session session, String actionId) {
        synchronized (session) {
            if (session.owner != UserContext.userId()) throw new BizException(403, "无权确认此操作");
            if (session.busy) throw new BizException("请等待本轮对话结束后确认");
            var action = session.actions.get(actionId);
            if (action == null || action.expires.isBefore(Instant.now())) throw new BizException("确认卡片已过期，请重新生成");
            if (action.consumed) {
                if (action.result != null) return action.result;
                throw new BizException("该操作已处理，请查询业务页面确认结果，勿重复提交");
            }
            action.consumed = true;
            JsonNode args = json.valueToTree(action.args);
            Object result = switch (action.tool) {
                case "prepare_reservation" -> {
                    ReservationDTO dto = reservationDto(positive(args, "labId"), time(args, "startTime"), time(args, "endTime"), positive(args, "headcount"), requiredText(args, "purpose", 200));
                    valid(dto);
                    Reservation r = reservations.create(dto);
                    yield Map.of("message", "预约提交成功", "orderNo", r.getOrderNo(), "status", r.getStatus(), "path", "/my-reservations");
                }
                case "prepare_cancel" -> {
                    long id = positive(args, "reservationId");
                    ownedReservation(id);
                    reservations.cancel(id);
                    yield Map.of("message", "预约已取消", "path", "/my-reservations");
                }
                case "prepare_fault" -> {
                    RepairOrder r = repairs.create(repairDto(args));
                    yield Map.of("message", "报修提交成功", "orderNo", r.getOrderNo(), "path", "/repairs");
                }
                default -> throw new BizException("不支持的操作");
            };
            action.result = result;
            // Confirmation becomes factual context for the next model turn; never fabricate tool_call IDs.
            session.turns.add(List.of(Map.of("role", "user", "content", "已通过按钮确认操作，系统执行结果：" + result)));
            while (session.turns.size() > 6) session.turns.removeFirst();
            return result;
        }
    }

    private Reservation ownedReservation(long id) {
        Reservation r = reservations.detail(id);
        if (!UserContext.userId().equals(r.getUserId())) throw new BizException(403, "助手只能操作本人的预约");
        return r;
    }

    private RepairCreateDTO repairDto(JsonNode args) {
        RepairCreateDTO dto = new RepairCreateDTO();
        dto.setEquipmentId((long) positive(args, "equipmentId"));
        dto.setFaultDesc(requiredText(args, "faultDesc", 500));
        dto.setFaultType(requiredText(args, "faultType", 50));
        dto.setUrgency(requiredText(args, "urgency", 20));
        if (!Set.of("LOW", "NORMAL", "HIGH").contains(dto.getUrgency())) throw new BizException("紧急程度必须为LOW、NORMAL或HIGH");
        valid(dto);
        return dto;
    }
    private void valid(Object dto) {
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) throw new BizException(violations.iterator().next().getMessage());
    }
    static ReservationDTO reservationDto(long id, LocalDateTime start, LocalDateTime end, int count, String purpose) {
        ReservationDTO dto = new ReservationDTO();
        dto.setLabId(id); dto.setStartTime(start); dto.setEndTime(end); dto.setHeadcount(count); dto.setPurpose(purpose);
        return dto;
    }
    private Map<String, Object> equipmentView(Equipment e) {
        return Map.of("equipmentId", e.getId(), "name", e.getName(), "code", e.getCode(), "status", e.getStatus(), "labName", Objects.toString(e.getLabName(), ""));
    }
    private Map<String, Object> reservationView(Reservation r) {
        return Map.of("reservationId", r.getId(), "orderNo", r.getOrderNo(), "labName", Objects.toString(r.getLabName(), ""),
                "startTime", TIME.format(r.getStartTime()), "endTime", TIME.format(r.getEndTime()), "status", r.getStatus());
    }
    static LocalDateTime time(JsonNode args, String key) { return LocalDateTime.parse(requiredText(args, key, 19), TIME); }
    static String text(JsonNode args, String key) { return args.path(key).asText("").trim(); }
    static String requiredText(JsonNode args, String key, int max) {
        String value = text(args, key);
        if (value.isBlank() || value.length() > max) throw new BizException("请补充有效的" + key);
        return value;
    }
    static int positive(JsonNode args, String key) {
        JsonNode value = args.path(key);
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 1) throw new BizException("请补充有效的" + key);
        return value.asInt();
    }
}
