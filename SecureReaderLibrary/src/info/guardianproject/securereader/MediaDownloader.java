package info.guardianproject.securereader;

import info.guardianproject.onionkit.trust.StrongHttpsClient;
import info.guardianproject.iocipher.File;
import info.guardianproject.iocipher.FileOutputStream;
import info.guardianproject.iocipher.FileInputStream;

import java.io.BufferedOutputStream;
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpHost;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.client.ClientProtocolException;
import ch.boye.httpclientandroidlib.client.HttpClient;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.conn.params.ConnRoutePNames;

import com.tinymission.rss.MediaContent;

public class MediaDownloader extends AsyncTask<MediaContent, Integer, File>
{

	public final static String LOGTAG = "MediaDownloader";
	
	SocialReader socialReader;
	MediaDownloaderCallback callback;
	
	public MediaDownloader(SocialReader _socialReader)
	{
		super();
		socialReader = _socialReader;
	}

	public interface MediaDownloaderCallback
	{
		public void mediaDownloaded(File mediaFile);
		public void mediaDownloadedNonVFS(java.io.File mediaFile);		
	}

	public void setMediaDownloaderCallback(MediaDownloaderCallback mdc)
	{
		callback = mdc;
	}

	private void copyFileFromFStoAppFS(java.io.File src, info.guardianproject.iocipher.File dst) throws IOException
	{
		InputStream in = new java.io.FileInputStream(src);
		OutputStream out = new info.guardianproject.iocipher.FileOutputStream(dst);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0)
		{
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}	

	@Override
	protected File doInBackground(MediaContent... params)
	{
		Log.v(LOGTAG, "MediaDownloader: doInBackground");

		File savedFile = null;
		java.io.File nonVFSSavedFile = null;
		
		InputStream inputStream = null;

		if (params.length == 0)
			return null;

		MediaContent mediaContent = params[0];
		HttpClient httpClient = new StrongHttpsClient(socialReader.applicationContext);

		if (socialReader.useTor())
		{
			httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(SocialReader.PROXY_HOST, SocialReader.PROXY_HTTP_PORT));
			Log.v(LOGTAG, "MediaDownloader: USE_TOR");
		}

		if (mediaContent.getUrl() != null && !(mediaContent.getUrl().isEmpty()))
		{
			try
			{
				Log.v(LOGTAG,"URL is: " + mediaContent.getUrl());
				Uri uriMedia = Uri.parse(mediaContent.getUrl());
				if (uriMedia != null && ContentResolver.SCHEME_CONTENT.equals(uriMedia.getScheme()))
				{
					BufferedOutputStream bos = null;
					
					savedFile = new File(socialReader.getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
					bos = new BufferedOutputStream(new FileOutputStream(savedFile));
					
					inputStream = socialReader.applicationContext.getContentResolver().openInputStream(uriMedia);

					byte data[] = new byte[1024];
					int count;
					while ((count = inputStream.read(data)) != -1)
					{
						bos.write(data, 0, count);
					}
					inputStream.close();
					bos.close();

					socialReader.getStoreBitmapDimensions(mediaContent);
					return savedFile;
				}

				if (mediaContent.getUrl().startsWith("file:///"))
				{
					Log.v(LOGTAG, "Have a file:/// url, we probably don't need to do anything but let's check");

					savedFile = new File(socialReader.getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());
					Log.v(LOGTAG, "Does " + socialReader.getFileSystemDir() + SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId() + " exist?");
					
					if (!savedFile.exists()) {
						Log.v(LOGTAG, "Saved File Doesn't Exist");
						
						URI existingFileUri = new URI(mediaContent.getUrl());
						java.io.File existingFile = new java.io.File(existingFileUri);
						copyFileFromFStoAppFS(existingFile, savedFile);
					}
					Log.v(LOGTAG, "Copy should have worked: " + savedFile.getAbsolutePath());
					socialReader.getStoreBitmapDimensions(mediaContent);
					return savedFile;
				}

				HttpGet httpGet = new HttpGet(mediaContent.getUrl());
				HttpResponse response = httpClient.execute(httpGet);

				int statusCode = response.getStatusLine().getStatusCode();
				if (statusCode != HttpStatus.SC_OK)
				{
					Log.w(LOGTAG, "Error " + statusCode + " while retrieving file from " + mediaContent.getUrl());
					return null;
				}

				HttpEntity entity = response.getEntity();
				if (entity == null)
				{
					Log.v(LOGTAG, "MediaDownloader: no response");

					return null;
				}

				Log.v(LOGTAG, "MediaDownloader: " + mediaContent.getType().toString());

				savedFile = new File(socialReader.getFileSystemDir(), SocialReader.MEDIA_CONTENT_FILE_PREFIX + mediaContent.getDatabaseId());

				BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(savedFile));
				inputStream = entity.getContent();
				long size = entity.getContentLength();

				byte data[] = new byte[1024];
				int count;
				long total = 0;
				while ((count = inputStream.read(data)) != -1)
				{
					total += count;
					bos.write(data, 0, count);
					publishProgress((int) (total / size * 100));
				}

				inputStream.close();
				bos.close();
				entity.consumeContent();

				socialReader.getStoreBitmapDimensions(mediaContent);
			}
			catch (ClientProtocolException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (URISyntaxException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return savedFile;
	}

	@Override
	protected void onProgressUpdate(Integer... progress)
	{
		// Log.v(LOGTAG, progress[0].toString());
	}

	@Override
	protected void onPostExecute(File cachedFile)
	{
		if (callback != null)
		{
			callback.mediaDownloaded(cachedFile);
		}
	}
}
