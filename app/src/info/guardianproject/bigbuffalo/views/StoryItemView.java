package info.guardianproject.bigbuffalo.views;

import info.guardianproject.bigbuffalo.App;
import info.guardianproject.bigbuffalo.MainActivity;
import info.guardianproject.bigbuffalo.R;
import info.guardianproject.bigbuffalo.api.Settings.ReaderSwipeDirection;
import info.guardianproject.bigbuffalo.api.SocialReader;
import info.guardianproject.bigbuffalo.models.PagedViewContent;
import info.guardianproject.bigbuffalo.ui.MediaViewCollection;
import info.guardianproject.bigbuffalo.ui.MediaViewCollection.OnMediaLoadedListener;
import info.guardianproject.bigbuffalo.ui.PackageHelper;
import info.guardianproject.bigbuffalo.uiutil.UIHelpers;
import info.guardianproject.bigbuffalo.widgets.AnimatedRelativeLayout;
import info.guardianproject.bigbuffalo.widgets.CustomFontTextView;
import info.guardianproject.bigbuffalo.widgets.PagedView;
import info.guardianproject.bigbuffalo.widgets.UpdatingTextView;
import info.guardianproject.bigbuffalo.widgets.UpdatingTextView.OnUpdateListener;

import java.text.Bidi;
import java.util.ArrayList;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.tinymission.rss.Item;

public class StoryItemView implements PagedViewContent, OnUpdateListener, OnMediaLoadedListener
{
	private PagedView mPagedView;
	private final Item mItem;
	private ArrayList<View> mBlueprintViews;
	private ArrayList<View> mPages;
	private SparseArray<Rect> mStoredPositions;
	private float mDefaultTextSize;
	private float mDefaultAuthorTextSize;

	public StoryItemView(Item item)
	{
		mItem = item;
	}

	private void createBlueprintViews(ViewGroup parentContainer)
	{
		mBlueprintViews = new ArrayList<View>();

		LayoutInflater inflater = LayoutInflater.from(parentContainer.getContext());

		ViewGroup blueprint = (ViewGroup) inflater.inflate(R.layout.story_item_page_blueprint, parentContainer, false);
		populateViewWithItem(blueprint, mItem);
		blueprint.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

		while (blueprint.getChildCount() > 0)
		{
			View child = blueprint.getChildAt(0);
			mBlueprintViews.add(child);
			blueprint.removeViewAt(0);
		}
	}

	// Used for layout!
	//
	private ViewGroup _CurrentColumn;
	private ViewGroup _NextColumn;

