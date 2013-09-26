package info.guardianproject.bigbuffalo.models;

import info.guardianproject.bigbuffalo.widgets.PagedView;

import java.util.ArrayList;

import android.view.View;

public interface PagedViewContent
{
	boolean usesReverseSwipe();

	ArrayList<View> createPages(PagedView parent);
}
