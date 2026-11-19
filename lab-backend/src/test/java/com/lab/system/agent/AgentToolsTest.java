package com.lab.system.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lab.system.common.*;
import com.lab.system.dto.ReservationDTO;
import com.lab.system.entity.*;
import com.lab.system.security.*;
import com.lab.system.service.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentToolsTest {
    final ObjectMapper json = new ObjectMapper();
    final LabService labs = mock(LabService.class);
    final EquipmentService equipments = mock(EquipmentService.class);
    final ReservationService reservations = mock(ReservationService.class);
    final RepairService repairs = mock(RepairService.class);
    final StatsService stats = mock(StatsService.class);
    ValidatorFactory factory;
    AgentTools tools;
    AgentSessions sessions;
    AgentSessions.Session session;

    static void login(long id) {
        LoginUser user = new LoginUser(); user.setId(id); user.setRole("STUDENT"); UserContext.set(user);
    }
    @BeforeEach void setup() {
        login(7);
        factory = Validation.buildDefaultValidatorFactory();
        tools = new AgentTools(labs, equipments, reservations, repairs, stats, mock(CreditService.class), json, factory.getValidator());
        sessions = new AgentSessions(); session = sessions.get(null);
    }
    @AfterEach void cleanup() { factory.close(); UserContext.clear(); }
    ObjectNode args() {
        LocalDate day = LocalDate.now().plusDays(1);
        return json.createObjectNode().put("labId", 1).put("startTime", day + " 14:00:00")
                .put("endTime", day + " 16:00:00").put("headcount", 4).put("purpose", "数据库实验");
    }
    String prepare() {
        Lab lab = new Lab(); lab.setId(1L); lab.setName("数据库实验室"); lab.setCode("LAB-A301");
        when(reservations.checkCanReserve(any())).thenReturn(lab);
        Map<?, ?> result = (Map<?, ?>) tools.execute("prepare_reservation", args(), session);
        return (String) ((Map<?, ?>) result.get("pendingAction")).get("id");
    }
    @Test void draftNeverWritesUntilConfirmedAndReplayDoesNotDuplicate() {
        String id = prepare();
        verify(reservations, never()).create(any());
        Reservation created = new Reservation(); created.setOrderNo("RS-1"); created.setStatus("PENDING");
        when(reservations.create(any())).thenReturn(created);
        Object result = tools.confirm(session, id);
        assertEquals(result, tools.confirm(session, id));
        verify(reservations, times(1)).create(any(ReservationDTO.class));
    }
    @Test void failedConfirmationCannotBeBlindlyReplayed() {
        String id = prepare();
        when(reservations.create(any())).thenThrow(new BizException("时段已被占用"));
        assertThrows(BizException.class, () -> tools.confirm(session, id));
        assertThrows(BizException.class, () -> tools.confirm(session, id));
        verify(reservations, times(1)).create(any());
    }
    @Test void anotherUserCannotReadSessionOrConfirmAction() {
        String id = prepare(); login(8);
        assertThrows(BizException.class, () -> sessions.get(session.id));
        assertThrows(BizException.class, () -> tools.confirm(session, id));
        verify(reservations, never()).create(any());
    }
    @Test void expiredSessionIsRemoved() {
        session.touched = Instant.now().minusSeconds(AgentSessions.TTL_SECONDS + 1);
        assertThrows(BizException.class, () -> sessions.get(session.id));
    }
    @Test void sessionsAreBoundedPerUser() {
        for (int i = 0; i < 4; i++) sessions.get(null);
        assertThrows(BizException.class, () -> sessions.get(null));
    }
    @Test void promptInjectionCannotRegisterArbitraryToolsOrUserIds() {
        assertThrows(BizException.class, () -> tools.execute("execute_sql", json.createObjectNode(), session));
        assertThrows(BizException.class, () -> tools.execute("prepare_reservation", args().put("userId", 8), session));
        assertThrows(BizException.class, () -> tools.execute("prepare_reservation", args().put("headcount", "4"), session));
        verifyNoInteractions(reservations);
    }
    @Test void cannotCancelAnotherPersonsReservationEvenAsAdmin() {
        UserContext.get().setRole("ADMIN");
        Reservation other = new Reservation(); other.setUserId(8L);
        when(reservations.detail(42L)).thenReturn(other);
        assertThrows(BizException.class, () -> tools.execute("prepare_cancel", json.createObjectNode().put("reservationId", 42), session));
        verify(reservations, never()).cancel(anyLong());
    }
    @Test void confirmationRechecksOwnershipOfCancellation() {
        Reservation r = new Reservation(); r.setUserId(7L); r.setOrderNo("RS-42"); r.setStatus("PENDING");
        r.setStartTime(LocalDateTime.now().plusDays(1));
        when(reservations.detail(42L)).thenReturn(r);
        tools.execute("prepare_cancel", json.createObjectNode().put("reservationId", 42), session);
        r.setUserId(8L);
        String id = session.actions.keySet().iterator().next();
        assertThrows(BizException.class, () -> tools.confirm(session, id));
        verify(reservations, never()).cancel(anyLong());
    }
    @Test void repairScopeCannotBeOverridden() {
        when(repairs.page(1, 20, null, null, null, "mine")).thenReturn(PageResult.of(0, List.of()));
        tools.execute("query_my_repairs", json.createObjectNode(), session);
        verify(repairs).page(1, 20, null, null, null, "mine");
        assertThrows(BizException.class, () -> tools.execute("query_my_repairs", json.createObjectNode().put("scope", "all"), session));
    }
    @Test void studentsCannotExportGlobalDashboard() {
        when(reservations.myPage(1, 1, null)).thenReturn(PageResult.of(3, List.of()));
        when(repairs.page(1, 1, null, null, null, "mine")).thenReturn(PageResult.of(2, List.of()));
        Map<?, ?> result = (Map<?, ?>) tools.execute("dashboard_summary", json.createObjectNode(), session);
        assertEquals(3L, result.get("myReservationTotal"));
        verifyNoInteractions(stats);
    }
    @Test void busySessionCannotBeConfirmedOrDeleted() {
        String id = prepare(); session.busy = true;
        assertThrows(BizException.class, () -> tools.confirm(session, id));
        assertThrows(BizException.class, () -> sessions.delete(session.id));
    }
}
