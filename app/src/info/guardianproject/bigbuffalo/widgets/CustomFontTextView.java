package info.guardianproject.bigbuffalo.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import info.guardianproject.bigbuffalo.R;
import info.guardianproject.bigbuffalo.uiutil.FontManager;

public class CustomFontTextView extends TextView
{
	private Rect mBounds;
	private Shader mShader;
	private boolean mFadeLastLine = false;
	private float mLineSpacingExtra;
	private float mLineSpacingMulti;
	private CustomFontTextViewHelper mHelper;

	public CustomFontTextView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init(attrs);
	}

	public CustomFontTextView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init(attrs);
	}

	public CustomFontTextView(Context context)
	{
		super(context);
		init(null);
	}

	private void init(AttributeSet attrs)
	{
		mBounds = new Rect();
		mLineSpacingExtra = 0;
		mLineSpacingMulti = 1.0f;

		mHelper = new CustomFontTextViewHelper(this, attrs);
		
		if (attrs != null)
		{
			TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.CustomFontTextView);
			mFadeLastLine = a.getBoolean(R.styleable.CustomFontTextView_fade_last_line, false);
			a.recycle();
			
			a = getContext().obtainStyledAttributes(attrs, new int[] { android.R.attr.lineSpacingExtra, android.R.attr.lineSpacingMultiplier });
			mLineSpacingExtra = a.getFloat(0, mLineSpacingExtra);
			mLineSpacingMulti = a.getFloat(1, mLineSpacingMulti);
			a.recycle();
		}
		mShader = getPaint().getShader();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (heightMeasureSpec == MeasureSpec.UNSPECIFIED || heightMeasureSpec == MeasureSpec.AT_MOST)
		{
			if (getLineCount() > 0)
			{
				int bottom = 0;
				int n = getVisibleLines(this.getMeasuredHeight());
				if (n > 0)
				{
					this.getLineBounds(n - 1, mBounds);
					bottom = mBounds.bottom;
				}
				super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(bottom + this.getPaddingBottom(), MeasureSpec.EXACTLY));
			}
		}
	}

	public void setFadeLastLine(boolean fadeLastLine)
	{
		if (mFadeLastLine != fadeLastLine)
		{
			mFadeLastLine = fadeLastLine;
			fadeLastLine();
		}
	}

	public CharSequence getOverflowingText()
	{
		int nVisible = getVisibleLines();
		if (getLayout() != null)
		{
			Layout layout = getLayout();
			int offset = layout.getLineStart(nVisible);
			CharSequence overflow = "";
			if (offset >= 0 && offset < getText().length())
				overflow = getText().subSequence(offset, getText().length());
			return overflow;
		}
		return null;
	}

	public int getVisibleLines()
	{
		return getVisibleLines(getHeight());
	}

	private int getVisibleLines(int height)
	{
		if (getLineCount() > 0 && height > 0 && getLayout() != null)
		{
			Rect bounds = new Rect();
			for (int i = 0; i < getLineCount(); i++)
			{
				getLineBounds(i, bounds);
				if ((bounds.bottom) > (height - this.getPaddingBottom()))
				{
					return i;
				}
			}
			return getLineCount(); // All fit
		}
		return 0;
	}

	public CustomFontTextView createClone()
	{
		CustomFontTextView newClone = new CustomFontTextView(getContext());
		newClone.setTypeface(mHelper.getOriginalFont());
		newClone.setTextSize(TypedValue.COMPLEX_UNIT_PX, this.getTextSize());
		newClone.setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), getPaddingBottom());
		newClone.setLineSpacing(mLineSpacingExtra, mLineSpacingMulti);
		LayoutParams params = getLayoutParams();
		if (params instanceof ViewGroup.MarginLayoutParams)
		{
			ViewGroup.MarginLayoutParams newParams = new ViewGroup.MarginLayoutParams(params);
			newClone.setLayoutParams(newParams);
		}
		return newClone;
	}

	@Override
	public void setLineSpacing(float add, float mult) {
		super.setLineSpacing(add, mult);
		mLineSpacingExtra = add;
		mLineSpacingMulti = mult;
	}

	@Override
	protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter)
	{
		super.onTextChanged(text, start, lengthBefore, lengthAfter);
		fadeLastLine();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		super.onSizeChanged(w, h, oldw, oldh);
		fadeLastLine();
	}

	private void fadeLastLine()
	{
		if (mFadeLastLine)
		{
			int nVisible = getVisibleLines();
			if (getLayout() != null && getLineCount() > nVisible)
			{
				int bottomStartFade = 0;
				int bottomEndFade = 0;
				Rect bounds = new Rect();
				if (nVisible > 0)
				{
					getLineBounds(nVisible - 1, bounds);
					bottomStartFade = bounds.bottom;
				}
				getLineBounds(nVisible, bounds);
				bottomEndFade = bounds.bottom;

				int defaultColor = getTextColors().getDefaultColor();
				int alphaColor = Color.argb(0, Color.red(defaultColor), Color.green(defaultColor), Color.blue(defaultColor));

				LinearGradient gradient = new LinearGradient(0f, bottomStartFade, 0f, bottomEndFade, new int[] { defaultColor, alphaColor }, new float[] { 0,
						1f }, Shader.TileMode.CLAMP);
				getPaint().setShader(gradient);
				return;
			}
		}

		getPaint().setShader(mShader); // original
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
