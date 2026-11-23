import sys, zipfile, os
orig, out, newdir = sys.argv[1], sys.argv[2], sys.argv[3]
REPLACE = {"system.new.dat.br", "system.transfer.list", "system.patch.dat"}
store = {"system.new.dat.br"}  # already brotli-compressed -> store
src = zipfile.ZipFile(orig, "r")
with zipfile.ZipFile(out, "w", allowZip64=False) as z:
    for zi in src.infolist():
        name = zi.filename
        comp = zipfile.ZIP_STORED if name in store else zipfile.ZIP_DEFLATED
        if name in REPLACE:
            data = open(os.path.join(newdir, os.path.basename(name)), "rb").read()
        else:
            data = src.read(name)
            comp = zi.compress_type
        ni = zipfile.ZipInfo(name, date_time=(2009, 1, 1, 0, 0, 0))
        ni.external_attr = zi.external_attr  # keep perms (update-binary 0755)
        ni.compress_type = comp
        z.writestr(ni, data)
src.close()
print("wrote", out, os.path.getsize(out), "bytes")
