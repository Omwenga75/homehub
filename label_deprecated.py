import os

path = r"C:\Users\USER\Desktop\HomeHub\app\src\main\java\com\example\homehub"
files = [f for f in os.listdir(path) if f.endswith(".kt") and f != "HomeHubApplication.kt"]

for f in files:
    full_path = os.path.join(path, f)
    if os.path.isfile(full_path):
        with open(full_path, "r", encoding="utf-8") as file:
            content = file.read()
        
        if not content.startswith("// DEPRECATED"):
            new_content = "// DEPRECATED: Safely delete this file. Implementation moved to subpackage.\n" + content
            with open(full_path, "w", encoding="utf-8") as file:
                file.write(new_content)

print(f"Processed {len(files)} files.")
