package info.guardianproject.bigbuffalo.widgets;

import info.guardianproject.bigbuffalo.uiutil.FontManager;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;
import android.widget.TextView.BufferType;

public class CustomFontButton extends Button {

	private CustomFontTextViewHelper mHelper;

	public CustomFontButton(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init(attrs);
	}

	public CustomFontButton(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init(attrs);
	}

	public CustomFontButton(Context context)
	{
		super(context);
		init(null);
	}
	
	private void init(AttributeSet attrs)
	{
		mHelper = new CustomFontTextViewHelper(this, attrs);
	}
	
	@Override
	public void setText(CharSequence text, BufferType type)
	{
		if (mHelper != null)
			super.setText(mHelper.precomposeAndSetFont(text, type), type);
		else
			super.setText(FontManager.precomposeText(this, text), type);
	}
}
