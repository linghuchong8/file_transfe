package com.transfer.psbcx.util;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;

/**
 * config.properties 配置读取工具。
 */
public final class TransferAppPropertiesUtil {

    private static final Logger log = LoggerFactory.getLogger(TransferAppPropertiesUtil.class);
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties PROPERTIES = load();

    private TransferAppPropertiesUtil() {
    }

    private static Properties load() {
        Properties properties = new Properties();
        URL url = Resources.getResource(CONFIG_FILE);
        try {
            properties.load(Resources.asCharSource(url, Charsets.UTF_8).openBufferedStream());
        } catch (IOException e) {
            throw new RuntimeException("加载配置文件异常", e);
        }
        return properties;
    }

    public static String getProperty(String key) {
        return PROPERTIES.getProperty(key);
    }

    public static String getProperty(String key, String defaultVal) {
        String value = PROPERTIES.getProperty(key);
        return Strings.isNullOrEmpty(value) ? defaultVal : value;
    }

    public static Properties getProperties() {
        return PROPERTIES;
    }
}
