/*
 * TimerService.java
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

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.dena.app.usage.watcher.App;
import com.dena.app.usage.watcher.R;
import com.dena.app.usage.watcher.model.WatchDatabase;
import com.dena.app.usage.watcher.util.WatchUtil;

public class TimerService extends BaseService implements View.OnTouchListener {

    public TimerService() {
        mHandler = new Handler();
        mRemained = 0;
    }

    public String getNotificationChannelId() {
        return "channel_id_timer";
    }
    public String getNotificationChannelName() {
        return getString(R.string.channel_timer);
    }
    public String getNotificationMessage() {
        return getString(R.string.notify_timer);
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");
        mView = LayoutInflater.from(this).inflate(R.layout.dialog_timer, null);
        mView.setOnTouchListener(this);
        mParams = new WindowManager.LayoutParams(-2, -2,
                                                 getLayoutParamsType(),
                                                 getLayoutParamsFlag(),
                                                 PixelFormat.TRANSLUCENT);
        mWindowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.addView(mView, mParams);
    }

    public void onDestroy()
    {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mWindowManager.removeView(mView);
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        mRemained = 60 * intent.getIntExtra(MINUTE, 0);
        final String packageName = intent.getStringExtra(PACKAGE_NAME);
        final int timerLevel = intent.getIntExtra(TIMER_LEVEL, WatchDatabase.TIMER_LEVEL_FINISH);
        final WatchDatabase db = ((App)getApplication()).getDatabase();
        final TextView textTimer = (TextView)mView.findViewById(R.id.text_timer);
        textTimer.setText(String.valueOf(mRemained));
        mHandler.post(new Runnable() {
            public void run() {
                if (mRemained <= 0 || 0 == db.getTimer(packageName, timerLevel)) {
                    mHandler.removeCallbacks(this);
                    stopSelf();
                } else {
                    String topPackageName = WatchUtil.getTopPackageName(TimerService.this);
                    if (null == topPackageName || packageName.equals(topPackageName) ||
                        packageName.equals(WatchUtil.PACKAGE_NAME_ALL)) {
                        textTimer.setText(String.valueOf(mRemained));
                        mHandler.postDelayed(this, 1000L);
                        mRemained--;
                    } else {
                        mHandler.removeCallbacks(this);
                        stopSelf();
                    }
                }
            }
        });
        mWindowManager.updateViewLayout(mView, mParams);
        return START_NOT_STICKY;
    }

    private int getLayoutParamsFlag() {
        return (WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    }

    public boolean onTouch(View view, MotionEvent motionEvent) {
        int x = (int)motionEvent.getRawX();
        int y = (int)motionEvent.getRawY();
        switch (motionEvent.getAction()) {
        case MotionEvent.ACTION_MOVE:
            int dx = mOffsetX - x;
            int dy = mOffsetY - y;
            mCurrentX -= dx;
            mCurrentY -= dy;
            mParams = new WindowManager.LayoutParams(-2, -2, mCurrentX, mCurrentY,
                                                     getLayoutParamsType(),
                                                     getLayoutParamsFlag(),
                                                     PixelFormat.TRANSLUCENT);
            mOffsetX = x;
            mOffsetY = y;
            mWindowManager.updateViewLayout(mView, mParams);
            break;
        case MotionEvent.ACTION_DOWN:
            mOffsetX = x;
            mOffsetY = y;
            break;
        default:
            break;
        }
        return true;
    }

    public static final String PACKAGE_NAME = "PACKAGE_NAME";
    public static final String TIMER_LEVEL = "TIMER_LEVEL";
    public static final String MINUTE = "MINUTE";

    private final String TAG = getClass().getSimpleName();
    private int mCurrentX;
    private int mCurrentY;
    private Handler mHandler;
    private int mOffsetX;
    private int mOffsetY;
    private View mView;
    private WindowManager.LayoutParams mParams;
    private volatile int mRemained;
    private WindowManager mWindowManager;
}
