package info.guardianproject.bigbuffalo;

import info.guardianproject.bigbuffalo.models.LockScreenCallbacks;
import info.guardianproject.bigbuffalo.ui.ActionProviderShare;
import info.guardianproject.bigbuffalo.ui.LayoutFactoryWrapper;
import info.guardianproject.bigbuffalo.ui.PackageHelper;
import info.guardianproject.bigbuffalo.ui.UICallbacks;
import info.guardianproject.bigbuffalo.uiutil.ActivitySwitcher;
import info.guardianproject.bigbuffalo.uiutil.UIHelpers;
import info.guardianproject.bigbuffalo.views.LeftSideMenu;
import info.guardianproject.bigbuffalo.views.LeftSideMenu.LeftSideMenuListener;
import info.guardianproject.cacheword.ICacheWordSubscriber;
import info.guardianproject.onionkit.ui.OrbotHelper;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class FragmentActivityWithMenu extends SherlockFragmentActivity implements LockScreenCallbacks, LeftSideMenuListener, ICacheWordSubscriber
{
	private LayoutInflater mInflater;
	private KillReceiver mKillReceiver;
	private SetUiLanguageReceiver mSetUiLanguageReceiver;
	private WipeReceiver mWipeReceiver;
	private int mIdMenu;
	private ActionProviderShare mShareActionProvider;
	private Menu mOptionsMenu;
	private boolean mInternalActivityOpened = false;
	private boolean mDisplayHomeAsUp = false;

	/**
	 * The main menu that will host all content links.
	 */
	LeftSideMenu mLeftSideMenu;

	int mDeferedCommand;

	protected void setMenuIdentifier(int idMenu)
	{
		mIdMenu = idMenu;
	}

	protected boolean useLeftSideMenu()
	{
		return true;
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.getWindow().setBackgroundDrawable(null);

		mKillReceiver = new KillReceiver();
		registerReceiver(mKillReceiver, new IntentFilter(App.EXIT_BROADCAST_ACTION), App.EXIT_BROADCAST_PERMISSION, null);
		mSetUiLanguageReceiver = new SetUiLanguageReceiver();
		registerReceiver(mSetUiLanguageReceiver, new IntentFilter(App.SET_UI_LANGUAGE_BROADCAST_ACTION), App.EXIT_BROADCAST_PERMISSION, null);
		mWipeReceiver = new WipeReceiver();
		registerReceiver(mWipeReceiver, new IntentFilter(App.WIPE_BROADCAST_ACTION), App.EXIT_BROADCAST_PERMISSION, null);

		final ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);

		if (useLeftSideMenu())
		{
			mLeftSideMenu = new LeftSideMenu(this, actionBar, R.layout.left_side_menu);
			mLeftSideMenu.setListener(this);
		}

		setDisplayHomeAsUp(false);
		actionBar.setDisplayShowHomeEnabled(true);
		actionBar.setDisplayUseLogoEnabled(true);
		actionBar.setDisplayShowTitleEnabled(false);
		actionBar.setDisplayHomeAsUpEnabled(false);
		actionBar.setHomeButtonEnabled(true);

		// If passcode is required we don't want to show the app thumbnail (the
		// screen shot might
		// contain sensitive information). Since onCreateThumbnail is not called
		// correctly for 4.0
		// devices, flag the activity as secure instead.
		if (App.getSettings().launchRequirePassphrase())
		{
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB)
			{
				 getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
				 WindowManager.LayoutParams.FLAG_SECURE);
			}
		}
	}

	public void setDisplayHomeAsUp(boolean displayHomeAsUp)
	{
		Drawable logo = null;
		if (displayHomeAsUp)
		{
			logo = this.getResources().getDrawable(R.drawable.actionbar_logo_up).mutate();

			TypedValue outValue = new TypedValue();
			getTheme().resolveAttribute(R.attr.actionBarThemeBackground, outValue, true);
			if (outValue.resourceId == R.drawable.actionbar_dark_background)
				logo = this.getResources().getDrawable(R.drawable.actionbar_logo_up_read).mutate();
		}
		else
			logo = this.getResources().getDrawable(R.drawable.actionbar_logo).mutate();
		// UIHelpers.colorizeDrawable(this, logo);
		getSupportActionBar().setLogo(logo);
		mDisplayHomeAsUp = displayHomeAsUp;
		
		if (mLeftSideMenu != null)
			mLeftSideMenu.setDisplayMenuIndicator(!displayHomeAsUp);
	}

	public void setActionBarTitle(String title)
	{
		if (title != null)
		{
			if (getSupportActionBar().getCustomView() == null)
			{
				View titleView = getLayoutInflater().inflate(R.layout.actionbar_custom_title, null);
				getSupportActionBar().setCustomView(titleView,
						new ActionBar.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			}
			getSupportActionBar().setDisplayShowCustomEnabled(true);
			TextView tvTitle = (TextView) getSupportActionBar().getCustomView().findViewById(R.id.tvTitle);
			tvTitle.setText(title);
		}
		else
		{
			getSupportActionBar().setDisplayShowCustomEnabled(false);
		}
	}

	private void launchLockScreen()
	{
		Intent intent = new Intent(this, LockScreenActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		intent.putExtra("originalIntent", getIntent());
		startActivity(intent);
	}

	@Override
	protected void onStop()
	{
		super.onStop();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMenuCommandReceiver);
		if (!isFinishing() && (getApplication() instanceof App))
		{
			((App) getApplication()).onActivityPause(this);
			launchLockScreenIfInBackground();
		}
	}

	@SuppressLint("NewApi")
	@Override
	protected void onResume()
	{
		super.onResume();
		if (Build.VERSION.SDK_INT >= 11)
			invalidateOptionsMenu();
		LocalBroadcastManager.getInstance(this).registerReceiver(mMenuCommandReceiver, new IntentFilter("MenuCommand"));
		if (launchLockScreenIfInBackground())
			return;
		((App) getApplication()).onActivityResume(this);
		mInternalActivityOpened = false;
		if (mLeftSideMenu != null)
			mLeftSideMenu.checkMenuCreated();
	}

	private boolean launchLockScreenIfInBackground()
	{
		if (((App) getApplication()).isApplicationInBackground() && App.getSettings().launchRequirePassphrase())
		{
			launchLockScreen();
			return true;
		}
		return false;
	}

	@Override
	public void onCacheWordUninitialized()
	{
		//launchLockScreen();
	}

	@Override
	public void onCacheWordLocked()
	{
		//launchLockScreen();
	}

	@Override
	public void onCacheWordOpened()
	{
	}

	private final class KillReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			finish();
		}
	}

	private final class SetUiLanguageReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			new Handler().post(new Runnable()
			{

				@Override
				public void run()
				{
					onUiLanguageChanged();
				}
			});
		}
	}

	private final class WipeReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			new Handler().post(new Runnable()
			{
				@Override
				public void run()
				{
					onWipe();
				}
			});
		}
	}

	/**
	 * Override this to react to a wipe!
	 */
	protected void onWipe()
	{

	}

	@SuppressLint("NewApi")
	protected void onUiLanguageChanged()
	{
		if (Build.VERSION.SDK_INT >= 11)
		{
			recreate();
		}
		else
		{
			Intent intentThis = getIntent();
			overridePendingTransition(0, 0);
			intentThis.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			finish();
			overridePendingTransition(0, 0);
			startActivity(intentThis);
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		unregisterReceiver(mKillReceiver);
		unregisterReceiver(mSetUiLanguageReceiver);
		unregisterReceiver(mWipeReceiver);
	}

	@Override
	public void startActivity(Intent intent)
	{
		checkIfInternalActivity(intent);
		super.startActivity(intent);
	}

	@Override
	public void startActivityForResult(Intent intent, int request)
	{
		checkIfInternalActivity(intent);
		super.startActivityForResult(intent, request);
	}

	public void startActivityForResultAsInternal(Intent intent, int request)
	{
		mInternalActivityOpened = true;
		super.startActivityForResult(intent, request);
	}

	private void checkIfInternalActivity(Intent intent)
	{
		// Whenever we call our own activity, the component and it's package
		// name is set.
		// If we call an activity from another package, or an open intent
		// (leaving android to resolve)
		// component has a different package name or it is null.
		ComponentName component = intent.getComponent();
		mInternalActivityOpened = false;
		if (component != null && component.getPackageName() != null && component.getPackageName().equals(App.getInstance().getPackageName()))
		{
			mInternalActivityOpened = true;
		}
		else if (OrbotHelper.ACTION_START_TOR.equals(intent.getAction()))
		{
			// Special case - when opening the Orbot UI, consider that part of
			// the app (so the
			// lock screen is not shown!)
			mInternalActivityOpened = true;
		}
		else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null)
		{
			if (intent.getData().toString().equals(getString(R.string.market_orbot)))
			{
				// Install orbot
				mInternalActivityOpened = true;
			}
			else if (intent.getData().toString().equals(PackageHelper.URI_ORWEB_PLAY))
			{
				// Install orweb
				mInternalActivityOpened = true;
			}
			else if (intent.getComponent() != null && intent.getComponent().getPackageName() != null && intent.getComponent().getPackageName().equals(PackageHelper.URI_ORWEB))
			{
				// Read more with orweb
				mInternalActivityOpened = true;
			}
			else if (intent.getData().toString().equals(PackageHelper.URI_CHATSECURE_PLAY))
			{
				// Install ChatSecure
				mInternalActivityOpened = true;
			}
		}
	}

	@Override
	public boolean isInternalActivityOpened()
	{
		return mInternalActivityOpened;
	}

	public boolean isApplicationInBackground()
	{
		return ((App) getApplication()).isApplicationInBackground();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		if (mIdMenu == 0)
			return false;
		mOptionsMenu = menu;
		super.onCreateOptionsMenu(menu);

		getSupportMenuInflater().inflate(mIdMenu, menu);

		// Locate MenuItem with ShareActionProvider
		MenuItem item = menu.findItem(R.id.menu_share);
		if (item != null)
		{
			mShareActionProvider = new ActionProviderShare(this);
			item.setActionProvider(mShareActionProvider);
		}

		colorizeMenuItems();
		return true;
	}

	private void colorizeMenuItems()
	{
		if (mOptionsMenu == null)
			return;
		for (int i = 0; i < mOptionsMenu.size(); i++)
		{
			MenuItem item = mOptionsMenu.getItem(i);
			Drawable d = item.getIcon();
			if (d != null)
			{
				d = d.getConstantState().newDrawable(getResources()).mutate();
				UIHelpers.colorizeDrawable(this, d);
				item.setIcon(d);
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case android.R.id.home:
			if (mDisplayHomeAsUp)
			{
				Intent intent = new Intent(this, MainActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
				this.startActivity(intent);
				this.overridePendingTransition(R.anim.slide_in_from_left, R.anim.slide_out_to_right);
				return true;
			}
			if (mLeftSideMenu != null)
				mLeftSideMenu.toggle();
			return true;

		case R.id.menu_panic:
		{
			Intent intent = new Intent(this, PanicActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			startActivity(intent);
			return true;
		}

		case R.id.menu_add_post:
		{
			Intent intent = new Intent(this, AddPostActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
			startActivity(intent);
			overridePendingTransition(R.anim.slide_in_from_right, R.anim.slide_out_to_left);
			return true;
		}

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	@SuppressLint("NewApi")
	public void onMenuCreated(final View parent, final View menuRoot, final View menu)
	{
		// News
		//
		menu.findViewById(R.id.llNews).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mMenuCommandReceiver.handleCommand(R.integer.command_news_list);
			}
		});

		menu.findViewById(R.id.btnAddFeed).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mMenuCommandReceiver.handleCommand(R.integer.command_feed_add);
			}
		});

		// Reporter
		//
		if (App.UI_ENABLE_REPORTER)
		{
			menu.findViewById(R.id.llReporter).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					mMenuCommandReceiver.handleCommand(R.integer.command_posts_list);
				}
			});

			menu.findViewById(R.id.btnAddPost).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					mMenuCommandReceiver.handleCommand(R.integer.command_post_add);
				}
			});
		}
		else
		{
			// Disable reporter functionality
			menu.findViewById(R.id.llReporter).setVisibility(View.GONE);
			menu.findViewById(R.id.llReporterSeparator).setVisibility(View.GONE);
			menu.findViewById(R.id.btnAddPost).setVisibility(View.GONE);
			menu.findViewById(R.id.btnAddPostSeparator).setVisibility(View.GONE);
		}

		// Chat
		//
		if (App.UI_ENABLE_CHAT)
		{
			menu.findViewById(R.id.llChat).setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					mMenuCommandReceiver.handleCommand(R.integer.command_chat);
				}
			});
		}
		else
		{
			// Disable chat functionality
			menu.findViewById(R.id.llChat).setVisibility(View.GONE);
			menu.findViewById(R.id.llChatSeparator).setVisibility(View.GONE);
		}

		// Help
		//
		menu.findViewById(R.id.llHelp).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mMenuCommandReceiver.handleCommand(R.integer.command_help);
			}
		});

		// Connect
		menu.findViewById(R.id.btnTorStatus).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mMenuCommandReceiver.handleCommand(R.integer.command_toggle_online, true);
				onBeforeShow(); // update menu
			}
		});

		// Receive share
		menu.findViewById(R.id.llReceiveShare).setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				mMenuCommandReceiver.handleCommand(R.integer.command_receiveshare);
			}
		});

		menu.findViewById(R.id.llShareApp).setOnClickListener(new View.OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				mMenuCommandReceiver.handleCommand(R.integer.command_shareapp);
			}
		});

		// Preferences
		//
		View btnSettings = menu.findViewById(R.id.llSettings);
		btnSettings.setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				mMenuCommandReceiver.handleCommand(R.integer.command_settings);
			}
		});

		performRotateTransition(parent, menuRoot);
	}

	@SuppressLint("NewApi")
	protected void performRotateTransition(final View parent, final View content)
	{
		// Get bitmap from intent!!!
		Bitmap bmp = App.getInstance().getTransitionBitmap();
		if (bmp != null)
		{
			final ImageView snap = new ImageView(this);
			snap.setImageBitmap(bmp);
			((ViewGroup) parent).addView(snap);

			if (Build.VERSION.SDK_INT >= 11)
			{
				content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
				snap.setLayerType(View.LAYER_TYPE_HARDWARE, null);
			}
			else
			{
				content.setDrawingCacheEnabled(true);
				snap.setDrawingCacheEnabled(true);
			}

			content.setVisibility(View.INVISIBLE);

			// Animate!!!
			ActivitySwitcher.animationOut(snap, getWindowManager(), new ActivitySwitcher.AnimationFinishedListener()
			{
				@Override
				public void onAnimationFinished()
				{
					ActivitySwitcher.animationIn(content, getWindowManager(), new ActivitySwitcher.AnimationFinishedListener()
					{
						@Override
						public void onAnimationFinished()
						{
							content.post(new Runnable()
							{
								@Override
								@SuppressLint("NewApi")
								public void run()
								{
									((ViewGroup) parent).removeView(snap);
									App.getInstance().putTransitionBitmap(null);

									if (Build.VERSION.SDK_INT >= 11)
									{
										content.setLayerType(View.LAYER_TYPE_NONE, null);
									}
									else
									{
										content.setDrawingCacheEnabled(false);
									}

									content.setVisibility(View.VISIBLE);
									content.clearAnimation();
									onAfterResumeAnimation();
								}
							});
						}
					});
				}
			});
		}
		else
		{
			onAfterResumeAnimation();
		}
	}

	protected void onAfterResumeAnimation()
	{
		// Override this to start doing stuff after the animation is complete
	}

	private class MenuViewHolder
	{
		public TextView tvTorStatus;
		public ImageView ivTorStatus;
		public TextView tvNumFeeds;
		public TextView tvNumStories;
		public TextView tvNumChats;
	}

	private MenuViewHolder mMenuViewHolder;

	@Override
	public void onBeforeShow()
	{
		View menuView = mLeftSideMenu.getMenuView();
		if (menuView != null)
		{
			UpdateMenuTask task = new UpdateMenuTask();
			task.execute((Void) null);
		}
	}

	class UpdateMenuTask extends AsyncTask<Void, Void, Void>
	{
		private boolean isOnline;
		// private boolean isSignedIn;
		private int numFeeds;
		private int numPosts;

		@Override
		protected Void doInBackground(Void... values)
		{
			if (mMenuViewHolder == null)
			{
				mMenuViewHolder = new MenuViewHolder();
				View menuView = mLeftSideMenu.getMenuView();
				mMenuViewHolder.tvTorStatus = (TextView) menuView.findViewById(R.id.tvTorStatus);
				mMenuViewHolder.ivTorStatus = (ImageView) menuView.findViewById(R.id.btnTorStatus);
				mMenuViewHolder.tvNumFeeds = (TextView) menuView.findViewById(R.id.tvNumFeeds);
				mMenuViewHolder.tvNumStories = (TextView) menuView.findViewById(R.id.tvNumStories);
				mMenuViewHolder.tvNumChats = (TextView) menuView.findViewById(R.id.tvNumChats);
			}

			isOnline = App.getInstance().socialReader.isTorOnline();
			// isSignedIn = App.getInstance().socialReporter.isSignedIn();

			numFeeds = App.getInstance().socialReader.getSubscribedFeedsList().size();
			numPosts = 0;
			// if (isSignedIn)
			numPosts = App.getInstance().socialReporter.getPosts().size();
			return null;
		}

		@Override
		protected void onPostExecute(Void result)
		{
			// Update TOR connection status
			//
			if (isOnline)
			{
				mMenuViewHolder.tvTorStatus.setText(R.string.menu_tor_connected);
				mMenuViewHolder.ivTorStatus.setImageResource(R.drawable.ic_menu_tor_on);
			}
			else
			{
				mMenuViewHolder.tvTorStatus.setText(R.string.menu_tor_not_connected);
				mMenuViewHolder.ivTorStatus.setImageResource(R.drawable.ic_menu_tor_off);
			}

			mMenuViewHolder.tvNumFeeds.setText(getString(R.string.menu_num_feeds, numFeeds));
			mMenuViewHolder.tvNumStories.setText(getString(R.string.menu_num_stories, numPosts));
			mMenuViewHolder.tvNumChats.setText(getString(R.string.menu_num_chats, 0));
		}
	}

	private class MenuBroadcastReceiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			int commandId = intent.getIntExtra("command", 0);
			handleCommand(commandId);
		}

		public void handleCommand(int commandId)
		{
			handleCommand(commandId, false);
		}

		public void handleCommand(int commandId, boolean forceNow)
		{
			mDeferedCommand = commandId;
			if (mLeftSideMenu != null && mLeftSideMenu.isOpen() && !forceNow)
			{
				mLeftSideMenu.hide();
			}
			else
			{
				doHandleCommand();
			}
		}
	};

	private final MenuBroadcastReceiver mMenuCommandReceiver = new MenuBroadcastReceiver();

	private void doHandleCommand()
	{
		if (mDeferedCommand != 0)
		{
			int command = mDeferedCommand;
			mDeferedCommand = 0;

			onCommand(command, null);
		}
	}

	@Override
	public void onHide()
	{
		doHandleCommand(); // Handle command, if any
	}

	protected boolean onCommand(int command, Bundle commandParameters)
	{
		UICallbacks.handleCommand(this, command, null);
		return true;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		if (this.mLeftSideMenu != null)
			mLeftSideMenu.onConfigurationChanged();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == UICallbacks.RequestCode.CREATE_CHAT_ACCOUNT.Value)
		{
			if (resultCode == RESULT_OK)
			{
				App.getSettings().setChatUsernamePasswordSet();
				// Then redirect somewhere?
			}
		}
	}

	@Override public Object  getSystemService(String name) {
	     if (LAYOUT_INFLATER_SERVICE.equals(name)) {
	         if (mInflater == null) {    
	        	 LayoutInflater mParent = (LayoutInflater) super.getSystemService(name);
	        	 mInflater = mParent.cloneInContext(this);
	        	 mInflater.setFactory(new LayoutFactoryWrapper());
	    	 }
	         return mInflater;
	     }
	     return super.getSystemService(name);
	 }
}
