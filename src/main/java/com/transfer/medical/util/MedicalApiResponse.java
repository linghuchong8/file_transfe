package com.transfer.medical.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用接口返回体。
 */
public class MedicalApiResponse extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    public MedicalApiResponse() {
        put("code", 0);
    }

    public static MedicalApiResponse error() {
        return error(500, "未知异常，请联系管理员");
    }

    public static MedicalApiResponse error(String message) {
        return error(500, message);
    }

    public static MedicalApiResponse error(int code, String message) {
        MedicalApiResponse body = new MedicalApiResponse();
        body.put("code", code);
        body.put("msg", message);
        return body;
    }

    public static MedicalApiResponse ok(String message) {
        MedicalApiResponse body = new MedicalApiResponse();
        body.put("msg", message);
        return body;
    }

    public static MedicalApiResponse ok(Map<String, Object> values) {
        MedicalApiResponse body = new MedicalApiResponse();
        body.putAll(values);
        return body;
    }

    public static MedicalApiResponse ok() {
        return new MedicalApiResponse();
    }

    @Override
    public MedicalApiResponse put(String key, Object value) {
        super.put(key, value);
        return this;
    }
}
