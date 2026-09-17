package com.transfer.medical.util;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 文件编码探测与读写工具。
 *
 * 说明：原文件共 4874 行，其中包含大段 GBFreq/GBKFreq/Big5Freq/Big5PFreq/EUC_TWFreq/KRFreq/JPFreq
 * 频率表（照片中为重复生成代码，已折叠）。此处保留可见的公共方法与编码常量表，
 * MedicalByteEncodingDetector 的概率算法（gb2312_probability 等）需对照原工程补全。
 */
@Slf4j
public class MedicalFileEncodingDetector {

    public static String getJavaEncode(String filePath) {
        return getJavaEncode(filePath, "");
    }

    /**
     * 探测文件编码，并支持按配置映射强制指定。
     *
     * @param filePath 文件路径
     * @param encode   指定编码，非空时优先
     * @return Java 编码名
     */
    public static String getJavaEncode(String filePath, String encode) {        MedicalByteEncodingDetector probe = new MedicalByteEncodingDetector();
        MedicalByteEncodingDetector resolver = new MedicalByteEncodingDetector();
        String fileCode = resolver.javaname[probe.detect(new File(filePath))];

        String mappingJson = MedicalAppPropertiesUtil.getProperty("interface.filepath.filecode.map");
        if ("".equals(encode)) {
            Map<String, String> mapping = new Gson().fromJson(mappingJson, HashMap.class);
            for (String key : mapping.keySet()) {
                if (filePath.contains(key)) {
                    fileCode = mapping.get(key);
                    break;
                }
            }
        } else {
            fileCode = encode;
        }
        log.info("文件: " + filePath + " MedicalFileEncodingDetector>>>>>>>>>>>>>>>>>>>>>>>>>>" + fileCode);
        return fileCode;
    }

    public static void writeFile(String path, String content, String charSet) {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path), charSet)) {
            writer.write(content);
            writer.flush();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }

    /**
     * 读取文件内容，可选是否压缩空白行。
     */
    public static String readFile(String file, boolean... isTrims) {
        StringBuilder buffer = new StringBuilder();
        String encode = "";
        String code = getJavaEncode(file, encode);
        boolean trim = isTrims.length > 0 && isTrims[0];
        String lineSeparator = System.getProperty("line.separator");
        String charset = code != null && !"".equals(code) ? code : "UTF-8";

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset))) {
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                if (!trim) {
                    if (lineNo != 1) {
                        buffer.append(lineSeparator);
                    }
                    lineNo++;
                }
                Object value = trim ? line.isEmpty() : line;
                buffer.append(value);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return buffer.toString();
    }
}

/**
 * 字节流编码探测器。
 */
class MedicalByteEncodingDetector extends MedicalEncodingTable {

    int[][] gbFreq;
    int[][] gbkFreq;
    int[][] big5Freq;
    int[][] big5PFreq;
    int[][] eucTwFreq;
    int[][] krFreq;
    int[][] jpFreq;
    private boolean debug;

    public MedicalByteEncodingDetector() {
        super();
        debug = false;
        gbFreq = new int[94][94];
        gbkFreq = new int[126][191];
        big5Freq = new int[94][158];
        big5PFreq = new int[126][191];
        eucTwFreq = new int[94][94];
        krFreq = new int[94][94];
        jpFreq = new int[94][94];
        initFrequencies();
    }

    public static void main(String[] options) {
        MedicalByteEncodingDetector detector = new MedicalByteEncodingDetector();
        for (String option : options) {
            int result = OTHER;
            if (option.startsWith("http://")) {
                try {
                    result = detector.detect(new URL(option));
                } catch (Exception e) {
                    System.err.println("Bad URL " + e);
                }
            } else if ("-d".equals(option)) {
                detector.debug = true;
                continue;
            } else {
                result = detector.detect(new File(option));
            }
            System.out.println(detector.nicename[result]);
        }
    }

    public int detect(URL testUrl) {
        byte[] rawtext = new byte[10000];
        int bytesRead = 0;
        int offset = 0;
        int guess = OTHER;
        try (InputStream stream = testUrl.openStream()) {
            while ((bytesRead = stream.read(rawtext, offset, rawtext.length - offset)) > 0) {
                offset += bytesRead;
            }
            guess = detect(rawtext);
        } catch (Exception e) {
            System.err.println("Error loading or using URL " + e);
            guess = -1;
        }
        return guess;
    }

    public int detect(File testFile) {
        byte[] rawtext = new byte[(int) testFile.length()];
        try (FileInputStream input = new FileInputStream(testFile)) {
            input.read(rawtext);
        } catch (Exception e) {
            System.err.println("Error: " + e);
        }
        return detect(rawtext);
    }

