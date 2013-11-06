package info.guardianproject.bigbuffalo.uiutil;

import info.guardianproject.bigbuffalo.MainActivity;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ironrabbit.type.CustomTypefaceManager;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

public class FontManager
{
	public static class TransformedText
	{
		public TransformedText(CharSequence transformedText, Typeface typeface)
		{
			this.transformedText = transformedText;
			this.typeface = typeface;
		}
		
		public CharSequence transformedText;
		public Typeface typeface;
	}
	
	private static HashMap<String, Typeface> gFonts = new HashMap<String, Typeface>();
	
	public static Typeface getFontByName(Context context, String name)
	{
		if (gFonts.containsKey(name))
			return gFonts.get(name);

		try
		{
			Typeface font = Typeface.createFromAsset(context.getAssets(), "fonts/" + name + ".ttf");
			if (font != null)
			{
				gFonts.put(name, font);
			}
			return font;
		}
		catch (Exception ex)
		{
			Log.e(MainActivity.LOGTAG, "Failed to get font: " + name);
		}
		return null;
	}
	
	private static Pattern gTibetanPattern = null;
	
	public static boolean isTibetan(CharSequence text)
	{
		if (!TextUtils.isEmpty(text))
		{
			if (gTibetanPattern == null)
				gTibetanPattern = Pattern.compile("[\u0F00-\u0FFF]+", 0);
	        Matcher unicodeTibetanMatcher = gTibetanPattern.matcher(text);
	        if (unicodeTibetanMatcher.find())
	        {
	        	return true;
	        }
		}
		return false;
	}
	
	public static TransformedText transformText(TextView view, CharSequence text)
	{
		if (FontManager.isTibetan(text))
		{
        	Typeface font = FontManager.getFontByName(view.getContext(), "Jomolhari");
			String result = text.toString();
			result = CustomTypefaceManager.handlePrecompose(result);
			return new TransformedText(result, font);
		}
		return null;
	}
}
