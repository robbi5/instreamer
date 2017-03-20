package com.robbi5.instreamer;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Toast;

import com.robbi5.instreamer.layout.MainFragment;

public class MainActivity extends FragmentActivity {

  Toast exitToast;

  private void hideSystemUi() {
    View decorView = getWindow().getDecorView();
    int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
    decorView.setSystemUiVisibility(uiOptions);
    try {
      getActionBar().hide();
    } catch (NullPointerException e) {}
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    hideSystemUi();

    exitToast = Toast.makeText(this, R.string.press_again_to_exit, Toast.LENGTH_SHORT);

    // fragment is loaded by the layout
  }

  @Override
  protected void onResume() {
    hideSystemUi();
    super.onResume();
  }

  @Override
  public void onBackPressed() {
    if (exitToast.getView().isShown()) {
      exitToast.cancel();
      super.onBackPressed(); // needed?
      finish();
    } else {
      exitToast.show();
    }
  }

  private void openSettings() {
    Intent intent = new Intent(getBaseContext(), SettingsActivity.class);
    startActivity(intent);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_MENU) {
      openSettings();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  private void toggleStreaming() {
    Intent intent = new Intent(MainFragment.STREAM_ACTIVITY_INTENT);
    intent.putExtra(MainFragment.EXTRA_TOGGLE_STREAMING, true);
    sendBroadcast(intent);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
      case KeyEvent.KEYCODE_MEDIA_PLAY:
      case KeyEvent.KEYCODE_1:
        toggleStreaming();
        return true;
      case KeyEvent.KEYCODE_BACK:
        onBackPressed();
        return true;
    }
    return super.onKeyUp(keyCode, event);
  }

}
