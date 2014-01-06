package info.guardianproject.securereader;

import info.guardianproject.cacheword.CacheWordHandler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.tinymission.rss.Feed;
import com.tinymission.rss.Item;
import com.tinymission.rss.MediaContent;

public class DatabaseAdapter
{
	public static final boolean LOGGING = false;
	
	public static final String LOGTAG = "DatabaseAdapter";
	private final DatabaseHelper databaseHelper;

	private SQLiteDatabase db;

	public SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

	private final CacheWordHandler cacheword;

	public DatabaseAdapter(CacheWordHandler _cacheword, Context _context)
	{
		cacheword = _cacheword;
		SQLiteDatabase.loadLibs(_context);
		this.databaseHelper = new DatabaseHelper(cacheword, _context);
		open();
	}

	public void close()
	{
		databaseHelper.close();
	}

	public void open() throws SQLException
	{
		db = databaseHelper.getWritableDatabase();
	}

	public boolean databaseReady()
	{
		if (db.isOpen())
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public long addFeed(Feed feed)
	{
		long returnValue = -1;

		try
		{
			ContentValues values = new ContentValues();

			values.put(DatabaseHelper.FEEDS_TABLE_TITLE, feed.getTitle());
			values.put(DatabaseHelper.FEEDS_TABLE_FEED_URL, feed.getFeedURL());
			values.put(DatabaseHelper.FEEDS_TABLE_LANGUAGE, feed.getLanguage());
			values.put(DatabaseHelper.FEEDS_TABLE_DESCRIPTION, feed.getDescription());
			values.put(DatabaseHelper.FEEDS_TABLE_LINK, feed.getLink());
			values.put(DatabaseHelper.FEEDS_TABLE_STATUS, feed.getStatus());
			
			if (feed.isSubscribed())
			{
				values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, 1);
			}
			else
			{
				values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, 0);
			}

			if (feed.getNetworkPullDate() != null)
			{
				values.put(DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE, dateFormat.format(feed.getNetworkPullDate()));
			}

			if (feed.getLastBuildDate() != null)
			{
				values.put(DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE, dateFormat.format(feed.getLastBuildDate()));
			}

			if (feed.getPubDate() != null)
			{
				values.put(DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE, dateFormat.format(feed.getPubDate()));
			}

			returnValue = db.insert(DatabaseHelper.FEEDS_TABLE, null, values);
			// close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}

		return returnValue;
	}

	public long addOrUpdateFeed(Feed feed)
	{
		long returnValue = -1;

		try
		{
			if (feed.getDatabaseId() != Feed.DEFAULT_DATABASE_ID)
			{
				int columnsUpdated = updateFeed(feed);
				if (columnsUpdated == 1)
				{
					returnValue = feed.getDatabaseId();
				}
			}
			else
			{
				String query = "select " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.FEEDS_TABLE_TITLE + ", "
						+ DatabaseHelper.FEEDS_TABLE_FEED_URL + " from " + DatabaseHelper.FEEDS_TABLE + " where " + DatabaseHelper.FEEDS_TABLE_FEED_URL
						+ " = '" + feed.getFeedURL() + "';";

				Log.w(LOGTAG, query);

				Cursor queryCursor = db.rawQuery(query, new String[] {});

				if (queryCursor.getCount() == 0)
				{
					returnValue = addFeed(feed);
				}
				else
				{
					queryCursor.moveToFirst();
					returnValue = queryCursor.getLong(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_COLUMN_ID));

					int columnsUpdated = updateFeed(feed);
					if (columnsUpdated == 1)
					{
						returnValue = feed.getDatabaseId();
					}
					else
					{
						returnValue = -1;
					}
				}

				queryCursor.close();
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}

		return returnValue;
	}

	public long addFeed(String title, String feedUrl)
	{
		long returnValue = -1;

		try
		{
			ContentValues values = new ContentValues();
			values.put(DatabaseHelper.FEEDS_TABLE_TITLE, title);
			values.put(DatabaseHelper.FEEDS_TABLE_FEED_URL, feedUrl);
			values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, 1);
			values.put(DatabaseHelper.FEEDS_TABLE_STATUS, Feed.STATUS_NOT_SYNCED);

			returnValue = db.insert(DatabaseHelper.FEEDS_TABLE, null, values);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		return returnValue;
	}

	public long addFeedIfNotExisting(String title, String feedUrl)
	{
		long returnValue = -1;

		try
		{
			String query = "select " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.FEEDS_TABLE_TITLE + ", "
					+ DatabaseHelper.FEEDS_TABLE_FEED_URL + " from " + DatabaseHelper.FEEDS_TABLE + " where " + DatabaseHelper.FEEDS_TABLE_FEED_URL + " = '"
					+ feedUrl + "';";

			Log.w(LOGTAG, query);

			Cursor queryCursor = db.rawQuery(query, new String[] {});

			if (queryCursor.getCount() == 0)
			{
				returnValue = addFeed(title, feedUrl);
			}
			else
			{
				queryCursor.moveToFirst();
				returnValue = queryCursor.getLong(queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_COLUMN_ID));
			}

			queryCursor.close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}

