using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.IO;
using System.Threading;

namespace Panopticonize
{
    class Program
    {
        static void Main(string[] args)
        {
            Panopticonize panopticon = new Panopticonize("6vbqupuahctdyhu");
            var f = panopticon.UploadForProcessing(Directory.GetCurrentDirectory()+"\\test.mp4");
            f.OnPlaceInQueueChanged += new Action<int,int>(f_OnPlaceInQueueChanged);
            f.OnProcessProgressChanged += new Action<int>(f_OnProgressChanged);
            f.OnUploadProgressChanged += new Action<int>(f_OnUploadProgressChanged);
            f.OnStatusChanged += new Action<FileStatus>(f_OnStatusChanged);
            Console.WriteLine("Uploading");
            while (true)
            {
                Thread.Sleep(200);
            }
        }

        static void f_OnUploadProgressChanged(int obj)
        {
            Console.WriteLine("Upload: " + obj);
        }

        static void f_OnStatusChanged(FileStatus obj)
        {
            Console.WriteLine("Status: " + obj);
        }

        static void f_OnProgressChanged(int obj)
        {
            Console.WriteLine("Progress: " + obj);
        }

        static void f_OnPlaceInQueueChanged(int obj,int obj2)
        {
            Console.WriteLine("In Queue: " + obj + " of " + obj2);
        }
    }
}
