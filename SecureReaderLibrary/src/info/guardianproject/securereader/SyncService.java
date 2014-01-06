package info.guardianproject.securereader;

import java.util.ArrayList;

import com.tinymission.rss.Feed;
import com.tinymission.rss.MediaContent;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class SyncService extends Service {

	public static String LOGTAG = "SyncService";
	
	// ArrayList of SyncTask objects, basically a queue of work to be done
	ArrayList<SyncTask> syncList = new ArrayList<SyncTask>();

	// Single thread for background syncing
	Thread syncThread;
	
	// Service starting..
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    		
    	Log.v(LOGTAG,"onStartCommand");
    	
    	// We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    // Service ending
    @Override
    public void onDestroy() {
    	Log.v(LOGTAG,"onDestroy");
    }

    // Bind with Activities
    @Override
    public IBinder onBind(Intent intent) {
    	Log.v(LOGTAG,"onBind");
        return mBinder;
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        SyncService getService() {
            return SyncService.this;
        }
    }
    
    // Register callbacks that track what the SyncService is doing
    // This can be used for the sidebar display
    SyncServiceListener syncServiceCallback;
	public void setSyncServiceListener(SyncServiceListener _syncServiceCallback) {
		syncServiceCallback = _syncServiceCallback;
	}
    
	// This is the interface that must be implemented to get the callbacks
    public interface SyncServiceListener {
    	/*
    	int SYNC_EVENT_TYPE_FEED_UPADTED = 0;
    	int SYNC_EVENT_TYPE_FEED_ADDED = 1;
    	int SYNC_EVENT_TYPE_FEED_QUEUED = 2;
    	int SYNC_EVENT_TYPE_MEDIA_QUEUED = 3;
    	int SYNC_EVENT_TYPE_MEDIA_UPDATED = 4;
    	*/

    	public void syncEvent(SyncTask syncTask);    	
    }
    
    // The actual SyncTask class, defines each action to take
    // Right now only supports feed syncing
    public class SyncTask {
    	public static final int TYPE_FEED = 0;
    	public static final int TYPE_MEDIA = 1;
    	
    	public static final int ERROR = -1;
    	public static final int CREATED = 0;
    	public static final int QUEUED = 1;
    	public static final int STARTED = 2;
    	public static final int FINISHED = 3;
    	
    	Feed feed;
    	MediaContent mediaContent;
    	
    	//SyncServiceFeedFetcher.SyncServiceFeedFetchedCallback callback;
    	public int status = CREATED;
    	public int type = 0;
    	
    	//SyncTask(Feed _feed, SyncServiceFeedFetcher.SyncServiceFeedFetchedCallback _callback) {
    	SyncTask(Feed _feed) {
    		feed = _feed;
    		type = TYPE_FEED;
    		//callback = _callback;
    	}
    	
    	SyncTask(MediaContent _mediaContent) {
    		mediaContent = _mediaContent;
    		type = TYPE_MEDIA;
    	}
    	
    	void updateStatus(int newStatus) {
    		status = newStatus;
    		syncServiceEvent(this);
    	}
    	
    	int getStatus() {
    		return status;
    	}
    	
    	void start() {
    		if (type == TYPE_FEED) {
    			startFeedFetcher();
    		} else if (type == TYPE_MEDIA) {
    			startMediaDownloader();
    		}
    	}
    	
    	private void startMediaDownloader() {
    		SyncServiceMediaDownloader ssMediaDownloader = new SyncServiceMediaDownloader(SyncService.this,this);
    		
    		Log.v(LOGTAG,"Create and start ssMediaDownloader ");
    		syncThread = new Thread(ssMediaDownloader);
    		syncThread.start();
    		updateStatus(SyncTask.STARTED);
    	}
    	    	
    	private void startFeedFetcher() {
    		//SyncServiceFeedFetcher feedFetcher = new SyncServiceFeedFetcher(SyncService.this,feed);
    		//feedFetcher.setFeedUpdatedCallback(callback);
    		
    		Log.v(LOGTAG,"Create SyncServiceFeedFetcher");
    		SyncServiceFeedFetcher feedFetcher = new SyncServiceFeedFetcher(SyncService.this,this);
    		
    		Log.v(LOGTAG,"Create and start fetcherThread ");
    		syncThread = new Thread(feedFetcher);
    		syncThread.start();    	
    		updateStatus(SyncTask.STARTED);
    	}
    	
    	void taskComplete(int status) {
    		if (status == FINISHED) {
    			if (type == TYPE_FEED) {
    				//((App) getApplicationContext()).socialReader.setFeedAndItemData(feed);
    				SocialReader.getInstance(getApplicationContext()).backgroundDownloadFeedItemMedia(feed);
    			}
    			//if (callback != null) {
    			//	callback.feedFetched(feed);
    			//}
        		updateStatus(status);
    		}
    	}
    }
    
    //public void addFeedSyncTask(Feed feed, SyncServiceFeedFetcher.SyncServiceFeedFetchedCallback callback) {
    public void addFeedSyncTask(Feed feed) {
    	//SyncTask newSyncTask = new SyncTask(feed,callback);
    	SyncTask newSyncTask = new SyncTask(feed);
    	syncList.add(newSyncTask);
    	newSyncTask.updateStatus(SyncTask.QUEUED);
    	
		syncServiceEvent(newSyncTask);
    }
    
    void syncServiceEvent(SyncTask _syncTask) {
    	
    	// Need to do a better job of queue management
    	for (int i = 0; i < syncList.size(); i++) {
    		if (syncList.get(i).status == SyncTask.STARTED) {
    			break;
    		}
    		else if (syncList.get(i).status == SyncTask.QUEUED) {
    			syncList.get(i).start();
    			break;
    		}
    	}
    	
    	if (syncServiceCallback != null && SocialReader.getInstance(getApplicationContext()).appStatus == SocialReader.APP_IN_FOREGROUND) {
    		syncServiceCallback.syncEvent(_syncTask);
    	}
    }
    
    public void addMediaContentSyncTask(MediaContent mediaContent) {
    	SyncTask newSyncTask = new SyncTask(mediaContent);
    	syncList.add(newSyncTask);
    	newSyncTask.updateStatus(SyncTask.QUEUED);
    	
		syncServiceEvent(newSyncTask);
    }
}
