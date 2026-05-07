import os
import re

BASE_DIR = r"C:\Users\USER\Desktop\HomeHub\app\src\main\java\com\example\homehub"
MANIFEST_PATH = r"C:\Users\USER\Desktop\HomeHub\app\src\main\AndroidManifest.xml"
LAYOUT_DIR = r"C:\Users\USER\Desktop\HomeHub\app\src\main\res\layout"

CATEGORIES = {
    'auth': ['SignUp', 'UserLogin', 'PhoneAuth', 'RoleSelection', 'Splash', 'PrivateAccess'],
    'admin': ['Admin', 'ManageUsers', 'VerifiedUsers', 'SuspendedUsers'],
    'caretaker': ['Caretaker', 'ManageCaretakers', 'MyProperties'],
    'student': ['Student', 'ManageStudents'],
    'supplier': ['WaterSupplier', 'Supplier', 'AddWaterService'],
    'property': ['Property', 'Properties', 'House', 'Room', 'Amenities', 'Filter', 'Location', 'Map', 'MapLocation'],
    'chat': ['Chat', 'Message'],
    'billing': ['Booking', 'Bookings', 'Payment', 'Rent', 'Mpesa'],
    'notification': ['Notification'],
    'core': ['Application', 'Review', 'Settings', 'Analytics', 'RecentActivity', 'Maintenance', 'Report', 'Image', 'Bitmap', 'Favorites', 'Session', 'Theme', 'Validation', 'Extensions', 'Encryption', 'DocumentVerificationBottomSheet', 'KenyanIdValidator']
}

def get_category_for_file(filename):
    name = filename.replace('.kt', '')
    for cat, keywords in CATEGORIES.items():
        for kw in keywords:
            if name.startswith(kw) or (kw in name):
                if 'Admin' in name: return 'admin'
                if 'Caretaker' in name: return 'caretaker'
                return cat
    
    if name.endswith('Adapter'): return 'adapters'
    elif name.endswith('Activity') or name.endswith('Fragment'): return 'activities'
    elif name.endswith('Manager') or name.endswith('Service') or name.endswith('Helper') or name.endswith('Utils'): return 'utils'
    else: return 'models'

def main():
    if not os.path.exists(BASE_DIR):
        print("BASE_DIR not found")
        return

    files = [f for f in os.listdir(BASE_DIR) if f.endswith('.kt') and os.path.isfile(os.path.join(BASE_DIR, f))]
    
    file_destinations = {}
    
    for f in files:
        cat = get_category_for_file(f)
        class_name = f.replace('.kt', '')
        file_destinations[f] = {
            'category': cat,
            'old_path': os.path.join(BASE_DIR, f),
            'new_path': os.path.join(BASE_DIR, cat, f),
            'class_name': class_name,
            'new_package': f"com.example.homehub.{cat}"
        }
        
    for fd in file_destinations.values():
        os.makedirs(os.path.dirname(fd['new_path']), exist_ok=True)
        
    file_contents = {}
    for f, fd in file_destinations.items():
        with open(fd['old_path'], 'r', encoding='utf-8') as file:
            file_contents[f] = file.read()
            
    # Process files
    for f, fd in file_destinations.items():
        content = file_contents[f]
        
        # 1. Update Package
        content = re.sub(r'^package com\.example\.homehub\s*$', f"package {fd['new_package']}", content, flags=re.MULTILINE)
        
        # 2. R import
        if 'import com.example.homehub.R' not in content:
            pkg_match = re.search(r'^package .+$', content, flags=re.MULTILINE)
            if pkg_match:
                insert_pos = pkg_match.end()
                content = content[:insert_pos] + '\n\nimport com.example.homehub.R' + content[insert_pos:]
                
        # 3. Add necessary imports for moved classes
        imports_to_add = set()
        for other_f, other_fd in file_destinations.items():
            if other_f == f: continue
            if other_fd['category'] == fd['category']: continue
            
            # Fast check if class name exists
            if other_fd['class_name'] in content:
                # Regex boundary check
                if re.search(r'\b' + other_fd['class_name'] + r'\b', content):
                    imp = f"import {other_fd['new_package']}.{other_fd['class_name']}"
                    if imp not in content:
                        imports_to_add.add(imp)
                        
        if imports_to_add:
            # find where to inject
            last_import = [m for m in re.finditer(r'^import .+$', content, flags=re.MULTILINE)]
            if last_import:
                insert_pos = last_import[-1].end()
                import_string = '\n' + '\n'.join(imports_to_add)
                content = content[:insert_pos] + import_string + content[insert_pos:]
            else:
                pkg_match = re.search(r'^package .+$', content, flags=re.MULTILINE)
                if pkg_match:
                    insert_pos = pkg_match.end()
                    import_string = '\n\n' + '\n'.join(imports_to_add)
                    content = content[:insert_pos] + import_string + content[insert_pos:]
                    
        file_contents[f] = content
        
    # Process Manifest
    with open(MANIFEST_PATH, 'r', encoding='utf-8') as m_file:
        manifest_content = m_file.read()
        
    for f, fd in file_destinations.items():
        class_name = fd['class_name']
        if class_name.endswith('Activity') or class_name == 'SignUp':
            old_name = f'".{class_name}"'
            new_name = f'".{fd["category"]}.{class_name}"'
            manifest_content = manifest_content.replace(old_name, new_name)
            
            old_full = f'"com.example.homehub.{class_name}"'
            new_full = f'"com.example.homehub.{fd["category"]}.{class_name}"'
            manifest_content = manifest_content.replace(old_full, new_full)

    if "HomeHubApplication.kt" in file_destinations:
        fd = file_destinations["HomeHubApplication.kt"]
        old_app = f'".{fd["class_name"]}"'
        new_app = f'".{fd["category"]}.{fd["class_name"]}"'
        manifest_content = manifest_content.replace(old_app, new_app)
            
    with open(MANIFEST_PATH, 'w', encoding='utf-8') as m_file:
        m_file.write(manifest_content)
        
    # Process Layout XMLs
    if os.path.exists(LAYOUT_DIR):
        xml_files = [os.path.join(LAYOUT_DIR, fx) for fx in os.listdir(LAYOUT_DIR) if fx.endswith('.xml')]
        for xml_file in xml_files:
            with open(xml_file, 'r', encoding='utf-8') as fx_file:
                xml_content = fx_file.read()
                
            modified = False
            for f, fd in file_destinations.items():
                class_name = fd['class_name']
                old_pkg = f"com.example.homehub.{class_name}"
                new_pkg = f"{fd['new_package']}.{class_name}"
                
                if old_pkg in xml_content:
                    xml_content = xml_content.replace(old_pkg, new_pkg)
                    modified = True
                    
                old_context = f'tools:context=".{class_name}"'
                new_context = f'tools:context=".{fd["category"]}.{class_name}"'
                if old_context in xml_content:
                    xml_content = xml_content.replace(old_context, new_context)
                    modified = True
                    
            if modified:
                with open(xml_file, 'w', encoding='utf-8') as fx_file:
                    fx_file.write(xml_content)

    # Write files out
    for f, fd in file_destinations.items():
        with open(fd['new_path'], 'w', encoding='utf-8') as file:
            file.write(file_contents[f])
        os.remove(fd['old_path'])
        
    print("Refactoring completed successfully")

if __name__ == '__main__':
    main()
