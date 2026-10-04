package com.azharul.lifemanager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** ফোন রিস্টার্ট বা অ্যাপ আপডেটের পর অ্যালার্মগুলো আবার বসায় */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Reminders.schedule(c);
    }
}
