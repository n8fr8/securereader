package info.guardianproject.yakreader.adapters;

import java.util.ArrayList;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import info.guardianproject.yakreader.R;
import info.guardianproject.yakreader.adapters.StoryListAdapter.ItemClickListener;
import info.guardianproject.yakreader.views.StoryItemPageView;

import com.tinymission.rss.Item;

public class PostPublishedListAdapter extends StoryListAdapter
{
	public PostPublishedListAdapter(Context context, ArrayList<Item> posts)
	{
		super(context, posts);
		setShowTags(true);
	}

	@Override
	protected View createView(ViewGroup parent)
	{
		StoryItemPageView item = (StoryItemPageView) mInflater.inflate(R.layout.story_item_page, parent, false);
		item.showAuthor(false);
		item.showContent(false);
		item.showSource(false);
		return item;
	}

	@Override
	protected void bindView(View view, int position, Item item)
	{
		StoryItemPageView storyView = (StoryItemPageView) view;
		storyView.setStory(item, false, false);
		storyView.showTags(this.showTags(), mOnTagClickedListener);
		storyView.setOnClickListener(new ItemClickListener(position));
	}

}
