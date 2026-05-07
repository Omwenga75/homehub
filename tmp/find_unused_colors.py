import re
import os

colors_path = r"c:\Users\USER\Desktop\HomeHub\app\src\main\res\values\colors.xml"
project_root = r"c:\Users\USER\Desktop\HomeHub"

# Read colors.xml and extract all color names
with open(colors_path, 'r', encoding='utf-8') as f:
    xml_content = f.read()

color_names = set(re.findall(r'<color name="([^"]+)">', xml_content))
used_colors = set()

# Files to scan (XML, Java, Kotlin)
extensions = ('.xml', '.java', '.kt', '.gradle')

print(f"Scanning for usages of {len(color_names)} colors in {project_root}...")

for root, dirs, files in os.walk(project_root):
    # Skip build and hidden directories
    if 'build' in dirs:
        dirs.remove('build')
    if '.git' in dirs:
        dirs.remove('.git')
    if '.gradle' in dirs:
        dirs.remove('.gradle')
    if '.idea' in dirs:
        dirs.remove('.idea')

    for file in files:
        if file.endswith(extensions) and file != 'colors.xml':
            file_path = os.path.join(root, file)
            try:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
                    # Check for @color/name or R.color.name
                    for color in color_names:
                        if color in used_colors:
                            continue
                        # Simple check first, then more specific
                        if color in content:
                            if re.search(rf'(@color/{color}\b|R\.color\.{color}\b|>{color}<)', content):
                                used_colors.add(color)

            except Exception as e:
                print(f"Error reading {file_path}: {e}")

unused_colors = color_names - used_colors

print(f"\nFound {len(unused_colors)} unused colors out of {len(color_names)}.")

# Writing results to a file for the next step
with open(r"c:\Users\USER\Desktop\HomeHub\tmp\unused_colors.txt", 'w') as f:
    for c in sorted(list(unused_colors)):
        f.write(f"{c}\n")

print(f"Unused colors list written to {project_root}\\tmp\\unused_colors.txt")
