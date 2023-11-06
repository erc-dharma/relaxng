import java.io.InputStream;
import java.nio.ByteBuffer;

public class Input extends InputStream {

	private ByteBuffer buf;
	private int byteOff;
	private int codeOff;
	private int lineNo;
	private int columnNo;
	private int columnNo16;

	private int validInfo = 0;
	private static final int BYTE_OFF = 1 << 0;
	private static final int LINE_NO = 1 << 1;
	private static final int COLUMN_NO = 1 << 2;

	private static final byte[] charLen = new byte[]{
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
		3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
		4, 4, 4, 4, 4, 4, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0
	};

	public Input(ByteBuffer b) {
		buf = b;
		lineNo = columnNo = 1;
		validInfo = BYTE_OFF;
		if (buf.remaining() >= 3 && buf.get(0) == 0xef && buf.get(1) == 0xbb && buf.get(2) == 0xbf)
			buf.position(3);
	}

	private boolean gotoLine(int line) {
		if (lineNo > line) {
			return false;
		}
		if (lineNo == line) {
			return true;
		}
		columnNo = columnNo16 = 1;
		do {
			if (buf.remaining() < 1) {
				return false;
			}
			byte b = buf.get();
			if (b == '\r') {
				if (buf.remaining() > 0 && buf.get() != '\n') {
					buf.position(buf.position() - 1);
				}
				lineNo++;
			} else if (b == '\n') {
				lineNo++;
			}
			int len = charLen[b & 0xff];
			if (len == 0 || buf.remaining() < len - 1) {
				return false;
			}
			while (--len > 0) {
				buf.get();
			}
			codeOff++;
		} while (lineNo < line);
		return true;
	}

	private boolean gotoColumn(int column16) {
		while (columnNo16 < column16) {
			if (buf.remaining() < 1) {
				return false;
			}
			byte b = buf.get();
			int len = charLen[b & 0xff];
			if (len == 0 || buf.remaining() < len - 1) {
				return false;
			}
			int c;
			switch (len) {
			case 1:
				c = b & 0xff;
				break;
			case 2:
				c = (b & 0x1f) << 6;
				c |= buf.get() & 0x3f;
				break;
			case 3:
				c = (b & 0x0f) << 12;
				c |= (buf.get() & 0x3f) << 6;
				c |= buf.get() & 0x3f;
				break;
			default:
				c = (b & 0x07) << 18;
				c |= (buf.get() & 0x3f) << 12;
				c |= (buf.get() & 0x3f) << 6;
				c |= buf.get() & 0x3f;
				break;
			}
			if (c <= 0xffff) {
				columnNo16++;
			} else {
				columnNo16 += 2;
			}
			columnNo++;
			codeOff++;
		}
		return true;
	}

	public void gotoLocation(int line, int column16) {
		if (validInfo == 0) {
			return;
		}
		validInfo &= ~(LINE_NO | COLUMN_NO);
		if (line > 0) {
			int savePos = buf.position();
			buf.position(byteOff);
			if (gotoLine(line)) {
				validInfo |= LINE_NO;
				if (column16 > 0) {
					if (gotoColumn(column16)) {
						validInfo |= COLUMN_NO;
					} else {
						validInfo = 0;
					}
				}
			} else {
				validInfo = 0;
			}
			byteOff = buf.position();
			buf.position(savePos);
		}
	}

	public int getByteOff() {
		return validInfo != 0 ? byteOff : -1;
	}

	public int getCodeOff() {
		return validInfo != 0 ? codeOff : -1;
	}

	public int getLineNo() {
		return (validInfo & LINE_NO) != 0 ? lineNo : -1;
	}

	public int getColumnNo() {
		return (validInfo & COLUMN_NO) != 0 ? columnNo : -1;
	}

	public int available() {
		return buf.remaining();
	}

	public void close() {
	}

	public boolean markSupported() {
		return false;
	}

	public int read() {
		if (buf.remaining() > 0) {
			return buf.get() & 0xff;
		}
		return -1;
	}

	public int read(byte[] b) {
		return read(b, 0, b.length);
	}

	public int read(byte[] b, int off, int len) {
		if (len == 0) {
			return 0;
		}
		int rem = buf.remaining();
		if (rem == 0) {
			return -1;
		}
		if (len > rem) {
			len = rem;
		}
		buf.get(b, off, len);
		return len;
	}

	public long skip(long n) {
		if (n <= 0) {
			return 0;
		}
		long rem = buf.remaining();
		if (n > rem) {
			n = rem;
		}
		buf.position(buf.position() + (int)n);
		return n;
	}
}
