import json, os, random, zipfile
from PIL import Image, ImageDraw

NS = "casino"          # <-- cambia si tu pack usa otro namespace
random.seed(7)
root = f"assets/{NS}"
for d in ("textures/item", "models/item", "items"):
    os.makedirs(f"{root}/{d}", exist_ok=True)

# ---------- TEXTURAS (32x32, se estiran a toda cara) ----------
def noise(img, base, amp):
    px = img.load()
    for x in range(img.width):
        for y in range(img.height):
            n = random.randint(-amp, amp)
            px[x, y] = tuple(max(0, min(255, c + n)) for c in base) + (255,)

def tex(name, fn, size=32):
    im = Image.new("RGBA", (size, size)); fn(im, ImageDraw.Draw(im)); im.save(f"{root}/textures/item/{name}.png")

def titan(im, d):
    noise(im, (38, 14, 62), 10)
    for i in range(0, 32, 8):
        d.line([(i, 0), (i, 31)], fill=(90, 30, 150, 255))
    d.line([(0, 16), (31, 16)], fill=(0, 220, 255, 255)); d.line([(16, 4), (16, 28)], fill=(0, 220, 255, 255))
    d.rectangle([0, 0, 31, 31], outline=(15, 5, 25, 255))
def face(im, d):
    noise(im, (28, 10, 48), 8)
    d.rectangle([4, 10, 12, 16], fill=(0, 240, 255, 255)); d.rectangle([19, 10, 27, 16], fill=(255, 40, 200, 255))
    d.rectangle([8, 22, 24, 25], fill=(10, 0, 20, 255))
    d.rectangle([0, 0, 31, 31], outline=(15, 5, 25, 255))
def core(im, d):
    for y in range(32):
        for x in range(32):
            t = 1 - (abs(x - 16) + abs(y - 16)) / 32
            im.putpixel((x, y), (int(60 + 195 * t * .3), int(200 * t + 40), 255, 255))
def egg(im, d):
    noise(im, (70, 25, 120), 12)
    for _ in range(14):
        x, y = random.randint(0, 28), random.randint(0, 28); d.ellipse([x, y, x + 4, y + 4], fill=(0, 230, 255, 255))
    for x in range(32): d.point((x, 16 + int(3 * ((x % 8) / 8))), fill=(255, 60, 200, 255))
def prop(im, d):
    noise(im, (110, 115, 130), 9)
    d.rectangle([0, 12, 31, 18], fill=(0, 220, 255, 255)); d.rectangle([0, 0, 31, 31], outline=(40, 40, 55, 255))
def coin(im, d):
    noise(im, (235, 185, 40), 10)
    d.ellipse([3, 3, 28, 28], outline=(150, 100, 10, 255), width=2)
    d.polygon([(16, 7), (18, 14), (25, 14), (19, 18), (21, 25), (16, 21), (11, 25), (13, 18), (7, 14), (14, 14)], fill=(255, 245, 160, 255))
for n, f in [("titan", titan), ("titan_face", face), ("titan_core", core), ("titan_egg", egg), ("propulsor", prop), ("slot_coin", coin)]:
    tex(n, f)

# ---------- MODELOS ----------
DIRS = ["north", "south", "east", "west", "up", "down"]
def cube(a, b, t="#0", front=None):
    faces = {d: {"uv": [0, 0, 16, 16], "texture": t} for d in DIRS}
    if front: faces["south"]["texture"] = front
    return {"from": a, "to": b, "faces": faces}

def model(name, elements, textures, display=None):
    m = {"textures": textures, "elements": elements}
    if display: m["display"] = display
    json.dump(m, open(f"{root}/models/item/{name}.json", "w"), indent=1)
    json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}}, open(f"{root}/items/{name}.json", "w"), indent=1)

