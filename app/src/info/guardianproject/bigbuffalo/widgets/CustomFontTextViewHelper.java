package info.guardianproject.bigbuffalo.widgets;

import info.guardianproject.bigbuffalo.R;
import info.guardianproject.bigbuffalo.uiutil.FontManager;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;
import android.widget.TextView.BufferType;

public class CustomFontTextViewHelper {
	private TextView mView;
	private Typeface mFont;

	public CustomFontTextViewHelper(TextView view, AttributeSet attrs)
	{
		mView = view;
		if (attrs != null)
		{
			TypedArray a = mView.getContext().obtainStyledAttributes(attrs, R.styleable.CustomFontTextView);
			String fontName = a.getString(R.styleable.CustomFontTextView_font);
			if (fontName != null && !mView.isInEditMode())
			{
				mFont = FontManager.getFontByName(mView.getContext(), fontName);
				if (mFont != null)
					mView.setTypeface(mFont);
			}
			a.recycle();
		}
		if (mFont == null)
			mFont = mView.getTypeface();
	}
	
	public Typeface getOriginalFont()
	{
		return mFont;
	}
	
	public CharSequence precomposeAndSetFont(CharSequence text, BufferType type)
	{
		CharSequence precomposed = FontManager.precomposeText(mView, text);
		if (precomposed == text)
		{
			// No conversion, reset font!
			if (mView.getTypeface() != mFont)
				mView.setTypeface(mFont);
		}
		return precomposed;
	}
}
