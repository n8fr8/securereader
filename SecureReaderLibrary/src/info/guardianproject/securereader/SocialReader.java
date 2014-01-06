/*
 *   This is the main class of the SocialReader portion of the application.
 *   It contains the management of online vs. offline
 *   It manages the database and tor connections
 *   It interfaces with the UI but doesn't contain any of the UI code itself
 *   It is therefore meant to allow the SocialReader to be pluggable with RSS
 *   API and UI and so on
 */

package info.guardianproject.securereader;

//import info.guardianproject.bigbuffalo.adapters.DownloadsAdapter;
import info.guardianproject.cacheword.CacheWordHandler;
import info.guardianproject.cacheword.ICacheWordSubscriber;
import info.guardianproject.cacheword.IOCipherMountHelper;
import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.VirtualFileSystem;
import info.guardianproject.onionkit.ui.OrbotHelper;

import info.guardianproject.securereader.MediaDownloader.MediaDownloaderCallback;
import info.guardianproject.securereader.Settings.UiLanguage;
import info.guardianproject.securereader.SyncServiceFeedFetcher.SyncServiceFeedFetchedCallback;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.tinymission.rss.Feed;
import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

public class SocialReader implements ICacheWordSubscriber
{
	public static final String LOGTAG = "SocialReader";

	public static final String CHAT_ROOM_NAME = "Courier_Chat";
	
	public static final String CONTENT_SHARING_MIME_TYPE = "application/x-bigbuffalo-bundle";
	public static final String CONTENT_SHARING_EXTENSION = "bbb";
	public static final String CONTENT_ITEM_EXTENSION = "bbi";
	
	public static final int APP_IN_FOREGROUND = 1;
	public static final int APP_IN_BACKGROUND = 0;
	public int appStatus = 0;

	public static final int FULL_APP_WIPE = 100;
	public static final int DATA_WIPE = 101;

	public final static String PROXY_HOST = "127.0.0.1";
	public final static int PROXY_HTTP_PORT = 8118; // default for Orbot/Tor

	public final static boolean RESET_DATABASE = false;

	public static final String MEDIA_CONTENT_FILE_PREFIX = "mc_";
	public static final String CONTENT_BUNDLE_FILE_PREFIX = "bundle_";
	
	public static final String TEMP_ITEM_CONTENT_FILE_NAME = "temp" + "." + CONTENT_ITEM_EXTENSION;
	
	public static final String VFS_SHARE_DIRECTORY = "share";
	public static final String NON_VFS_SHARE_DIRECTORY = "share";

	public final static String FILES_DIR_NAME = "files";
	public final static String IOCIPHER_FILE_NAME = "vfs.db";

	private String ioCipherFilePath;
	private VirtualFileSystem vfs;

	public static final int DEFAULT_NUM_FEED_ITEMS = 20;

	public long defaultFeedId = -1;
	public static final String BIG_BUFFALO_FEED_URL = "http://bigbuffalo.com/feed/";
	public static final String OPML_URL = "http://securereader.guardianproject.info/opml/opml.php"; // Needs to have lang=en_US or fa_IR or bo or bo_CN or zh_CN
	public static final String APP_FEED_URL = "http://securereader.guardianproject.info/swfeed/swfeed.php";
	public static final String EPUB_FEED_URL = "http://securereader.guardianproject.info/opds/opds.php";

	// In Milliseconds
	public final static long FEED_REFRESH_AGE = 300000; // 5 minutes
	public final static long OPML_CHECK_FREQUENCY = 43200000; // .5 day
	public final static long EXPIRATION_CHECK_FREQUENCY = 43200000; // .5 days
	

	// Constant to use when passing an item to be shared to the
	// securebluetoothsender as an extra in the intent
	public static final String SHARE_ITEM_ID = "SHARE_ITEM_ID";

	long lastFeedRefreshTime = 0;

	public Context applicationContext;
	DatabaseAdapter databaseAdapter;
	CacheWordHandler cacheWord;
	public SecureSettings ssettings;
	Settings settings;
	SyncServiceConnection syncServiceConnection;
	OrbotHelper oc;

	public static final int ONLINE = 1;
	public static final int NOT_ONLINE_NO_TOR = -1;
	public static final int NOT_ONLINE_NO_WIFI = -2;
	public static final int NOT_ONLINE_NO_WIFI_OR_NETWORK = -3;


	// This Timer and TimerHandler are used by the SyncService
	Timer periodicTimer;
	class TimerHandler extends Handler {
        @Override
        public void dispatchMessage(Message msg) {
        	Log.v(LOGTAG,"Timer Expired");

    		if (settings.syncFrequency() != Settings.SyncFrequency.Manual) {
    			if ((appStatus == SocialReader.APP_IN_BACKGROUND && settings.syncFrequency() == Settings.SyncFrequency.InBackground)
    				|| appStatus == SocialReader.APP_IN_FOREGROUND) {
    				backgroundSyncSubscribedFeeds();
    			}
    		}
			checkOPML();
			expireOldContent();
        }
	}
	TimerHandler timerHandler = new TimerHandler();

    private SyncService syncService;
    private SyncService.SyncServiceListener syncServiceListener;

    public void setSyncServiceListener(SyncService.SyncServiceListener listener) {
    	syncServiceListener = listener;

    	if (syncService != null) {
    		Log.v(LOGTAG,"Setting SyncServiceListener");
    		syncService.setSyncServiceListener(syncServiceListener);
    	} else {
    		Log.v(LOGTAG,"Can't set SyncServiceListener, syncService is null");
    		// No problem, we'll add it later, when we bind
    	}
    }

	class SyncServiceConnection implements ServiceConnection {

		public boolean isConnected = false;

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
        	syncService = ((SyncService.LocalBinder)service).getService();

        	Log.v(LOGTAG,"Connected to SyncService");

        	// Add Listener?
        	if (syncServiceListener != null) {
        		syncService.setSyncServiceListener(syncServiceListener);
        		Log.v(LOGTAG,"added syncServiceListener");
        	}

    		// Back to the front, check the syncing
    		if (settings.syncFrequency() != Settings.SyncFrequency.Manual) {
    			backgroundSyncSubscribedFeeds();

    	    	periodicTimer = new Timer();
    	        TimerTask periodicTask = new TimerTask() {
    	            @Override
                    public void run() {
    	            	timerHandler.sendEmptyMessage(0);
    	            }
    	        };
    	        periodicTimer.schedule(periodicTask, FEED_REFRESH_AGE, FEED_REFRESH_AGE);
    		}

    		isConnected = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.

        	syncService = null;

        	Log.v(LOGTAG,"Disconnected from SyncService");

