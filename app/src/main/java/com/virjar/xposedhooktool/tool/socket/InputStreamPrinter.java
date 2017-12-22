package com.virjar.xposedhooktool.tool.socket;

import com.virjar.xposedhooktool.tool.log.LogUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

public class InputStreamPrinter {

	private List<Integer> bufferInput = new ArrayList<Integer>();
	private String[] headerArrs;
	private Integer contentLength ;
	private Integer curChunkedSize ;
	private boolean isChunked ;
	private boolean isGzip ;
	private String contentType ;
	private ByteArrayOutputStream out ;
	
	private Socket socket;
	
	private Boolean isHttp;
	private OutPrinter outPrinter;
	//HTTP/
	
	public InputStreamPrinter(Socket socket, OutPrinter outPrinter) {
		this.socket = socket;
		this.outPrinter = outPrinter;
	}
	
	public void read(int oneByte) {
		if (oneByte != -1) {
			check(oneByte);
		}
	}
	
	public void read(byte[] buf, int offset, int byteCount) {
		 for (int i = offset; i < offset + byteCount; i++) {
			 check(buf[i]);
		}
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
					String hs = new String(new byte[] {(byte)b1,(byte)b2,(byte)b3,(byte)b4,(byte)b5});
					isHttp = hs.equalsIgnoreCase("HTTP/");
					
					if (!isHttp) {
						outPrinter.outRead(b1);
						outPrinter.outRead(b2);
						outPrinter.outRead(b3);
						outPrinter.outRead(b4);
						outPrinter.outRead(b5);
					}
				}
			}
			
			if (isHttp != null && !isHttp) {
				if (!isAdd) {
					outPrinter.outRead(oneByte);
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
						buf[i] = (byte)(int) bufferInput.get(i);
					}
					bufferInput.clear();
					String headerStr = new String(buf);
					headerArrs = headerStr.split("\r\n");
					contentLength = getContentLength();
					if (contentLength == null) {
						isChunked = isChunked();
						out = new ByteArrayOutputStream();
					} else {
						curChunkedSize = contentLength;
						out = new ByteArrayOutputStream(contentLength);
					}
					isGzip = isGzip();
					contentType = getContentType();
				}
			}
		} else {
			if (contentLength == null) {
				if (isChunked) {
					if (curChunkedSize == null) {
						bufferInput.add(oneByte);
						if (bufferInput.size() > 2) {
							int r = bufferInput.get(bufferInput.size() - 2);
							int n = bufferInput.get(bufferInput.size() - 1);
							if (r == '\r' && n == '\n') {
								byte[] bs = new byte[bufferInput.size() - 2];
								for (int i = 0; i < bs.length; i++) {
									bs[i] = (byte)(int)bufferInput.get(i);
								}
								bufferInput.clear();
								
								String lenStr = new String(bs).trim();
								curChunkedSize = Integer.parseInt(lenStr, 16);
							}
						}
						
					} else if (curChunkedSize > 0) {
						out.write(oneByte);
						curChunkedSize--;
						if (curChunkedSize == 0) {
							curChunkedSize = null;
						}
					} else {
						//footer
						bufferInput.add(oneByte);
						if (bufferInput.size() >= 2) {
							int r = bufferInput.get(bufferInput.size() - 2);
							int n = bufferInput.get(bufferInput.size() - 1);
							if (r == '\r' && n == '\n') {
								bufferInput.clear();
								out();
							}
						}
					}
					
				} else {
					// throw exception ?
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
	}
	
	private void out() {
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
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		// Content-Type
		String cs = null;
		String ct = contentType;
		if (ct != null && ct.contains(";")) {
			String[] arr2 = ct.split(";");
			ct = arr2[1];
			if (ct.contains("=")) {
				arr2 = ct.split("=");
				cs = arr2[1];
			}
		}
		
		if(cs == null) {
			cs = "UTF-8" ;
		} else if(cs.equalsIgnoreCase("gb2312")) {
			cs = "GBK" ;
		}
		try {
//			saveLog(new String(out
//					.toByteArray(), cs));
			StringBuilder sb = new StringBuilder();
			sb.append("localPort:").append(socket.getLocalPort()).append(",remotePort:").append(socket.getPort()).append("\n");
			for (int i = 0; i < headerArrs.length; i++) {
				sb.append(headerArrs[i]).append("\r\n");
			}
			sb.append("\r\n");
			
			if (contentType != null && contentType.startsWith("image/")) {
				sb.append("this content is a image!");
			} else {
				if (contentType != null && (contentType.contains("text")
						|| contentType.contains("application/json"))) {
					sb.append(new String(out
							.toByteArray(), cs));
				} else {
					sb.append(LogUtil.printHexString(out.toByteArray()));
				}
			}
			sb.append("\n");
			LogUtil.outLog(sb.toString());
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		bufferInput.clear();
		headerArrs = null;
		contentLength = null ;
		curChunkedSize = null;
		isChunked = false ;
		isGzip = false ;
		contentType = null ;
		out = null ;
	}
	
	private Integer getContentLength() {
		if (headerArrs != null) {
			for (int i = 1; i < headerArrs.length; i++) {
				String str = headerArrs[i];
				if (str.contains(": ")) {
					String[] paramvalue = str.split(": ");
					String name = paramvalue[0].trim();
					String value = paramvalue.length >= 2 ? paramvalue[1].trim() : null;
					if ("Content-Length".equalsIgnoreCase(name)) {
						return Integer.parseInt(value);
					}
				}
			}
		}
		return null;
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
	
	private boolean isChunked() {
		if (headerArrs != null) {
			for (int i = 1; i < headerArrs.length; i++) {
				String str = headerArrs[i];
				int pos = str.indexOf(":");
				if (pos != -1) {
					String name = str.substring(0, pos).trim();
					String value = str.substring(pos + 1).trim();
					if ("Transfer-Encoding".equalsIgnoreCase(name) && "chunked".equalsIgnoreCase(value)) {
						return true;
					}
				}
			}
		}
		return false;
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
					//Content-Type: application/zip
					if ("Content-Type".equalsIgnoreCase(name) && "application/zip".equalsIgnoreCase(value)) {
						return true;
					}
					
				}
			}
		}
		return false;
	}
	
	
}
