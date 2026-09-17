package com.transfer.psbcx.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用字符串工具。
 */
@Slf4j
public class TransferStringHelper {

    private static final String TRIPLE_DES = "DESede";
    private static final String DEFAULT_TRIPLE_DES_KEY = "AD67EA2F3BE6E5ADD368DFE1";
    private static final Pattern WORKFLOW_PATTERN = Pattern.compile(
            "^((\\d{11})_(.+)_(\\d{4})_(\\d{8})_([ZAI])_(\\d{4})_(\\d{4}).(.+)");

    public static String getUUID() {
        String uuid = java.util.UUID.randomUUID().toString().replaceAll("-", "");
        return uuid.length() > 32 ? uuid.substring(0, 32) : uuid;
    }

    /**
     * 去掉字符串中的空格（含全角与半角）。
     */
    public static String lmtrim(String s) {
        if (s == null) {
            return null;
        }
        return s.replaceAll("　| ", "");
    }

    /**
     * 将路径统一为 Unix 风格并去掉末尾的 /。
     */
    public static String formatPath(String path) {
        if (path == null || "".equals(path)) {
            return "";
        }
        if (path.lastIndexOf('/') == path.length() - 1) {
            path = path.substring(0, path.length() - 1);
        }
        path = path.replace('\\', '/');
        return path.replaceAll("//", "/");
    }

    public static String getStandardDate(String str) {
        if (str == null || "".equals(str)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(str);
        while (builder.length() < 14) {
            builder.append('0');
        }
        return builder.toString();
    }

    /**
     * 按标准长度截取字符串，超出时追加省略号。
     */
    public static String getSubstring(String str, String isDate, int length) {
        if (str == null) {
            return null;
        }
        if (str.length() <= length) {
            return str;
        }
        return "y".equals(isDate) ? str.substring(0, length) : str.substring(0, length) + "...";
    }

    public static Date getUtilDateType(java.sql.Timestamp timestamp) {
        return timestamp;
    }

    public static String doEmpty(String src) {
        if (src == null || "null".equalsIgnoreCase(src)) {
            return "";
        }
        return String.valueOf(src);
    }

    public static String doEmpty(Object src) {
        if (src == null || "null".equalsIgnoreCase(String.valueOf(src))) {
            return "";
        }
        return String.valueOf(src);
    }

    public static String doEmpty(String src, String replace) {
        if (src == null || "null".equalsIgnoreCase(src) || src.length() < 1) {
            return replace;
        }
        return src;
    }

    public static String doEmpty(Object src, String replace) {
        if (src == null || "null".equalsIgnoreCase(String.valueOf(src))
                || String.valueOf(src).length() < 1) {
            return replace;
        }
        return String.valueOf(src);
    }

    public static String doEmptyAndTrim(String src) {
        if (src == null || "null".equalsIgnoreCase(src)) {
            return "";
        }
        return src.trim();
    }

    public static String doEmptyAndTrim(String src, String replace) {
        if (src == null || "null".equalsIgnoreCase(src) || src.length() < 1) {
            return replace;
        }
        return src.trim();
    }

    public static int toInt(String intStr) {
        return toInt(intStr, "0");
    }

    public static int toInt(String intStr, String replace) {
        String value = doEmpty(intStr, replace);
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            System.err.println("StringUtil.toInt(String intStr) failed. " + intStr + " can't parse to int.");
            return 0;
        }
    }

    public static int toInt(Object intStr) {
        if (intStr == null) {
            return 0;
        }
        try {
            return Integer.parseInt(doEmpty(intStr, "0"));
        } catch (Exception e) {
            System.err.println("StringUtil.toInt(Object intStr) failed. " + intStr + " can't parse to int.");
            return 0;
        }
    }

    public static int toInt(Object intStr, String replace) {
        if (intStr == null) {
            return 0;
        }
        try {
            return Integer.parseInt(doEmpty(intStr, replace));
        } catch (Exception e) {
            System.err.println("StringUtil.toInt(Object intStr) failed. " + intStr + " can't parse to int.");
            return 0;
        }
    }

