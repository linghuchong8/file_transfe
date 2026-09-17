package com.transfer.medical.util;

import org.apache.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import java.util.Arrays;

/**
 * DES 加解密工具（ECB / NoPadding）。
 *
 * @author zhucl
 */
public class MedicalCryptoUtils {

    public static Logger log = Logger.getLogger(MedicalCryptoUtils.class);

    private static final String DEFAULT_HEX_KEY =
            "123456781234567812345678123456781234567812345678";
    private static final String ALGORITHM = "DES";
    private static final String MODE = "ECB";

    public MedicalCryptoUtils() {
    }

    public static String encryptForString(String sourceData) {
        return encryptForString(sourceData, DEFAULT_HEX_KEY);
    }

    public static String encryptForString(String sourceData, String hexKey) {
        byte[] key = hexToBytes(hexKey);
        byte[] data = sourceData.getBytes();
        return bytesToHex(encryptPadded(data, key));
    }

    public static String decryptToString(String hexData) {
        return decryptToString(hexData, DEFAULT_HEX_KEY).trim();
    }

    public static String decryptToString(String hexData, String hexKey) {
        byte[] data = hexToBytes(hexData);
        byte[] key = hexToBytes(hexKey);
        return new String(doDecrypt(data, key));
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length() / 2;
        byte[] result = new byte[len];
        char[] chars = hex.toCharArray();
        for (int i = 0; i < len; i++) {
            int pos = i * 2;
            result[i] = (byte) (hexCharToByte(chars[pos]) << 4 | hexCharToByte(chars[pos + 1]));
        }
        return result;
    }

    private static byte hexCharToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() < 2) {
                builder.append('0');
            }
            builder.append(hex.toUpperCase());
        }
        return builder.toString();
    }

    private static byte[] encryptPadded(byte[] data, byte[] key) {
        int padding = data.length % 8 == 0 ? 0 : 8 - data.length % 8;
        byte[] padded = new byte[data.length + padding];
        Arrays.fill(padded, (byte) 0);
        System.arraycopy(data, 0, padded, 0, data.length);
        return doEncrypt(padded, key);
    }

    private static byte[] doEncrypt(byte[] data, byte[] key) {
        try {
            validateKey(key);
            validateData(data);
            return cipher(key, Cipher.ENCRYPT_MODE).doFinal(data);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    private static byte[] doDecrypt(byte[] data, byte[] key) {
        try {
            validateKey(key);
            return cipher(key, Cipher.DECRYPT_MODE).doFinal(data);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    private static void validateKey(byte[] key) throws Exception {
        if (key == null || key.length % 8 != 0) {
            throw new Exception("参数key的长度必须是8位的整数倍！");
        }
    }

    private static void validateData(byte[] data) throws Exception {
        if (data == null || data.length % 8 != 0) {
            throw new Exception("参数data的长度必须是8位的整数倍！");
        }
    }

    private static Cipher cipher(byte[] key, int mode) throws Exception {
        DESKeySpec desKey = new DESKeySpec(key);
        SecretKey secretKey = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(desKey);
        Cipher cipher = Cipher.getInstance(ALGORITHM + "/" + MODE + "/NoPadding");
        cipher.init(mode, secretKey);
        return cipher;
    }
}
