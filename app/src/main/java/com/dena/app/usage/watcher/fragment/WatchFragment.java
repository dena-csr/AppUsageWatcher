/*
 * WatchFragment.java
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

package com.dena.app.usage.watcher.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.OvershootInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.dena.app.usage.watcher.App;
import com.dena.app.usage.watcher.R;
import com.dena.app.usage.watcher.model.WatchItem;
import com.dena.app.usage.watcher.model.UnwatchManager;
import com.dena.app.usage.watcher.model.WatchDatabase;
import com.dena.app.usage.watcher.util.WatchUtil;
import com.dena.app.usage.watcher.util.StringUtil;

public class WatchFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener, AdapterView.OnItemClickListener {

    public static WatchFragment newInstance(boolean isTotal) {
        WatchFragment fragment = new WatchFragment();
        Bundle bundle = new Bundle();
        bundle.putBoolean("isTotal", isTotal);
        fragment.setArguments(bundle);
        return fragment;
    }

    public WatchFragment() {
        mHandler = new Handler();
    }

    public View onCreateView(LayoutInflater layoutinflater, ViewGroup viewgroup, Bundle bundle) {
        mIsTotal = getArguments().getBoolean("isTotal");
        View view = layoutinflater.inflate(R.layout.fragment_watch, null);
        mDB = ((App)getActivity().getApplication()).getDatabase();
        mSwipeRefreshLayout = (SwipeRefreshLayout)view.findViewById(R.id.swipe_refresh_layout);
        ListView listView = (ListView)view.findViewById(R.id.listView);
        mAdapter = new WatchAdapter(getActivity(), R.layout.item_watch, new ArrayList(), mDB);
        listView.setAdapter(mAdapter);
        mSwipeRefreshLayout.setOnRefreshListener(this);
        listView.setOnItemClickListener(this);
        onRefresh();
        return view;
    }

    public void onResume() {
        super.onResume();
        getActivity().invalidateOptionsMenu();
    }

    private void showSetTimerDialog(final WatchItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(item.getAppName());
        View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_set_timer, null);
        final TextView editInfo = (TextView)view.findViewById(R.id.editInfoTime);
        editInfo.setText(Integer.toString(mDB.getTimerInfo(item.getPackageName())));
        final TextView editWarning = (TextView)view.findViewById(R.id.editWarningTime);
        editWarning.setText(Integer.toString(mDB.getTimerWarning(item.getPackageName())));
        final TextView editFinish = (TextView)view.findViewById(R.id.editFinishTime);
        editFinish.setText(Integer.toString(mDB.getTimerFinish(item.getPackageName())));
        builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                mDB.setTimerInfo(item.getPackageName(), StringUtil.parseInt(editInfo.getText().toString(), 0));
                mDB.setTimerWarning(item.getPackageName(), StringUtil.parseInt(editWarning.getText().toString(), 0));
                mDB.setTimerFinish(item.getPackageName(), StringUtil.parseInt(editFinish.getText().toString(), 0));
                onRefresh();
            }
        });
        builder.setNegativeButton(getString(R.string.cancel), null);
        AlertDialog dialog = builder.create();
        dialog.setView((view));
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }
    
    public void onItemClick(AdapterView adapterview, View view, int i, long l) {
        final WatchItem item = (WatchItem)adapterview.getItemAtPosition(i);
        final Intent intent = getActivity().getPackageManager().getLaunchIntentForPackage(item.getPackageName());
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(item.getAppName());
        List<String> list = new ArrayList<>();
        list.add(getString(R.string.set_usage_limit));
        list.add(getString(R.string.reset_usage_limit));
        list.add(getString(R.string.hide_this_app));
        if (null != intent) {
            list.add(getString(R.string.launch_this_app));
        }
        builder.setItems(list.toArray(new String[0]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialoginterface, int j) {
                switch (j) {
                case 0:
                    showSetTimerDialog(item);
                    break;
                case 1:
                    mDB.setTimerInfo(item.getPackageName(), 0);
                    mDB.setTimerWarning(item.getPackageName(), 0);
                    mDB.setTimerFinish(item.getPackageName(), 0);
                    onRefresh();
                    break;
                case 2:
                    (new UnwatchManager(getActivity())).addUnwatch(item.getPackageName());
                    onRefresh();
                    break;
                case 3:
                    if (null != intent) {
                        startActivity(intent);
                    }
                    break;
                }
            }
        });
        AlertDialog alertdialog = builder.create();
        alertdialog.setCancelable(true);
        alertdialog.setCanceledOnTouchOutside(true);
        alertdialog.show();
    }
    
    public void onRefresh() {
        if (!mSwipeRefreshLayout.isRefreshing()) {
            mSwipeRefreshLayout.setRefreshing(true);
        }
        mHandler.postDelayed(new Runnable() {
            public void run() {
                updateAdapter(new OnRefreshedListener() {
                    public void onRefreshed() {
                        mHandler.postDelayed(new Runnable() {
                            public void run() {
                                mSwipeRefreshLayout.setRefreshing(false);
                            }
                        }, 500L);
                    }
                });
            }
         }, 500L);
    }

    private void updateAdapter(final OnRefreshedListener listener) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(System.currentTimeMillis());
        (new AsyncTask<Void,Void,List<WatchItem>>() {
            protected List<WatchItem> doInBackground(Void... avoid) {
                return (mIsTotal) ? mDB.getTotalUsage() : mDB.getUsage(calendar);
            }

            protected void onPostExecute(List<WatchItem> list) {
                mAdapter.clear();
                FragmentActivity fragmentactivity = getActivity();
                if (fragmentactivity == null) {
                    mAdapter.addAll(list);
                } else {
                    UnwatchManager unwatchManager = new UnwatchManager(fragmentactivity);
                    for (WatchItem item : list) {
                        if (!unwatchManager.isUnwatched(item.getPackageName())) {
                            mAdapter.add(item);
                        }
                    }
                }
                listener.onRefreshed();
            }
        }).execute();
    }
    
    private Handler mHandler;
    private WatchDatabase mDB;
    private boolean mIsTotal;
    private WatchAdapter mAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    private interface OnRefreshedListener {
        void onRefreshed();
    }

    private static class WatchAdapter extends ArrayAdapter {
        private static class ViewHolder {
            ImageView mImageAppIcon;
            TextView mTextAppName;
            TextView mTextAppSec;
            ImageView mImageTimerOn;
            ImageView mImageTimerOff;
        }

        public WatchAdapter(Context context, int resourceId, List list, WatchDatabase database) {
            super(context, resourceId, list);
            mResourceId = resourceId;
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mDB = database;
        }

        public View getView(int i, View view, ViewGroup viewgroup) {
            WatchItem item = (WatchItem)getItem(i);
            ViewHolder holder;
            if (null == view) {
                view = mInflater.inflate(mResourceId, null);
                holder = new ViewHolder();
                holder.mImageAppIcon = (ImageView)view.findViewById(R.id.image_appicon);
                holder.mTextAppName = (TextView)view.findViewById(R.id.text_appname);
                holder.mTextAppSec = (TextView)view.findViewById(R.id.text_appsec);
                holder.mImageTimerOn = (ImageView)view.findViewById(R.id.image_timer_on);
                holder.mImageTimerOff = (ImageView)view.findViewById(R.id.image_timer_off);
                view.setTag(holder);
            } else {
                holder = (ViewHolder)view.getTag();
            }
            holder.mImageAppIcon.setImageDrawable(item.getAppIcon());
            holder.mTextAppName.setText(item.getAppName());
            holder.mTextAppSec.setText(item.getSecString());
            int timerInfo = mDB.getTimerInfo(item.getPackageName());
            int timerWarning = mDB.getTimerWarning(item.getPackageName());
            int timerFinish = mDB.getTimerFinish(item.getPackageName());
            if (0 < timerInfo + timerWarning + timerFinish) {
                holder.mImageTimerOn.setVisibility(View.VISIBLE);
                holder.mImageTimerOff.setVisibility(View.GONE);
            } else {
                holder.mImageTimerOn.setVisibility(View.GONE);
                holder.mImageTimerOff.setVisibility(View.VISIBLE);
            }
            AnimationSet animations = new AnimationSet(true);
            animations.setDuration(1000L);
            animations.setInterpolator(new OvershootInterpolator());
            animations.addAnimation(new AlphaAnimation(0.0F, 1.0F));
            int RELATIVE_TO_SELF = TranslateAnimation.RELATIVE_TO_SELF;
            Animation anim = new TranslateAnimation(RELATIVE_TO_SELF, -1F, RELATIVE_TO_SELF, 0F, RELATIVE_TO_SELF, 0F, RELATIVE_TO_SELF, 0F);
            animations.addAnimation(anim);
            view.startAnimation(animations);
            return view;
        }
        private LayoutInflater mInflater;
        private int mResourceId;
        private WatchDatabase mDB;
    }
    
}
