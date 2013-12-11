package info.guardianproject.bigbuffalo.ui;

import info.guardianproject.bigbuffalo.R;
import info.guardianproject.bigbuffalo.models.OnMediaOrientationListener;
import info.guardianproject.bigbuffalo.views.ApplicationMediaContentPreviewView;
import info.guardianproject.bigbuffalo.views.EPubMediaContentPreviewView;
import info.guardianproject.bigbuffalo.views.ImageMediaContentPreviewView;
import info.guardianproject.bigbuffalo.views.MediaContentPreviewView;
import info.guardianproject.bigbuffalo.views.VideoMediaContentPreviewView;

import java.util.ArrayList;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.view.View;
import android.widget.ImageView.ScaleType;

import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

public class MediaViewCollection implements OnMediaOrientationListener
{
	public interface OnMediaLoadedListener
	{
		void onMediaLoaded();
	}

	Item mStory;
	private ArrayList<MediaContent> mContent;
	private ArrayList<View> mContentViews;
	private final OnMediaLoadedListener mOnMediaLoadedListener;
	private final Context mContext;
	private boolean mContainsLoadedMedia;
	private boolean mIsFirstViewPortrait;
	private boolean mInCreateViews;
	private int mInCreateViewsIndex;
	private ScaleType mDefaultScaleType;
	private String mFirstContentType;

	public MediaViewCollection(Context context, OnMediaLoadedListener onMediaLoadedListener, Item story, boolean useThisThread)
	{
		this(context, onMediaLoadedListener, story, false, useThisThread);
	}

	public MediaViewCollection(Context context, OnMediaLoadedListener onMediaLoadedListener, Item story, boolean forceBitwiseDownloads, boolean useThisThread)
	{
		super();
		mContext = context;
		mOnMediaLoadedListener = onMediaLoadedListener;
		mStory = story;
		mDefaultScaleType = ScaleType.CENTER_CROP;
		refreshViews(forceBitwiseDownloads, useThisThread);
	}

	public void setScaleType(ScaleType scaleType)
	{
		if (scaleType != mDefaultScaleType)
		{
			mDefaultScaleType = scaleType;
			for (int i = 0; i < this.getCount(); i++)
			{
				View view = this.getView(i);
				if (view instanceof ImageMediaContentPreviewView)
				{
					((ImageMediaContentPreviewView) view).setScaleType(mDefaultScaleType);
				}
				else if (view instanceof VideoMediaContentPreviewView)
				{
					((VideoMediaContentPreviewView) view).setScaleType(mDefaultScaleType);
				}
			}
		}
	}

	public boolean containsLoadedMedia()
	{
		return mContainsLoadedMedia;
	}

	public boolean isFirstViewPortrait()
	{
		return mIsFirstViewPortrait;
	}

	/**
	 * Returns an icon for the media collection. Currently, this depends on the first item
	 * in the collection.
	 * @return An icon resource identifier
	 */
	public int placeholderIcon()
	{
		if (mFirstContentType != null)
		{
			boolean isVideo = mFirstContentType.startsWith("video/");
			boolean isAudio = mFirstContentType.startsWith("audio/");
			boolean isApplication = mFirstContentType.startsWith("application/vnd.android.package-archive");
			boolean isEpub = mFirstContentType.startsWith("application/epub+zip");
			if (isVideo)
				return R.drawable.ic_load_video;
			else if (isEpub)
				return R.drawable.ic_content_epub;
		}
		return R.drawable.ic_load_photo;
	}

	public CharSequence placeholderText()
	{
		if (mFirstContentType != null)
		{
			boolean isVideo = mFirstContentType.startsWith("video/");
			boolean isAudio = mFirstContentType.startsWith("audio/");
			boolean isApplication = mFirstContentType.startsWith("application/vnd.android.package-archive");
			boolean isEpub = mFirstContentType.startsWith("application/epub+zip");
			if (isEpub)
				return mContext.getText(R.string.download_epub_hint);
		}
		return null;
	}
	
	/**
	 * Some media types need a more user friendly download view, use "final size" for these.
	 * The rest will use default download view height of 50dp.
	 * @return True if the download view should extend across the whole media content view.
	 */
	public boolean placeholderUseFinalSize()
	{
		if (mFirstContentType != null)
		{
			boolean isVideo = mFirstContentType.startsWith("video/");
			boolean isAudio = mFirstContentType.startsWith("audio/");
			boolean isApplication = mFirstContentType.startsWith("application/vnd.android.package-archive");
			boolean isEpub = mFirstContentType.startsWith("application/epub+zip");
			if (isEpub)
				return true;
		}
		return false;
	}
	
