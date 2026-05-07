import re

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find the <!-- Hero Action Card: ... -->
    hero_match = re.search(r'(?s)(\s*<!-- Hero Action Card:[^\n]*?-->.*?app:strokeWidth="0dp">.*?</com\.google\.android\.material\.card\.MaterialCardView>)', content)
    
    if not hero_match:
        print(f"Hero card not found in {filepath}!")
        return
        
    hero_block = hero_match.group(1)
    
    # Remove the hero block from its original position
    new_content = content.replace(hero_block, '', 1)
    
    # Insert it before the </com.google.android.material.appbar.AppBarLayout>
    if '</com.google.android.material.appbar.AppBarLayout>' in new_content:
        new_content = new_content.replace('</com.google.android.material.appbar.AppBarLayout>', hero_block + '\n    </com.google.android.material.appbar.AppBarLayout>', 1)
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)
        print(f"Successfully updated {filepath}")
    else:
        print(f"Could not find AppBarLayout closing tag in {filepath}")

process_file(r'app\src\main\res\layout\activity_caretaker_dashboard.xml')
process_file(r'app\src\main\res\layout\activity_water_supplier_dashboard.xml')
