const fs = require('fs');
const path = require('path');

const BASE_DIR = "C:\\Users\\USER\\Desktop\\HomeHub\\app\\src\\main\\java\\com\\example\\homehub";
const MANIFEST_PATH = "C:\\Users\\USER\\Desktop\\HomeHub\\app\\src\\main\\AndroidManifest.xml";
const LAYOUT_DIR = "C:\\Users\\USER\\Desktop\\HomeHub\\app\\src\\main\\res\\layout";

const CATEGORIES = {
    'auth': ['SignUp', 'UserLogin', 'PhoneAuth', 'RoleSelection', 'Splash', 'PrivateAccess'],
    'admin': ['Admin', 'ManageUsers', 'VerifiedUsers', 'SuspendedUsers'],
    'caretaker': ['Caretaker', 'ManageCaretakers', 'MyProperties'],
    'student': ['Student', 'ManageStudents'],
    'supplier': ['WaterSupplier', 'Supplier', 'AddWaterService'],
    'property': ['Property', 'Properties', 'House', 'Room', 'Amenities', 'Filter', 'Location', 'Map', 'MapLocation'],
    'chat': ['Chat', 'Message'],
    'billing': ['Booking', 'Bookings', 'Payment', 'Rent', 'Mpesa'],
    'notification': ['Notification'],
    'core': ['Application', 'Review', 'Settings', 'Analytics', 'RecentActivity', 'Maintenance', 'Report', 'Image', 'Bitmap', 'Favorites', 'Session', 'Theme', 'Validation', 'Extensions', 'Encryption', 'DocumentVerificationBottomSheet', 'KenyanIdValidator']
};

function getCategoryForFile(filename) {
    let name = filename.replace('.kt', '');
    for (const [cat, keywords] of Object.entries(CATEGORIES)) {
        for (const kw of keywords) {
            if (name.startsWith(kw) || name.includes(kw)) {
                if (name.includes('Admin')) return 'admin';
                if (name.includes('Caretaker')) return 'caretaker';
                return cat;
            }
        }
    }
    if (name.endsWith('Adapter')) return 'adapters';
    else if (name.endsWith('Activity') || name.endsWith('Fragment')) return 'activities';
    else if (name.endsWith('Manager') || name.endsWith('Service') || name.endsWith('Helper') || name.endsWith('Utils')) return 'utils';
    else return 'models';
}

function processRefactor() {
    if (!fs.existsSync(BASE_DIR)) {
        console.log("BASE_DIR not found");
        return;
    }

    let files = fs.readdirSync(BASE_DIR).filter(f => f.endsWith('.kt') && fs.statSync(path.join(BASE_DIR, f)).isFile());
    
    let fileDestinations = {};
    for (let f of files) {
        let cat = getCategoryForFile(f);
        let className = f.replace('.kt', '');
        fileDestinations[f] = {
            category: cat,
            oldPath: path.join(BASE_DIR, f),
            newPath: path.join(BASE_DIR, cat, f),
            className: className,
            newPackage: `com.example.homehub.${cat}`
        };
    }

    for (let f in fileDestinations) {
        let dir = path.dirname(fileDestinations[f].newPath);
        if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    }

    let fileContents = {};
    for (let f in fileDestinations) {
        fileContents[f] = fs.readFileSync(fileDestinations[f].oldPath, 'utf8');
    }

    for (let [f, fd] of Object.entries(fileDestinations)) {
        let content = fileContents[f];
        
        content = content.replace(/^package com\.example\.homehub\s*$/m, `package ${fd.newPackage}`);
        
        if (!content.includes('import com.example.homehub.R')) {
            let pkgMatch = content.match(/^package .+$/m);
            if (pkgMatch) {
                let insertPos = pkgMatch.index + pkgMatch[0].length;
                content = content.slice(0, insertPos) + '\n\nimport com.example.homehub.R' + content.slice(insertPos);
            }
        }

        let importsToAdd = new Set();
        for (let [otherF, otherFd] of Object.entries(fileDestinations)) {
            if (otherF === f) continue;
            if (otherFd.category === fd.category) continue;
            
            if (content.includes(otherFd.className)) {
                let regex = new RegExp(`\\b${otherFd.className}\\b`, 'g');
                if (regex.test(content)) {
                    let imp = `import ${otherFd.newPackage}.${otherFd.className}`;
                    if (!content.includes(imp)) {
                        importsToAdd.add(imp);
                    }
                }
            }
        }

        if (importsToAdd.size > 0) {
            let importString = '\n' + Array.from(importsToAdd).join('\n');
            let lastImportMatch = [...content.matchAll(/^import .+$/gm)].pop();
            
            if (lastImportMatch) {
                let insertPos = lastImportMatch.index + lastImportMatch[0].length;
                content = content.slice(0, insertPos) + importString + content.slice(insertPos);
            } else {
                let pkgMatch = content.match(/^package .+$/m);
                if (pkgMatch) {
                    let insertPos = pkgMatch.index + pkgMatch[0].length;
                    content = content.slice(0, insertPos) + '\n' + importString + content.slice(insertPos);
                }
            }
        }
        
        fileContents[f] = content;
    }

    let manifestContent = fs.readFileSync(MANIFEST_PATH, 'utf8');
    for (let [f, fd] of Object.entries(fileDestinations)) {
        let className = fd.className;
        if (className.endsWith('Activity') || className === 'SignUp') {
            manifestContent = manifestContent.split(`".${className}"`).join(`".${fd.category}.${className}"`);
            manifestContent = manifestContent.split(`"com.example.homehub.${className}"`).join(`"com.example.homehub.${fd.category}.${className}"`);
        }
        if (className === 'HomeHubApplication') {
            manifestContent = manifestContent.split(`".${className}"`).join(`".${fd.category}.${className}"`);
        }
    }
    fs.writeFileSync(MANIFEST_PATH, manifestContent, 'utf8');

    if (fs.existsSync(LAYOUT_DIR)) {
        let xmlFiles = fs.readdirSync(LAYOUT_DIR).filter(f => f.endsWith('.xml')).map(f => path.join(LAYOUT_DIR, f));
        for (let xmlFile of xmlFiles) {
            let xmlContent = fs.readFileSync(xmlFile, 'utf8');
            let modified = false;
            
            for (let [f, fd] of Object.entries(fileDestinations)) {
                let className = fd.className;
                let oldPkg = `com.example.homehub.${className}`;
                let newPkg = `${fd.newPackage}.${className}`;
                
                if (xmlContent.includes(oldPkg)) {
                    xmlContent = xmlContent.split(oldPkg).join(newPkg);
                    modified = true;
                }
                
                let oldContext = `tools:context=".${className}"`;
                let newContext = `tools:context=".${fd.category}.${className}"`;
                if (xmlContent.includes(oldContext)) {
                    xmlContent = xmlContent.split(oldContext).join(newContext);
                    modified = true;
                }
            }
            if (modified) {
                fs.writeFileSync(xmlFile, xmlContent, 'utf8');
            }
        }
    }

    for (let [f, fd] of Object.entries(fileDestinations)) {
        fs.writeFileSync(fd.newPath, fileContents[f], 'utf8');
        fs.unlinkSync(fd.oldPath);
    }
    
    console.log("Refactoring completed successfully.");
}

processRefactor();
