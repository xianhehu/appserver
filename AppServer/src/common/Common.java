package common;
import java.util.Date;

public class Common {
	public static short b2int16(byte[] buf) {
		int ret = 0;
		
		ret += (buf[0]&0xff)<< 8;
		ret += (buf[1]&0xff)<< 0;
		
		return (short)ret;
	}
	
	public static int b2int32(byte[] buf) {
		int ret = 0;
		
		ret += (buf[0]&0xff)<<24;
		ret += (buf[1]&0xff)<<16;
		ret += (buf[2]&0xff)<< 8;
		ret += (buf[3]&0xff)<< 0;
		
		return ret;
	}
	
	public static long b2int64(byte[] buf) {
		long ret = 0;
		
		ret += (buf[0]&0xff)<<56;
		ret += (buf[1]&0xff)<<48;
		ret += (buf[2]&0xff)<<40;
		ret += (buf[3]&0xff)<<32;
		ret += (buf[4]&0xff)<<24;
		ret += (buf[5]&0xff)<<16;
		ret += (buf[6]&0xff)<< 8;
		ret += (buf[7]&0xff)<< 0;
		
		return ret;
	}
	
	public static byte[] subbytes(byte[] buf, int start, int end) {
		byte[] ret = new byte[end-start];
		System.arraycopy(buf, start, ret, 0, end-start);
		
		return ret;
	}
	
	public static long getTime() {
		return new Date().getTime();
	}
}
