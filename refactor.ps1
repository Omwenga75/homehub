$ErrorActionPreference = "Stop"
$BaseDir = "C:\Users\USER\Desktop\HomeHub\app\src\main\java\com\example\homehub"
$ManifestFile = "C:\Users\USER\Desktop\HomeHub\app\src\main\AndroidManifest.xml"
$LayoutDir = "C:\Users\USER\Desktop\HomeHub\app\src\main\res\layout"

$Categories = @{
    "auth" = @("SignUp", "UserLogin", "PhoneAuth", "RoleSelection", "Splash", "PrivateAccess")
    "admin" = @("Admin", "ManageUsers", "VerifiedUsers", "SuspendedUsers")
    "caretaker" = @("Caretaker", "ManageCaretakers", "MyProperties")
    "student" = @("Student", "ManageStudents")
    "supplier" = @("WaterSupplier", "Supplier", "AddWaterService")
    "property" = @("Property", "Properties", "House", "Room", "Amenities", "Filter", "Location", "Map", "MapLocation")
    "chat" = @("Chat", "Message")
    "billing" = @("Booking", "Bookings", "Payment", "Rent", "Mpesa")
    "notification" = @("Notification")
    "core" = @("Application", "Review", "Settings", "Analytics", "RecentActivity", "Maintenance", "Report", "Image", "Bitmap", "Favorites", "Session", "Theme", "Validation", "Extensions", "Encryption", "DocumentVerificationBottomSheet", "KenyanIdValidator")
}

function Get-CategoryForFile($Filename) {
    $name = $Filename -replace '\.kt$',''
    foreach ($cat in $Categories.Keys) {
        foreach ($kw in $Categories[$cat]) {
            if ($name.StartsWith($kw) -or $name.Contains($kw)) {
                if ($name.Contains('Admin')) { return 'admin' }
                if ($name.Contains('Caretaker')) { return 'caretaker' }
                return $cat
            }
        }
    }
    if ($name.EndsWith('Adapter')) { return 'adapters' }
    elseif ($name.EndsWith('Activity') -or $name.EndsWith('Fragment')) { return 'activities' }
    elseif ($name.EndsWith('Manager') -or $name.EndsWith('Service') -or $name.EndsWith('Helper') -or $name.EndsWith('Utils')) { return 'utils' }
    else { return 'models' }
}

$Files = Get-ChildItem -Path $BaseDir -Filter "*.kt" -File

$FileDestinations = @{}

foreach ($f in $Files) {
    if ($f.Name -match 'MainActivity') { continue }
    $cat = Get-CategoryForFile $f.Name
    $className = $f.Name -replace '\.kt$',''
    $newPkg = "com.example.homehub.$cat"
    $newPath = Join-Path (Join-Path $BaseDir $cat) $f.Name
    
    $FileDestinations[$f.Name] = @{
        category = $cat
        oldPath = $f.FullName
        newPath = $newPath
        className = $className
        newPackage = $newPkg
    }
}

foreach ($fd in $FileDestinations.Values) {
    $dir = Split-Path $fd.newPath
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
}

$FileContents = @{}
foreach ($f in $FileDestinations.Keys) {
    $FileContents[$f] = [IO.File]::ReadAllText($FileDestinations[$f].oldPath, [System.Text.Encoding]::UTF8)
}

foreach ($f in $FileDestinations.Keys) {
    $fd = $FileDestinations[$f]
    $content = $FileContents[$f]
    
    # 1. Update Package
    $content = $content -replace '(?m)^package com\.example\.homehub\s*$', "package $($fd.newPackage)"
    
    # 2. Add R import
    if (-not $content.Contains("import com.example.homehub.R")) {
        $content = $content -replace '(?m)^package .+', "$&`n`nimport com.example.homehub.R"
    }

    # 3. Add necessary imports
    $ImportsToAdd = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($otherF in $FileDestinations.Keys) {
        if ($otherF -eq $f) { continue }
        $otherFd = $FileDestinations[$otherF]
        if ($otherFd.category -eq $fd.category) { continue }
        
        $cName = $otherFd.className
        if ($content.Contains($cName)) {
            if ($content -match "\b$cName\b") {
                $imp = "import $($otherFd.newPackage).$cName"
                if (-not $content.Contains($imp)) {
                    $null = $ImportsToAdd.Add($imp)
                }
            }
        }
    }

    if ($ImportsToAdd.Count -gt 0) {
        $importString = "`n" + ($ImportsToAdd -join "`n")
        
        if ($content -match '(?m)^import .+') {
            $matchColl = [regex]::Matches($content, '(?m)^import .+')
            if ($matchColl.Count -gt 0) {
                $lastMatch = $matchColl[$matchColl.Count - 1]
                $insertPos = $lastMatch.Index + $lastMatch.Length
                $content = $content.Substring(0, $insertPos) + $importString + $content.Substring($insertPos)
            }
        } else {
            $matchPkg = [regex]::Match($content, '(?m)^package .+')
            if ($matchPkg.Success) {
                $insertPos = $matchPkg.Index + $matchPkg.Length
                $content = $content.Substring(0, $insertPos) + "`n" + $importString + $content.Substring($insertPos)
            }
        }
    }
    
    $FileContents[$f] = $content
}

# Process Manifest
$ManifestContent = [IO.File]::ReadAllText($ManifestFile, [System.Text.Encoding]::UTF8)
foreach ($fd in $FileDestinations.Values) {
    $cName = $fd.className
    if ($cName.EndsWith('Activity') -or $cName -eq 'SignUp' -or $cName -eq 'HomeHubApplication') {
        $cat = $fd.category
        $ManifestContent = $ManifestContent.Replace(`".${cName}`", `".${cat}.${cName}`")
        $ManifestContent = $ManifestContent.Replace(`"com.example.homehub.${cName}`", `"com.example.homehub.${cat}.${cName}`")
    }
}
[IO.File]::WriteAllText($ManifestFile, $ManifestContent, [System.Text.Encoding]::UTF8)

# Process Layout XMLs
if (Test-Path $LayoutDir) {
    $XmlFiles = Get-ChildItem -Path $LayoutDir -Filter "*.xml" -File
    foreach ($xmlFile in $XmlFiles) {
        $xmlContent = [IO.File]::ReadAllText($xmlFile.FullName, [System.Text.Encoding]::UTF8)
        $modified = $false
        
        foreach ($fd in $FileDestinations.Values) {
            $cName = $fd.className
            $oldPkg = "com.example.homehub.$cName"
            $newPkg = "com.example.homehub.$($fd.category).$cName"
            
            if ($xmlContent.Contains($oldPkg)) {
                $xmlContent = $xmlContent.Replace($oldPkg, $newPkg)
                $modified = $true
            }
            
            $oldContext = "tools:context=`".$cName`""
            $newContext = "tools:context=`".$($fd.category).$cName`""
            if ($xmlContent.Contains($oldContext)) {
                $xmlContent = $xmlContent.Replace($oldContext, $newContext)
                $modified = $true
            }
        }
        
        if ($modified) {
            [IO.File]::WriteAllText($xmlFile.FullName, $xmlContent, [System.Text.Encoding]::UTF8)
        }
    }
}

# Write files and delete old ones
foreach ($f in $FileDestinations.Keys) {
    $fd = $FileDestinations[$f]
    [IO.File]::WriteAllText($fd.newPath, $FileContents[$f], [System.Text.Encoding]::UTF8)
    Remove-Item -Path $fd.oldPath -Force
}

Write-Host "Refactoring completed successfully."
