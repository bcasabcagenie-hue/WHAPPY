from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).parent
base = Image.open(root / "../public/og.png").convert("RGBA")
logo = Image.open(root / "wapi-logo-hd.png").convert("RGBA")

logo = logo.resize((84, 84), Image.Resampling.LANCZOS)
base.alpha_composite(logo, (52, 32))

draw = ImageDraw.Draw(base)
font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 20)
small_logo = logo.resize((30, 30), Image.Resampling.LANCZOS)
badge = (804, 54, 925, 98)
draw.rounded_rectangle(badge, radius=22, fill=(0, 102, 207, 235))
base.alpha_composite(small_logo, (812, 61))
draw.text((850, 64), "WAPI", font=font, fill="white")

base.save(root / "wapi-store-hero.png", format="PNG")