    public int detect(byte[] rawtext) {
        int[] scores = new int[TOTALTYPES];
        scores[GB2312] = gb2312_probability(rawtext);
        scores[GBK] = gbk_probability(rawtext);
        scores[GB18030] = gb18030_probability(rawtext);
        scores[HZ] = hz_probability(rawtext);
        scores[BIG5] = big5_probability(rawtext);
        scores[CNS11643] = euc_tw_probability(rawtext);
        scores[ISO2022CN] = iso_2022_cn_probability(rawtext);
        scores[EUC_KR] = euc_kr__probability(rawtext);
        scores[CP949] = cp949__probability(rawtext);
        scores[JOHAB] = 0;
        scores[ISO2022KR] = iso_2022_kr_probability(rawtext);
        scores[ASCII] = ascii_probability(rawtext);
        scores[SJIS] = sjis_probability(rawtext);
        scores[EUC_JP] = euc_jp_probability(rawtext);
        scores[ISO2022JP] = iso_2022_jp_probability(rawtext);
        scores[UNICODE] = 0;
        scores[UNICODES] = 0;
        scores[ISO2022CN_GB] = 0;
        scores[ISO2022CN_CNS] = 0;
        scores[OTHER] = 0;

        int guess = OTHER;
        int maxScore = 0;
        for (int index = 0; index < TOTALTYPES; index++) {
            if (debug) {
                System.err.println("Encoding " + nicename[index] + " score " + scores[index]);
            }
            if (scores[index] > maxScore) {
                guess = index;
                maxScore = scores[index];
            }
        }
        if (maxScore <= 50) {
            guess = OTHER;
        }
        return guess;
    }

    // ... 各编码概率算法（gb2312_probability/gbk_probability/gb18030_probability/hz_probability/
    //     big5_probability/euc_tw_probability/iso_2022_cn_probability/utf8_probability/
    //     utf16_probability/ascii_probability/euc_kr__probability/cp949__probability/
    //     iso_2022_kr_probability/euc_jp_probability/iso_2022_jp_probability/sjis_probability）
    //     及其频率表初始化（约 3800 行）未在照片中完整收录，需对照原工程补全。

    void initFrequencies() {
        for (int i = 0; i < 94; i++) {
            for (int j = 0; j < 94; j++) {
                gbFreq[i][j] = 0;
            }
        }
        for (int i = 0; i < 126; i++) {
            for (int j = 0; j < 191; j++) {
                gbkFreq[i][j] = 0;
            }
        }
        for (int i = 0; i < 94; i++) {
            for (int j = 0; j < 158; j++) {
                big5Freq[i][j] = 0;
            }
        }
        for (int i = 0; i < 126; i++) {
            for (int j = 0; j < 191; j++) {
                big5PFreq[i][j] = 0;
            }
        }
        for (int i = 0; i < 94; i++) {
            for (int j = 0; j < 94; j++) {
                eucTwFreq[i][j] = 0;
            }
        }
        for (int i = 0; i < 94; i++) {
            for (int j = 0; j < 94; j++) {
                jpFreq[i][j] = 0;
            }
        }
        gbFreq[20][35] = 599;
        gbFreq[49][26] = 598;
        gbFreq[41][38] = 597;
        gbFreq[17][26] = 596;
        gbFreq[32][42] = 595;
        gbFreq[39][42] = 594;
        gbFreq[45][49] = 593;
        // ... 其余频率表（gbFreq/gbkFreq/big5Freq/big5PFreq/eucTwFreq/krFreq/jpFreq）
        //     约 line 1009-4722，未在照片中完整收录，需对照原工程补全。
    }
}

/**
 * 编码类型常量表。
 */
class MedicalEncodingTable {

    public static final int GB2312 = 0;
    public static final int GBK = 1;
    public static final int GB18030 = 2;
    public static final int HZ = 3;
    public static final int BIG5 = 4;
    public static final int CNS11643 = 5;
    public static final int UTF8 = 6;
    public static final int UTF8T = 7;
    public static final int UTF8S = 8;
    public static final int UNICODE = 9;
    public static final int UNICODET = 10;
    public static final int UNICODES = 11;
    public static final int ISO2022CN = 12;
    public static final int ISO2022CN_CNS = 13;
    public static final int ISO2022CN_GB = 14;
    public static final int EUC_KR = 15;
    public static final int CP949 = 16;
    public static final int ISO2022KR = 17;
    public static final int JOHAB = 18;
    public static final int SJIS = 19;
    public static final int EUC_JP = 20;
    public static final int ISO2022JP = 21;
    public static final int ASCII = 22;
    public static final int OTHER = 23;
    public static final int TOTALTYPES = 24;
    public static final int SIMP = 0;
    public static final int TRAD = 1;

