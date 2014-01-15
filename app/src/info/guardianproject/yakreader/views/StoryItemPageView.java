package info.guardianproject.yakreader.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewStub;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import info.guardianproject.yakreader.R;
import info.guardianproject.yakreader.App;
import info.guardianproject.yakreader.adapters.StoryListAdapter.OnTagClickedListener;
import info.guardianproject.yakreader.models.FeedFilterType;
import info.guardianproject.yakreader.ui.MediaViewCollection;
import info.guardianproject.yakreader.ui.MediaViewCollection.OnMediaLoadedListener;
import info.guardianproject.yakreader.ui.UICallbacks;
import info.guardianproject.yakreader.uiutil.UIHelpers;

import com.tinymission.rss.Item;

public class StoryItemPageView extends RelativeLayout implements OnMediaLoadedListener 
{
	private enum PageMode
	{
		UNKNOWN, NO_PHOTO, LANDSCAPE_PHOTO, PORTRAIT_PHOTO
	}

	protected TextView mTvTitle;
	protected TextView mTvAuthor;
	protected TextView mTvContent;
	protected StoryMediaContentView mMediaContentView;
	protected TextView mTvTime;
	protected TextView mTvSource;

	protected Item mStory = null;
	protected float mDefaultTextSize = 10;
	protected float mDefaultAuthorTextSize = 10;
	protected boolean mShowAuthor = true;
	protected boolean mShowContent = true;
	protected boolean mShowSource = true;
	private boolean mAllowFullScreenMediaViewing;
	private PageMode mCurrentPageMode;
	private boolean mShowTags;
	private OnTagClickedListener mOnTagClickedListener;
	private MediaViewCollection mMediaViewCollection;

	public StoryItemPageView(Context context)
	{
		super(context);
		init(null);
	}

