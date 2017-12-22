package com.virjar.xposedhooktool.tool.socket;

import com.virjar.xposedhooktool.tool.log.LogUtil;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class OutPrinter {
	private DateFormat df = new SimpleDateFormat("yyyy-dd-MM HH_mm_ss.SSS");
	
	public final static boolean PRINT_STACK = true;

	private String addr;
	private int remotePort;
	private int localPort;
	
	private FormatOuter formatOuter;
	private FormatOuter defaultFormatOuter;
	
	public OutPrinter(String addr, int remotePort, int localPort) {
		super();
		this.addr = addr;
		this.remotePort = remotePort;
		this.localPort = localPort;
	}
	
	public void setFormatOuter(FormatOuter formatOuter) {
		if (formatOuter.handleServer(addr, remotePort)) {
			this.formatOuter = formatOuter;
		}
	}
	
	private boolean isInit;
	private void initLogOuter() throws IOException {
		if (!isInit) {
			isInit = true;
			long time = System.currentTimeMillis();
			if (defaultFormatOuter == null) {
				this.defaultFormatOuter = new DefaultFormatOuter();
			}
			
			defaultFormatOuter.init("/sdcard/log/" + LogUtil.packageName + "/" + addr + "_" + remotePort + "_" + localPort + "_" + df.format(time) + "_" + defaultFormatOuter.type() );
			
			if (formatOuter != null) {
				formatOuter.init("/sdcard/log/" + LogUtil.packageName + "/" + addr + "_" + remotePort + "_" + localPort + "_" + df.format(time) + "_" + formatOuter.type() );
			}
		}
	}
	
	public synchronized void outWrite(int oneByte) {
		try {
			initLogOuter();
			if (formatOuter != null) {
				formatOuter.write(oneByte);
			}
			
			defaultFormatOuter.write(oneByte);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public synchronized void outRead(int oneByte) {
		try {
			initLogOuter();
			if (formatOuter != null) {
				formatOuter.read(oneByte);
			}
			
			defaultFormatOuter.read(oneByte);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public void close() {
		if (formatOuter != null) {
			try {
				formatOuter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (defaultFormatOuter != null) {
			try {
				defaultFormatOuter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void flush() {
		if (formatOuter != null) {
			try {
				formatOuter.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (defaultFormatOuter != null) {
			try {
				defaultFormatOuter.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private static class DefaultFormatOuter implements FormatOuter {
		private SimpleDateFormat df = new SimpleDateFormat("yyyy-dd-MM HH:mm:ss.SSS");
		
		private int curType = -1;//-1 none 0 read 1 write
		
		private FileOutputStream fWriteOuter;
		private FileOutputStream fReadOuter;
		
		@Override
		public void init(String fileName) throws IOException {
			if (fReadOuter == null) {
				fReadOuter = new FileOutputStream(fileName + ".read.txt");
			}
			if (fWriteOuter == null) {
				fWriteOuter = new FileOutputStream(fileName + ".write.txt");
			}
		}

		@Override
		public boolean write(int oneByte) throws IOException {
			if (fWriteOuter != null) {
				if (curType == -1 || curType == 0) {
					curType = 1;
					
					fWriteOuter.write("\n".getBytes());
					fWriteOuter.write(df.format(System.currentTimeMillis()).getBytes());
					fWriteOuter.write("\nwrite:\n".getBytes());
					if (PRINT_STACK) {
						fWriteOuter.write((LogUtil.getTrack() + "\n").getBytes());
					}
				}
				fWriteOuter.write((LogUtil.b2h((byte) oneByte)).getBytes());
			}
			return false;
		}

		@Override
		public boolean read(int oneByte) throws IOException {
			if (fReadOuter != null) {
				if (curType == -1 || curType == 1) {
					curType = 0;
					
					fReadOuter.write("\n".getBytes());
					fReadOuter.write(df.format(System.currentTimeMillis()).getBytes());
					fReadOuter.write("\nread:\n".getBytes());
				}
				fReadOuter.write((LogUtil.b2h((byte) oneByte)).getBytes());
			}
			return false;
		}

		@Override
		public void flush() throws IOException {
			
			if (fWriteOuter != null) {
				fWriteOuter.write("\n".getBytes());
				fWriteOuter.write(df.format(System.currentTimeMillis()).getBytes());
				fWriteOuter.write("\nflush\n".getBytes());
			}
			if (fReadOuter != null) {
				fReadOuter.write("\n".getBytes());
				fReadOuter.write(df.format(System.currentTimeMillis()).getBytes());
				fReadOuter.write("\nflush\n".getBytes());
			}
			
		}

		@Override
		public void close() throws IOException {
			if (fWriteOuter != null) {
				fWriteOuter.write("\n".getBytes());
				fWriteOuter.write(df.format(System.currentTimeMillis()).getBytes());
				fWriteOuter.write("\nclose()".getBytes());
				fWriteOuter.close();
				fWriteOuter = null;
			}
			if (fReadOuter != null) {
				fReadOuter.write("\n".getBytes());
				fReadOuter.write(df.format(System.currentTimeMillis()).getBytes());
				fReadOuter.write("\nclose()".getBytes());
				fReadOuter.close();
				fReadOuter = null;
			}
		}

		@Override
		public boolean handleServer(String ip, int port) {
			
			return true;
		}

		@Override
		public String type() {
			return "default";
		}
		
	}
	
	public static interface FormatOuter {
		void init(String fileName) throws IOException;
		boolean write(int oneByte) throws IOException ;
		boolean read(int oneByte) throws IOException ;
		void flush() throws IOException;
		void close() throws IOException;
		boolean handleServer(String ip, int port);
		String type();
	}
}
