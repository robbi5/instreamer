package com.robbi5.instreamer.layout;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Fragment;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.realtek.hardware.RtkHDMIRxManager;
import com.realtek.server.HDMIRxStatus;
import com.realtek.server.RtkHDMIService;
import com.robbi5.instreamer.BuildConfig;
import com.robbi5.instreamer.R;
import com.robbi5.instreamer.StreamService;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainFragment extends Fragment {
  static final int CHECK_HDMI_MSG = 0;
  public static final String STREAM_ACTIVITY_INTENT = BuildConfig.APPLICATION_ID + ".STREAM_ACTIVITY_INTENT";
  public static final String EXTRA_SURFACE = BuildConfig.APPLICATION_ID + ".SURFACE";
  public static final String EXTRA_TOGGLE_STREAMING = BuildConfig.APPLICATION_ID + ".TOGGLE_STREAMING";
  public static final int PERMISSIONS_RESPONSE = 42;

  public static final int MODE_NO_SIGNAL = 0;
  public static final int MODE_PREVIEW = 1;
  public static final int MODE_STREAMING = 2;

  SharedPreferences preferences;
  SharedPreferences.OnSharedPreferenceChangeListener preferencesChangeListener;

  SurfaceView surfaceView;
  SurfaceHolder surfaceHolder;
  TextView noSignalTextView;
  TextView textView;
  ScrollView scrollView;
  ImageView recordingImageView;

  HdmiSurfaceHolderCallback hdmiSurfaceHolderCallback;
  HdmiHotplugReceiver hdmiHotplugReceiver;
  Handler checkHdmiReadyHandler;
  StreamActivityReceiver streamActivityReceiver;
  RtkHDMIRxManager hdmiRxManager;

  Intent service = null;
  int mode = MODE_NO_SIGNAL;
  int lastMode = MODE_NO_SIGNAL;

  public MainFragment() {}

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View root = inflater.inflate(R.layout.fragment_main, container, false);

    surfaceView = (SurfaceView) root.findViewById(R.id.surfaceView);
    surfaceHolder = surfaceView.getHolder();
    noSignalTextView = (TextView) root.findViewById(R.id.noSignalTextView);
    textView = (TextView) root.findViewById(R.id.textView);
    scrollView = (ScrollView) root.findViewById(R.id.scrollView);
    recordingImageView = (ImageView) root.findViewById(R.id.recordingImageView);

    return root;
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  @Override
  public void onStart() {
    super.onStart();

    if (preferences == null) {
      preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
    }

    // halt startup if we don't have all needed permissions
    if (!checkPermissions()) {
      return;
    }

    if (hdmiSurfaceHolderCallback == null) {
      hdmiSurfaceHolderCallback = new HdmiSurfaceHolderCallback();
      surfaceHolder.addCallback(hdmiSurfaceHolderCallback);
    }

    if (hdmiHotplugReceiver == null) {
      hdmiHotplugReceiver = new HdmiHotplugReceiver();
      getActivity().registerReceiver(hdmiHotplugReceiver, new IntentFilter(HDMIRxStatus.ACTION_HDMIRX_PLUGGED));
    }

    if (checkHdmiReadyHandler == null) {
      checkHdmiReadyHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
          checkHdmiReady();
          return true;
        }
      });
    }

    if (hdmiRxManager == null) {
      hdmiRxManager = new RtkHDMIRxManager();
    }

    if (streamActivityReceiver == null) {
      streamActivityReceiver = new StreamActivityReceiver();
      getActivity().registerReceiver(streamActivityReceiver, new IntentFilter(STREAM_ACTIVITY_INTENT));
    }

    if (preferencesChangeListener == null) {
      preferencesChangeListener = new SharedPreferenceChangeListener();
      preferences.registerOnSharedPreferenceChangeListener(preferencesChangeListener);
    }

    onSettingsUpdate();

    // Don't call, that happens with hdmiHotplugReceiver already
    // checkHdmiReady();
  }

  public boolean checkPermissions() {
    String[] requiredPermissions = new String[]{
      Manifest.permission.INTERNET,
      Manifest.permission.CAMERA,
      Manifest.permission.RECORD_AUDIO,
      Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    boolean shouldAskForPermissions = false;
    for (String requiredPermission : requiredPermissions) {
      if (ContextCompat.checkSelfPermission(getActivity(), requiredPermission) != PackageManager.PERMISSION_GRANTED) {
        shouldAskForPermissions = true;
        break;
      }
    }

    if (!shouldAskForPermissions) {
      return true;
    }

    ActivityCompat.requestPermissions(getActivity(), requiredPermissions, PERMISSIONS_RESPONSE);
    return false;
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
    if (requestCode != PERMISSIONS_RESPONSE) {
      return;
    }

    if (grantResults.length > 0  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      // try again to start:
      onStart();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    onSettingsUpdate();
  }

  @Override
  public void onStop() {
    super.onStop();

    if (hdmiSurfaceHolderCallback != null) {
      surfaceHolder.removeCallback(hdmiSurfaceHolderCallback);
    }
    if (hdmiHotplugReceiver != null) {
      getActivity().unregisterReceiver(hdmiHotplugReceiver);
    }
    if (checkHdmiReadyHandler != null) {
      checkHdmiReadyHandler.removeMessages(CHECK_HDMI_MSG);
      checkHdmiReadyHandler = null;
    }
    if (hdmiRxManager != null) {
      if (mode == MODE_PREVIEW) {
        hdmiRxManager.stop();
        mode = MODE_NO_SIGNAL;
      }
      hdmiRxManager.release();
      hdmiRxManager = null;
    }
    if (streamActivityReceiver != null) {
      getActivity().unregisterReceiver(streamActivityReceiver);
    }
    if (preferencesChangeListener != null) {
      preferences.unregisterOnSharedPreferenceChangeListener(preferencesChangeListener);
    }
  }

  private void onSettingsUpdate() {
    scrollView.setVisibility(preferences.getBoolean("displayLog", true) ? View.VISIBLE : View.INVISIBLE);
  }

  public synchronized void checkHdmiReady() {
    checkHdmiReadyHandler.removeMessages(CHECK_HDMI_MSG);
    if (mode == MODE_PREVIEW) {
      return;
    }
    if (hdmiSurfaceHolderCallback != null && !hdmiSurfaceHolderCallback.isReady()) {
      checkHdmiReadyHandler.sendEmptyMessageDelayed(CHECK_HDMI_MSG, 500);
      return;
    }
    if (hdmiRxManager == null) {
      checkHdmiReadyHandler.sendEmptyMessageDelayed(CHECK_HDMI_MSG, 500);
      return;
    }
    HDMIRxStatus rxStatus = hdmiRxManager.getHDMIRxStatus();
    if (rxStatus == null || rxStatus.status != 1) {
      checkHdmiReadyHandler.sendEmptyMessageDelayed(CHECK_HDMI_MSG, 500);
      return;
    }
    if (hdmiRxManager.open() != 0) {
      checkHdmiReadyHandler.sendEmptyMessageDelayed(CHECK_HDMI_MSG, 500);
      return;
    }

    if (preferences.getBoolean("start_after_boot", false) || lastMode == MODE_STREAMING) {
      startStreaming();
    } else {
      startPreview();
    }
  }

  private void startPreview() {
    if (mode == MODE_PREVIEW) {
      stopPreview();
      checkHdmiReadyHandler.sendEmptyMessageDelayed(CHECK_HDMI_MSG, 500);
      return;
    }

    try {
      hdmiRxManager.setPreviewDisplay(surfaceHolder);
      hdmiRxManager.configureTargetFormat(
        StreamService.buildVideoConfigFromSettings(preferences),
        StreamService.buildAudioConfigFromSettings(preferences));
      hdmiRxManager.play();
      mode = MODE_PREVIEW;
    } catch (IOException e) {
      mode = MODE_NO_SIGNAL;
    }
  }

  private void stopPreview() {
    if (hdmiRxManager != null && mode == MODE_PREVIEW) {
      hdmiRxManager.stop();
    }
    mode = MODE_NO_SIGNAL;
  }

  private void startStreaming() {
    if (service != null) {
      Toast.makeText(getActivity(), R.string.already_streaming, Toast.LENGTH_SHORT).show();
      return;
    }
    stopPreview();

    // Give the surface to the streamService
    // the preview is no longer happening on the surface
    // the streaming HdmiRxManager needs an surface to display
    // else something gets cleaned up and the pipe stops
    service = new Intent(getActivity(), StreamService.class);
    Surface s = surfaceHolder.getSurface();
    service.putExtra(EXTRA_SURFACE, s);
    getActivity().startService(service);

    recordingImageView.setVisibility(View.VISIBLE);
    mode = MODE_STREAMING;
  }

  private void stopStreaming() {
    if (service != null) {
      getActivity().stopService(service);
      service = null;
      recordingImageView.setVisibility(View.INVISIBLE);
      mode = MODE_NO_SIGNAL;
    }
  }

  private void toggleStreaming() {
    if (service != null) {
      stopStreaming();
    } else {
      startStreaming();
    }
  }

  class HdmiSurfaceHolderCallback implements SurfaceHolder.Callback {
    AtomicBoolean ready = new AtomicBoolean(false);

    HdmiSurfaceHolderCallback() {}

    public void surfaceChanged(SurfaceHolder arg0, int arg1, int width, int height) {
      if (ready.compareAndSet(false, true)) {
        checkHdmiReadyHandler.sendEmptyMessageDelayed(CHECK_HDMI_MSG, 500);
      }
    }

    public boolean isReady() {
      return ready.get();
    }

    public void surfaceCreated(SurfaceHolder arg0) {}

    public void surfaceDestroyed(SurfaceHolder arg0) {
      ready.set(false);
    }
  }

  class HdmiHotplugReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (!intent.getBooleanExtra(RtkHDMIService.EXTRA_HDMI_PLUGGED_STATE, false)) {
        noSignalTextView.setVisibility(View.VISIBLE);
        lastMode = mode;
        if (mode == MODE_PREVIEW) {
          stopPreview();
        } else if (mode == MODE_STREAMING) {
          stopStreaming();
        }
        return;
      }

      checkHdmiReadyHandler.sendEmptyMessageDelayed(CHECK_HDMI_MSG, 500);
      noSignalTextView.setVisibility(View.INVISIBLE);
    }
  }

  class StreamActivityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.hasExtra(EXTRA_TOGGLE_STREAMING)) {
        toggleStreaming();
      } else if (intent.hasExtra("log")) {
        String log = intent.getExtras().getString("log");
        textView.append(log + "\n");
        scrollView.smoothScrollTo(0, textView.getBottom());
      } else if (intent.hasExtra("error")) {
        String message = intent.getExtras().getString("error");
        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
      } else if (intent.hasExtra("failure")) {
        stopStreaming();
        checkHdmiReadyHandler.sendEmptyMessageDelayed(CHECK_HDMI_MSG, 500);
      }
    }
  }

  class SharedPreferenceChangeListener implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
      onSettingsUpdate();
    }
  }
}
