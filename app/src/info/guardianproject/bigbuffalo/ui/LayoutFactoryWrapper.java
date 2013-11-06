package info.guardianproject.bigbuffalo.ui;

import info.guardianproject.bigbuffalo.App;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;

public class LayoutFactoryWrapper implements LayoutInflater.Factory
{
	public LayoutFactoryWrapper()
	{
	}
	
	@Override
	public View onCreateView(String name, Context context,
			AttributeSet attrs) {
		return App.createView(name, context, attrs);
	}	
}