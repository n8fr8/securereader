package info.guardianproject.securereader;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.tinymission.rss.Item;

import info.guardianproject.cacheword.CacheWordHandler;
import info.guardianproject.securereader.XMLRPCPublisher.XMLRPCPublisherCallback;

import net.bican.wordpress.Wordpress;

import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.codec.binary.Hex;

//import com.tinymission.rss.MediaContent.MediaContentType;

// Lots of borrowed code from:
//https://github.com/guardianproject/mrapp/blob/master/app/src/info/guardianproject/mrapp/server/ServerManager.java

// To Do:  Deal with media, Get Lists working

public class SocialReporter
{
	public static String LOGTAG = "SocialReporter";

	public static String XMLRPC_ENDPOINT = "http://ec2-23-20-243-244.compute-1.amazonaws.com/wordpress/xmlrpc.php";

	SocialReader socialReader;
	Context applicationContext;
	Wordpress wordpress;
	CacheWordHandler cacheWord;

	public SocialReporter(SocialReader _socialReader)
	{
		socialReader = _socialReader;
		applicationContext = socialReader.applicationContext;
	}

	public boolean useTor() 
	{
		return true;
	}

	public ArrayList<Item> getPosts()
	{
		Log.v(LOGTAG, "getPosts()");
		ArrayList<Item> posts = new ArrayList<Item>();
		
		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady()) {
			posts = socialReader.databaseAdapter.getFeedItems(DatabaseHelper.POSTS_FEED_ID, -1);
		} else {
			Log.e(LOGTAG,"Database not ready");
		}
		
		return posts;
	}

	public ArrayList<Item> getDrafts()
	{
		Log.v(LOGTAG, "getDrafts()");
		ArrayList<Item> drafts =  new ArrayList<Item>();
		
		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady())
		{
			drafts = socialReader.databaseAdapter.getFeedItems(DatabaseHelper.DRAFTS_FEED_ID, -1);
			
			// Debugging
			for (int i = 0; i < drafts.size(); i++)
			{
				Item draft = drafts.get(i);
				Log.v("DRAFT", draft.getTitle());
			}
		} else {
			Log.e(LOGTAG,"Database not ready");
		}
		
		return drafts;
	}

	public Item createDraft(String title, String content, ArrayList<String> tags, ArrayList<Bitmap> mediaItems)
	{
		Log.v(LOGTAG, "createDraft");
		
		Item item = new Item("BigBuffalo_" + new Date().getTime() + "" + (int) (Math.random() * 1000), title, new Date(), "SocialReporter", content,
				DatabaseHelper.DRAFTS_FEED_ID);

		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady()) {
			// Add the tags
			if (tags != null)
			{
				for (String tag : tags)
					item.addTag(tag);
			}
	
			socialReader.databaseAdapter.addOrUpdateItem(item);
		} else {
			Log.e(LOGTAG,"Database not ready");
		}
		return item;
	}

	public void saveDraft(Item story)
	{
		Log.v(LOGTAG, "saveDraft");
		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady())
		{
			socialReader.databaseAdapter.addOrUpdateItem(story);
		}
		else 
		{
			Log.e(LOGTAG,"Database not ready");
		}
	}

	public void deleteDraft(Item story)
	{
		Log.v(LOGTAG, "deleteDraft");
		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady())
		{
			socialReader.databaseAdapter.deleteItem(story.getDatabaseId());
		}
		else 
		{
			Log.e(LOGTAG,"Database not ready");
		}
	}

	public void publish(Item story, XMLRPCPublisherCallback callback)
	{
		Log.v(LOGTAG, "publish");

		if (socialReader.databaseAdapter != null && socialReader.databaseAdapter.databaseReady())
		{
			// Do the actual publishing in a background thread
			XMLRPCPublisher publisher = new XMLRPCPublisher(this);
			publisher.setXMLRPCPublisherCallback(callback);
			publisher.execute(story);
	
			story.setFeedId(DatabaseHelper.POSTS_FEED_ID);
			socialReader.databaseAdapter.addOrUpdateItem(story);
		} else {
			Log.e(LOGTAG,"Database not ready");
		}
	}
		
	/*
	public boolean isSignedIn()
	{
		Log.v(LOGTAG, "isSignedIn");
		return true;
	}
	*/
	
	public String getAuthorName()
	{		
		// Might have to check for null
		if (socialReader.ssettings != null) {
			return socialReader.ssettings.nickname();
		} else {
			return null;
		}
	}

	public void createAuthorName(String authorName)
	{
		// Might have to check for null
		if (socialReader.ssettings != null) {
			socialReader.ssettings.setNickname(authorName);
		} else {
			Log.e(LOGTAG,"Can't set nickname, SecureSettings object not created");
		}
	}
}
