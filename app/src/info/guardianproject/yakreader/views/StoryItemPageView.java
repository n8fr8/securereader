package info.guardianproject.yakreader.views;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import info.guardianproject.yakreader.R;
import info.guardianproject.yakreader.App;
import info.guardianproject.yakreader.ui.MediaViewCollection;
import info.guardianproject.yakreader.uiutil.UIHelpers;

import com.tinymission.rss.Item;

public class StoryItemPageView extends RelativeLayout 
{
	public interface StoryItemPageViewListener
	{
		void onSourceClicked(long feedId);
		void onTagClicked(String tag);
	}
	
	protected Item mItem;
	protected int mCurrentViewType;	
	protected StoryMediaContentView mMediaContentView;
	protected TextView mTvTitle;
	protected TextView mTvContent;
	protected TextView mTvTime;
	protected TextView mTvSource;
	protected View mLayoutTags;
	protected LinearLayout mLlTags;
	
	protected StoryItemPageViewListener mListener;
	
	// Configuration
	//
	private boolean mShowTags;
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
		mCurrentViewType = -1;
		setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
		setBackgroundResource(R.drawable.story_item_background_selector);
//		if (attrs != null && !this.isInEditMode())
//		{
//			TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.StoryItemPageView);
//			mShowAuthor = a.getBoolean(R.styleable.StoryItemPageView_show_author, true);
//			mShowContent = a.getBoolean(R.styleable.StoryItemPageView_show_content, true);
//			mShowSource = a.getBoolean(R.styleable.StoryItemPageView_show_source, true);
//			a.recycle();
//		}
	}
	
	public void recycle()
	{
		if (mMediaContentView != null && mMediaContentView.getMediaCollection() != null)
		{
			mMediaContentView.getMediaCollection().recycle();
		}
	}

	public static int getViewTypeForMedia(MediaViewCollection mediaViewCollection)
	{
		// No media
		if (mediaViewCollection == null || mediaViewCollection.getCount() == 0)
			return 0;
	
		if (mediaViewCollection.containsLoadedMedia())
		{
			if (mediaViewCollection.isFirstViewPortrait())
				return 1; // Portrait mode
			return 2; // Landscape mode
		}
		return 0; // Nothing loaded
	}
	
	protected int getViewResourceByType(int type)
	{
		if (type == 0)
			return R.layout.story_item_page_merge_no_photo;
		else if (type == 1)
			return R.layout.story_item_page_merge_portrait_photo;
		return R.layout.story_item_page_merge_landscape_photo;
	}
	
	private void createViews()
	{
		if (mItem == null || mMediaViewCollection == null)
			return;

		int type = getViewTypeForMedia(mMediaViewCollection);
		if (type == mCurrentViewType)
			return;
		
		mCurrentViewType = type;

		LayoutInflater inflater = LayoutInflater.from(getContext());
		View view = inflater.inflate(getViewResourceByType(mCurrentViewType), this, true);
		findViews(view);
	}
	
	protected void findViews(View view)
	{
		mMediaContentView = (StoryMediaContentView) view.findViewById(R.id.ivPhotos);
		mTvTitle = (TextView) view.findViewById(R.id.tvTitle);
		mTvContent = (TextView) view.findViewById(R.id.tvContent);
		mTvTime = (TextView) view.findViewById(R.id.tvTime);
		mTvSource = (TextView) view.findViewById(R.id.tvSource);
		mLayoutTags = view.findViewById(R.id.layout_tags);
		mLlTags = (LinearLayout) view.findViewById(R.id.llTags);
		if (mTvContent != null)
			mTvContent.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTvContent.getTextSize() + App.getSettings().getContentFontSizeAdjustment());
	}
		
	public void populateWithItem(Item item)
	{
		populateWithItem(item, null);
	}	
	
	public void populateWithItem(Item item, MediaViewCollection media)
	{
		if (mItem != item)
		{
			mItem = item;
			mMediaViewCollection = media;
			if (mMediaViewCollection == null && item.getNumberOfMediaContent() > 0)
				mMediaViewCollection = new MediaViewCollection(getContext(), item);
			
			createViews();

			if (mMediaContentView != null)
				mMediaContentView.setMediaCollection(mMediaViewCollection, false, false);
			if (mTvTitle != null)
				mTvTitle.setText(item.getTitle());
			if (mTvContent != null)
				mTvContent.setText(item.getCleanMainContent());
			if (mTvSource != null)
			{
				mTvSource.setText(item.getSource());
				mTvSource.setTag(Long.valueOf(item.getFeedId()));
				mTvSource.setOnClickListener(new OnClickListener()
				{
					@Override
					public void onClick(View v)
					{
						long feedId = ((Long) v.getTag()).longValue();
						if (feedId != -1)
						{
							if (mListener != null)
								mListener.onSourceClicked(feedId);
						}
					}
				});
			}
			if (mLlTags != null && mShowTags && item.getNumberOfTags() > 0)
			{
				mLlTags.removeAllViews();
				for (final String tag : item.getTags())
				{
					View tagItem = LayoutInflater.from(getContext()).inflate(R.layout.story_item_short_tag_item, mLlTags, false);
					TextView tv = (TextView) tagItem.findViewById(R.id.tvTag);
					tv.setText(tag);
					tagItem.setOnClickListener(new OnClickListener()
					{
						@Override
						public void onClick(View v)
						{
							if (mListener != null)
								mListener.onTagClicked(tag);
						}
					});
					mLlTags.addView(tagItem);
				}
			}
		}

		populateTime();
		
		if (mLayoutTags != null)
		{
			if (mShowTags && item.getNumberOfTags() > 0)
				mLayoutTags.setVisibility(View.VISIBLE);
			else
				mLayoutTags.setVisibility(View.GONE);
		}
	}
	
	public void loadMedia(MediaViewCollection.OnMediaLoadedListener listener)
	{
		if (mMediaContentView != null && mMediaContentView.getMediaCollection() != null)
		{
			MediaViewCollection mvc = mMediaContentView.getMediaCollection();
			if (listener != null)
				mvc.addListener(listener);
			mvc.load(false, false);
		}
	}
	
	protected void populateTime()
	{
		if (mTvTime != null)
			mTvTime.setText(UIHelpers.dateDiffDisplayString(mItem.getPublicationTime(), getContext(), R.string.story_item_short_published_never,
						R.string.story_item_short_published_recently, R.string.story_item_short_published_minutes, R.string.story_item_short_published_minute,
						R.string.story_item_short_published_hours, R.string.story_item_short_published_hour, R.string.story_item_short_published_days,
						R.string.story_item_short_published_day));
	}

	private final Runnable mUpdateTimestamp = new Runnable()
	{
		@Override
		public void run()
		{
			// Every minute
			populateTime();

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

	@Override
	protected void onDetachedFromWindow()
	{
		super.onDetachedFromWindow();

		Handler handler = getHandler();
		if (handler != null)
		{
			handler.removeCallbacks(mUpdateTimestamp);
		}
	}
	
	public boolean showTags()
	{
		return mShowTags;
	}
	
	public void showTags(boolean showTags)
	{
		mShowTags = showTags;
	}
	
	public void setListener(StoryItemPageViewListener listener)
	{
		mListener = listener;
	}
}
