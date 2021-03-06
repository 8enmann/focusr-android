package net.benjmann.focusr;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.MultiSelectListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends AppCompatPreferenceActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_general);
        SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
        onSharedPreferenceChanged(sp, "total_time_per_day");
        onSharedPreferenceChanged(sp, "apps");
        onSharedPreferenceChanged(sp, "progress_interval");
    }

    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if (pref instanceof EditTextPreference) {
            EditTextPreference etp = (EditTextPreference) pref;
            pref.setSummary(etp.getText());
        } else if (pref instanceof MultiSelectListPreference){
            MultiSelectListPreference mslp = (MultiSelectListPreference) pref;
            ArrayList<CharSequence> items = new ArrayList<>();
            CharSequence[] entries = mslp.getEntries();
            for (String value : sharedPreferences.getStringSet(key, new HashSet<String>())) {
                items.add(entries[mslp.findIndexOfValue(value)]);
            }

            pref.setSummary(TextUtils.join(", ", items));
        }
    }
}
