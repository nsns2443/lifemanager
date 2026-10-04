package com.azharul.lifemanager;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

/** হোম স্ক্রিন উইজেট: আজকের কাজ ও সংক্ষিপ্ত তথ্য (অ্যাপ সর্বশেষ যা জমা দিয়েছে তা দেখায়) */
public class TaskWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) {
            m.updateAppWidget(id, build(c));
        }
    }

    static RemoteViews build(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        String title = "📌 আজকের কাজ";
        String footer = "ট্যাপ করলে অ্যাপ খুলবে";
        String[] lines = new String[]{"", "", "", "", ""};
        String json = c.getSharedPreferences("widget", Context.MODE_PRIVATE).getString("json", "");
        if (json.isEmpty()) {
            lines[0] = "অ্যাপ একবার খুললে এখানে কাজ দেখাবে";
        } else {
            try {
                JSONObject o = new JSONObject(json);
                title = o.optString("title", title);
                footer = o.optString("footer", footer);
                JSONArray arr = o.optJSONArray("lines");
                if (arr != null) {
                    for (int i = 0; i < 5 && i < arr.length(); i++) lines[i] = arr.optString(i, "");
                }
            } catch (Exception e) {
                lines[0] = "তথ্য পড়া যায়নি, অ্যাপ খুলুন";
            }
        }
        v.setTextViewText(R.id.title, title);
        v.setTextViewText(R.id.l1, lines[0]);
        v.setTextViewText(R.id.l2, lines[1]);
        v.setTextViewText(R.id.l3, lines[2]);
        v.setTextViewText(R.id.l4, lines[3]);
        v.setTextViewText(R.id.l5, lines[4]);
        v.setTextViewText(R.id.footer, footer);
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.root, pi);
        return v;
    }

    static void save(Context c, String json) {
        c.getSharedPreferences("widget", Context.MODE_PRIVATE).edit().putString("json", json).apply();
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, TaskWidget.class));
        for (int id : ids) {
            m.updateAppWidget(id, build(c));
        }
    }
}
