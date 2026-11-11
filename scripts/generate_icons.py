import os
import math
from PIL import Image, ImageDraw, ImageFilter

def create_icon(size=512, is_round=False):
    # 512x512 master image with supersampling for ultra smooth rendering
    scale = 4
    canvas_size = size * scale
    img = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    center = canvas_size / 2.0
    radius = (canvas_size * 0.92) / 2.0 if is_round else canvas_size * 0.44

    # 1. Background
    # Create radial/diagonal gradient background
    bg = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    bg_draw = ImageDraw.Draw(bg)

    if is_round:
        # Circular mask
        mask = Image.new("L", (canvas_size, canvas_size), 0)
        mask_draw = ImageDraw.Draw(mask)
        mask_draw.ellipse([center - radius, center - radius, center + radius, center + radius], fill=255)
    else:
        # Squircle / Rounded Rect
        mask = Image.new("L", (canvas_size, canvas_size), 0)
        mask_draw = ImageDraw.Draw(mask)
        corner_r = int(canvas_size * 0.22)
        mask_draw.rounded_rectangle([canvas_size * 0.04, canvas_size * 0.04, canvas_size * 0.96, canvas_size * 0.96], radius=corner_r, fill=255)

    # Draw gradient on background: Deep navy (#0F172A) to Royal Blue (#1E3A8A) with cyan accent (#0284C7)
    for y in range(canvas_size):
        ratio = y / canvas_size
        # Interpolate from #0F172A (15, 23, 42) to #1E3A8A (30, 58, 138)
        r = int(15 * (1 - ratio) + 30 * ratio)
        g = int(23 * (1 - ratio) + 58 * ratio)
        b = int(42 * (1 - ratio) + 138 * ratio)
        bg_draw.line([(0, y), (canvas_size, y)], fill=(r, g, b, 255))

    # Add subtle glowing radial flare in top right
    flare = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    flare_draw = ImageDraw.Draw(flare)
    flare_center = (canvas_size * 0.75, canvas_size * 0.25)
    flare_radius = canvas_size * 0.5
    for r_step in range(int(flare_radius), 0, -8):
        alpha = int(45 * (1 - r_step / flare_radius))
        flare_draw.ellipse([flare_center[0] - r_step, flare_center[1] - r_step,
                            flare_center[0] + r_step, flare_center[1] + r_step],
                           fill=(0, 229, 255, alpha))
    bg.alpha_composite(flare)

    # Apply mask
    bg_final = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    bg_final.paste(bg, (0, 0), mask)

    # 2. Foreground Elements: Outer Synchronize Orbital Arrows + Stylized Fitness Dumbbell
    fg = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    fg_draw = ImageDraw.Draw(fg)

    # Draw Orbital Sync Rings
    # Two smooth circular arcs with arrow heads
    orbit_r = canvas_size * 0.32
    orbit_width = int(canvas_size * 0.038)

    # Top arc (from 200 deg to 340 deg) - Neon Cyan #00E5FF
    cyan_color = (0, 229, 255, 255)
    blue_color = (56, 189, 248, 255)

    # Draw arc 1: Top-Right
    fg_draw.arc([center - orbit_r, center - orbit_r, center + orbit_r, center + orbit_r],
                start=210, end=350, fill=cyan_color, width=orbit_width)

    # Arrowhead 1 at ~350 deg
    a1_rad = math.radians(350)
    a1_x = center + orbit_r * math.cos(a1_rad)
    a1_y = center + orbit_r * math.sin(a1_rad)
    # Triangle arrowhead pointing along tangent
    tangent1 = a1_rad + math.pi / 2
    arrow_len = canvas_size * 0.06
    arrow_w = canvas_size * 0.04
    p1 = (a1_x + arrow_len * math.cos(tangent1), a1_y + arrow_len * math.sin(tangent1))
    p2 = (a1_x - arrow_w * math.sin(tangent1), a1_y + arrow_w * math.cos(tangent1))
    p3 = (a1_x + arrow_w * math.sin(tangent1), a1_y - arrow_w * math.cos(tangent1))
    fg_draw.polygon([p1, p2, p3], fill=cyan_color)

    # Draw arc 2: Bottom-Left (from 30 deg to 170 deg)
    fg_draw.arc([center - orbit_r, center - orbit_r, center + orbit_r, center + orbit_r],
                start=30, end=170, fill=cyan_color, width=orbit_width)

    # Arrowhead 2 at ~170 deg
    a2_rad = math.radians(170)
    a2_x = center + orbit_r * math.cos(a2_rad)
    a2_y = center + orbit_r * math.sin(a2_rad)
    tangent2 = a2_rad + math.pi / 2
    p4 = (a2_x + arrow_len * math.cos(tangent2), a2_y + arrow_len * math.sin(tangent2))
    p5 = (a2_x - arrow_w * math.sin(tangent2), a2_y + arrow_w * math.cos(tangent2))
    p6 = (a2_x + arrow_w * math.sin(tangent2), a2_y - arrow_w * math.cos(tangent2))
    fg_draw.polygon([p4, p5, p6], fill=cyan_color)

    # 3. Center Modern Dumbbell (rotated 45 degrees)
    # Draw rotated dumbbell on a separate layer
    db = Image.new("RGBA", (canvas_size, canvas_size), (0, 0, 0, 0))
    db_draw = ImageDraw.Draw(db)

    # Draw horizontal dumbbell, then rotate 45 degrees
    db_center = (canvas_size / 2, canvas_size / 2)
    bar_len = canvas_size * 0.28
    bar_h = canvas_size * 0.045
    # Central bar
    db_draw.rounded_rectangle([db_center[0] - bar_len / 2, db_center[1] - bar_h / 2,
                               db_center[0] + bar_len / 2, db_center[1] + bar_h / 2],
                              radius=int(bar_h / 2), fill=(241, 245, 249, 255))

    # Inner weight plates
    plate1_w = canvas_size * 0.04
    plate1_h = canvas_size * 0.16
    db_draw.rounded_rectangle([db_center[0] - bar_len / 2 - plate1_w, db_center[1] - plate1_h / 2,
                               db_center[0] - bar_len / 2, db_center[1] + plate1_h / 2],
                              radius=int(plate1_w / 3), fill=(56, 189, 248, 255))
    db_draw.rounded_rectangle([db_center[0] + bar_len / 2, db_center[1] - plate1_h / 2,
                               db_center[0] + bar_len / 2 + plate1_w, db_center[1] + plate1_h / 2],
                              radius=int(plate1_w / 3), fill=(56, 189, 248, 255))

    # Outer main weight plates
    plate2_w = canvas_size * 0.055
    plate2_h = canvas_size * 0.22
    db_draw.rounded_rectangle([db_center[0] - bar_len / 2 - plate1_w - plate2_w, db_center[1] - plate2_h / 2,
                               db_center[0] - bar_len / 2 - plate1_w, db_center[1] + plate2_h / 2],
                              radius=int(plate2_w / 3), fill=(248, 250, 252, 255))
    db_draw.rounded_rectangle([db_center[0] + bar_len / 2 + plate1_w, db_center[1] - plate2_h / 2,
                               db_center[0] + bar_len / 2 + plate1_w + plate2_w, db_center[1] + plate2_h / 2],
                              radius=int(plate2_w / 3), fill=(248, 250, 252, 255))

    # Center pulse / energy dot
    dot_r = canvas_size * 0.032
    db_draw.ellipse([db_center[0] - dot_r, db_center[1] - dot_r,
                     db_center[0] + dot_r, db_center[1] + dot_r],
                    fill=(0, 229, 255, 255))

    # Rotate dumbbell by 45 degrees
    db_rotated = db.rotate(45, resample=Image.BICUBIC, center=db_center)

    # Composite
    fg.alpha_composite(db_rotated)

    # Composite FG onto BG
    final_icon = Image.alpha_composite(bg_final, fg)

    # Downsample cleanly to target size with Lanczos filter for high fidelity
    return final_icon.resize((size, size), Image.Resampling.LANCZOS)

def generate_all():
    base_res = "app/src/main/res"
    densities = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    for folder, size in densities.items():
        dir_path = os.path.join(base_res, folder)
        os.makedirs(dir_path, exist_ok=True)
        # Regular icon
        icon = create_icon(size=size, is_round=False)
        icon.save(os.path.join(dir_path, "ic_launcher.png"), "PNG")
        # Round icon
        round_icon = create_icon(size=size, is_round=True)
        round_icon.save(os.path.join(dir_path, "ic_launcher_round.png"), "PNG")
        print(f"Generated {folder}: {size}x{size}")

    # Also save 512x512 preview icon
    os.makedirs("app/src/main/res/drawable", exist_ok=True)
    master = create_icon(size=512, is_round=False)
    master.save("app/src/main/res/drawable/app_icon_preview.png", "PNG")
    print("Master 512x512 preview generated.")

if __name__ == "__main__":
    generate_all()
