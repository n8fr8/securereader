package info.guardianproject.bigbuffalo.adapters;

import android.support.v4.view.PagerAdapter;

public abstract class ObservablePagerAdapter extends PagerAdapter implements ObservableAdapter
{
	private ObservableAdapter.ObservableAdapterListener mObserver;

	@Override
	public void notifyDataSetChanged()
	{
		super.notifyDataSetChanged();
		if (mObserver != null)
			mObserver.onChanged();
	}

	public void registerDataSetObserver(ObservableAdapterListener observer)
	{
		mObserver = observer;
	}

	public void unregisterDataSetObserver(ObservableAdapterListener observer)
	{
		mObserver = null;
	}
}
