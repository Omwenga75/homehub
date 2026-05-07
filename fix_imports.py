import os
import re

BASE = r"C:\Users\USER\Desktop\HomeHub\app\src\main\java\com\example\homehub"

# Map class names to their correct domain packages
CLASS_MAP = {
    # Models
    "Student": "student",
    "Caretaker": "caretaker",
    "Booking": "billing",
    "Room": "property",
    "RoomType": "property",
    "Property": "property",
    "Review": "property",
    "StatusEntry": "property",
    "House": "property",
    "Feature": "property",
    "Category": "property",
    "FilterData": "property",
    "ChatRoom": "chat",
    "Chat": "chat",
    "Message": "chat",
    "LocationDetails": "property",
    
    # Adapters
    "StudentAdapter": "admin",
    "CaretakersAdapter": "admin",
    "ApplicationsAdapter": "admin",
    "RoomAdapter": "property",
    "ChatRoomAdapter": "chat",
    "ChatManager": "chat",
    "UnifiedPropertyAdapter": "property",
    "BookingsAdapter": "billing",
    "MyPropertiesAdapter": "property",
    
    # Activities
    "AdminDashboardActivity": "admin",
    "StudentDashboardActivity": "student",
    "CaretakerDashboardActivity": "caretaker",
    "WaterSupplierDashboardActivity": "supplier",
    "PropertyDetailsActivity": "property",
    "CaretakerApplicationActivity": "caretaker",
    
    # Managers/Utils
    "SessionManager": "auth",
    "AdminSessionManager": "admin",
    "BitmapResizer": "utils",
    "ValidationUtils": "utils",
    "ImageWatermarkUtils": "utils",
    "KYCUploadWorker": "utils",
    "HybridPropertyManager": "utils",
    "HybridProfileManager": "utils",
    "ReportGenerator": "admin",
    "EncryptionUtils": "utils",
    "ThemeHelper": "utils",
    "ProfilePictureUtils": "utils",
    
    # ViewModels
    "CaretakerApplicationViewModel": "caretaker",
}

fixed_count = 0
files_modified = []

for domain in ["admin", "auth", "billing", "caretaker", "chat", "other", "property", "student", "supplier", "utils"]:
    domain_dir = os.path.join(BASE, domain)
    if not os.path.exists(domain_dir):
        continue
    for f in os.listdir(domain_dir):
        if not f.endswith(".kt"):
            continue
        filepath = os.path.join(domain_dir, f)
        with open(filepath, "r", encoding="utf-8") as file:
            content = file.read()
        
        original = content
        for class_name, target_pkg in CLASS_MAP.items():
            old_import = f"import com.example.homehub.{class_name}"
            new_import = f"import com.example.homehub.{target_pkg}.{class_name}"
            # Only replace exact root-level imports (not already in a subpackage)
            if old_import in content and new_import not in content:
                content = content.replace(old_import, new_import)
                fixed_count += 1
        
        if content != original:
            with open(filepath, "w", encoding="utf-8") as file:
                file.write(content)
            files_modified.append(f"{domain}/{f}")

print(f"Fixed {fixed_count} imports across {len(files_modified)} files:")
for f in files_modified:
    print(f"  - {f}")
