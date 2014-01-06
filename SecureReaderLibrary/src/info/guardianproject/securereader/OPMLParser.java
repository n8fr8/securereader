package info.guardianproject.securereader;

import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileInputStream;
import info.guardianproject.onionkit.trust.StrongHttpsClient;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import ch.boye.httpclientandroidlib.HttpHost;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.conn.params.ConnRoutePNames;

public class OPMLParser {

	public static final String OUTLINE_ELEMENT = "outline";
	public static final String TEXT_ATTRIBUTE = "text";
	public static final String HTMLURL_ATTRIBUTE = "htmlUrl";
	public static final String XMLURL_ATTRIBUTE = "xmlUrl";
	
	private static final String LOGTAG = "OPMLPARSER";
	
	public class OPMLOutline {
		public String text = "";
		public String htmlUrl = "";
		public String xmlUrl = "";
	}
	
	ArrayList<OPMLOutline> outlines;
	
	OPMLOutline currentOutline;
	
	public interface OPMLParserListener {
		public void opmlParsed(ArrayList<OPMLOutline> outlines);
	}

	public OPMLParserListener opmlParserListener;
	
	public SocialReader socialReader;
	
	public void setOPMLParserListener(OPMLParserListener listener) {
		opmlParserListener = listener;
	}
	
	public OPMLParser(final SocialReader socialReader, final String urlToParse, final OPMLParserListener listener) {
		setOPMLParserListener(listener);

		AsyncTask<Void, Void, Void> asyncTask = new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... params)
			{
				HttpClient httpClient = new StrongHttpsClient(socialReader.applicationContext);
				if (socialReader.useTor())
				{
					Log.v(LOGTAG,"Using Tor for OPML Retrieval");
					httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(SocialReader.PROXY_HOST, SocialReader.PROXY_HTTP_PORT));
				} else {
					Log.v(LOGTAG,"NOT Using Tor for OPML Retrieval");
				}
		
				HttpGet httpGet = new HttpGet(urlToParse);
				HttpResponse response;
				try {
					response = httpClient.execute(httpGet);
				
					if (response.getStatusLine().getStatusCode() == 200) {
						Log.v(LOGTAG,"Response Code is good for OPML feed");
						
						InputStream	is = response.getEntity().getContent();
						parse(is);
						is.close();
					}
				} catch (IllegalStateException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				return null;
			}

			@Override
			protected void onPostExecute(Void nothing)
			{
				Log.v(LOGTAG, "Should be calling opmlParsed on opmlParserListener");
				if (opmlParserListener != null) {
					Log.v(LOGTAG, "Actually calling opmlParsed on opmlParserListener");
					opmlParserListener.opmlParsed(outlines);
				}
			}
		};
		
		asyncTask.execute();
	}
	
	public OPMLParser(InputStream streamToParse, OPMLParserListener listener) {
		setOPMLParserListener(listener);
		parse(streamToParse);
		
		if (opmlParserListener != null) {
    		opmlParserListener.opmlParsed(outlines);
    	}		
	}
	
	public OPMLParser(File fileToParse, OPMLParserListener listener) {
		try {
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(fileToParse));			
			parse(bis);
			bis.close();	
			
			if (opmlParserListener != null) {
	    		opmlParserListener.opmlParsed(outlines);
	    	}			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void parse(InputStream streamToParse) {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser saxParser = factory.newSAXParser();
			OPMLParserHandler handler = new OPMLParserHandler();
			
			saxParser.parse(streamToParse, handler);			
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public class OPMLParserHandler extends DefaultHandler {
		
	    public void startDocument() throws SAXException {
	    	outlines = new ArrayList<OPMLOutline>();
	    	Log.v(LOGTAG,"startDocument");
	    }
	
	    public void endDocument() throws SAXException {
	    	/*if (opmlParserListener != null) {
	    		opmlParserListener.opmlParsed(outlines);
	    	}*/
	    	
        	Log.v(LOGTAG,"endDocument");

	    }
	
	    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
	        if (qName.equalsIgnoreCase(OUTLINE_ELEMENT)) {
	        	currentOutline = new OPMLOutline();
	        	currentOutline.text = atts.getValue(TEXT_ATTRIBUTE);
	        	currentOutline.htmlUrl = atts.getValue(HTMLURL_ATTRIBUTE);
	        	currentOutline.xmlUrl = atts.getValue(XMLURL_ATTRIBUTE);
	        	
	        	Log.v(LOGTAG,"startElement OUTLINE_ELEMENT");
	        }
	    }
	
	    public void endElement(String uri, String localName, String qName) throws SAXException {
	        if (qName.equalsIgnoreCase(OUTLINE_ELEMENT)) {
	        	outlines.add(currentOutline);
	        	
	        	Log.v(LOGTAG,"endElement OUTLINE_ELEMENT");
	        }
	        
	    }
	}
}