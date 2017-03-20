package com.robbi5.instreamer.layout;

import android.app.Fragment;
import android.os.Bundle;
import android.support.v14.preference.PreferenceFragment;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v17.preference.LeanbackSettingsFragment;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceScreen;

import com.robbi5.instreamer.R;

public class SettingsFragment extends LeanbackSettingsFragment {

  public SettingsFragment() {}

  @Override
  public void onPreferenceStartInitialScreen() {
    startPreferenceFragment(new PrefsFragment());
  }

  @Override
  public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
    return false;
  }

  @Override
  public boolean onPreferenceStartScreen(PreferenceFragment caller, PreferenceScreen pref) {
    final Fragment f = new PrefsFragment();
    final Bundle args = new Bundle(1);
    args.putString(PreferenceFragment.ARG_PREFERENCE_ROOT, pref.getKey());
    f.setArguments(args);
    startPreferenceFragment(f);
    return true;
  }

  public static class PrefsFragment extends LeanbackPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
      // Load the preferences from an XML resource
      setPreferencesFromResource(R.xml.settings, rootKey);
    }
  }
}
