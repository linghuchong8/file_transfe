package com.transfer.psbcx.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.transfer.psbcx.util.TransferTransferConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * company_three 客户端 HTTP 交互服务。
 *
 * 负责消息接收、文件下载/上传、消息回执与插件探测等 HTTP 调用。
 */
@Slf4j
public class TransferTransferHttpClientService {

    private static final String SUCCESS_CODE = "0000000000000000";
    private static final String CLIENT_PORT = ":9021";
    private static final int STREAM_BUFFER_SIZE = 512 * 1024;

    @Value("${downloadFiles.interface}")
    private String downloadInterface;
    @Value("${ackMessage.interface}")
    private String ackInterface;
    @Value("${uploadFiles.interface}")
    private String uploadInterface;
    @Value("${uploadFiles.stream.status}")
    private String uploadMode;
    @Value("#{'${edb.client-ip}'.split(',')}")
    private List<String> clientIps;

    private final List<String> ipList;

    public TransferTransferHttpClientService(String ipList) {
        this.ipList = Arrays.asList(ipList.split(","));
    }

    /**
     * 从配置的客户端地址中随机取一个。
     */
    public String randomClientIp() {
        if (clientIps == null || clientIps.isEmpty()) {
            return "";
        }
        return clientIps.get(new Random().nextInt(clientIps.size()));
    }

    /**
     * 返回本机对应的 EDB 客户端基地址。
     */
    public String nextClientUrl() {
        log.info("客户端地址列表: {}", clientIps == null ? "[]" : clientIps.toString());
        return "http://" + localIp() + CLIENT_PORT;
    }

    /**
     * 文件下载。
     *
     * @param jsonInputStr     请求参数
     * @param backupPluginPath 接收目录
     * @param flag             下载方式：0 文件路径
     * @param fileName         文件名
     * @return 是否成功
     */
    public boolean downloadFile(String jsonInputStr, String backupPluginPath, String flag, String fileName) {
        JSONObject response = null;
        int attempt = 0;
        while (attempt <= TransferTransferConstants.MAX_RETRIES) {
            String url = nextClientUrl() + downloadInterface;
            log.info("下载文件 {}, 地址 {} && {}", fileName, url, jsonInputStr);

            if ("0".equals(flag)) {
                try {
                    response = JSONObject.parseObject(postJson(url, jsonInputStr));
                    log.info("下载文件 {} 响应: {}", fileName, response);
                } catch (Exception e) {
                    log.error("下载接口异常, 文件 {}, 原因 {}", fileName, e.getMessage());
                }
            }
            if (response != null && !isSuccess(response)) {
                log.error("下载失败, 文件 {}, 地址 {}, 响应 {}", fileName, url, response.toJSONString());
            }
            if (response != null && isSuccess(response)) {
                log.info("下载成功, 文件 {}", fileName);
                return true;
            }

            attempt++;
            if (attempt <= TransferTransferConstants.MAX_RETRIES) {
                log.warn("地址不可用, 下载失败, 重试 {} 次中的第 {} 次", TransferTransferConstants.MAX_RETRIES, attempt);
            }
        }
        log.error("下载重试 {} 次仍失败, 文件 {}", TransferTransferConstants.MAX_RETRIES, fileName);
        return false;
    }

    /**
     * 文件上传。
     *
     * @param jsonInputStr 上传请求参数
     * @return 是否成功
     */
    public boolean uploadFile(String jsonInputStr) {
        String localIp = localIp();
        if ("".equals(localIp)) {
            log.error("获取本机地址失败, 上传参数 {}", jsonInputStr);
            return false;
        }

        JSONObject response = null;
        int attempt = 0;
        while (attempt <= TransferTransferConstants.MAX_RETRIES) {
            String url = nextClientUrl() + uploadInterface;
            if (!url.contains(localIp) && attempt == 0) {
                log.info("上传地址与本地不一致, 参数 {}, 地址 {}, 本地 {}", jsonInputStr, url, localIp);
                continue;
            }
            log.info("文件上传接口 {} && {}, 上传方式 {}", url, jsonInputStr, uploadMode);
            try {
                response = JSONObject.parseObject(postMultipart(url, jsonInputStr, uploadMode));
            } catch (Exception e) {
                log.error("上传接口异常: " + e.getMessage());
            }
            if (response != null && !isSuccess(response)) {
                log.error("上传失败, 接口 {} && {}, 响应 {}", url, jsonInputStr, response.toJSONString());
            }
            if (response != null && isSuccess(response)) {
                log.info("上传成功, 响应 {}", response);
                return true;
            }

            attempt++;
            if (attempt <= TransferTransferConstants.MAX_RETRIES) {
                log.warn("地址不可用, 上传失败, 重试 {} 次中的第 {} 次", TransferTransferConstants.MAX_RETRIES, attempt);
            }
        }
        log.error("上传重试 {} 次仍失败, 响应 {}", TransferTransferConstants.MAX_RETRIES, response);
        return false;
    }

