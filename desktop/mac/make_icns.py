import struct, subprocess, sys
svg, out = sys.argv[1], sys.argv[2]
types = [("icp4",16),("icp5",32),("icp6",64),("ic07",128),("ic08",256),("ic09",512),("ic10",1024),("ic11",32),("ic12",64),("ic13",256),("ic14",512)]
chunks = b""
for t, px in types:
    png = subprocess.run(["rsvg-convert","-w",str(px),"-h",str(px),svg], capture_output=True, check=True).stdout
    chunks += t.encode() + struct.pack(">I", len(png)+8) + png
open(out,"wb").write(b"icns" + struct.pack(">I", len(chunks)+8) + chunks)
