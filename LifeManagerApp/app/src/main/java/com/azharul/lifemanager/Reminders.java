package com.azharul.lifemanager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** কাজের রিমাইন্ডার ফোনের অ্যালার্মে বসানো ও সরানো */
final class Reminders {
    private Reminders() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("rem", Context.MODE_PRIVATE);
    }

    static void save(Context c, String json) {
        prefs(c).edit().putString("json", json).apply();
    }

    private static PendingIntent pending(Context c, String id) {
        Intent i = new Intent(c, AlarmReceiver.class);
        return PendingIntent.getBroadcast(c, id.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent pendingWithData(Context c, String id, String title, String body) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("body", body);
        return PendingIntent.getBroadcast(c, id.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void cancelAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        String old = prefs(c).getString("sched", "[]");
        try {
            JSONArray arr = new JSONArray(old);
            for (int k = 0; k < arr.length(); k++) {
                String id = arr.getJSONObject(k).getString("id");
                PendingIntent pi = pending(c, id);
                am.cancel(pi);
                pi.cancel();
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString("sched", "[]").putString("json", "[]").apply();
    }

    /** সংরক্ষিত তালিকা থেকে আবার সব অ্যালার্ম বসায় (পুরোনোগুলো আগে মুছে) */
    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        String json = prefs(c).getString("json", "[]");
        String old = prefs(c).getString("sched", "[]");
        try {
            JSONArray oldArr = new JSONArray(old);
            for (int k = 0; k < oldArr.length(); k++) {
                String id = oldArr.getJSONObject(k).getString("id");
                PendingIntent pi = pending(c, id);
                am.cancel(pi);
                pi.cancel();
            }
        } catch (Exception ignored) {
        }
        long now = System.currentTimeMillis();
        JSONArray kept = new JSONArray();
        try {
            JSONArray arr = new JSONArray(json);
            for (int k = 0; k < arr.length(); k++) {
                JSONObject o = arr.getJSONObject(k);
                long at = o.getLong("at");
                if (at <= now + 2000) continue;
                String id = o.getString("id");
                PendingIntent pi = pendingWithData(c, id, o.optString("title", "রিমাইন্ডার"), o.optString("body", ""));
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                }
                kept.put(new JSONObject().put("id", id));
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString("sched", kept.toString()).apply();
    }
}
