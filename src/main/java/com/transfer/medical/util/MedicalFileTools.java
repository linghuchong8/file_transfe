package com.transfer.medical.util;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * 文件格式转换与压缩处理工具。
 */
public class MedicalFileTools {

    private static final Logger log = LoggerFactory.getLogger(MedicalFileTools.class);

    /** 批量落盘阈值。 */
    private static final int BATCH_SIZE = 10000;

    /** 行结束标记。 */
    private static final String ROW_END = "|-|";

    /** 字段分隔标记。 */
    private static final String FIELD_SEPARATOR = "|+|";

    /** 转义后的字段分隔符。 */
    private static final char FIELD_SEPARATOR_CHAR = '\u0001';

    /**
     * 将行内用 |+| 分隔的 xml 文本转换为 txt。
     *
     * @param xmlPath xml 全路径
     * @param txtPath txt 全路径
     * @param encode  指定字符集，空则自动探测
     */
    public static void xml2txt(final String xmlPath, final String txtPath, String encode) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(xmlPath), "%s 不能为空", "xmlPath");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(txtPath), "%s 不能为空", "txtPath");

        final File xmlFile = new File(xmlPath);
        if (!xmlFile.exists()) {
            throw new FileNotFoundException(xmlPath);
        }
        final File txtFile = resetTarget(txtPath);

        LineIterator iterator = FileUtils.lineIterator(xmlFile,
                MedicalFileEncodingDetector.getJavaEncode(xmlPath, encode));
        List<String> buffer = Lists.newArrayList();
        int pending = 0;
        while (iterator.hasNext()) {
            String line = iterator.next().trim();
            if (line.contains(ROW_END) && line.endsWith(ROW_END)) {
                String converted = line.substring(0, line.lastIndexOf(ROW_END));
                converted = StringUtils.replace(converted, FIELD_SEPARATOR, String.valueOf(FIELD_SEPARATOR_CHAR));
                buffer.add(converted);
                pending++;
            }
            if (pending >= BATCH_SIZE) {
                FileUtils.writeLines(txtFile, buffer, true);
                pending = 0;
                buffer.clear();
            }
        }
        FileUtils.writeLines(txtFile, buffer, true);
    }

    /**
     * 只保留 xml 行中的指定字段后转 txt。
     *
     * @param xmlPath  xml 全路径
     * @param txtPath  txt 全路径
     * @param encode   指定字符集，空则自动探测
     * @param specFile 逗号分隔的字段下标
     */
    public static void xmlSpectxt(final String xmlPath, final String txtPath,
                                  String encode, String specFile) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(xmlPath), "%s 不能为空", "xmlPath");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(txtPath), "%s 不能为空", "txtPath");

        final File xmlFile = new File(xmlPath);
        if (!xmlFile.exists()) {
            throw new FileNotFoundException(xmlPath);
        }
        final File txtFile = resetTarget(txtPath);

        String[] fieldIndexes = specFile.split(",");
        LineIterator iterator = FileUtils.lineIterator(xmlFile,
                MedicalFileEncodingDetector.getJavaEncode(xmlPath, encode));
        List<String> buffer = Lists.newArrayList();
        int pending = 0;
        while (iterator.hasNext()) {
            String line = iterator.next().trim();
            String content = null;
            if (line.contains(ROW_END) && line.endsWith(ROW_END)) {
                content = line.substring(0, line.lastIndexOf(ROW_END));
                content = pickFields(content.split("\\|\\+\\|", -1), fieldIndexes);
            } else if (line.indexOf('|') != line.lastIndexOf('|') && line.endsWith("|")) {
                content = pickFields(line.split("\\|"), fieldIndexes);
            }
            if (content != null) {
                buffer.add(content);
                pending++;
            }
            if (pending >= BATCH_SIZE) {
                FileUtils.writeLines(txtFile, buffer, true);
                pending = 0;
                buffer.clear();
            }
        }
        FileUtils.writeLines(txtFile, buffer, true);
    }

    /**
     * 按字段下标拼接内容，字段之间用 \u0001 分隔。
     */
    private static String pickFields(String[] fields, String[] fieldIndexes) {
        StringBuilder builder = new StringBuilder();
        for (String index : fieldIndexes) {
            builder.append(fields[Integer.parseInt(index)]).append(FIELD_SEPARATOR_CHAR);
        }
        builder.setLength(builder.lastIndexOf(String.valueOf(FIELD_SEPARATOR_CHAR)));
        return builder.toString();
    }

    /**
     * 将 dat 文件转换为 txt。
     */
    public static void dat2txt(final String datPath, final String txtPath) throws IOException {
        log.info("开始由 dat 转换成 txt");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(datPath), "dat源路径不能为空");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(txtPath), "txt目标路径不能为空");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(Files.getFileExtension(datPath)), "dat源路径不合法");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(Files.getFileExtension(txtPath)), "dat源路径不合法");

        final File datFile = new File(datPath);
        if (!datFile.exists()) {
            throw new FileNotFoundException("dat文件不存在");
        }
        String content = FileUtils.readFileToString(datFile, "GBK");
        content = StringUtils.replace(content, FIELD_SEPARATOR, String.valueOf(FIELD_SEPARATOR_CHAR))
                .replaceAll("\\r", "")
                .replaceAll("\\n", "<br>")
                .replaceAll("|+" + "<br>", "\n")
                .replaceAll("|+|", "\n")
                .replaceAll("\\n\\n", "\n");
        if (content.endsWith("<br>")) {
            content = content.substring(0, content.lastIndexOf("<br>"));
        }
        FileUtils.write(new File(txtPath), content);
    }

    public static void decompress(InputStream is, OutputStream os) throws IOException {
        final int bufferSize = 1024;
        GZIPInputStream gzip = new GZIPInputStream(is);
        int count;
        byte[] buffer = new byte[bufferSize];
        while ((count = gzip.read(buffer, 0, bufferSize)) != -1) {
            os.write(buffer, 0, count);
        }
        gzip.close();
    }

    public static void decompress(final String inputPath, final String outputPath,
                                  final boolean isDelete) throws IOException {
        try (InputStream in = new FileInputStream(inputPath);
             OutputStream out = new FileOutputStream(outputPath)) {
            decompress(in, out);
        }
        if (isDelete && new File(inputPath).delete()) {
            log.info("解压文件【{}】删除成功", inputPath);
        } else if (isDelete) {
            log.info("解压文件【{}】删除失败", inputPath);
        }
    }

    /**
     * 医保结算系统 xml 转 txt，兼容两种行格式。
     */
    public static void xml2txtForFinance(final String xmlPath, final String txtPath,
                                         String encode) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(xmlPath), "%s 不能为空", "xmlPath");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(txtPath), "%s 不能为空", "txtPath");

        final File xmlFile = new File(xmlPath);
        if (!xmlFile.exists()) {
            throw new FileNotFoundException(xmlPath);
        }
        final File txtFile = resetTarget(txtPath);

        LineIterator iterator = FileUtils.lineIterator(xmlFile,
                MedicalFileEncodingDetector.getJavaEncode(xmlPath, encode));
        List<String> buffer = Lists.newArrayList();
        int pending = 0;
        while (iterator.hasNext()) {
            String line = iterator.next().trim();
            if (line.contains(ROW_END) && line.endsWith(ROW_END)) {
                String converted = line.substring(0, line.lastIndexOf(ROW_END));
                buffer.add(StringUtils.replace(converted, FIELD_SEPARATOR,
                        String.valueOf(FIELD_SEPARATOR_CHAR)));
                pending++;
            } else if (line.indexOf('|') != line.lastIndexOf('|') && line.endsWith("|")) {
                buffer.add(StringUtils.replace(line, "|", String.valueOf(FIELD_SEPARATOR_CHAR)));
                pending++;
            }
            if (pending >= BATCH_SIZE) {
                FileUtils.writeLines(txtFile, buffer, true);
                pending = 0;
                buffer.clear();
            }
        }
        FileUtils.writeLines(txtFile, buffer, true);
    }

    /**
     * csv 转 txt，跳过表头。
     */
    public static void csv2txt(final String csvPath, final String txtPath,
                               String encode) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(csvPath), "%s 不能为空", "csvPath");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(txtPath), "%s 不能为空", "txtPath");

        final File csvFile = new File(csvPath);
        if (!csvFile.exists()) {
            throw new FileNotFoundException(csvPath);
        }
        final File txtFile = resetTarget(txtPath);

        LineIterator iterator = FileUtils.lineIterator(csvFile,
                MedicalFileEncodingDetector.getJavaEncode(csvPath, encode));
        List<String> buffer = Lists.newArrayList();
        int pending = 0;
        boolean header = true;
        while (iterator.hasNext()) {
            String line = iterator.next().trim();
            if (header) {
                header = false;
                continue;
            }
            if (line.contains(ROW_END) && line.endsWith(ROW_END)) {
                String converted = line.substring(0, line.lastIndexOf(ROW_END));
                converted = StringUtils.replace(converted, "\"", "");
                converted = StringUtils.replace(converted, FIELD_SEPARATOR,
                        String.valueOf(FIELD_SEPARATOR_CHAR));
                buffer.add(converted);
                pending++;
            }
            if (pending >= BATCH_SIZE) {
                FileUtils.writeLines(txtFile, buffer, true);
                pending = 0;
                buffer.clear();
            }
        }
        FileUtils.writeLines(txtFile, buffer, true);
    }

    /**
     * 准备目标文件：不存在则建父目录，存在则先清空。
     */
    private static File resetTarget(String targetPath) throws IOException {
        File target = new File(targetPath);
        if (!target.exists()) {
            Files.createParentDirs(target);
        } else {
            FileUtils.forceDelete(target);
        }
        return target;
    }
}
