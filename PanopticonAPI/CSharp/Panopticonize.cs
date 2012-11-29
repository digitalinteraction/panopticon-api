using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Timers;
using System.Web.Script.Serialization;
using System.Net;
using System.Collections.Specialized;
using System.IO;
using System.Threading;
using System.Collections;

namespace Panopticonize
{
    public enum FileStatus {UPLOADING, INQUEUE, PROCESSING, DOWNLOADING, DONE};

    public class PanopticonizeException : Exception
    {
        public PanopticonizeException(int code, string message):base(message)
        {
            this.ErrorCode = code;
        }
        public int ErrorCode { get; internal set; }
    }

    public class File
    {
        public string LocalFilename { get; internal set; }
        public int Progress { get; internal set; }
        public FileStatus Status { get; internal set; }
        public int PlaceInQueue { get; internal set; }
        public int QueueLength { get; internal set; }
        public string LocalProcessedFilename { get; internal set; }
        public event Action<int> OnProcessProgressChanged;
        public event Action<int> OnUploadProgressChanged;
        public event Action<FileStatus> OnStatusChanged;
        public event Action<int,int> OnPlaceInQueueChanged;
        public string ConvertedFile { get; internal set; }

        private bool done = false;

        private string servername;
        private string apikey;

        internal File(string filename,string destfilename)
        {
            LocalFilename = filename;
            LocalProcessedFilename = destfilename;
        }
        System.Timers.Timer t = new System.Timers.Timer();
        internal void Upload(string apikey)
        {
            this.apikey = apikey;
            Status = FileStatus.UPLOADING;
            Thread thread = new Thread(new ThreadStart(() => {
                Uploader u = new Uploader();
                u.OnUploadProgress += new Action<int>(u_OnUploadProgress);
                try
                {
                    string s = u.UploadFileEx(LocalFilename, Panopticonize.SERVER + "/api.php?key=" + apikey + "&a=upload&", "files", "video/mp4", null, new CookieContainer());
                    Hashtable sdd = (Hashtable)ser.Deserialize<Hashtable>(s);
                    if (sdd.ContainsKey("code"))
                    {
                        throw new PanopticonizeException(Int32.Parse(sdd["code"].ToString()), sdd["details"].ToString());
                    }
                    servername = sdd["id"].ToString();
                    //when upload complete
                    Status = FileStatus.INQUEUE;
                    if (OnStatusChanged != null)
                        OnStatusChanged(FileStatus.INQUEUE);

                    t.Interval = TimeSpan.FromSeconds(1).TotalMilliseconds;
                    t.Elapsed += new ElapsedEventHandler(t_Elapsed);
                    t.Start();
                    web.DownloadStringCompleted += new DownloadStringCompletedEventHandler(web_DownloadStringCompleted);
                    web.DownloadFileCompleted += new System.ComponentModel.AsyncCompletedEventHandler(web_DownloadFileCompleted);
                    t_Elapsed(null, null);
                }
                catch (Exception e)
                {
                    throw e;
                }                
            }));
            thread.Start();            
        }

        void web_DownloadFileCompleted(object sender, System.ComponentModel.AsyncCompletedEventArgs e)
        {
            Status = FileStatus.DONE;
            if (OnStatusChanged != null)
                OnStatusChanged(FileStatus.DONE);
        }

        void u_OnUploadProgress(int obj)
        {
            if (OnUploadProgressChanged != null)
            {
                OnUploadProgressChanged(obj);
            }
        }
        
        void web_DownloadStringCompleted(object sender, DownloadStringCompletedEventArgs e)
        {
            try
            {
                ServerData sd = ser.Deserialize<ServerData>(e.Result);
                if (sd.progress != Progress)
                {
                    Progress = sd.progress;
                    if (OnProcessProgressChanged != null)
                    {
                        OnProcessProgressChanged(sd.progress);
                    }
                    if (Status != FileStatus.PROCESSING)
                    {
                        Status = FileStatus.PROCESSING;
                        if (OnStatusChanged != null)
                            OnStatusChanged(FileStatus.PROCESSING);
                    }
                }

                if (sd.done == true && sd.link != "" && !done)
                {
                    done = true;
                    t.Stop();
                    ConvertedFile = sd.link;
                    Status = FileStatus.DOWNLOADING;
                    if (OnStatusChanged != null)
                        OnStatusChanged(FileStatus.DOWNLOADING);

                    //download
                    web.DownloadFileAsync(new Uri(Panopticonize.SERVER + "/" + sd.link), LocalProcessedFilename);
                }

                if (sd.q != PlaceInQueue)
                {
                    PlaceInQueue = sd.q;
                    QueueLength = sd.qtotal;
                    if (OnPlaceInQueueChanged != null)
                    {
                        OnPlaceInQueueChanged(sd.q,sd.qtotal);
                    }
                }
            }
            catch (Exception ex)
            {
                //do something here...
            }
        }

