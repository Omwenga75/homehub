# Firebase Email Update Tool

This Python script updates user email domains in your Firebase Firestore database.

## Quick Setup

### Option 1: Automated Setup (Recommended)
```bash
python setup_firebase.py
```

### Option 2: Manual Setup
1. **Install Python** (if not installed)
2. **Install Firebase Admin SDK**:
   ```bash
   pip install firebase-admin
   ```
3. **Get Firebase Service Account Key** (see below)

## Firebase Service Account Key Setup

### Step-by-Step Instructions:
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project: `homehub-588b9`
3. Navigate to **Project Settings** → **Service Accounts**
4. Click **"Generate new private key"**
5. Download the JSON file
6. Save it as `serviceAccountKey.json` in your project root

### Testing Your Setup:
```bash
# Test Firebase connection
python update_emails.py --test-connection

# Preview email changes (safe)
python update_emails.py --dry-run
```

## Usage

### Basic Commands
```bash
# Preview changes without applying them
python update_emails.py --dry-run

# Apply changes with automatic backup
python update_emails.py --backup

# Custom domains
python update_emails.py --old-domain @old.com --new-domain @new.com

# Use custom service account key
python update_emails.py --key-path path/to/your/key.json
```

### Advanced Options
```bash
# Test connection only
python update_emails.py --test-connection

# Full safety mode with backup
python update_emails.py --dry-run --backup --old-domain @host.com --new-domain @caretaker.com

# Help
python update_emails.py --help
```

## Troubleshooting

### "Service account key file not found"
- Ensure you've downloaded the key from Firebase Console
- Save it as `serviceAccountKey.json` in the project root
- Or specify the path: `--key-path path/to/key.json`

### "Invalid service account credentials"
- Regenerate your service account key (old keys may expire)
- Ensure the key is for the correct project (`homehub-588b9`)
- Check that the JSON file wasn't corrupted during download

### "Connection test failed"
- Verify Firebase Console permissions
- Check Firestore security rules
- Ensure network connectivity
- Try regenerating the service account key

### "No module named 'firebase_admin'"
- Install the Firebase Admin SDK: `pip install firebase-admin`
- Or run the setup script: `python setup_firebase.py`

### Common Issues
- **"Project ID mismatch"**: Ensure your key is for `homehub-588b9`
- **"Permission denied"**: Check Firebase Console IAM permissions
- **"Network timeout"**: Check internet connection and firewall settings

## Safety Features

- **🔍 Dry Run Mode**: Preview all changes before applying
- **💾 Automatic Backups**: Optional backup creation before changes
- **✅ Email Validation**: Validates email format before processing
- **📊 Progress Tracking**: Real-time status updates
- **🛡️ Error Handling**: Comprehensive error reporting and recovery

## Example Output

```
🔧 Firebase Email Update Tool
==================================================
📍 Project: homehub-588b9
🔑 Key Path: serviceAccountKey.json
✅ Service account key validated for project: homehub-588b9
🔗 Initializing Firebase with key: serviceAccountKey.json
✅ Firebase initialized successfully

📧 Email Update Operation
------------------------------
📊 Summary:
   Emails updated: 5

📊 Final Summary
--------------------
✅ Successfully updated 5 email addresses!
🎉 Operation completed without errors!
```

## File Structure

```
HomeHub/
├── update_emails.py          # Main email update script
├── setup_firebase.py         # Automated setup helper
├── EMAIL_UPDATE_README.md    # This documentation
├── serviceAccountKey.json    # Firebase service account key (you provide)
└── users_backup_*.json       # Automatic backups (created when using --backup)
```

## Security Notes

- Never commit `serviceAccountKey.json` to version control
- Add it to `.gitignore`
- Regenerate keys periodically for security
- Store keys securely and limit access

## Support

If you encounter issues:
1. Run `python update_emails.py --test-connection` to diagnose connection problems
2. Check the troubleshooting section above
3. Ensure your Firebase project is active and accessible
4. Verify your service account has Firestore permissions

## Command Line Options

- `--dry-run`: Preview changes without applying them
- `--old-domain`: Old email domain to replace (default: @host.com)
- `--new-domain`: New email domain (default: @caretaker.com)
- `--backup`: Create backup before making changes
- `--key-path`: Path to Firebase service account key (default: serviceAccountKey.json)

## Safety Features

- **Dry Run Mode**: Test changes before applying
- **Backup Creation**: Optional backup of user data
- **Error Handling**: Comprehensive error reporting
- **Email Validation**: Basic email format validation
- **Progress Tracking**: Real-time update status

## Example Output

```
🔧 Firebase Email Update Tool
========================================
✅ Firebase initialized successfully
✅ Backup created: users_backup_20240108_143022.json

⚡ LIVE UPDATE MODE
Updating emails from '@host.com' to '@caretaker.com'
✅ Updated: john@host.com -> john@caretaker.com
✅ Updated: jane@host.com -> jane@caretaker.com

📊 Summary:
   Emails updated: 2

✅ Email update completed successfully!
```

## Troubleshooting

### "Service account key file not found"
- Ensure you've downloaded the service account key from Firebase Console
- Place it in the project root as `serviceAccountKey.json`
- Or specify the path with `--key-path`

### "Invalid JSON in service account key file"
- Re-download the service account key from Firebase Console
- Ensure the file wasn't corrupted during download

### Permission Errors
- Ensure your service account has Firestore read/write permissions
- Check that you're using the correct Firebase project</content>
<parameter name="filePath">c:\Users\USER\Desktop\HomeHub\EMAIL_UPDATE_README.md