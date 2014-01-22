package info.guardianproject.yakreader.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import info.guardianproject.yakreader.R;

public class HeightLimitedRelativeLayout extends RelativeLayout
{
	private float mHeightLimit;

	public HeightLimitedRelativeLayout(Context context)
	{
		super(context);
		initView(context, null);
	}

	public HeightLimitedRelativeLayout(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		initView(context, attrs);
	}

	private void initView(Context context, AttributeSet attrs)
	{
		mHeightLimit = 1.75f;
		if (attrs != null)
		{
			TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.HeightLimitedRelativeLayout);
			mHeightLimit = a.getFloat(R.styleable.HeightLimitedRelativeLayout_height_limit, 1.75f);
			a.recycle();
		}
	}

	public void setHeightLimit(float heightLimit)
	{
		mHeightLimit = heightLimit;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{		
		if (mHeightLimit == 0)
		{
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
		else if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED)
		{
			int inhibitedHeightSpec = MeasureSpec.makeMeasureSpec((int) (MeasureSpec.getSize(widthMeasureSpec) / mHeightLimit),
					MeasureSpec.AT_MOST);
			super.onMeasure(widthMeasureSpec, inhibitedHeightSpec);
		}
		else
		{
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			int width = this.getMeasuredWidth();
			int height = (int) (width / mHeightLimit);
			super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
		}
	}
}
