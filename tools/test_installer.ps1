param([string]$Root = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
. (Join-Path $Root 'installer/Detection.ps1')
$cases = @(
    @('26.2', '26.2.x', $true), @('26.3', '26.2.x', $false),
    @('0.19.3', '>=0.19.3', $true), @('0.19.2', '>=0.19.3', $false),
    @('1.0.23', '>=1.0.21 <2.0', $true), @('2.0.0', '>=1.0.21 <2.0', $false),
    @('2.5.1', '2.5.0 || 2.5.1', $true), @('2.5.2', '2.5.0 || 2.5.1', $false),
    @('0.19.5', '^0.19.3', $true), @('0.20.0', '^0.19.3', $false),
    @('1.0.23+26.3', '>=1.0.21', $true), @('1.0.21-beta.1', '>=1.0.21', $false),
    @('1.2.3', '~1.2', $true), @('1.3.0', '~1.2', $false),
    @('26.2', '*', $true), @('unknown', '>=1.0', $false),
    @('26.2', '~26.2-', $true), @('26.3', '~26.2-', $false)
)
$count = 0
foreach ($case in $cases) {
    $actual = Test-VersionRange $case[0] $case[1]
    if ($actual -ne $case[2]) { throw "Range mismatch: $($case -join ' / ')" }
    $count++
}
if (-not (Test-VersionRange '26.3' @('26.2.x','26.3.x'))) { throw 'Array alternatives failed' }; $count++
$dependency = [pscustomobject]@{ id='carpet'; minimum='0' }
$info = [pscustomobject]@{ id='carpet'; version='26.3'; depends=[pscustomobject]@{minecraft='26.3'; fabricloader='>=0.19.3'} }
if (-not (Test-ExistingDependency $info $dependency '26.3' '0.19.5')) { throw 'Existing dependency reuse failed' }; $count++
if (Test-ExistingDependency $info $dependency '26.2' '0.19.5') { throw 'Wrong game accepted' }; $count++
$info.id = 'not-carpet'
if (Test-ExistingDependency $info $dependency '26.3' '0.19.5') { throw 'Wrong ID accepted' }; $count++
foreach ($file in @(Get-ChildItem (Join-Path $Root 'installer') -Filter '*.ps1')) {
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$null,[ref]$errors) > $null
    if ($errors.Count) { throw ($errors | Out-String) }
}
Write-Host "INSTALLER_DETECTION_PASS $count checks; all installer scripts parsed"
