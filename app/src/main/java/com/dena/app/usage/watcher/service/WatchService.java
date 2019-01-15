/*
 * WatchService.java
 *
 * Copyright (c) 2015 DeNA Co., Ltd.
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
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

import com.dena.app.usage.watcher.App;
import com.dena.app.usage.watcher.R;
import com.dena.app.usage.watcher.model.WatchDatabase;
import com.dena.app.usage.watcher.util.WatchUtil;

public class WatchService extends BaseService {
    public WatchService() {
    }

    public String getNotificationChannelId() {
        return "channel_id_watch";
    }
    public String getNotificationChannelName() {
        return getString(R.string.channel_watch);
    }
    public String getNotificationMessage() {
        return getString(R.string.notify_watching);
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        super.onCreate();
        try {
            mTimer = new Timer(true);
            mTimer.scheduleAtFixedRate(new TimerTask() {
                @TargetApi(Build.VERSION_CODES.KITKAT)
                public void run() {
                    if (WatchUtil.isScreenLocked(WatchService.this)) {
                        return;
                    }
                    try {
                        WatchDatabase db = ((App) getApplication()).getDatabase();
                        long time = System.currentTimeMillis();
                        String packageName = WatchUtil.getTopPackageName(WatchService.this);
                        if (null != packageName) {
                            db.addUsageAt(time, packageName);
                            db.addUsageAt(time);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            }, 0L, 1000L);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        mTimer.cancel();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        sendBroadcast(WATCH_START);
        return START_STICKY;
    }

    private void sendBroadcast(String message) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.putExtra(WATCH_MESSAGE, message);
        broadcastIntent.setAction(WATCH_ACTION);
        getBaseContext().sendBroadcast(broadcastIntent);
    }

    public static final String WATCH_MESSAGE = "WATCH_MESSAGE";
    public static final String WATCH_ACTION = "WATCH_ACTION";
    public static final String WATCH_START  = "WATCH_START";

    private Timer mTimer;
    private final String TAG = getClass().getSimpleName();
}
