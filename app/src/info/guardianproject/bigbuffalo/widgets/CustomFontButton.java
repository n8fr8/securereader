package info.guardianproject.bigbuffalo.widgets;

import info.guardianproject.bigbuffalo.uiutil.FontManager;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

public class CustomFontButton extends Button {

	public CustomFontButton(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
	}

	public CustomFontButton(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	public CustomFontButton(Context context)
	{
		super(context);
	}
	
	@Override
	public void setText(CharSequence text, BufferType type)
	{
		super.setText(FontManager.precomposeText(this, text), type);
	}
}
