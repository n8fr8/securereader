package com.tinymission.rss;

import info.guardianproject.onionkit.trust.StrongHttpsClient;
import info.guardianproject.securereader.SocialReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import android.util.Log;

import ch.boye.httpclientandroidlib.HttpHost;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.conn.params.ConnRoutePNames;

/**
 * Reads an RSS feed and creates and RssFeed object.
 */
public class Reader
{

	public final static String LOGTAG = "TinyRSS Reader";
	public final static boolean LOGGING_ON = false;

	private Feed feed;

	private SocialReader socialReader;

	/**
	 * The allowed tags to parse content from (everything else gets lumped into
	 * its parent content, which allows for embedding html content.
	 * 
	 */
	public final static String[] CONTENT_TAGS = { "title", "link", "language", "pubDate", "lastBuildDate", "docs", "generator", "managingEditor", "webMaster",
			"guid", "author", "category", "content:encoded", "description", "url" };

	/**
	 * The tags that should be parsed into separate entities, not just
	 * properties of existing entities.
	 * 
	 */
	public final static String[] ENTITY_TAGS = { "channel", "item", "media:content", "media:thumbnail", "enclosure", "image" };

	/**
	 * @return whether tag is a valid content tag
	 */
	public static boolean isContentTag(String tag)
	{
		for (String t : CONTENT_TAGS)
		{
			if (t.equalsIgnoreCase(tag))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * @return whether tag is a valid entity tag
	 */
	public static boolean isEntityTag(String tag)
	{
		for (String t : ENTITY_TAGS)
		{
			if (t.equals(tag))
			{
				return true;
			}
		}
		return false;
	}

	// In this case, we preserve the feed object?
	public Reader(SocialReader _socialReader, Feed _feed)
	{
		socialReader = _socialReader;
		_feed.setStatus(Feed.STATUS_SYNC_IN_PROGRESS);
		feed = _feed;
	}

	/**
	 * Actually grabs the feed from the URL and parses it into java objects.
	 * 
	 * @return The feed object containing all the feed data.
	 */
	public Feed fetchFeed()
	{
		try
		{
			SAXParserFactory spf = SAXParserFactory.newInstance();
			spf.setNamespaceAware(true);
			SAXParser sp = spf.newSAXParser();

			XMLReader xr = sp.getXMLReader();
			
			xr.setErrorHandler(new ErrorHandler() { 
				public void error(SAXParseException exception) throws SAXException {
					Log.v(LOGTAG, "ErrorHandler: SAXParseException error: " + exception.getMessage()); 
					exception.printStackTrace(); 
				}
			  
				public void fatalError(SAXParseException exception) throws SAXException { 
					Log.v(LOGTAG, "ErrorHandler: SAXParseException fatalError: " + exception.getMessage()); 
					exception.printStackTrace(); 
				}
			  
				public void warning(SAXParseException exception) throws SAXException { 
					Log.v(LOGTAG, "ErrorHandler: SAXParseException warning: " + exception.getMessage());
					exception.printStackTrace(); 
				} 
			});
			

			Handler handler = new Handler();
			xr.setContentHandler(handler);

			if (LOGGING_ON)	
				Log.v(LOGTAG, "Feed Link: " + feed.getFeedURL());

			// URLConnection ucon = new URL(feed.getLink()).openConnection();
			// InputStream is = ucon.getInputStream();

			HttpClient httpClient = new StrongHttpsClient(socialReader.applicationContext);
			if (socialReader.useTor())
			{
				httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(SocialReader.PROXY_HOST, SocialReader.PROXY_HTTP_PORT));
			}

			if (feed.getFeedURL() != null && !(feed.getFeedURL().isEmpty()))
			{
				HttpGet httpGet = new HttpGet(feed.getFeedURL());

				HttpResponse response = httpClient.execute(httpGet);

				/*
				 * StringBuffer sb = new StringBuffer();
				 * sb.append(response.getStatusLine()).append("\n\n");
				 */

				if (response.getStatusLine().getStatusCode() == 200) {
					Log.v(LOGTAG,"Response Code is good");
					
					InputStream is = response.getEntity().getContent();
					xr.parse(new InputSource(is));
					
					is.close();

					Date currentDate = new Date();
					if (LOGGING_ON)	
						Log.v(LOGTAG, "Setting Feed Network Pull Date: " + currentDate.toString());
					feed.setNetworkPullDate(currentDate);
					if (LOGGING_ON)	
						Log.v(LOGTAG, "Feed has Pull date: " + feed.getNetworkPullDate().toString());
					feed.setStatus(Feed.STATUS_LAST_SYNC_GOOD);
					
				} else {
					Log.v(LOGTAG,"Response Code: " + response.getStatusLine().getStatusCode());
					if (response.getStatusLine().getStatusCode() == 404) {
						feed.setStatus(Feed.STATUS_LAST_SYNC_FAILED_404);
					} else {
						feed.setStatus(Feed.STATUS_LAST_SYNC_FAILED_UNKNOWN);
					}
				}
			} else {
				feed.setStatus(Feed.STATUS_LAST_SYNC_FAILED_BAD_URL);
			}
		}
		catch (ParserConfigurationException pce)
		{
			Log.e("SAX XML", "sax parse error", pce);
			feed.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);

		}
		catch (SAXException se)
		{
			Log.e("SAX XML", "sax error", se);
			feed.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);

		}
		catch (IOException ioe)
		{
			Log.e("SAX XML", "sax parse io error", ioe);
			feed.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);

		}
		catch (IllegalStateException ise)
		{
			ise.printStackTrace();
			feed.setStatus(Feed.STATUS_LAST_SYNC_PARSE_ERROR);
		}
		return feed;
	}

	/**
	 * SAX handler for parsing RSS feeds.
	 * 
	 */
	public class Handler extends DefaultHandler
	{

		// Keep track of which entity we're currently assigning properties to
		private final Stack<FeedEntity> _entityStack;

		public Handler()
		{
			_entityStack = new Stack<FeedEntity>();
			_entityStack.add(feed);
		}

		private StringBuilder _contentBuilder;

		@Override
		public void startDocument() throws SAXException
		{
			_contentBuilder = new StringBuilder();
		}

		@Override
		public void endDocument() throws SAXException
		{
			super.endDocument();
		}

		@Override
		public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
		{
			if (isContentTag(localName))
			{
				_contentBuilder = new StringBuilder();
			}
			// else if (isMainContentTag(localName))
			// {
			// _contentBuilder = new StringBuilder();
			// }
			else if (isEntityTag(qName))
			{
				if (qName.equals("item"))
				{
					Item item = new Item(attributes);
					_entityStack.add(item);
					feed.addItem(item);
				}
				else if (qName.equals("enclosure"))
				{
					MediaContent mediaContent = new MediaContent(attributes);
					FeedEntity lastEntity = _entityStack.lastElement();
					if (lastEntity.getClass() == Item.class)
					{
						((Item) lastEntity).setMediaContent(mediaContent);
					}
					_entityStack.add(mediaContent);
				}
				else if (qName.equals("media:content"))
				{
					MediaContent mediaContent = new MediaContent(attributes);
					FeedEntity lastEntity = _entityStack.lastElement();
					if (lastEntity.getClass() == Item.class)
					{
						((Item) lastEntity).setMediaContent(mediaContent);
					}
					_entityStack.add(mediaContent);
				}
				else if (qName.equals("media:thumbnail"))
				{
					MediaThumbnail mediaThumbnail = new MediaThumbnail(attributes);
					FeedEntity lastEntity = _entityStack.lastElement();
					if (lastEntity.getClass() == Item.class)
					{
						Item item = (Item) lastEntity;
						MediaThumbnail existingMt = item.getMediaThumbnail();
						if (existingMt == null)
						{
							item.setMediaThumbnail(mediaThumbnail);
						}
					}
					_entityStack.add(mediaThumbnail);
				}
				else if (qName.equals("channel"))
				{
					// this is just the start of the feed
				}
				else if (qName.equals("image"))
				{
					MediaContent mediaContent = new MediaContent(attributes);
										
					FeedEntity lastEntity = _entityStack.lastElement();
					if (lastEntity.getClass() == Feed.class)
					{						
						((Feed) lastEntity).setMediaContent(mediaContent);
					}					
					_entityStack.add(mediaContent);
				}
				else
				{
					throw new RuntimeException("Don't know how to create an entity from tag " + qName);
				}
			}
		}

		@Override
		public void endElement(String uri, String localName, String qName) throws SAXException
		{
			if (LOGGING_ON)	
				Log.v(LOGTAG, "Ending " + localName + " " + qName);
			// get the latest parsed content, if there is any
			String content = "";
			if (isContentTag(qName))
			{
				content = _contentBuilder.toString().trim();
				if (qName.equalsIgnoreCase("content:encoded"))
				{
					if (LOGGING_ON)	
						Log.v(LOGTAG, "We Have content:encoded!");
					qName = "contentEncoded";
				}
				if (LOGGING_ON)	
					Log.v(LOGTAG, "Content " + content);
				_entityStack.lastElement().setProperty(qName, content);
			}
			else if (isEntityTag(qName))
			{
				_entityStack.pop();
			}
		}

		@Override
		public void characters(char[] ch, int start, int length) throws SAXException
		{
			String content = new String(ch, start, length);
			_contentBuilder.append(content);
		}
	}
}
