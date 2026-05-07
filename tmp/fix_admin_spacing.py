from pathlib import Path

path = Path('app/src/main/res/layout/activity_admin_dashboard.xml')
text = path.read_text(encoding='utf-8')
replacements = [
    ('android:layout_marginTop="12dp"\n            app:cardBackgroundColor="@color/primary_dark"', 'android:layout_marginTop="16dp"\n            app:cardBackgroundColor="@color/primary_dark"'),
    ('android:layout_marginBottom="0dp"\n            app:cardCornerRadius="20dp"', 'android:layout_marginBottom="16dp"\n            app:cardCornerRadius="20dp"'),
    ('android:layout_marginTop="-16dp"', 'android:layout_marginTop="8dp"'),
    ('android:layout_marginTop="-12dp"', 'android:layout_marginTop="8dp"'),
    ('android:paddingBottom="40dp"', 'android:paddingBottom="24dp"'),
    ('android:layout_marginTop="12dp"', 'android:layout_marginTop="10dp"'),
    ('android:paddingVertical="24dp"', 'android:paddingVertical="16dp"'),
    ('android:layout_marginBottom="12dp"', 'android:layout_marginBottom="8dp"'),
    ('android:layout_height="160dp"', 'android:layout_height="140dp"'),
]
for old, new in replacements:
    text = text.replace(old, new)
path.write_text(text, encoding='utf-8')
print('updated')
