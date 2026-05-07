import os
import shutil

print("Starting asset migration...")

# Copy Layouts
src_layout_dir = r"C:\Users\USER\HomeView\app\src\main\res\layout"
dst_layout_dir = r"C:\Users\USER\Desktop\HomeHub\app\src\main\res\layout"
os.makedirs(dst_layout_dir, exist_ok=True)

layouts = [
    "activity_admin_dashboard.xml",
    "activity_host_dashboard.xml",
    "activity_admin_login.xml",
    "activity_login.xml",
    "item_admin_property.xml",
    "item_host.xml"
]

for f in layouts:
    src_path = os.path.join(src_layout_dir, f)
    dst_path = os.path.join(dst_layout_dir, f)
    if os.path.exists(src_path):
        try:
            shutil.copy2(src_path, dst_path)
            print(f"Copied layout: {f}")
        except Exception as e:
            print(f"Error copying layout {f}: {e}")
    else:
        print(f"Skipped layout: {f} (Not found in source)")

# Copy Drawables
src_drawable_dir = r"C:\Users\USER\HomeView\app\src\main\res\drawable"
dst_drawable_dir = r"C:\Users\USER\Desktop\HomeHub\app\src\main\res\drawable"
os.makedirs(dst_drawable_dir, exist_ok=True)

if os.path.exists(src_drawable_dir):
    copied_drawables = 0
    for f in os.listdir(src_drawable_dir):
        if f.endswith(".xml") or f.endswith(".png") or f.endswith(".jpg") or f.endswith(".webp") or f.endswith(".jpeg"):
            src_path = os.path.join(src_drawable_dir, f)
            dst_path = os.path.join(dst_drawable_dir, f)
            try:
                shutil.copy2(src_path, dst_path)
                copied_drawables += 1
            except Exception as e:
                print(f"Error copying drawable {f}: {e}")
    print(f"Copied {copied_drawables} drawables.")
else:
    print(f"Source drawable directory not found: {src_drawable_dir}")

print("Asset migration completed.")