    /**
     * 发送 multipart 上传请求。
     */
    public static String postMultipart(String url, String jsonStr, String uploadMode) {
        HttpPost httpPost = new HttpPost(url);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setCharset(StandardCharsets.UTF_8);
            builder.setMode(HttpMultipartMode.RFC6532);
            builder.addTextBody("uploadFileRequestParam", jsonStr, ContentType.APPLICATION_JSON);

            if ("2".equals(uploadMode)) {
                JsonObject jsonObject = new JsonParser().parse(jsonStr).getAsJsonObject();
                File file = new File(jsonObject.get("filePath").toString());
                builder.addBinaryBody("jarFile", file, ContentType.create("application/octet-stream"),
                        new String(file.getName().getBytes(), StandardCharsets.UTF_8));
            } else {
                builder.addBinaryBody("jarFile", "".getBytes());
            }
            builder.setContentType(ContentType.MULTIPART_FORM_DATA);
            httpPost.setEntity(builder.build());

            HttpResponse httpResponse = httpClient.execute(httpPost);
            String responseStr = EntityUtils.toString(httpResponse.getEntity());
            log.info("POST 请求响应 {}", responseStr);
            return responseStr;
        } catch (IOException e) {
            log.error("POST 请求失败, 原因 {}", e.getMessage());
        } finally {
            httpPost.releaseConnection();
        }
        return StringUtils.EMPTY;
    }

    /**
     * 发送 JSON POST 请求。
     */
    public static String postJson(String url, String jsonStr) {
        HttpPost httpPost = new HttpPost(url);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            httpPost.setHeader(TransferTransferConstants.HEADER_CONTENT_TYPE, TransferTransferConstants.JSON_CONTENT_TYPE);
            httpPost.setEntity(new StringEntity(jsonStr));

            HttpResponse httpResponse = httpClient.execute(httpPost);
            String body = EntityUtils.toString(httpResponse.getEntity());
            log.info("请求 {}, 响应 {}", url + jsonStr, body);

            JSONObject jsonObject = JSONObject.parseObject(body);
            log.info("响应 code: {}, message: {}",
                    jsonObject.getString("code"), jsonObject.getString("message"));
            return body;
        } catch (IOException e) {
            log.error("post 请求失败, 原因 {}", e.getMessage());
        } finally {
            httpPost.releaseConnection();
        }
        return StringUtils.EMPTY;
    }

    /**
     * 以文件流方式下载文件。
     *
     * @param url              请求地址
     * @param jsonInputStr     请求参数
     * @param backupPluginPath 接收目录
     * @return 成功时返回成功 JSON，失败返回空串或 null
     */
    public String postStream(String url, String jsonInputStr, String backupPluginPath) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(600000)
                .setSocketTimeout(36000000)
                .setConnectionRequestTimeout(30000)
                .build();

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        try {
            httpPost.setConfig(requestConfig);
            httpPost.setEntity(new StringEntity(jsonInputStr, StandardCharsets.UTF_8.name()));
            httpPost.setHeader(TransferTransferConstants.HEADER_CONTENT_TYPE, TransferTransferConstants.JSON_CONTENT_TYPE);

            HttpResponse httpResponse = httpClient.execute(httpPost);
            HttpEntity httpEntity = httpResponse.getEntity();
            String saveFileName = resolveSaveFileName(
                    httpResponse.getFirstHeader(TransferTransferConstants.HEADER_CONTENT_DISPOSITION));
            if (StringUtils.isEmpty(saveFileName)) {
                return null;
            }

            String outputFilePath = backupPluginPath + saveFileName;
            try (BufferedInputStream in = new BufferedInputStream(httpEntity.getContent());
                 OutputStream out = new FileOutputStream(outputFilePath)) {
                byte[] buffer = new byte[STREAM_BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            }
            log.info("{} 文件流保存成功, 路径 {}", saveFileName, outputFilePath);

            Map<String, String> result = new HashMap<>();
            result.put("code", SUCCESS_CODE);
            result.put("message", "成功");
            return JSON.toJSONString(result);
        } catch (IOException e) {
            log.info("post 请求发送失败");
        } finally {
            httpPost.releaseConnection();
        }
        return StringUtils.EMPTY;
    }

    private String resolveSaveFileName(Header contentDisposition) {
        if (contentDisposition == null) {
            return "";
        }
        for (String part : contentDisposition.getValue().split(";")) {
            if (part.trim().startsWith(TransferTransferConstants.DISPOSITION_FILENAME)) {
                String name = part.substring(part.indexOf('=') + 1).replace("\"", "");
                log.info("接收到流文件名: {}", name);
                return name;
            }
        }
        return "";
    }

    private boolean isSuccess(JSONObject response) {
        log.info("校验响应: {}", response);
        String code = (String) response.get("code");
        log.info("校验响应 code: {}", code);
        return SUCCESS_CODE.equals(code);
    }

    /**
     * 接收消息通知。
     */
    public JSONObject receiveMessages(String jsonStr, String interfaceName) {
        JSONObject response = null;
        int attempt = 0;
        while (attempt <= TransferTransferConstants.MAX_RETRIES) {
            String url = nextClientUrl() + interfaceName;
            log.info("消息接收地址: {}", url);
            log.info("消息接收请求体: {}", jsonStr);
            try {
                response = JSONObject.parseObject(postJson(url, jsonStr));
            } catch (Exception e) {
                log.error("消息接收失败: " + e.getMessage());
            }
            if (response != null && !isSuccess(response)) {
                log.error("消息接收失败, 地址 {} && {}, 响应 {}", url, jsonStr, response.toJSONString());
            }
            if (response != null && isSuccess(response)) {
                log.info("消息接收成功, 地址 {} && {}, 响应 {}", url, jsonStr, response.toJSONString());
                return response;
            }

            attempt++;
            if (attempt <= TransferTransferConstants.MAX_RETRIES) {
                log.warn("地址不可用, 消息接收失败, 重试 {} 次中的第 {} 次", TransferTransferConstants.MAX_RETRIES, attempt);
            }
        }
        log.error("消息接收重试 {} 次仍失败", TransferTransferConstants.MAX_RETRIES);
        return response;
    }

    /**
     * 消息回执。
     */
    public boolean ackMessage(String messageAckId, String ip) {
        int attempt = 0;
        while (attempt <= TransferTransferConstants.MAX_RETRIES) {
            String url = "http://" + ip + CLIENT_PORT + ackInterface;
            log.info("消息回执地址: {}", url);
            JSONObject response = JSONObject.parseObject(postJson(url, messageAckId));
            if (isSuccess(response)) {
                return true;
            }

            attempt++;
            if (attempt <= TransferTransferConstants.MAX_RETRIES) {
                log.warn("地址不可用, 消息回执失败, 重试 {} 次中的第 {} 次", TransferTransferConstants.MAX_RETRIES, attempt);
            }
        }
        log.error("消息回执重试 {} 次仍失败", attempt);
        return false;
    }

    /**
     * 获取本机 IP。
     */
    public static String localIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.error("获取本机地址失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 发送 GET 请求。
     *
     * @param url    请求地址
     * @param params 查询参数
     * @return 响应内容
     */
    public String sendGetRequest(String url, Map<String, String> params) {
        URI uri = buildUri(url, params);
        log.info("sendGet - {}", uri);
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpGet = new HttpGet(uri);
            httpGet.setConfig(RequestConfig.custom()
                    .setSocketTimeout(60 * 1000)
                    .setConnectTimeout(60 * 1000)
                    .build());
            try (CloseableHttpResponse httpResponse = httpClient.execute(httpGet)) {
                String body = EntityUtils.toString(httpResponse.getEntity());
                log.info("探测 {} 接口请求成功, 响应: {}", url, body);
                return body;
            }
        } catch (IOException e) {
            log.error("sendGetRequest 异常, 原因 {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private static URI buildUri(String url, Map<String, String> params) {
        try {
            StringBuilder builder = new StringBuilder(url);
            if (params != null && !params.isEmpty()) {
                builder.append("?");
                Set<Map.Entry<String, String>> entries = params.entrySet();
                for (Map.Entry<String, String> entry : entries) {
                    builder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }
                builder.setLength(builder.length() - 1);
            }
            return new URI(builder.toString());
        } catch (URISyntaxException e) {
            log.error("buildUri 异常, 原因 {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
