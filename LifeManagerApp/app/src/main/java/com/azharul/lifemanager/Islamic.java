package com.azharul.lifemanager;

import java.util.Calendar;

/** তারিখ (বাংলা/ইংরেজি/হিজরি) ও নামাজের সময়ের হিসাব — ইন্টারনেট ছাড়াই ফোনে হয় */
public class Islamic {

    // হিজরি তারিখ চাঁদ দেখার উপর নির্ভর করে; বাংলাদেশের জন্য ১ দিন যোগ করা হয়েছে
    static final int HIJRI_OFFSET = 2;

    static final String[] WEEK = {"রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার"};
    static final String[] EN_MONTH = {"জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন", "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"};
    static final String[] BN_MONTH = {"বৈশাখ", "জ্যৈষ্ঠ", "আষাঢ়", "শ্রাবণ", "ভাদ্র", "আশ্বিন", "কার্তিক", "অগ্রহায়ণ", "পৌষ", "মাঘ", "ফাল্গুন", "চৈত্র"};
    static final String[] HJ_MONTH = {"মুহাররম", "সফর", "রবিউল আউয়াল", "রবিউল আখির", "জমাদিউল আউয়াল", "জমাদিউল আখির", "রজব", "শাবান", "রমজান", "শাওয়াল", "জিলকদ", "জিলহজ"};
    // বাংলা মাস শুরুর ইংরেজি তারিখ (মাস, দিন): বৈশাখ ১৪ এপ্রিল ... চৈত্র ১৫ মার্চ
    static final int[][] BN_START = {{4, 14}, {5, 15}, {6, 15}, {7, 16}, {8, 16}, {9, 16}, {10, 16}, {11, 15}, {12, 15}, {1, 14}, {2, 13}, {3, 15}};

