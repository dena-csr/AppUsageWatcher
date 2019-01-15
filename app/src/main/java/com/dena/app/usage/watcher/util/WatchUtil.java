/*
 * WatchUtil.java
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

package com.dena.app.usage.watcher.util;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.PowerManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.dena.app.usage.watcher.R;

public class WatchUtil {

    private static Bitmap scaleBitmap(Bitmap bitmap, float size) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        float scale = Math.max(size/(float)w, size/(float)h);
        int s = Math.min(w, h);
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        return Bitmap.createBitmap(bitmap, (w-s)/2, (h-s)/2, s, s, matrix, true);
    }

    private static Drawable adjustDrawable(Context context, Drawable drawable) {
        Bitmap bitmap;
        Bitmap bitmap1;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable)drawable).getBitmap();
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }
        bitmap1 = scaleBitmap(bitmap, context.getResources().getDimension(R.dimen.icon_size));
        return new BitmapDrawable(context.getResources(), bitmap1);
    }

    public static Drawable searchAppIcon(Context context, String packageName) {
        try {
            if (packageName.equals(PACKAGE_NAME_ALL)) {
                return context.getResources().getDrawable(R.mipmap.ic_launcher);
            }
            PackageManager packageManager = context.getPackageManager();
            Drawable drawable = packageManager.getApplicationInfo(packageName, 0).loadIcon(packageManager);
            return adjustDrawable(context, drawable);
        } catch (Exception e) {
        }
        return context.getResources().getDrawable(R.mipmap.ic_launcher);
    }

    public static String searchAppName(Context context, String packageName) {
        try {
            if (packageName.equals(PACKAGE_NAME_ALL)) {
                return context.getString(R.string.package_name_all);
            }
            PackageManager packageManager = context.getPackageManager();
            return packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString();
        } catch (Exception e) {
        }
        return context.getString(R.string.package_name_unknown);
    }
    
    public static boolean isRunningService(Context context, String className) {
        ActivityManager manager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (className.equals(info.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    private static String getTopPackageNameBeforeLollipop(Context context) {
        ActivityManager activityManager = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        return activityManager.getRunningTasks(1).get(0).topActivity.getPackageName();
    }

    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    private static String getTopPackageNameAfterLollipop(Context context) {
        PowerManager powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        UsageStatsManager usageStatsManager = (UsageStatsManager)context.getSystemService(USAGE_STATS);
        if (!powerManager.isInteractive()) {
            sForegroundMap.clear();
            return null;
        }
        long time = System.currentTimeMillis();
        UsageEvents usageEvents = usageStatsManager.queryEvents(time-5000, time);
        if (null == usageEvents) {
            return null;
        }
        String packageName = null;
        while (usageEvents.hasNextEvent()) {
            UsageEvents.Event event = new UsageEvents.Event();
            usageEvents.getNextEvent(event);
            packageName = event.getPackageName();
            switch (event.getEventType()) {
            case UsageEvents.Event.MOVE_TO_FOREGROUND:
                sForegroundMap.put(packageName, Long.valueOf(event.getTimeStamp()));
                break;
            case UsageEvents.Event.MOVE_TO_BACKGROUND:
                sForegroundMap.remove(packageName);
                break;
            }
        }
        long latest = 0;
        for (Map.Entry<String, Long> entry : sForegroundMap.entrySet()) {
            long timestamp = entry.getValue().longValue();
            if (latest < timestamp) {
                packageName = entry.getKey();
                latest = timestamp;
            }
        }
        return packageName;
    }
    
    public static String getTopPackageName(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return getTopPackageNameBeforeLollipop(context);
        } else {
            return getTopPackageNameAfterLollipop(context);
        }
    }

    public static boolean isScreenLocked(Context context) {
        KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (null != km && km.inKeyguardRestrictedInputMode()) {
            return true;
        }
        return false;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public static boolean canAccessUsageStat(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return true;
        }
        try {
            String packageName = context.getPackageName();
            PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_META_DATA);
            AppOpsManager appOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            return AppOpsManager.MODE_ALLOWED == appOpsManager.checkOp(AppOpsManager.OPSTR_GET_USAGE_STATS, packageInfo.applicationInfo.uid, packageName);
        } catch (Throwable t) {
        }
        return false;
    }

    private static final String USAGE_STATS = "usagestats";
    private static final Map<String,Long> sForegroundMap = new HashMap<String,Long>();

    public static final String PACKAGE_NAME_ALL = "ALL";
}
