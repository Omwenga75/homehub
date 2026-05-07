import firebase_admin
from firebase_admin import credentials, firestore
import datetime

# Initialize Firebase Admin SDK
# To get the service account key:
# 1. Go to Firebase Console > Your Project > Project Settings > Service Accounts
# 2. Click "Generate new private key"
# 3. Download the JSON file and place it in the project root
# 4. Update the path below
cred = credentials.Certificate('serviceAccountKey.json')  # Update this path
firebase_admin.initialize_app(cred)

db = firestore.client()

# Define the cutoff date: 2 days ago from today (April 8, 2026)
cutoff_date = datetime.datetime(2026, 4, 6)

collections_to_clean = [
    'properties',
    'notifications',
    'activityLog',
    'verificationRequests',
    'verifiedCaretakers',
    'hostApplications',
    'chatRooms',
    'caretakerApplications',
    'bookings',
    'caretakerStatusLogs',
    'caretakerVerificationLogs'
]

def delete_old_documents(collection_name, cutoff):
    print(f"Cleaning collection: {collection_name}")
    docs = db.collection(collection_name).stream()
    deleted_count = 0
    for doc in docs:
        data = doc.to_dict()
        # Check for various timestamp fields
        timestamp = None
        for field in ['createdAt', 'timestamp', 'submittedAt', 'updatedAt', 'changedAt', 'lastMessageTime']:
            if field in data:
                ts = data[field]
                if isinstance(ts, datetime.datetime):
                    timestamp = ts
                elif hasattr(ts, 'toDate'):  # Firestore Timestamp
                    timestamp = ts.toDate()
                break
        if timestamp and timestamp < cutoff:
            doc.reference.delete()
            deleted_count += 1
    print(f"Deleted {deleted_count} documents from {collection_name}")

# Clean users: keep admins, delete others older than cutoff
print("Cleaning users collection")
users_docs = db.collection('users').stream()
deleted_users = 0
kept_admins = 0
for doc in users_docs:
    data = doc.to_dict()
    role = data.get('role', '').lower()
    if 'admin' in role:  # Keep if role contains 'admin'
        kept_admins += 1
        continue  # Keep admins
    # For non-admins, check createdAt
    created_at = data.get('createdAt')
    if created_at:
        if hasattr(created_at, 'toDate'):
            created_at = created_at.toDate()
        if isinstance(created_at, datetime.datetime) and created_at < cutoff_date:
            doc.reference.delete()
            deleted_users += 1
print(f"Kept {kept_admins} admin users, deleted {deleted_users} old non-admin users")

# Clean other collections
for coll in collections_to_clean:
    delete_old_documents(coll, cutoff_date)

print("Data cleaning completed.")