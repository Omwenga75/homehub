const fs = require('fs');

function processFile(filepath) {
    let content = fs.readFileSync(filepath, 'utf8');
    
    // Find the Hero block
    const heroRegex = /(\s*<!-- Hero Action Card:[^\n]*?-->.*?app:strokeWidth="0dp">.*?<\/com\.google\.android\.material\.card\.MaterialCardView>)/s;
    const match = content.match(heroRegex);
    
    if (!match) {
        console.log(`Hero card not found in ${filepath}`);
        return;
    }
    
    const heroBlock = match[1];
    
    // Remove the hero block
    content = content.replace(heroBlock, '');
    
    // Insert before AppBarLayout closing tag
    const target = '</com.google.android.material.appbar.AppBarLayout>';
    if (content.includes(target)) {
        content = content.replace(target, heroBlock + '\n    ' + target);
        fs.writeFileSync(filepath, content, 'utf8');
        console.log(`Successfully updated ${filepath}`);
    } else {
        console.log(`Could not find AppBarLayout closing tag in ${filepath}`);
    }
}

processFile('app/src/main/res/layout/activity_caretaker_dashboard.xml');
processFile('app/src/main/res/layout/activity_water_supplier_dashboard.xml');