	public StoryItemPageView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init(attrs);
	}

	public StoryItemPageView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init(attrs);
	}

	private void init(AttributeSet attrs)
	{
		mCurrentPageMode = PageMode.UNKNOWN;

		if (attrs != null && !this.isInEditMode())
		{
			TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.StoryItemPageView);
			mShowAuthor = a.getBoolean(R.styleable.StoryItemPageView_show_author, true);
			mShowContent = a.getBoolean(R.styleable.StoryItemPageView_show_content, true);
			mShowSource = a.getBoolean(R.styleable.StoryItemPageView_show_source, true);
			a.recycle();
		}
	}
	
	public void recycle()
	{
		if (mMediaViewCollection != null)
			mMediaViewCollection.recycle();
		mMediaViewCollection = null;
	}

	/**
	 * Based on the media this Item contains, determine which kind of layout we
	 * need for the display.
	 */
	private PageMode getRequiredPageMode()
	{
		if (mMediaViewCollection != null && (mMediaViewCollection.getCount() == 0 || !mMediaViewCollection.containsLoadedMedia()))
			return PageMode.NO_PHOTO;
		else if (mMediaViewCollection != null && mMediaViewCollection.isFirstViewPortrait())
			return PageMode.PORTRAIT_PHOTO;
		return PageMode.LANDSCAPE_PHOTO;
	}

	public void recreateViews()
	{
		PageMode requiredPageMode = getRequiredPageMode();
		if (mCurrentPageMode == requiredPageMode)
			return; // No change
		mCurrentPageMode = requiredPageMode;

		this.removeAllViews();

		if (requiredPageMode == PageMode.NO_PHOTO)
			LayoutInflater.from(getContext()).inflate(R.layout.story_item_page_merge_no_photo, this, true);
		else if (requiredPageMode == PageMode.PORTRAIT_PHOTO)
			LayoutInflater.from(getContext()).inflate(R.layout.story_item_page_merge_portrait_photo, this, true);
		else
			LayoutInflater.from(getContext()).inflate(R.layout.story_item_page_merge_landscape_photo, this, true);

		mTvTitle = (TextView) findViewById(R.id.tvTitle);
		mTvAuthor = (TextView) findViewById(R.id.tvAuthor);
		mTvContent = (TextView) findViewById(R.id.tvContent);
		mMediaContentView = (StoryMediaContentView) findViewById(R.id.ivPhotos);
		mTvTime = (TextView) findViewById(R.id.tvTime);
		mTvSource = (TextView) findViewById(R.id.tvSource);

		if (!this.isInEditMode() && mTvContent != null)
			mDefaultTextSize = mTvContent.getTextSize();
		if (!this.isInEditMode() && mTvAuthor != null)
			mDefaultAuthorTextSize = mTvAuthor.getTextSize();

		updateTextSize();
		showAuthor(mShowAuthor);
		showContent(mShowContent);
		showSource(mShowSource);
		showTags(mShowTags, mOnTagClickedListener);
	}

	public void updateTextSize()
	{
		if (!this.isInEditMode() && mTvContent != null)
			mTvContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, mDefaultTextSize + App.getSettings().getContentFontSizeAdjustment());
		if (!this.isInEditMode() && mTvAuthor != null)
			mTvAuthor.setTextSize(TypedValue.COMPLEX_UNIT_PX, mDefaultAuthorTextSize + App.getSettings().getContentFontSizeAdjustment());
	}

	public void showContent(boolean show)
	{
		if (mTvContent != null)
		{
			if (show)
			{
				if (mTvContent.getText() != null && mTvContent.getText().length() > 0)
					mTvContent.setVisibility(View.VISIBLE);
				else
					mTvContent.setVisibility(View.GONE);
			}
			else
			{
				mTvContent.setVisibility(View.GONE);
			}
		}
	}

	public void showAuthor(boolean show)
	{
		if (mTvAuthor != null)
		{
			if (show)
			{
				if (mTvAuthor.getText() != null && mTvAuthor.getText().length() > 0)
					mTvAuthor.setVisibility(View.VISIBLE);
				else
					mTvAuthor.setVisibility(View.GONE);
			}
			else
			{
				mTvAuthor.setVisibility(View.GONE);
			}
		}
	}

	public void showSource(boolean show)
	{
		if (mTvSource != null)
		{
			if (show)
			{
				mTvSource.setVisibility(View.VISIBLE);
			}
			else
			{
				mTvSource.setVisibility(View.GONE);
			}
		}
	}

	public void showTags(boolean showTags, OnTagClickedListener onTagClickedListener)
	{
		mShowTags = showTags;
		mOnTagClickedListener = onTagClickedListener;
		
		View svTags = findViewById(R.id.svTags);
		if (showTags && mStory.getNumberOfTags() > 0)
		{
			if (svTags == null)
				svTags = ((ViewStub) findViewById(R.id.viewStubTags)).inflate().findViewById(R.id.svTags);

			LinearLayout llTags = (LinearLayout) findViewById(R.id.llTags);
			llTags.removeAllViews();

			LayoutInflater inflater = LayoutInflater.from(getContext());

			for (final String tag : mStory.getTags())
			{
				View item = inflater.inflate(R.layout.story_item_short_tag_item, llTags, false);
				TextView tv = (TextView) item.findViewById(R.id.tvTag);
				tv.setText(tag);
				item.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						if (mOnTagClickedListener != null)
							mOnTagClickedListener.onTagClicked(tag);
					}
				});
				llTags.addView(item);
			}
			svTags.setVisibility(View.VISIBLE);
		}
		else
		{
			LinearLayout llTags = (LinearLayout) findViewById(R.id.llTags);
			if (llTags != null)
				llTags.removeAllViews();
			if (svTags != null)
				svTags.setVisibility(View.GONE);
		}
	}

	public void setStory(Item story, boolean forceBitwiseDownloads, boolean allowFullScreenMediaViewing)
	{
		if (mStory != story)
		{
			mStory = story;
			if (mMediaViewCollection != null)
			{
				mMediaViewCollection.removeListener(this);
				mMediaViewCollection.recycle();
			}
			mMediaViewCollection = new MediaViewCollection(getContext(), mStory, false);
			mMediaViewCollection.addListener(this);
			mAllowFullScreenMediaViewing = allowFullScreenMediaViewing;
			recreateViews();
			populate();
		}
	}

	protected void populate()
	{
		// Set title
		//
		mTvTitle.setText(mStory.getTitle());

		// Set image(s)
		//
		if (mMediaContentView != null)
		{
			mMediaContentView.setMediaCollection(mMediaViewCollection, mAllowFullScreenMediaViewing, false);
			if (mMediaContentView.getCount() == 0)
				mMediaContentView.setVisibility(View.GONE);
			else
				mMediaContentView.setVisibility(View.VISIBLE);
		}

		// Author
		if (mTvAuthor != null)
		{
			if (mStory.getAuthor() != null)
				mTvAuthor.setText(getContext().getString(R.string.story_item_short_author, mStory.getAuthor()));
			else
				mTvAuthor.setText(null);
		}

		// Content
		if (mTvContent != null)
			mTvContent.setText(mStory.getCleanMainContent());

		// Set publication time (and add timer for updating it every minute)
		//
		updateTime();

		// Set source
		//
		mTvSource.setText(mStory.getSource());
		mTvSource.setTag(Long.valueOf(mStory.getFeedId()));
		mTvSource.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				long feedId = ((Long) v.getTag()).longValue();
				if (feedId != -1)
				{
					UICallbacks.setFeedFilter(FeedFilterType.SINGLE_FEED, feedId, this);
				}
			}
		});
	}

	public Item getStory()
	{
		return mStory;
	}

	protected void updateTime()
	{
		if (mStory != null)
		{
			if (mTvTime != null)
			{
				mTvTime.setText(UIHelpers.dateDiffDisplayString(mStory.getPublicationTime(), getContext(), R.string.story_item_short_published_never,
						R.string.story_item_short_published_recently, R.string.story_item_short_published_minutes, R.string.story_item_short_published_minute,
						R.string.story_item_short_published_hours, R.string.story_item_short_published_hour, R.string.story_item_short_published_days,
						R.string.story_item_short_published_day));
			}
		}
		else
		{
			if (mTvTime != null)
			{
				mTvTime.setText(R.string.story_item_short_published_never);
			}
		}
	}

	private final Runnable mUpdateTimestamp = new Runnable()
	{
		@Override
		public void run()
		{
			// Every minute
			updateTime();

			Handler handler = getHandler();
			if (handler != null)
			{
				handler.postDelayed(mUpdateTimestamp, 1000 * 60); // Every
																	// minute
			}
		}
	};

	@Override
	protected void onAttachedToWindow()
	{
		super.onAttachedToWindow();

		Handler handler = getHandler();
		if (handler != null)
		{
			handler.removeCallbacks(mUpdateTimestamp);
			handler.postDelayed(mUpdateTimestamp, 1000 * 60); // Every minute
		}
	}

	public void forceUpdate()
	{
		mCurrentPageMode = PageMode.UNKNOWN; // Force recreation
		recreateViews();
		populate();
	}

	@Override
	public void onViewLoaded(MediaViewCollection collection)
	{
	}

	@Override
	public void onIsFirstViewPortraitChanged(MediaViewCollection collection, boolean isFirstViewPortrait)
	{
		recreateViews();
		populate();
	}
}
