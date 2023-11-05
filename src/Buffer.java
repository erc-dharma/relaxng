import java.io.OutputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

public class Buffer extends OutputStream {

	private ByteBuffer data;
	private boolean finished = false;
	private boolean overflowed = false;

	private static final byte[] more = new String("-1:-1:-1:-1:warning:more error messages follow but were truncated\n").getBytes();

	public Buffer(ByteBuffer buf) {
		buf.clear();
		data = buf;
	}

	public int clear() {
		data.put(0, (byte)'\0');
		data.position(0);
		finished = false;
		overflowed = false;
		return 0;
	}

	public void close() {
	}

	public void flush() {
	}

	public void write(byte[] b, int off, int n) {
		n = fitting(n);
		if (n > 0) {
			data.put(b, off, n);
		}
	}

	public void write(int b) {
		if (fitting(1) > 0) {
			data.put((byte)b);
		}
	}

	private int fitting(int n) {
		if (overflowed) {
			return  0;
		}
		int rem = data.remaining() - more.length - 1;
		if (n > rem) {
			overflowed = true;
			return 0;
		}
		return n;
	}

	private void addTrailingRecord() {
		int len = data.position();
		while (len > 0 && data.get(len - 1) != '\n') {
			len--;
		}
		data.position(len);
		overflowed = false;
		try {
			write(more);
		} catch (IOException e) {
			System.err.printf("relaxng: internal error\n");
			System.exit(1);
		}
	}

	public int finish() {
		if (finished) {
			return data.position();
		}
		finished = true;
		if (overflowed) {
			addTrailingRecord();
		} else {
			int len = data.position();
			if (len > 0) {
				// Cut the trailing new line
				data.position(data.position() - 1);
			}
		}
		int len = data.position();
		data.put(len, (byte)'\0');
		return len;
	}
}
