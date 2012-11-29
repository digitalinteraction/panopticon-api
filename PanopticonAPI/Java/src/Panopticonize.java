import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;

import com.google.gson.Gson;

public class Panopticonize {
	public enum FileStatus {
		UPLOADING, INQUEUE, PROCESSING, DOWNLOADING, DONE
	};

	public final static String SERVER = "http://panopticonize.com/";
	private String APIKey;
	
	public String getAPIKey() {
		return APIKey;
	}

	public Panopticonize(String apikey) {
		APIKey = apikey;
	}

	public File UploadForProcessing(String filename, String destination) {
		Panopticonize.File f = new Panopticonize.File(filename, destination);
		f.Upload(APIKey);
		return f;
	}

	public File UploadForProcessing(String filename) {
		return UploadForProcessing(filename, filename + ".panop.mp4");
	}

	public interface IFileEventsListener {
		void OnProcessProgressChanged(int i);
		void OnUploadProgressChanged(int i);
		void OnStatusChanged(FileStatus i);
		void OnPlaceInQueueChanged(int i, int l);
	}

	private class Uploader extends Thread {
		File f;

		public Uploader(File f) {
			this.f = f;
		}

		@Override
		public void run() {
			super.run();
			HttpClient httpClient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(Panopticonize.SERVER
					+ "/api.php?key=" + f.apikey + "&a=upload");
			FileBody bin = new FileBody(new java.io.File(f.LocalFilename));
			MultipartEntityWithProgressBar reqEntity = new MultipartEntityWithProgressBar(
					new Panopticonize.UploadListener(bin.getContentLength(), f));
			reqEntity.addPart("files", bin);
			httppost.setEntity(reqEntity);
			try {
				HttpResponse uploadResponse = httpClient.execute(httppost);
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(uploadResponse.getEntity()
								.getContent()));
				String ss = null;
				String tt = "";
				while ((ss = reader.readLine()) != null)
					tt += ss;

				Hashtable ht = f.gson.fromJson(tt, Hashtable.class);
				if (ht.containsKey("code")) {
					throw new PanopticonizeException(Integer.parseInt(ht.get(
							"code").toString()), ht.get("details").toString());
				}
				// if its not an error, then process...
				f.servername = ht.get("id").toString();
				// when upload complete
				f.Status = FileStatus.INQUEUE;
				f.StartGetProgress();

			} catch (Exception e) {
				// do nothing
			}

		}

	}

	public class File {
		private IFileEventsListener list;
		private int Progress = 0;
		private FileStatus Status = FileStatus.UPLOADING;
		private int PlaceInQueue = 0;
		private int QueueLength = 0;
		private String LocalProcessedFilename;
		private String ConvertedFile;
		private boolean done = false;
		private String servername;
		private String apikey;
		private Timer t = new Timer();
		private String LocalFilename;
		private Gson gson = new Gson();

		public void AddListener(IFileEventsListener listener) {
			this.list = listener;
		}

		public String getLocalFilename() {
			return LocalFilename;
		}

		public int getProgress() {
			return Progress;
		}

		public FileStatus getStatus() {
			return Status;
		}

		public int getPlaceInQueue() {
			return PlaceInQueue;
		}

		public int getQueueLength() {
			return QueueLength;
		}

		public String getLocalProcessedFilename() {
			return LocalProcessedFilename;
		}

		public String getConvertedFile() {
			return ConvertedFile;
		}

		protected File(String filename, String destfilename) {
			LocalFilename = filename;
			LocalProcessedFilename = destfilename;
		}

		protected void Upload(String apikey) {
			this.apikey = apikey;
			Status = FileStatus.UPLOADING;
			(new Uploader(this)).start();
		}

		protected void StartGetProgress() {
			t.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					// get progress
					try {
						// get the data...
						DefaultHttpClient httpclient = new DefaultHttpClient();
						HttpGet httpGet = new HttpGet(Panopticonize.SERVER
								+ "api.php?key=" + APIKey + "&a=progress&f="
								+ servername);
						HttpResponse response1 = httpclient.execute(httpGet);
						BufferedReader reader = new BufferedReader(
								new InputStreamReader(response1.getEntity()
										.getContent()));
						// URL url = new URL("http://stackoverflow.com/");
						// new InputStreamReader(url.openStream()));
						String ss = null;
						String tt = "";
						while ((ss = reader.readLine()) != null)
							tt += ss;

						Hashtable ht = gson.fromJson(tt, Hashtable.class);
						if (ht.containsKey("code")) {
							throw new PanopticonizeException(Integer
									.parseInt(ht.get("code").toString()), ht
									.get("details").toString());
						}

						ServerData sd = gson.fromJson(tt, ServerData.class);

						if (sd.progress != Progress) {
							Progress = sd.progress;
							list.OnProcessProgressChanged(sd.progress);

							if (Status != FileStatus.PROCESSING) {
								Status = FileStatus.PROCESSING;
								list.OnStatusChanged(FileStatus.PROCESSING);
							}
						}

						if (sd.done == true && sd.link != "" && !done) {
							done = true;
							t.cancel();
							ConvertedFile = sd.link;
							Status = FileStatus.DOWNLOADING;
							list.OnStatusChanged(FileStatus.DOWNLOADING);

							// download
							Content r = Request
									.Get(Panopticonize.SERVER + "/" + sd.link)
									.execute().returnContent();
							FileOutputStream s = new FileOutputStream(
									LocalProcessedFilename);

							s.write(r.asBytes());
							s.close();
							Status = FileStatus.DONE;
							list.OnStatusChanged(FileStatus.DONE);

						}

						if (sd.q != PlaceInQueue) {
							PlaceInQueue = sd.q;
							QueueLength = sd.qtotal;
							list.OnPlaceInQueueChanged(sd.q, sd.qtotal);
						}
					} catch (Exception ex) {
						// do something here...

					}
				}
			}, 0, 1000);
		}

	}

	@SuppressWarnings("serial")
	class PanopticonizeException extends Exception {
		public int ErrorCode;
		
		public PanopticonizeException(int code, String message) {
			this.ErrorCode = code;
		}		
	}

	class ServerData {
		public String file = "";
		public int progress = 0;
		public boolean done = false;
		public int q = 0;
		public int qtotal = 0;
		public String error = "";
		public String link = "";
	}

	public class UploadListener implements Panopticonize.WriteListener {
		long total;
		Panopticonize.File file;

		public UploadListener(long total, Panopticonize.File file) {
			this.total = total;
			this.file = file;
		}

		@Override
		public void registerWrite(long amountOfBytesWritten) {
			file.list
					.OnUploadProgressChanged((int) ((amountOfBytesWritten / (double) total) * 100));
		}

	}

	public interface WriteListener {
		void registerWrite(long amountOfBytesWritten);
	}

	public class OutputStreamProgress extends OutputStream {

		private final OutputStream outstream;
		private volatile long bytesWritten = 0;
		private final WriteListener writeListener;

		public OutputStreamProgress(OutputStream outstream,
				WriteListener writeListener) {
			this.outstream = outstream;
			this.writeListener = writeListener;
		}

		@Override
		public void write(int b) throws IOException {
			outstream.write(b);
			bytesWritten++;
			writeListener.registerWrite(bytesWritten);
		}

		@Override
		public void write(byte[] b) throws IOException {
			outstream.write(b);
			bytesWritten += b.length;
			writeListener.registerWrite(bytesWritten);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			outstream.write(b, off, len);
			bytesWritten += len;
			writeListener.registerWrite(bytesWritten);
		}

		@Override
		public void flush() throws IOException {
			outstream.flush();
		}

		@Override
		public void close() throws IOException {
			outstream.close();
		}
	}

	public class MultipartEntityWithProgressBar extends MultipartEntity {
		private OutputStreamProgress outstream;
		private WriteListener writeListener;

		@Override
		public void writeTo(OutputStream outstream) throws IOException {
			this.outstream = new OutputStreamProgress(outstream, writeListener);
			super.writeTo(this.outstream);
		}

		public MultipartEntityWithProgressBar(WriteListener writeListener) {
			super();
			this.writeListener = writeListener;
		}

		public MultipartEntityWithProgressBar(HttpMultipartMode mode,
				WriteListener writeListener) {
			super(mode);
			this.writeListener = writeListener;
		}

		public MultipartEntityWithProgressBar(HttpMultipartMode mode,
				String boundary, Charset charset, WriteListener writeListener) {
			super(mode, boundary, charset);
			this.writeListener = writeListener;
		}
	}
}