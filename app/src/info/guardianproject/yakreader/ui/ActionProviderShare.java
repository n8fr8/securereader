package info.guardianproject.yakreader.ui;

import info.guardianproject.yakreader.R;
import info.guardianproject.yakreader.App;
import info.guardianproject.yakreader.adapters.ShareSpinnerAdapter;

import org.holoeverywhere.widget.AdapterView;
import org.holoeverywhere.widget.Spinner;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

import com.actionbarsherlock.view.ActionProvider;

public class ActionProviderShare extends ActionProvider
{
	private final Context mContext;
	private ShareSpinnerAdapter mAdapter;

	public ActionProviderShare(Context context)
	{
		super(context);
		mContext = context;
	}

	@Override
	public View onCreateActionView()
	{
		LayoutInflater inflater = LayoutInflater.from(mContext);
		View view = inflater.inflate(R.layout.actionbar_spinner_share, null);
		view.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
		Spinner spinner = (Spinner) view.findViewById(R.id.spinnerShare);
		if (mAdapter == null)
		{
			mAdapter = new ShareSpinnerAdapter(spinner, mContext, R.string.feed_share_popup_title, R.layout.actionbar_spinner_share_item);
		}
		mAdapter.clear();
		Intent shareIntent = App.getInstance().socialReader.getShareIntent(App.getInstance().m_activeFeed);
		mAdapter.addSecureBTShareResolver(shareIntent);
		mAdapter.addIntentResolvers(shareIntent);
		spinner.setAdapter(mAdapter);
		spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
		{
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id)
			{
				ShareSpinnerAdapter adapter = (ShareSpinnerAdapter) parent.getAdapter();
				Intent shareIntent = adapter.getIntentAtPosition(position);
				if (shareIntent != null)
				{
					mContext.startActivity(shareIntent);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0)
			{
			}
		});
		return view;
	}

	@Override
	public boolean hasSubMenu()
	{
		return false;
	}
}