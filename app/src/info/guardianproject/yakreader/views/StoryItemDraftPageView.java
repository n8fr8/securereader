package info.guardianproject.yakreader.views;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import info.guardianproject.yakreader.R;
import info.guardianproject.yakreader.uiutil.UIHelpers;

public class StoryItemDraftPageView extends StoryItemPageView
{
	public StoryItemDraftPageView(Context context)
	{
		super(context);
	}

	public StoryItemDraftPageView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	protected void populate()
	{
		super.populate();
		if (TextUtils.isEmpty(mTvTitle.getText()))
		{
			mTvTitle.setText(R.string.post_item_no_title);
		}
	}

	@Override
	protected void updateTime()
	{
		if (mStory != null)
		{
			mTvTime.setText(UIHelpers.dateDiffDisplayString(mStory.getPublicationTime(), getContext(), R.string.post_draft_saved_never,
					R.string.post_draft_saved_recently, R.string.post_draft_saved_minutes, R.string.post_draft_saved_minute, R.string.post_draft_saved_hours,
					R.string.post_draft_saved_hour, R.string.post_draft_saved_days, R.string.post_draft_saved_day));
		}
		else
		{
			mTvTime.setText(R.string.post_draft_saved_never);
		}
	}
}
