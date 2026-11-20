/*
 * 应用空间助手
 * Copyright (C) 2026 AYUAN
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dsh.appspace;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Keeps the app process in the foreground while the accessibility service drives
 * system Settings. ColorOS aggressively trims background apps when the launcher
 * activity is no longer visible; a foreground service makes the operation far
 * less likely to be interrupted halfway through.
 */
public final class AutomationForegroundService extends Service {
    private static final String TAG = "AppSpaceKeepAlive";
    private static final String CHANNEL_ID = "automation_keepalive";
    private static final int NOTIFICATION_ID = 4101;
    private static volatile boolean active;

    static boolean start(Context context) {
        try {
            Intent intent = new Intent(context, AutomationForegroundService.class);
            context.startForegroundService(intent);
            return true;
        } catch (RuntimeException e) {
            Log.w(TAG, "unable to start foreground keeper", e);
            return false;
        }
    }

    static void stop(Context context) {
        try {
            context.stopService(new Intent(context, AutomationForegroundService.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "unable to stop foreground keeper", e);
        }
    }

    static boolean isActive() {
        return active;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            active = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "unable to enter foreground", e);
            active = false;
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!active) {
            // startForeground() failed above; do not leave an unbound service behind.
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        active = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "自动处理保活", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("在逐个处理应用设置时保持应用运行，避免系统中途停止");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("应用空间助手正在运行")
                .setContentText("正在逐个处理应用，请保持屏幕解锁")
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setShowWhen(false)
                .setColor(0xFF4D8DFF)
                .build();
    }
}