        JavaScriptSerializer ser = new JavaScriptSerializer();
        WebClient web = new WebClient();

        void t_Elapsed(object sender, ElapsedEventArgs e)
        {
            //get status...
            web.DownloadStringAsync(new Uri(Panopticonize.SERVER+"/api.php?key="+apikey+"&a=progress&f="+servername));
        }
    }

    class ServerData
    {
        public string file="";
	    public int progress=0;
	    public bool done = false;
	    public int q = 0;
	    public int qtotal = 0;
	    public string error = "";
	    public string link = "";
    }

    class Uploader
    {
        public event Action<int> OnUploadProgress;

        internal string UploadFileEx(string uploadfile, string url, string fileFormName, string contenttype, NameValueCollection querystring, CookieContainer cookies)
        {
            if ((fileFormName == null) ||
                (fileFormName.Length == 0))
            {
                fileFormName = "file";
            }

            if ((contenttype == null) ||
                (contenttype.Length == 0))
            {
                contenttype = "application/octet-stream";
            }


            string postdata;
            postdata = "?";
            Uri uri;
            if (querystring != null)
            {
                foreach (string key in querystring.Keys)
                {
                    postdata += key + "=" + querystring.Get(key) + "&";
                }
                uri = new Uri(url + postdata);
            }
            else
            {
                uri = new Uri(url);
            }



            string boundary = "----------" + DateTime.Now.Ticks.ToString("x");
            HttpWebRequest webrequest = (HttpWebRequest)WebRequest.Create(uri);
            webrequest.CookieContainer = cookies;
            webrequest.ContentType = "multipart/form-data; boundary=" + boundary;
            webrequest.Method = "POST";


            // Build up the post message header
            StringBuilder sb = new StringBuilder();
            sb.Append("--");
            sb.Append(boundary);
            sb.Append("\r\n");
            sb.Append("Content-Disposition: form-data; name=\"");
            sb.Append(fileFormName);
            sb.Append("\"; filename=\"");
            sb.Append(Path.GetFileName(uploadfile));
            sb.Append("\"");
            sb.Append("\r\n");
            sb.Append("Content-Type: ");
            sb.Append(contenttype);
            sb.Append("\r\n");
            sb.Append("\r\n");

            string postHeader = sb.ToString();
            byte[] postHeaderBytes = Encoding.UTF8.GetBytes(postHeader);

            // Build the trailing boundary string as a byte array
            // ensuring the boundary appears on a line by itself
            byte[] boundaryBytes = Encoding.ASCII.GetBytes("\r\n--" + boundary + "\r\n");

            FileStream fileStream = new FileStream(uploadfile, FileMode.Open, FileAccess.Read);
            long length = postHeaderBytes.Length + fileStream.Length + boundaryBytes.Length;
            webrequest.ContentLength = length;

            Stream requestStream = webrequest.GetRequestStream();

            // Write out our post header
            requestStream.Write(postHeaderBytes, 0, postHeaderBytes.Length);

            // Write out the file contents
            byte[] buffer = new Byte[checked((uint)Math.Min(4096, (int)fileStream.Length))];
            int bytesRead = 0;

            int total = 0;
            int max = (int)fileStream.Length;

            while ((bytesRead = fileStream.Read(buffer, 0, buffer.Length)) != 0)
            {
                requestStream.Write(buffer, 0, bytesRead);
                total += bytesRead;
                if (OnUploadProgress != null)
                    OnUploadProgress((int)((total / (double)length) * 100));
            }

            // Write out the trailing boundary
            requestStream.Write(boundaryBytes, 0, boundaryBytes.Length);
            WebResponse responce = webrequest.GetResponse();
            Stream s = responce.GetResponseStream();
            StreamReader sr = new StreamReader(s);

            return sr.ReadToEnd();
        }
    }

    public class Panopticonize
    {
        internal const string SERVER = "http://panopticonize.com/";

        public string APIKey { get; private set; }
        public Panopticonize(string apikey)
        {
            APIKey = apikey;
        }

        public File UploadForProcessing(string filename,string destination)
        {
            File f = new File(filename,destination);
            f.Upload(APIKey);
            return f;
        }

        public File UploadForProcessing(string filename)
        {
            return UploadForProcessing(filename, filename + ".panop.mp4");
        }
    }



}