	public void refreshViews(boolean forceBitwiseDownloads, boolean useThisThread)
	{
		mInCreateViews = true;
		mIsFirstViewPortrait = false;
		mContainsLoadedMedia = false;

		mContent = new ArrayList<MediaContent>();
		mContentViews = new ArrayList<View>();

		for (mInCreateViewsIndex = 0; mInCreateViewsIndex < mStory.getNumberOfMediaContent(); mInCreateViewsIndex++)
		{
			MediaContent mediaContent = mStory.getMediaContent(mInCreateViewsIndex);
			if (mediaContent == null || mediaContent.getUrl() == null || mediaContent.getType() == null)
				continue;

			if (mInCreateViewsIndex == 0)
				mFirstContentType = mediaContent.getType();
			
			View mediaView = null;

			boolean isVideo = mediaContent.getType().startsWith("video/");
			boolean isAudio = mediaContent.getType().startsWith("audio/");
			boolean isApplication = mediaContent.getType().startsWith("application/vnd.android.package-archive");
			boolean isEpub = mediaContent.getType().startsWith("application/epub+zip"); 
			if (isVideo)
			{
				VideoMediaContentPreviewView vmc = new VideoMediaContentPreviewView(mContext);
				vmc.setScaleType(mDefaultScaleType);
				vmc.setOnMediaOrientationListener(this);
				vmc.setMediaContent(mediaContent, false, forceBitwiseDownloads, useThisThread);
				if (vmc.isCached())
					mContainsLoadedMedia = true;
				mediaView = vmc;
			}
			else if (isApplication)
			{
				ApplicationMediaContentPreviewView amc = new ApplicationMediaContentPreviewView(mContext);
				amc.setOnMediaOrientationListener(this);
				amc.setMediaContent(mediaContent, false, forceBitwiseDownloads, useThisThread);
				if (amc.isCached())
					mContainsLoadedMedia = true;
				mediaView = amc;
			}
			else if (isEpub) {
				EPubMediaContentPreviewView amc = new EPubMediaContentPreviewView(mContext);
				amc.setOnMediaOrientationListener(this);
				amc.setMediaContent(mediaContent, false, forceBitwiseDownloads, useThisThread);
				if (amc.isCached())
					mContainsLoadedMedia = true;
				mediaView = amc;				
			}
			else
			{
				ImageMediaContentPreviewView imc = new ImageMediaContentPreviewView(mContext);
				imc.setScaleType(mDefaultScaleType);
				imc.setOnMediaOrientationListener(this);
				imc.setMediaContent(mediaContent, false, forceBitwiseDownloads, useThisThread);
				if (imc.isCached())
					mContainsLoadedMedia = true;
				mediaView = imc;
			}
			mContent.add(mediaContent);
			mContentViews.add(mediaView);
		}
		mInCreateViews = false;
	}

	public void recycle()
	{
		for (int i = 0; i < this.getCount(); i++)
		{
			View view = this.getView(i);
			if (view instanceof MediaContentPreviewView)
			{
				((MediaContentPreviewView) view).recycle();
			}
		}
		mContentViews.clear();
	}

	public int getCount()
	{
		return mContentViews.size();
	}

	public View getView(int position)
	{
		if (position < mContentViews.size())
			return mContentViews.get(position);
		return null;
	}

	public MediaContent getContentForView(View view)
	{
		for (int i = 0; i < mContentViews.size(); i++)
		{
			if (view == mContentViews.get(i))
				return mContent.get(i);
		}
		return null;
	}

	public View getViewForContent(MediaContent content)
	{
		for (int i = 0; i < mContent.size(); i++)
		{
			if (content == mContent.get(i))
				return mContentViews.get(i);
		}
		return null;
	}

	@Override
	public void onMediaOrientation(View view, int orientation)
	{
		if (view != null && orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
		{
			if ((mInCreateViews && mInCreateViewsIndex == 0) || (!mInCreateViews && view.equals(getView(0))))
				mIsFirstViewPortrait = true;
		}

		mContainsLoadedMedia = true;
		if (!mInCreateViews)
		{
			if (mOnMediaLoadedListener != null)
				mOnMediaLoadedListener.onMediaLoaded();
		}
	}

	public boolean isLoadingMedia()
	{
		boolean isLoading = false;

		for (int i = 0; i < this.getCount(); i++)
		{
			View view = this.getView(i);
			if (view instanceof MediaContentPreviewView)
			{
				isLoading |= ((MediaContentPreviewView) view).isLoading();
			}
		}
		return isLoading;
	}

}
