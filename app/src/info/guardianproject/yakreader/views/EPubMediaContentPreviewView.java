package info.guardianproject.yakreader.views;

import info.guardianproject.yakreader.R;
import info.guardianproject.iocipher.File;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import com.tinymission.rss.MediaContent;

public class EPubMediaContentPreviewView extends FrameLayout implements MediaContentPreviewView
{
	private MediaContent mMediaContent;
	private java.io.File mMediaFile;
	private Handler mHandler;
	private boolean mUseThisThread;

	public EPubMediaContentPreviewView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		initView(context);
	}

	public EPubMediaContentPreviewView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		initView(context);
	}

	public EPubMediaContentPreviewView(Context context)
	{
		super(context);
		initView(context);
	}

	private void initView(Context context)
	{
		View.inflate(context, R.layout.epub_preview_view, this);
		
	}

	@Override
	public void setMediaContent(MediaContent mediaContent, File mediaFile, java.io.File mediaFileNonVFS, boolean useThisThread)
	{
		mMediaContent = mediaContent;
		mMediaFile = mediaFileNonVFS;
		mUseThisThread = useThisThread;
		mMediaContent.setDownloadedNonVFSFile(mediaFile);
		if (mMediaFile == null)
		{
			Log.v("EPubMediaContentPreviewView", "Failed to download media, no file.");
			return;
		}

		if (mHandler == null && !mUseThisThread)
			mHandler = new Handler();

		Runnable reportRunnable = new Runnable()
		{
			@Override
			public void run()
			{
				Log.v("EPubMediaContentPreviewView", "reporting orientation");
			}
		};

		if (mUseThisThread)
			reportRunnable.run();
		else
			mHandler.post(reportRunnable);
		
	}

	@Override
	public void recycle()
	{
		// Do nothing
	}

	@Override
	public MediaContent getMediaContent()
	{
		return mMediaContent;
	}
}