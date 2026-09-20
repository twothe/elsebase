"""Fetch checksum-pinned optional Iris/Sodium test dependencies into ignored build fixtures only."""
from pathlib import Path
import hashlib
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FILES = [
    ("iris-neoforge-1.8.12+mc1.21.1.jar", "https://cdn.modrinth.com/data/YL57xq9U/versions/t3ruzodq/iris-neoforge-1.8.12%2Bmc1.21.1.jar",
     "57b8026a3c3c433cf6123d63dc6ce7e11f6d480a72926370db1fc7f2b06059bc16a753ecd7e7af659c19e90b592103196b0e89585ce4f0744a4ca433f59bcf1a"),
    ("sodium-neoforge-0.6.13+mc1.21.1.jar", "https://cdn.modrinth.com/data/AANobbMI/versions/Pb3OXVqC/sodium-neoforge-0.6.13%2Bmc1.21.1.jar",
     "ce58f34d05d96c0a109a5cea23c741f6bdb2e6be31fc087c5989274cefca5f10ba0c08c62083cf554a51f2c7667bf46e4164383f675c844e77633aef2659996b"),
]
for fixture, profile in ((fixture, profile) for fixture in ("portal-client", "template-client") for profile in ("iris", "iris-active")):
    directory = ROOT / "build" / f"{fixture}-{profile}"
    (directory / "mods").mkdir(parents=True, exist_ok=True)
    (directory / "config").mkdir(exist_ok=True)
    for filename, url, digest in FILES:
        target = directory / "mods" / filename
        data = target.read_bytes() if target.exists() else urllib.request.urlopen(
            urllib.request.Request(url, headers={"User-Agent": "Elsebase-render-regression"}), timeout=60).read()
        if hashlib.sha512(data).hexdigest() != digest:
            raise RuntimeError(f"Checksum mismatch: {filename}")
        target.write_bytes(data)
    (directory / "config" / "iris.properties").write_text(
        f"enableShaders={'true' if profile == 'iris-active' else 'false'}\nshaderPack=Elsebase-Test.zip\n", encoding="utf-8")
    if profile == "iris-active":
        (directory / "shaderpacks").mkdir(exist_ok=True)
        # Minimal authored shader fixture, not a third-party pack compatibility certification.
        with zipfile.ZipFile(directory / "shaderpacks" / "Elsebase-Test.zip", "w") as pack:
            pack.writestr("shaders/gbuffers_basic.vsh", "#version 120\nvoid main(){gl_Position=ftransform();gl_FrontColor=gl_Color;}\n")
            pack.writestr("shaders/gbuffers_basic.fsh", "#version 120\nvoid main(){gl_FragData[0]=gl_Color;}\n")
            # Explicit full-bright terrain makes texture assertions independent of a shader's lighting policy.
            pack.writestr("shaders/gbuffers_terrain.vsh", "#version 120\nvarying vec2 uv; void main(){gl_Position=ftransform();uv=gl_MultiTexCoord0.xy;}\n")
            pack.writestr("shaders/gbuffers_terrain.fsh", "#version 120\nuniform sampler2D gtexture; varying vec2 uv; void main(){gl_FragData[0]=texture2D(gtexture,uv);}\n")
    print(f"Prepared {directory}")
