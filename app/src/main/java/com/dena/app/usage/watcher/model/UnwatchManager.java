/*
 * UnwatchManager.java
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

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.List;

public class UnwatchManager {

    public UnwatchManager(Context context) {
        mContext = context;
        mSharedPref = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void addUnwatch(String packageName) {
        String packageNames = mSharedPref.getString(UNWATCH, "") ;
        mSharedPref.edit().putString(UNWATCH, packageNames + packageName + ",").commit();
    }

    public List<UnwatchItem> getUnwatchList() {
        String packageNames[] = mSharedPref.getString(UNWATCH, "").split(",");
        List<UnwatchItem> result = new ArrayList<>();
        for (int i = 0; i < packageNames.length; i++) {
            if (0 < packageNames[i].length()) {
                result.add(new UnwatchItem(mContext, packageNames[i]));
            }
        }
        return result;
    }

    public boolean isUnwatched(String packageName) {
        String packageNames = mSharedPref.getString(UNWATCH, "");
        return (0 <= packageNames.indexOf(packageName));
    }

    public void removeUnwatch(String packageName) {
        String packageNames[] = mSharedPref.getString(UNWATCH, "").split(",");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < packageNames.length; i++) {
            if (!packageName.equals(packageNames[i])) {
                builder.append(packageNames[i]);
                builder.append(",");
            }
        }
        mSharedPref.edit().putString(UNWATCH, builder.toString()).commit();
    }

    private static final String UNWATCH = "UNWATCH";
    private Context mContext;
    private SharedPreferences mSharedPref;
}
