import os

root_dir = r"c:\Users\USER\Desktop\HomeHub\app\src\main"

renames = []

for root, dirs, files in os.walk(root_dir):
    for name in files:
        if "host" in name.lower():
            old_path = os.path.join(root, name)
            new_name = name.replace("Host", "Caretaker").replace("host", "caretaker")
            new_path = os.path.join(root, new_name)
            renames.append((old_path, new_path))
    
    # Also handle directory renames if any (though not expected in this project structure for 'host')
    for name in dirs:
        if "host" in name.lower():
            old_path = os.path.join(root, name)
            new_name = name.replace("Host", "Caretaker").replace("host", "caretaker")
            new_path = os.path.join(root, new_name)
            renames.append((old_path, new_path))

# Sort by depth descending to avoid renaming parent before child (though not strictly necessary here)
renames.sort(key=lambda x: x[0].count(os.sep), reverse=True)

for old, new in renames:
    print(f"Renaming: {old} -> {new}")
    try:
        os.rename(old, new)
    except Exception as e:
        print(f"Failed to rename {old}: {e}")
