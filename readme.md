# RelaxNG – Python interface

This is a Python package for validating XML documents with RelaxNG.

There already exists a Python module for RelaxNG validation in
[lxml](https://lxml.de), but it does not work. (So far, I ran into infinite
loops while loading schemas, and the validation results I managed to obtain are
nonsensical.) It seems that the [libxml2](https://github.com/GNOME/libxml2)
implementation of RelaxNG lxml uses is either incorrect or not up-to-date with
the standard.

Hence the usefulness of this module. It is a Python binding to the reference
RelaxNG implementation, namely, [jing](https://relaxng.org/jclark/jing.html).
Jing is written in Java, which makes it inconvenient to use outside of the JVM,
but, fortunately, it is possible to compile Java bytecode to native code with
[GraalVM](https://www.graalvm.org). This is the approach taken by this package.

The resulting Python bindings are actually faster than the Java original, even
without turning on compiler optimizations.

## Installation

This directory can be used as a Python package. Put it in some location
enumerated in `$PYTHONPATH` or in the calling code's directory. In practice,
you only need the files `librelaxng.so` and `relaxng.py`.

The shared library `librelaxng.so` is compiled for Linux x86_64. To use the
code within another platform or with another version of glibc, it needs to be
recompiled on this platform, and a few modifications need to be made to
`relaxng.py` and to the `makefile` (the `.so` extension might need to be
modified, in particular).

To recompile it, first download GraalVM, version 21 or ulterior, from
[here](https://www.graalvm.org/downloads), and unpack the archive. Say you
unpacked the archive to the directory `~/downloads/graalvm-jdk-21.0.1+12.1`.
Set up the following environment variables accordingly:

	export JAVA_HOME=~/downloads/graalvm-jdk-21.0.1+12.1
	export PATH=$JAVA_HOME/bin:$PATH

Once you have made the necessary modifications to the `makefile` and to
`relaxng.py`, you can run `make` and wait a few minutes for the shared library
to recompile.

There is also a `make config` target. It is used to exercise the Java code so
as to generate configuration files that GraalVM uses to determine what bundles
and classes it should include into the compiled image. You should not need to
use this target.

## Usage

The script `relaxng.py` can be used directly for validating files:

	python3 relaxng.py test/inscription.rng test/bad_structure.xml

To do the same thing in Python:

```python
import relaxng

schema = relaxng.Schema("test/inscription.rng")
errors = schema("test/bad_structure.xml")
for err in errors:
	print(f"{file}:{err.line}:{err.column}: {err.type}: {err.text}")
```

## API Reference

### Error Objects

```python
class Error(Exception)
```

Base error class.

We only throw errors of this type.


### SchemaError Objects

```python
class SchemaError(Error)
```

Raised when loading an invalid schema.

The `errors` members contains a list of `Message` objects
indicating problems in the schema, in the same format as the
ones returned by `Schema.__call__()`.


### XMLError Objects

```python
class XMLError(Error)
```

Raised when the file to validate is not valid XML (unclosed tags,
improper nesting, etc.). This might be due to encoding errors.


### Schema Objects

```python
class Schema(object)
```

Represents a RelaxNG schema.


##### \_\_init\_\_

```python
def __init__(schema, compact=None)
```

Loads a schema.

If `schema` is a byte string, it is assumed to be the
schema itself, encoded in UTF-8. Otherwise, it is interpreted
as a file path. The given file must be a regular file (or a
symbolic link to a regular file) and must be encoded in UTF-8.

The schema can be in the XML format or in the compact format.
Per default, it is assumed to be in the XML format, unless
`schema` is a path and the file extension is `.rnc`. Pass
`True` or `False` to `compact` to specify explicitly whether the
file is in the compact format or not, respectively.


##### \_\_call\_\_

```python
def __call__(xml)
```

Validates an XML document.

If `xml` is a byte string, it is assumed to be the document
itself, encoded in UTF-8. Otherwise, it is interpreted as a
file path. The given file must be a regular file (or a symbolic
link to a regular file) and must be encoded in UTF-8.

Returns a list of `Message` objects. `Message` objects represent
validation errors. They have the following fields:

* `byte_offset`: offset of the error within the validated file.
* `code_offset`: same as `byte_offset`, but counting in code points.
* `line`: number of the line where the error occurs, starting at 1.
  Lines are counted as in the XML specification: delimitors are
  `\n`, `\r`, and `\r\n`. The line break characters introduced
  in Unicode, namely U+2028 and U+2029, are not counted as line
  breaks. But Python treats them as such in the function
  `str.splitlines()`, so you should be careful not to use it
  when reporting errors.
* `column`: column offset, counting in code points, starting at 1.
  Code points do not necessary correspond to character units,
  but most tools  out there count columns in code points, so we do
  it too.
* `type`: error type, one of "warning", "error" or "fatal".
* `text`: error message.

The exact error location is not necessarily available. When
`byte_offset`, etc., are not known, they are to set to -1. You
should be prepared to handle the following cases:

* `line` and `column` are both valid. Other fields are valid.
* `line` is valid but `column` is not. Other fields are valid.
* `line` and `column` are both invalid. In this case, `byte_offset`
   and `code_offset` are invalid, too.

When the error location is available, it points right after the
part of the document that generated the error.

Not all errors might be reported if there are too many.
In this case, the last error message will say so.