    static String bn(long n) {
        String s = String.valueOf(n);
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('০' + (ch - '0')) : ch);
        return b.toString();
    }

    static String bn2(int n) { return bn(n < 10 ? "0" + n : "" + n); }
    static String bn(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('০' + (ch - '0')) : ch);
        return b.toString();
    }

    static String englishDate(Calendar c) {
        return WEEK[c.get(Calendar.DAY_OF_WEEK) - 1] + ", " + bn2(c.get(Calendar.DAY_OF_MONTH)) + " "
                + EN_MONTH[c.get(Calendar.MONTH)] + ", " + bn(c.get(Calendar.YEAR));
    }

    static String banglaDate(Calendar c) {
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        int y0 = (m > 4 || (m == 4 && d >= 14)) ? y : y - 1; // বাংলা বছর শুরুর ইংরেজি বছর
        int today = y * 10000 + m * 100 + d;
        int idx = 0;
        long startKey = 0;
        for (int i = 0; i < 12; i++) {
            int sy = BN_START[i][0] >= 4 ? y0 : y0 + 1;
            int key = sy * 10000 + BN_START[i][0] * 100 + BN_START[i][1];
            if (key <= today) { idx = i; startKey = key; }
        }
        Calendar st = Calendar.getInstance();
        st.clear();
        st.set((int) (startKey / 10000), (int) ((startKey / 100) % 100) - 1, (int) (startKey % 100), 12, 0, 0);
        Calendar cur = Calendar.getInstance();
        cur.clear();
        cur.set(y, m - 1, d, 12, 0, 0);
        int bd = (int) Math.round((cur.getTimeInMillis() - st.getTimeInMillis()) / 86400000.0) + 1;
        int by = y0 - 593;
        return bn(bd) + " " + BN_MONTH[idx] + " " + bn(by) + " বঙ্গাব্দ";
    }

    static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        int a = y / 100, b = 2 - a + a / 4;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** আরবি তারিখ সমন্বয়: ০ = সৌদি (উম্মুল কুরা), -১ = বাংলাদেশ (১ দিন পিছনে), +১ */
    static volatile int hijriAdj = 0;

    static String hijriDate(Calendar c) {
        try {
            Calendar x = (Calendar) c.clone();
            x.add(Calendar.DAY_OF_MONTH, hijriAdj);
            android.icu.util.IslamicCalendar ic = new android.icu.util.IslamicCalendar(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
            ic.setTimeInMillis(x.getTimeInMillis());
            int mi = Math.max(0, Math.min(11, ic.get(android.icu.util.Calendar.MONTH)));
            return bn(ic.get(android.icu.util.Calendar.DAY_OF_MONTH)) + " " + HJ_MONTH[mi] + " " + bn(ic.get(android.icu.util.Calendar.YEAR)) + " হিজরি";
        } catch (Throwable ignored) {
        }
        return hijriTab(c, hijriAdj);
    }

    static String hijriTab(Calendar c, int adj) {
        long jd = (long) (julian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)) + 0.5) + HIJRI_OFFSET + adj;
        long l = jd - 1948440 + 10632;
        long n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        long j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        long m = (24 * l) / 709;
        long d = l - (709 * m) / 24;
        long y = 30 * n + j - 30;
        int mi = (int) Math.max(1, Math.min(12, m)) - 1;
        return bn(d) + " " + HJ_MONTH[mi] + " " + bn(y) + " হিজরি";
    }

    // ---------- নামাজের সময় (করাচি পদ্ধতি: ফজর ও ইশা ১৮°, আসর হানাফি) ----------
    static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }
    static double sind(double x) { return Math.sin(Math.toRadians(x)); }
    static double cosd(double x) { return Math.cos(Math.toRadians(x)); }

    static double[] sun(double jd) {
        double d = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * d, 360), q = fix(280.459 + 0.98564736 * d, 360);
        double L = fix(q + 1.915 * sind(g) + 0.020 * sind(2 * g), 360);
        double e = 23.439 - 0.00000036 * d;
        double ra = fix(Math.toDegrees(Math.atan2(cosd(e) * sind(L), cosd(L))) / 15, 24);
        double dec = Math.toDegrees(Math.asin(sind(e) * sind(L)));
        return new double[]{dec, q / 15 - ra};
    }

    /** আসরের ছায়া: ২ = হানাফি, ১ = শাফেয়ী (অ্যাপের সেটিং থেকে বসে) */
    static volatile int asrFactor = 2;

    /** সৌদি/গালফ এলাকা: উম্মুল কুরা পদ্ধতি (ফজর ১৮.৫°, ইশা = মাগরিবের ৯০ মিনিট পর) */
    static boolean gulf(double lat, double lng) { return lat >= 12 && lat <= 33 && lng >= 34 && lng <= 60; }

    /** ফেরত: {ফজর, সূর্যোদয়, যোহর, আসর, সূর্যাস্ত/মাগরিব, ইশা} — দিনের ঘণ্টা (দশমিক) */
    static double[] prayer(Calendar c, double lat, double lng, double tz) {
        double jd = julian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)) - lng / (15 * 24.0);
        double adj = tz - lng / 15.0;
        double[] r = new double[6];
        r[0] = angle(jd, lat, gulf(lat, lng) ? 18.5 : 18, 5 / 24.0, true) + adj;
        r[1] = angle(jd, lat, 0.833, 6 / 24.0, true) + adj;
        r[2] = fix(12 - sun(jd + 0.5)[1], 24) + adj + 1 / 60.0; // যোহর: দুপুর + ১ মিনিট
        double dec = sun(jd + 13 / 24.0)[0];
        double aa = -Math.toDegrees(Math.atan(1.0 / (asrFactor + Math.tan(Math.toRadians(Math.abs(lat - dec))))));
        r[3] = angle(jd, lat, aa, 13 / 24.0, false) + adj;
        r[4] = angle(jd, lat, 0.833, 18 / 24.0, false) + adj;
        r[5] = gulf(lat, lng) ? r[4] + 1.5 : angle(jd, lat, 18, 18 / 24.0, false) + adj;
        return r;
    }

    static double angle(double jd, double lat, double a, double t, boolean ccw) {
        double[] s = sun(jd + t);
        double mid = fix(12 - s[1], 24);
        double v = (-sind(a) - sind(s[0]) * sind(lat)) / (cosd(s[0]) * cosd(lat));
        v = Math.max(-1, Math.min(1, v));
        return mid + (ccw ? -1 : 1) * Math.toDegrees(Math.acos(v)) / 15.0;
    }

    /** ১২ ঘণ্টা ফরম্যাট, বাংলা সংখ্যা, am/pm ছাড়া (যেমন ০৪:০৭) */
    static String fmt(double h) {
        h = fix(h + 0.5 / 60, 24);
        int hh = (int) h, mm = (int) ((h - hh) * 60);
        int h12 = hh % 12 == 0 ? 12 : hh % 12;
        return bn2(h12) + ":" + bn2(mm);
    }

    static final String[] CITY = {"ঢাকা", "চট্টগ্রাম", "খুলনা", "রাজশাহী", "সিলেট", "বরিশাল", "রংপুর", "ময়মনসিংহ"};
    static final double[][] CITY_XY = {{23.8103, 90.4125}, {22.3569, 91.7832}, {22.8456, 89.5403}, {24.3745, 88.6042},
            {24.8949, 91.8687}, {22.7010, 90.3535}, {25.7439, 89.2752}, {24.7471, 90.4203}};

    static String cityName(double lat, double lng) {
        int best = -1;
        double bd = 1e9;
        for (int i = 0; i < CITY.length; i++) {
            double dy = (lat - CITY_XY[i][0]) * 111, dx = (lng - CITY_XY[i][1]) * 111 * Math.cos(Math.toRadians(lat));
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d < bd) { bd = d; best = i; }
        }
        return (best >= 0 && bd < 70) ? CITY[best] : "আপনার অবস্থান";
    }
}
