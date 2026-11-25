"""Inspect the final resource tables, including strings contributed by libraries."""
import argparse
from pathlib import Path
import shlex
import subprocess
import tempfile
from zipfile import ZipFile


def check_apk(aapt2, apk, label):
    result = subprocess.check_output([aapt2, "dump", "badging", apk], text=True)
    line = next(line for line in result.splitlines() if line.startswith("locales:"))
    tags = shlex.split(line.removeprefix("locales:"))
    languages = {tag.split("-")[0] for tag in tags if tag != "--_--"}
    # AAPT uses the historic Indonesian code in some build-tools versions.
    languages = {"id" if language == "in" else language for language in languages}
    if "--_--" in tags:
        languages.add("en")  # English is the unqualified app resource set.
    expected = {"en", "ms", "id"}
    if languages != expected:
        raise ValueError(f"{label}: expected {sorted(expected)}, found {sorted(languages)} in {tags}")
    print(f"{label}: packaged languages en, id, ms; resource locales {', '.join(tags)}", flush=True)


def check_bundle(aapt2, bundle):
    # App bundles carry the same linked resources in protobuf format, before Play makes splits.
    with ZipFile(bundle) as archive, tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        modules = [name.removesuffix("/resources.pb") for name in archive.namelist() if name.endswith("/resources.pb")]
        if "base" not in modules:
            raise ValueError(f"No base resource table in {bundle}")
        for module in modules:
            proto = root / "resources.apk"
            binary = root / "binary.apk"
            with ZipFile(proto, "w") as output:
                output.writestr("AndroidManifest.xml", archive.read(f"{module}/manifest/AndroidManifest.xml"))
                output.writestr("resources.pb", archive.read(f"{module}/resources.pb"))
                for name in archive.namelist():
                    if name.startswith(f"{module}/res/") and not name.endswith("/"):
                        output.writestr(name.removeprefix(f"{module}/"), archive.read(name))
            subprocess.run([aapt2, "convert", "--output-format", "binary", "-o", binary, proto], check=True)
            check_apk(aapt2, binary, f"{bundle.name}/{module}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aapt2")
    parser.add_argument("artifacts", nargs="+", type=Path)
    args = parser.parse_args()
    for artifact in args.artifacts:
        if artifact.suffix == ".aab":
            check_bundle(args.aapt2, artifact)
        else:
            check_apk(args.aapt2, artifact, artifact.name)
