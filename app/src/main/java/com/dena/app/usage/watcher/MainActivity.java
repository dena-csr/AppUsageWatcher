/*
 * MainActivity.java
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

package com.dena.app.usage.watcher;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.media.AudioManager;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.SwitchCompat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;

import com.dena.app.usage.watcher.fragment.SettingFragment;
import com.dena.app.usage.watcher.fragment.WatchFragment;
import com.dena.app.usage.watcher.fragment.UnwatchFragment;
import com.dena.app.usage.watcher.fragment.AboutFragment;
import com.dena.app.usage.watcher.fragment.NavigationDrawerFragment;
import com.dena.app.usage.watcher.receiver.WatchReceiver;
import com.dena.app.usage.watcher.service.WatchService;
import com.dena.app.usage.watcher.util.WatchUtil;


public class MainActivity extends AppCompatActivity implements NavigationDrawerFragment.NavigationDrawerCallbacks, CompoundButton.OnCheckedChangeListener {

    private Handler mHandler;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private SwitchCompat mSwitchCompat;
    private CharSequence mTitle;
    private AudioManager mAudioManager;
    private static final String SUPPORT_FRAGMENT_TAG = Fragment.class.getName();
    private static final String FRAGMENT_TAG = android.app.Fragment.class.getName();

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mHandler = new Handler();
        startSplash();
        mNavigationDrawerFragment = (NavigationDrawerFragment)
            getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        WatchReceiver watchReceiver = new WatchReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WatchService.WATCH_ACTION);
        registerReceiver(watchReceiver, intentFilter);

        watchReceiver.registerHandler(new Handler() {
            public void handleMessage(Message msg) {
                if (null == mSwitchCompat) {
                    return;
                }
                Bundle bundle = msg.getData();
                String message = bundle.getString(WatchService.WATCH_MESSAGE);
                if (message.equals(WatchService.WATCH_ACTION)) {
                    mSwitchCompat.setChecked(true);
                }
            }
        });
    }

    private void startSplash() {
        getSupportActionBar().hide();
        findViewById(R.id.splash).setVisibility(View.VISIBLE);
        findViewById(R.id.drawer_layout).setVisibility(View.GONE);
    }

    private void endSplash() {
        getSupportActionBar().show();
        findViewById(R.id.splash).setVisibility(View.GONE);
        findViewById(R.id.drawer_layout).setVisibility(View.VISIBLE);
    }

    protected void onStart() {
        super.onStart();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                endSplash();
                mNavigationDrawerFragment.setUp(R.id.navigation_drawer,
                        (DrawerLayout) findViewById(R.id.drawer_layout));
            }
        }, 1500L);
    }

    private void startFragment(android.app.Fragment fragment) {
        Fragment supportFragment = getSupportFragmentManager().findFragmentByTag(SUPPORT_FRAGMENT_TAG);
        if (null != supportFragment) {
            getSupportFragmentManager().beginTransaction().remove(supportFragment).commit();
        }
        getFragmentManager().beginTransaction()
                .replace(R.id.container, fragment, FRAGMENT_TAG)
                .commit();

    }

    private void startSupportFragment(Fragment supportFragment) {
        android.app.Fragment fragment = getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
        if (null != fragment) {
            getFragmentManager().beginTransaction().remove(fragment).commit();
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, supportFragment, SUPPORT_FRAGMENT_TAG)
                .commit();

    }

    public void onNavigationDrawerItemSelected(int position) {
        if (position == 0) {
            startSupportFragment(WatchFragment.newInstance(false));
        } else if (position == 1) {
            startSupportFragment(WatchFragment.newInstance(true));
        } else if (position == 2) {
            startSupportFragment(UnwatchFragment.newInstance());
        }
    }

    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = getString(R.string.title_section1);
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                break;
            case 3:
                mTitle = getString(R.string.title_section3);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setTitle(mTitle);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            getMenuInflater().inflate(R.menu.main, menu);
            MenuItem item = menu.findItem(R.id.action_switch);
            mSwitchCompat =  (SwitchCompat) MenuItemCompat.getActionView(item);
            mSwitchCompat.setOnCheckedChangeListener(this);
            boolean checked = WatchUtil.isRunningService(this, WatchService.class.getName());
            mSwitchCompat.setChecked(checked);
            restoreActionBar();
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_settings:
            startFragment(SettingFragment.newInstance());
            return true;
        case R.id.action_about:
            startSupportFragment(AboutFragment.newInstance());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            startWatchService(true, true);
        } else {
            stopWatchService();
        }
    }

    private Intent createWatchServiceIntent() {
        return new Intent(getApplicationContext(), WatchService.class);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private boolean startWatchService(boolean shouldAskUsageStat, boolean shouldAskOverlay) {
        if (!checkPermissionUsageStat(shouldAskUsageStat) || !checkPermissionOverlay(shouldAskOverlay)) {
            mSwitchCompat.setChecked(false);
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startService(createWatchServiceIntent());
        } else {
            startForegroundService(createWatchServiceIntent());
        }
        mSwitchCompat.setChecked(true);
        return true;
    }

    private void stopWatchService() {
        stopService(createWatchServiceIntent());
        mSwitchCompat.setChecked(false);
    }

    public boolean onKeyDown(int keyCode, KeyEvent evt) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return true;
            default:
                break;
        }
        return super.onKeyDown(keyCode, evt);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
        case REQUEST_USAGE_STAT:
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startWatchService(false, true);
                }
            }, 100L);
            break;
        case REQUEST_OVERLAY:
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startWatchService(true, false);
                }
            }, 500L);
            break;
        default:
            break;
        }
    }

    private static final int REQUEST_USAGE_STAT = 100;
    private static final int REQUEST_OVERLAY = 101;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private boolean checkPermissionUsageStat(boolean shouldAsk) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return true;
        }
        if (WatchUtil.canAccessUsageStat(this)) {
            return true;
        }
        if (!shouldAsk) {
            return false;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.permission)
            .setMessage(R.string.rationale_permission_usage_stat).create();
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", (DialogInterface.OnClickListener)null);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                try {
                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                    startActivityForResult(intent, REQUEST_USAGE_STAT);
                } catch (Throwable t) {
                    onActivityResult(REQUEST_USAGE_STAT, RESULT_CANCELED, null);
                }
            }
        });
        dialog.show();
        return false;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private boolean checkPermissionOverlay(boolean shouldAsk) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (Settings.canDrawOverlays(this)) {
            return true;
        }
        if (!shouldAsk) {
            return false;
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.permission)
            .setMessage(R.string.rationale_permission_overlay).create();
        dialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", (DialogInterface.OnClickListener)null);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, REQUEST_OVERLAY);
                } catch (Throwable t) {
                    onActivityResult(REQUEST_OVERLAY, RESULT_CANCELED, null);
                }
            }
        });
        dialog.show();
        return false;
    }

}
