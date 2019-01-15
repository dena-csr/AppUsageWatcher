/*
 * AlertService.java
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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.dena.app.usage.watcher.model.WatchDatabase;
import com.dena.app.usage.watcher.util.WatchUtil;
import com.dena.app.usage.watcher.R;

public class AlertService extends BaseService {

    public AlertService() {
    }

    public String getNotificationChannelId() {
        return "channel_id_alert";
    }
    public String getNotificationChannelName() {
        return getString(R.string.channel_alert);
    }
    public String getNotificationMessage() {
        return getString(R.string.notify_alerting);
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mView = LayoutInflater.from(this).inflate(R.layout.dialog_alert, null);
        mParams = new WindowManager.LayoutParams(-2, -2,
                                                 getLayoutParamsType(),
                                                 WindowManager.LayoutParams.FLAG_DIM_BEHIND,
                                                 PixelFormat.TRANSLUCENT);
        mParams.dimAmount = 0.8F;
        mWindowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.addView(mView, mParams);
        mPlayer = new MediaPlayer();
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mWindowManager.removeView(mView);
        mPlayer.stop();
        mPlayer.release();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void startTimerService(String packageName, int timerLevel, int minute) {
        Intent intent = new Intent(getApplicationContext(), TimerService.class);
        intent.putExtra(TimerService.PACKAGE_NAME, packageName);
        intent.putExtra(TimerService.TIMER_LEVEL, timerLevel);
        intent.putExtra(TimerService.MINUTE, minute);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startService(intent);
        } else {
            startForegroundService(intent);
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        TextView textview = (TextView)mView.findViewById(R.id.textAlert);
        ImageView imageInfo = (ImageView)mView.findViewById(R.id.imageInfo);
        ImageView imageWarning = (ImageView)mView.findViewById(R.id.imageWarning);
        ImageView imageFinish = (ImageView)mView.findViewById(R.id.imageFinish);
        Button button = (Button)mView.findViewById(R.id.buttonDismiss);
        final String packageName = intent.getStringExtra(PACKAGE_NAME);
        final int timerLevel = intent.getIntExtra(TIMER_LEVEL, WatchDatabase.TIMER_LEVEL_FINISH);
        final int lastMinute = intent.getIntExtra(LAST_MINUTE, 0);
        final int nextMinute = intent.getIntExtra(NEXT_MINUTE, 0);
        int alertId = 0;
        switch (timerLevel) {
        case WatchDatabase.TIMER_LEVEL_INFO:
            alertId = R.string.alert_info;
            imageInfo.setVisibility(View.VISIBLE);
            imageWarning.setVisibility(View.GONE);
            imageFinish.setVisibility(View.GONE);
            break;
        case WatchDatabase.TIMER_LEVEL_WARNING:
            alertId = R.string.alert_warning;
            imageInfo.setVisibility(View.GONE);
            imageWarning.setVisibility(View.VISIBLE);
            imageFinish.setVisibility(View.GONE);
            break;
        case WatchDatabase.TIMER_LEVEL_FINISH:
        default:
            alertId = R.string.alert_finish;
            imageInfo.setVisibility(View.GONE);
            imageWarning.setVisibility(View.GONE);
            imageFinish.setVisibility(View.VISIBLE);
        }
        textview.setText(getString(alertId, WatchUtil.searchAppName(this, packageName), lastMinute));
        final int minute = Math.max(1, nextMinute - lastMinute);
        button.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(View view) {
                if (0 < minute) {
                    startTimerService(packageName, timerLevel, minute);
                }
                stopSelf();
            }
        });
        mWindowManager.updateViewLayout(mView, mParams);
        playSound(timerLevel);
        return START_NOT_STICKY;
    }

    private void playSound(int timerLevel) {
        if (mSharedPref.getBoolean("preference_vibration", false)) {
            final Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(500);
        }
        if (mSharedPref.getBoolean("preference_sound", false)) {
            if (null != mPlayer && !mPlayer.isPlaying()) {
                try {
                    String soundName = null;
                    switch (timerLevel) {
                        case WatchDatabase.TIMER_LEVEL_INFO:
                            soundName = "notification.m4a";
                            break;
                        case WatchDatabase.TIMER_LEVEL_WARNING:
                            soundName = "notification.m4a";
                            break;
                        case WatchDatabase.TIMER_LEVEL_FINISH:
                        default:
                            soundName = "notification.m4a";
                    }
                    AssetFileDescriptor afd = getAssets().openFd(soundName);
                    mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    mPlayer.prepare();
                    mPlayer.start();
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        }
    }

    public static final String PACKAGE_NAME = "PACKAGE_NAME";
    public static final String TIMER_LEVEL = "TIMER_LEVEL";
    public static final String LAST_MINUTE = "LAST_MINUTE";
    public static final String NEXT_MINUTE = "NEXT_MINUTE";
    private final String TAG = getClass().getSimpleName();
    private View mView;
    private WindowManager.LayoutParams mParams;
    private WindowManager mWindowManager;
    private MediaPlayer mPlayer;
    private SharedPreferences mSharedPref;
}
