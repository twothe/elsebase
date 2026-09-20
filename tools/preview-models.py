"""Render front elevations of generated models using the local Minecraft texture archive.

Requires Pillow. This is an asset inspection sheet, not a gameplay screenshot or lighting preview.
Run after generate-resources.mjs; output and extracted pixels stay in ignored build/.
"""
import io
import json
from pathlib import Path
import zipfile
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/generated/resources"
ARCHIVE = ROOT / "build/moddev/artifacts/neoforge-21.1.250-client-extra-aka-minecraft-resources.jar"
THEMES = ["quiet_workshop", "arcane_archive", "verdant_cloister", "astral_observatory",
          "deepstone_halls", "porcelain_sanctuary", "service_layer"]


def main():
    canvas = Image.new("RGB", (1400, 720), "#18252b")
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 16)
    heading = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 26)
    draw.text((32, 20), "ELSEBASE / OPEN THRESHOLDS", font=heading, fill="#efad6f")
    draw.text((32, 60), "Actual model front elevations | local Minecraft textures | not a gameplay screenshot", font=font, fill="#c6d6d9")
    with zipfile.ZipFile(ARCHIVE) as archive:
        textures = {}

        def texture(identifier):
            if identifier not in textures:
                namespace, path = identifier.split(":")
                textures[identifier] = Image.open(io.BytesIO(archive.read(f"assets/{namespace}/textures/{path}.png"))).convert("RGBA")
            return textures[identifier]

        def render(model_path, left, top, scale):
            model = json.loads(model_path.read_text())
            # Looking toward -Z. Furthest faces first, matching a front elevation.
            for element in sorted(model["elements"], key=lambda e: e["to"][2]):
                face = element["faces"].get("south")
                if face is None:
                    continue
                x0, y0, _ = element["from"]
                x1, y1, _ = element["to"]
                reference = face["texture"]
                while reference.startswith("#"):
                    reference = model["textures"][reference[1:]]
                source = texture(reference)
                uv = face.get("uv", [x0, 16-y1, x1, 16-y0])
                factor = source.width / 16
                crop = source.crop(tuple(round(v*factor) for v in uv))
                width, height = max(1, round((x1-x0)*scale)), max(1, round((y1-y0)*scale))
                if crop.width == 0 or crop.height == 0:
                    crop = source.resize((1, 1))
                crop = crop.resize((width, height), Image.Resampling.NEAREST)
                canvas.paste(crop, (round(left+x0*scale), round(top+(16-y1)*scale)), crop)

        for index, theme in enumerate(THEMES):
            left = 32 + index * 196
            prefix = RESOURCES if index == 0 else RESOURCES / "resourcepacks" / theme
            for x in range(8):
                for y in range(16):
                    color = "#263a40" if (x+y) % 2 else "#2e454b"
                    draw.rectangle((left+x*16, 124+y*16, left+x*16+15, 124+y*16+15), fill=color)
            render(prefix / "assets/elsebase/models/block/portal_upper.json", left, 124, 8)
            render(prefix / "assets/elsebase/models/block/portal_lower.json", left, 252, 8)
            for row, word in enumerate(theme.replace("_", " ").title().split()):
                draw.text((left, 400+row*21), word, font=font, fill="#d9e5e7")
        draw.text((32, 478), "Reusable tools and crafting core", font=heading, fill="#efad6f")
        for index, name in enumerate(["anchor_tool", "portal_tool", "removal_tool", "creation_tool", "threshold_core"]):
            left = 45+index*275
            render(RESOURCES / f"assets/elsebase/models/item/{name}.json", left, 530, 8)
            draw.text((left, 672), name.replace("_", " ").title(), font=font, fill="#d9e5e7")
        # Verify all bundled texture references, including optional packs the client has not enabled.
        for file in RESOURCES.rglob("*.json"):
            for reference in json.loads(file.read_text()).get("textures", {}).values():
                if not reference.startswith("#"):
                    texture(reference)
        print(f"Verified {len(textures)} local texture references across all seven themes.")
    output = ROOT / "build/model-preview.png"
    canvas.save(output)
    print(output)


if __name__ == "__main__":
    main()
