package info.guardianproject.bigbuffalo;

import info.guardianproject.bigbuffalo.api.Settings;
import info.guardianproject.bigbuffalo.api.Settings.UiLanguage;
import info.guardianproject.bigbuffalo.api.SocialReader;
import info.guardianproject.bigbuffalo.api.SocialReporter;
import info.guardianproject.bigbuffalo.models.LockScreenCallbacks;
import info.guardianproject.bigbuffalo.widgets.CustomFontButton;
import info.guardianproject.bigbuffalo.widgets.CustomFontEditText;
import info.guardianproject.bigbuffalo.widgets.CustomFontRadioButton;
import info.guardianproject.bigbuffalo.widgets.CustomFontTextView;

import java.util.Locale;



import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.tinymission.rss.Feed;

public class App extends Application implements OnSharedPreferenceChangeListener
{
	public static final boolean UI_ENABLE_POPULAR_ITEMS = false;
	public static final boolean UI_ENABLE_COMMENTS = false;
	public static final boolean UI_ENABLE_TAGS = true;
	public static final boolean UI_ENABLE_POST_LOGIN = false;
	public static final boolean UI_ENABLE_REPORTER = false;
	public static final boolean UI_ENABLE_CHAT = false;
	public static final boolean UI_ENABLE_LANGUAGE_CHOICE = true;
	
	public static final String EXIT_BROADCAST_PERMISSION = "info.guardianproject.bigbuffalo.exit.permission";
	public static final String EXIT_BROADCAST_ACTION = "info.guardianproject.bigbuffalo.exit.action";
	public static final String SET_UI_LANGUAGE_BROADCAST_ACTION = "info.guardianproject.bigbuffalo.setuilanguage.action";
	public static final String WIPE_BROADCAST_ACTION = "info.guardianproject.bigbuffalo.wipe.action";

	private static App m_singleton;

	public Feed m_activeFeed;
	public int m_selectedArticleId;
	public boolean m_unreadOnly = true;
	public boolean m_unreadArticlesOnly = true;
	// public String m_sessionId;
	// public int m_apiLevel;
	public boolean m_canUseProgress;

	public static Context m_context;
	public static Settings m_settings;

	public SocialReader socialReader;
	public SocialReporter socialReporter;

	@Override
	public void onCreate()
	{
		super.onCreate();

		m_singleton = this;
		m_context = this;
		m_settings = new Settings(m_context);
		applyUiLanguage();

		socialReader = new SocialReader(getApplicationContext());
		socialReporter = new SocialReporter(socialReader);

		m_settings.registerChangeListener(this);
	}

	public static Context getContext()
	{
		return m_context;
	}

	public static App getInstance()
	{
		return m_singleton;
	}

	public static Settings getSettings()
	{
		return m_settings;
	}

	private boolean mInBackground = true;

	// When any activity pauses with the new activity not being ours.
	public void onActivityPause(LockScreenCallbacks activity)
	{
		Log.v("App", "onActivityPause");

		if (activity.isInternalActivityOpened())
			return;

		if (!mInBackground)
		{
			mInBackground = true;
			socialReader.onPause();
		}
	}

	// When any activity resumes with a previous activity not being ours.
	public void onActivityResume(LockScreenCallbacks activity)
	{
		Log.v("App", "onActivityResume");

		if (activity.isInternalActivityOpened())
			return;

		boolean wasInBackground = mInBackground;
		mInBackground = false;
		if (wasInBackground) {
			socialReader.onResume();
		}
	}

	// Helper to find if the Application is in background from any activity.
	public boolean isApplicationInBackground()
	{
		return mInBackground;
	}

	private Bitmap mTransitionBitmap;

	public Bitmap getTransitionBitmap()
	{
		return mTransitionBitmap;
	}

	public void putTransitionBitmap(Bitmap bitmap)
	{
		mTransitionBitmap = bitmap;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (key.equals(Settings.KEY_UI_LANGUAGE))
		{
			applyUiLanguage();
		}
	}
		
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		applyUiLanguage();
	}

	@SuppressLint("NewApi")
	private void applyUiLanguage()
	{
		UiLanguage lang = m_settings.uiLanguage();

		// Update language!
		//
		Configuration config = new Configuration();
		
		String language = "en";
		if (lang == UiLanguage.Farsi)
			language = "ar";
		else if (lang == UiLanguage.Tibetan)
			language = "bo";
		else if (lang == UiLanguage.Chinese)
			language = "zh";
		
		if (language.equals(getBaseContext().getResources().getConfiguration().locale.getLanguage()))
			return; //No change
		
		Locale loc = new Locale(language);
		if (Build.VERSION.SDK_INT >= 17)
			config.setLocale(loc);
		else
			config.locale = loc;
		Locale.setDefault(loc);
		getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
	
		// Notify activities (if any)
		Intent intent = new Intent(App.SET_UI_LANGUAGE_BROADCAST_ACTION);
		this.sendOrderedBroadcast(intent, App.EXIT_BROADCAST_PERMISSION, new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
			}
		}, null, Activity.RESULT_OK, null, null);
	}

	public void wipe(int wipeMethod)
	{
		socialReader.doWipe(wipeMethod);

		// Notify activities (if any)
		Intent intent = new Intent(App.WIPE_BROADCAST_ACTION);
		this.sendOrderedBroadcast(intent, App.EXIT_BROADCAST_PERMISSION, new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
			}
		}, null, Activity.RESULT_OK, null, null);
	}
	
	public static View createView(String name, Context context, AttributeSet attrs)
	{
		if (name.equals("TextView"))
		{
			return new CustomFontTextView(context, attrs);
		}
		else if (name.equals("Button"))
		{
			return new CustomFontButton(context, attrs);
		}
		else if (name.equals("RadioButton"))
		{
			return new CustomFontRadioButton(context, attrs);
		}
		else if (name.equals("EditText"))
		{
			return new CustomFontEditText(context, attrs);
		}
		return null;
	}
}
