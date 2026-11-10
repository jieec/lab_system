package com.lab.system.aop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.system.entity.SysLog;
import com.lab.system.mapper.SysLogMapper;
import com.lab.system.security.OperationLog;
import com.lab.system.security.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;

/**
 * 审计日志切面：记录谁、在什么时候、对什么接口做了什么操作。
 * 日志写入使用自动提交，即使业务事务回滚也保留失败记录。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final Set<String> SENSITIVE = Set.of(
            "password", "oldpassword", "newpassword", "token", "secret", "apikey");

    private final SysLogMapper logMapper;
    private final ObjectMapper objectMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long begin = System.currentTimeMillis();
        SysLog record = baseRecord(joinPoint, operationLog);
        try {
            Object result = joinPoint.proceed();
            record.setSuccess(1);
            return result;
        } catch (Throwable e) {
            record.setSuccess(0);
            record.setErrorMsg(truncate(e.getMessage(), 500));
            throw e;
        } finally {
            record.setCostMs(System.currentTimeMillis() - begin);
            record.setCreateTime(LocalDateTime.now());
            try {
                logMapper.insert(record);
            } catch (Exception e) {
                log.warn("审计日志写入失败：{}", e.getMessage());
            }
        }
    }

    private SysLog baseRecord(ProceedingJoinPoint joinPoint, OperationLog operationLog) {
        SysLog record = new SysLog();
        record.setModule(operationLog.module());
        record.setAction(operationLog.value());
        record.setMethod(joinPoint.getSignature().getDeclaringTypeName() + "#"
                + joinPoint.getSignature().getName());
        try {
            if (UserContext.peek() != null) {
                record.setOperatorId(UserContext.userId());
                record.setOperatorName(UserContext.peek().getRealName());
            }
        } catch (Exception ignored) {
            // 未登录场景（如登录接口）留空
        }
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest req = attrs.getRequest();
                record.setUri(truncate(req.getRequestURI(), 200));
                record.setIp(clientIp(req));
            }
            record.setParams(safeParams(joinPoint.getArgs()));
        } catch (Exception e) {
            record.setParams("参数序列化失败");
        }
        return record;
    }

    /** 参数摘要：脱敏密码类字段，限制长度 */
    private String safeParams(Object[] args) {
        try {
            String json = objectMapper.writeValueAsString(Arrays.asList(args));
            for (String key : SENSITIVE) {
                json = json.replaceAll("(?i)\"" + key + "\"\\s*:\\s*\"[^\"]*\"",
                        "\"" + key + "\":\"***\"");
            }
            return truncate(json, 1000);
        } catch (Exception e) {
            return "参数序列化失败";
        }
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        return truncate(ip, 50);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
