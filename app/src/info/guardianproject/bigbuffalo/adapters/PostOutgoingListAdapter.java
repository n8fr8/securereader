package info.guardianproject.bigbuffalo.adapters;

import java.util.ArrayList;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import info.guardianproject.bigbuffalo.R;
import info.guardianproject.bigbuffalo.views.StoryItemPageView;

import com.tinymission.rss.Item;

public class PostOutgoingListAdapter extends PostPublishedListAdapter
{
	public PostOutgoingListAdapter(Context context, ArrayList<Item> posts)
	{
		super(context, posts);
		setHeaderView(R.layout.post_list_no_outgoing, true);
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
}
