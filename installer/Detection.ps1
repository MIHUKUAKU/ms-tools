# Shared read-only detection helpers. Windows PowerShell 5.1 compatible.
function Compare-ModVersion([string]$left, [string]$right) {
    $matchLeft = [regex]::Match($left, '^v?(\d+(?:\.\d+){0,3})(?:[+\-].*)?$')
    $matchRight = [regex]::Match($right, '^v?(\d+(?:\.\d+){0,3})(?:[+\-].*)?$')
    if (-not $matchLeft.Success -or -not $matchRight.Success) { throw "Cannot compare versions: $left / $right" }
    $a = @($matchLeft.Groups[1].Value.Split('.') | ForEach-Object { [int]$_ })
    $b = @($matchRight.Groups[1].Value.Split('.') | ForEach-Object { [int]$_ })
    for ($i = 0; $i -lt 4; $i++) {
        $av = if ($i -lt $a.Count) { $a[$i] } else { 0 }
        $bv = if ($i -lt $b.Count) { $b[$i] } else { 0 }
        if ($av -lt $bv) { return -1 }; if ($av -gt $bv) { return 1 }
    }
    # Pre-release builds are not silently retained as a stable installation.
    if ($left -match '-' -and $right -notmatch '-') { return -1 }
    if ($right -match '-' -and $left -notmatch '-') { return 1 }
    return 0
}
function Test-VersionRange([string]$version, $ranges) {
    foreach ($range in @($ranges)) {
        foreach ($alternative in ([string]$range -split '\s*\|\|\s*')) {
            $valid = $true
            foreach ($term in ($alternative.Trim() -split '\s+')) {
                if ($term -eq '*' -or $term -eq '') { continue }
                # Fabric uses an empty prerelease suffix (e.g. ~26.2-) for
                # ranges that include prereleases as well as stable releases.
                # Our installer selects stable 26.2/26.3 instances only.
                if ($term -match '^([~^<>=]*\d+(?:\.\d+)*)-$') { $term = $Matches[1] }
                if ($term -match '^(\d+(?:\.\d+)*)\.[xX*]$') {
                    if (-not ($version -eq $Matches[1] -or $version.StartsWith($Matches[1] + '.'))) { $valid = $false }; continue
                }
                if ($term -notmatch '^(>=|<=|>|<|=|~|\^)?(v?\d+(?:\.\d+){0,3}(?:[+\-][\w.]+)?)$') { $valid = $false; break }
                $op = $Matches[1]; $target = $Matches[2]
                try { $comparison = Compare-ModVersion $version $target } catch { $valid = $false; break }
                switch ($op) {
                    '>=' { if ($comparison -lt 0) { $valid = $false } }
                    '<=' { if ($comparison -gt 0) { $valid = $false } }
                    '>' { if ($comparison -le 0) { $valid = $false } }
                    '<' { if ($comparison -ge 0) { $valid = $false } }
                    '~' {
                        $parts = @($target.Split('.')); $prefix = $parts[0]
                        if ($parts.Count -ge 2) { $prefix += '.' + $parts[1] }
                        if ($comparison -lt 0 -or -not ($version -eq $prefix -or $version.StartsWith($prefix + '.'))) { $valid = $false }
                    }
                    '^' {
                        $parts = @($target.Split('.') | ForEach-Object { [int]$_ }); $at = 0
                        while ($at -lt ($parts.Count - 1) -and $parts[$at] -eq 0) { $at++ }
                        $upper = @($parts[0..$at]); $upper[$at]++
                        if ($comparison -lt 0 -or (Compare-ModVersion $version ($upper -join '.')) -ge 0) { $valid = $false }
                    }
                    default { if ($comparison -ne 0) { $valid = $false } }
                }
            }
            if ($valid) { return $true }
        }
    }
    return $false
}
function Read-FabricProfile([string]$folder) {
    $profiles = @()
    foreach ($file in @(Get-ChildItem -LiteralPath $folder -Filter '*.json' -File)) {
        try { $j = Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json } catch { continue }
        $library = @($j.libraries | Where-Object { $_.name -match '^net\.fabricmc:fabric-loader:' })
        if ($library.Count -ne 1) { continue }
        $loader = ($library[0].name -split ':')[2]
        $game = [string]$j.inheritsFrom
        if ($game -notmatch '^26\.(2|3)$') {
            $matchesGame = [regex]::Matches([string]$j.id, '(?<![\d.])26\.(2|3)(?![\d.])')
            if ($matchesGame.Count -ne 1) { continue }
            $game = $matchesGame[0].Value
        }
        $profiles += [pscustomobject]@{ Game=$game; Loader=$loader; File=$file.FullName }
    }
    if ($profiles.Count -ne 1) { throw "无法唯一识别26.2/26.3 Fabric版本JSON：$folder。请先用启动器创建并启动一次对应Fabric实例。" }
    if ((Compare-ModVersion $profiles[0].Loader '0.19.3') -lt 0) { throw 'Fabric Loader至少需要0.19.3，请先在启动器更新。' }
    return $profiles[0]
}
function Test-ExistingDependency($info, $dependency, [string]$game, [string]$loader) {
    if ($null -eq $info -or $info.id -ne $dependency.id) { return $false }
    if (-not (Test-VersionRange ([string]$info.version) @('>=' + $dependency.minimum))) { return $false }
    if ($null -eq $info.depends.minecraft -or -not (Test-VersionRange $game $info.depends.minecraft)) { return $false }
    if ($null -ne $info.depends.fabricloader -and -not (Test-VersionRange $loader $info.depends.fabricloader)) { return $false }
    return $true
}
function Choose-WorldFolder {
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
    $dialog.Description = '更智能的假人：选择要启用工具的存档文件夹（内含level.dat）'
    $dialog.ShowNewFolderButton = $false
    $dialog.SelectedPath = Join-Path $env:APPDATA '.minecraft'
    if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { throw '已取消，没有修改游戏。' }
    return $dialog.SelectedPath
}
