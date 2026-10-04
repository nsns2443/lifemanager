package com.azharul.lifemanager;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
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

    static void update(Context c, AppWidgetManager m, int id) {
        int wDp = 250, hDp = 180;
        try {
            Bundle o = m.getAppWidgetOptions(id);
            int w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
            int h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
            if (h <= 0) h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180);
            if (w > 60) wDp = w;
            if (h > 60) hDp = h;
        } catch (Exception ignored) {
        }
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        try {
            v.setImageViewBitmap(R.id.img, draw(c, wDp, hDp));
        } catch (Throwable t) {
            // ছবি আঁকা ব্যর্থ হলে খালি রাখা হবে
        }
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.root, pi);
        m.updateAppWidget(id, v);
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
        // ছবি খুব বড় হতে না দেওয়া
        while (wDp * scale * hDp * scale > 1100000f) scale *= 0.85f;
        int wp = Math.max(1, Math.round(wDp * scale));
        int hp = Math.max(1, Math.round(hDp * scale));
        Bitmap bmp = Bitmap.createBitmap(wp, hp, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.scale(scale, scale);
        float W = wDp, H = hDp;

        // ডেটা পড়া
        String title = "আজকের কাজ";
        String[] today = new String[0], up = new String[0];
        int todayN = 0, upN = 0;
        String[] labs = {"আমল", "আজকের আয়", "আজকের খরচ"};
        String[] vals = {"–", "–", "–"};
        String empty = "";
        String json = c.getSharedPreferences("widget", Context.MODE_PRIVATE).getString("json", "");
        if (json.isEmpty()) {
            empty = "অ্যাপ একবার খুললে এখানে তথ্য আসবে";
        } else {
            try {
                JSONObject o = new JSONObject(json);
                title = clean(o.optString("title", title));
                today = arr(o, "today", 8);
                up = arr(o, "upcoming", 10);
                todayN = o.optInt("todayCount", today.length);
                upN = o.optInt("upCount", up.length);
                JSONArray st = o.optJSONArray("stats");
                if (st != null) {
                    for (int i = 0; i < 3 && i < st.length(); i++) {
                        JSONArray p = st.optJSONArray(i);
                        if (p != null) {
                            labs[i] = clean(p.optString(0, labs[i]));
                            vals[i] = clean(p.optString(1, vals[i]));
                        }
                    }
                }
            } catch (Exception e) {
                empty = "তথ্য পড়া যায়নি, অ্যাপ খুলুন";
            }
        }

        // পটভূমি: গোলাপি → কালো গ্রেডিয়েন্ট
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, W, H, 0xFFE11D74, 0xFF0B0B10, Shader.TileMode.CLAMP));
        cv.drawRoundRect(new RectF(0, 0, W, H), 22, 22, bg);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextAlign(Paint.Align.CENTER);
        Typeface reg = font(c, false), bold = font(c, true);
        float cx = W / 2f, maxW = W - 28;

        // কতগুলো লাইন ধরবে
        float fixed = 24 + 22 + 44 + 26;
        int cap = (int) Math.max(4, Math.floor((H - fixed) / 17f));
        int tCap = (cap + 1) / 2, uCap = cap / 2;
        if (up.length == 0) { tCap = Math.min(cap - 1, 5); uCap = 1; }
        if (today.length == 0) { uCap = cap - 1; tCap = 1; }

        float contentH = 24 + 17f * (Math.min(Math.max(today.length, 1), tCap)
                + Math.min(Math.max(up.length, 1), uCap)) + 22 + 44 + 8;
        float y = Math.max(10, (H - contentH) / 2f) + 14;

        p.setTypeface(bold);
        p.setColor(0xFFFFFFFF);
        p.setTextSize(15);
        cv.drawText(ell(title, p, maxW), cx, y, p);
        y += 8;

        if (!empty.isEmpty()) {
            p.setTypeface(reg);
            p.setTextSize(13);
            y += 24;
            cv.drawText(ell(empty, p, maxW), cx, y, p);
            y += 12;
        } else {
            y = lines(cv, p, reg, today, todayN, tCap, "আজ কোনো কাজ নেই", cx, y, maxW);
            // আগামী ৭ দিন শিরোনাম
            y += 5;
            p.setTypeface(bold);
            p.setTextSize(12);
            p.setColor(0xFFF9C6DD);
            y += 12;
            cv.drawText("আগামী ৭ দিনের কাজ (" + bn(upN) + ")", cx, y, p);
            y += 3;
            p.setColor(0xFFFFFFFF);
            y = lines(cv, p, reg, up, upN, uCap, "আগামী ৭ দিনে কোনো কাজ নেই", cx, y, maxW);
        }

        // বিভাজক রেখা
        Paint ln = new Paint(Paint.ANTI_ALIAS_FLAG);
        ln.setColor(0x55FFFFFF);
        y += 6;
        cv.drawRect(16, y, W - 16, y + 1, ln);
        y += 6;

        // তিনটি তথ্য
        float cw = (W - 16) / 3f;
        for (int i = 0; i < 3; i++) {
            float x = 8 + cw * i + cw / 2f;
            p.setTypeface(reg);
            p.setColor(0xFFF9C6DD);
            p.setTextSize(10.5f);
            cv.drawText(ell(labs[i], p, cw - 6), x, y + 11, p);
            p.setTypeface(bold);
            p.setColor(0xFFFFFFFF);
            p.setTextSize(14);
            cv.drawText(ell(vals[i], p, cw - 6), x, y + 28, p);
        }
        return bmp;
    }

    static float lines(Canvas cv, Paint p, Typeface reg, String[] a, int total, int cap,
                       String emptyText, float cx, float y, float maxW) {
        p.setTypeface(reg);
        p.setTextSize(12.5f);
        p.setColor(0xFFFFFFFF);
        if (a.length == 0) {
            y += 17;
            p.setColor(0xFFF9C6DD);
            cv.drawText(emptyText, cx, y, p);
            p.setColor(0xFFFFFFFF);
            return y;
        }
        int show = Math.min(a.length, cap);
        for (int i = 0; i < show; i++) {
            y += 17;
            String s = a[i];
            if (i == show - 1 && total > show) s = s + "  (+" + bn(total - show) + ")";
            cv.drawText(ell(s, p, maxW), cx, y, p);
        }
        return y;
    }

    static String ell(String s, Paint p, float w) {
        return TextUtils.ellipsize(s, new android.text.TextPaint(p), w, TextUtils.TruncateAt.END).toString();
    }

    static String bn(int n) {
        String s = String.valueOf(n);
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append((char) ('০' + (ch - '0')));
        return b.toString();
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