T = f"{NS}:item/titan"
# Partes del jefe (centradas en 8,8,8 => pivote de animación)
model("boss_body", [
    cube([3, 2, 5], [13, 14, 11], "#0"),
    cube([2.5, 1, 4.5], [13.5, 3, 11.5], "#0"),
    cube([6, 6, 11], [10, 10, 11.8], "#1"),
    cube([4, 10, 3], [6, 15, 5], "#0"), cube([10, 10, 3], [12, 15, 5], "#0"),
    cube([12, 12.5, 4.5], [16, 16, 11.5], "#0"), cube([0, 12.5, 4.5], [4, 16, 11.5], "#0"),
], {"0": T, "1": f"{NS}:item/titan_core"})
model("boss_head", [
    cube([5, 5, 5], [11, 11, 11], "#0", "#1"),
    cube([4, 10, 7], [5.2, 15, 9], "#0"), cube([10.8, 10, 7], [12, 15, 9], "#0"),
    cube([7, 11, 7], [9, 14, 9], "#2"),
], {"0": T, "1": f"{NS}:item/titan_face", "2": f"{NS}:item/titan_core"})
model("boss_limb", [
    cube([6.5, -6, 6.5], [9.5, 8, 9.5], "#0"),
    cube([5.8, -9, 5.8], [10.2, -5, 10.2], "#0"),
], {"0": T})

# Huevo 3D (capas apiladas)
layers = [(1, 3, 3), (3, 5, 4.5), (5, 8, 5.5), (8, 11, 5), (11, 13, 4), (13, 15, 2.5)]
egg_disp = {"gui": {"rotation": [25, 35, 0], "scale": [0.9, 0.9, 0.9]},
            "ground": {"scale": [0.6, 0.6, 0.6]},
            "fixed": {"rotation": [0, 180, 0]},
            "thirdperson_righthand": {"scale": [0.6, 0.6, 0.6]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.6, 0.6, 0.6]}}
model("titan_egg", [cube([8 - h, a, 8 - h], [8 + h, b, 8 + h], "#0") for a, b, h in layers],
      {"0": f"{NS}:item/titan_egg"}, egg_disp)

# Propulsor 3D
prop_disp = {"gui": {"rotation": [30, 40, 0], "scale": [0.95, 0.95, 0.95]},
             "ground": {"scale": [0.6, 0.6, 0.6]}, "fixed": {"rotation": [0, 180, 0]},
             "thirdperson_righthand": {"rotation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
             "firstperson_righthand": {"rotation": [0, 20, 0], "scale": [0.7, 0.7, 0.7]}}
model("propulsor", [
    cube([6, 3, 6], [10, 13, 10]), cube([5, 1, 5], [11, 3, 11]), cube([6.5, 13, 6.5], [9.5, 15, 9.5]),
    cube([3, 5, 7.5], [6, 11, 8.5]), cube([10, 5, 7.5], [13, 11, 8.5]),
    cube([5.5, 8, 5.5], [10.5, 9, 10.5], "#1"),
], {"0": f"{NS}:item/propulsor", "1": f"{NS}:item/titan_core"}, prop_disp)

# Moneda 3D
coin_disp = {"gui": {"rotation": [15, -30, 0], "scale": [1.1, 1.1, 1.1]},
             "ground": {"scale": [0.5, 0.5, 0.5]}, "fixed": {"rotation": [0, 180, 0]},
             "thirdperson_righthand": {"scale": [0.6, 0.6, 0.6]}, "firstperson_righthand": {"scale": [0.7, 0.7, 0.7]}}
model("slot_coin", [cube([5, 3, 7.25], [11, 13, 8.75]), cube([3, 5, 7.25], [13, 11, 8.75]), cube([4, 4, 7.25], [12, 12, 8.75])],
      {"0": f"{NS}:item/slot_coin"}, coin_disp)

# Del pack: pack_format 46 = 1.21.4 (ajustalo si tu servidor usa otra version)
json.dump({"pack": {"pack_format": 46, "description": "Texture pack compartido - Void Titan + Casino"}}, open("pack.mcmeta", "w"), indent=1)

with zipfile.ZipFile("VoidTitan-Pack.zip", "w", zipfile.ZIP_DEFLATED) as z:
    z.write("pack.mcmeta")
    for r, _, fs in os.walk("assets"):
        for f in fs: z.write(os.path.join(r, f))
print("OK")
