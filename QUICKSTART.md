# ⚡ QuickStart - Email Update Setup

## 🎯 Just Run This ONE Command:

```
RUN_ME_FIRST.bat
```

## 📋 What It Does:

1. **Checks for service account key** 
   - If missing → Opens Firebase Console to get it
   - If found → Starts email update immediately

2. **Guides you through getting the key** (only if needed)
   - Click "Generate new private key"
   - Download JSON file
   - Save as `serviceAccountKey.json` in HomeHub folder

3. **Runs email update automatically**
   - Updates: `@host.com` → `@caretaker.com`
   - Creates backup
   - Shows results

## ✅ Steps:

### Step 1: Double-click `RUN_ME_FIRST.bat`

### Step 2: Follow the prompts
- If it asks for service account key → Firefox/Chrome opens
- Generate the key and save it to HomeHub folder

### Step 3: Script finishes
- Emails get updated
- Backup created
- Done!

## 🆘 If It Doesn't Work:

**Issue: "System cannot find the path"**
- Make sure you're running from: `C:\Users\USER\Desktop\HomeHub`
- Double-click `RUN_ME_FIRST.bat` from Windows Explorer

**Issue: "Service account key not found"**
- Check if file is downloaded to `C:\Users\USER\Desktop\HomeHub`
- Make sure it's named exactly: `serviceAccountKey.json`

**Issue: "Python not found"**
- Windows will ask to install Python → Click "Install"
- Or manually: `python setup_firebase.py`

That's it! 🎉