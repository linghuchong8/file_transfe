package com.transfer.medical.util;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * EDB 客户端 HTTP 交互（早期实现，保留兼容）。
 *
 * @author xiehao
 */
public class MedicalHttpAction {

    private static final Logger log = LoggerFactory.getLogger(MedicalHttpAction.class);

    @Value("${downloadFiles.interface}")
    private String downloadInterface;

    @Value("${ackMessage.interface}")
    private String ackInterface;

    private final List<String> ipList;
    private int cursor;

    public MedicalHttpAction(String ipList) {
        this.ipList = Arrays.asList(ipList.split(","));
    }

    public String nextIp() {
        if (ipList.isEmpty()) {
            throw new IllegalStateException("IP列表为空");
        }
        String ip = ipList.get(cursor);
        cursor = cursor + 1 >= ipList.size() ? 0 : cursor + 1;
        return ip;
    }

    public boolean downloadFile(JSONObject jsonInput, String backupPluginPath) {
        int attempt = 0;
        while (attempt <= MedicalConstants.MAX_RETRIES) {
            String url = nextIp() + downloadInterface;
            log.info("文件下载地址: {}, 文件名: {}", url, jsonInput.getString("fileName"));
            String response = postStream(url, jsonInput.toJSONString(), backupPluginPath);
            if (StringUtils.isNotBlank(response)) {
                log.info("文件下载请求成功");
                return true;
            }
            attempt++;
            if (attempt <= MedicalConstants.MAX_RETRIES) {
                log.warn("地址不可用, 下载失败, 重试 {} 次中的第 {} 次", MedicalConstants.MAX_RETRIES, attempt);
            }
        }
        log.error("文件下载重试 {} 次仍失败", MedicalConstants.MAX_RETRIES);
        return false;
    }

    public String postStream(String url, String jsonInputStr, String backupPluginPath) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        try {
            httpPost.setEntity(new StringEntity(jsonInputStr, StandardCharsets.UTF_8.name()));
            httpPost.setHeader(MedicalConstants.HEADER_CONTENT_TYPE, MedicalConstants.JSON_CONTENT_TYPE);

            HttpResponse httpResponse = httpClient.execute(httpPost);
            HttpEntity httpEntity = httpResponse.getEntity();
            String saveFileName = resolveFileName(
                    httpResponse.getFirstHeader(MedicalConstants.HEADER_CONTENT_DISPOSITION));
            if (StringUtils.isEmpty(saveFileName)) {
                return null;
            }
            String outputFilePath = backupPluginPath + saveFileName;
            try (BufferedInputStream in = new BufferedInputStream(httpEntity.getContent());
                 OutputStream out = new FileOutputStream(outputFilePath)) {
                byte[] buffer = new byte[512 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            log.info("{} 文件流保存成功, 路径 {}", saveFileName, outputFilePath);
            return httpResponse.toString();
        } catch (IOException e) {
            log.info("post 请求发送失败");
        } finally {
            httpPost.releaseConnection();
        }
        return StringUtils.EMPTY;
    }

    private String resolveFileName(Header contentDisposition) {
        if (contentDisposition == null) {
            return "";
        }
        for (String part : contentDisposition.getValue().split(";")) {
            if (part.trim().startsWith(MedicalConstants.DISPOSITION_FILENAME)) {
                String name = part.substring(part.indexOf('=') + 1).replace("\"", "");
                log.info("接收到流文件名: {}", name);
                return name;
            }
        }
        return "";
    }

    public JSONObject autoReceiveMessage(String jsonStr, String interfaceName) {
        JSONObject response = null;
        int attempt = 0;
        while (attempt <= MedicalConstants.MAX_RETRIES) {
            String url = nextIp() + interfaceName;
            log.info("消息自动回执地址: {}", url);
            response = JSONObject.parseObject(postJson(url, jsonStr));
            if (isSuccess(response)) {
                return response;
            }
            attempt++;
            if (attempt <= MedicalConstants.MAX_RETRIES) {
                log.warn("地址不可用, 消息接收失败, 重试 {} 次中的第 {} 次", MedicalConstants.MAX_RETRIES, attempt);
            }
        }
        log.error("消息接收重试 {} 次仍失败", MedicalConstants.MAX_RETRIES);
        return response;
    }

    public static String postJson(String url, String jsonInputStr) {
        HttpPost httpPost = new HttpPost(url);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            httpPost.setEntity(new StringEntity(jsonInputStr, StandardCharsets.UTF_8.name()));
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, "application/json;charset=UTF-8");
            HttpResponse httpResponse = httpClient.execute(httpPost);
            String responseStr = EntityUtils.toString(httpResponse.getEntity());
            log.info("POST 请求响应: {}", responseStr);
            return responseStr;
        } catch (IOException e) {
            log.error("post 请求发送失败");
        } finally {
            httpPost.releaseConnection();
        }
        return StringUtils.EMPTY;
    }

    private boolean isSuccess(JSONObject response) {
        return "0000000000000000".equals(response.get("code"));
    }

    public boolean ackMessage(String messageAckId) {
        int attempt = 0;
        while (attempt <= MedicalConstants.MAX_RETRIES) {
            String url = nextIp() + ackInterface;
            log.info("消息回执地址: {}", url);
            if (isSuccess(JSONObject.parseObject(postJson(url, messageAckId)))) {
                return true;
            }
            attempt++;
            if (attempt <= MedicalConstants.MAX_RETRIES) {
                log.warn("地址不可用, 消息回执失败, 重试 {} 次中的第 {} 次", MedicalConstants.MAX_RETRIES, attempt);
            }
        }
        log.error("消息回执重试 {} 次仍失败", MedicalConstants.MAX_RETRIES);
        return false;
    }

    public static void main(String[] args) throws UnknownHostException {
        System.out.println(InetAddress.getLocalHost().getHostAddress());
    }
}
