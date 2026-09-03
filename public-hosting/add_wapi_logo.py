from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).parent
base = Image.open(root / "../public/og.png").convert("RGBA")
logo = Image.open(root / "wapi-logo-new.png").convert("RGBA")

logo = logo.resize((84, 84), Image.Resampling.LANCZOS)
base.alpha_composite(logo, (52, 32))

draw = ImageDraw.Draw(base)
font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 20)
headline_font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 92)
body_font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 28)
feature_font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 24)
# Replace the legacy WHAPPY campaign copy with the WAPI identity.
draw.rounded_rectangle((38, 128, 635, 424), radius=18, fill=(3, 17, 49, 232), outline=(0, 166, 240, 110), width=2)
draw.text((58, 136), "WAPI", font=headline_font, fill=(0, 166, 240, 255))
draw.text((60, 260), "Votre monde, au bout du numéro.", font=body_font, fill=(255, 255, 255, 255))
draw.line((60, 314, 215, 314), fill=(0, 190, 235, 255), width=3)
draw.text((60, 338), "Messages  •  Appels  •  Marketplace", font=feature_font, fill=(255, 255, 255, 255))
small_logo = logo.resize((30, 30), Image.Resampling.LANCZOS)
badge = (804, 54, 925, 98)
draw.rounded_rectangle(badge, radius=22, fill=(0, 102, 207, 235))
base.alpha_composite(small_logo, (812, 61))
draw.text((850, 64), "WAPI", font=font, fill="white")

# App Store badge alongside the existing Android availability badge.
draw.rounded_rectangle((480, 457, 735, 551), radius=14, fill=(5, 5, 5, 255), outline=(255, 255, 255, 90), width=2)
store_small = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 14)
store_big = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial Bold.ttf", 22)
draw.text((529, 472), "Disponible sur", font=store_small, fill="white")
draw.text((529, 498), "App Store", font=store_big, fill="white")

base.save(root / "wapi-store-hero.png", format="PNG")
