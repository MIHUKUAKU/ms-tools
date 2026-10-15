param(
    [string]$InstancePath,
    [string]$WorldName,
    [ValidateSet('MS','Trade','Both')][string]$Mode,
    [switch]$DryRun,
    [switch]$Yes
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Fail([string]$message) { throw $message }
function Read-Choice([string]$message) {
    $answer = Read-Host $message
    if ([string]::IsNullOrWhiteSpace($answer)) { Fail '已取消：没有选择。' }
    return $answer.Trim()
}
function Full-ExistingDirectory([string]$path) {
    $item = Get-Item -LiteralPath $path -ErrorAction Stop
    if (-not $item.PSIsContainer) { Fail "不是文件夹：$path" }
    if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) { Fail "拒绝符号链接/目录联接作为安装目标：$path" }
    return $item.FullName.TrimEnd('\')
}
function Assert-PlainDirectory([string]$path) {
    if (Test-Path -LiteralPath $path) {
        $item = Get-Item -LiteralPath $path
        if (-not $item.PSIsContainer -or ($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            Fail "安装子目录不是普通文件夹：$path"
        }
    }
}
function Read-ModInfo([string]$jar) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $entry = $zip.GetEntry('fabric.mod.json')
        if ($null -eq $entry) { return $null }
        $reader = New-Object System.IO.StreamReader($entry.Open(), [System.Text.Encoding]::UTF8)
        try { return ($reader.ReadToEnd() | ConvertFrom-Json) }
        finally { $reader.Dispose() }
    }
    finally { $zip.Dispose() }
}
function Resolve-Payload([string]$folder, [string]$id) {
    if (-not (Test-Path -LiteralPath $folder -PathType Container)) { Fail "发布包不完整，缺少：$folder" }
    $hits = @()
    foreach ($file in @(Get-ChildItem -LiteralPath $folder -Filter '*.jar' -File)) {
        $info = Read-ModInfo $file.FullName
        if ($null -ne $info -and $info.id -eq $id) { $hits += $file.FullName }
    }
    if ($hits.Count -ne 1) { Fail "发布包中模组 $id 应恰好有一个 jar，实际 $($hits.Count) 个。" }
    return $hits[0]
}
function Assert-PayloadVersion([string]$jar, [string]$gameVersion) {
    $info = Read-ModInfo $jar
    $ranges = @($info.depends.minecraft)
    $supported = @($ranges | Where-Object { $_ -eq '*' -or $_ -eq $gameVersion -or $_ -eq "$gameVersion.x" })
    if ($supported.Count -eq 0) { Fail "发布包的 $($info.id) 没有声明支持 Minecraft $gameVersion；拒绝强行安装。" }
}
function Get-Version([string]$folder) {
    $name = Split-Path -Leaf $folder
    if ($name -match '(^|[^0-9])26\.(2|3)([^0-9]|$)') {
        $foundVersion = "26.$($Matches[2])"
        if ($name -match 'fabric') { return $foundVersion }
    }
    foreach ($file in @(Get-ChildItem -LiteralPath $folder -Filter '*.json' -File)) {
        $raw = Get-Content -LiteralPath $file.FullName -Raw
        if ($raw -match '(?i)fabric' -and $raw -match '(^|[^0-9])26\.(2|3)([^0-9]|$)') { return "26.$($Matches[2])" }
    }
    Fail '只接受能识别为 Minecraft 26.2 或 26.3 的 Fabric 版本目录；请在 PCL 中选择具体版本文件夹。'
}
function Latest-ModrinthFile([string]$slug, [string]$gameVersion, [string]$expectedId) {
    $loaders = [uri]::EscapeDataString('["fabric"]')
    $games = [uri]::EscapeDataString("[`"$gameVersion`"]")
    $uri = "https://api.modrinth.com/v2/project/$slug/version?loaders=$loaders&game_versions=$games&include_changelog=false"
    $versions = @(Invoke-RestMethod -Uri $uri -Headers @{'User-Agent'='ms-tools-installer/1.0 (Windows PowerShell)'} -TimeoutSec 30)
    $versions = @($versions | Where-Object { $_.version_type -eq 'release' } | Sort-Object date_published -Descending)
    if ($versions.Count -eq 0) { Fail "Modrinth 没有找到 $slug 对应 $gameVersion Fabric 的正式版。" }
    foreach ($version in $versions) {
        $files = @($version.files | Where-Object { $_.primary -eq $true })
        if ($files.Count -eq 0) { $files = @($version.files) }
        foreach ($file in $files) {
            if ($file.filename -like '*.jar' -and $file.hashes.sha512 -match '^[a-fA-F0-9]{128}$') {
                if ($file.filename -ne [System.IO.Path]::GetFileName($file.filename) -or $file.filename -match '[\\/:]') {
                    Fail "$slug 返回了不安全的文件名。"
                }
                return [pscustomobject]@{ Slug=$slug; Id=$expectedId; Name=$file.filename; Url=$file.url; Hash=$file.hashes.sha512.ToLowerInvariant() }
            }
        }
    }
    Fail "Modrinth 没有为 $slug 提供可校验的 jar。"
}
function Stage-Download($item, [string]$stage) {
    $destination = Join-Path $stage $item.Name
    if (Test-Path -LiteralPath $destination) { Fail "下载文件重名：$($item.Name)" }
    Write-Host "下载 $($item.Slug)：$($item.Name)"
    Invoke-WebRequest -Uri $item.Url -OutFile $destination -UseBasicParsing -TimeoutSec 120
    $actualHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash.ToLowerInvariant()
    if ($actualHash -ne $item.Hash) { Fail "$($item.Name) SHA-512 校验失败，尚未修改游戏文件。" }
    $info = Read-ModInfo $destination
    if ($null -eq $info -or $info.id -ne $item.Id) { Fail "$($item.Name) 的 Fabric 模组 ID 不符，尚未修改游戏文件。" }
    return $destination
}
function Merge-CarpetConfig([string]$source, [string]$destination) {
    $lines = @()
    if (Test-Path -LiteralPath $source -PathType Leaf) { $lines = @(Get-Content -LiteralPath $source) }
    $updated = @()
    $found = @{'commandPlayer'=$false; 'scriptsAutoload'=$false}
    foreach ($line in $lines) {
        if ($line -match '^\s*(commandPlayer|scriptsAutoload)\s+') {
            $rule = $Matches[1]
            if (-not $found[$rule]) { $updated += "$rule true"; $found[$rule] = $true }
        } else { $updated += $line }
    }
    foreach ($rule in @('commandPlayer','scriptsAutoload')) { if (-not $found[$rule]) { $updated += "$rule true" } }
    [System.IO.File]::WriteAllLines($destination, [string[]]$updated, (New-Object System.Text.UTF8Encoding($false)))
}

$packageRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$stageRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('ms-tools-stage-' + [guid]::NewGuid().ToString('N'))
$stageCreated = $false
try {
    Write-Host '安装前请完全退出 Minecraft / PCL 当前游戏。此脚本不会检查进程，继续即表示已退出。'
    if ([string]::IsNullOrWhiteSpace($InstancePath)) { $InstancePath = Read-Choice 'PCL 的 26.2/26.3 Fabric 版本文件夹完整路径' }
    $instance = Full-ExistingDirectory $InstancePath
    $gameVersion = Get-Version $instance
    if ([string]::IsNullOrWhiteSpace($Mode)) {
        $selection = Read-Choice '安装模式：1=MS，2=刷交易，3=两者'
        $Mode = switch ($selection) { '1' {'MS'} '2' {'Trade'} '3' {'Both'} default { Fail '模式只能为 1、2 或 3。' } }
    }
    $wantMs = $Mode -eq 'MS' -or $Mode -eq 'Both'
    $wantTrade = $Mode -eq 'Trade' -or $Mode -eq 'Both'
    $modsPath = Join-Path $instance 'mods'
    Assert-PlainDirectory $modsPath
    $world = $null
    if ($wantMs) {
        $saves = Join-Path $instance 'saves'
        if (-not (Test-Path -LiteralPath $saves -PathType Container)) { Fail "找不到存档目录：$saves" }
        Assert-PlainDirectory $saves
        $worlds = @(Get-ChildItem -LiteralPath $saves -Directory | Where-Object {
            ($_.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -eq 0 -and
            (Test-Path -LiteralPath (Join-Path $_.FullName 'level.dat') -PathType Leaf)
        })
        if ($worlds.Count -eq 0) { Fail '没有找到含 level.dat 的单人存档。' }
        if ([string]::IsNullOrWhiteSpace($WorldName)) {
            for ($i=0; $i -lt $worlds.Count; $i++) { Write-Host "  $($i+1). $($worlds[$i].Name)" }
            $choice = Read-Choice '选择存档编号'
            $index = 0
            if (-not [int]::TryParse($choice, [ref]$index) -or $index -lt 1 -or $index -gt $worlds.Count) { Fail '存档编号无效。' }
            $world = $worlds[$index-1].FullName
        } else {
            if ($WorldName -ne [System.IO.Path]::GetFileName($WorldName) -or $WorldName -in @('.', '..')) { Fail 'WorldName 必须是存档文件夹名称。' }
            $match = @($worlds | Where-Object { $_.Name -eq $WorldName })
            if ($match.Count -ne 1) { Fail "未找到存档：$WorldName" }
            $world = $match[0].FullName
        }
    }
    $wanted = @()
    $payload = @()
    if ($wantMs) {
        $localJar = Resolve-Payload (Join-Path $packageRoot 'payload/mods') 'mstrade'
        Assert-PayloadVersion $localJar $gameVersion
        $payload += $localJar
        foreach ($name in @('ms.sc','fetch.sc','put.sc','throw.sc')) {
            $path = Join-Path (Join-Path $packageRoot 'payload/scripts') $name
            if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Fail "发布包不完整，缺少：$path" }
        }
        $wanted += Latest-ModrinthFile 'carpet' $gameVersion 'carpet'
    }
    if ($wantTrade) {
        $localJar = Resolve-Payload (Join-Path $packageRoot 'payload/mods') 'trade_target_331'
        Assert-PayloadVersion $localJar $gameVersion
        $payload += $localJar
        $wanted += Latest-ModrinthFile 'fabric-api' $gameVersion 'fabric-api'
        $wanted += Latest-ModrinthFile 'trade-cycling' $gameVersion 'trade_cycling'
        $wanted += Latest-ModrinthFile 'visible-traders' $gameVersion 'visibletraders'
    }
    New-Item -ItemType Directory -Path $stageRoot | Out-Null
    $stageCreated = $true
    $stageMods = Join-Path $stageRoot 'mods'
    New-Item -ItemType Directory -Path $stageMods | Out-Null
    $installMods = @()
    foreach ($jar in $payload) {
        $destination = Join-Path $stageMods (Split-Path -Leaf $jar)
        Copy-Item -LiteralPath $jar -Destination $destination
        $installMods += $destination
    }
    foreach ($item in $wanted) { $installMods += Stage-Download $item $stageMods }
    $newIds = @()
    foreach ($jar in $installMods) {
        $info = Read-ModInfo $jar
        if ($null -eq $info) { Fail "无效模组文件：$jar" }
        if ($newIds -contains $info.id) { Fail "安装包内有重复模组 ID：$($info.id)" }
        $newIds += [string]$info.id
    }
    $oldMods = @()
    if (Test-Path -LiteralPath $modsPath -PathType Container) {
        foreach ($jar in @(Get-ChildItem -LiteralPath $modsPath -Filter '*.jar' -File)) {
            try { $info = Read-ModInfo $jar.FullName }
            catch { Fail "无法检查现有模组 $($jar.Name)：$($_.Exception.Message)" }
            if ($null -ne $info -and $newIds -contains $info.id) { $oldMods += $jar.FullName }
        }
    }
    $configPath = if ($wantMs) { Join-Path $world 'carpet.conf' } else { $null }
    if ($wantMs) { Merge-CarpetConfig $configPath (Join-Path $stageRoot 'carpet.conf') }
    $scriptDest = if ($wantMs) { Join-Path $world 'scripts' } else { $null }
    if ($wantMs) { Assert-PlainDirectory $scriptDest }
    $oldFiles = @($oldMods)
    if ($wantMs) {
        foreach ($name in @('ms.sc','fetch.sc','put.sc','throw.sc')) {
            $file = Join-Path $scriptDest $name
            if (Test-Path -LiteralPath $file -PathType Leaf) { $oldFiles += $file }
        }
        if (Test-Path -LiteralPath $configPath -PathType Leaf) { $oldFiles += $configPath }
    }
    Write-Host "目标：$instance | Minecraft $gameVersion Fabric | 模式 $Mode"
    if ($wantMs) { Write-Host "存档：$world" }
    Write-Host "将替换相关旧文件 $($oldFiles.Count) 个；无关模组、存档数据、链 JSON 均不触碰。"
    if ($DryRun) { Write-Host 'DryRun：下载、哈希和路径检查完成；没有修改目标实例。'; return }
    if (-not $Yes) {
        $answer = Read-Choice '确认已退出游戏并安装？输入 YES'
        if ($answer -cne 'YES') { Fail '已取消，目标实例未修改。' }
    }
    $backupRoot = Join-Path $instance ('.ms-tools-backup\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
    $moved = @()
    $installed = @()
    try {
        New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
        foreach ($file in $oldFiles) {
            $relative = $file.Substring($instance.Length).TrimStart('\')
            $target = Join-Path $backupRoot $relative
            New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
            Move-Item -LiteralPath $file -Destination $target
            $moved += [pscustomobject]@{ From=$file; To=$target }
        }
        New-Item -ItemType Directory -Path $modsPath -Force | Out-Null
        foreach ($jar in $installMods) {
            $target = Join-Path $modsPath (Split-Path -Leaf $jar)
            if (Test-Path -LiteralPath $target) { Fail "目标文件名冲突：$target" }
            Copy-Item -LiteralPath $jar -Destination $target
            $installed += $target
        }
        if ($wantMs) {
            New-Item -ItemType Directory -Path $scriptDest -Force | Out-Null
            foreach ($name in @('ms.sc','fetch.sc','put.sc','throw.sc')) {
                $target = Join-Path $scriptDest $name
                Copy-Item -LiteralPath (Join-Path (Join-Path $packageRoot 'payload/scripts') $name) -Destination $target
                $installed += $target
            }
            Copy-Item -LiteralPath (Join-Path $stageRoot 'carpet.conf') -Destination $configPath
            $installed += $configPath
        }
        Write-Host "安装完成。旧文件备份：$backupRoot"
        if ($wantMs) { Write-Host '进入存档后脚本会自动加载；直接输入 /ms。若未出现，请查看日志并让有权限的管理员检查脚本加载。' }
    } catch {
        $cause = $_.Exception.Message
        foreach ($file in $installed) { if (Test-Path -LiteralPath $file -PathType Leaf) { Remove-Item -LiteralPath $file -Force } }
        [array]::Reverse($moved)
        foreach ($entry in $moved) { Move-Item -LiteralPath $entry.To -Destination $entry.From }
        Fail "安装中断，已尝试恢复旧文件：$cause；备份位置：$backupRoot"
    }
} catch {
    Write-Error $_.Exception.Message
    exit 1
} finally {
    $safeTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\') + '\'
    $safeStage = [System.IO.Path]::GetFullPath($stageRoot)
    if ($stageCreated -and $safeStage.StartsWith($safeTemp + 'ms-tools-stage-', [System.StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $safeStage -PathType Container)) {
        Remove-Item -LiteralPath $safeStage -Recurse -Force
    }
}
