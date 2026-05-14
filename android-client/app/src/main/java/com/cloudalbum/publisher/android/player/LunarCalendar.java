package com.cloudalbum.publisher.android.player;

import java.util.Calendar;

public class LunarCalendar {
    private static final String[] LUNAR_MONTHS = {
            "正月", "二月", "三月", "四月", "五月", "六月",
            "七月", "八月", "九月", "十月", "冬月", "腊月"
    };
    private static final String[] LUNAR_DAYS = {
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    };
    private static final String[] TIANGAN = {
            "甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"
    };
    private static final String[] DIZHI = {
            "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"
    };
    private static final String[] ANIMALS = {
            "鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"
    };

    // Lunar calendar data from 1900 to 2100
    // Each entry encodes: leap month flag, days in each month, and leap month days
    // Bits 0-11: 0=29 days, 1=30 days for months 1-12
    // Bits 12-15: leap month (0=none, 1-12=which month)
    // Bit 16: 0=29 days for leap month, 1=30 days
    private static final int[] LUNAR_INFO = {
            0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
            0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
            0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
            0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
            0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
            0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
            0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
            0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
            0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
            0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
            0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
            0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
            0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
            0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
            0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
            0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
            0x092e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
            0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
            0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
            0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a4d0, 0x0d150, 0x0f252, // 2090-2099
            0x0d520                                                                                      // 2100
    };

    private static final int LUNAR_YEAR_START = 1900;
    private static final long LUNAR_BASE_MS = -2208549897600L; // 1900-01-31 00:00:00 UTC

    public static String formatLunar(Calendar cal) {
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);

        int[] lunar = solarToLunar(year, month, day);
        int lunarYear = lunar[0];
        int lunarMonth = lunar[1];
        int lunarDay = lunar[2];
        boolean isLeap = lunar[3] == 1;

        String monthStr = (isLeap ? "闰" : "") + LUNAR_MONTHS[lunarMonth - 1];
        String dayStr = LUNAR_DAYS[lunarDay - 1];

        int ganIndex = (lunarYear - 4) % 10;
        int zhiIndex = (lunarYear - 4) % 12;
        String ganZhi = TIANGAN[ganIndex] + DIZHI[zhiIndex];
        String animal = ANIMALS[zhiIndex];

        return ganZhi + animal + "年 " + monthStr + dayStr;
    }

    private static int[] solarToLunar(int year, int month, int day) {
        int offset = daysBetweenSolar(1900, 1, 31, year, month, day);
        if (offset < 0) {
            return new int[] {1900, 1, 1, 0};
        }

        int lunarYear = LUNAR_YEAR_START;
        int daysInYear;
        for (int i = 0; i < LUNAR_INFO.length && offset > 0; i++) {
            daysInYear = daysInLunarYear(lunarYear);
            if (offset < daysInYear) break;
            offset -= daysInYear;
            lunarYear++;
        }

        int leapMonth = leapMonth(lunarYear);
        boolean isLeap = false;
        int lunarMonth = 1;
        int daysInMonth = 0;
        for (int i = 1; i <= 12 && offset > 0; i++) {
            if (leapMonth > 0 && i == leapMonth + 1 && !isLeap) {
                --i;
                isLeap = true;
                daysInMonth = leapDays(lunarYear);
            } else {
                daysInMonth = monthDays(lunarYear, i);
            }
            if (isLeap && i == leapMonth + 1) {
                isLeap = false;
            }
            offset -= daysInMonth;
            if (!isLeap) {
                lunarMonth++;
            }
        }

        if (offset == 0 && leapMonth > 0 && lunarMonth == leapMonth + 1) {
            if (isLeap) {
                isLeap = false;
            } else {
                isLeap = true;
                --lunarMonth;
            }
        }

        if (offset < 0) {
            offset += daysInMonth;
            --lunarMonth;
        }

        int lunarDay = offset + 1;

        return new int[] {lunarYear, lunarMonth, lunarDay, isLeap ? 1 : 0};
    }

    private static int daysBetweenSolar(int y1, int m1, int d1, int y2, int m2, int d2) {
        Calendar c1 = Calendar.getInstance();
        c1.set(y1, m1 - 1, d1, 0, 0, 0);
        c1.set(Calendar.MILLISECOND, 0);

        Calendar c2 = Calendar.getInstance();
        c2.set(y2, m2 - 1, d2, 0, 0, 0);
        c2.set(Calendar.MILLISECOND, 0);

        long diff = c2.getTimeInMillis() - c1.getTimeInMillis();
        return (int) (diff / (24L * 60 * 60 * 1000));
    }

    private static int daysInLunarYear(int year) {
        int sum = 348;
        int info = LUNAR_INFO[year - LUNAR_YEAR_START];
        for (int i = 0x8000; i > 0x8; i >>= 1) {
            sum += (info & i) != 0 ? 1 : 0;
        }
        return sum + leapDays(year);
    }

    private static int monthDays(int year, int month) {
        int bit = 1 << (16 - month);
        return (LUNAR_INFO[year - LUNAR_YEAR_START] & bit) != 0 ? 30 : 29;
    }

    private static int leapMonth(int year) {
        return LUNAR_INFO[year - LUNAR_YEAR_START] & 0xf;
    }

    private static int leapDays(int year) {
        if (leapMonth(year) != 0) {
            return (LUNAR_INFO[year - LUNAR_YEAR_START] & 0x10000) != 0 ? 30 : 29;
        }
        return 0;
    }
}
