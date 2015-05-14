/*
 * UnwatchFragment.java
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

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.dena.app.usage.watcher.R;
import com.dena.app.usage.watcher.model.UnwatchItem;
import com.dena.app.usage.watcher.model.UnwatchManager;
import java.util.ArrayList;
import java.util.List;

public class UnwatchFragment extends Fragment implements AdapterView.OnItemClickListener {

    public static UnwatchFragment newInstance() {
        UnwatchFragment fragment = new UnwatchFragment();
        return fragment;
    }

    public UnwatchFragment() {
    }

    private void loadUnwatch() {
        List<UnwatchItem> list = new UnwatchManager(getActivity()).getUnwatchList();
        mAdapter.clear();
        mAdapter.addAll(list);
        mAdapter.notifyDataSetChanged();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup viewgroup, Bundle bundle) {
        View view = inflater.inflate(R.layout.fragment_unwatch, null);
        mActionBar = getActivity().getActionBar();
        if (null != mActionBar) {
            mActionBar.setTitle(getString(R.string.hidden_apps));
        }
        ListView listView = (ListView)view.findViewById(R.id.listView);
        mAdapter = new UnwatchAdapter(getActivity(), R.layout.item_unwatch, new ArrayList());
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(this);
        return view;
    }

    public void onDestroyView() {
        super.onDestroyView();
        if (null != mActionBar) {
            mActionBar.setTitle(getString(R.string.app_name));
        }
    }

    public void onItemClick(AdapterView adapterview, View view, int i, long l) {
        final UnwatchItem item = (UnwatchItem)adapterview.getItemAtPosition(i);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(item.getAppName());
        builder.setMessage(getString(R.string.show_app_again));
        builder.setPositiveButton(getString(R.string.positive), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialoginterface, int j) {
                String s = item.getPackageName();
                (new UnwatchManager(getActivity())).removeUnwatch(s);
                loadUnwatch();
            }
        });
        builder.setNegativeButton(getString(R.string.negative), null);
        builder.create().show();
    }

    public void onStart() {
        super.onStart();
        loadUnwatch();
    }

    private ActionBar mActionBar;
    private UnwatchAdapter mAdapter;

    private static class UnwatchAdapter extends ArrayAdapter {
        private static class ViewHolder {
            ImageView mImageAppIcon;
            TextView mTextAppName;
            TextView mTextPackageName;
        }

        public UnwatchAdapter(Context context, int resourceId, List list) {
            super(context, resourceId, list);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mResourceId = resourceId;
        }

        public View getView(int i, View view, ViewGroup viewgroup) {
            ViewHolder holder;
            if (null == view)  {
                view = mInflater.inflate(mResourceId, null);
                holder = new ViewHolder();
                holder.mImageAppIcon = (ImageView)view.findViewById(R.id.image_appIcon);
                holder.mTextAppName = (TextView)view.findViewById(R.id.text_appName);
                holder.mTextPackageName = (TextView)view.findViewById(R.id.text_packageName);
                view.setTag(holder);
            } else {
                holder = (ViewHolder)view.getTag();
            }
            UnwatchItem item = (UnwatchItem)getItem(i);
            holder.mTextPackageName.setText(item.getPackageName());
            holder.mTextAppName.setText(item.getAppName());
            holder.mImageAppIcon.setImageDrawable(item.getAppIcon());
            return view;
        }
        private LayoutInflater mInflater;
        private int mResourceId;
    }
}
