package com.massdata.util;

import java.util.Map;

/**
 * 统一响应结果
 */
public class R {
    private int code;
    private String message;
    private Object data;

    public static R ok(Object data) {
        R r = new R();
        r.code = 200;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static R ok() {
        return ok(null);
    }

    public static R ok(String message, Object data) {
        R r = new R();
        r.code = 200;
        r.message = message;
        r.data = data;
        return r;
    }

    public static R error(int code, String message) {
        R r = new R();
        r.code = code;
        r.message = message;
        return r;
    }

    public static R error(String message) {
        return error(500, message);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
