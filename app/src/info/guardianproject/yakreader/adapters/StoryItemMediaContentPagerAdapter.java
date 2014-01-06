package info.guardianproject.yakreader.adapters;

import info.guardianproject.yakreader.ui.MediaViewCollection;
import info.guardianproject.yakreader.ui.OnMediaItemClickedListener;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;


public class StoryItemMediaContentPagerAdapter extends ObservablePagerAdapter
{
	private final Context mContext;
	private final MediaViewCollection mMediaViewCollection;

	public StoryItemMediaContentPagerAdapter(Context context, MediaViewCollection mediaViewCollection, boolean allowFullScreenViewing)
	{
		super();
		mContext = context;
		mMediaViewCollection = mediaViewCollection;

		// Hookup events?
		for (int i = 0; i < mMediaViewCollection.getCount(); i++)
		{
			View mediaView = mMediaViewCollection.getView(i);
			if (allowFullScreenViewing)
			{
				mediaView.setOnClickListener(new OnMediaItemClickedListener(mMediaViewCollection.getContentForView(mediaView)));
			}
			else
			{
				mediaView.setOnClickListener(null);
			}
		}
	}

	@Override
	public boolean isViewFromObject(View arg0, Object arg1)
	{
		return arg0 == (View) arg1;
	}

	@Override
	public Object instantiateItem(ViewGroup container, int position)
	{
		View mediaView = mMediaViewCollection.getView(position);
		if (mediaView != null)
		{
			if (mediaView.getParent() != null)
				((ViewGroup) mediaView.getParent()).removeView(mediaView);
			container.addView(mediaView, 0);
		}
		return mediaView;
	}

	@Override
	public int getItemPosition(Object object)
	{
		return POSITION_NONE;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object)
	{
		container.removeView((View) object);
	}

	@Override
	public int getCount()
	{
		return mMediaViewCollection.getCount();
	}

	public View getView(int position)
	{
		return mMediaViewCollection.getView(position);
	}

	public long getItemId(int position)
	{
		View view = getView(position);
		if (view != null)
			return view.hashCode();
		return 0;
	}

	@Override
	public CharSequence getPageTitle(int position)
	{
		return "Page " + position;
	}
}
