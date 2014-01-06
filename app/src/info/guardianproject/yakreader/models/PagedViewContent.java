package info.guardianproject.yakreader.models;

import info.guardianproject.yakreader.widgets.PagedView;

import java.util.ArrayList;

import android.view.View;

public interface PagedViewContent
{
	boolean usesReverseSwipe();

	ArrayList<View> createPages(PagedView parent);
}
