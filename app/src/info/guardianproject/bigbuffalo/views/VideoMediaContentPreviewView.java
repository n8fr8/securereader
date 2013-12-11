package info.guardianproject.bigbuffalo.views;

import info.guardianproject.bigbuffalo.App;
import info.guardianproject.bigbuffalo.R;
import info.guardianproject.bigbuffalo.api.MediaDownloader.MediaDownloaderCallback;
import info.guardianproject.bigbuffalo.models.OnMediaOrientationListener;
import info.guardianproject.bigbuffalo.uiutil.AnimationHelpers;
import info.guardianproject.iocipher.File;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Handler;
import android.provider.MediaStore.Video;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

import com.tinymission.rss.MediaContent;

public class VideoMediaContentPreviewView extends FrameLayout implements MediaDownloaderCallback, MediaContentPreviewView
{
	private MediaContent mMediaContent;
	private OnMediaOrientationListener mOrientationListener;
	private ImageView mImageView;
	private View mPlayView;
	private boolean mHasBeenRecycled;
	private boolean mIsLoading;
	private boolean mInSetMediaContent;
	private boolean mIsCached;
	private File mMediaFile;
	private Handler mHandler;
	private boolean mUseThisThread;

	public VideoMediaContentPreviewView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		initView(context);
	}

	public VideoMediaContentPreviewView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		initView(context);
	}

	public VideoMediaContentPreviewView(Context context)
	{
		super(context);
		initView(context);
	}

	public void setScaleType(ScaleType scaleType)
	{
		if (mImageView != null)
			mImageView.setScaleType(scaleType);
	}

	private void initView(Context context)
	{
		View.inflate(context, R.layout.video_preview_view, this);

		mImageView = (ImageView) findViewById(R.id.image);
		mPlayView = findViewById(R.id.btnPlay);
		mPlayView.setVisibility(View.GONE);
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
	public void mediaDownloaded(final File mediaFile)
	{
		// If mediaDownloaded is called while we're still in setMediaContent we
		// are
		// loading cached data.
		if (mInSetMediaContent)
			mIsCached = true;

		mIsLoading = false;

		mMediaFile = mediaFile;
		if (mMediaFile == null)
		{
			Log.v("VideoMediaContentPreviewView", "Failed to download media, no file.");
			return;
		}
		if (mHasBeenRecycled)
		{
			Log.v("VideoMediaContentPreviewView", "Media downloaded, but already recycled. Ignoring.");
			return;
		}

		if (mHandler == null && !mUseThisThread)
			mHandler = new Handler();

		Runnable getOrientationRunnable = new Runnable()
		{
			@Override
			public void run()
			{
				Log.v("VideoMediaContentPreviewView", "getOrientationThread");
				Bitmap preview = ThumbnailUtils.createVideoThumbnail(mMediaFile.getPath(), Video.Thumbnails.MINI_KIND);

				Runnable reportRunnable = new Runnable()
				{
					private Bitmap mBitmap;

					@Override
					public void run()
					{
						Log.v("VideoMediaContentPreviewView", "reporting orientation");

						boolean isPortrait = false;
						if (!mUseThisThread)
							AnimationHelpers.fadeOut(mImageView, 0, 0, false);
						if (mBitmap != null)
						{
							mImageView.setScaleType(ScaleType.CENTER_CROP);
							mImageView.setImageBitmap(mBitmap);
							isPortrait = (mBitmap.getHeight() > mBitmap.getWidth());
						}
						else
						{
							mImageView.setScaleType(ScaleType.CENTER_CROP);
							mImageView.setImageResource(R.drawable.img_placeholder);
						}
						if (!mUseThisThread)
							AnimationHelpers.fadeIn(mImageView, 500, 0, false);
						else
							AnimationHelpers.fadeIn(mImageView, 0, 0, false);
						notifyBitmapOrientation(isPortrait);
					}

					private Runnable init(Bitmap bitmap)
					{
						mBitmap = bitmap;
						return this;
					}

				}.init(preview);

				if (mUseThisThread)
					reportRunnable.run();
				else
					mHandler.post(reportRunnable);
			}
		};

		if (mUseThisThread)
		{
			getOrientationRunnable.run();
		}
		else
		{
			Thread getOrientationThread = new Thread(getOrientationRunnable);
			getOrientationThread.start();
		}
	}

	public void recycle()
	{
		mHasBeenRecycled = true;
		mImageView.setImageBitmap(null);
	}

	private void notifyBitmapOrientation(boolean isPortrait)
	{
		if (mOrientationListener != null)
		{
			mOrientationListener.onMediaOrientation(this, isPortrait ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
	}

	@Override
	public void mediaDownloadedNonVFS(java.io.File mediaFile) {
		// TODO Auto-generated method stub
		// Not being used for Video content at the moment.
	}
}