		return returnValue;
	}

	public boolean deleteFeed(long feedDatabaseId)
	{
		boolean returnValue = false;

		try
		{
			deleteFeedItems(feedDatabaseId);

			returnValue = db.delete(DatabaseHelper.FEEDS_TABLE, DatabaseHelper.FEEDS_TABLE_COLUMN_ID + "=" + feedDatabaseId, null) > 0;
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}

		return returnValue;
	}

	public int updateFeed(Feed feed)
	{
		ContentValues values = new ContentValues();
		values.put(DatabaseHelper.FEEDS_TABLE_TITLE, feed.getTitle());
		values.put(DatabaseHelper.FEEDS_TABLE_FEED_URL, feed.getFeedURL());
		values.put(DatabaseHelper.FEEDS_TABLE_LANGUAGE, feed.getLanguage());
		values.put(DatabaseHelper.FEEDS_TABLE_DESCRIPTION, feed.getDescription());
		values.put(DatabaseHelper.FEEDS_TABLE_LINK, feed.getLink());
		values.put(DatabaseHelper.FEEDS_TABLE_STATUS, feed.getStatus());
		if (feed.isSubscribed())
		{
			values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, 1);
		}
		else
		{
			values.put(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED, 0);
		}

		if (feed.getNetworkPullDate() != null)
		{
			values.put(DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE, dateFormat.format(feed.getNetworkPullDate()));
		}

		if (feed.getLastBuildDate() != null)
		{
			values.put(DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE, dateFormat.format(feed.getLastBuildDate()));
		}

		if (feed.getPubDate() != null)
		{
			values.put(DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE, dateFormat.format(feed.getPubDate()));
		}

		int returnValue = db
				.update(DatabaseHelper.FEEDS_TABLE, values, DatabaseHelper.FEEDS_TABLE_COLUMN_ID + "=?", new String[] { "" + feed.getDatabaseId() });

		return returnValue;
	}
	
	public void deleteExpiredItems(Date expirationDate) {
		Cursor queryCursor = null;

		try
		{
			/*	
			String blahQuery = "select " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE 
					+ ", strftime('%s'," + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE 
					+ ") " 
					+ " from " + DatabaseHelper.ITEMS_TABLE + " where 1=1;";
			
			Cursor blahQueryCursor = db.rawQuery(blahQuery, new String[]{});
			if (blahQueryCursor.moveToFirst())
			{
				do
				{
					String datetime = blahQueryCursor.getString(0);
					
					//Log.v(LOGTAG,"is datetime: " + datetime + " < " + expirationDate.getTime()/1000);
					if (datetime != null && Long.parseLong(datetime) < expirationDate.getTime()/1000) {
						Log.v(LOGTAG,"datetime: " + datetime + " < " + expirationDate.getTime()/1000);						
					} else {
						Log.v(LOGTAG,"datetime: " + datetime + " > " + expirationDate.getTime()/1000);						
					}
				}
				while (blahQueryCursor.moveToNext());
			}	
			blahQueryCursor.close();
			*/
			
			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", "
					+ DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " 
					+ DatabaseHelper.ITEMS_TABLE_FEED_ID + " from " + DatabaseHelper.ITEMS_TABLE + " where " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " < '"
					+ dateFormat.format(expirationDate) + "' and " + DatabaseHelper.ITEMS_TABLE_FAVORITE + " != 1 and " + DatabaseHelper.ITEMS_TABLE_SHARED + " != 1 order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ";";

			if (LOGGING)
				Log.w(LOGTAG, query);

			queryCursor = db.rawQuery(query, new String[] {});
			
			if (LOGGING)
				Log.v(LOGTAG,"Count " + queryCursor.getCount());

			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
			int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
			int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);

			if (queryCursor.moveToFirst())
			{
				do
				{
					int id = queryCursor.getInt(idColumn);
					String title = queryCursor.getString(titleColumn);
					String publishDate = queryCursor.getString(publishDateColumn);
					
					if (LOGGING)
						Log.v(LOGTAG,"Going to delete " + id + " " + title + " " + publishDate);
					this.deleteItem(id);
				}
				while (queryCursor.moveToNext());
			}

			queryCursor.close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}		
	}

	public Feed fillFeedObject(Feed feed)
	{
		try
		{

			String query = "select " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.FEEDS_TABLE_TITLE + ", "
					+ DatabaseHelper.FEEDS_TABLE_FEED_URL + ", " + DatabaseHelper.FEEDS_TABLE_DESCRIPTION + ", " + DatabaseHelper.FEEDS_TABLE_LANGUAGE + ", "
					+ DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE + ", " + DatabaseHelper.FEEDS_TABLE_LINK + ", " + DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE
					+ ", " + DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + ", " + DatabaseHelper.FEEDS_TABLE_STATUS + " from " + DatabaseHelper.FEEDS_TABLE
					+ " where " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + "=" + feed.getDatabaseId() + ";";

			if (LOGGING)
				Log.w(LOGTAG, query);

			Cursor queryCursor = db.rawQuery(query, new String[] {});

			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_COLUMN_ID);
			int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_TITLE);
			int feedURLColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_FEED_URL);
			int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_DESCRIPTION);
			int languageColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LANGUAGE);
			int lastBuildDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE);
			int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LINK);
			int networkPullDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE);
			int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE);
			int subscribedColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED);
			int statusColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_STATUS);
			
			if (queryCursor.moveToFirst())
			{
				int id = queryCursor.getInt(idColumn);
				feed.setDatabaseId(id);

				String title = queryCursor.getString(titleColumn);
				feed.setTitle(title);

				String feedUrl = queryCursor.getString(feedURLColumn);
				feed.setFeedURL(feedUrl);

				if (queryCursor.getString(descriptionColumn) != null)
				{
					feed.setDescription(queryCursor.getString(descriptionColumn));
				}

				if (queryCursor.getString(languageColumn) != null)
				{
					feed.setLanguage(queryCursor.getString(languageColumn));
				}

				if (queryCursor.getString(lastBuildDateColumn) != null)
				{
					feed.setLastBuildDate(queryCursor.getString(lastBuildDateColumn));
				}

				if (queryCursor.getString(networkPullDateColumn) != null)
				{
					feed.setNetworkPullDate(queryCursor.getString(networkPullDateColumn));
				}

				if (queryCursor.getString(linkColumn) != null)
				{
					feed.setLink(queryCursor.getString(linkColumn));
				}

				if (queryCursor.getString(publishDateColumn) != null)
				{
					feed.setPubDate(queryCursor.getString(publishDateColumn));
				}

				if (queryCursor.getInt(subscribedColumn) == 1)
				{
					feed.setSubscribed(true);
				}
				else
				{
					feed.setSubscribed(false);
				}
				
				feed.setStatus(queryCursor.getInt(statusColumn));
			}

			queryCursor.close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}

		return feed;
	}

	public ArrayList<Feed> getSubscribedFeeds()
	{
		return getAllFeeds(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + " = 1");
	}

	public ArrayList<Feed> getUnSubscribedFeeds()
	{
		return getAllFeeds(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + " = 0");
	}

	public Feed getSubscribedFeedItems(int numItems)
	{
		Cursor queryCursor = null;
		Feed feed = new Feed();
		
		try
		{
			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", " + DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED
					+ ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", "
					+ DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + ", "
					+ DatabaseHelper.FEEDS_TABLE_TITLE + ", " + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED
					+ " from " + DatabaseHelper.ITEMS_TABLE + ", " + DatabaseHelper.FEEDS_TABLE
					+ " where " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID 
					+ " and " + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + " = 1"
					+ " order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " DESC"
					+ " limit " + numItems + ";";
	
			if (LOGGING)
				Log.w(LOGTAG, query);

			queryCursor = db.rawQuery(query, new String[] {});

			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
			int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
			int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
			int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
			int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
			int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
			int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
			int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
			int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
			int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
			int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
			int feedIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
			int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
			
			int feedTableFeedIdColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_COLUMN_ID);
			int feedTableFeedTitle = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_TITLE);
			int feedTableFeedSubscribe = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED);
					

			if (queryCursor.moveToFirst())
			{
				do
				{
					int id = queryCursor.getInt(idColumn);
					String description = queryCursor.getString(descriptionColumn);
					String contentEncoded = queryCursor.getString(contentEncodedColumn);
					String title = queryCursor.getString(titleColumn);
					long feedId = queryCursor.getLong(feedIdColumn);
					String publishDate = queryCursor.getString(publishDateColumn);
					String guid = queryCursor.getString(guidColumn);

					String author = queryCursor.getString(authorColumn);
					String category = queryCursor.getString(categoryColumn);
					int favorite = queryCursor.getInt(favoriteColumn);
					int shared = queryCursor.getInt(sharedColumn);
					String link = queryCursor.getString(linkColumn);
					
					String feedTitle = queryCursor.getString(feedTableFeedTitle);
					

					Item item = new Item(guid, title, publishDate, feedTitle, description, feedId);
					item.setDatabaseId(id);
					item.setAuthor(author);
					item.setCategory(category);
					item.setContentEncoded(contentEncoded);
					if (favorite == 1)
					{
						item.setFavorite(true);
					}
					else
					{
						item.setFavorite(false);
					}
					if (shared == 1) {
						item.setShared(true);
					} else {
						item.setShared(false);
					}
					
					item.setGuid(guid);
					item.setLink(link);

					feed.addItem(item);
					
					Log.v(LOGTAG, "Added " + item.getFeedId() + " " + item.getDatabaseId() + " " + item.getTitle() + " " + item.getPubDate());
				}
				while (queryCursor.moveToNext());
			}

			queryCursor.close();
			
			for(Item item : feed.getItems())      
			    item.setMediaContent(getItemMedia(item));
			
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}
		return feed;		
	}	
	
	public ArrayList<Feed> getAllFeeds()
	{
		return getAllFeeds("");
	}

	public ArrayList<Feed> getAllFeeds(String whereClause)
	{
		ArrayList<Feed> feeds = new ArrayList<Feed>();

		try
		{
			StringBuilder query = new StringBuilder("select " + DatabaseHelper.FEEDS_TABLE_COLUMN_ID + ", " + DatabaseHelper.FEEDS_TABLE_TITLE + ", "
					+ DatabaseHelper.FEEDS_TABLE_FEED_URL + ", " + DatabaseHelper.FEEDS_TABLE_DESCRIPTION + ", " + DatabaseHelper.FEEDS_TABLE_LANGUAGE + ", "
					+ DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE + ", " + DatabaseHelper.FEEDS_TABLE_LINK + ", " + DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE
					+ ", " + DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.FEEDS_TABLE_SUBSCRIBED + ", " + DatabaseHelper.FEEDS_TABLE_STATUS + " from " + DatabaseHelper.FEEDS_TABLE);

			if (!whereClause.isEmpty())
			{
				query.append(" where " + whereClause);
			}

			query.append(";");

			if (LOGGING)
				Log.w(LOGTAG, query.toString());

			Cursor queryCursor = db.rawQuery(query.toString(), new String[] {});

			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_COLUMN_ID);
			int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_TITLE);
			int feedURLColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_FEED_URL);
			int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_DESCRIPTION);
			int languageColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LANGUAGE);
			int lastBuildDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LAST_BUILD_DATE);
			int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_LINK);
			int networkPullDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_NETWORK_PULL_DATE);
			int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_PUBLISH_DATE);
			int subscribedColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_SUBSCRIBED);
			int statusColumn = queryCursor.getColumnIndex(DatabaseHelper.FEEDS_TABLE_STATUS);

			if (queryCursor.moveToFirst())
			{
				do
				{
					int id = queryCursor.getInt(idColumn);
					String title = queryCursor.getString(titleColumn);
					String feedUrl = queryCursor.getString(feedURLColumn);

					Feed tempFeed = new Feed(id, title, feedUrl);

					if (queryCursor.getString(descriptionColumn) != null)
					{
						tempFeed.setDescription(queryCursor.getString(descriptionColumn));
					}

					if (queryCursor.getString(languageColumn) != null)
					{
						tempFeed.setLanguage(queryCursor.getString(languageColumn));
					}

					if (queryCursor.getString(lastBuildDateColumn) != null)
					{
						tempFeed.setLastBuildDate(queryCursor.getString(lastBuildDateColumn));
					}

					if (queryCursor.getString(networkPullDateColumn) != null)
					{
						tempFeed.setNetworkPullDate(queryCursor.getString(networkPullDateColumn));
					}

					if (queryCursor.getString(linkColumn) != null)
					{
						tempFeed.setLink(queryCursor.getString(linkColumn));
					}

					if (queryCursor.getString(publishDateColumn) != null)
					{
						tempFeed.setPubDate(queryCursor.getString(publishDateColumn));
					}

					if (queryCursor.getInt(subscribedColumn) == 1)
					{
						tempFeed.setSubscribed(true);
					}
					else
					{
						tempFeed.setSubscribed(false);
					}
					
					tempFeed.setStatus(queryCursor.getInt(statusColumn));

					feeds.add(tempFeed);
				}
				while (queryCursor.moveToNext());

			}

			queryCursor.close();
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		return feeds;
	}

	public void deleteFeedItems(long feedDatabaseId)
	{
		ArrayList<Item> feedItems = getFeedItems(feedDatabaseId, -1);
		for (Item item : feedItems)
		{
			deleteItem(item.getDatabaseId());
		}
	}

	public boolean deleteItem(long itemDatabaseId)
	{
		deleteItemMedia(itemDatabaseId);
		deleteItemTags(itemDatabaseId);
		
		boolean returnValue = false;

		try
		{
			returnValue = db.delete(DatabaseHelper.ITEMS_TABLE, DatabaseHelper.ITEMS_TABLE_COLUMN_ID + "=" + itemDatabaseId, null) > 0;
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}

		return returnValue;
	}

	public int updateItem(Item item)
	{
		int returnValue = -1;

		try
		{

			// private final ArrayList<Comment> _comments;
			// private final ArrayList<MediaContent> _mediaContent;
			// private final ArrayList<String> _tags;

			// Should this be here??
			// private MediaThumbnail _mediaThumbnail;

			ContentValues values = new ContentValues();
			values.put(DatabaseHelper.ITEMS_TABLE_AUTHOR, item.getAuthor());
			values.put(DatabaseHelper.ITEMS_TABLE_CATEGORY, item.getCategory());
			values.put(DatabaseHelper.ITEMS_TABLE_DESCRIPTION, item.getDescription());
			values.put(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED, item.getContentEncoded());
			values.put(DatabaseHelper.ITEMS_TABLE_FAVORITE, item.getFavorite());
			values.put(DatabaseHelper.ITEMS_TABLE_SHARED, item.getShared());
			values.put(DatabaseHelper.ITEMS_TABLE_GUID, item.getGuid());
			values.put(DatabaseHelper.ITEMS_TABLE_LINK, item.getLink());
			values.put(DatabaseHelper.ITEMS_TABLE_SOURCE, item.getSource());
			values.put(DatabaseHelper.ITEMS_TABLE_TITLE, item.getTitle());
			values.put(DatabaseHelper.ITEMS_TABLE_FEED_ID, item.getFeedId());

			if (item.getPubDate() != null)
			{
				values.put(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE, dateFormat.format(item.getPubDate()));
			}

			returnValue = db
					.update(DatabaseHelper.ITEMS_TABLE, values, DatabaseHelper.ITEMS_TABLE_COLUMN_ID + "=?", new String[] { "" + item.getDatabaseId() });
						
			addOrUpdateItemMedia(item, item.getMediaContent());
			addOrUpdateItemTags(item);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}

		return returnValue;
	}

	public Feed getFavoriteFeedItems(Feed feed)
	{
		Cursor queryCursor = null;

		try
		{
			feed.clearItems();

			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", " + DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED
					+ ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", "
					+ DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " from " + DatabaseHelper.ITEMS_TABLE + " where " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = "
					+ feed.getDatabaseId() + " and " + DatabaseHelper.ITEMS_TABLE_FAVORITE + " = 1 order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ";";

			if (LOGGING)
				Log.w(LOGTAG, query);

			queryCursor = db.rawQuery(query, new String[] {});

			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
			int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
			int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
			int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
			int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
			int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
			int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
			int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
			int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
			int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
			int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
			int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);

			if (queryCursor.moveToFirst())
			{
				do
				{
					int id = queryCursor.getInt(idColumn);
					String description = queryCursor.getString(descriptionColumn);
					String contentEncoded = queryCursor.getString(contentEncodedColumn);
					String title = queryCursor.getString(titleColumn);
					long feedId = queryCursor.getLong(feedIdColunn);
					String publishDate = queryCursor.getString(publishDateColumn);
					String guid = queryCursor.getString(guidColumn);

					String author = queryCursor.getString(authorColumn);
					String category = queryCursor.getString(categoryColumn);
					int favorite = queryCursor.getInt(favoriteColumn);
					String link = queryCursor.getString(linkColumn);

					Item item = new Item(guid, title, publishDate, feed.getTitle(), description, feed.getDatabaseId());
					item.setDatabaseId(id);
					item.setAuthor(author);
					item.setCategory(category);
					item.setContentEncoded(contentEncoded);
					if (favorite == 1)
					{
						item.setFavorite(true);
					}
					else
					{
						item.setFavorite(false);
					}
					item.setGuid(guid);
					item.setLink(link);

					feed.addItem(item);
				}
				while (queryCursor.moveToNext());
			}

			queryCursor.close();

			for(Item item : feed.getItems())      
			    item.setMediaContent(getItemMedia(item));
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}		

		return feed;

	}
	
	public Feed getAllSharedItems() {
		Cursor queryCursor = null;
		Feed feed = new Feed();
		
		try
		{
			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", " + DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED
					+ ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", "
					+ DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + " from " + DatabaseHelper.ITEMS_TABLE + " where "
					+ DatabaseHelper.ITEMS_TABLE_SHARED + " = 1 order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ";";

			if (LOGGING)
				Log.w(LOGTAG, query);

			queryCursor = db.rawQuery(query, new String[] {});

			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
			int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
			int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
			int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
			int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
			int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
			int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
			int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
			int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
			int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
			int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
			int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
			int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);

			if (queryCursor.moveToFirst())
			{
				do
				{
					int id = queryCursor.getInt(idColumn);
					String description = queryCursor.getString(descriptionColumn);
					String contentEncoded = queryCursor.getString(contentEncodedColumn);
					String title = queryCursor.getString(titleColumn);
					long feedId = queryCursor.getLong(feedIdColunn);
					String publishDate = queryCursor.getString(publishDateColumn);
					String guid = queryCursor.getString(guidColumn);

					String author = queryCursor.getString(authorColumn);
					String category = queryCursor.getString(categoryColumn);
					int favorite = queryCursor.getInt(favoriteColumn);
					int shared = queryCursor.getInt(sharedColumn);
					String link = queryCursor.getString(linkColumn);

					Item item = new Item(guid, title, publishDate, feed.getTitle(), description, feed.getDatabaseId());
					item.setDatabaseId(id);
					item.setAuthor(author);
					item.setCategory(category);
					item.setContentEncoded(contentEncoded);
					if (favorite == 1)
					{
						item.setFavorite(true);
					}
					else
					{
						item.setFavorite(false);
					}
					if (shared == 1) {
						item.setShared(true);
					} else {
						item.setShared(false);
					}
					
					item.setGuid(guid);
					item.setLink(link);

					feed.addItem(item);
				}
				while (queryCursor.moveToNext());
			}

			queryCursor.close();
			
			for(Item item : feed.getItems())      
			    item.setMediaContent(getItemMedia(item));
			
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}
		return feed;		
	}

	public Feed getSharedFeedItems(Feed feed)
	{
		Cursor queryCursor = null;
		
		try
		{
			feed.clearItems();

			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", " + DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED
					+ ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", "
					+ DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " from " + DatabaseHelper.ITEMS_TABLE + " where " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = "
					+ feed.getDatabaseId() + " and " + DatabaseHelper.ITEMS_TABLE_SHARED + " = 1 order by " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + ";";

			if (LOGGING)
				Log.w(LOGTAG, query);

			queryCursor = db.rawQuery(query, new String[] {});

			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
			int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
			int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
			int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
			int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
			int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
			int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
			int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
			int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
			int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
			int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
			int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
			int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);

			if (queryCursor.moveToFirst())
			{
				do
				{
					int id = queryCursor.getInt(idColumn);
					String description = queryCursor.getString(descriptionColumn);
					String contentEncoded = queryCursor.getString(contentEncodedColumn);
					String title = queryCursor.getString(titleColumn);
					long feedId = queryCursor.getLong(feedIdColunn);
					String publishDate = queryCursor.getString(publishDateColumn);
					String guid = queryCursor.getString(guidColumn);

					String author = queryCursor.getString(authorColumn);
					String category = queryCursor.getString(categoryColumn);
					int favorite = queryCursor.getInt(favoriteColumn);
					int shared = queryCursor.getInt(sharedColumn);
					String link = queryCursor.getString(linkColumn);

					Item item = new Item(guid, title, publishDate, feed.getTitle(), description, feed.getDatabaseId());
					item.setDatabaseId(id);
					item.setAuthor(author);
					item.setCategory(category);
					item.setContentEncoded(contentEncoded);
					if (favorite == 1)
					{
						item.setFavorite(true);
					}
					else
					{
						item.setFavorite(false);
					}
					if (shared == 1) {
						item.setShared(true);
					} else {
						item.setShared(false);
					}
					
					item.setGuid(guid);
					item.setLink(link);

					feed.addItem(item);
				}
				while (queryCursor.moveToNext());
			}

			queryCursor.close();
			
			for(Item item : feed.getItems())      
			    item.setMediaContent(getItemMedia(item));
			
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}
		return feed;

	}	
	
	/*
	 * This returns an arraylist of items given a feed id It creates a temp feed
	 * with that ID to use the below method to do the query
	 */
	public ArrayList<Item> getFeedItems(long feedId, int numItems)
	{
		Feed tempFeed = new Feed();
		tempFeed.setDatabaseId(feedId);
		tempFeed = getFeedItems(tempFeed, numItems);
		ArrayList<Item> items = tempFeed.getItems();
		return items;
	}

	public Item getItemById(long itemId) {
		Item returnItem = null;
		Cursor queryCursor = null;
		
		try
		{
			String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
					+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", "
					+ DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED + ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " + DatabaseHelper.ITEMS_TABLE_SHARED + ", " 
					+ DatabaseHelper.ITEMS_TABLE_GUID + ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", " + DatabaseHelper.ITEMS_TABLE_SOURCE + ", " 
					+ DatabaseHelper.ITEMS_TABLE_TITLE + ", " + DatabaseHelper.ITEMS_TABLE_FEED_ID + ", " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE 
					+ " from " + DatabaseHelper.ITEMS_TABLE
					+ " where " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + " = " + itemId + ";";
			
			if (LOGGING)	
				Log.v(LOGTAG,query);
			
			queryCursor = db.rawQuery(query, new String[] {});
	
			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
			int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
			int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
			int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
			int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
			int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
			int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
			int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
			int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
			int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
			int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
			int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
			int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);
	
			if (queryCursor.moveToFirst())
			{
				int id = queryCursor.getInt(idColumn);
				String description = queryCursor.getString(descriptionColumn);
				String contentEncoded = queryCursor.getString(contentEncodedColumn);
				String title = queryCursor.getString(titleColumn);
				long feedId = queryCursor.getLong(feedIdColunn);
				String publishDate = queryCursor.getString(publishDateColumn);
				String guid = queryCursor.getString(guidColumn);
	
				String author = queryCursor.getString(authorColumn);
				String category = queryCursor.getString(categoryColumn);
				int favorite = queryCursor.getInt(favoriteColumn);
				int shared = queryCursor.getInt(sharedColumn);
	
				String source = queryCursor.getString(sourceColumn);
				String link = queryCursor.getString(linkColumn);
	
				returnItem = new Item(guid, title, publishDate, source, description, Feed.DEFAULT_DATABASE_ID);
				returnItem.setDatabaseId(id);
				returnItem.setAuthor(author);
				returnItem.setCategory(category);
				returnItem.setContentEncoded(contentEncoded);
	
				if (favorite == 1)
				{
					returnItem.setFavorite(true);
				}
				else
				{
					returnItem.setFavorite(false);
				}
				if (shared == 1) {
					returnItem.setShared(true);
				} else {
					returnItem.setShared(false);
				}
	
				returnItem.setGuid(guid);
				returnItem.setLink(link);
			}
			queryCursor.close();
			
			returnItem.setMediaContent(this.getItemMedia(returnItem));
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}
		return returnItem;
	}
	
	public Feed getFeedItems(Feed feed, int numItems)
	{
		Cursor queryCursor = null;
		
		try
		{
			feed.clearItems();
			String query = "";
			if (numItems > 0)
			{
				query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
						+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", "
						+ DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED + ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " 
						+ DatabaseHelper.ITEMS_TABLE_SHARED + ", " + DatabaseHelper.ITEMS_TABLE_GUID
						+ ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", " + DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", "
						+ DatabaseHelper.ITEMS_TABLE_FEED_ID + ", " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " from " + DatabaseHelper.ITEMS_TABLE
						+ " where " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = " + feed.getDatabaseId() + " order by "
						+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " DESC LIMIT " + numItems + ";";
			}
			else
			{
				query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_AUTHOR + ", "
						+ DatabaseHelper.ITEMS_TABLE_CATEGORY + ", " + DatabaseHelper.ITEMS_TABLE_DESCRIPTION + ", "
						+ DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED + ", " + DatabaseHelper.ITEMS_TABLE_FAVORITE + ", " 
						+ DatabaseHelper.ITEMS_TABLE_SHARED + ", " + DatabaseHelper.ITEMS_TABLE_GUID
						+ ", " + DatabaseHelper.ITEMS_TABLE_LINK + ", " + DatabaseHelper.ITEMS_TABLE_SOURCE + ", " + DatabaseHelper.ITEMS_TABLE_TITLE + ", "
						+ DatabaseHelper.ITEMS_TABLE_FEED_ID + ", " + DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " from " + DatabaseHelper.ITEMS_TABLE
						+ " where " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = " + feed.getDatabaseId() + " order by "
						+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " DESC;";
			}
			
			if (LOGGING)
				Log.w(LOGTAG, query);

			queryCursor = db.rawQuery(query, new String[] {});

			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID);
			int authorColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_AUTHOR);
			int categoryColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CATEGORY);
			int descriptionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_DESCRIPTION);
			int contentEncodedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED);
			int favoriteColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FAVORITE);
			int sharedColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SHARED);
			int guidColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_GUID);
			int linkColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_LINK);
			int sourceColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_SOURCE);
			int titleColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_TITLE);
			int feedIdColunn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_FEED_ID);
			int publishDateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE);

			if (queryCursor.moveToFirst())
			{
				do
				{
					int id = queryCursor.getInt(idColumn);
					String description = queryCursor.getString(descriptionColumn);
					String contentEncoded = queryCursor.getString(contentEncodedColumn);
					String title = queryCursor.getString(titleColumn);
					long feedId = queryCursor.getLong(feedIdColunn);
					String publishDate = queryCursor.getString(publishDateColumn);
					String guid = queryCursor.getString(guidColumn);

					String author = queryCursor.getString(authorColumn);
					String category = queryCursor.getString(categoryColumn);
					int favorite = queryCursor.getInt(favoriteColumn);
					int shared = queryCursor.getInt(sharedColumn);

					String link = queryCursor.getString(linkColumn);

					Item item = new Item(guid, title, publishDate, feed.getTitle(), description, feed.getDatabaseId());
					item.setDatabaseId(id);
					item.setAuthor(author);
					item.setCategory(category);
					item.setContentEncoded(contentEncoded);
					if (favorite == 1)
					{
						item.setFavorite(true);
					}
					else
					{
						item.setFavorite(false);
					}
					
					if (shared == 1) {
						item.setShared(true);
					} else {
						item.setShared(false);
					}

					item.setGuid(guid);
					item.setLink(link);

					feed.addItem(item);
				}
				while (queryCursor.moveToNext());
				
				queryCursor.close();
				
				for(Item item : feed.getItems()) {
				    item.setMediaContent(getItemMedia(item));
				    item.setCategories(getItemTags(item));
				}
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}

		return feed;
	}

	public long addItem(Item item)
	{
		long returnValue = -1;

		try
		{
			ContentValues values = new ContentValues();
			values.put(DatabaseHelper.ITEMS_TABLE_AUTHOR, item.getAuthor());
			values.put(DatabaseHelper.ITEMS_TABLE_TITLE, item.getTitle());
			values.put(DatabaseHelper.ITEMS_TABLE_FEED_ID, item.getFeedId());
			values.put(DatabaseHelper.ITEMS_TABLE_CATEGORY, item.getCategory());
			values.put(DatabaseHelper.ITEMS_TABLE_COMMENTS_URL, item.getCommentsUrl());
			values.put(DatabaseHelper.ITEMS_TABLE_DESCRIPTION, item.getDescription());
			values.put(DatabaseHelper.ITEMS_TABLE_CONTENT_ENCODED, item.getContentEncoded());
			values.put(DatabaseHelper.ITEMS_TABLE_GUID, item.getGuid());
			values.put(DatabaseHelper.ITEMS_TABLE_LINK, item.getLink());
			values.put(DatabaseHelper.ITEMS_TABLE_SOURCE, item.getSource());
			values.put(DatabaseHelper.ITEMS_TABLE_SHARED, item.getShared());
			values.put(DatabaseHelper.ITEMS_TABLE_FAVORITE, item.getFavorite());

			if (item.getPubDate() != null)
			{
				values.put(DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE, dateFormat.format(item.getPubDate()));
			}

			returnValue = db.insert(DatabaseHelper.ITEMS_TABLE, null, values);

			item.setDatabaseId(returnValue);

			this.addOrUpdateItemMedia(item, item.getMediaContent());
			addOrUpdateItemTags(item);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		return returnValue;
	}

	public long addOrUpdateItem(Item item)
	{
		long returnValue = -1;
		Cursor queryCursor = null;
		
		try
		{
			if (item.getDatabaseId() == Item.DEFAULT_DATABASE_ID)
			{
				String query = "select " + DatabaseHelper.ITEMS_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEMS_TABLE_GUID + ", "
						+ DatabaseHelper.ITEMS_TABLE_FEED_ID + " from " + DatabaseHelper.ITEMS_TABLE + " where " + DatabaseHelper.ITEMS_TABLE_GUID + " = '"
						+ item.getGuid() + "' and " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " = " + item.getFeedId() + ";";

				if (LOGGING)
					Log.w(LOGTAG, query);

				queryCursor = db.rawQuery(query, new String[] {});

				if (LOGGING)
					Log.v(LOGTAG, "Got " + queryCursor.getCount() + " results");

				if (queryCursor.getCount() == 0)
				{
					returnValue = addItem(item);
				}
				else
				{
					queryCursor.moveToFirst();

					returnValue = queryCursor.getLong(queryCursor.getColumnIndex(DatabaseHelper.ITEMS_TABLE_COLUMN_ID));

					item.setDatabaseId(returnValue);
					int columnCount = updateItem(item);
					if (columnCount != 1)
					{
						returnValue = -1;
					}

				}
				queryCursor.close();
			}
			else
			{
				int columnCount = updateItem(item);

				if (columnCount != 1)
				{
					returnValue = -1;
				}
				else
				{
					returnValue = item.getDatabaseId();
				}
			}
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}		
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}

		return returnValue;
	}

	public ArrayList<MediaContent> getItemMedia(Item item)
	{
		ArrayList<MediaContent> mediaContent = new ArrayList<MediaContent>();
		
		//Log.v(LOGTAG,"Skipping getItemMedia");
		if (LOGGING)
			Log.v(LOGTAG,"getItemMedia");
		
		String query = "select " + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEM_MEDIA_ITEM_ID + ", "
				+ DatabaseHelper.ITEM_MEDIA_URL + ", " + DatabaseHelper.ITEM_MEDIA_TYPE + ", " + DatabaseHelper.ITEM_MEDIA_MEDIUM + ", "
				+ DatabaseHelper.ITEM_MEDIA_HEIGHT + ", " + DatabaseHelper.ITEM_MEDIA_WIDTH + ", " + DatabaseHelper.ITEM_MEDIA_FILESIZE + ", "
				+ DatabaseHelper.ITEM_MEDIA_DURATION + ", " + DatabaseHelper.ITEM_MEDIA_DEFAULT + ", " + DatabaseHelper.ITEM_MEDIA_EXPRESSION + ", "
				+ DatabaseHelper.ITEM_MEDIA_BITRATE + ", " + DatabaseHelper.ITEM_MEDIA_FRAMERATE + ", " + DatabaseHelper.ITEM_MEDIA_LANG + ", "
				+ DatabaseHelper.ITEM_MEDIA_SAMPLE_RATE + " from " + DatabaseHelper.ITEM_MEDIA_TABLE + " where " + DatabaseHelper.ITEM_MEDIA_ITEM_ID + " = "
				+ "?;";

		if (LOGGING)
			Log.w(LOGTAG, query);

		Cursor queryCursor = null;
		
		try
		{
			queryCursor = db.rawQuery(query, new String[] {""+item.getDatabaseId()});

			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID);
			int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_ITEM_ID);
			int urlColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_URL);
			int typeColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TYPE);
			int mediumColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_MEDIUM);
			int heightColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_HEIGHT);
			int widthColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_WIDTH);
			int filesizeColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_FILESIZE);
			int durationColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_DURATION);
			int defaultColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_DEFAULT);
			int expressionColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_EXPRESSION);
			int bitrateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_BITRATE);
			int framerateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_FRAMERATE);
			int langColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_LANG);
			int samplerateColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_SAMPLE_RATE);

			if (queryCursor.moveToFirst())
			{
				do {
					long id = queryCursor.getLong(idColumn);
					long itemId = queryCursor.getLong(itemIdColumn);
					String url = queryCursor.getString(urlColumn);
					String type = queryCursor.getString(typeColumn);
					String medium = queryCursor.getString(mediumColumn);
					int height = queryCursor.getInt(heightColumn);
					int width = queryCursor.getInt(widthColumn);
					int filesize = queryCursor.getInt(filesizeColumn);
					int duration = queryCursor.getInt(durationColumn);
					boolean isDefault = false;
					if (queryCursor.getInt(defaultColumn) == 1)
					{
						isDefault = true;
					}
					String expression = queryCursor.getString(expressionColumn);
					int bitrate = queryCursor.getInt(bitrateColumn);
					int framerate = queryCursor.getInt(framerateColumn);
					String lang = queryCursor.getString(langColumn);
					String samplerate = queryCursor.getString(samplerateColumn);
	
					if (LOGGING)
						Log.v(LOGTAG,"new MediaContent " + url);
					
					MediaContent mc = new MediaContent(itemId, url, type);
					mc.setDatabaseId(id);
					mc.setMedium(medium);
					mc.setHeight(height);
					mc.setWidth(width);
					mc.setFileSize(filesize);
					mc.setDuration(duration);
					mc.setIsDefault(isDefault);
					mc.setExpression(expression);
					mc.setBitrate(bitrate);
					mc.setFramerate(framerate);
					mc.setLang(lang);
					mc.setSampligRate(samplerate);
	
					mediaContent.add(mc);
				} while (queryCursor.moveToNext());
			}

			queryCursor.close();

			if (LOGGING)
				Log.v(LOGTAG, "There is " + mediaContent.size() + " media for the item");
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}

		return mediaContent;
	}

	public void addOrUpdateItemMedia(Item item, ArrayList<MediaContent> itemMediaList)
	{
		if (LOGGING)
			Log.v(LOGTAG,"addOrUpdateItemMedia");
		for (MediaContent itemMedia : itemMediaList)
		{
			addOrUpdateItemMedia(item, itemMedia);
			if (LOGGING)
				Log.v(LOGTAG,"itemMedia added or updated: " + itemMedia.getDatabaseId());
		}
	}

	public long addOrUpdateItemMedia(Item item, MediaContent itemMedia)
	{
		long returnValue = -1;
		if (itemMedia.getDatabaseId() == MediaContent.DEFAULT_DATABASE_ID)
		{
			String query = "select " + DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + ", " + DatabaseHelper.ITEM_MEDIA_URL + ", "
					+ DatabaseHelper.ITEM_MEDIA_ITEM_ID + " from " + DatabaseHelper.ITEM_MEDIA_TABLE + " where " + DatabaseHelper.ITEM_MEDIA_URL + " =? and " 
					+ DatabaseHelper.ITEM_MEDIA_ITEM_ID + " =?;";

			if (LOGGING)
				Log.w(LOGTAG, query);

			Cursor queryCursor = db.rawQuery(query, new String[] {itemMedia.getUrl(), "" + item.getDatabaseId()});

			if (queryCursor.getCount() == 0)
			{
				queryCursor.close();
				
				if (LOGGING)
					Log.v(LOGTAG,"Default database id and nothing related there so creating new");
			
				ContentValues values = new ContentValues();
				values.put(DatabaseHelper.ITEM_MEDIA_ITEM_ID, item.getDatabaseId());
				values.put(DatabaseHelper.ITEM_MEDIA_URL, itemMedia.getUrl());
				values.put(DatabaseHelper.ITEM_MEDIA_TYPE, itemMedia.getType());
				values.put(DatabaseHelper.ITEM_MEDIA_MEDIUM, itemMedia.getMedium());
				values.put(DatabaseHelper.ITEM_MEDIA_HEIGHT, itemMedia.getHeight());
				values.put(DatabaseHelper.ITEM_MEDIA_WIDTH, itemMedia.getWidth());
				values.put(DatabaseHelper.ITEM_MEDIA_FILESIZE, itemMedia.getFileSize());
				values.put(DatabaseHelper.ITEM_MEDIA_DURATION, itemMedia.getDuration());
				values.put(DatabaseHelper.ITEM_MEDIA_DEFAULT, itemMedia.getIsDefault());
				values.put(DatabaseHelper.ITEM_MEDIA_EXPRESSION, itemMedia.getExpression());
				values.put(DatabaseHelper.ITEM_MEDIA_BITRATE, itemMedia.getBitrate());
				values.put(DatabaseHelper.ITEM_MEDIA_FRAMERATE, itemMedia.getFramerate());
				values.put(DatabaseHelper.ITEM_MEDIA_LANG, itemMedia.getLang());
				values.put(DatabaseHelper.ITEM_MEDIA_SAMPLE_RATE, itemMedia.getSampligRate());
		
				try
				{
					returnValue = db.insert(DatabaseHelper.ITEM_MEDIA_TABLE, null, values);
					itemMedia.setDatabaseId(returnValue);
					if (LOGGING)
						Log.v(LOGTAG,"Created itemMedia: " + itemMedia.getDatabaseId());
		
					//Log.v(LOGTAG, "Added Item Media Content: " + returnValue + " item id: " + item.getDatabaseId());
				}
				catch (SQLException e)
				{
					e.printStackTrace();
				}
			} else {
				// else, it is already in the database, let's update the database id

				int databaseIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID);
		
				if (queryCursor.moveToFirst())
				{
					long databaseId = queryCursor.getLong(databaseIdColumn);					
					itemMedia.setDatabaseId(databaseId);
					returnValue = databaseId;
					
				} else {
					Log.e(LOGTAG,"Couldn't move to first row");
				}

				queryCursor.close();
				
			}
			
		} else {
			int columnsUpdated = updateItemMedia(itemMedia);
			if (columnsUpdated == 1)
			{
				returnValue = itemMedia.getDatabaseId();
			}		
		}
		return returnValue;
	}
	
	public long addOrUpdateSetting(String key, String value) {
		int returnValue = -1;

		ContentValues values = new ContentValues();
		values.put(DatabaseHelper.SETTINGS_TABLE_KEY, key);
		values.put(DatabaseHelper.SETTINGS_TABLE_VALUE, value);
			
		if (getSettingValue(key) != null) {
			// Update
			int rowsAffected = db.update(DatabaseHelper.SETTINGS_TABLE, values, DatabaseHelper.SETTINGS_TABLE_KEY + "=?", new String[] {key});
			if (rowsAffected == 1) {
				returnValue = 1;
			}
			
			if (LOGGING)
				Log.v(LOGTAG,"update " + key + " " + value + " result:" + returnValue);
		}
		else {
			// Insert
			long id = db.insert(DatabaseHelper.SETTINGS_TABLE, null, values);
			if (id > -1) {
				returnValue = 1;
			}
			
			if (LOGGING)
				Log.v(LOGTAG,"insert " + key + " " + value + " result:" + returnValue);
			
		}
		
		return returnValue;		
	}
	
	public String getSettingValue(String key) {
		
		String returnValue = null;
		Cursor queryCursor = null;
		
		try {
			
			String query = "select " + DatabaseHelper.SETTINGS_TABLE_ID + ", " + DatabaseHelper.SETTINGS_TABLE_KEY + ", "
					+ DatabaseHelper.SETTINGS_TABLE_VALUE + " from " + DatabaseHelper.SETTINGS_TABLE + " where " + DatabaseHelper.SETTINGS_TABLE_KEY + " =? LIMIT 1;";
			
			if (LOGGING)
				Log.v(LOGTAG,query);
			
			queryCursor = db.rawQuery(query, new String[] {key});
			
			int idColumn = queryCursor.getColumnIndex(DatabaseHelper.SETTINGS_TABLE_ID);
			int keyColumn = queryCursor.getColumnIndex(DatabaseHelper.SETTINGS_TABLE_KEY);
			int valueColumn = queryCursor.getColumnIndex(DatabaseHelper.SETTINGS_TABLE_VALUE);
	
			if (queryCursor.moveToFirst())
			{
				long returnId = queryCursor.getLong(idColumn);
				String returnKey = queryCursor.getString(keyColumn);
				returnValue = queryCursor.getString(valueColumn);
				
				if (LOGGING) {
					Log.v(LOGTAG,"returnValue: " + returnValue);
					Log.v(LOGTAG,"returnid: " + returnId);
					Log.v(LOGTAG,"returnKey: " + returnKey);
				}
				
			} else {
				if (LOGGING)
					Log.v(LOGTAG,"Couldn't move to first");
			}
		
			queryCursor.close();		
		} catch (SQLException e) {
			e.printStackTrace();
		} 		
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}

		return returnValue;
	}
	
	public int updateItemMedia(MediaContent itemMedia) {
		int returnValue = -1;
		
		ContentValues values = new ContentValues();
		values.put(DatabaseHelper.ITEM_MEDIA_ITEM_ID, itemMedia.getItemDatabaseId());
		values.put(DatabaseHelper.ITEM_MEDIA_URL, itemMedia.getUrl());
		values.put(DatabaseHelper.ITEM_MEDIA_TYPE, itemMedia.getType());
		values.put(DatabaseHelper.ITEM_MEDIA_MEDIUM, itemMedia.getMedium());
		values.put(DatabaseHelper.ITEM_MEDIA_HEIGHT, itemMedia.getHeight());
		values.put(DatabaseHelper.ITEM_MEDIA_WIDTH, itemMedia.getWidth());
		values.put(DatabaseHelper.ITEM_MEDIA_FILESIZE, itemMedia.getFileSize());
		values.put(DatabaseHelper.ITEM_MEDIA_DURATION, itemMedia.getDuration());
		values.put(DatabaseHelper.ITEM_MEDIA_DEFAULT, itemMedia.getIsDefault());
		values.put(DatabaseHelper.ITEM_MEDIA_EXPRESSION, itemMedia.getExpression());
		values.put(DatabaseHelper.ITEM_MEDIA_BITRATE, itemMedia.getBitrate());
		values.put(DatabaseHelper.ITEM_MEDIA_FRAMERATE, itemMedia.getFramerate());
		values.put(DatabaseHelper.ITEM_MEDIA_LANG, itemMedia.getLang());
		values.put(DatabaseHelper.ITEM_MEDIA_SAMPLE_RATE, itemMedia.getSampligRate());

		try
		{
			returnValue = db.update(DatabaseHelper.ITEM_MEDIA_TABLE, values, DatabaseHelper.ITEM_MEDIA_TABLE_COLUMN_ID + "=?", new String[] { "" + itemMedia.getDatabaseId() });
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}		
		
		return returnValue;
	}
	
	public ArrayList<Item> getFeedItemsWithTag(Feed feed, String tag) {
		ArrayList<Item> items = new ArrayList<Item>();
		
		Cursor queryCursor = null;
		
		try {
			
			String query = "select " + DatabaseHelper.ITEM_TAGS_TABLE_ID + ", " + DatabaseHelper.ITEM_TAG + ", "
					+ DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + " from " + DatabaseHelper.ITEM_TAGS_TABLE + ", " 
					+ DatabaseHelper.ITEMS_TABLE + " where " + DatabaseHelper.ITEM_TAG + " =? and " 
					+ DatabaseHelper.ITEM_TAGS_TABLE_ID + "=" + DatabaseHelper.ITEMS_TABLE_COLUMN_ID 
					+ " and " + DatabaseHelper.ITEMS_TABLE_FEED_ID + " =? order by "
					+ DatabaseHelper.ITEMS_TABLE_PUBLISH_DATE + " DESC;";
			
			if (LOGGING)
				Log.v(LOGTAG,query);
			
			queryCursor = db.rawQuery(query, new String[] {tag, "" + feed.getDatabaseId()});
			
			int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID);
	
			if (queryCursor.moveToFirst())
			{
				do {
					int itemId = queryCursor.getInt(itemIdColumn);
					Item item = this.getItemById(itemId);
					items.add(item);
				} while (queryCursor.moveToNext());
					
			} else {
				if (LOGGING)
					Log.v(LOGTAG,"Couldn't move to first");
			}
		
			queryCursor.close();		
		} catch (SQLException e) {
			e.printStackTrace();
		} 		
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}
		return items;
	}		
	
	public ArrayList<Item> getItemsWithTag(String tag) {
		ArrayList<Item> items = new ArrayList<Item>();
		
		Cursor queryCursor = null;
		
		try {
			
			String query = "select " + DatabaseHelper.ITEM_TAGS_TABLE_ID + ", " + DatabaseHelper.ITEM_TAG + ", "
					+ DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + " from " + DatabaseHelper.ITEM_TAGS_TABLE + " where " + DatabaseHelper.ITEM_TAG + " =?;";
			
			if (LOGGING)
				Log.v(LOGTAG,query);
			
			queryCursor = db.rawQuery(query, new String[] {tag});
			
			int itemIdColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID);
	
			if (queryCursor.moveToFirst())
			{
				do {
					int itemId = queryCursor.getInt(itemIdColumn);
					Item item = this.getItemById(itemId);
					items.add(item);
				} while (queryCursor.moveToNext());
					
			} else {
				if (LOGGING)
					Log.v(LOGTAG,"Couldn't move to first");
			}
		
			queryCursor.close();		
		} catch (SQLException e) {
			e.printStackTrace();
		} 		
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}
		return items;
	}		
	
	public ArrayList<String> getItemTags(Item item) {

		ArrayList<String> itemTags = new ArrayList<String>();
		Cursor queryCursor = null;
		
		try {
			
			String query = "select " + DatabaseHelper.ITEM_TAGS_TABLE_ID + ", " + DatabaseHelper.ITEM_TAG + ", "
					+ DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + " from " + DatabaseHelper.ITEM_TAGS_TABLE + " where " + DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + " =?;";
			
			if (LOGGING)
				Log.v(LOGTAG,query);
			
			queryCursor = db.rawQuery(query, new String[] {"" + item.getDatabaseId()});
			
			int tagColumn = queryCursor.getColumnIndex(DatabaseHelper.ITEM_TAG);
	
			if (queryCursor.moveToFirst())
			{
				do {
					String tag = queryCursor.getString(tagColumn);
					itemTags.add(tag);
					if (LOGGING)
						Log.v(LOGTAG,"tag: " + tag);
				} while (queryCursor.moveToNext());
					
			} else {
				if (LOGGING)
					Log.v(LOGTAG,"Couldn't move to first");
			}
		
			queryCursor.close();		
		} catch (SQLException e) {
			e.printStackTrace();
		} 		
		finally
		{
			if (queryCursor != null)
			{
				try
				{
					queryCursor.close();					
				}
				catch(Exception e) {}
			}
		}
		return itemTags;
	}
	
	public void addOrUpdateItemTags(Item item) {

		deleteItemTags(item.getDatabaseId());
		
		for (String tag : item.getCategories()) {
			try
			{
				ContentValues values = new ContentValues();
				values.put(DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID, item.getDatabaseId());
				values.put(DatabaseHelper.ITEM_TAG, tag);
		
				db.insert(DatabaseHelper.ITEM_TAGS_TABLE, null, values);
			}
			catch (SQLException e)
			{
				e.printStackTrace();
			}		
		}
	}
	
	public void deleteItemTags(long itemDatabaseId) {
		try
		{
			long returnValue = db.delete(DatabaseHelper.ITEM_TAGS_TABLE, DatabaseHelper.ITEM_TAGS_TABLE_ITEM_ID + "=?", new String[] { ""+itemDatabaseId });
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}

	}
	
	public void deleteItemTags(Item item) {
		deleteItemTags(item.getDatabaseId());
	}
	
	
	public void deleteItemMedia(Item item)
	{
		deleteItemMedia(item.getDatabaseId());
	}

	public void deleteItemMedia(long itemId)
	{
		try
		{
			long returnValue = db.delete(DatabaseHelper.ITEM_MEDIA_TABLE, DatabaseHelper.ITEM_MEDIA_ITEM_ID + "=?", new String[] { "" + itemId });
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
	
	public void deleteAll()
	{
		try
		{
			if (!databaseReady())
				open();
			
			db.delete(DatabaseHelper.ITEMS_TABLE, "1", null);
			db.delete(DatabaseHelper.FEEDS_TABLE, "1", null);
			db.delete(DatabaseHelper.ITEM_MEDIA_TABLE, "1", null);
			db.delete(DatabaseHelper.SETTINGS_TABLE, "1", null);
			db.delete(DatabaseHelper.ITEM_TAGS_TABLE, "1", null);
			//db.delete(DatabaseHelper.TAGS_TABLE, "1", null);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
	}
}