package info.guardianproject.yakreader.widgets;

import info.guardianproject.yakreader.uiutil.FontManager;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.RadioButton;

public class CustomFontRadioButton extends RadioButton {

	@SuppressWarnings("unused")
	private CustomFontTextViewHelper mHelper;

	public CustomFontRadioButton(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init(attrs);
	}

	public CustomFontRadioButton(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init(attrs);
	}

	public CustomFontRadioButton(Context context)
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
		super.setText(FontManager.transformText(this, text), type);
	}
}
