/*
 * BaseService.java
 *
 * Copyright (c) 2019 DeNA Co., Ltd.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.dena.app.usage.watcher.service;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.WindowManager;

import com.dena.app.usage.watcher.MainActivity;
import com.dena.app.usage.watcher.R;

public abstract class BaseService extends Service {

    public abstract String getNotificationChannelId();
    public abstract String getNotificationChannelName();
    public abstract String getNotificationMessage();

    public void onCreate() {
        super.onCreate();
        startForeground(R.mipmap.ic_launcher, buildNotification());
    }

    @TargetApi(Build.VERSION_CODES.O)
    public int getLayoutParamsType() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_PHONE;
        } else {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private Notification.Builder createNotificationBuilder() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return new Notification.Builder(this);
        }
        NotificationManager manager = (NotificationManager) getApplicationContext()
			.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(getNotificationChannelId(),
                                                              getNotificationChannelName(),
                                                              NotificationManager.IMPORTANCE_DEFAULT);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
        return new Notification.Builder(this, getNotificationChannelId());
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private Notification buildNotification() {
        Notification.Builder builder = createNotificationBuilder();
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle(getString(R.string.app_name));
        builder.setContentText(getNotificationMessage());
        builder.setTicker(getNotificationMessage());
        builder.setOngoing(true);
        if (Build.VERSION_CODES.JELLY_BEAN < Build.VERSION.SDK_INT) {
            builder.setPriority(Notification.PRIORITY_DEFAULT);
        }
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        builder.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
        return builder.build();
    }
}