        	isConnected = false;
        }
    };

    private static SocialReader socialReader = null;
    public static SocialReader getInstance(Context _context) {
    	if (socialReader == null) {
    		socialReader = new SocialReader(_context);
    	}
    	return socialReader;
    }
    
	private SocialReader(Context _context) {
		this.applicationContext = _context;
		this.settings = new Settings(applicationContext);
		this.cacheWord = new CacheWordHandler(applicationContext, this);
		this.oc = new OrbotHelper(applicationContext);
	}

	private boolean initialized = false;
	public void initialize() {
		Log.v(LOGTAG,"initialize");

	    if (!initialized) {

            initializeFileSystemCache();
            initializeDatabase();
            
            ssettings = new SecureSettings(databaseAdapter);
            Log.v(LOGTAG,"SecureSettings initialized");

            syncServiceConnection = new SyncServiceConnection();

            //Using startService() overrides the default service lifetime that is managed by bindService(Intent, ServiceConnection, int): it requires the service to remain running until stopService(Intent) is called, regardless of whether any clients are connected to it. Note that calls to startService() are not nesting: no matter how many times you call startService(), a single call to stopService(Intent) will stop it.
            applicationContext.startService(new Intent(applicationContext, SyncService.class));
            applicationContext.bindService(new Intent(applicationContext, SyncService.class), syncServiceConnection, Context.BIND_AUTO_CREATE);

            initialized = true;

	    } else {
	    	Log.v(LOGTAG,"Already initialized!");
	    }
	}

	public void uninitialize() {
		if (syncServiceConnection != null && syncServiceConnection.isConnected) {
			applicationContext.unbindService(syncServiceConnection);
		}

		// If we aren't going to do any background syncing, stop the service
		if (settings.syncFrequency() != Settings.SyncFrequency.InBackground)
		{
			Log.v(LOGTAG,"settings.syncFrequency() != Settings.SyncFrequency.InBackground so we are stopping the service");
			applicationContext.stopService(new Intent(applicationContext, SyncService.class));

			if (databaseAdapter != null && databaseAdapter.databaseReady()) {
	        	databaseAdapter.close();
	        }

	        if (vfs != null && vfs.isMounted()) {
	        	vfs.unmount();
	        }
		}
		initialized = false;			
	}
	
	private void loadOPMLFile() {
		Log.v(LOGTAG,"laodOPMLFile()");

		Resources res = applicationContext.getResources();
		InputStream inputStream = res.openRawResource(R.raw.bigbuffalo_opml);

		OPMLParser oParser = new OPMLParser(inputStream,
				new OPMLParser.OPMLParserListener() {
					@Override
					public void opmlParsed(ArrayList<OPMLParser.OPMLOutline> outlines) {
						Log.v(LOGTAG,"Finished Parsing OPML Feed");
						if (outlines != null) {
							for (int i = 0; i < outlines.size(); i++) {
								OPMLParser.OPMLOutline outlineElement = outlines.get(i);
								Feed newFeed = new Feed(outlineElement.text, outlineElement.htmlUrl);
								newFeed.setSubscribed(true);
								databaseAdapter.addOrUpdateFeed(newFeed);
								Log.v(LOGTAG,"May have added: " + newFeed.getTitle() + " " + newFeed.getFeedURL());
							}
						} else {
							Log.e(LOGTAG,"Received null after OPML Parsed");
						}
					}
				}
			);
	}
	
	private void expireOldContent() {
		Log.v(LOGTAG,"expireOldContent");
		if (settings.articleExpiration() != Settings.ArticleExpiration.Never) {
			if (settings.lastItemExpirationCheckTime() < System.currentTimeMillis() - EXPIRATION_CHECK_FREQUENCY) {
				Log.v(LOGTAG,"Checking Article Expirations");
				Date expirationDate = new Date(System.currentTimeMillis() - settings.articleExpirationMillis());
				databaseAdapter.deleteExpiredItems(expirationDate);
			}
		} else {
			Log.v(LOGTAG,"Settings set to never expire");
		}
	}

	private void checkOPML() {
		Log.v(LOGTAG,"checkOPML");

		UiLanguage lang = settings.uiLanguage();
		String opmlUrl = OPML_URL + "?lang=";
		if (lang == UiLanguage.Farsi) {
			opmlUrl = opmlUrl + "fa_IR";
		} else if (lang == UiLanguage.English) {
			opmlUrl = opmlUrl + "en_US_BO";
		} else if (lang == UiLanguage.Tibetan) {
			opmlUrl = opmlUrl + "bo_CN";
		} else if (lang == UiLanguage.Chinese) {
			opmlUrl = opmlUrl + "en_US_BO";
		}
		Log.v(LOGTAG, "OPML Feed Url: " + opmlUrl);
		
		if (isOnline() == ONLINE && settings.lastOPMLCheckTime() < System.currentTimeMillis() - OPML_CHECK_FREQUENCY) {
			OPMLParser oParser = new OPMLParser(SocialReader.this, opmlUrl,
				new OPMLParser.OPMLParserListener() {
					@Override
					public void opmlParsed(ArrayList<OPMLParser.OPMLOutline> outlines) {
						Log.v(LOGTAG,"Finished Parsing OPML Feed");
						if (outlines != null) {
							for (int i = 0; i < outlines.size(); i++) {
								OPMLParser.OPMLOutline outlineElement = outlines.get(i);
								Feed newFeed = new Feed(outlineElement.text, outlineElement.xmlUrl);
								newFeed.setSubscribed(true);
								databaseAdapter.addOrUpdateFeed(newFeed);
								Log.v(LOGTAG,"May have added: " + newFeed.getTitle() + " " + newFeed.getFeedURL());
							}
						} else {
							Log.e(LOGTAG,"Received null after OPML Parsed");
						}
					}
				}
			);
		} else {
			Log.v(LOGTAG,"Either not online or OPML last checked recently");
		}
	}

	// When the foreground app is paused
	public void onPause() {
		Log.v(LOGTAG, "SocialReader onPause");
		appStatus = SocialReader.APP_IN_BACKGROUND;
		cacheWord.disconnect();
	}

	// When the foreground app is unpaused
	public void onResume() {
		Log.v(LOGTAG, "SocialReader onResume");
		cacheWord.connectToService();

        appStatus = SocialReader.APP_IN_FOREGROUND;
	}
	
	public boolean isTorOnline() 
	{
		if (useTor() && oc.isOrbotInstalled() && oc.isOrbotRunning()) 
		{
			return true;
		} 
		else 
		{
			return false;
		}		
	}

	// This public method will indicate whether or not the application is online
	// it takes into account whether or not the application should be online (connectionMode)
	// as well as the physical network connection and tor status
	public int isOnline()
	{
		ConnectivityManager connectivityManager = (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo;

		if (settings.syncNetwork() == Settings.SyncNetwork.WifiOnly) {
			networkInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		} else {
			networkInfo = connectivityManager.getActiveNetworkInfo();
		}

		if (networkInfo != null && networkInfo.isConnectedOrConnecting()) {
			if (settings.requireTor()) {
				if (oc.isOrbotInstalled() && oc.isOrbotRunning()) {
					// Network is connected
					// Tor is running we are good
					return ONLINE;
				} else {
					// Tor not running or not installed
					return NOT_ONLINE_NO_TOR;
				}
			} else {
				// Network is connected and we don't have to use Tor
				return ONLINE;
			}
		} else {
			// Network not connected
			if (settings.syncNetwork() == Settings.SyncNetwork.WifiOnly) {
				return NOT_ONLINE_NO_WIFI;
			}
			else {
				return NOT_ONLINE_NO_WIFI_OR_NETWORK;
			}
		}
	}

	// Working hand in hand with isOnline this tells other classes whether or not they should use Tor when connecting
	public boolean useTor() {
		//if (settings.requireTor() || oc.isOrbotRunning()) {
		if (settings.requireTor()) {
			Log.v(LOGTAG, "USE TOR");
			return true;
		} else {
			Log.v(LOGTAG, "DON'T USE TOR");
			return false;
		}
	}

	public boolean connectTor(Activity _activity)
	{
		Log.v(LOGTAG, "Checking Tor");
		Log.v(LOGTAG, "isOrbotInstalled: " + oc.isOrbotInstalled());

		// This is returning the wrong value oc.isOrbotRunning, even if Orbot isn't installed
		Log.v(LOGTAG, "isOrbotRunning: " + oc.isOrbotRunning());

		if (!oc.isOrbotInstalled())
		{
			// This is getting intercepted by the lock screen at the moment
			oc.promptToInstall(_activity);
		}
		else if (!oc.isOrbotRunning())
		{
			// This seems to be working ok
			oc.requestOrbotStart(_activity);
		}

		return true;
	}
	
	public long getDefaultFeedId() {
		return defaultFeedId;
	}

	/*
	 * Return ArrayList of all Feeds in the database, these feed objects will
	 * not contain item data
	 */
	public ArrayList<Feed> getFeedsList()
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.getAllFeeds();
		}
		else
		{
			return new ArrayList<Feed>();
		}
	}

	/*
	 * Return ArrayList of all Feeds that the user is subscribed to in the
	 * database, these feed objects will not contain item data
	 */
	public ArrayList<Feed> getSubscribedFeedsList()
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.getSubscribedFeeds();
		}
		else
		{
			return new ArrayList<Feed>();
		}
	}

	/*
	 * Return ArrayList of all Feeds in the database that the user is NOT
	 * subscribed to, these feed objects will not contain item data
	 */
	public ArrayList<Feed> getUnsubscibedFeedsList()
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.getUnSubscribedFeeds();
		}
		else
		{
			return new ArrayList<Feed>();
		}
	}

	/*
	 * Utilizes the SyncService to Requests feed and feed items to be pulled from the network
	 */
	private void backgroundRequestFeedNetwork(Feed feed, SyncServiceFeedFetchedCallback callback)
	{
		Log.v(LOGTAG,"requestFeedNetwork");

		if (syncService != null) {
			Log.v(LOGTAG,"syncService != null");
			syncService.addFeedSyncTask(feed);
		} else {
			Log.v(LOGTAG,"syncService is null!");
		}
	}

	/*
	 * Requests feed and feed items to be pulled from the network returns false
	 * if feed cannot be requested from the network
	 */
	private boolean foregroundRequestFeedNetwork(Feed feed, FeedFetcher.FeedFetchedCallback callback)
	{
		FeedFetcher feedFetcher = new FeedFetcher(this);
		feedFetcher.setFeedUpdatedCallback(callback);

		if (isOnline() == ONLINE)
		{
			Log.v(LOGTAG, "Calling feedFetcher.execute: " + feed.getFeedURL());
			feedFetcher.execute(feed);
			return true;
		}
		else
		{
			return false;
		}
	}

	// Do network feed refreshing in the background
	private void backgroundSyncSubscribedFeeds()
	{
		Log.v(LOGTAG,"backgroundSyncSubscribedFeeds()");

		final ArrayList<Feed> feeds = getSubscribedFeedsList();
		for (Feed feed : feeds)
		{
			Log.v(LOGTAG,"Checking " + feed.getTitle());
			if (shouldRefresh(feed) && isOnline() == ONLINE) {
				Log.v(LOGTAG,"Going to request " + feed.getTitle());
				backgroundRequestFeedNetwork(feed, new SyncServiceFeedFetchedCallback() {
					@Override
					public void feedFetched(Feed _feed) {
						Log.v(LOGTAG,"Finished fecthing: " + _feed.getTitle());
					}
				});
			} else if (isOnline() != ONLINE) {
				Log.v(LOGTAG,feed.getTitle() + " not refreshing, not online: " + isOnline());
			} else {
				Log.v(LOGTAG,feed.getTitle() + " doesn't need refreshing");
			}
		}
	}

	public boolean manualSyncInProgress() {
		return requestPending;
	}

	// Request all of the feeds one at a time in the foreground
	int requestAllFeedsCurrentFeedIndex = 0;
	Feed compositeFeed = new Feed();

	boolean requestPending = false;
	FeedFetcher.FeedFetchedCallback finalCallback = null;

	public void manualSyncSubscribedFeeds(FeedFetcher.FeedFetchedCallback _finalCallback)
	{
		finalCallback = _finalCallback;

		if (!requestPending)
		{
			requestPending = true;

			final ArrayList<Feed> feeds = getSubscribedFeedsList();

			requestAllFeedsCurrentFeedIndex = 0;
			compositeFeed.clearItems();

			Log.v(LOGTAG, "requestAllFeedsCurrentFeedIndex:" + requestAllFeedsCurrentFeedIndex);

			if (feeds.size() > 0)
			{
				FeedFetcher.FeedFetchedCallback ffcallback = new FeedFetcher.FeedFetchedCallback()
				{
					@Override
					public void feedFetched(Feed _feed)
					{
						Log.v(LOGTAG, "Done Fetching: " + _feed.getFeedURL());

						compositeFeed.addItems(_feed.getItems());

						if (requestAllFeedsCurrentFeedIndex < feeds.size() - 1)
						{
							requestAllFeedsCurrentFeedIndex++;
							Log.v(LOGTAG, "requestAllFeedsCurrentFeedIndex:" + requestAllFeedsCurrentFeedIndex);
							foregroundRequestFeedNetwork(feeds.get(requestAllFeedsCurrentFeedIndex), this);
						}
						else
						{
							Log.v(LOGTAG, "Feed Fetcher Done!");
							requestPending = false;
							if (appStatus == SocialReader.APP_IN_FOREGROUND) {
								finalCallback.feedFetched(compositeFeed);
							}
						}
					}
				};

				Log.v(LOGTAG, "requestAllFeedsCurrentFeedIndex:" + requestAllFeedsCurrentFeedIndex);
				foregroundRequestFeedNetwork(feeds.get(requestAllFeedsCurrentFeedIndex), ffcallback);
			}
			Log.v(LOGTAG, "feeds.size is " + feeds.size());
		}
	}

	/*
	 * This is to manually sync a specific feed. It takes a callback that will
	 * be used to notify the listener that the network process is complete. This
	 * will override the default syncing behavior forcing an immediate network
	 * sync.
	 */
	public void manualSyncFeed(Feed feed, FeedFetcher.FeedFetchedCallback callback)
	{
		if (isOnline() == ONLINE)
		{
			// Adding an intermediate callback
			// Essentially, I never want it directly from the network, I want to
			// re-request it from the database

			final FeedFetcher.FeedFetchedCallback finalcallback = callback;
			FeedFetcher.FeedFetchedCallback intermediateCallback = callback;

			if (databaseAdapter != null && databaseAdapter.databaseReady())
			{
				intermediateCallback = new FeedFetcher.FeedFetchedCallback()
				{
					@Override
					public void feedFetched(Feed _feed)
					{
						if (finalcallback != null) {
							Feed updatedFeed = getFeed(_feed);
							if (appStatus == SocialReader.APP_IN_FOREGROUND) {
								finalcallback.feedFetched(updatedFeed);
							}
						}
					}
				};
			}

			Log.v(LOGTAG, "Refreshing Feed from Network");
			foregroundRequestFeedNetwork(feed, intermediateCallback);
		}
	}

	/*
	 * This will get a feed's items from the database.
	 */
	public Feed getFeed(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			Log.v(LOGTAG, "Feed from Database **" + feed.getTitle());
			feed = databaseAdapter.getFeedItems(feed, DEFAULT_NUM_FEED_ITEMS);
		}
		return feed;
	}

	Feed manualCompositeFeed = new Feed();

	/*
	public Feed getSubscribedFeedItems()
	{
		Feed returnFeed = new Feed();
		ArrayList<Feed> feeds = getSubscribedFeedsList();

		for (Feed feed : feeds)
		{
			returnFeed.addItems(getFeed(feed).getItems());
		}

		return returnFeed;
	}
	*/
	
	public Feed getSubscribedFeedItems()
	{
		return getCombinedSubscribedFeedItems();
	}	

	Feed cachedSubscribedFeedItems = new Feed();
	public Feed getCombinedSubscribedFeedItems()
	{
		if (cachedSubscribedFeedItems == null) {
			cachedSubscribedFeedItems = new Feed();
		}
		cachedSubscribedFeedItems.setDatabaseId(Feed.DEFAULT_DATABASE_ID);
		
		new AsyncTask<Void, Void, Feed>() {
			protected Feed doInBackground(Void... nothing) {
				if (databaseAdapter != null && databaseAdapter.databaseReady())
				{
					return databaseAdapter.getSubscribedFeedItems(DEFAULT_NUM_FEED_ITEMS);
				}
				
				return null;
		    }

		    protected void onProgressUpdate(Void... progress) {
		    }

		    protected void onPostExecute(Feed result) {
		    	cachedSubscribedFeedItems = result;
		    }			
		}.execute();

		return cachedSubscribedFeedItems;
	}
		
	public Feed getFeedItemsWithTag(Feed feed, String tag) {
		Feed returnFeed = new Feed();
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			returnFeed.addItems(databaseAdapter.getFeedItemsWithTag(feed, tag));
			Log.v(LOGTAG, "Feed from Database with tag**" + feed.getTitle() + " " + tag);
		}
		return feed;
		
			
	}

	private void initializeDatabase()
	{
		Log.v(LOGTAG,"initializeDatabase()");

		if (RESET_DATABASE) {
			applicationContext.deleteDatabase(DatabaseHelper.DATABASE_NAME);
		}

		databaseAdapter = new DatabaseAdapter(cacheWord, applicationContext);

		if (databaseAdapter.getAllFeeds().size() == 0) {
						
			Feed otherNewFeed = new Feed(applicationContext.getString(R.string.apps_feed_name), APP_FEED_URL);
			otherNewFeed.setSubscribed(true);
			databaseAdapter.addOrUpdateFeed(otherNewFeed);
			
			Feed thirdNewFeed = new Feed(applicationContext.getString(R.string.epubs_feed_name), EPUB_FEED_URL);
			thirdNewFeed.setSubscribed(true);
			databaseAdapter.addOrUpdateFeed(thirdNewFeed);
			
			loadOPMLFile();
		}

		Log.v(LOGTAG,"databaseAdapter initialized");
	}

	private java.io.File getNonVirtualFileSystemDir()
	{
		java.io.File filesDir;

		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
		{
			filesDir = new java.io.File(applicationContext.getExternalFilesDir(null), FILES_DIR_NAME + File.separator);
			if (!filesDir.exists())
			{
				filesDir.mkdirs();
			}
		}
		else
		{
			filesDir = applicationContext.getDir(FILES_DIR_NAME, Context.MODE_PRIVATE);
		}
		return filesDir;
	}
	
	
	private java.io.File getNonVirtualFileSystemInternalDir()
	{
		java.io.File filesDir;

		// Slightly more secure?
		filesDir = applicationContext.getDir(FILES_DIR_NAME, Context.MODE_PRIVATE);
		
		return filesDir;
	}
	
	private void initializeFileSystemCache()
	{
		Log.v(LOGTAG,"initializeFileSystemCache");

		// Check external storage, determine where to write..
		// Use IOCipher

		java.io.File filesDir = getNonVirtualFileSystemDir();

		ioCipherFilePath = filesDir + IOCIPHER_FILE_NAME;

		IOCipherMountHelper ioHelper = new IOCipherMountHelper(cacheWord);
		try {
		    vfs = ioHelper.mount(ioCipherFilePath);
		} catch ( IOException e ) {
		    // TODO: handle IOCipher open failure
		    Log.e(LOGTAG,"IOCipher open failure");
		    e.printStackTrace();
		}

		// Test it
		/*
		File testFile = new File(getFileSystemDir(),"test.txt");
		try {
	        BufferedWriter out = new BufferedWriter(new FileWriter(testFile));
	        out.write("test");
	        out.close();
		} catch (IOException e) {
			Log.e(LOGTAG,"FAILED TEST");			
		}
		*/
		Log.v(LOGTAG,"***Filesystem Initialized***");
	}

	private void deleteFileSystem()
	{
		if (vfs != null && vfs.isMounted()) {
			vfs.unmount();
		}

		// Delete all possible locations
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
		{
			// getExternalFilesDir() These persist
			java.io.File externalFilesDir = new java.io.File(applicationContext.getExternalFilesDir(null), FILES_DIR_NAME + "/");
			if (externalFilesDir.exists())
			{
				java.io.File[] externalFiles = externalFilesDir.listFiles();
				for (int i = 0; i < externalFiles.length; i++)
				{
					externalFiles[i].delete();
				}
				externalFilesDir.delete();
			}
		}

		java.io.File internalDir = applicationContext.getDir(FILES_DIR_NAME, Context.MODE_PRIVATE);
		java.io.File[] internalFiles = internalDir.listFiles();
		for (int i = 0; i < internalFiles.length; i++)
		{
			internalFiles[i].delete();
		}
		internalDir.delete();
	}

	public File getFileSystemDir()
	{
		// returns the root of the VFS
		return new File("/");
	}
	
	/*
	 * Checks to see if a feed should be refreshed from the network or not based
	 * upon the the last sync date/time
	 */
	public boolean shouldRefresh(Feed feed)
	{
		long refreshDate = new Date().getTime() - FEED_REFRESH_AGE;

		Log.v(LOGTAG, "Feed Databae Id " + feed.getDatabaseId());
		feed = databaseAdapter.fillFeedObject(feed);

		if (feed.getNetworkPullDate() != null)
		{
			Log.v(LOGTAG, "Feed pull date: " + feed.getNetworkPullDate().getTime());
		}
		else
		{
			Log.v(LOGTAG, "Feed pull date: NULL");
		}
		Log.v(LOGTAG, "Feed refresh date: " + refreshDate);

		if (feed.getNetworkPullDate() == null || feed.getNetworkPullDate().getTime() < refreshDate)
		{
			Log.v(LOGTAG, "Should refresh feed");
			return true;
		}
		else
		{
			Log.v(LOGTAG, "Get feeed from database");
			return false;
		}
	}

	/*
	 * Returns feed/list of favorite items for a specific feed
	 */
	public Feed getFeedFavorites(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			Feed favorites = databaseAdapter.getFavoriteFeedItems(feed);
			return favorites;
		}
		else
		{
			return new Feed();
		}
	}

	public ArrayList<Feed> getAllShared() {
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			//ArrayList<Feed> allFeeds = getFeedsList();
			ArrayList<Feed> shared = new ArrayList<Feed>();
			/*for (int i = 0; i < allFeeds.size(); i++)
			{
				Feed feedShared = databaseAdapter.getSharedFeedItems(allFeeds.get(i));
				shared.add(feedShared);
			}*/
			Feed feedShared = databaseAdapter.getAllSharedItems();
			shared.add(feedShared);
			return shared;
		}
		else
		{
			return new ArrayList<Feed>();
		}
	}

	/**
	 * Get number of received items.
	 *
	 * @return Number of items received.
	 */
	public int getAllSharedCount()
	{
		int count = 0;

		ArrayList<Feed> shareItemsPerFeed = getAllShared();
		if (shareItemsPerFeed != null)
		{
			Iterator<Feed> itFeed = shareItemsPerFeed.iterator();
			while (itFeed.hasNext())
			{
				Feed feed = itFeed.next();
				count += feed.getItemCount();
			}
		}
		return count;
	}
	
	/*
	 * Returns ArrayList of Feeds containing only the favorites
	 */
	public ArrayList<Feed> getAllFavorites()
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			ArrayList<Feed> allFeeds = getFeedsList();
			ArrayList<Feed> favorites = new ArrayList<Feed>();
			for (int i = 0; i < allFeeds.size(); i++)
			{
				favorites.add(getFeedFavorites(allFeeds.get(i)));
			}
			return favorites;
		}
		else
		{
			return new ArrayList<Feed>();
		}
	}

	/**
	 * Get number of favorite items.
	 *
	 * @return Number of items marked as favorite.
	 */
	public int getAllFavoritesCount()
	{
		int count = 0;

		ArrayList<Feed> favItemsPerFeed = getAllFavorites();
		if (favItemsPerFeed != null)
		{
			Iterator<Feed> itFeed = favItemsPerFeed.iterator();
			while (itFeed.hasNext())
			{
				Feed feed = itFeed.next();
				count += feed.getItemCount();
			}
		}
		return count;
	}

	public void markItemAsFavorite(Item item, boolean favorite)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			// Pass in the item that will be marked as a favorite
			// Take a boolean so we can "unmark" a favorite as well.
			item.setFavorite(favorite);
			setItemData(item);
		}
		else
		{
			Log.e(LOGTAG,"Database not ready: markItemAsFavorite");
		}
	}

	public long setItemData(Item item)
	{
		
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			return databaseAdapter.addOrUpdateItem(item);
		}
		else
		{
			Log.e(LOGTAG,"Database not ready: setItemData");
		}
		return -1;
	}

	/*
	 * Updates the feed data matching the feed object in the database. This
	 * ignores any items that are referenced in the feed object
	 *
	 * Returns null if update failed
	 */
	public Feed setFeedData(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			// First see if the feed has a valid database ID, if not, add a stub
			// and set the id
			if (feed.getDatabaseId() == Feed.DEFAULT_DATABASE_ID)
			{
				feed.setDatabaseId(databaseAdapter.addFeedIfNotExisting(feed.getTitle(), feed.getFeedURL()));
			}

			// Now update the record in the database, this fills more of the
			// data out
			int result = databaseAdapter.updateFeed(feed);
			Log.v(LOGTAG, feed.getTitle() + " setFeedData: " + result);

			if (result == 1)
			{
				// Return the feed as it may have a new database id
				return feed;
			}
			else
			{
				return null;
			}
		}
		else
		{
			Log.e(LOGTAG,"Database not ready: setFeedData");
			return null;
		}
	}

	/*
	 * Updates the feed data matching the feed object in the database. This
	 * includes any items that are referenced in the feed object.
	 */
	public void setFeedAndItemData(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			setFeedData(feed);

			for (Item item : feed.getItems())
			{
				// Make sure the feed ID is correct and the source is set correctly
				item.setFeedId(feed.getDatabaseId());
				item.setSource(feed.getTitle());
				setItemData(item);
			}
		}
		else
		{
			Log.e(LOGTAG,"Database not ready: setFeedAndItemData");
		}
	}

	public void backgroundDownloadFeedItemMedia(Feed feed)
	{
		feed = getFeed(feed);
		for (Item item : feed.getItems())
		{
			backgroundDownloadItemMedia(item);
		}
	}

	public void backgroundDownloadItemMedia(Item item)
	{
		if (settings.syncMode() != Settings.SyncMode.BitWise) {
			for (MediaContent contentItem : item.getMediaContent())
			{
				if (syncService != null) {
					Log.v(LOGTAG,"syncService != null");
					syncService.addMediaContentSyncTask(contentItem);
				} else {
					Log.v(LOGTAG,"syncService is null!");
				}
			}
		}
	}

	/*
	 * Adds a new feed to the database, this is used when the user manually
	 * subscribes to a feed
	 */
	public void addFeedByURL(String url, FeedFetcher.FeedFetchedCallback callback)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			Feed newFeed = new Feed("", url);
			newFeed.setDatabaseId(databaseAdapter.addFeedIfNotExisting("", url));

			if (callback != null)
			{
				foregroundRequestFeedNetwork(newFeed,callback);
			}
		}
		else
		{
			Log.e(LOGTAG,"Database not ready: addFeedByURL");
		}
	}

	public void subscribeFeed(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			feed.setSubscribed(true);
			databaseAdapter.addOrUpdateFeed(feed);
		}
		else
		{
			Log.e(LOGTAG,"Database not ready: subscribeFeed");
		}
	}

	// Remove this feed from the ones we are listening to. Do we need an
	// "addFeed(Feed...)" as well
	// or do we use the URL-form that's already there, i.e. addFeed(String url)?
	public void unsubscribeFeed(Feed feed)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			feed.setSubscribed(false);
			databaseAdapter.addOrUpdateFeed(feed);
			// databaseAdapter.deleteFeed(feed.getDatabaseId());
		}
		else
		{
			Log.e(LOGTAG,"Database not ready: unsubscribeFeed");
		}
	}

	public void removeFeed(Feed feed) {
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			feed.setSubscribed(false);
			databaseAdapter.addOrUpdateFeed(feed);
			databaseAdapter.deleteFeed(feed.getDatabaseId());
		}
		else
		{
			Log.e(LOGTAG,"Database not ready: removeFeed");
		}
	}

	// Stub for Intent.. We don't start an activity here since we are doing a
	// custom chooser in FragmentActivityWithMenu. We could though use a generic
	// chooser
	// Mikael, what do you think?
	// Since the share list is kind of a UI component I guess we shouldn't use a
	// generic chooser.
	// We could perhaps introduce a
	// "doShare(Intent shareInten, ResolveInfo chosenWayToShare)"?
	public Intent getShareIntent(Item item)
	{
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		sendIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, item.getTitle());
		sendIntent.putExtra(Intent.EXTRA_TEXT, item.getTitle() + "\n" + item.getLink() + "\n" + item.getCleanMainContent());

		sendIntent.putExtra(SocialReader.SHARE_ITEM_ID, item.getDatabaseId());
		sendIntent.setType("text/plain");
		sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		// applicationContext.startActivity(Intent.createChooser(sendIntent,"Share via: "));

		return sendIntent;
	}
	
	public Intent getSecureShareIntent(Item item, boolean onlyPrototype) {
		java.io.File sharingFile = new java.io.File("/test");
		if (!onlyPrototype)
			sharingFile = packageItemNonVFS(item.getDatabaseId());
		
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		//sendIntent.setDataAndType(Uri.parse(SecureShareContentProvider.CONTENT_URI + "item/" + item.getDatabaseId()),CONTENT_SHARING_MIME_TYPE);
		sendIntent.setDataAndType(Uri.fromFile(sharingFile), CONTENT_SHARING_MIME_TYPE);
		sendIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		Log.v(LOGTAG,"Secure Share Intent: " + Uri.parse(SecureShareContentProvider.CONTENT_URI + "item/" + item.getDatabaseId()).toString());
		
		return sendIntent;
	}

	// Stub for Intent
	public Intent getShareIntent(Feed feed)
	{
	    if (databaseAdapter == null || !databaseAdapter.databaseReady())
	    {
			Log.e(LOGTAG,"Database not ready: getShareIntent");
	    	return new Intent();
	    }

		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);

		if (feed != null)
		{
			sendIntent.putExtra(Intent.EXTRA_TEXT, feed.getTitle() + "\n" + feed.getLink() + "\n" + feed.getDescription());
		}
		else
		{
			ArrayList<Feed> subscribed = getSubscribedFeedsList();
			StringBuilder builder = new StringBuilder();

			for (Feed subscribedFeed : subscribed)
			{
				if (builder.length() > 0)
					builder.append("\n\n");
				builder.append(subscribedFeed.getTitle() + "\n" + subscribedFeed.getLink() + "\n" + subscribedFeed.getDescription());
			}
			sendIntent.putExtra(Intent.EXTRA_TEXT, builder.toString());
		}
		sendIntent.setType("text/plain");
		sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		return sendIntent;
	}

	public void doWipe(int wipeMethod)
	{
		Log.v(LOGTAG, "doing doWipe()");

		if (wipeMethod == DATA_WIPE)
		{
			dataWipe();
		}
		else if (wipeMethod == FULL_APP_WIPE)
		{
			dataWipe();
			deleteApp();
		}
		else
		{
			Log.v(LOGTAG, "This shouldn't happen");
		}
	}

	private void deleteApp()
	{
		Uri packageURI = Uri.parse("package:info.guardianproject.bigbuffalo");
		Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
		uninstallIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		applicationContext.startActivity(uninstallIntent);
	}

	private void dataWipe()
	{
		Log.v(LOGTAG, "deleteDatabase");
		//http://code.google.com/p/android/issues/detail?id=13727

		if (databaseAdapter != null && databaseAdapter.databaseReady()) {
			databaseAdapter.deleteAll();
			databaseAdapter.close();
		}

		if (vfs != null && vfs.isMounted()) {
			vfs.unmount();
		}
		
		applicationContext.deleteDatabase(DatabaseHelper.DATABASE_NAME);

		Log.v(LOGTAG, "Delete data");
		deleteFileSystem();
	}

	/*
	public void loadDisplayImageMediaContent(MediaContent mc, ImageView imageView)
	{
		loadDisplayImageMediaContent(mc, imageView, false);
	}

	// This should really be a GUI widget but it is here for now
	// Load the media for a specific item.
	public void loadDisplayImageMediaContent(MediaContent mc, ImageView imageView, boolean forceBitwiseDownload)
	{
		final ImageView finalImageView = imageView;

		MediaDownloaderCallback mdc = new MediaDownloaderCallback()
		{
			@Override
			public void mediaDownloaded(File mediaFile)
			{
					//Log.v(LOGTAG, "mediaDownloaded: " + mediaFile.getAbsolutePath());
				try {

					BufferedInputStream bis = new BufferedInputStream(new FileInputStream(mediaFile));

					BitmapFactory.Options bmpFactoryOptions = new BitmapFactory.Options();
					// Should take into account view size?
					if (finalImageView.getWidth() > 0 && finalImageView.getHeight() > 0)
					{
						//Log.v(LOGTAG, "ImageView dimensions " + finalImageView.getWidth() + " " + finalImageView.getHeight());

						bmpFactoryOptions.inJustDecodeBounds = true;

						//BitmapFactory.decodeFile(mediaFile.getAbsolutePath(), bmpFactoryOptions);
						BitmapFactory.decodeStream(bis, null, bmpFactoryOptions);
						bis.close();

						int heightRatio = (int) Math.ceil(bmpFactoryOptions.outHeight / (float) finalImageView.getHeight());
						int widthRatio = (int) Math.ceil(bmpFactoryOptions.outWidth / (float) finalImageView.getWidth());

						if (heightRatio > 1 && widthRatio > 1)
						{
							if (heightRatio > widthRatio)
							{
								bmpFactoryOptions.inSampleSize = heightRatio;
							}
							else
							{
								bmpFactoryOptions.inSampleSize = widthRatio;
							}
						}

						// Decode it for real
						bmpFactoryOptions.inJustDecodeBounds = false;
					}
					else
					{
						//Log.v(LOGTAG, "ImageView dimensions aren't set");
						bmpFactoryOptions.inSampleSize = 2;
					}

					//Bitmap bmp = BitmapFactory.decodeFile(mediaFile.getAbsolutePath(), bmpFactoryOptions);
					BufferedInputStream bis2 = new BufferedInputStream(new FileInputStream(mediaFile));
					Bitmap bmp = BitmapFactory.decodeStream(bis2, null, bmpFactoryOptions);
					bis2.close();

					finalImageView.setImageBitmap(bmp);
					finalImageView.invalidate();
					//Log.v(LOGTAG, "Should have set bitmap");

				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		};
		loadImageMediaContent(mc, mdc, forceBitwiseDownload);
	}
	*/

	public boolean loadMediaContent(MediaContent mc, MediaDownloaderCallback mdc) {
		return loadMediaContent(mc, mdc, false);
	}

	public boolean loadMediaContent(MediaContent mc, MediaDownloaderCallback mdc, boolean forceBitwiseDownload)
	{
		Log.v(LOGTAG, "loadImageMediaContent: " + mc.getUrl() + " " + mc.getType());
		
		final MediaDownloaderCallback mediaDownloaderCallback = mdc;
		
		if (mc.getType().equals("application/vnd.android.package-archive") || mc.getType().equals("application/epub+zip")) {
			
			java.io.File possibleFile = new java.io.File(this.getNonVirtualFileSystemDir(), MEDIA_CONTENT_FILE_PREFIX + mc.getDatabaseId());
			
			if (possibleFile.exists())
			{
				Log.v(LOGTAG, "Already downloaded: " + possibleFile.getAbsolutePath());
				mdc.mediaDownloadedNonVFS(possibleFile);
				return true;
			}
			else if (forceBitwiseDownload && isOnline() == ONLINE)
			//else if ((settings.syncMode() != Settings.SyncMode.BitWise || forceBitwiseDownload) && isOnline() == ONLINE)
			// only want to download this content type if they click it so...
			{
				Log.v(LOGTAG, "File doesn't exist, downloading");
	
				if (forceBitwiseDownload)
				{
					mdc = new DownloadsNotifierMediaDownloaderCallback(mc.getItemDatabaseId(), mdc);
				}
	
				NonVFSMediaDownloader mediaDownloader = new NonVFSMediaDownloader(this,possibleFile);
				mediaDownloader.setMediaDownloaderCallback(new NonVFSMediaDownloader.MediaDownloaderCallback() {
					@Override
					public void mediaDownloaded(java.io.File mediaFile) {
						mediaFile.setReadable(true, false); // Security alert
						mediaDownloaderCallback.mediaDownloadedNonVFS(mediaFile);
					}
				});
				mediaDownloader.execute(mc);
	
				return true;
			}
			else {
				return false;
			}
			
		} 
		else if (mc.getType().startsWith("audio") || mc.getType().startsWith("video")) 
		{
			File possibleFile = new File(getFileSystemDir(), MEDIA_CONTENT_FILE_PREFIX + mc.getDatabaseId());
			if (possibleFile.exists())
			{
				Log.v(LOGTAG, "Already downloaded: " + possibleFile.getAbsolutePath());
				mdc.mediaDownloaded(possibleFile);
				return true;
			}
			else if (forceBitwiseDownload && isOnline() == ONLINE)
			{
				Log.v(LOGTAG, "File doesn't exist, downloading");
	
				if (forceBitwiseDownload)
				{
					mdc = new DownloadsNotifierMediaDownloaderCallback(mc.getItemDatabaseId(), mdc);
				}
	
				MediaDownloader mediaDownloader = new MediaDownloader(this);
				mediaDownloader.setMediaDownloaderCallback(mdc);
	
				mediaDownloader.execute(mc);
	
				return true;
			}
			else
			{
				//Log.v(LOGTAG, "Can't download, not online or in bitwise mode");
				return false;
			}			
		}
		else if (mc.getType().startsWith("image")) 
		{
			File possibleFile = new File(getFileSystemDir(), MEDIA_CONTENT_FILE_PREFIX + mc.getDatabaseId());
			if (possibleFile.exists())
			{
				Log.v(LOGTAG, "Already downloaded: " + possibleFile.getAbsolutePath());
				mdc.mediaDownloaded(possibleFile);
				return true;
			}
			else if ((settings.syncMode() != Settings.SyncMode.BitWise || forceBitwiseDownload) && isOnline() == ONLINE)
			{
				Log.v(LOGTAG, "File doesn't exist, downloading");
	
				if (forceBitwiseDownload)
				{
					mdc = new DownloadsNotifierMediaDownloaderCallback(mc.getItemDatabaseId(), mdc);
				}
	
				MediaDownloader mediaDownloader = new MediaDownloader(this);
				mediaDownloader.setMediaDownloaderCallback(mdc);
	
				mediaDownloader.execute(mc);
	
				return true;
			}
			else
			{
				//Log.v(LOGTAG, "Can't download, not online or in bitwise mode");
				return false;
			}
		} else {
			Log.v(LOGTAG,"Not a media type we support");
			return false;
		}
	}


	private class DownloadsNotifierMediaDownloaderCallback implements MediaDownloaderCallback
	{
		private final MediaDownloaderCallback mWrapped;
		private final long mItemId;

		public DownloadsNotifierMediaDownloaderCallback(long itemId, MediaDownloaderCallback wrapped)
		{
			mItemId = itemId;
			mWrapped = wrapped;
			//DownloadsAdapter.downloading(mItemId);
		}

		@Override
		public void mediaDownloaded(File mediaFile)
		{
			//DownloadsAdapter.downloaded(mItemId);
			if (mWrapped != null)
				mWrapped.mediaDownloaded(mediaFile);
		}

		@Override
		public void mediaDownloadedNonVFS(java.io.File mediaFile) {
			//DownloadsAdapter.downloaded(mItemId);
			if (mWrapped != null)
				mWrapped.mediaDownloadedNonVFS(mediaFile);
		}
	}

	public File vfsTempItemBundle() {
		File tempContentFile = new File(getVFSSharingDir(), CONTENT_BUNDLE_FILE_PREFIX + System.currentTimeMillis() + Item.DEFAULT_DATABASE_ID + "." + SocialReader.CONTENT_SHARING_EXTENSION);
		return tempContentFile;
	}
	
	public java.io.File nonVfsTempItemBundle() {
		return new java.io.File(getNonVFSSharingDir(), CONTENT_BUNDLE_FILE_PREFIX + Item.DEFAULT_DATABASE_ID + "." + SocialReader.CONTENT_SHARING_EXTENSION);
	}
	
	private java.io.File getNonVFSSharingDir() {
		//java.io.File sharingDir = new java.io.File(getNonVirtualFileSystemInternalDir(), NON_VFS_SHARE_DIRECTORY);
		java.io.File sharingDir = new java.io.File(getNonVirtualFileSystemDir(), NON_VFS_SHARE_DIRECTORY);

		sharingDir.mkdirs();
		return sharingDir;
	}
	
	private File getVFSSharingDir() {
		File sharingDir = new File(getFileSystemDir(), VFS_SHARE_DIRECTORY);
		sharingDir.mkdirs();
		return sharingDir;
	}
	
	public java.io.File packageItemNonVFS(long itemId) {
		
		java.io.File possibleFile = new java.io.File(getNonVFSSharingDir(), CONTENT_BUNDLE_FILE_PREFIX + itemId + "." + SocialReader.CONTENT_SHARING_EXTENSION);
		
		Log.v(LOGTAG,"Going to package as: " + possibleFile.toString());
		
		if (possibleFile.exists())
		{
			Log.v(LOGTAG, "item already packaged " + possibleFile.getAbsolutePath());
		}
		else
		{
			Log.v(LOGTAG, "item not already packaged, going to do so now " + possibleFile.getAbsolutePath());
			
			try {
				
				if (databaseAdapter != null && databaseAdapter.databaseReady())
				{
					byte[] buf = new byte[1024]; 
			        int len; 
					
					Item itemToShare = databaseAdapter.getItemById(itemId);
					
					Log.v(LOGTAG,"Going to package " + itemToShare.toString());
					
					ZipOutputStream zipOutputStream = new ZipOutputStream(new java.io.FileOutputStream(possibleFile)); 
			        
					// Package content
					File tempItemContentFile = new File(this.getFileSystemDir(), SocialReader.TEMP_ITEM_CONTENT_FILE_NAME);
			        ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tempItemContentFile)));
			        output.writeObject(itemToShare);
			        output.flush();
			        output.close();
			        
			        zipOutputStream.putNextEntry(new ZipEntry(tempItemContentFile.getName()));
			        FileInputStream in = new FileInputStream(tempItemContentFile);
			        
			        while ((len = in.read(buf)) > 0) { 
			        	zipOutputStream.write(buf, 0, len); 
			        } 
			        zipOutputStream.closeEntry(); 
			        in.close(); 
			        // Finished content

			        // Now do media
					ArrayList<MediaContent> mc = itemToShare.getMediaContent();
					for (MediaContent mediaContent : mc) {
						File mediaFile = new File(getFileSystemDir(), MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
						Log.v(LOGTAG,"Checking for " + mediaFile.getAbsolutePath());
						if (mediaFile.exists())
						{
							Log.v(LOGTAG, "Media exists, adding it: " + mediaFile.getAbsolutePath());
							zipOutputStream.putNextEntry(new ZipEntry(mediaFile.getName()));
							FileInputStream mIn = new FileInputStream(mediaFile);
					        while ((len = mIn.read(buf)) > 0) { 
					        	zipOutputStream.write(buf, 0, len); 
					        } 
					        zipOutputStream.closeEntry(); 
					        mIn.close(); 
						} else {
							Log.v(LOGTAG, "Media doesn't exist, not adding it");
						}
					}
					
					zipOutputStream.close();
				}
				else
				{
					Log.e(LOGTAG,"Database not ready: packageItem");
				}
			} catch (FileNotFoundException fnfe) {
				Log.e(LOGTAG,"Can't write package file, not found");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();

			}

		}
		possibleFile.setReadable(true, false);
		return possibleFile;		
	}

	public Item getItemFromId(long itemId)
	{
		if (databaseAdapter != null && databaseAdapter.databaseReady())
		{
			Item item = databaseAdapter.getItemById(itemId);
			return item;
		}
		else
		{
			Log.e(LOGTAG, "Database not ready: getItemFromId");
		}
		return null;
	}

	public File packageItem(long itemId)
	{
		// IOCipher File
		File possibleFile = new File(getVFSSharingDir(), CONTENT_BUNDLE_FILE_PREFIX + itemId + "." + SocialReader.CONTENT_SHARING_EXTENSION);
		Log.v(LOGTAG,"possibleFile: " + possibleFile.getAbsolutePath());
		
		if (possibleFile.exists())
		{
			Log.v(LOGTAG, "item already packaged " + possibleFile.getAbsolutePath());
		}
		else
		{
			Log.v(LOGTAG, "item not already packaged, going to do so now " + possibleFile.getAbsolutePath());
			
			try {

				if (databaseAdapter != null && databaseAdapter.databaseReady())
				{
					byte[] buf = new byte[1024]; 
			        int len; 
					
					Item itemToShare = databaseAdapter.getItemById(itemId);
					
					Log.v(LOGTAG,"Going to package " + itemToShare.toString());
					
					ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(possibleFile)); 
			        
					// Package content
					File tempItemContentFile = new File(this.getFileSystemDir(), SocialReader.TEMP_ITEM_CONTENT_FILE_NAME);
			        ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tempItemContentFile)));
			        output.writeObject(itemToShare);
			        output.flush();
			        output.close();
			        
			        zipOutputStream.putNextEntry(new ZipEntry(tempItemContentFile.getName()));
			        FileInputStream in = new FileInputStream(tempItemContentFile);
			        
			        while ((len = in.read(buf)) > 0) { 
			        	zipOutputStream.write(buf, 0, len); 
			        } 
			        zipOutputStream.closeEntry(); 
			        in.close(); 
			        // Finished content

			        // Now do media
					ArrayList<MediaContent> mc = itemToShare.getMediaContent();
					for (MediaContent mediaContent : mc) {
						File mediaFile = new File(getFileSystemDir(), MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
						Log.v(LOGTAG,"Checking for " + mediaFile.getAbsolutePath());
						if (mediaFile.exists())
						{
							Log.v(LOGTAG, "Media exists, adding it: " + mediaFile.getAbsolutePath());
							zipOutputStream.putNextEntry(new ZipEntry(mediaFile.getName()));
							FileInputStream mIn = new FileInputStream(mediaFile);
					        while ((len = mIn.read(buf)) > 0) { 
					        	zipOutputStream.write(buf, 0, len); 
					        } 
					        zipOutputStream.closeEntry(); 
					        mIn.close(); 
						} else {
							Log.v(LOGTAG, "Media doesn't exist, not adding it");
						}
					}
					
					zipOutputStream.close();
				}
				else
				{
					Log.e(LOGTAG,"Database not ready: packageItem");
				}
			} catch (FileNotFoundException fnfe) {
				fnfe.printStackTrace();
				Log.e(LOGTAG,"Can't write package file, not found");
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		return possibleFile;
	}

    @Override
    public void onCacheWordUninitialized() {
    	Log.v(LOGTAG,"onCacheWordUninitialized");

    	uninitialize();
    }

    @Override
    public void onCacheWordLocked() {
    	Log.v(LOGTAG, "onCacheWordLocked");

    	uninitialize();
    }

    @Override
    public void onCacheWordOpened() {
    	Log.v(LOGTAG,"onCacheWordOpened");
        initialize();
    }
}
