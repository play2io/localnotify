package com.tealeaf.plugin.plugins;

import java.lang.reflect.Method;
import android.content.SharedPreferences;


// To be compatible with API level 8, we need to separate out the apply function
public class PreferencesWrapper {
	public static void runApply(SharedPreferences.Editor editor) {
		editor.apply();
	}
}

