package com.orange.oidc.tim.proxy.hello;


// display settings activity from resource
public class SettingsActivity extends android.preference.PreferenceActivity {

	@Override
	protected void onCreate(android.os.Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.settings);
	}

}