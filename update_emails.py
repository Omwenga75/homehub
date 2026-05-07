import firebase_admin
from firebase_admin import credentials, firestore
import os
import sys
import json
from datetime import datetime
import argparse

# Default Firebase project configuration
DEFAULT_PROJECT_ID = "homehub-588b9"
DEFAULT_KEY_PATH = "serviceAccountKey.json"

def check_existing_keys():
    """Check for existing service account keys in common locations"""
    possible_paths = [
        "serviceAccountKey.json",
        "firebase-service-account.json",
        "homehub-service-account.json",
        f"{DEFAULT_PROJECT_ID}-firebase-adminsdk.json",
        "app/serviceAccountKey.json",
        "config/serviceAccountKey.json"
    ]

    found_keys = []
    for path in possible_paths:
        if os.path.exists(path):
            found_keys.append(path)

    return found_keys

def validate_service_account_key(key_path):
    """Validate that the service account key file exists and is valid JSON"""
    if not os.path.exists(key_path):
        print(f"[ERROR] Service account key file not found at '{key_path}'")
        print("\nChecking for existing keys...")
        existing_keys = check_existing_keys()
        if existing_keys:
            print(f"Found potential keys: {', '.join(existing_keys)}")
            print(f"Try: python update_emails.py --key-path {existing_keys[0]}")
        else:
            print("No service account keys found in common locations.")

        print("\nTo get the service account key:")
        print("1. Go to https://console.firebase.google.com/")
        print(f"2. Select project: {DEFAULT_PROJECT_ID}")
        print("3. Go to Project Settings > Service Accounts")
        print("4. Click 'Generate new private key'")
        print("5. Download the JSON file and save it in the project root")
        print("6. Run: python update_emails.py --key-path path/to/your/key.json")
        return False

    try:
        with open(key_path, 'r') as f:
            key_data = json.load(f)

        # Validate required fields
        required_fields = ['type', 'project_id', 'private_key_id', 'private_key', 'client_email']
        missing_fields = [field for field in required_fields if field not in key_data]

        if missing_fields:
            print(f"[ERROR] Service account key missing required fields: {', '.join(missing_fields)}")
            return False

        # Validate project ID matches
        if key_data.get('project_id') != DEFAULT_PROJECT_ID:
            print(f"[WARNING] Key project_id '{key_data.get('project_id')}' doesn't match expected '{DEFAULT_PROJECT_ID}'")
            print("   This might cause authentication issues.")

        print(f"[SUCCESS] Service account key validated for project: {key_data.get('project_id')}")
        return True

    except json.JSONDecodeError as e:
        print(f"[ERROR] Invalid JSON in service account key file '{key_path}': {e}")
        return False
    except Exception as e:
        print(f"[ERROR] Error reading service account key: {e}")
        return False

def initialize_firebase(service_account_path):
    """Initialize Firebase Admin SDK with detailed error handling"""
    try:
        print(f"Initializing Firebase with key: {service_account_path}")

        # Check if Firebase is already initialized
        if firebase_admin._apps:
            print("[WARNING] Firebase already initialized, using existing app")
            return firestore.client()

        cred = credentials.Certificate(service_account_path)
        firebase_admin.initialize_app(cred)
        print("[SUCCESS] Firebase initialized successfully")
        return firestore.client()

    except FileNotFoundError:
        print(f"[ERROR] Service account key file not found: {service_account_path}")
        return None
    except ValueError as e:
        print(f"[ERROR] Invalid service account credentials: {e}")
        print("This usually means:")
        print("   - The key file is corrupted or invalid")
        print("   - The key is for a different project")
        print("   - The service account has been deleted")
        return None
    except Exception as e:
        print(f"[ERROR] Error initializing Firebase: {e}")
        print("Common solutions:")
        print("   - Regenerate the service account key")
        print("   - Check Firebase Console permissions")
        print("   - Verify the key file wasn't modified")
        return None

def backup_users_data(db, backup_file):
    """Create a backup of current user data"""
    try:
        users_docs = db.collection('users').stream()
        backup_data = {}

        for doc in users_docs:
            data = doc.to_dict()
            backup_data[doc.id] = data

        with open(backup_file, 'w') as f:
            json.dump(backup_data, f, indent=2, default=str)

        print(f"[SUCCESS] Backup created: {backup_file}")
        return True
    except Exception as e:
        print(f"[ERROR] Error creating backup: {e}")
        return False

def validate_email(email):
    """Basic email validation"""
    return '@' in email and '.' in email

