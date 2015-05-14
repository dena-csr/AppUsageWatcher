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
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.WindowManager;

import java.util.Timer;
import java.util.TimerTask;

import com.dena.app.usage.watcher.MainActivity;
import com.dena.app.usage.watcher.App;
import com.dena.app.usage.watcher.R;
import com.dena.app.usage.watcher.model.WatchDatabase;
import com.dena.app.usage.watcher.util.WatchUtil;

public class WatchService extends Service {
    public static class DialogActivity extends Activity {
        protected void onCreate(Bundle bundle) {
            super.onCreate(bundle);
            if (showFlag)  {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.app_name));
                builder.setMessage(getString(R.string.please_allow_access_history));
                builder.setPositiveButton(getString(R.string.positive), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int i) {
                        finish();
                        Intent intent = new Intent("android.settings.USAGE_ACCESS_SETTINGS");
                        startActivity(intent);
                    }
                });
                builder.setNegativeButton(getString(R.string.negative), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int i) {
                        finish();
                    }
                });
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface d) {
                        finish();
                    }
                });
                builder.setCancelable(true);
                builder.create().show();
                showFlag = false;
                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        DialogActivity.showFlag = true;
                    }
                }, 30000L);
            } else {
                finish();
            }
        }
        static boolean showFlag = true;
    }

    public WatchService() {
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle(getString(R.string.app_name));
        builder.setContentText(getString(R.string.notify_watching));
        builder.setTicker(getString(R.string.notify_watching));
        builder.setOngoing(true);
        builder.setPriority(NotificationCompat.PRIORITY_MIN);
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        builder.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT));
        return builder.build();
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        super.onCreate();
        try {
            final PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
            mTimer = new Timer(true);
            mTimer.scheduleAtFixedRate(new TimerTask() {
                @TargetApi(Build.VERSION_CODES.KITKAT)
                public void run() {
                    if (WatchUtil.isScreenLocked(WatchService.this)) {
                        return;
                    }
                    try {
                        WatchDatabase db = ((App) getApplication()).getDatabase();
                        if (Build.VERSION_CODES.LOLLIPOP <= Build.VERSION.SDK_INT) {
                            AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
                            if (null != appOpsManager && AppOpsManager.MODE_ALLOWED != appOpsManager.checkOp(AppOpsManager.OPSTR_GET_USAGE_STATS, packageInfo.applicationInfo.uid, getPackageName())) {
                                Intent intent = new Intent(getApplicationContext(), DialogActivity.class);
                                intent.addFlags(WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE |
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                                startActivity(intent);
                                return;
                            }
                        }
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
            startForeground(R.mipmap.ic_launcher, buildNotification());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        mTimer.cancel();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        DialogActivity.showFlag = true;
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
