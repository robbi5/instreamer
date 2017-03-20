package com.robbi5.instreamer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class TVActivity extends Activity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent i = new Intent(this, MainActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(i);
  }
}
