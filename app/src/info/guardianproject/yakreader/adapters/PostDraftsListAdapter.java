package info.guardianproject.yakreader.adapters;

import java.util.ArrayList;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import info.guardianproject.yakreader.R;
import info.guardianproject.yakreader.adapters.StoryListAdapter.ItemClickListener;
import info.guardianproject.yakreader.views.StoryItemDraftPageView;

import com.tinymission.rss.Item;

public class PostDraftsListAdapter extends PostPublishedListAdapter
{
	public interface PostDraftsListAdapterListener
	{
		void onEditDraft(Item item);

		void onDeleteDraft(Item item);
	}

	private PostDraftsListAdapterListener mPostDraftsListAdapterListener;

	public PostDraftsListAdapter(Context context, ArrayList<Item> posts)
	{
		super(context, posts);
		mPostDraftsListAdapterListener = null;
		setHeaderView(R.layout.post_list_no_drafts, true);
	}

	public void setPostDraftsListAdapterListener(PostDraftsListAdapterListener listener)
	{
		mPostDraftsListAdapterListener = listener;
	}

	@Override
	protected View createView(ViewGroup parent)
	{
		LinearLayout item = (LinearLayout) mInflater.inflate(R.layout.post_item_draft, parent, false);
		return item;
	}

	@Override
	protected void bindView(View view, int position, Item story)
	{
		StoryItemDraftPageView page = (StoryItemDraftPageView) view.findViewById(R.id.llRoot);
		page.setStory(story, true, false);
		page.showTags(this.showTags(), mOnTagClickedListener);
		page.showAuthor(false);
		page.showSource(false);
		page.showContent(false);

		View btnDelete = view.findViewById(R.id.btnDelete);
		btnDelete.setOnClickListener(new DeleteButtonClickListener(mContext, story));

		View btnEdit = view.findViewById(R.id.btnEdit);
		btnEdit.setOnClickListener(new EditButtonClickListener(mContext, story));

		view.setOnClickListener(new ItemClickListener(position));
	}

	private class EditButtonClickListener implements View.OnClickListener
	{
		private final Context mContext;
		private final Item mStory;

		public EditButtonClickListener(Context context, Item story)
		{
			mContext = context;
			mStory = story;
		}

		@Override
		public void onClick(View v)
		{
			if (mPostDraftsListAdapterListener != null)
				mPostDraftsListAdapterListener.onEditDraft(mStory);
		}
	}

	private class DeleteButtonClickListener implements View.OnClickListener
	{
		private final Context mContext;
		private final Item mStory;

		public DeleteButtonClickListener(Context context, Item story)
		{
			mContext = context;
			mStory = story;
		}

		@Override
		public void onClick(View v)
		{
			if (mPostDraftsListAdapterListener != null)
				mPostDraftsListAdapterListener.onDeleteDraft(mStory);
		}
	}
}
