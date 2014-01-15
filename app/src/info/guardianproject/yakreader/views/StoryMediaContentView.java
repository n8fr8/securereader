package info.guardianproject.yakreader.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import info.guardianproject.yakreader.R;
import info.guardianproject.yakreader.adapters.StoryItemMediaContentPagerAdapter;
import info.guardianproject.yakreader.ui.MediaViewCollection;
import info.guardianproject.yakreader.ui.MediaViewCollection.MediaContentLoadInfo;
import info.guardianproject.yakreader.ui.MediaViewCollection.OnMediaLoadedListener;
import info.guardianproject.yakreader.ui.OnMediaItemClickedListener;
import info.guardianproject.yakreader.uiutil.UIHelpers;
import info.guardianproject.yakreader.widgets.DottedProgressView;
import info.guardianproject.yakreader.widgets.NestedViewPager;

public class StoryMediaContentView extends FrameLayout implements View.OnClickListener, OnMediaLoadedListener 
{
	private NestedViewPager mViewPager;
	private DottedProgressView mCurrentPageIndicator;
	private ImageView mImageView;
	private View mDownloadView;
	private boolean mShowPlaceholder;
	private float mHeightInhibitor;
	private boolean mShowDLButtonForBitWise;
	private boolean mShowPlaceholderWhileLoading;
	private boolean mAllowFullScreenMediaViewing;
	private boolean mUseFinalSizeForDownloadView;
	private MediaViewCollection mMediaViewCollection;
	
	public StoryMediaContentView(Context context)
	{
		super(context);
		initView(context, null);
	}

