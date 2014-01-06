package info.guardianproject.yakreader.views;

import info.guardianproject.securereader.MediaDownloader.MediaDownloaderCallback;
import info.guardianproject.yakreader.App;
import info.guardianproject.yakreader.models.OnMediaOrientationListener;
import info.guardianproject.yakreader.uiutil.AnimationHelpers;
import info.guardianproject.yakreader.uiutil.UIHelpers;
import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.tinymission.rss.MediaContent;

public class ImageMediaContentPreviewView extends ImageView implements MediaDownloaderCallback, MediaContentPreviewView
{
	private boolean mHasBeenRecycled;
	private MediaContent mMediaContent;
	private OnMediaOrientationListener mOrientationListener;
	private File mMediaFile;
	private Bitmap mRealBitmap;
	private int mMediaFileWidth;
	private int mMediaFileHeight;
	private boolean mIsLoading;
	private boolean mInSetMediaContent;
	private boolean mIsCached;
	private Thread mSetImageThread;
	private Handler mHandler;
	private boolean mUseThisThread;

	public ImageMediaContentPreviewView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		initView(context);
	}

	public ImageMediaContentPreviewView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		initView(context);
	}

	public ImageMediaContentPreviewView(Context context)
	{
		super(context);
		initView(context);
	}

	private void initView(Context context)
	{
		this.setScaleType(ScaleType.CENTER_CROP);
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

	@SuppressLint("NewApi")
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
			Log.v("ImageMediaContentPreviewView", "Failed to download media, no file.");
			return;
		}
		if (mHasBeenRecycled)
		{
			Log.v("ImageMediaContentPreviewView", "Media downloaded, but already recycled. Ignoring.");
			return;
		}

		if (mHandler == null && !mUseThisThread)
			mHandler = new Handler();

		Runnable getOrientationRunnable = new Runnable()
		{
			@Override
			public void run()
			{
				Log.v("ImageMediaContentPreviewView", "getOrientationThread");

				int w = getWidth();
				int h = getHeight();

				boolean isPortrait = false;
				if (w > 0 && h > 0)
				{
					mRealBitmap = UIHelpers.scaleToMaxGLSize(mMediaFile, w, h);
					if (mRealBitmap != null)
						isPortrait = mRealBitmap.getHeight() > mRealBitmap.getWidth();
				}
				else
				{
					try
					{
						BitmapFactory.Options o = new BitmapFactory.Options();
						o.inJustDecodeBounds = true;
						BufferedInputStream bis = new BufferedInputStream(new FileInputStream(mMediaFile));
						// BitmapFactory.decodeFile(mMediaFile.getAbsolutePath(),
						// o);
						BitmapFactory.decodeStream(bis, null, o);
						bis.close();
						isPortrait = o.outWidth < o.outHeight;
						mMediaFileWidth = o.outWidth;
						mMediaFileHeight = o.outHeight;
					}
					catch (FileNotFoundException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					catch (IOException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

				Runnable reportRunnable = new Runnable()
				{
					private boolean mIsPortrait;

					@Override
					public void run()
					{
						Log.v("ImageMediaContentPreviewView", "reporting orientation");
						if (mRealBitmap != null)
							setImageBitmap(mRealBitmap);
						notifyBitmapOrientation(mIsPortrait);
					}

					private Runnable init(boolean isPortrait)
					{
						mIsPortrait = isPortrait;
						return this;
					}
				}.init(isPortrait);

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

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom)
	{
		setBitmapIfDownloaded();
		super.onLayout(changed, left, top, right, bottom);
	}

	@Override
	public void setImageBitmap(Bitmap bm)
	{
		// If we are setting the image from a different thread, make sure to
		// fade it in.
		// If we, however, set it from this thread (as we do when closing the
		// full screen mode
		// view) we want it to show immediately!
		if (bm != null && !mUseThisThread)
			AnimationHelpers.fadeOut(this, 0, 0, false);
		super.setImageBitmap(bm);
		if (bm != null && !mUseThisThread)
			AnimationHelpers.fadeIn(this, 500, 0, false);
		else if (bm != null)
			AnimationHelpers.fadeIn(this, 0, 0, false);
	}

	public void recycle()
	{
		mHasBeenRecycled = true;
		setImageBitmap(null);
		if (mRealBitmap != null)
		{
			mRealBitmap.recycle();
			mRealBitmap = null;
		}
	}

	private synchronized void setBitmapIfDownloaded()
	{
		if (mMediaFile != null && mRealBitmap == null && mSetImageThread == null)
		{
			if (mHandler == null && !mUseThisThread)
				mHandler = new Handler();

			Runnable setImageRunnable = new Runnable()
			{
				@Override
				public void run()
				{
					int w = getWidth();
					int h = getHeight();
					Bitmap bmp = UIHelpers.scaleToMaxGLSize(mMediaFile, w, h);

					Runnable doSetImageRunnable = new Runnable()
					{
						private Bitmap mBitmap;

						@Override
						public void run()
						{
							mRealBitmap = mBitmap;
							setImageBitmap(mRealBitmap);
						}

						private Runnable init(Bitmap bitmap)
						{
							mBitmap = bitmap;
							return this;
						}

					}.init(bmp);

					if (mUseThisThread)
						doSetImageRunnable.run();
					else
						mHandler.post(doSetImageRunnable);
				}
			};

			if (mUseThisThread)
			{
				setImageRunnable.run();
			}
			else
			{
				mSetImageThread = new Thread(setImageRunnable);
				mSetImageThread.start();
			}
		}
	}

	@Override
	protected int getSuggestedMinimumHeight()
	{
		if (mMediaFile != null && mRealBitmap == null)
			return mMediaFileHeight;
		return super.getSuggestedMinimumHeight();
	}

	@Override
	protected int getSuggestedMinimumWidth()
	{
		if (mMediaFile != null && mRealBitmap == null)
			return mMediaFileWidth;
		return super.getSuggestedMinimumWidth();
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
		// Not being used for image content at the moment
	}

}