"RelaxNG validation of XML documents."

from ctypes import *
import os, re, threading, collections

__all__ = ["Error", "SchemaError", "XMLError", "Message", "Schema"]

class Error(Exception):
	"""Base error class.

	We only throw errors of this type.
	"""

class SchemaError(Error):
	"""Raised when loading an invalid schema.

	The `errors` members contains a list of `Message` objects
	indicating problems in the schema, in the same format as the
	ones returned by `Schema.__call__()`.
	"""

	errors = []

class XMLError(Error):
	"""Raised when the file to validate is not valid XML (unclosed tags,
	improper nesting, etc.). This might be due to encoding errors."""

_lib = cdll.LoadLibrary(os.path.join(os.path.dirname(__file__), "librelaxng.so"))
_lib.relaxng_init.argtypes = (c_void_p, POINTER(c_char), c_int)
_lib.relaxng_load_schema.argtypes = (c_void_p, c_char_p, POINTER(c_char), c_int, c_int)
_lib.relaxng_unload_schema.argtypes = (c_void_p, c_char_p)
_lib.relaxng_validate.argtypes = (c_void_p, c_char_p, POINTER(c_char), c_int)

_main = threading.local()
_main.buf = create_string_buffer(64 * 1024)
_main.thread = c_void_p()

if _lib.graal_create_isolate(c_void_p(), pointer(c_void_p()), pointer(_main.thread)) != 0:
	raise Error("cannot allocate GraalVM")
_lib.relaxng_init(_main.thread, _main.buf, len(_main.buf))

def _common_call(func, name, xml, extra=None):
	if isinstance(xml, bytes):
		n = len(xml)
		# ctypes let ints overflow.
		if n > 1 * 1024 * 1024 * 1024:
			raise Error("text buffer too long")
	else:
		xml = str(xml).encode()
		n = -1
	if extra is None:
		ret = func(_main.thread, name, xml, n)
	else:
		ret = func(_main.thread, name, xml, n, extra)
	if ret == 0:
		text = ""
	elif ret > 0:
		text = _main.buf.raw[:ret].decode("UTF-8")
	else:
		text = _main.buf.value.decode()
		if ret == -1:
			# Should not happen unless we mess with the C calls.
			raise Error("internal problem: %s" % text)
		elif ret == -2:
			pass # XML error, will deal with this later on.
		elif ret == -3 or ret == -4:
			raise Error("Java IOException: %s" % text)
		elif ret == -5:
			raise UnicodeDecodeError("Java error: %s" % text)
		else:
			raise Error("internal problem: %s" % text)
	return ret, text

Message = collections.namedtuple("Message", "byte_offset code_offset line column type text")

def _extract_errors(text):
	errs = []
	for line in text.split("\n"):
		byte_offset, code_offset, line, column, type, message = line.split(":", 5)
		rec = Message(int(byte_offset), int(code_offset), int(line), int(column), type, message)
		errs.append(rec)
	return errs

class Schema(object):
	"Represents a RelaxNG schema."

	def __init__(self, schema, compact=None):
		"""Loads a schema.

		If `schema` is a byte string, it is assumed to be the
		schema itself, encoded in UTF-8. Otherwise, it is interpreted
		as a file path. The given file must be a regular file (or a
		symbolic link to a regular file) and must be encoded in UTF-8.

		The schema can be in the XML format or in the compact format.
		Per default, it is assumed to be in the XML format, unless
		`schema` is a path and the file extension is `.rnc. Pass
		True or False to `compact` to specify explicitly whether the
		file is in the compact format or not, respectively.
		"""
		self._name = c_char_p(str(id(self)).encode("UTF-8"))
		if not isinstance(schema, bytes):
			self._readable_name = str(schema)
			if compact is None:
				_, ext = os.path.splitext(self._readable_name)
				compact = ext.lower() == ".rnc"
		else:
			self._readable_name = ":memory:"
		compact = bool(compact)
		ret, text = _common_call(_lib.relaxng_load_schema, self._name, schema, compact)
		if ret == 0:
			return
		if ret < 0:
			raise SchemaError("invalid XML: %s" % text)
		err = SchemaError("invalid schema %r" % self._readable_name)
		err.errors = _extract_errors(text)
		raise err

	def __repr__(self):
		name = self._readable_name
		if " " in name:
			name = repr(name)
		return "<Schema(%s)>" % name

	def __call__(self, xml):
		"""Validates an XML document.

		If `xml` is a byte string, it is assumed to be the document
		itself, encoded in UTF-8. Otherwise, it is interpreted as a
		file path. The given file must be a regular file (or a symbolic
		link to a regular file) and must be encoded in UTF-8.

		Returns a list of `Message` objects. `Message` objects represent
		validation errors. They have the following fields:

		* `byte_offset`: offset of the error within the validated file.
		* `code_offset`: same as `byte_offset`, but counting in code points.
		* `line`: number of the line where the error occurs, starting at 1.
		  Lines are counted as in the XML specification: delimitors are \\n,
		  \\r, and \\r\\n. The line break characters introduced
		  in Unicode, namely U+2028 and U+2029, are not counted as line
		  breaks. But Python treats them as such in the function
		  `str.splitlines()`, so you should be careful not to use it
		  when reporting errors.
		* `column`: column offset, counting in code points, starting at 1.
		  Code points do not necessary correspond to character units,
		  but most tools  out there count columns in code points, so we do
		  it too.
		* `text`: error message.

		The exact error location is not necessarily available. When
		`byte_offset`, etc., are not known, they are to set to -1. You
		should be prepared to handle the following cases:

		* `line` and `column` are both valid. Other fields are valid.
		* `line` is valid but `column` is not. Other fields are valid.
		* `line` and `column` are both invalid. In this case, `byte_offset`
		   and `code_offset` are invalid, too.
		"""
		ret, text = _common_call(_lib.relaxng_validate, self._name, xml)
		if ret < 0:
			raise XMLError(text)
		if ret == 0:
			return []
		return _extract_errors(text)

	def __del__(self):
		_lib.relaxng_unload_schema(_main.thread, self._name)

if __name__ == "__main__":
	import sys, argparse
	prog = "relaxng"

	def print_recs(file, recs):
		for rec in recs:
			print("%s:%d:%d: %s: %s" % (file, rec.line, rec.column, rec.type, rec.text))

	def die(msg, file=None, recs=[]):
		print("%s: %s" % (prog, msg), file=sys.stderr)
		print_recs(file, recs)
		sys.exit(1)

	parser = argparse.ArgumentParser(
		prog=prog,
		description="Validates XML documents against a RelaxNG schema.")
	parser.add_argument("schema", help="""Schema path. If the file's extension
		is .rnc, it is assumed to be in the compact syntax. Otherwise,
		it is treated as an XML document.""")
	parser.add_argument("files", nargs="*", help="""Files to validate. If none
		are given, the schema itself is validated.""")
	args = parser.parse_args()
	try:
		schema = Schema(args.schema)
	except SchemaError as e:
		die(e, args.schema, e.errors)
	except Error as e:
		die(e)
	if not args.files:
		sys.exit(0)
	ret = 0
	for file in args.files:
		try:
			errs = schema(file)
			print_recs(file, errs)
		except XMLError as e:
			print_recs(file, [Message(text=str(e))])
			ret = 1
		except Error as e:
			print("%s: %s" % (prog, e), file=sys.stderr)
			ret = 1
	sys.exit(ret)
