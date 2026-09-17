package com.transfer.psbcx.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用接口返回体。
 */
public class TransferApiResponse extends HashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    public TransferApiResponse() {
        put("code", 0);
    }

    public static TransferApiResponse error() {
        return error(500, "未知异常，请联系管理员");
    }

    public static TransferApiResponse error(String message) {
        return error(500, message);
    }

    public static TransferApiResponse error(int code, String message) {
        TransferApiResponse body = new TransferApiResponse();
        body.put("code", code);
        body.put("msg", message);
        return body;
    }

    public static TransferApiResponse ok(String message) {
        TransferApiResponse body = new TransferApiResponse();
        body.put("msg", message);
        return body;
    }

    public static TransferApiResponse ok(Map<String, Object> values) {
        TransferApiResponse body = new TransferApiResponse();
        body.putAll(values);
        return body;
    }

    public static TransferApiResponse ok() {
        return new TransferApiResponse();
    }

    @Override
    public TransferApiResponse put(String key, Object value) {
        super.put(key, value);
        return this;
    }
}
