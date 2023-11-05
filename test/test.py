import os, relaxng

os.chdir(os.path.dirname(__file__))

relaxng.Schema("inscription.rnc")
relaxng.Schema("inscription.rng")
relaxng.Schema("inscription.rnc", True)
relaxng.Schema(open("inscription.rng", "rb").read())
relaxng.Schema(open("inscription.rng", "rb").read(), False)
relaxng.Schema(open("inscription.rnc", "rb").read(), True)

try:
	relaxng.Schema("bad_schema.rnc", True)
except relaxng.SchemaError:
	pass
try:
	relaxng.Schema("bad_schema.rng")
except relaxng.SchemaError:
	pass

sch = relaxng.Schema("inscription.rnc")
sch("bad_structure.xml")
sch("inscription.rng")
sch(open("bad_structure.xml", "rb").read())
sch(open("inscription.rng", "rb").read())

try:
	sch("bad_xml.xml")
except relaxng.XMLError:
	pass
try:
	sch("inexisting.xml")
except relaxng.Error:
	pass