    /** Java 可识别的编码名。 */
    String[] javaname;

    /** 便于阅读的编码名。 */
    String[] nicename;

    /** HTML meta 中使用的编码名。 */
    String[] htmlname;

    public MedicalEncodingTable() {
        javaname = new String[TOTALTYPES];
        nicename = new String[TOTALTYPES];
        htmlname = new String[TOTALTYPES];

        javaname[GB2312] = "GB2312";
        javaname[GBK] = "GBK";
        javaname[GB18030] = "GB18030";
        javaname[HZ] = "ASCII";
        javaname[ISO2022CN_GB] = "ISO2022CN_GB";
        javaname[BIG5] = "BIG5";
        javaname[CNS11643] = "EUC-TW";
        javaname[ISO2022CN_CNS] = "ISO2022CN_CNS";
        javaname[ISO2022CN] = "ISO2022CN";
        javaname[UTF8] = "UTF-8";
        javaname[UTF8T] = "UTF-8";
        javaname[UTF8S] = "UTF-8";
        javaname[UNICODE] = "Unicode";
        javaname[UNICODET] = "Unicode";
        javaname[UNICODES] = "Unicode";
        javaname[EUC_KR] = "EUC_KR";
        javaname[CP949] = "MS949";
        javaname[ISO2022KR] = "ISO2022KR";
        javaname[JOHAB] = "Johab";
        javaname[SJIS] = "SJIS";
        javaname[EUC_JP] = "EUC_JP";
        javaname[ISO2022JP] = "ISO2022JP";
        javaname[ASCII] = "ASCII";
        javaname[OTHER] = "ISO8859_1";

        htmlname[GB2312] = "GB2312";
        htmlname[GBK] = "GBK";
        htmlname[GB18030] = "GB18030";
        htmlname[HZ] = "HZ-GB-2312";
        htmlname[ISO2022CN_GB] = "ISO-2022-CN-EXT";
        htmlname[BIG5] = "BIG5";
        htmlname[CNS11643] = "EUC-TW";
        htmlname[ISO2022CN_CNS] = "ISO-2022-CN-EXT";
        htmlname[ISO2022CN] = "ISO-2022-CN";
        htmlname[UTF8] = "UTF-8";
        htmlname[UTF8T] = "UTF-8";
        htmlname[UTF8S] = "UTF-8";
        htmlname[UNICODE] = "UTF-16";
        htmlname[UNICODET] = "UTF-16";
        htmlname[UNICODES] = "UTF-16";
        htmlname[EUC_KR] = "EUC-KR";
        htmlname[CP949] = "x-windows-949";
        htmlname[ISO2022KR] = "ISO-2022-KR";
        htmlname[JOHAB] = "x-Johab";
        htmlname[SJIS] = "Shift_JIS";
        htmlname[EUC_JP] = "EUC-JP";
        htmlname[ISO2022JP] = "ISO-2022-JP";
        htmlname[ASCII] = "ASCII";
        htmlname[OTHER] = "ISO8859-1";

        nicename[GB2312] = "GB-2312";
        nicename[GBK] = "GBK";
        nicename[GB18030] = "GB18030";
        nicename[HZ] = "HZ";
        nicename[ISO2022CN_GB] = "ISO2022CN-GB";
        nicename[BIG5] = "Big5";
        nicename[CNS11643] = "CNS11643";
        nicename[ISO2022CN_CNS] = "ISO2022CN-CNS";
        nicename[ISO2022CN] = "ISO2022 CN";
        nicename[UTF8] = "UTF-8";
        nicename[UTF8T] = "UTF-8 (Trad)";
        nicename[UTF8S] = "UTF-8 (Simp)";
        nicename[UNICODE] = "Unicode";
        nicename[UNICODET] = "Unicode (Trad)";
        nicename[UNICODES] = "Unicode (Simp)";
        nicename[EUC_KR] = "EUC-KR";
        nicename[CP949] = "CP949";
        nicename[ISO2022KR] = "ISO 2022 KR";
        nicename[JOHAB] = "Johab";
        nicename[SJIS] = "Shift-JIS";
        nicename[EUC_JP] = "EUC-JP";
        nicename[ISO2022JP] = "ISO 2022 JP";
        nicename[ASCII] = "ASCII";
        nicename[OTHER] = "OTHER";
    }
}
