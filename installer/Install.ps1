param(
    [string]$InstancePath,
    [string]$WorldPath,
    [string]$ProfilePath,
    [switch]$ChooseWorld,
    [switch]$EnableOnly,
    [string]$WorldName,
    [ValidateSet('MS','Trade','Both')][string]$Mode,
    [switch]$DryRun,
    [switch]$Yes
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Detection.ps1')
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
    return $item.FullName.TrimEnd([char[]]@('\','/'))
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
function Stage-Download($item, [string]$stage) {
    $destination = Join-Path $stage $item.Name
    if (Test-Path -LiteralPath $destination) { Fail "下载文件重名：$($item.Name)" }
    $cached = Join-Path (Join-Path (Join-Path $packageRoot 'payload/dependencies') $gameVersion) $item.Name
    if ($item.bundled -and (Test-Path -LiteralPath $cached -PathType Leaf)) {
        Write-Host "随包前置 $($item.slug)：$($item.Name)"
        Copy-Item -LiteralPath $cached -Destination $destination
    } else {
        Write-Host "官方下载 $($item.slug)：$($item.Name) | $($item.projectUrl)"
        Invoke-WebRequest -Uri $item.Url -OutFile $destination -UseBasicParsing -TimeoutSec 120
    }
    $actualHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash.ToLowerInvariant()
    if ($actualHash -ne $item.sha512) { Fail "$($item.Name) SHA-512 校验失败，尚未修改游戏文件。" }
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
    Write-Host '更智能的假人 | 安装体验更新 1.7.4'
    Write-Host '请完全退出游戏；存档不会被升级版本。需要已能正常启动的26.2/26.3 Fabric实例和Java25。'
    if ($ChooseWorld -and [string]::IsNullOrWhiteSpace($WorldPath)) { $WorldPath = Choose-WorldFolder }
    if (-not [string]::IsNullOrWhiteSpace($WorldPath)) {
        $worldSelected = Full-ExistingDirectory $WorldPath
        if (-not (Test-Path -LiteralPath (Join-Path $worldSelected 'level.dat') -PathType Leaf)) { Fail '请选择内含level.dat的存档文件夹。' }
        $savesSelected = Full-ExistingDirectory (Split-Path -Parent $worldSelected)
        if ((Split-Path -Leaf $savesSelected) -ne 'saves') { Fail '存档应位于实例的saves文件夹中；请先把存档放入启动器对应实例。' }
        $derivedInstance = Full-ExistingDirectory (Split-Path -Parent $savesSelected)
        if (-not [string]::IsNullOrWhiteSpace($InstancePath) -and (Full-ExistingDirectory $InstancePath) -ne $derivedInstance) { Fail '指定实例与所选存档不在同一目录。' }
        $InstancePath = $derivedInstance
        $WorldName = Split-Path -Leaf $worldSelected
    }
    if ([string]::IsNullOrWhiteSpace($InstancePath)) { $InstancePath = Read-Choice 'PCL的26.2/26.3 Fabric版本文件夹完整路径' }
    $instance = Full-ExistingDirectory $InstancePath
    $profileFolder = if ($ProfilePath) { Full-ExistingDirectory $ProfilePath } else { $instance }
    try { $profile = Read-FabricProfile $profileFolder }
    catch {
        if (-not $ChooseWorld -or -not (Test-Path -LiteralPath (Join-Path $instance 'versions') -PathType Container)) { throw }
        Add-Type -AssemblyName System.Windows.Forms
        $picker = New-Object System.Windows.Forms.OpenFileDialog
        $picker.Title = '非版本隔离实例：选择将用于打开此存档的Fabric版本JSON'
        $picker.InitialDirectory = Join-Path $instance 'versions'
        $picker.Filter = 'Minecraft版本JSON (*.json)|*.json'
        if ($picker.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { Fail '已取消，没有修改游戏。' }
        $profile = Read-FabricProfile (Split-Path -Parent $picker.FileName)
    }
    $gameVersion = $profile.Game
    Write-Host "识别到 Minecraft $gameVersion | Fabric Loader $($profile.Loader)"
    if ([string]::IsNullOrWhiteSpace($Mode)) {
        $selection = Read-Choice '安装模式：1=MS，2=刷交易，3=两者'
        $Mode = switch ($selection) { '1' {'MS'} '2' {'Trade'} '3' {'Both'} default { Fail '模式只能为 1、2 或 3。' } }
    }
    $wantMs = $Mode -eq 'MS' -or $Mode -eq 'Both'
    $wantTrade = $Mode -eq 'Trade' -or $Mode -eq 'Both'
    if ($EnableOnly -and $Mode -ne 'MS') { Fail '启用已有存档入口仅用于MS假人工具。' }
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
    $requiredIds = @()
    $payload = @()
    if ($wantMs) {
        if (-not $EnableOnly) {
            $localJar = Resolve-Payload (Join-Path $packageRoot 'payload/mods') 'mstrade'
            Assert-PayloadVersion $localJar $gameVersion
            $payload += $localJar
        }
        foreach ($name in @('ms.sc','fetch.sc','put.sc','throw.sc')) {
            $path = Join-Path (Join-Path $packageRoot 'payload/scripts') $name
            if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { Fail "发布包不完整，缺少：$path" }
        }
        $requiredIds += 'carpet'
    }
    if ($wantTrade) {
        $localJar = Resolve-Payload (Join-Path $packageRoot 'payload/mods') 'trade_target_331'
        Assert-PayloadVersion $localJar $gameVersion
        $payload += $localJar
        $requiredIds += @('fabric-api','trade_cycling','visibletraders')
    }
    $manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'dependencies.json') -Raw | ConvertFrom-Json
    $available = @($manifest.games.PSObject.Properties[$gameVersion].Value)
    $existing = @()
    if (Test-Path -LiteralPath $modsPath -PathType Container) {
        foreach ($jar in @(Get-ChildItem -LiteralPath $modsPath -Filter '*.jar' -File)) {
            try { $info = Read-ModInfo $jar.FullName }
            catch { Fail "无法检查现有模组 $($jar.Name)：$($_.Exception.Message)" }
            if ($null -ne $info) { $existing += [pscustomobject]@{ Path=$jar.FullName; Info=$info } }
        }
    }
    if ($EnableOnly) {
        $app = @($existing | Where-Object { $_.Info.id -eq 'mstrade' })
        $appRequirement = [pscustomobject]@{ id='mstrade'; minimum='1.7.3' }
        if ($app.Count -ne 1 -or -not (Test-ExistingDependency $app[0].Info $appRequirement $gameVersion $profile.Loader)) {
            Fail '此实例尚未装好支持当前游戏版本的MS工具，请先运行“开始安装.cmd”。'
        }
        Write-Host '启用已有存档：保留全部模组，只复制四脚本并开启该存档的假人权限和自动加载。'
    }
    foreach ($id in $requiredIds) {
        $dependency = @($available | Where-Object { $_.id -eq $id })
        if ($dependency.Count -ne 1) { Fail "安装包缺少$id的准确下载信息。" }
        $dependency = $dependency[0]
        $found = @($existing | Where-Object { $_.Info.id -eq $id })
        if ($found.Count -eq 1 -and (Test-ExistingDependency $found[0].Info $dependency $gameVersion $profile.Loader)) {
            Write-Host "已安装，保留：$id $($found[0].Info.version) | $($found[0].Path)"
            if ($found[0].Info.version -ne $dependency.version) { Write-Host '提示：保留用户现有版本；与推荐包不同的组合未经过本包完整游戏验证。' }
        } else {
            if ($EnableOnly) { Fail '此实例的Carpet缺失、重复或版本不符，请先运行“开始安装.cmd”。' }
            if ($found.Count -gt 0) { Write-Host "需备份替换：$id（版本要求不符或重复安装）" }
            else { Write-Host "缺失，将安装：$id $($dependency.version)" }
            $wanted += $dependency
        }
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
    foreach ($item in $wanted) {
        $downloaded = Stage-Download $item $stageMods
        if (-not (Test-ExistingDependency (Read-ModInfo $downloaded) $item $gameVersion $profile.Loader)) { Fail "推荐前置$($item.id)与当前游戏/Loader不匹配；请先在启动器更新Loader。" }
        $installMods += $downloaded
    }
    $newIds = @()
    foreach ($jar in $installMods) {
        $info = Read-ModInfo $jar
        if ($null -eq $info) { Fail "无效模组文件：$jar" }
        if ($newIds -contains $info.id) { Fail "安装包内有重复模组 ID：$($info.id)" }
        $newIds += [string]$info.id
    }
    $oldMods = @($existing | Where-Object { $newIds -contains $_.Info.id } | ForEach-Object { $_.Path })
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
    if ($ChooseWorld -and -not $Yes) {
        Add-Type -AssemblyName System.Windows.Forms
        $action = if ($EnableOnly) { '只复制脚本并开启该存档的假人权限与自动加载，保留全部模组。' } else { '补齐缺失前置、安装工具，开启该存档的假人权限与脚本自动加载。' }
        $answer = [System.Windows.Forms.MessageBox]::Show("存档：$WorldName`n游戏：$gameVersion Fabric`n模式：$Mode`n$action`n旧相关文件先备份。`n`n请确认已退出游戏，继续？", '更智能的假人', 'YesNo', 'Question')
        if ($answer -ne [System.Windows.Forms.DialogResult]::Yes) { Fail '已取消，目标实例未修改。' }
    } elseif (-not $Yes) {
        $answer = Read-Choice '确认已退出游戏并安装？输入 YES'
        if ($answer -cne 'YES') { Fail '已取消，目标实例未修改。' }
    }
    $backupRoot = Join-Path $instance ('.ms-tools-backup/' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
    $moved = @()
    $installed = @()
    try {
        New-Item -ItemType Directory -Path $backupRoot -Force | Out-Null
        foreach ($file in $oldFiles) {
            $relative = $file.Substring($instance.Length).TrimStart([char[]]@('\','/'))
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
    $safeTemp = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd([char[]]@('\','/')) + [System.IO.Path]::DirectorySeparatorChar
    $safeStage = [System.IO.Path]::GetFullPath($stageRoot)
    if ($stageCreated -and $safeStage.StartsWith($safeTemp + 'ms-tools-stage-', [System.StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $safeStage -PathType Container)) {
        Remove-Item -LiteralPath $safeStage -Recurse -Force
    }
}
