package com.virjar.xposedhooktool.tool.socket;

import com.virjar.xposedhooktool.tool.log.LogUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class OutputStreamPrinter {

	final List<Integer> bufferInput = new ArrayList<Integer>();
	private String[] headerArrs;
	private Integer contentLength ;
	private Integer curChunkedSize ;
	private boolean isGzip ;
	private ByteArrayOutputStream out ;
	
	private Socket socket;
	
	private Boolean isHttp;
	private OutPrinter outPrinter;
	
	public OutputStreamPrinter(Socket socket, OutPrinter outPrinter) {
		this.socket = socket;
		this.outPrinter = outPrinter;
	}
	
	public void write(byte[] buffer, int offset, int count) {
		for (int i = offset; i < offset + count; i++) {
			check(buffer[i]);
		}
	}
	
	public void write(int oneByte) {
		check(oneByte);
	}
	
	private void check(int oneByte) {
		if (headerArrs == null) {
			boolean isAdd = false;
			if (isHttp == null) {
				if (bufferInput.size() < 5) {
					bufferInput.add(oneByte);
					isAdd = true;
				}
				
				if (bufferInput.size() >= 5) {
					int b1 = bufferInput.get(0);
					int b2 = bufferInput.get(1);
					int b3 = bufferInput.get(2);
					int b4 = bufferInput.get(3);
					int b5 = bufferInput.get(4);
					String post = new String(new byte[] {(byte)b1,(byte)b2,(byte)b3,(byte)b4,(byte)b5});
					if (post.equalsIgnoreCase("POST ")) {
						isHttp = true;
					} else {
						String method = new String(new byte[] {(byte)b1,(byte)b2,(byte)b3,(byte)b4});
						isHttp = method.equalsIgnoreCase("GET ") || method.equalsIgnoreCase("PUT ");
					}
					if (!isHttp) {
						outPrinter.outWrite(b1);
						outPrinter.outWrite(b2);
						outPrinter.outWrite(b3);
						outPrinter.outWrite(b4);
						outPrinter.outWrite(b5);
					}
				}
			}
			if (isHttp != null && !isHttp) {
				if (!isAdd) {
					outPrinter.outWrite(oneByte);
				}
				return;
			}
			
			if (!isAdd) {
				bufferInput.add(oneByte);
			}
			
			
			if (bufferInput.size() > 4) {
				int r1 = bufferInput.get(bufferInput.size() - 4);
				int n1 = bufferInput.get(bufferInput.size() - 3);
				int r2 = bufferInput.get(bufferInput.size() - 2);
				int n2 = bufferInput.get(bufferInput.size() - 1);
				if (r1 == '\r' && n1 == '\n' && r2 == '\r' && n2 == '\n') {
					byte[] buf = new byte[bufferInput.size() - 4];
					for (int i = 0; i < buf.length; i++) {
						buf[i] = (byte)(int)bufferInput.get(i);
					}
					bufferInput.clear();
					String headerStr = new String(buf);
					headerArrs = headerStr.split("\r\n");
					contentLength = getContentLength();
					if (contentLength == null || contentLength == 0) {
						out();
					} else {
						curChunkedSize = contentLength;
						out = new ByteArrayOutputStream(contentLength);
						isGzip = isGzip();
					}
				}
			}
		} else {
			if (curChunkedSize > 0) {
				out.write(oneByte);
				curChunkedSize--;
			}
			if (curChunkedSize == 0) {
				out();
			}
		}
	}
	
	private void out() {
		StringBuilder sb = new StringBuilder();
		sb.append("localPort:").append(socket.getLocalPort()).append(",remotePort:").append(socket.getPort()).append("\n");
		for (int i = 0; i < headerArrs.length; i++) {
			sb.append(headerArrs[i]).append("\r\n");
		}
		sb.append("\r\n");
//		if (headerArrs[0].contains("POST /app_logs HTTP/1.1")) {
//			String msg = "";
//			StackTraceElement[] ste = new Throwable().getStackTrace();
//			for (StackTraceElement stackTraceElement : ste) {
//				msg += (stackTraceElement.getClassName() + "." + stackTraceElement.getMethodName() + ":" + stackTraceElement.getLineNumber() + "\n");
//			}
//			XposedBridge.log(msg);
//			Log.i("fuck",msg);
//		}
		if (out != null) {
			
			try {
				if (isGzip) {
					ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
							out.toByteArray());
					out.close();
					out = new ByteArrayOutputStream();
					
					GZIPInputStream gzipInputStream = new GZIPInputStream(
							byteArrayInputStream);
					byte[] buf = new byte[4096];
					int len = -1;
					while ((len = gzipInputStream.read(buf)) != -1) {
						out.write(buf, 0, len);
					}
					gzipInputStream.close();
				}
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			try {
				String ct = getContentType();
				if(ct != null && (ct.contains("text")
						|| ct.contains("application/x-www-form-urlencoded")
						|| ct.contains("application/json"))) {
					sb.append(new String(out
							.toByteArray(), "UTF-8"));
				} else {
					sb.append("0x::" + LogUtil.printHexString(out.toByteArray()));
				}
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		
		LogUtil.outLog(sb.toString());
		
		bufferInput.clear();
		headerArrs = null;
		contentLength = null ;
		curChunkedSize = null;
		isGzip = false ;
		out = null ;
	
	}


	private Integer getContentLength() {
		if (headerArrs != null) {
			for (int i = 1; i < headerArrs.length; i++) {
				String str = headerArrs[i];
				int pos = str.indexOf(":");
				if (pos != -1) {
					String name = str.substring(0, pos).trim();
					String value = str.substring(pos + 1).trim();
					if ("Content-Length".equalsIgnoreCase(name)) {
						return Integer.parseInt(value);
					}
				}
			}
		}
		return null;
	}
	
	private boolean isGzip() {
		if (headerArrs != null) {
			for (int i = 1; i < headerArrs.length; i++) {
				String str = headerArrs[i];
				int pos = str.indexOf(":");
				if (pos != -1) {
					String name = str.substring(0, pos).trim();
					String value = str.substring(pos + 1).trim();
					if ("Content-Encoding".equalsIgnoreCase(name) && "gzip".equalsIgnoreCase(value)) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	private String getContentType() {
		if (headerArrs != null) {
			for (int i = 1; i < headerArrs.length; i++) {
				String str = headerArrs[i];
				int pos = str.indexOf(":");
				if (pos != -1) {
					String name = str.substring(0, pos).trim();
					String value = str.substring(pos + 1).trim();
					if ("Content-Type".equalsIgnoreCase(name)) {
						return value;
					}
				}
			}
		}
		return null;
	}
	
}
