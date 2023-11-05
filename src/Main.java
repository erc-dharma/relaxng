import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Scanner;
import java.nio.file.Files;

// The point of this is to exercise the code so as to discover bundles and uses
// of reflection that should be pointed out to the compiler.
public class Main {

	private static void report(ByteBuffer buf, String[] argv, int ret) {
		System.out.printf("CMD ");
		for (int i = 0; i < argv.length; i++) {
			System.out.printf("%s ", argv[i]);
		}
		System.out.printf("%d\n", ret);
		if (ret != 0) {
			String msg = null;
			try {
				msg = new String(buf.array(), 0, buf.position(), "UTF-8");
				System.out.printf("%s\n", msg);
			} catch (UnsupportedEncodingException e) {
				System.out.printf("%s\n", e);
			}
		}
	}

	private static void doFile(Processor proc, ByteBuffer buf, String[] argv) {
		int ret = -333;
		switch (argv[0]) {
		case "load":
			ret = proc.loadSchema(argv[1], argv[2], !argv[3].equals("0"));
			break;
		case "unload":
			ret = proc.unloadSchema(argv[1]);
			break;
		case "validate":
			ret = proc.validate(argv[1], argv[2]);
			break;
		}
		report(buf, argv, ret);
	}

	private static void doBuffer(Processor proc, ByteBuffer buf, String[] argv) {
		if (argv.length < 3) {
			return;
		}
		File test = new File(argv[2]);
		if (!test.exists()) {
			return;
		}
		ByteBuffer xml = null;
		try {
			xml = ByteBuffer.wrap(Files.readAllBytes(Path.of(argv[2])));
		} catch (java.io.IOException e) {
			return;
		}
		int ret = -333;
		switch (argv[0]) {
		case "load":
			ret = proc.loadSchema(argv[1], xml, !argv[3].equals("0"));
			break;
		case "unload":
			ret = proc.unloadSchema(argv[1]);
			break;
		case "validate":
			ret = proc.validate(argv[1], xml);
			break;
		}
		report(buf, argv, ret);
	}

	public static void main(String[] args) {
		Processor proc = new Processor();
		ByteBuffer buf = ByteBuffer.wrap(new byte[8 * 1024]);
		proc.init(buf);
		Scanner in = new Scanner(System.in);
		while (in.hasNext()) {
			String line = in.nextLine();
			String[] argv = line.split(" ");
			doFile(proc, buf, argv);
			doBuffer(proc, buf, argv);
		}
	}
}
