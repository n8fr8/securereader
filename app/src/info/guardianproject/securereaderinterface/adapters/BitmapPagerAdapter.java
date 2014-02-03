package info.guardianproject.securereaderinterface.adapters;

import info.guardianproject.securereaderinterface.MainActivity;

import java.io.FileNotFoundException;
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;


public class BitmapPagerAdapter extends ObservablePagerAdapter
{
	private final Context mContext;
	ArrayList<ImageView> mPages;
	ArrayList<Uri> mBitmapUris;
	ArrayList<Bitmap> mBitmaps;

	public BitmapPagerAdapter(Context context)
	{
		super();
		mContext = context;
		mPages = new ArrayList<ImageView>();
		mBitmapUris = new ArrayList<Uri>();
		mBitmaps = new ArrayList<Bitmap>();
	}

	public void addBitmap(Context context, Uri bitmapUri)
	{
		Bitmap bitmap;
		try
		{
			bitmap = decodeUri(bitmapUri);

			ImageView view = new ImageView(context);
			view.setScaleType(ScaleType.CENTER_CROP);
			view.setImageBitmap(bitmap);
			mPages.add(view);
			mBitmapUris.add(bitmapUri);
			mBitmaps.add(bitmap);
			this.notifyDataSetChanged();
		}
		catch (FileNotFoundException e)
		{
			Log.e(MainActivity.LOGTAG, "Failed to convert bitmap: " + bitmapUri + " :" + e.toString());
		}
	}

	public ArrayList<Uri> getBitmaps()
	{
		return mBitmapUris;
	}

	@Override
	public boolean isViewFromObject(View arg0, Object arg1)
	{
		return arg0 == (View) arg1;
	}

	@Override
	public Object instantiateItem(ViewGroup container, int position)
	{
		if (position < 0 || position >= mPages.size())
			return null;

		View view = mPages.get(position);
		container.addView(view, 0);
		return view;
	}

	@Override
	public int getItemPosition(Object object)
	{
		for (int i = 0; i < mPages.size(); i++)
		{
			if (mPages.get(i).equals(object))
				return i;
		}
		return POSITION_NONE;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object)
	{
		container.removeView((View) object);
	}

	@Override
	public int getCount()
	{
		return mPages.size();
	}

	public long getItemId(int position)
	{
		if (position < mPages.size())
			return mPages.get(position).hashCode();
		return 0;
	}

	private Bitmap decodeUri(Uri selectedImage) throws FileNotFoundException
	{

		// Decode image size
		BitmapFactory.Options o = new BitmapFactory.Options();
		o.inJustDecodeBounds = true;
		BitmapFactory.decodeStream(mContext.getContentResolver().openInputStream(selectedImage), null, o);

		// The new size we want to scale to
		final int REQUIRED_SIZE = 500;

		// Find the correct scale value. It should be the power of 2.
		int width_tmp = o.outWidth, height_tmp = o.outHeight;
		int scale = 1;
		while (true)
		{
			if (width_tmp / 2 < REQUIRED_SIZE || height_tmp / 2 < REQUIRED_SIZE)
			{
				break;
			}
			width_tmp /= 2;
			height_tmp /= 2;
			scale *= 2;
		}

		// Decode with inSampleSize
		BitmapFactory.Options o2 = new BitmapFactory.Options();
		o2.inSampleSize = scale;
		return BitmapFactory.decodeStream(mContext.getContentResolver().openInputStream(selectedImage), null, o2);
	}
}