	private View createPage(boolean isFirstPage)
	{
		View newPage = null;
		if (isFirstPage)
		{
			newPage = LayoutInflater.from(mPagedView.getContext()).inflate(R.layout.story_item_fullscreen_view_page_1, mPagedView, false);
		}
		else
		{
			newPage = LayoutInflater.from(mPagedView.getContext()).inflate(R.layout.story_item_fullscreen_view_page_n, mPagedView, false);
		}
		populateViewWithItem((ViewGroup) newPage, mItem);
		newPage.measure(View.MeasureSpec.makeMeasureSpec(mPagedView.getWidth(), View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(mPagedView.getHeight(), View.MeasureSpec.EXACTLY));
		newPage.layout(0, 0, newPage.getMeasuredWidth(), newPage.getMeasuredHeight());
		return newPage;
	}

	private ViewGroup getNextColumn(ArrayList<View> pages)
	{
		if (_NextColumn == null || _CurrentColumn == _NextColumn)
		{
			View newPage = createPage(_CurrentColumn == null);
			_CurrentColumn = (ViewGroup) newPage.findViewById(R.id.column1);
			_NextColumn = (ViewGroup) newPage.findViewById(R.id.column2);
			pages.add(newPage);
		}
		else
		{
			_CurrentColumn = _NextColumn;
		}
		return _CurrentColumn;
	}

	private boolean isTwoColumnMode()
	{
		return _NextColumn != null;
	}

	private boolean isInFirstColumn()
	{
		return _CurrentColumn != _NextColumn;
	}

	private ArrayList<View> relayout()
	{
		updateTextSize();

		ArrayList<View> ret = new ArrayList<View>();

		ArrayList<View> blueprints = new ArrayList<View>(mBlueprintViews);

		// Null old layout helpers
		_CurrentColumn = null;
		_NextColumn = null;

		ViewGroup column = getNextColumn(ret);
		int columnHeightMax = column.getHeight();
		int currentColumnHeight = 0;

		for (int idxView = 0; idxView < blueprints.size();)
		{
			ViewGroup currentPageView = ((ViewGroup) ret.get(ret.size() - 1));

			View child = blueprints.get(idxView);
			if (child.getParent() != null)
				((ViewGroup) child.getParent()).removeView(child);

			if (child.getId() != R.id.tvReadMore)
				child.setPadding(child.getPaddingLeft(), 0, child.getPaddingRight(), 0);

			if (child.getId() == R.id.ivPhotos)
			{
				if (isTwoColumnMode())
					((StoryMediaContentView) child).setHeightInhibitor(0); // Full
				// bleed
				else
					((StoryMediaContentView) child).setHeightInhibitor(1.75f);

				((StoryMediaContentView) child).setScaleType(!isTwoColumnMode());
				((StoryMediaContentView) child).updateView();
			}
			else if (child instanceof CustomFontTextView)
			{
				CustomFontTextView tv = (CustomFontTextView) child;
				tv.setMaxLines(Integer.MAX_VALUE);
			}

			if (child.getId() == R.id.tvAuthor && TextUtils.isEmpty(((TextView) child).getText()))
			{
				// Author is empty, so remove that view
				idxView++;
				continue;
			}
			else if (child.getId() == R.id.tvContent && TextUtils.isEmpty(((TextView) child).getText()))
			{
				// Content is empty, so remove that view
				idxView++;
				continue;
			}
			else if (child.getVisibility() == View.GONE)
			{
				// Dont add this view
				idxView++;
				continue;
			}

			if (currentColumnHeight != 0)
				currentColumnHeight += UIHelpers.dpToPx(10, child.getContext());
			else if (currentColumnHeight == 0 && child.getId() == R.id.tvTitle && !isTwoColumnMode())
				currentColumnHeight += UIHelpers.dpToPx(10, child.getContext());

			// Author should be in second column in 2 column mode, inless it has
			// been line breaked to next page
			// (in that case it is topmost in the colun)
			if (child.getId() == R.id.tvAuthor && isTwoColumnMode() && isInFirstColumn() && currentColumnHeight != 0)
			{
				// Do nothing. This will pull up a new column for us,
				// effectively placing the author in column 2.
			}
			else if (child.getId() == R.id.tvTitle && isTwoColumnMode() && isInFirstColumn() && currentPageView.findViewById(R.id.ivPhotos) != null
					&& ((StoryMediaContentView) currentPageView.findViewById(R.id.ivPhotos)).isMediaLoaded())
			{
				// Do nothing. This will put the title in column 2.
			}
			else
			{
				boolean spillToNextColumn = false;

				// Adjust to column
				if (child.getId() == R.id.ivPhotos && isTwoColumnMode() && ((StoryMediaContentView) child).isMediaLoaded())
					child.measure(View.MeasureSpec.makeMeasureSpec(column.getWidth(), View.MeasureSpec.EXACTLY),
							View.MeasureSpec.makeMeasureSpec(columnHeightMax - currentColumnHeight, View.MeasureSpec.EXACTLY));
				else
					child.measure(View.MeasureSpec.makeMeasureSpec(column.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
				child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());

				if (child.getHeight() + currentColumnHeight <= columnHeightMax || child instanceof CustomFontTextView)
				{
					if (child instanceof CustomFontTextView)
					{
						CustomFontTextView tv = (CustomFontTextView) child;

						if (child.getHeight() + currentColumnHeight > columnHeightMax)
						{
							int numVisibleLines = tv.getVisibleLines();

							// Special case, we can't line break the "read more"
							// control
							if (child.getId() == R.id.tvReadMore)
								numVisibleLines = 0;

							if (numVisibleLines == 0)
							{
								column = getNextColumn(ret);
								columnHeightMax = column.getHeight();
								currentColumnHeight = 0;
								continue;
							}

							tv.setHeight(columnHeightMax - currentColumnHeight);
							tv.measure(View.MeasureSpec.makeMeasureSpec(column.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
							tv.layout(0, 0, tv.getMeasuredWidth(), tv.getMeasuredHeight());

							if (tv.getId() == R.id.tvContent
									|| (tv.getTag() != null && tv.getTag() instanceof Integer && ((Integer) tv.getTag()).intValue() == R.id.tvContent))
							{
								// Split it in two!
								CustomFontTextView newClone = ((CustomFontTextView) child).createClone();
								newClone.setText(tv.getOverflowingText());
								newClone.setTag(Integer.valueOf(R.id.tvContent));
								blueprints.add(idxView + 1, newClone);
							}
							spillToNextColumn = true;
						}
					}
				}

				idxView++;

				// Fits
				RelativeLayout.LayoutParams relLayout = new RelativeLayout.LayoutParams(child.getWidth(), child.getHeight());
				relLayout.leftMargin = UIHelpers.getRelativeLeft(column);
				relLayout.topMargin = UIHelpers.getRelativeTop(column) + currentColumnHeight;

				currentPageView.addView(child);
				child.setLayoutParams(relLayout);
				currentColumnHeight += child.getHeight();
				if (!spillToNextColumn)
					continue;
			}

			column = getNextColumn(ret);
			columnHeightMax = column.getHeight();
			currentColumnHeight = 0;
		}

		// Remove the column views themselves
		for (View page : ret)
		{
			View col1 = page.findViewById(R.id.column1);
			View col2 = page.findViewById(R.id.column2);
			if (col1 != null)
				((ViewGroup) page).removeView(col1);
			if (col2 != null)
				((ViewGroup) page).removeView(col2);
		}

		// Animations?
		View page1 = ret.get(0);
		if (page1 != null && page1 instanceof AnimatedRelativeLayout)
		{
			((AnimatedRelativeLayout) page1).setStartPositions(mStoredPositions);
		}

		mPages = ret;
		mStoredPositions = getStoredPositions();
		this.updateTime();
		return ret;
	}

	/**
	 * Use this method to set optional initial starting positions for the views.
	 * 
	 * @param storedPositions
	 */
	public void setStoredPositions(SparseArray<Rect> storedPositions)
	{
		mStoredPositions = storedPositions;
	}

	private SparseArray<Rect> getStoredPositions()
	{
		if (mPages == null || mPages.size() == 0)
			return null;

		SparseArray<Rect> positions = new SparseArray<Rect>();
		for (View child : mBlueprintViews)
		{
			if (child.getId() != View.NO_ID)
			{
				if (child.getLayoutParams() != null && child.getLayoutParams() instanceof RelativeLayout.LayoutParams)
				{
					RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) child.getLayoutParams();
					Rect currentRect = new Rect(lp.leftMargin, lp.topMargin, lp.leftMargin + child.getWidth(), lp.topMargin + child.getHeight());
					positions.put(child.getId(), currentRect);
				}
			}
		}
		return positions;
	}

	private void updateTextSize()
	{
		for (View view : this.mBlueprintViews)
		{
			if (view.getId() == R.id.tvContent)
			{
				((TextView) view).setTextSize(TypedValue.COMPLEX_UNIT_PX, this.mDefaultTextSize + App.getSettings().getContentFontSizeAdjustment());
			}
			else if (view.getId() == R.id.tvAuthor)
			{
				((TextView) view).setTextSize(TypedValue.COMPLEX_UNIT_PX, this.mDefaultAuthorTextSize + App.getSettings().getContentFontSizeAdjustment());
			}
		}
	}

	private void populateViewWithItem(ViewGroup blueprint, Item story)
	{
		// Set title
		//
		TextView tv = (TextView) blueprint.findViewById(R.id.tvTitle);
		if (tv != null)
			tv.setText(story.getTitle());

		// Set image(s)
		//
		StoryMediaContentView mediaContent = (StoryMediaContentView) blueprint.findViewById(R.id.ivPhotos);
		if (mediaContent != null)
		{
			mediaContent.setMediaCollection(new MediaViewCollection(blueprint.getContext(), this, story, true), true, true);
			if (mediaContent.getCount() == 0)
				mediaContent.setVisibility(View.GONE);
			else
				mediaContent.setVisibility(View.VISIBLE);
		}

		// Author
		tv = (TextView) blueprint.findViewById(R.id.tvAuthor);
		if (tv != null)
		{
			mDefaultAuthorTextSize = tv.getTextSize();
			tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.getTextSize() + App.getSettings().getContentFontSizeAdjustment());
			if (story.getAuthor() != null)
				tv.setText(blueprint.getContext().getString(R.string.story_item_short_author, story.getAuthor()));
			else
				tv.setText(null);
		}

		// Content
		tv = (TextView) blueprint.findViewById(R.id.tvContent);
		if (tv != null)
		{
			mDefaultTextSize = tv.getTextSize();
			tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.getTextSize() + App.getSettings().getContentFontSizeAdjustment());
			tv.setText(story.getCleanMainContent());
		}

		// Set source
		//
		tv = (TextView) blueprint.findViewById(R.id.tvSource);
		if (tv != null)
		{
			tv.setText(story.getSource());
		}

		// Time
		tv = (TextView) blueprint.findViewById(R.id.tvTime);
		if (tv != null)
		{
			onUpdateNeeded((UpdatingTextView) tv);
		}

		// Read more
		tv = (TextView) blueprint.findViewById(R.id.tvReadMore);
		if (tv != null)
		{
			if (story.getLink() != null)
			{
				boolean isReadMoreEnabled = !TextUtils.isEmpty(story.getLink()) && App.getInstance().socialReader.isOnline() == SocialReader.ONLINE;

				tv.setEnabled(isReadMoreEnabled);
				if (!isReadMoreEnabled)
				{
					tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_read_orweb_gray, 0, 0, 0);
					tv.setOnClickListener(null);
				}
				else
				{
					tv.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_read_orweb, 0, 0, 0);
					if (PackageHelper.isOrwebInstalled(blueprint.getContext()))
						tv.setOnClickListener(new ReadMoreClickListener(story));
					else
						tv.setOnClickListener(new PromptOrwebClickListener(blueprint.getContext()));
				}
				tv.setVisibility(View.VISIBLE);
			}
			else
			{
				tv.setVisibility(View.GONE);
			}
		}
	}

	@Override
	public boolean usesReverseSwipe()
	{
		boolean bReverse = false;
		if (mItem != null)
		{
			// Use the bidi class to figure out the swipe direction!
			if (App.getSettings().readerSwipeDirection() == ReaderSwipeDirection.Automatic)
			{
				Bidi bidi = new Bidi(mItem.getCleanMainContent(), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
				if (!bidi.baseIsLeftToRight())
					bReverse = true;
			}
			else if (App.getSettings().readerSwipeDirection() == ReaderSwipeDirection.Ltr)
			{
				bReverse = true;
			}
		}
		return bReverse;
	}

	@Override
	public ArrayList<View> createPages(PagedView parent)
	{
		mPagedView = parent;
		if (mPages == null)
		{
			this.createBlueprintViews(parent);
		}
		relayout();
		return mPages;
	}

	@Override
	public void onUpdateNeeded(UpdatingTextView view)
	{
		if (view != null)
		{
			if (mItem != null)
			{
				view.setText(UIHelpers.dateDiffDisplayString(mItem.getPublicationTime(), view.getContext(), R.string.story_item_short_published_never,
						R.string.story_item_short_published_recently, R.string.story_item_short_published_minutes, R.string.story_item_short_published_minute,
						R.string.story_item_short_published_hours, R.string.story_item_short_published_hour, R.string.story_item_short_published_days,
						R.string.story_item_short_published_day));
			}
			else
			{
				view.setText(R.string.story_item_short_published_never);
			}
		}
	}

	protected void updateTime()
	{
		if (mPages == null)
			return;

		for (View view : mPages)
		{
			UpdatingTextView tvTime = (UpdatingTextView) view.findViewById(R.id.tvTime);
			if (tvTime != null)
			{
				this.onUpdateNeeded(tvTime);
				tvTime.setOnUpdateListener(this);
			}
		}
	}

	@Override
	public void onMediaLoaded()
	{
		Log.v("StoryItemView", "Media content has requested relayout.");
		mPagedView.recreateViewsForContent(this);
	}

	private class ReadMoreClickListener implements View.OnClickListener
	{
		private final Item mItem;

		public ReadMoreClickListener(Item item)
		{
			mItem = item;
		}

		@Override
		public void onClick(View v)
		{
			try
			{
				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mItem.getLink()));
				intent.setClassName("info.guardianproject.browser", "info.guardianproject.browser.Browser");
				// intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				v.getContext().startActivity(intent);
			}
			catch (Exception e)
			{
				Log.d(MainActivity.LOGTAG, "Error trying to open read more link: " + mItem.getLink());
			}
		}

	}

	private class PromptOrwebClickListener implements View.OnClickListener
	{
		private final Context mContext;

		public PromptOrwebClickListener(Context context)
		{
			mContext = context;
		}

		@Override
		public void onClick(View v)
		{
			AlertDialog dialog = PackageHelper.showDownloadDialog(mContext, R.string.install_orweb_title, R.string.install_orweb_prompt, android.R.string.ok,
					android.R.string.cancel, PackageHelper.URI_ORWEB_PLAY);
		}

	}
}
