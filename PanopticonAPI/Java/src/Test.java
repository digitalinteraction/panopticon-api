class Test
{
		
 public static void main(String[] args)
 {
	  Panopticonize panopticon = new Panopticonize("--api key here--");
	  Panopticonize.File f = panopticon.UploadForProcessing("-- file here --");
	  f.AddListener(new Panopticonize.IFileEventsListener() {
		
		@Override
		public void OnUploadProgressChanged(int i) {
			// TODO Auto-generated method stub
			 System.out.println("Upload: " + i);
		}
		
		@Override
		public void OnStatusChanged(Panopticonize.FileStatus i) {
			// TODO Auto-generated method stub
			System.out.println("Status: " + i);
		}
		
		@Override
		public void OnProcessProgressChanged(int i) {
			// TODO Auto-generated method stub
			System.out.println("Progress: " + i);
		}
		
		@Override
		public void OnPlaceInQueueChanged(int i, int l) {
			// TODO Auto-generated method stub
			System.out.println("In Queue: " + i + " of " + l);
		}

		
	});
	  
	  System.out.println("Uploading");
	  while (true)
	  {
		 try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	  }
 }
}