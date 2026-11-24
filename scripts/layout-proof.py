"""Transport synthetic captures through the authenticated review job."""
import base64
import hashlib
from io import BytesIO
from pathlib import Path
import sys

from PIL import Image

directory = Path(sys.argv[1])
screens = ("ready", "active", "history", "log", "settings")
for mode in ("light", "dark"):
    for screen in screens:
        name = f"layout-{screen}-{mode}"
        with Image.open(directory / f"{name}.png") as original:
            assert original.format == "PNG"
            assert original.width >= 411 and original.height > original.width
            im = original.convert("RGB")
            im.thumbnail((411, 822), Image.Resampling.LANCZOS)
            buf = BytesIO()
            im.save(buf, format="JPEG", quality=89, optimize=True, subsampling=0)
        raw = buf.getvalue()
        print(f"FSUX|BEGIN|{name}|{len(raw)}|{hashlib.sha256(raw).hexdigest()}")
        for part, start in enumerate(range(0, len(raw), 2400)):
            print(f"FSUX|PART|{name}|{part}|{base64.b64encode(raw[start:start+2400]).decode()}")
        print(f"FSUX|END|{name}")
