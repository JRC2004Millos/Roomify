package com.unity3d.player;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;

public class UnityPlayerActivity extends Activity implements IUnityPlayerLifecycleEvents, IUnityPermissionRequestSupport, IUnityPlayerSupport {

    protected UnityPlayerForActivityOrService mUnityPlayer;

    private boolean shouldExitUnity(Intent intent) {
        return intent != null && intent.getBooleanExtra("EXIT_UNITY", false);
    }

    private void finishSilently() {
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
        finish();
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
    }

    protected String updateUnityCommandLineArguments(String cmdLine) {
        return cmdLine;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);

        if (shouldExitUnity(getIntent())) {
            finishSilently();
            return;
        }

        String cmdLine = updateUnityCommandLineArguments(getIntent().getStringExtra("unity"));
        getIntent().putExtra("unity", cmdLine);

        mUnityPlayer = new UnityPlayerForActivityOrService(this, this);
        setContentView(mUnityPlayer.getFrameLayout());
        mUnityPlayer.getFrameLayout().requestFocus();
    }

    @Override
    public UnityPlayerForActivityOrService getUnityPlayerConnection() {
        return mUnityPlayer;
    }

    @Override
    public void onUnityPlayerUnloaded() {
        moveTaskToBack(true);
    }

    @Override
    public void onUnityPlayerQuitted() {
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (shouldExitUnity(intent)) {
            setIntent(intent);
            finishSilently();
            return;
        }

        setIntent(intent);
        if (mUnityPlayer != null) {
            mUnityPlayer.newIntent(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mUnityPlayer != null) mUnityPlayer.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mUnityPlayer != null) mUnityPlayer.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mUnityPlayer != null) mUnityPlayer.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mUnityPlayer != null) mUnityPlayer.onResume();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mUnityPlayer != null) {
            mUnityPlayer.onTrimMemory(UnityPlayerForActivityOrService.MemoryUsage.Critical);
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (mUnityPlayer == null) return;
        switch (level) {
            case TRIM_MEMORY_RUNNING_MODERATE:
                mUnityPlayer.onTrimMemory(UnityPlayerForActivityOrService.MemoryUsage.Medium);
                break;
            case TRIM_MEMORY_RUNNING_LOW:
                mUnityPlayer.onTrimMemory(UnityPlayerForActivityOrService.MemoryUsage.High);
                break;
            case TRIM_MEMORY_RUNNING_CRITICAL:
                mUnityPlayer.onTrimMemory(UnityPlayerForActivityOrService.MemoryUsage.Critical);
                break;
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mUnityPlayer != null) mUnityPlayer.configurationChanged(newConfig);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (mUnityPlayer != null) mUnityPlayer.windowFocusChanged(hasFocus);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_MULTIPLE && mUnityPlayer != null)
            return mUnityPlayer.injectEvent(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void requestPermissions(PermissionRequest request) {
        if (mUnityPlayer != null) mUnityPlayer.addPermissionRequest(request);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (mUnityPlayer != null) mUnityPlayer.permissionResponse(this, requestCode, permissions, grantResults);
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event)     { return mUnityPlayer != null && mUnityPlayer.getFrameLayout().onKeyUp(keyCode, event); }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event)   { return mUnityPlayer != null && mUnityPlayer.getFrameLayout().onKeyDown(keyCode, event); }
    @Override public boolean onTouchEvent(MotionEvent event)          { return mUnityPlayer != null && mUnityPlayer.getFrameLayout().onTouchEvent(event); }
    @Override public boolean onGenericMotionEvent(MotionEvent event)  { return mUnityPlayer != null && mUnityPlayer.getFrameLayout().onGenericMotionEvent(event); }
}