def update_host_emails(db, dry_run=False, old_domain='@host.com', new_domain='@caretaker.com'):
    """Update user emails from old domain to new domain"""
    print(f"\n{'[DRY RUN MODE]' if dry_run else '[LIVE UPDATE MODE]'}")
    print(f"Updating emails from '{old_domain}' to '{new_domain}'")

    try:
        users_docs = db.collection('users').stream()
        updated_count = 0
        errors = []

        for doc in users_docs:
            try:
                data = doc.to_dict()
                email = data.get('email', '')

                if not email:
                    continue

                if not validate_email(email):
                    errors.append(f"Invalid email format: {email}")
                    continue

                if old_domain in email:
                    new_email = email.replace(old_domain, new_domain)

                    if dry_run:
                        print(f"Would update: {email} -> {new_email}")
                    else:
                        doc.reference.update({'email': new_email})
                        print(f"[SUCCESS] Updated: {email} -> {new_email}")

                    updated_count += 1

            except Exception as e:
                errors.append(f"Error processing user {doc.id}: {e}")

        print(f"\n Summary:")
        print(f"   Emails {'would be' if dry_run else ''} updated: {updated_count}")

        if errors:
            print(f"   Errors encountered: {len(errors)}")
            for error in errors[:5]:  # Show first 5 errors
                print(f"     - {error}")
            if len(errors) > 5:
                print(f"     ... and {len(errors) - 5} more errors")

        return updated_count, errors

    except Exception as e:
        print(f"[ERROR] Error during email update: {e}")
        return 0, [str(e)]

def test_firebase_connection(db):
    """Test Firebase connection by reading a small amount of data"""
    try:
        print("Testing Firebase connection...")

        # Try to read from users collection
        users_ref = db.collection('users')
        docs = users_ref.limit(1).stream()

        user_count = 0
        for doc in docs:
            user_count += 1
            user_data = doc.to_dict()
            email = user_data.get('email', 'No email')
            print(f"[SUCCESS] Connection successful! Found user: {email}")

        if user_count == 0:
            print("[SUCCESS] Connection successful! (No users found in collection)")

        return True

    except Exception as e:
        print(f"[ERROR] Connection test failed: {e}")
        print("This might indicate:")
        print("   - Firestore security rules blocking access")
        print("   - Service account lacks proper permissions")
        print("   - Network connectivity issues")
        return False

def main():
    parser = argparse.ArgumentParser(
        description='Update user emails in Firebase Firestore',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python update_emails.py --test-connection    # Test Firebase connection
  python update_emails.py --dry-run           # Preview email changes
  python update_emails.py --backup            # Update with backup
  python update_emails.py --key-path custom-key.json  # Use custom key file
        """
    )
    parser.add_argument('--dry-run', action='store_true', help='Preview changes without applying them')
    parser.add_argument('--old-domain', default='@host.com', help='Old email domain to replace (default: @host.com)')
    parser.add_argument('--new-domain', default='@caretaker.com', help='New email domain (default: @caretaker.com)')
    parser.add_argument('--backup', action='store_true', help='Create backup before making changes')
    parser.add_argument('--key-path', default=DEFAULT_KEY_PATH, help=f'Path to Firebase service account key (default: {DEFAULT_KEY_PATH})')
    parser.add_argument('--test-connection', action='store_true', help='Test Firebase connection and exit')
    parser.add_argument('--project-id', default=DEFAULT_PROJECT_ID, help=f'Firebase project ID (default: {DEFAULT_PROJECT_ID})')

    args = parser.parse_args()

    print("Firebase Email Update Tool")
    print("=" * 50)
    print(f"Project: {args.project_id}")
    print(f"Key Path: {args.key_path}")

    # Validate service account key
    if not validate_service_account_key(args.key_path):
        print("\n[ERROR] Cannot proceed without valid service account key.")
        print("Follow the setup instructions above to get your key.")
        sys.exit(1)

    # Initialize Firebase
    db = initialize_firebase(args.key_path)
    if not db:
        print("\n[ERROR] Cannot proceed without Firebase connection.")
        print("Check your service account key and Firebase Console permissions.")
        sys.exit(1)

    # Test connection if requested
    if args.test_connection:
        print("\nTesting Firebase Connection")
        print("-" * 30)
        if test_firebase_connection(db):
            print("\n[SUCCESS] Firebase connection test passed!")
            print("Setup is working correctly.")
        else:
            print("\n[ERROR] Firebase connection test failed!")
            print("Please check your setup and try again.")
        return

    # Create backup if requested
    if args.backup and not args.dry_run:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        backup_file = f"users_backup_{timestamp}.json"
        print(f"\nSaving backup: {backup_file}")
        if not backup_users_data(db, backup_file):
            print("[WARNING] Backup failed. Continue anyway? (y/N): ", end='')
            try:
                if input().lower() != 'y':
                    print("Operation cancelled.")
                    sys.exit(1)
            except KeyboardInterrupt:
                print("\nOperation cancelled.")
                sys.exit(1)

    # Perform email update
    print(f"\n Email Update Operation")
    print("-" * 30)
    updated_count, errors = update_host_emails(
        db, args.dry_run, args.old_domain, args.new_domain
    )

    # Final status
    print(f"\n Final Summary")
    print("-" * 20)
    if args.dry_run:
        print("This was a dry run. No changes were made.")
        print("   Run without --dry-run to apply the changes.")
    else:
        if updated_count > 0:
            print(f"[SUCCESS] Successfully updated {updated_count} email addresses!")
        else:
            print("No emails needed updating.")

        if errors:
            print(f"[WARNING] {len(errors)} errors occurred during the process.")
            print("   Check the output above for details.")
        else:
            print("Operation completed without errors!")

if __name__ == "__main__":
    main()