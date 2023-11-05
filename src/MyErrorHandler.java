import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXException;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MyErrorHandler implements ErrorHandler {

	private Buffer buf;
	private PrintWriter writer;
	private Input input;
	private int errCode;

	public MyErrorHandler(ByteBuffer b) {
		buf = new Buffer(b);
		writer = new PrintWriter(new OutputStreamWriter(buf, StandardCharsets.UTF_8), true);
	}

	public void reset(Input in) {
		input = in;
		buf.clear();
		errCode = 0;
	}

	public void writeError(int ec, Exception e) {
		buf.clear();
		writer.printf("%s\n", e.getMessage());
		errCode = ec;
	}

	public int finish() {
		int ret = buf.finish();
		if (errCode != 0) {
			return errCode;
		}
		return ret;
	}

	public void report(SAXParseException e, String type) {
		input.gotoLocation(e.getLineNumber(), e.getColumnNumber());
		String msg = e.getMessage();
		if (msg == null) {
			msg = "no error details";
		}
		writer.printf("%d:%d:%d:%d:%s:%s\n",
			Integer.valueOf(input.getByteOff()),
			Integer.valueOf(input.getCodeOff()),
			Integer.valueOf(input.getLineNo()),
			Integer.valueOf(input.getColumnNo()),
			type, msg);
	}

	public void error(SAXParseException e) {
		report(e, "error");
	}

	public void fatalError(SAXParseException e) {
		report(e, "fatal");
	}

	public void warning(SAXParseException e) {
		report(e, "warning");
	}
}
