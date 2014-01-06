package info.guardianproject.yakreader.adapters;

import info.guardianproject.yakreader.R;
import info.guardianproject.yakreader.views.StoryItemPageView;
import info.guardianproject.yakreader.views.StoryListView.StoryListListener;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.tinymission.rss.Item;

public class StoryListAdapter extends BaseAdapter
{
	public interface OnTagClickedListener
	{
		void onTagClicked(String tag);
	}

	public interface OnHeaderCreatedListener
	{
		void onHeaderCreated(View headerView, int resIdHeader);
	}

	protected final Context mContext;
	protected final LayoutInflater mInflater;
	private ArrayList<Item> mStories;
	private ArrayList<Item> mFilteredStories;
	private boolean mShowTags;
	protected OnTagClickedListener mOnTagClickedListener;
	protected OnHeaderCreatedListener mOnHeaderCreatedListener;
	private StoryListListener mListener;
	private int mResIdHeaderView;
	private boolean mHeaderOnlyIfNoItems;
	private String mTagFilter;
	private int mCurrentOrientation;

	public StoryListAdapter(Context context, ArrayList<Item> stories)
	{
		mContext = context;
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mStories = stories;
		mFilteredStories = (ArrayList<Item>) ((stories == null) ? null : stories.clone());
		mShowTags = false;
		mOnTagClickedListener = null;
		mHeaderOnlyIfNoItems = false;
	}

	public void setHeaderView(int resIdHeaderView, boolean onlyIfNoItems)
	{
		mResIdHeaderView = resIdHeaderView;
		mHeaderOnlyIfNoItems = onlyIfNoItems;
		this.notifyDataSetChanged();
	}

	public void updateItems(ArrayList<Item> items)
	{
		mStories = items;
		applyFilters();
		this.notifyDataSetChanged();
	}

	public void setTagFilter(String tagFilter)
	{
		mTagFilter = tagFilter;
		applyFilters();
	}

	private void applyFilters()
	{
		if (mStories != null)
		{
			mFilteredStories = new ArrayList<Item>();
			for (Item item : mStories)
			{
				if (mTagFilter != null)
				{
					if (item.getTags() != null)
					{
						boolean bFoundTag = false;
						for (String tag : item.getTags())
						{
							if (tag.contains(mTagFilter))
							{
								bFoundTag = true;
								break;
							}
						}
						if (!bFoundTag)
							continue; // Don't add this, i.e. filter it
					}
				}
				mFilteredStories.add(item);
			}
		}
		else
		{
			mFilteredStories = null;
		}
	}

	public void setListener(StoryListListener listener)
	{
		mListener = listener;
	}

	public boolean showTags()
	{
		return mShowTags;
	}

	public void setShowTags(boolean showTags)
	{
		mShowTags = showTags;
	}

	public void setOnTagClickedListener(OnTagClickedListener listener)
	{
		mOnTagClickedListener = listener;
	}

	public void setOnHeaderCreatedListener(OnHeaderCreatedListener listener)
	{
		mOnHeaderCreatedListener = listener;
	}

	private boolean hasHeaderView()
	{
		return (mResIdHeaderView != 0) && (!mHeaderOnlyIfNoItems || mFilteredStories == null || mFilteredStories.size() == 0);
	}

	@Override
	public int getItemViewType(int position)
	{
		if (position == 0 && hasHeaderView())
			return 1; // This is a header type
		Item item = (Item) getItem(position);
		if (item != null && item.getNumberOfMediaContent() > 1)
			return 2; // Multi media item type
		return 0; // Normal item type
	}

	@Override
	public int getViewTypeCount()
	{
		// We have an (optional) header and two types of items (one/zero image
		// and multi image)
		return 3;
	}

	@Override
	public int getCount()
	{
		int count = 0;
		if (mFilteredStories != null)
			count = mFilteredStories.size();
		if (hasHeaderView())
			count += 1;
		return count;
	}

	@Override
	public Object getItem(int position)
	{
		if (position == 0 && hasHeaderView())
			return mResIdHeaderView;
		if (mFilteredStories == null)
			return null;
		return mFilteredStories.get(position - (hasHeaderView() ? 1 : 0));
	}

	@Override
	public long getItemId(int position)
	{
		if (position == 0 && hasHeaderView())
			return 0;
		if (mFilteredStories == null)
			return 0;
		return mFilteredStories.get(position - (hasHeaderView() ? 1 : 0)).getDatabaseId();
	}

	@Override
	public void notifyDataSetChanged()
	{
		mCurrentOrientation = mContext.getResources().getConfiguration().orientation;
		super.notifyDataSetChanged();
	}

	private class ConvertViewInfo
	{
		int viewType;
		int orientation;

		public ConvertViewInfo(int viewType, int orientation)
		{
			this.viewType = viewType;
			this.orientation = orientation;
		}
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup parent)
	{
		// Buggy android does not always hone view type when sending in a
		// convertView
		//
		int viewType = getItemViewType(position);
		if (convertView != null)
		{
			ConvertViewInfo info = (ConvertViewInfo) convertView.getTag();
			if (info == null || info.viewType != viewType || info.orientation != mCurrentOrientation)
				convertView = null;
		}

		if (position == 0 && hasHeaderView())
		{
			View headerView = convertView;
			if (headerView == null)
				headerView = mInflater.inflate(mResIdHeaderView, parent, false);
			storeTag(headerView, viewType);
			if (this.mOnHeaderCreatedListener != null)
				this.mOnHeaderCreatedListener.onHeaderCreated(headerView, mResIdHeaderView);
			return headerView;
		}
		View view = (convertView != null) ? convertView : createView(parent);
		bindView(view, position, (Item) getItem(position));
		storeTag(view, viewType);
		return view;
	}

	private void storeTag(View view, int viewType)
	{
		ConvertViewInfo info = (ConvertViewInfo) view.getTag();
		if (info == null)
			info = new ConvertViewInfo(viewType, mCurrentOrientation);
		else
		{
			info.orientation = mCurrentOrientation;
			info.viewType = viewType;
		}
		view.setTag(info);
	}

	protected View createView(ViewGroup parent)
	{
		StoryItemPageView item = (StoryItemPageView) mInflater.inflate(R.layout.story_item_page, parent, false);
		return item;
	}

	protected void bindView(View view, int position, Item item)
	{
		StoryItemPageView storyView = (StoryItemPageView) view;
		storyView.setStory(item, false, false);
		storyView.showAuthor(true);
		storyView.showContent(true);
		storyView.showTags(this.showTags(), mOnTagClickedListener);
		storyView.setOnClickListener(new ItemClickListener(position));
	}

	protected class ItemClickListener implements View.OnClickListener
	{
		private final int mPosition;

		public ItemClickListener(int position)
		{
			mPosition = position;
		}

		@Override
		public void onClick(View v)
		{
			int positionInList = mPosition - (hasHeaderView() ? 1 : 0);
			if (mListener != null)
				mListener.onStoryClicked(mFilteredStories, positionInList, v);
		}
	}

	public void updateVisibleView(int visiblePosition, View view)
	{
		if (view instanceof StoryItemPageView)
		{
			StoryItemPageView storyView = (StoryItemPageView) view;
			storyView.forceUpdate();
			storyView.showAuthor(true);
			storyView.showContent(true);
		}
	}
}
