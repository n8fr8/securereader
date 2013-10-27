package info.guardianproject.bigbuffalo.widgets;

import info.guardianproject.bigbuffalo.uiutil.FontManager;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

public class CustomFontEditText extends EditText {

	public CustomFontEditText(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public CustomFontEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CustomFontEditText(Context context) {
		super(context);
	}
	
	@Override
	public void setText(CharSequence text, BufferType type)
	{
		super.setText(FontManager.precomposeText(this, text), type);
	}

}
