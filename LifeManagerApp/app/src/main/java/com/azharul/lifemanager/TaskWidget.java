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
import android.os.Handler;
import android.os.Looper;
import android.graphics.Path;
import android.widget.Toast;
import android.content.SharedPreferences;
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
    public void onUpdate(final Context c, AppWidgetManager m, int[] ids) {
        ensureFont(c);
        for (int id : ids) update(c, m, id);
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (fetchData(c) == 1) refreshAll(c);
                } catch (Throwable ignored) {
                } finally {
                    pr.finish();
                }
            }
        }).start();
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

    static final String ACTION_NOW = "com.azharul.lifemanager.WIDGET_REFRESH_NOW";

    @Override
    public void onReceive(final Context c, Intent i) {
        super.onReceive(c, i);
        if (i == null) return;
        if (ACTION_REFRESH.equals(i.getAction())) {
            refreshAll(c);
        } else if (ACTION_NOW.equals(i.getAction())) {
            final PendingResult pr = goAsync();
            final Handler h = new Handler(Looper.getMainLooper());
            h.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(c, "হালনাগাদ হচ্ছে…", Toast.LENGTH_SHORT).show();
                }
            });
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String msg;
                    try {
                        refreshLocation(c);
                        int r = fetchData(c);
                        refreshAll(c);
                        msg = r == 1 ? "হালনাগাদ হয়েছে" : r == 0 ? "অ্যাপ একবার খুলে লগইন করুন" : "ইন্টারনেট/লগইন সমস্যা — আগের তথ্য দেখানো হচ্ছে";
                    } catch (Throwable t) {
                        msg = "হালনাগাদ করা যায়নি";
                    } finally {
                        pr.finish();
                    }
                    final String m = msg;
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(c, m, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }).start();
        }
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
        refreshLocation(c);
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
        try {
            v.setImageViewBitmap(R.id.refresh, refreshIcon(c));
        } catch (Throwable ignored) {
        }
        Intent now = new Intent(c, TaskWidget.class);
        now.setAction(ACTION_NOW);
        v.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, 88, now,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
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
            loadAsr(c);
            double[] loc = location(c);
            TimeZone zone = tzOf(c);
            Calendar now = Calendar.getInstance(zone);
            double tz = zone.getOffset(now.getTimeInMillis()) / 3600000.0;
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

    /** ফোনের সর্বশেষ জানা অবস্থান নিয়ে রাখে; জায়গা বদলালে শহরের নাম নতুন করে বের করে */
    static void refreshLocation(final Context c) {
        try {
            if (manual(c)) return;
            if (android.os.Build.VERSION.SDK_INT >= 23
                    && c.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
            android.location.LocationManager lm = (android.location.LocationManager) c.getSystemService(Context.LOCATION_SERVICE);
            android.location.Location best = null;
            for (String pr : lm.getProviders(true)) {
                android.location.Location l = lm.getLastKnownLocation(pr);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best == null) return;
            final double lat = best.getLatitude(), lng = best.getLongitude();
            android.content.SharedPreferences sp = c.getSharedPreferences("loc", Context.MODE_PRIVATE);
            boolean had = sp.contains("lat");
            double[] old = location(c);
            double dy = (lat - old[0]) * 111, dx = (lng - old[1]) * 111 * Math.cos(Math.toRadians(lat));
            boolean moved = !had || Math.sqrt(dx * dx + dy * dy) > 5;
            if (!moved) return;
            saveLocation(c, lat, lng);
            sp.edit().putString("city", "").apply();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        android.location.Geocoder g = new android.location.Geocoder(c, new java.util.Locale("bn", "BD"));
                        java.util.List<android.location.Address> l = g.getFromLocation(lat, lng, 1);
                        if (l != null && !l.isEmpty()) {
                            android.location.Address a = l.get(0);
                            String n = a.getLocality();
                            if (n == null) n = a.getSubAdminArea();
                            if (n == null) n = a.getAdminArea();
                            if (n == null) n = a.getCountryName();
                            if (n != null && !n.isEmpty()) {
                                c.getSharedPreferences("loc", Context.MODE_PRIVATE).edit().putString("city", n).apply();
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    try {
                        refreshAll(c);
                    } catch (Throwable ignored) {
                    }
                }
            }).start();
        } catch (Throwable ignored) {
        }
    }

    static boolean manual(Context c) {
        return c.getSharedPreferences("loc", Context.MODE_PRIVATE).getBoolean("manual", false);
    }

    static void loadAsr(Context c) {
        try { Islamic.asrFactor = c.getSharedPreferences("loc", Context.MODE_PRIVATE).getInt("asr", 2) == 1 ? 1 : 2; } catch (Throwable ignored) { }
    }

    static void loadHj(Context c) {
        try { Islamic.hijriAdj = c.getSharedPreferences("loc", Context.MODE_PRIVATE).getInt("hj", 0); } catch (Throwable ignored) { }
    }

    static void setHj(Context c, int v) {
        v = Math.max(-2, Math.min(2, v));
        c.getSharedPreferences("loc", Context.MODE_PRIVATE).edit().putInt("hj", v).apply();
        Islamic.hijriAdj = v;
    }

    static void setAsr(Context c, int f) {
        c.getSharedPreferences("loc", Context.MODE_PRIVATE).edit().putInt("asr", f == 1 ? 1 : 2).apply();
        Islamic.asrFactor = f == 1 ? 1 : 2;
    }

    static TimeZone tzOf(Context c) {
        try {
            SharedPreferences p = c.getSharedPreferences("loc", Context.MODE_PRIVATE);
            String z = p.getString("mtz", "");
            if (p.getBoolean("manual", false) && z != null && !z.isEmpty()) return TimeZone.getTimeZone(z);
        } catch (Throwable ignored) {
        }
        return TimeZone.getDefault();
    }

    /** অ্যাপ থেকে নিজে বাছাই করা জায়গা: json = {name, lat, lng, tz}; ফাঁকা হলে আবার অটো */
    static void setPlace(Context c, String json) {
        SharedPreferences.Editor e = c.getSharedPreferences("loc", Context.MODE_PRIVATE).edit();
        try {
            if (json == null || json.trim().isEmpty()) {
                e.putBoolean("manual", false);
            } else {
                JSONObject o = new JSONObject(json);
                e.putBoolean("manual", true);
                e.putLong("mlat", Double.doubleToLongBits(o.getDouble("lat")));
                e.putLong("mlng", Double.doubleToLongBits(o.getDouble("lng")));
                e.putString("mname", o.optString("name", ""));
                e.putString("mtz", o.optString("tz", ""));
            }
        } catch (Throwable t) {
            e.putBoolean("manual", false);
        }
        e.apply();
    }

    static final String[][] BN_CITY = {
        {"dhaka", "ঢাকা"}, {"gazipur", "গাজীপুর"}, {"narayanganj", "নারায়ণগঞ্জ"}, {"narsingdi", "নরসিংদী"}, {"manikganj", "মানিকগঞ্জ"},
        {"munshiganj", "মুন্সিগঞ্জ"}, {"tangail", "টাঙ্গাইল"}, {"kishoreganj", "কিশোরগঞ্জ"}, {"faridpur", "ফরিদপুর"}, {"madaripur", "মাদারীপুর"},
        {"shariatpur", "শরীয়তপুর"}, {"gopalganj", "গোপালগঞ্জ"}, {"rajbari", "রাজবাড়ী"}, {"chattogram", "চট্টগ্রাম"}, {"chittagong", "চট্টগ্রাম"},
        {"cox's bazar", "কক্সবাজার"}, {"coxs bazar", "কক্সবাজার"}, {"cumilla", "কুমিল্লা"}, {"comilla", "কুমিল্লা"}, {"feni", "ফেনী"},
        {"brahmanbaria", "ব্রাহ্মণবাড়িয়া"}, {"rangamati", "রাঙ্গামাটি"}, {"noakhali", "নোয়াখালী"}, {"chandpur", "চাঁদপুর"}, {"lakshmipur", "লক্ষ্মীপুর"},
        {"khagrachhari", "খাগড়াছড়ি"}, {"khagrachari", "খাগড়াছড়ি"}, {"bandarban", "বান্দরবান"}, {"rajshahi", "রাজশাহী"}, {"bogura", "বগুড়া"},
        {"bogra", "বগুড়া"}, {"pabna", "পাবনা"}, {"sirajganj", "সিরাজগঞ্জ"}, {"natore", "নাটোর"}, {"naogaon", "নওগাঁ"},
        {"chapainawabganj", "চাঁপাইনবাবগঞ্জ"}, {"nawabganj", "চাঁপাইনবাবগঞ্জ"}, {"joypurhat", "জয়পুরহাট"}, {"khulna", "খুলনা"}, {"jashore", "যশোর"},
        {"jessore", "যশোর"}, {"satkhira", "সাতক্ষীরা"}, {"bagerhat", "বাগেরহাট"}, {"kushtia", "কুষ্টিয়া"}, {"jhenaidah", "ঝিনাইদহ"},
        {"chuadanga", "চুয়াডাঙ্গা"}, {"meherpur", "মেহেরপুর"}, {"magura", "মাগুরা"}, {"narail", "নড়াইল"}, {"barishal", "বরিশাল"},
        {"barisal", "বরিশাল"}, {"bhola", "ভোলা"}, {"patuakhali", "পটুয়াখালী"}, {"pirojpur", "পিরোজপুর"}, {"barguna", "বরগুনা"},
        {"jhalokati", "ঝালকাঠি"}, {"sylhet", "সিলেট"}, {"moulvibazar", "মৌলভীবাজার"}, {"habiganj", "হবিগঞ্জ"}, {"sunamganj", "সুনামগঞ্জ"},
        {"rangpur", "রংপুর"}, {"dinajpur", "দিনাজপুর"}, {"kurigram", "কুড়িগ্রাম"}, {"gaibandha", "গাইবান্ধা"}, {"nilphamari", "নীলফামারী"},
        {"lalmonirhat", "লালমনিরহাট"}, {"thakurgaon", "ঠাকুরগাঁও"}, {"panchagarh", "পঞ্চগড়"}, {"mymensingh", "ময়মনসিংহ"}, {"jamalpur", "জামালপুর"},
        {"netrokona", "নেত্রকোণা"}, {"sherpur", "শেরপুর"}
    };

    /** ইংরেজিতে এলে বাংলাদেশের জেলার নাম বাংলায় বদলে দেয় */
    static String bnName(String n) {
        if (n == null) return "";
        String k = n.toLowerCase().replace(" district", "").replace(" division", "").replace(" city", "").replace(" sadar", "").trim();
        for (String[] r : BN_CITY) if (r[0].equals(k)) return r[1];
        return n;
    }

    static String city(Context c, double[] loc) {
        SharedPreferences p = c.getSharedPreferences("loc", Context.MODE_PRIVATE);
        if (p.getBoolean("manual", false)) {
            String m = p.getString("mname", "");
            if (m != null && !m.isEmpty()) return m;
        }
        String n = p.getString("city", "");
        return (n == null || n.isEmpty()) ? Islamic.cityName(loc[0], loc[1]) : bnName(n);
    }

    /** সংরক্ষিত অবস্থান (না থাকলে খুলনা) */
    static double[] location(Context c) {
        SharedPreferences p = c.getSharedPreferences("loc", Context.MODE_PRIVATE);
        if (p.getBoolean("manual", false) && p.contains("mlat")) {
            return new double[]{Double.longBitsToDouble(p.getLong("mlat", 0)), Double.longBitsToDouble(p.getLong("mlng", 0))};
        }
        if (p.contains("lat")) return new double[]{Double.longBitsToDouble(p.getLong("lat", 0)), Double.longBitsToDouble(p.getLong("lng", 0))};
        return new double[]{22.8456, 89.5403};
    }

    static void saveLocation(Context c, double lat, double lng) {
        c.getSharedPreferences("loc", Context.MODE_PRIVATE).edit()
                .putLong("lat", Double.doubleToLongBits(lat)).putLong("lng", Double.doubleToLongBits(lng)).apply();
    }

    /** অ্যাপ থেকে সেশন জমা (উইজেট নিজে সার্ভার থেকে কাজ আনার জন্য) */
    static void setSession(Context c, String url, String token) {
        c.getSharedPreferences("session", Context.MODE_PRIVATE).edit().putString("url", url == null ? "" : url).putString("token", token == null ? "" : token).apply();
    }

    /** ফেরত: 1 = সফল, 0 = সেশন নেই, -1 = ব্যর্থ */
    static int fetchData(Context c) {
        try {
            SharedPreferences s = c.getSharedPreferences("session", Context.MODE_PRIVATE);
            String url = s.getString("url", ""), tok = s.getString("token", "");
            if (url == null || url.isEmpty() || tok == null || tok.isEmpty()) return 0;
            java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            h.setRequestMethod("POST");
            h.setDoOutput(true);
            h.setConnectTimeout(8000);
            h.setReadTimeout(20000);
            h.setInstanceFollowRedirects(true);
            h.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
            JSONObject body = new JSONObject();
            body.put("a", "dashboard");
            body.put("p", new JSONObject());
            body.put("t", tok);
            java.io.OutputStream os = h.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();
            if (h.getResponseCode() != 200) return -1;
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(h.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject o = new JSONObject(sb.toString());
            if (!o.optBoolean("ok", false)) return -1;
            JSONObject d = o.getJSONObject("data");
            JSONArray td = d.optJSONArray("todayTasks"), up = d.optJSONArray("upcomingTasks");
            JSONArray today = new JSONArray(), upc = new JSONArray();
            int tn = td == null ? 0 : td.length(), un = up == null ? 0 : up.length();
            for (int i = 0; i < Math.min(8, tn); i++) {
                JSONObject t = td.getJSONObject(i);
                String tm = t.optString("DueTime", "");
                today.put((tm.isEmpty() ? "" : Islamic.bn(tm) + "  ") + t.optString("Title", ""));
            }
            for (int i = 0; i < Math.min(10, un); i++) {
                JSONObject t = up.getJSONObject(i);
                String ds = t.optString("DueDate", ""), tm = t.optString("DueTime", "");
                String dm = "";
                if (ds.length() >= 10) dm = Islamic.bn(Integer.parseInt(ds.substring(8, 10))) + "/" + Islamic.bn(Integer.parseInt(ds.substring(5, 7)));
                upc.put(dm + (tm.isEmpty() ? "" : " " + Islamic.bn(tm)) + "  " + t.optString("Title", ""));
            }
            JSONObject out = new JSONObject();
            out.put("title", "আজকের কাজ (" + Islamic.bn(tn) + ")");
            out.put("today", today);
            out.put("todayCount", tn);
            out.put("upcoming", upc);
            out.put("upCount", un);
            save(c, out.toString());
            return 1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** রিফ্রেশ আইকন (গোল তীর) */
    static Bitmap refreshIcon(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        int px = Math.round(26 * d);
        Bitmap bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.scale(d, d);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x33FFFFFF);
        cv.drawCircle(13, 13, 12.5f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0xFFFFFFFF);
        p.setStrokeCap(Paint.Cap.ROUND);
        RectF r = new RectF(7.5f, 7.5f, 18.5f, 18.5f);
        cv.drawArc(r, -60, 280, false, p);
        p.setStyle(Paint.Style.FILL);
        Path ph = new Path();
        ph.moveTo(18.5f, 5.2f);
        ph.lineTo(18.9f, 10.6f);
        ph.lineTo(13.9f, 8.6f);
        ph.close();
        cv.drawPath(ph, p);
        return bmp;
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

    /** লেখা জায়গায় না ধরলে ফন্ট ছোট করে বসায় */
    static float fit(Paint p, String s, float maxW, float size, float min) {
        p.setTextSize(size);
        while (p.measureText(s) > maxW && size > min) {
            size -= 0.5f;
            p.setTextSize(size);
        }
        return size;
    }

    static void ctext(Canvas cv, Paint p, String s, float cx, float y, float maxW, float size, float min) {
        fit(p, s, maxW, size, min);
        cv.drawText(ell(s, p, maxW), cx, y, p);
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

        TimeZone zone = tzOf(c);
        Calendar now = Calendar.getInstance(zone);
        double[] loc = location(c);
        double tz = zone.getOffset(now.getTimeInMillis()) / 3600000.0;
        loadAsr(c);
        loadHj(c);
        double[] t = Islamic.prayer(now, loc[0], loc[1], tz);
        Calendar tmr = (Calendar) now.clone();
        tmr.add(Calendar.DAY_OF_MONTH, 1);
        double nextFajr = Islamic.prayer(tmr, loc[0], loc[1], tz)[0];

        // ---- হেডার: ঠিক মাঝখানে দাগ, দুই পাশেই লেখা মাঝ বরাবর ----
        float mid = W / 2f;
        float lcx = mid / 2f, lw = mid - 12;
        float rLeft = mid + 6, rRight = W - 34;
        float rcx = (rLeft + rRight) / 2f, rw = rRight - rLeft;
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(bold);
        p.setColor(0xFFFFFFFF);
        ctext(cv, p, Islamic.hijriDate(now), lcx, 17, lw, 12.5f, 8.5f);
        p.setTypeface(reg);
        p.setColor(0xFFFBD3E5);
        ctext(cv, p, Islamic.englishDate(now), lcx, 32, lw, 11.5f, 8.5f);
        ctext(cv, p, Islamic.banglaDate(now), lcx, 47, lw, 11.5f, 8.5f);

        Paint ln = new Paint(Paint.ANTI_ALIAS_FLAG);
        ln.setColor(0x55FFFFFF);
        cv.drawRect(mid, 6, mid + 1, 50, ln);

        p.setColor(0xFFFFFFFF);
        ctext(cv, p, "সূর্যোদয় " + Islamic.fmt(t[1]), rcx, 17, rw, 11.5f, 8.5f);
        ctext(cv, p, "সূর্যাস্ত " + Islamic.fmt(t[4]), rcx, 32, rw, 11.5f, 8.5f);
        p.setColor(0xFFFBD3E5);
        ctext(cv, p, city(c, loc), rcx, 47, rw, 11.5f, 8.5f);

        // ---- পাঁচ ওয়াক্ত ----
        String[] names = {"ফজর", "যোহর", "আসর", "মাগরিব", "ইশা"};
        double[] pt = {t[0], t[2], t[3], t[4], t[5]};
        double[] pe = {t[1], t[3], t[4], t[5], nextFajr};
        double nowH = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60.0;
        int next = 0;
        for (int i = 0; i < 5; i++) { if (pt[i] > nowH) { next = i; break; } if (i == 4) next = 0; }
        float top = 56, ph = 33, gap = 4;
        float pw = (W - pad * 2 - gap * 4) / 5f;
        for (int i = 0; i < 5; i++) {
            float x0 = pad + i * (pw + gap);
            boolean on = i == next;
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setColor(on ? 0xFFFFFFFF : 0x2EFFFFFF);
            cv.drawRoundRect(new RectF(x0, top, x0 + pw, top + ph), 9, 9, bg);
            p.setTypeface(reg);
            p.setColor(on ? 0xFFB0145C : 0xFFFBD3E5);
            ctext(cv, p, names[i], x0 + pw / 2, top + 12, pw - 4, 10, 8);
            p.setTypeface(bold);
            p.setColor(on ? 0xFF1A1A1F : 0xFFFFFFFF);
            ctext(cv, p, Islamic.fmt(pt[i]), x0 + pw / 2, top + 23, pw - 4, 11.5f, 8.5f);
            p.setTypeface(reg);
            p.setColor(on ? 0xFF6B6B75 : 0xFFFBD3E5);
            ctext(cv, p, "শেষ " + Islamic.fmt(pe[i]), x0 + pw / 2, top + 31, pw - 4, 7.5f, 5.5f);
        }

        // ---- বিভাজক ----
        float y = top + ph + 7;
        cv.drawRect(pad + 6, y, W - pad - 6, y + 1, ln);
        float areaTop = y + 1, areaH = H - areaTop - 3;

        // ---- কাজ: বামে আজকের, মাঝে দাগ, ডানে আগামী ৭ দিনের ----
        float halfW = W / 2f;
        float lx = halfW / 2f, rx = halfW + halfW / 2f, colW = halfW - pad - 6;
        p.setTextAlign(Paint.Align.CENTER);
        if (!empty.isEmpty()) {
            p.setTypeface(reg);
            p.setColor(0xFFFBD3E5);
            ctext(cv, p, empty, W / 2f, areaTop + areaH / 2f + 4, W - pad * 2, 12.5f, 9);
            return bmp;
        }
        int cap = (int) Math.max(1, Math.floor((areaH - 22) / 14f));
        int tl = Math.max(1, Math.min(today.length, cap)), ul = Math.max(1, Math.min(up.length, cap));
        int rows = Math.max(tl, ul);
        float block = 16 + rows * 14f;
        float y0 = areaTop + Math.max(0, (areaH - block) / 2f) + 13;
        cv.drawRect(halfW, areaTop + 4, halfW + 1, H - 6, ln);
        p.setTypeface(bold);
        p.setColor(0xFFFFFFFF);
        ctext(cv, p, "আজকের কাজ (" + Islamic.bn(todayN) + ")", lx, y0, colW, 12.5f, 8.5f);
        lines(cv, p, reg, today, todayN, tl, "আজ কোনো কাজ নেই", lx, y0, colW);
        p.setTypeface(bold);
        p.setColor(0xFFFFFFFF);
        ctext(cv, p, "আগামী ৭ দিনের কাজ (" + Islamic.bn(upN) + ")", rx, y0, colW, 12.5f, 8.5f);
        lines(cv, p, reg, up, upN, ul, "আগামী ৭ দিনে কোনো কাজ নেই", rx, y0, colW);
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
