package com.azharul.lifemanager;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

/** নির্ধারিত সময়ে রিংসহ নোটিফিকেশন দেখায় (অ্যাপ বন্ধ থাকলেও) */
public class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL = "reminders_v1";

    @Override
    public void onReceive(Context c, Intent intent) {
        String id = intent.getStringExtra("id");
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        if (id == null) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        long[] vib = new long[]{0, 600, 300, 600, 300, 600};

        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "কাজের রিমাইন্ডার", NotificationManager.IMPORTANCE_HIGH);
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            ch.setSound(sound, aa);
            ch.enableVibration(true);
            ch.setVibrationPattern(vib);
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
            b = new Notification.Builder(c, CHANNEL);
            b.setTimeoutAfter(60000);
        } else {
            b = new Notification.Builder(c);
            b.setSound(sound, android.media.AudioManager.STREAM_ALARM);
            b.setVibrate(vib);
            b.setPriority(Notification.PRIORITY_MAX);
        }
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title == null ? "রিমাইন্ডার" : title)
                .setContentText(body == null ? "" : body)
                .setStyle(new Notification.BigTextStyle().bigText(body == null ? "" : body))
                .setCategory(Notification.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(content);
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT;   // ক্লিক/সরানো পর্যন্ত রিং বারবার বাজে (সর্বোচ্চ ১ মিনিট)
        nm.notify(id.hashCode(), n);
    }
}
