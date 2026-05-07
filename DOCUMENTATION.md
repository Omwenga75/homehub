# HomeHub - Professional Smart Living Ecosystem

## 📋 Project Overview
HomeHub is a sophisticated, multi-role Android application designed to streamline property management, caretaker services, and student living. It integrates a robust Firebase backend with a modular Kotlin-based frontend to provide a seamless experience for all stakeholders in the residential ecosystem.

---

## 🏗️ Technical Architecture
- **Platform:** Android (Kotlin)
- **Backend:** Firebase (Authentication, Real-time Database, Storage)
- **Architecture:** Modular Package-by-Feature (Admin, Auth, Billing, Caretaker, Chat, Property, Student, Supplier)
- **Automation:** Python-based maintenance scripts for Firebase data management and email migrations.

---

## 👥 User Roles & Modules
The system is built around several key user personas, each with dedicated dashboards and functionality:

### 1. **Student / Resident**
- Property search and application.
- Rent payment and billing history.
- Maintenance request tracking.
- Direct communication with caretakers/suppliers.

### 2. **Caretaker**
- Maintenance task management.
- Resident communication.
- Property inspection logs.
- Emergency response coordination.

### 3. **Supplier**
- Service request fulfillment.
- Invoicing and payment tracking.
- Availability management.

### 4. **Administrator**
- User role management.
- System-wide monitoring and reporting.
- Financial oversight and billing reconciliation.

---

## 🚀 Key Features
- **Real-time Chat:** Instant communication between residents, caretakers, and service providers.
- **Billing System:** Integrated billing for rent and services with automated tracking.
- **Property Management:** Comprehensive listing and management of residential units.
- **Firebase Integration:** Secure authentication and real-time data synchronization.
- **Maintenance Workflow:** End-to-end tracking of service requests from reporting to completion.

---

## 🛠️ Maintenance & DevOps
The project includes a suite of specialized tools for developers:
- `RUN_ME_FIRST.bat`: Automated setup and Firebase initialization.
- `update_emails.py`: Scripted migration of user identities (e.g., domain updates).
- `setup_firebase.py`: Streamlined service account configuration.
- `refactor.py`: Utilities for project-wide code reorganization.

---

## 📖 Setup Instructions
1. **Clone the Repository**: Ensure all submodules and assets are present.
2. **Firebase Configuration**:
   - Place `serviceAccountKey.json` in the root directory.
   - Run `RUN_ME_FIRST.bat` to initialize the database environment.
3. **Android Studio**:
   - Open the project in Android Studio (Iguana or later).
   - Sync Gradle and build the project.
4. **Environment**:
   - Python 3.x is required for maintenance scripts.

---

## 📅 Version & Status
- **Current Version:** 1.2.0
- **Build Status:** Stable
- **Maintenance Status:** Active
