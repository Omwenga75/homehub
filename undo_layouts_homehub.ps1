# Revert HomeHub layouts to original flat structure
$root = "app/src/main/res/layout"
$base = "app/src/main/res/layouts"

# Move all XML files back to the original folder
Get-ChildItem -Path "$base" -Filter "*.xml" -Recurse | ForEach-Object {
    $dest = "$root/$($_.Name)"
    Write-Host "Moving back $($_.Name)"
    Move-Item -Path $_.FullName -Destination $dest -Force
}

# Delete the temporary layouts structure
Remove-Item -Path $base -Recurse -Force

Write-Host "Reversion complete! All layouts are back in $root"
