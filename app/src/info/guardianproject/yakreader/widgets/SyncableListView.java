package info.guardianproject.yakreader.widgets;

import info.guardianproject.yakreader.R;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ListView;

public class SyncableListView extends ListView
{
	public interface OnPullDownListener
	{
		/**
		 * Called when the list has been pulled down to reveal the header (if
		 * header height is set with
		 * {@link SyncableListView#setHeaderHeight(int)}).
		 * 
		 * @param heightVisible
		 *            Number of visible pixels.
		 */
		void onListPulledDown(int heightVisible);

		/**
		 * Called when the list was "dropped" while it was expanded (if header
		 * height > 0)
		 */
		void onListDroppedWhilePulledDown();
	}

	private int mHeaderHeight;
	private int mCurrentPullDownHeight;
	private boolean mDragging;
	private final Interpolator mDragInterpolator = new LinearInterpolator();
	private OnPullDownListener mListener;

	public SyncableListView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init(attrs);
	}

	public SyncableListView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init(attrs);
	}

	public SyncableListView(Context context)
	{
		super(context);
		init(null);
	}

	private void init(AttributeSet attrs)
	{
		this.setOverScrollMode(OVER_SCROLL_ALWAYS);
		this.setAdapter(null);
		
		View emptyView = new View(getContext());
		emptyView.setBackgroundColor(getContext().getResources().getColor(R.color.grey_dark));
		emptyView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, 1));
		setEmptyView(emptyView);
	}

	public void setPullDownListener(OnPullDownListener listener)
	{
		mListener = listener;
	}

	public void setHeaderHeight(int height)
	{
		mHeaderHeight = height;
	}

	@Override
	protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY)
	{
		if (mHeaderHeight > 0 && scrollY <= 0 && mDragging)
		{
			mCurrentPullDownHeight = -scrollY;
			if (mListener != null)
				mListener.onListPulledDown(-scrollY);
		}
		super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
	}

	@Override
	protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX,
			int maxOverScrollY, boolean isTouchEvent)
	{
		if (mHeaderHeight > 0 && scrollY <= 0 && mDragging)
		{
			// Adjust over scroll using interpolation to make it more "bouncy"
			//
			int headerVisible = -scrollY;
			float headerTotal = mHeaderHeight;

			float fractionVisible = headerVisible / headerTotal;
			float fractionNext = (-(scrollY + deltaY)) / headerTotal;

			float outputVisible = mDragInterpolator.getInterpolation(fractionVisible);
			float outputNext = mDragInterpolator.getInterpolation(fractionNext);

			outputVisible *= headerTotal;
			outputNext *= headerTotal;

			int originalDelta = deltaY;
			deltaY = (int) (outputVisible - outputNext);

			// Safeguard to always keep scrolling if float value is rounded to 0
			if (deltaY == 0 && originalDelta < 0)
				deltaY = -1;

			return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, mHeaderHeight, isTouchEvent);
		}
		return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev)
	{
		if (ev.getAction() == MotionEvent.ACTION_DOWN)
		{
			View view = this.getChildAt(0);
			if (view == null || view.getTop() == 0)
			{
				mDragging = true;
				this.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
			}
			else
				mDragging = false;
		}
		else if (ev.getAction() == MotionEvent.ACTION_UP)
		{
			// Check if we "dropped" it when it was fully expanded
			if (mDragging && mHeaderHeight > 0 && mCurrentPullDownHeight == mHeaderHeight && mListener != null)
			{
				mListener.onListDroppedWhilePulledDown();
			}
			mDragging = false;
			this.setOverScrollMode(View.OVER_SCROLL_NEVER);
			mCurrentPullDownHeight = 0;
			if (mListener != null)
				mListener.onListPulledDown(0);
		}

		return super.dispatchTouchEvent(ev);
	}

	@Override
	public int pointToPosition(int x, int y)
	{
		int position = super.pointToPosition(x, y);
		if (position == INVALID_POSITION && this.getAdapter() != null && this.getAdapter().getCount() > 0)
			position = this.getAdapter().getCount() - 1;
		return position;
	}
}
