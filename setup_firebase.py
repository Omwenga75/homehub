#!/usr/bin/env python3
"""
Firebase Setup Helper for HomeHub
This script helps you set up Firebase authentication for your Python scripts.
"""

import os
import sys
import json
import subprocess

def check_python():
    """Check if Python and pip are available"""
    try:
        result = subprocess.run([sys.executable, '--version'], capture_output=True, text=True)
        print(f"✅ Python: {result.stdout.strip()}")

        result = subprocess.run([sys.executable, '-m', 'pip', '--version'], capture_output=True, text=True)
        if result.returncode == 0:
            print(f"✅ Pip: {result.stdout.split()[0]} {result.stdout.split()[1]}")
            return True
        else:
            print("❌ Pip not found")
            return False
    except Exception as e:
        print(f"❌ Error checking Python: {e}")
        return False

def install_firebase_admin():
    """Install Firebase Admin SDK"""
    try:
        print("📦 Installing Firebase Admin SDK...")
        subprocess.check_call([sys.executable, '-m', 'pip', 'install', 'firebase-admin'])
        print("✅ Firebase Admin SDK installed successfully")
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ Failed to install Firebase Admin SDK: {e}")
        return False

def check_service_account_key():
    """Check for existing service account keys"""
    possible_paths = [
        "serviceAccountKey.json",
        "firebase-service-account.json",
        "homehub-service-account.json",
        "homehub-588b9-firebase-adminsdk.json"
    ]

    found_keys = []
    for path in possible_paths:
        if os.path.exists(path):
            found_keys.append(path)

    if found_keys:
        print("📁 Found existing service account keys:")
        for i, key in enumerate(found_keys, 1):
            print(f"   {i}. {key}")
        return found_keys[0]  # Return first found key
    else:
        print("❌ No service account key found")
        return None

def test_firebase_connection(key_path):
    """Test Firebase connection"""
    try:
        import firebase_admin
        from firebase_admin import credentials, firestore

        print(f"🔗 Testing connection with: {key_path}")

        cred = credentials.Certificate(key_path)
        firebase_admin.initialize_app(cred)
        db = firestore.client()

        # Test read
        users_ref = db.collection('users')
        docs = users_ref.limit(1).stream()

        user_count = 0
        for doc in docs:
            user_count += 1

        print(f"✅ Firebase connection successful! Found {user_count} test user(s)")
        return True

    except Exception as e:
        print(f"❌ Firebase connection failed: {e}")
        return False

def main():
    print("🔧 Firebase Setup Helper for HomeHub")
    print("=" * 50)

    # Check Python
    if not check_python():
        print("\n❌ Python setup incomplete. Please install Python 3.6+ first.")
        return

    # Install Firebase Admin SDK
    if not install_firebase_admin():
        print("\n❌ Failed to install Firebase Admin SDK.")
        return

    # Check for service account key
    key_path = check_service_account_key()

    if not key_path:
        print("\n📋 To get your service account key:")
        print("1. Go to https://console.firebase.google.com/")
        print("2. Select project: homehub-588b9")
        print("3. Go to Project Settings > Service Accounts")
        print("4. Click 'Generate new private key'")
        print("5. Download the JSON file and save it as 'serviceAccountKey.json'")
        print("6. Run this script again")
        return

    # Test connection
    print(f"\n🧪 Testing Firebase connection with: {key_path}")
    if test_firebase_connection(key_path):
        print("\n🎉 Setup complete! You can now run your email update script:")
        print("   python update_emails.py --dry-run")
        print("   python update_emails.py --backup")
    else:
        print("\n❌ Connection test failed. Please check:")
        print("   - Service account key is valid")
        print("   - Firebase project permissions")
        print("   - Network connectivity")

if __name__ == "__main__":
    main()