    public static String getTextareaHTML(String src) {
        src = doEmpty(src);
        src = src.replaceAll("\\&", "&amp;");
        src = src.replaceAll("\\<", "&lt;");
        src = src.replaceAll("\\>", "&gt;");
        src = src.replaceAll("\\r\\n", "<br/>");
        src = src.replaceAll("\\n", "<br/>");
        return src.replaceAll(" ", "&nbsp;");
    }

    /**
     * 按工作流编码规则从文件名中截取指定分组。
     *
     * @param fileName 文件名
     * @param prefix   前缀
     * @param index    正则分组序号
     * @return 分组内容，不匹配时返回文件名
     */
    public static String getWorkflowCode(String fileName, String prefix, int index) {
        Matcher matcher = WORKFLOW_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            System.out.println("文件: " + fileName +
                    "名称格式不符合要求【NNNNNNNN_SSS_SS_ZZZZ_YYYYMMDD_C_XXXX_XXXX.XXX】!!!");
            return fileName;
        }
        String result = matcher.group(index);
        if (TransferStringHelper.doEmpty(prefix).length() > 0) {
            result = prefix + "_" + result;
        }
        return result;
    }

    public static String encrypt(String args, String key) {
        try {
            return encryptToBase64(key, args, "UTF-8");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    public static String decrypt(String args, String key) {
        try {
            return decryptFromBase64(key, args, "UTF-8");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    public static String encryptToBase64(String key, String src, String encoding) {
        try {
            BASE64Encoder encoder = new BASE64Encoder();
            return encoder.encode(encrypt(key.getBytes(), src.getBytes(encoding)));
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    public static String decryptFromBase64(String key, String src, String encoding) {
        try {
            BASE64Decoder decoder = new BASE64Decoder();
            return new String(decrypt(key.getBytes(), decoder.decodeBuffer(src)), encoding);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    public static byte[] encrypt(byte[] keybyte, byte[] src) {
        return doCipher(Cipher.ENCRYPT_MODE, keybyte, src);
    }

    public static byte[] decrypt(byte[] keybyte, byte[] src) {
        return doCipher(Cipher.DECRYPT_MODE, keybyte, src);
    }

    private static byte[] doCipher(int mode, byte[] keybyte, byte[] src) {
        try {
            SecretKey deskey = new SecretKeySpec(keybyte, TRIPLE_DES);
            Cipher cipher = Cipher.getInstance(TRIPLE_DES);
            cipher.init(mode, deskey);
            return cipher.doFinal(src);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    public static long getFileNum(String fileName) {
        String date = TransferStringHelper.getWorkflowCode(fileName, "", 4);
        date = date.substring(4);
        String pc = TransferStringHelper.getWorkflowCode(fileName, "", 6);
        String seq = TransferStringHelper.getWorkflowCode(fileName, "", 7);
        return Long.parseLong(date + pc + seq);
    }

    public static String addEndSeparator(String path) {
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return path;
    }

    public static String trimEndSeparator(String path) {
        if (path.endsWith("/")) {
            path = path.substring(0, path.lastIndexOf("/"));
        }
        return path;
    }

    public static String getPathByOS(String path) {
        String result;
        if (StringUtils.lowerCase(System.getProperty("os.name")).contains("windows")) {
            result = path.contains(":") ? path : "D:" + path;
        } else {
            result = path.contains(":") ? path.substring(path.indexOf(":") + 1) : path;
        }
        return result;
    }

    public static String getLocalHostAndName() {
        String serviceId = "";
        try {
            String canonicalHostName = InetAddress.getLocalHost().getCanonicalHostName();
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            serviceId = canonicalHostName + "-" + hostAddress;
        } catch (UnknownHostException e) {
            log.error("未知主机异常", e);
        }
        return serviceId;
    }
}
