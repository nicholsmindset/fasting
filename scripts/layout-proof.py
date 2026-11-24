"""Transport synthetic captures through the authenticated review job."""
import base64
import hashlib
from io import BytesIO
from pathlib import Path
import sys

from PIL import Image

directory = Path(sys.argv[1])
screens = ("ready", "active", "history", "log", "settings")
modes = sys.argv[2:] or ("light", "dark")
assert all(mode in ("light", "dark") for mode in modes)
captures = {f"layout-{screen}-{mode}": directory / f"layout-{screen}-{mode}.png"
            for mode in modes for screen in screens}
if sys.argv[2:]:
    for mode in modes:
        captures[f"layout-reminders-{mode}"] = directory.parent / "settings" / f"4a-reminders-{mode}.png"
        picker = directory.parent / "settings" / f"4b-milestone-picker-{mode}.png"
        if picker.exists():
            captures[f"layout-picker-{mode}"] = picker
    if "light" in modes:
        captures["layout-large-light"] = directory / "timer-large-text.png"
for name, pathname in captures.items():
    with Image.open(pathname) as original:
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
