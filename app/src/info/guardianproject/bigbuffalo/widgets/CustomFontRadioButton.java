package info.guardianproject.bigbuffalo.widgets;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.RadioButton;

public class CustomFontRadioButton extends RadioButton {

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
		if (mHelper != null)
			super.setText(mHelper.precomposeAndSetFont(text, type), type);
		else
			super.setText(text, type);
	}
	
	@Override
	public void setTypeface(Typeface tf) {
		if (mHelper != null)
			super.setTypeface(mHelper.handleSetTypefaceRequest(tf));
		else
			super.setTypeface(tf);
	}
}
