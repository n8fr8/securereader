package info.guardianproject.bigbuffalo.widgets;

import info.guardianproject.bigbuffalo.R;
import info.guardianproject.bigbuffalo.uiutil.FontManager;
import info.guardianproject.bigbuffalo.uiutil.FontManager.TransformedText;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;
import android.widget.TextView.BufferType;

public class CustomFontTextViewHelper {
	private TextView mView;
	private Typeface mFont;
	private boolean mHintNeedsTransform;
	private boolean mUsingTransformedFont;
	private Typeface mTransformedFont;

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

		mHintNeedsTransform = false;
		if (mView.getHint() != null)
		{
			// Hint needs precompose?
			CharSequence hint = mView.getHint();
			TransformedText transformed = FontManager.transformText(mView, hint);
			if (transformed != null)
			{
				mHintNeedsTransform = true;
				mView.setHint(transformed.transformedText);
				useTransformedTypeface(transformed);
			}
		}
		
		if (mView.getText() != null)
		{
			TransformedText transformed = FontManager.transformText(mView, mView.getText());
			if (transformed != null)
			{
				mView.setText(transformed.transformedText);
				useTransformedTypeface(transformed);
			}			
		}
	}
	
	public Typeface getOriginalFont()
	{
		return mFont;
	}
	
	public CharSequence precomposeAndSetFont(CharSequence text, BufferType type)
	{
		TransformedText transformed = FontManager.transformText(mView, text);
		if (transformed == null && !mHintNeedsTransform)
		{
			// No conversion, reset font!
			mUsingTransformedFont = false;
			if (mView.getTypeface() != mFont)
				mView.setTypeface(mFont);
			return text;
		}
		else if (transformed != null)
		{
			useTransformedTypeface(transformed);
			return transformed.transformedText;
		}
		return text;
	}

	private void useTransformedTypeface(TransformedText transformed)
	{
		mTransformedFont = transformed.typeface;
		mUsingTransformedFont = true;	
    	if (transformed.typeface != null && transformed.typeface != mView.getTypeface())
    		mView.setTypeface(transformed.typeface);
	}
	
	public Typeface handleSetTypefaceRequest(Typeface tf) {
		if (tf != mTransformedFont)
			mFont = tf;
		if (mUsingTransformedFont)
			return mTransformedFont;
		return tf;
	}
}