	public StoryMediaContentView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		initView(context, attrs);
	}

	public StoryMediaContentView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		initView(context, attrs);
	}

	private void initView(Context context, AttributeSet attrs)
	{
		mShowPlaceholder = false;
		mHeightInhibitor = 1.75f;
		if (attrs != null)
		{
			TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.StoryMediaContentView);
			mShowPlaceholder = a.getBoolean(R.styleable.StoryMediaContentView_show_placeholder, false);
			mHeightInhibitor = a.getFloat(R.styleable.StoryMediaContentView_height_inhibitor, 1.75f);
			a.recycle();
		}
		if (this.isInEditMode())
			mShowPlaceholder = true;
		mUseFinalSizeForDownloadView = false;
	}

	public void setHeightInhibitor(float heightInhibitor)
	{
		mHeightInhibitor = heightInhibitor;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		if (!mShowPlaceholder && (mMediaViewCollection == null || mMediaViewCollection.getCount() == 0))
		{
			setMeasuredDimension(0, 0);
			return;
		}

		if (mMediaViewCollection != null && !mMediaViewCollection.containsLoadedMedia() && !mShowPlaceholderWhileLoading)
		{
			if (this.mShowDLButtonForBitWise)
			{
				Log.v("MediaView", "No cached image, but show DL button");
				super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			}
			else
			{
				Log.v("MediaView", "No cached image, set height to 0");
				setMeasuredDimension(0, 0);
			}
		}
		else if (mHeightInhibitor == 0)
		{
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			Log.v("MediaView", "Contains cached image, no constraints, set height to " + this.getMeasuredHeight());
		}
		else if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED)
		{
			int inhibitedHeightSpec = MeasureSpec.makeMeasureSpec((int) (MeasureSpec.getSize(widthMeasureSpec) / mHeightInhibitor),
					MeasureSpec.getMode(widthMeasureSpec));
			super.onMeasure(widthMeasureSpec, inhibitedHeightSpec);
			Log.v("MediaView", "Cached image, set height to " + this.getMeasuredHeight());
		}
		else
		{
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			int width = this.getMeasuredWidth();
			if (mHeightInhibitor != 0)
			{
				int height = (int) (width / mHeightInhibitor);
				super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
			}
			Log.v("MediaView", "Contains cached image, set height to " + this.getMeasuredHeight());
		}
	}
	
	public void setMediaCollection(MediaViewCollection mediaViewCollection, boolean allowFullScreenMediaViewing, boolean showDLButtonForBitWise)
	{
		mShowDLButtonForBitWise = showDLButtonForBitWise;
		mAllowFullScreenMediaViewing = allowFullScreenMediaViewing;

		if (mMediaViewCollection != null)
			mMediaViewCollection.removeListener(this);
		mMediaViewCollection = mediaViewCollection;
		if (mMediaViewCollection != null)
			mMediaViewCollection.addListener(this);
		updateView();
	}

	public void setScaleType(boolean setToFitIfFirstPhotoIsPortrait)
	{
		if (mMediaViewCollection != null)
		{
			if (setToFitIfFirstPhotoIsPortrait && mMediaViewCollection.isFirstViewPortrait())
				mMediaViewCollection.setScaleType(ScaleType.FIT_CENTER);
			else
				mMediaViewCollection.setScaleType(ScaleType.CENTER_CROP);
		}
	}

	public void updateView()
	{
		this.removeAllViews();
		if (mMediaViewCollection == null || mMediaViewCollection.getCount() == 0)
		{
			if (mShowPlaceholder)
				createPlaceholderView();
		}
		else if (!mMediaViewCollection.containsLoadedMedia())
		{
			if (mShowDLButtonForBitWise)
			{
				if (mMediaViewCollection.isLoadingMedia())
					createDownloadingView(mUseFinalSizeForDownloadView);
				else
					createDownloadView(mUseFinalSizeForDownloadView);
			}
			else if (mShowPlaceholderWhileLoading)
				createDownloadingView(true);
		}
		else if (mMediaViewCollection.getCountLoaded() > 1)
		{
			createMultiImageView();
		}
		else if (mMediaViewCollection.getCountLoaded() == 1)
		{
			createSingleImageView();
		}
	}

	public int getCount()
	{
		if (mMediaViewCollection == null)
			return 0;
		return mMediaViewCollection.getCount();
	}

	public int getCurrentItemIndex()
	{
		if (this.getChildCount() > 0 && mViewPager != null && this.getChildAt(0) == mViewPager)
			return mViewPager.getCurrentItem();
		return 0;
	}

	public void setShowPlaceholder(boolean showPlaceholder)
	{
		this.mShowPlaceholder = showPlaceholder;
		updateView();
	}

	public void setShowPlaceholderWhileLoading(boolean showPlaceholderWhileLoading)
	{
		this.mShowPlaceholderWhileLoading = showPlaceholderWhileLoading;
		updateView();
	}

	private void createMultiImageView()
	{
		this.removeAllViews();

		View content = LayoutInflater.from(getContext()).inflate(R.layout.story_media_content_view_multi, this, false);

		mCurrentPageIndicator = (DottedProgressView) content.findViewById(R.id.currentPageIndicator);
		mViewPager = (NestedViewPager) content.findViewById(R.id.contentPager);
		mViewPager.setViewPagerIndicator(mCurrentPageIndicator);
		mViewPager.setAdapter(new StoryItemMediaContentPagerAdapter(getContext(), mMediaViewCollection.getLoadedViews(), mAllowFullScreenMediaViewing));
		if (mAllowFullScreenMediaViewing)
		{
			mViewPager.setPropagateClicks(false);
		}
		else
		{
			mViewPager.setPropagateClicks(true);
		}
		addView(content);
	}

	private void createSingleImageView()
	{
		MediaContentPreviewView contentView = mMediaViewCollection.getFirstView();
		View view = (View) contentView;
		if (view.getParent() != null)
			((ViewGroup) view.getParent()).removeView(view);
		if (mAllowFullScreenMediaViewing)
		{
			view.setOnClickListener(new OnMediaItemClickedListener(contentView.getMediaContent()));
		}
		else
		{
			view.setOnClickListener(null);
			view.setClickable(false);
		}
		this.addView(view);
	}

	private void createPlaceholderView()
	{
		if (mImageView == null)
		{
			mImageView = new ImageView(getContext());
			mImageView.setClickable(false);
			mImageView.setScaleType(ScaleType.CENTER_CROP);
			mImageView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		}
		mImageView.setImageResource(R.drawable.img_placeholder);
		this.addView(mImageView);
	}

	private void createDownloadView(boolean useFinalSize)
	{
		if (useFinalSize || placeholderUseFinalSize())
		{
			mDownloadView = LayoutInflater.from(getContext()).inflate(R.layout.story_media_bitwise_placeholder_large, this, false);
		}
		else
		{
			mDownloadView = LayoutInflater.from(getContext()).inflate(R.layout.story_media_bitwise_placeholder, this, false);
		}
		
		mDownloadView.setOnClickListener(this);
		ImageView iv = ((ImageView) mDownloadView.findViewById(R.id.ivDownloadIcon));
		iv.setImageResource(placeholderIcon());
		TextView tv = (TextView) mDownloadView.findViewById(R.id.tvDownload);
		if (tv != null)
			tv.setText(placeholderText());
		this.addView(mDownloadView);
		iv.clearAnimation();
	}

	private void createDownloadingView(boolean useFinalSize)
	{
		if (mDownloadView == null)
		{
			mDownloadView = LayoutInflater.from(getContext()).inflate(R.layout.story_media_bitwise_placeholder, this, false);
		}
		if (useFinalSize || placeholderUseFinalSize())
			mDownloadView.getLayoutParams().height = LayoutParams.MATCH_PARENT;
		else
			mDownloadView.getLayoutParams().height = UIHelpers.dpToPx(50, getContext());

		mDownloadView.setOnClickListener(null);
		ImageView iv = ((ImageView) mDownloadView.findViewById(R.id.ivDownloadIcon));
		iv.setImageResource(R.drawable.ic_context_load);
		TextView tv = (TextView) mDownloadView.findViewById(R.id.tvDownload);
		if (tv != null)
			tv.setText(null);
		this.addView(mDownloadView);
		iv.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.rotate));
	}

	@Override
	public void onClick(View v)
	{
		if (v == mDownloadView)
		{
			mMediaViewCollection.createViews(true);
			updateView();
		}
	}
	
	/**
	 * @return true is this view will create a view for us to look at, i.e. media is
	 * already loaded or placeholder will be shown. Also if download button is displayed.
	 */
	public boolean willCreateView()
	{
		if (mMediaViewCollection == null || mMediaViewCollection.getCount() == 0)
		{
			if (mShowPlaceholder)
				return true;
		}
		else if (!mMediaViewCollection.containsLoadedMedia())
		{
			if (mShowDLButtonForBitWise)
			{
				if (mMediaViewCollection.isLoadingMedia())
					return true;
				else
					return true;
			}
			else if (mShowPlaceholderWhileLoading)
				return true;
		}
		else if (mMediaViewCollection.getCountLoaded() > 1)
		{
			return true;
		}	
		else if (mMediaViewCollection.getCountLoaded() == 1)
		{
			return true;
		}
		return false;
	}
	
	public void setUseFinalSizeForDownloadView(boolean useFinalSizeForDownloadView)
	{
		mUseFinalSizeForDownloadView = useFinalSizeForDownloadView;
	}
	
	/**
	 * Returns an icon for the media collection. Currently, this depends on the first item
	 * in the collection.
	 * @return An icon resource identifier
	 */
	public int placeholderIcon()
	{
		if (mMediaViewCollection != null)
		{
			MediaContentLoadInfo info = mMediaViewCollection.getFirstLoadInfo();
			if (info != null)
			{
				if (info.isVideo())
					return R.drawable.ic_load_video;
				else if (info.isEpub())
					return R.drawable.ic_content_epub;
			}
		}
		return R.drawable.ic_load_photo;
	}

	public CharSequence placeholderText()
	{
		if (mMediaViewCollection != null)
		{
			MediaContentLoadInfo info = mMediaViewCollection.getFirstLoadInfo();
			if (info != null)
			{
				if (info.isEpub())
					return getContext().getText(R.string.download_epub_hint);
			}
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
		if (mMediaViewCollection != null)
		{
			MediaContentLoadInfo info = mMediaViewCollection.getFirstLoadInfo();
			if (info != null)
			{
				if (info.isEpub())
					return true;
			}
		}
		return false;
	}

	@Override
	public void onIsFirstViewPortraitChanged(MediaViewCollection collection, boolean isFirstViewPortrait)
	{
	}

	@Override
	public void onViewLoaded(MediaViewCollection collection)
	{
		updateView();
	}
}
