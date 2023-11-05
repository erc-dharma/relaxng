import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import java.nio.ByteBuffer;

public class Binding {

	private static Processor proc = new Processor();

	@CEntryPoint(name = "relaxng_init")
	private static int init(IsolateThread thread, CCharPointer ptr, int len) {
		ByteBuffer buf = CTypeConversion.asByteBuffer(ptr, len);
		return proc.init(buf);
	}

	@CEntryPoint(name = "relaxng_load_schema")
	private static int loadSchema(IsolateThread thread, CCharPointer ptr, CCharPointer fptr, int len, int compact) {
		String name = CTypeConversion.toJavaString(ptr);
		if (len < 0) {
			String file = CTypeConversion.toJavaString(fptr);
			return proc.loadSchema(name, file, compact != 0);
		}
		ByteBuffer buf = CTypeConversion.asByteBuffer(fptr, len);
		return proc.loadSchema(name, buf, compact != 0);
	}

	@CEntryPoint(name = "relaxng_unload_schema")
	private static int unloadSchema(IsolateThread thread, CCharPointer ptr) {
		String name = CTypeConversion.toJavaString(ptr);
		return proc.unloadSchema(name);
	}

	@CEntryPoint(name = "relaxng_validate")
	private static int validate_file(IsolateThread thread, CCharPointer nptr, CCharPointer fptr, int len) {
		String name = CTypeConversion.toJavaString(nptr);
		if (len < 0) {
			String file = CTypeConversion.toJavaString(fptr);
			return proc.validate(name, file);
		}
		ByteBuffer buf = CTypeConversion.asByteBuffer(fptr, len);
		return proc.validate(name, buf);
	}
}
