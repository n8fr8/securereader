package info.guardianproject.yakreader.adapters;

public interface ObservableAdapter
{
	public interface ObservableAdapterListener
	{
		void onChanged();
	}

	void registerDataSetObserver(ObservableAdapterListener observer);

	void unregisterDataSetObserver(ObservableAdapterListener observer);
}
