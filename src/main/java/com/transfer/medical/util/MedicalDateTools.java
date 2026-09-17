package com.transfer.medical.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 日期处理工具。
 */
public class MedicalDateTools {

    private static final String DEFAULT_DATE_PATTERN = "yyyy-MM-dd";
    private static final String COMPACT_DATE_PATTERN = "yyyyMMdd";

    public static String getFormatDate(Date date) {
        try {
            return new SimpleDateFormat(DEFAULT_DATE_PATTERN).format(date);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 返回当前时间，格式 yyyy-MM-dd HH:mm:ss。
     */
    public static String getCurrentDateString() {
        return getFormatDate("yyyy-MM-dd HH:mm:ss");
    }

    public static String getFormatDate(String pattern) {
        try {
            return new SimpleDateFormat(pattern).format(new Date());
        } catch (Exception e) {
            return "";
        }
    }

    public static String getFormatDate(String source, String pattern) {
        try {
            Date date = new SimpleDateFormat(DEFAULT_DATE_PATTERN).parse(source);
            return new SimpleDateFormat(pattern).format(date);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getFormatDate(Date source, String pattern) {
        try {
            return new SimpleDateFormat(pattern).format(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getFormatDate(String source, String sourcePattern, String pattern) {
        try {
            Date date = new SimpleDateFormat(sourcePattern).parse(source);
            return new SimpleDateFormat(pattern).format(date);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取指定日期次日零点，格式 yyyy-MM-dd 24:00:00。
     */
    public static String getFormatDateEnd(String source, String sourcePattern, String pattern) {
        try {
            Date date = new SimpleDateFormat(sourcePattern).parse(source);
            date = new Date(date.getTime() + (24 * 60 * 60) * 1000);
            return new SimpleDateFormat(pattern).format(date);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date getDateFromStr(String source, String sourcePattern) {
        try {
            return new SimpleDateFormat(sourcePattern).parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date getDateFromStr(String source) {
        try {
            return new SimpleDateFormat(DEFAULT_DATE_PATTERN).parse(source);
        } catch (Exception e) {
            return null;
        }
    }

    public static Date getDateOffset(Date date, int offset) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DATE, offset);
        return calendar.getTime();
    }

    public static Date getHourOffset(Date date, int offset) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.HOUR_OF_DAY, offset);
        return calendar.getTime();
    }

    public static Date getMinutesOffset(Date date, int offset) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, offset);
        return calendar.getTime();
    }

    public static int getYear(String date) throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new SimpleDateFormat(COMPACT_DATE_PATTERN).parse(date));
        return calendar.get(Calendar.YEAR);
    }

    public static int getMonth(String date) throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new SimpleDateFormat(COMPACT_DATE_PATTERN).parse(date));
        return calendar.get(Calendar.MONTH) + 1;
    }

    public static int getDay(String date) throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new SimpleDateFormat(COMPACT_DATE_PATTERN).parse(date));
        return calendar.get(Calendar.DAY_OF_MONTH);
    }

    public static String getMinMonthDate(String date) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat(COMPACT_DATE_PATTERN);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(format.parse(date));
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMinimum(Calendar.DAY_OF_MONTH));
        return format.format(calendar.getTime());
    }

    public static String getMaxMonthDate() throws Exception {
        SimpleDateFormat format = new SimpleDateFormat(COMPACT_DATE_PATTERN);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        return format.format(calendar.getTime());
    }

    public static String getMaxMonthDate(String date) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat(COMPACT_DATE_PATTERN);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(format.parse(date));
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        return format.format(calendar.getTime());
    }
}
