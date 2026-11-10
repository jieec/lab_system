package com.lab.system.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValid(BindException e) {
        FieldError error = e.getBindingResult().getFieldError();
        String msg = error == null ? "参数校验失败" : error.getDefaultMessage();
        return R.fail(400, msg);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegal(IllegalArgumentException e) {
        return R.fail(400, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return R.fail("系统繁忙，请稍后再试");
    }
}
