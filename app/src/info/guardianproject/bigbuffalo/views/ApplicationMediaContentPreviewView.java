package info.guardianproject.bigbuffalo.views;

import info.guardianproject.bigbuffalo.App;
import info.guardianproject.bigbuffalo.R;
import info.guardianproject.bigbuffalo.api.MediaDownloader.MediaDownloaderCallback;
import info.guardianproject.bigbuffalo.models.OnMediaOrientationListener;
import info.guardianproject.iocipher.File;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.tinymission.rss.MediaContent;

public class ApplicationMediaContentPreviewView extends FrameLayout implements MediaDownloaderCallback
{
	private MediaContent mMediaContent;
	private OnMediaOrientationListener mOrientationListener;
	private boolean mHasBeenRecycled;
	private boolean mIsLoading;
	private boolean mInSetMediaContent;
	private boolean mIsCached;
	private java.io.File mMediaFile;
	private Handler mHandler;
	private boolean mUseThisThread;

	public ApplicationMediaContentPreviewView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		initView(context);
	}

	public ApplicationMediaContentPreviewView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		initView(context);
	}

	public ApplicationMediaContentPreviewView(Context context)
	{
		super(context);
		initView(context);
	}

	private void initView(Context context)
	{
		View.inflate(context, R.layout.application_preview_view, this);
	}

	public void setMediaContent(MediaContent mediaContent, boolean enableInteraction, boolean forceBitwiseDownloads, boolean useThisThread)
	{
		mMediaContent = mediaContent;
		mInSetMediaContent = true;
		mUseThisThread = useThisThread;
		mIsLoading = App.getInstance().socialReader.loadMediaContent(mMediaContent, this, forceBitwiseDownloads);
		mInSetMediaContent = false;
	}

	public boolean isCached()
	{
		return mIsCached;
	}

	public boolean isLoading()
	{
		return mIsLoading;
	}

	/**
	 * Sets a listener that will be notified when media has been downloaded and
	 * it is known whether this media is in landscape or portrait mode.
	 * 
	 * @param listener
	 */
	public void setOnMediaOrientationListener(OnMediaOrientationListener listener)
	{
		this.mOrientationListener = listener;
	}

	@Override
	public void mediaDownloadedNonVFS(final java.io.File mediaFile)
	{
		mMediaContent.setDownloadedNonVFSFile(mediaFile);
		
		// If mediaDownloaded is called while we're still in setMediaContent we
		// are
		// loading cached data.
		if (mInSetMediaContent)
			mIsCached = true;

		mIsLoading = false;

		mMediaFile = mediaFile;
		
		if (mMediaFile == null)
		{
			Log.v("ApplicationMediaContentPreviewView", "Failed to download media, no file.");
			return;
		}
		if (mHasBeenRecycled)
		{
			Log.v("ApplicationMediaContentPreviewView", "Media downloaded, but already recycled. Ignoring.");
			return;
		}

		if (mHandler == null && !mUseThisThread)
			mHandler = new Handler();

		Runnable reportRunnable = new Runnable()
		{
			@Override
			public void run()
			{
				Log.v("ApplicationMediaContentPreviewView", "reporting orientation");
				notifyBitmapOrientation(false);
			}
		};

		if (mUseThisThread)
			reportRunnable.run();
		else
			mHandler.post(reportRunnable);
	}

	private void notifyBitmapOrientation(boolean isPortrait)
	{
		if (mOrientationListener != null)
		{
			mOrientationListener.onMediaOrientation(this, isPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
	}

	@Override
	public void mediaDownloaded(File mediaFile) {
		// TODO Auto-generated method stub
		// Only non iocipher files for Applications
	}
}