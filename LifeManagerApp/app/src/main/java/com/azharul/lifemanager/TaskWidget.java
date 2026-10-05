package com.azharul.lifemanager;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.app.AlarmManager;
import java.util.Calendar;
import java.util.TimeZone;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/** হোম স্ক্রিন উইজেট: ছবি এঁকে দেখানো হয় যাতে তিরো বাংলা ফন্ট ব্যবহার করা যায় */
public class TaskWidget extends AppWidgetProvider {

    static final String FONT_FILE = "TiroBangla-Regular.ttf";
    static Typeface cached;
    static long cachedLen = -1;

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        ensureFont(c);
        for (int id : ids) update(c, m, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        update(c, m, id);
    }

    static final String ACTION_REFRESH = "com.azharul.lifemanager.WIDGET_REFRESH";
    static final String[] KEYS = {"dashboard", "expenses", "add", "income", "more"};
    static final String[] ICONS = {"🏠", "💸", "＋", "💰", "☰"};
    static final String[] LABELS = {"হোম", "খরচ", "যোগ", "আয়", "আরও"};
    static final int[] IDS = {R.id.ic1, R.id.ic2, R.id.ic3, R.id.ic4, R.id.ic5};

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        if (i != null && ACTION_REFRESH.equals(i.getAction())) refreshAll(c);
    }

    static void update(Context c, AppWidgetManager m, int id) {
        int wDp = 250, hDp = 250;
        try {
            Bundle o = m.getAppWidgetOptions(id);
            int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
            int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (h <= 0) h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250);
            if (w > 100) wDp = w;
            if (h > 100) hDp = h;
        } catch (Exception ignored) {
        }
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        int infoW = wDp - 12, infoH = hDp - 46 - 10;
        try {
            v.setImageViewBitmap(R.id.img, draw(c, infoW, infoH));
        } catch (Throwable t) {
            // ছবি আঁকা ব্যর্থ হলে খালি থাকবে
        }
        float cellW = infoW / 5f;
        for (int i = 0; i < 5; i++) {
            try {
                v.setImageViewBitmap(IDS[i], icon(c, i, cellW, 46));
            } catch (Throwable ignored) {
            }
            Intent it = new Intent(c, MainActivity.class);
            it.setData(Uri.parse("lm://go/" + KEYS[i]));
            it.putExtra("go", KEYS[i]);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            v.setOnClickPendingIntent(IDS[i], PendingIntent.getActivity(c, 10 + i, it,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        v.setOnClickPendingIntent(R.id.img, PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(id, v);
        scheduleRefresh(c);
    }

    /** পরের নামাজের সময় বা মধ্যরাতে উইজেট নিজে নতুন করে আঁকবে */
    static void scheduleRefresh(Context c) {
        try {
            double[] loc = location(c);
            Calendar now = Calendar.getInstance();
            double tz = TimeZone.getDefault().getOffset(now.getTimeInMillis()) / 3600000.0;
            long next = Long.MAX_VALUE;
            for (int d = 0; d < 2; d++) {
                Calendar day = (Calendar) now.clone();
                day.add(Calendar.DAY_OF_MONTH, d);
                double[] t = Islamic.prayer(day, loc[0], loc[1], tz);
                int[] idx = {0, 2, 3, 4, 5};
                for (int k : idx) {
                    Calendar x = (Calendar) day.clone();
                    x.set(Calendar.HOUR_OF_DAY, 0); x.set(Calendar.MINUTE, 0); x.set(Calendar.SECOND, 0); x.set(Calendar.MILLISECOND, 0);
                    long ms = x.getTimeInMillis() + (long) (t[k] * 3600000L) + 60000L;
                    if (ms > now.getTimeInMillis() + 30000 && ms < next) next = ms;
                }
            }
            Calendar mid = (Calendar) now.clone();
            mid.add(Calendar.DAY_OF_MONTH, 1);
            mid.set(Calendar.HOUR_OF_DAY, 0); mid.set(Calendar.MINUTE, 1); mid.set(Calendar.SECOND, 0);
            if (mid.getTimeInMillis() < next) next = mid.getTimeInMillis();
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(c, TaskWidget.class);
            i.setAction(ACTION_REFRESH);
            PendingIntent pi = PendingIntent.getBroadcast(c, 77, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.set(AlarmManager.RTC, next, pi);
        } catch (Throwable ignored) {
        }
    }

    /** সংরক্ষিত অবস্থান (না থাকলে খুলনা) */
    static double[] location(Context c) {
        android.content.SharedPreferences p = c.getSharedPreferences("loc", Context.MODE_PRIVATE);
        if (p.contains("lat")) return new double[]{Double.longBitsToDouble(p.getLong("lat", 0)), Double.longBitsToDouble(p.getLong("lng", 0))};
        return new double[]{22.8456, 89.5403};
    }

    static void saveLocation(Context c, double lat, double lng) {
        c.getSharedPreferences("loc", Context.MODE_PRIVATE).edit()
                .putLong("lat", Double.doubleToLongBits(lat)).putLong("lng", Double.doubleToLongBits(lng)).apply();
    }

    /** নিচের ৫টি আইকন-বোতাম */
    static Bitmap icon(Context c, int i, float wDp, float hDp) {
        float d = c.getResources().getDisplayMetrics().density;
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, Math.round(wDp * d)), Math.round(hDp * d), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.scale(d, d);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float tw = Math.min(wDp - 6, 52), x0 = (wDp - tw) / 2f;
        boolean fab = i == 2;
        p.setColor(fab ? 0xFFFFFFFF : 0x2EFFFFFF);
        cv.drawRoundRect(new RectF(x0, 3, x0 + tw, hDp - 3), 13, 13, p);
        p.setTextAlign(Paint.Align.CENTER);
        float cx = wDp / 2f;
        if (fab) {
            p.setColor(0xFFE11D74);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(22);
            cv.drawText("+", cx, 24, p);
        } else {
            p.setColor(0xFFFFFFFF);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(17);
            cv.drawText(ICONS[i], cx, 22, p);
        }
        p.setTypeface(font(c, false));
        p.setTextSize(9.5f);
        p.setColor(fab ? 0xFFB0145C : 0xFFFFFFFF);
        cv.drawText(LABELS[i], cx, hDp - 7, p);
        return bmp;
    }

    static Typeface font(Context c, boolean bold) {
        Typeface base = null;
        try {
            File f = new File(c.getFilesDir(), FONT_FILE);
            if (f.exists() && f.length() > 10000) {
                if (cached == null || cachedLen != f.length()) {
                    cached = Typeface.createFromFile(f);
                    cachedLen = f.length();
                }
                base = cached;
            }
        } catch (Throwable ignored) {
        }
        if (base == null) base = Typeface.DEFAULT;
        return bold ? Typeface.create(base, Typeface.BOLD) : base;
    }

    /** ইমোজি/অদ্ভুত চিহ্ন বাদ দেয় (ফন্টে এগুলো নেই) */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isSurrogate(ch)) continue;
            int t = Character.getType(ch);
            if (t == Character.OTHER_SYMBOL || t == Character.FORMAT && ch != '‌' && ch != '‍') continue;
            if (ch == '️') continue;
            b.append(ch);
        }
        return b.toString().trim();
    }

    static String[] arr(JSONObject o, String key, int max) {
        JSONArray a = o.optJSONArray(key);
        if (a == null) return new String[0];
        int n = Math.min(max, a.length());
        String[] r = new String[n];
        for (int i = 0; i < n; i++) r[i] = clean(a.optString(i, ""));
        return r;
    }

    static Bitmap draw(Context c, int wDp, int hDp) {
        float d = c.getResources().getDisplayMetrics().density;
        float scale = d;
        while (wDp * scale * hDp * scale > 1000000f) scale *= 0.85f;
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, Math.round(wDp * scale)), Math.max(1, Math.round(hDp * scale)), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.scale(scale, scale);
        float W = wDp, H = hDp;

        String[] today = new String[0], up = new String[0];
        int todayN = 0, upN = 0;
        String empty = "";
        String json = c.getSharedPreferences("widget", Context.MODE_PRIVATE).getString("json", "");
        if (json.isEmpty()) {
            empty = "কাজ দেখতে অ্যাপ একবার খুলুন";
        } else {
            try {
                JSONObject o = new JSONObject(json);
                today = arr(o, "today", 8);
                up = arr(o, "upcoming", 10);
                todayN = o.optInt("todayCount", today.length);
                upN = o.optInt("upCount", up.length);
            } catch (Exception e) {
                empty = "তথ্য পড়া যায়নি, অ্যাপ খুলুন";
            }
        }

        Typeface reg = font(c, false), bold = font(c, true);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float pad = 8;

        // তারিখ ও সময়
        Calendar now = Calendar.getInstance();
        double[] loc = location(c);
        double tz = TimeZone.getDefault().getOffset(now.getTimeInMillis()) / 3600000.0;
        double[] t = Islamic.prayer(now, loc[0], loc[1], tz);
        float splitX = W * 0.62f;

        float y = 4;
        p.setTextAlign(Paint.Align.LEFT);
        p.setTypeface(bold);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(13);
        y += 12;
        cv.drawText(ell(Islamic.banglaDate(now), p, splitX - pad - 4), pad, y, p);
        p.setTypeface(reg);
        p.setTextSize(11.5f);
        p.setColor(0xFFFBD3E5);
        y += 14;
        cv.drawText(ell(Islamic.englishDate(now), p, splitX - pad - 4), pad, y, p);
        y += 14;
        cv.drawText(ell(Islamic.hijriDate(now), p, splitX - pad - 4), pad, y, p);

        // ডানে: সূর্যোদয়/সূর্যাস্ত/অবস্থান
        Paint ln = new Paint(Paint.ANTI_ALIAS_FLAG);
        ln.setColor(0x44FFFFFF);
        cv.drawRect(splitX, 7, splitX + 1, 46, ln);
        float rx = splitX + 8;
        p.setColor(0xFFFFFFFF);
        p.setTextSize(11.5f);
        cv.drawText(ell("সূর্যোদয় " + Islamic.fmt(t[1]), p, W - rx - 4), rx, 17, p);
        cv.drawText(ell("সূর্যাস্ত " + Islamic.fmt(t[4]), p, W - rx - 4), rx, 31, p);
        p.setColor(0xFFFBD3E5);
        p.setTextSize(10.5f);
        cv.drawText(ell(Islamic.cityName(loc[0], loc[1]), p, W - rx - 4), rx, 44, p);

        // পাঁচ ওয়াক্ত
        String[] names = {"ফজর", "যোহর", "আসর", "মাগরিব", "ইশা"};
        double[] pt = {t[0], t[2], t[3], t[4], t[5]};
        double nowH = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0;
        int next = 0;
        for (int i = 0; i < 5; i++) { if (pt[i] > nowH) { next = i; break; } if (i == 4) next = 0; }
        float top = 53, ph = 33, gap = 4;
        float pw = (W - pad * 2 - gap * 4) / 5f;
        for (int i = 0; i < 5; i++) {
            float x0 = pad + i * (pw + gap);
            boolean on = i == next;
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(on ? 0xFFFFFFFF : 0x2EFFFFFF);
            cv.drawRoundRect(new RectF(x0, top, x0 + pw, top + ph), 9, 9, bg);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(reg);
            p.setTextSize(10);
            p.setColor(on ? 0xFFB0145C : 0xFFFBD3E5);
            cv.drawText(names[i], x0 + pw / 2, top + 13, p);
            p.setTypeface(bold);
            p.setTextSize(11.5f);
            p.setColor(on ? 0xFF1A1A1F : 0xFFFFFFFF);
            cv.drawText(Islamic.fmt(pt[i]), x0 + pw / 2, top + 27, p);
        }

        // বিভাজক
        y = top + ph + 6;
        cv.drawRect(pad + 6, y, W - pad - 6, y + 1, ln);
        y += 1;

        // কাজ: আজকের ও আগামী ৭ দিন
        float cx = W / 2f, maxW = W - pad * 2;
        float avail = H - y - 4;
        int cap = (int) Math.max(2, Math.floor((avail - 36) / 14f));
        int uCap = Math.max(1, cap / 2), tCap = Math.max(1, cap - uCap);
        if (up.length == 0) { uCap = 1; tCap = Math.max(1, cap - 1); }
        else if (today.length == 0) { tCap = 1; uCap = Math.max(1, cap - 1); }
        else if (today.length < tCap) { uCap = Math.min(up.length, cap - today.length); }

        p.setTextAlign(Paint.Align.CENTER);
        if (!empty.isEmpty()) {
            p.setTypeface(reg);
            p.setTextSize(12);
            p.setColor(0xFFFBD3E5);
            cv.drawText(empty, cx, y + 26, p);
        } else {
            y += 14;
            p.setTypeface(bold);
            p.setTextSize(12.5f);
            p.setColor(0xFFFFFFFF);
            cv.drawText("আজকের কাজ (" + Islamic.bn(todayN) + ")", cx, y, p);
            y = lines(cv, p, reg, today, todayN, tCap, "আজ কোনো কাজ নেই", cx, y, maxW);
            y += 15;
            p.setTypeface(bold);
            p.setTextSize(12.5f);
            p.setColor(0xFFFFFFFF);
            cv.drawText("আগামী ৭ দিনের কাজ (" + Islamic.bn(upN) + ")", cx, y, p);
            y = lines(cv, p, reg, up, upN, uCap, "আগামী ৭ দিনে কোনো কাজ নেই", cx, y, maxW);
        }
        return bmp;
    }

    static float lines(Canvas cv, Paint p, Typeface reg, String[] a, int total, int cap,
                       String emptyText, float cx, float y, float maxW) {
        p.setTypeface(reg);
        p.setTextSize(11.5f);
        if (a.length == 0) {
            y += 13;
            p.setColor(0xFFFBD3E5);
            cv.drawText(emptyText, cx, y, p);
            return y;
        }
        p.setColor(0xFFFBD3E5);
        int show = Math.min(a.length, cap);
        for (int i = 0; i < show; i++) {
            y += 13.5f;
            String s = a[i];
            if (i == show - 1 && total > show) s = s + "  (+" + Islamic.bn(total - show) + ")";
            cv.drawText(ell(s, p, maxW), cx, y, p);
        }
        return y;
    }

    static String ell(String s, Paint p, float w) {
        return TextUtils.ellipsize(s, new android.text.TextPaint(p), w, TextUtils.TruncateAt.END).toString();
    }

    static void save(Context c, String json) {
        c.getSharedPreferences("widget", Context.MODE_PRIVATE).edit().putString("json", json).apply();
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, TaskWidget.class));
        for (int id : ids) update(c, m, id);
    }

    /** তিরো বাংলা ফন্ট একবার নামিয়ে ফোনে রেখে দেয় */
    static void ensureFont(final Context c) {
        final File f = new File(c.getFilesDir(), FONT_FILE);
        if (f.exists() && f.length() > 10000) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String[] urls = {
                    "https://raw.githubusercontent.com/google/fonts/main/ofl/tirobangla/TiroBangla-Regular.ttf",
                    "https://cdn.jsdelivr.net/gh/google/fonts@main/ofl/tirobangla/TiroBangla-Regular.ttf",
                    "https://github.com/google/fonts/raw/main/ofl/tirobangla/TiroBangla-Regular.ttf"
                };
                for (String u : urls) {
                    try {
                        java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                        h.setConnectTimeout(15000);
                        h.setReadTimeout(30000);
                        h.setInstanceFollowRedirects(true);
                        if (h.getResponseCode() != 200) continue;
                        File tmp = new File(c.getFilesDir(), FONT_FILE + ".tmp");
                        java.io.InputStream in = h.getInputStream();
                        java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                        out.close();
                        in.close();
                        if (tmp.length() > 10000 && tmp.renameTo(f)) {
                            refreshAll(c);
                            return;
                        }
                        tmp.delete();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }).start();
    }
}
