/*
 * WatchDatabase.java
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

package com.dena.app.usage.watcher.model;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.dena.app.usage.watcher.service.AlertAllService;
import com.dena.app.usage.watcher.service.AlertService;
import com.dena.app.usage.watcher.service.TimerService;
import com.dena.app.usage.watcher.util.WatchUtil;

public class WatchDatabase extends SQLiteOpenHelper {

    public WatchDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        mDB = getWritableDatabase();
        mContext = context;
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        mUnwatchManager = new UnwatchManager(context);
    }

    private String createTable(Calendar calendar) {
        String tableName = makeTableName(calendar);
        String query = "CREATE TABLE IF NOT EXISTS " + tableName + " (_id integer primary key autoincrement, packageName text, sec integer);";
        mDB.execSQL(query);
        return tableName;
    }

    private void dropTable(Calendar calendar) {
        String tableName = makeTableName(calendar);
        String query = "DROP TABLE " + tableName + ";";
        mDB.execSQL(query);
    }

    private String makeTableName(Calendar calendar) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
        return PREFIX_USAGE + sdf.format(calendar.getTime());
    }

    private int insertOrUpdateUsageAt(long timeInMillis, String packageName) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        String tableName = createTable(calendar);
        Cursor cursor = null;
        int sec = 1;
        try {
            String[] hints = {packageName};
            cursor = mDB.query(tableName, null, "packageName = ?", hints, null, null, null);
            if (cursor.moveToFirst()) {
                sec += cursor.getInt(2);
                ContentValues values = new ContentValues();
                values.put("packageName", packageName);
                values.put("sec", sec);
                mDB.update(tableName, values, "packageName = ?", hints);
            } else {
                ContentValues values = new ContentValues();
                values.put("packageName", packageName);
                values.put("sec", sec);
                mDB.insert(tableName, null, values);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        } finally {
            if (null != cursor) {
                cursor.close();
            }
        }
        return sec;
    }
    
    @TargetApi(Build.VERSION_CODES.O)
    private void startAlertService(Class alertServiceClass, String packageName, int timerLevel, int lastMinute, int nextMinute) {
        if (!WatchUtil.isRunningService(mContext, alertServiceClass.getName())) {
            Intent intent = new Intent(mContext.getApplicationContext(), alertServiceClass);
            intent.putExtra(AlertService.PACKAGE_NAME, packageName);
            intent.putExtra(AlertService.TIMER_LEVEL, timerLevel);
            intent.putExtra(AlertService.LAST_MINUTE, lastMinute);
            intent.putExtra(AlertService.NEXT_MINUTE, nextMinute);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                mContext.startService(intent);
            } else {
                mContext.startForegroundService(intent);
            }
        }
    }
    private void stopAlertService(Class alertServiceClass) {
        if (WatchUtil.isRunningService(mContext, alertServiceClass.getName()))  {
            mContext.stopService(new Intent(mContext.getApplicationContext(), alertServiceClass));
        }
    }

    public void addUsageAt(long timeInMillis) {
        addUsageAt(timeInMillis, WatchUtil.PACKAGE_NAME_ALL, AlertAllService.class);
    }
    public void addUsageAt(long timeInMillis, String packageName) {
        addUsageAt(timeInMillis, packageName, AlertService.class);
    }

    private void addUsageAt(long timeInMillis, String packageName, Class alertServiceClass) {
        int usage = insertOrUpdateUsageAt(timeInMillis, packageName);
        if (0 < usage && !mUnwatchManager.isUnwatched(packageName)) {
            int timerInfo = getTimerInfo(packageName);
            int timerWarning = getTimerWarning(packageName);
            int timerFinish = getTimerFinish(packageName);
            if (!WatchUtil.isRunningService(mContext, TimerService.class.getName())) {
                if (0 != timerFinish && timerFinish*60 < usage) {
                    startAlertService(alertServiceClass, packageName, TIMER_LEVEL_FINISH, usage/60, timerFinish+1);
                } else if (0 != timerWarning && timerWarning*60 < usage) {
                    startAlertService(alertServiceClass, packageName, TIMER_LEVEL_WARNING, usage/60, timerFinish);
                } else if (0 != timerInfo && timerInfo*60 < usage) {
                    startAlertService(alertServiceClass, packageName, TIMER_LEVEL_INFO, usage/60, timerWarning);
                } else {
                    stopAlertService(alertServiceClass);
                }
            }
        }
    }

    private boolean getUsage(String tableName, Map<String,WatchItem> map) {
        Cursor cursor = null;
        try {
            cursor = mDB.query(tableName, null, null, null, null, null, null);
            if (cursor.moveToFirst()) {
                do {
                    String packageName = cursor.getString(1);
                    int sec = cursor.getInt(2);
                    WatchItem item = map.get(packageName);
                    if (null != item) {
                        item.addSec(sec);
                    } else {
                        item = new WatchItem(mContext, packageName, sec);
                        map.put(packageName, item);
                    }
                } while(cursor.moveToNext());
            }
            return true;
        } catch (Exception ex) {
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return false;
    }

    public List<WatchItem> getUsage(Calendar calendar) {
        Map<String,WatchItem> map = new HashMap<>();
        if (!getUsage(makeTableName(calendar), map)) {
            return new ArrayList<>();
        }
        List<WatchItem> list = new ArrayList<>(map.values());
        Collections.sort(list, new WatchItemComparator());
        return list;
    }

    public List<WatchItem> getTotalUsage() {
        List<String> tableNames = new ArrayList<>();
        Cursor cursor = mDB.rawQuery("SELECT * FROM sqlite_master WHERE type='table' ", null);
        if (cursor.moveToFirst()) {
            do {
                String tableName = cursor.getString(1);
                if (tableName.startsWith(PREFIX_USAGE)) {
                    tableNames.add(tableName);
                }
            } while(cursor.moveToNext());
        }
        cursor.close();

        Map<String,WatchItem> map = new HashMap<>();
        for (String tableName : tableNames) {
            getUsage(tableName, map);
        }
        List<WatchItem> list = new ArrayList<>(map.values());
        Collections.sort(list, new WatchItemComparator());
        return list;
    }

    public void onCreate(SQLiteDatabase db) {
        mDB = db;
    }

    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        mDB = db;
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }

    public int getTimer(String packageName, int level) {
        return mSharedPref.getInt(packageName + ".level" + level, 0);
    }
    public int getTimerInfo(String packageName) {
        return getTimer(packageName, TIMER_LEVEL_INFO);
    }
    public int getTimerWarning(String packageName) {
        return getTimer(packageName, TIMER_LEVEL_WARNING);
    }
    public int getTimerFinish(String packageName) {
        return getTimer(packageName, TIMER_LEVEL_FINISH);
    }
    public void setTimer(String packageName, int level, int minute) {
        mSharedPref.edit().putInt(packageName + ".level" + level, minute).commit();
    }
    public void setTimerInfo(String packageName, int minute) {
        setTimer(packageName, TIMER_LEVEL_INFO, minute);
    }
    public void setTimerWarning(String packageName, int minute) {
        setTimer(packageName, TIMER_LEVEL_WARNING, minute);
    }
    public void setTimerFinish(String packageName, int minute) {
        setTimer(packageName, TIMER_LEVEL_FINISH, minute);
    }

    private static final String DB_NAME = "watch.db";
    private static final int DB_VERSION = 1;

    private static final String PREFIX_USAGE = "usage_";

    private final String TAG = getClass().getSimpleName();

    private Context mContext;
    private SQLiteDatabase mDB;
    private SharedPreferences mSharedPref;
    private UnwatchManager mUnwatchManager;

    public static final int TIMER_LEVEL_INFO = 1;
    public static final int TIMER_LEVEL_WARNING = 2;
    public static final int TIMER_LEVEL_FINISH = 3;
}
