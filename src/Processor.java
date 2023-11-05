import com.thaiopensource.util.PropertyMapBuilder;
import com.thaiopensource.util.PropertyMap;
import com.thaiopensource.validate.ValidationDriver;
import com.thaiopensource.validate.ValidateProperty;
import com.thaiopensource.validate.SchemaReader;
import com.thaiopensource.validate.rng.CompactSchemaReader;
import com.thaiopensource.validate.prop.rng.RngProperty;
import com.thaiopensource.xml.sax.ErrorHandlerImpl;

import org.xml.sax.SAXException;
import org.xml.sax.InputSource;
import org.xml.sax.ErrorHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.io.FileNotFoundException;
import java.nio.charset.CharacterCodingException;
import java.util.HashMap;

public class Processor {

	public static final int OK = 0;
	public static final int API_ERROR = -1;
	public static final int SAX_ERROR = -2;
	public static final int IO_ERROR = -3;
	public static final int FILE_NOT_FOUND_ERROR = -4;

	private PropertyMap props;
	private HashMap<String, ValidationDriver> drivers = new HashMap<String, ValidationDriver>();
	private MyErrorHandler errHandler;

	public int init(ByteBuffer b) {
		errHandler = new MyErrorHandler(b);
		PropertyMapBuilder properties = new PropertyMapBuilder();
		properties.put(ValidateProperty.ERROR_HANDLER, errHandler);
		// Avoid all the mess with XML injection and also make our
		// lives simpler by only processing one file at a time.
		properties.put(ValidateProperty.ENTITY_RESOLVER, null);
		properties.put(ValidateProperty.URI_RESOLVER, null);
		properties.put(ValidateProperty.RESOLVER, null);
		RngProperty.CHECK_ID_IDREF.add(properties);
		props = properties.toPropertyMap();
		return 0;
	}

	private ByteBuffer loadFile(String name) {
		try {
			FileChannel f = FileChannel.open(Path.of(name), StandardOpenOption.READ);
			long size = f.size();
			if (size <= 32 * 1024) {
				ByteBuffer b = ByteBuffer.allocate((int)size);
				if (f.read(b) >= (int)size) {
					b.position(0);
					return b;
				}
				throw new IOException("unexpected short read");
			} else if (size <= 1 * 1024 * 1024 * 1024) {
				return f.map(FileChannel.MapMode.READ_ONLY, 0, size);
			} else {
				throw new IOException("file too large (limit is 1GiB)");
			}
		} catch (IOException e) {
			errHandler.writeError(IO_ERROR, e);
		}
		return null;
	}

	public int loadSchema(String name, ByteBuffer b, boolean compact) {
		SchemaReader sr = null;
		if (compact) {
			sr = CompactSchemaReader.getInstance();
		}
		Input in = new Input(b);
		errHandler.reset(in);
		ValidationDriver driver = new ValidationDriver(props, sr);
		try {
			InputSource src = new InputSource(in);
			src.setEncoding("UTF-8");
			if (driver.loadSchema(src)) {
				drivers.put(name, driver);
			}
		} catch (SAXException e) {
			errHandler.writeError(SAX_ERROR, e);
		} catch (FileNotFoundException e) {
			errHandler.writeError(FILE_NOT_FOUND_ERROR, e);
		} catch (IOException e) {
			errHandler.writeError(IO_ERROR, e);
		}
		return errHandler.finish();
	}

	public int loadSchema(String name, String file, boolean compact) {
		ByteBuffer b = loadFile(file);
		if (b == null) {
			return errHandler.finish();
		}
		return loadSchema(name, b, compact);
	}

	public int unloadSchema(String file) {
		errHandler.reset(null);
		drivers.remove(file);
		return errHandler.finish();
	}

	public int validate(String schema, ByteBuffer b) {
		ValidationDriver driver = drivers.get(schema);
		if (driver == null) {
			errHandler.writeError(API_ERROR, new Exception("schema '" + schema + "' not loaded"));
			return errHandler.finish();
		}
		Input in = new Input(b);
		errHandler.reset(in);
		try {
			InputSource src = new InputSource(in);
			src.setEncoding("UTF-8");
			driver.validate(src);
		} catch (SAXException e) {
			errHandler.writeError(SAX_ERROR, e);
		} catch (FileNotFoundException e) {
			errHandler.writeError(FILE_NOT_FOUND_ERROR, e);
		} catch (IOException e) {
			errHandler.writeError(IO_ERROR, e);
		}
		return errHandler.finish();
	}

	public int validate(String schema, String file) {
		ByteBuffer b = loadFile(file);
		if (b == null) {
			return errHandler.finish();
		}
		return validate(schema, b);
	}
}
