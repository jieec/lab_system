package com.lab.system.common;

import lombok.Data;

/**
 * 统一响应结构
 */
@Data
public class R<T> {

    private int code;
    private String msg;
    private T data;

    public static <T> R<T> ok() {
        return build(200, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return build(200, "操作成功", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return build(200, msg, data);
    }

    public static <T> R<T> fail(String msg) {
        return build(500, msg, null);
    }

    public static <T> R<T> fail(int code, String msg) {
        return build(code, msg, null);
    }

    private static <T> R<T> build(int code, String msg, T data) {
        R<T> r = new R<>();
        r.code = code;
        r.msg = msg;
        r.data = data;
        return r;
    }
}
