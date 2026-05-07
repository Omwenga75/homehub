
import re

with open(r'c:\Users\USER\Desktop\HomeHub\app\src\main\res\layout\activity_admin_dashboard.xml', 'r', encoding='utf-8') as f:
    lines = f.readlines()

nesting_level = 0
for i, line in enumerate(lines):
    open_tags = len(re.findall(r'<LinearLayout(?!\w)', line))
    close_tags = len(re.findall(r'</LinearLayout>', line))
    
    if open_tags > 0 or close_tags > 0:
        nesting_level += open_tags
        nesting_level -= close_tags
        print(f"Line {i+1:4}: {open_tags} open, {close_tags} close -> Nesting: {nesting_level}")

print(f"Final Nesting Level: {nesting_level}")
