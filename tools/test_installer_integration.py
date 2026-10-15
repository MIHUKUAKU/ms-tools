"""Exercise real installer file operations on disposable fixtures, never real saves.

Usage: python tools/test_installer_integration.py /path/to/pwsh
Requires dependencies cached by fetch_installer_dependencies.py.
"""
import hashlib
import base64
import json
import os
import platform
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PWSH = sys.argv[1]
PACKAGE_ROOT = Path(sys.argv[sys.argv.index('--package') + 1]).resolve() if '--package' in sys.argv else None
checks = []

def run(package, world, mode='MS', success=True, dry=False, enable_only=False):
    args = [PWSH, '-NoProfile', '-File', str(package/'installer/Install.ps1'),
            '-WorldPath', str(world), '-Mode', mode, '-Yes']
    if dry:
        args.append('-DryRun')
    if enable_only:
        args.append('-EnableOnly')
    if os.name == 'nt':
        # Windows PowerShell 5.1 uses the system code page by default. Explicitly
        # emit UTF-8 so assertions also work for Chinese save names and messages.
        quote = lambda value: "'" + str(value).replace("'", "''") + "'"
        command = '[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false); & ' + ' '.join(
            value if value.startswith('-') else quote(value) for value in args[3:])
        args = [PWSH, '-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand',
                base64.b64encode(command.encode('utf-16le')).decode('ascii')]
    child_env = os.environ.copy()
    if os.name == 'nt':
        # Match an Explorer launch. A pwsh-hosted Actions step can otherwise
        # inject its PowerShell 7 module paths into Windows PowerShell 5.1.
        child_env.pop('PSModulePath', None)
    result = subprocess.run(args, env=child_env, text=True, encoding='utf-8', errors='replace', capture_output=True, timeout=180)
    if (result.returncode == 0) != success:
        raise AssertionError(result.stdout + result.stderr)
    return result.stdout + result.stderr

def fixture(base, game):
    instance = base / ('Fabric ' + game)
    world = instance / 'saves' / '我的生存 world'
    (world/'scripts').mkdir(parents=True)
    (instance/'mods').mkdir()
    (instance/'profile.json').write_text(json.dumps({
        'id': 'fabric-' + game, 'inheritsFrom': game,
        'libraries': [{'name': 'net.fabricmc:fabric-loader:0.19.5'}]}))
    (world/'level.dat').write_bytes(b'TEST-FIXTURE-DO-NOT-LAUNCH')
    (world/'carpet.conf').write_text('commandPlayer ops\nscriptsAutoload false\notherRule keep\n')
    (world/'scripts/ms.data.nbt').write_bytes(b'presets-preserve')
    (world/'scripts/other.sc').write_text('other-script-preserve')
    (world/'unchanged-chains.json').write_text('{"preserve":true}')
    with zipfile.ZipFile(instance/'mods/unrelated.jar', 'w') as z:
        z.writestr('fabric.mod.json', json.dumps({'id':'unrelated', 'version':'1'}))
    return instance, world

def snapshot(folder):
    return {str(p.relative_to(folder)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in folder.rglob('*') if p.is_file()}

with tempfile.TemporaryDirectory(prefix='ms-install-qa-') as temporary:
    base = Path(temporary)
    package = base/'package'
    source = PACKAGE_ROOT or ROOT
    shutil.copytree(source/'installer', package/'installer')
    shutil.copytree(source/'payload', package/'payload')
    manifest = json.loads((package/'installer/dependencies.json').read_text())
    for game in ['26.2','26.3']:
        instance, world = fixture(base, game)
        before = snapshot(instance)
        run(package, world, dry=True)
        assert snapshot(instance) == before
        checks.append(game + ': DryRun left fixture unchanged')
        run(package, world)
        assert (world/'scripts/ms.sc').read_bytes() == (package/'payload/scripts/ms.sc').read_bytes()
        assert 'commandPlayer true' in (world/'carpet.conf').read_text()
        assert 'scriptsAutoload true' in (world/'carpet.conf').read_text()
        assert 'otherRule keep' in (world/'carpet.conf').read_text()
        for name in ['scripts/ms.data.nbt','scripts/other.sc','unchanged-chains.json','level.dat']:
            assert snapshot(instance)[str((world/name).relative_to(instance))] == before[str((world/name).relative_to(instance))]
        checks.append(game + ': scripts enabled; rules, presets, chain markers and world marker preserved')
        carpet = next(x for x in manifest['games'][game] if x['id']=='carpet')
        carpet_path = instance/'mods'/carpet['name']
        renamed = instance/'mods/renamed-existing-carpet.jar'
        carpet_path.rename(renamed)
        old_hash = hashlib.sha256(renamed.read_bytes()).hexdigest()
        output = run(package, world)
        assert '已安装，保留：carpet' in output
        assert not carpet_path.exists()
        assert hashlib.sha256(renamed.read_bytes()).hexdigest() == old_hash
        checks.append(game + ': renamed existing dependency detected by ID and reused')
        shutil.copy2(renamed, instance/'mods/duplicate-carpet.jar')
        run(package, world)
        assert carpet_path.exists() and not renamed.exists() and not (instance/'mods/duplicate-carpet.jar').exists()
        assert list((instance/'.ms-tools-backup').rglob('renamed-existing-carpet.jar'))
        assert list((instance/'.ms-tools-backup').rglob('duplicate-carpet.jar'))
        checks.append(game + ': duplicate dependency jars backed up and replaced once')
        second_world = instance/'saves/另一个存档'
        second_world.mkdir()
        (second_world/'level.dat').write_bytes(b'ANOTHER-FIXTURE')
        mods_before = snapshot(instance/'mods')
        first_world_before = snapshot(world)
        run(package, second_world, enable_only=True)
        assert snapshot(instance/'mods') == mods_before
        assert snapshot(world) == first_world_before
        assert 'commandPlayer true' in (second_world/'carpet.conf').read_text()
        assert 'scriptsAutoload true' in (second_world/'carpet.conf').read_text()
        assert (second_world/'scripts/ms.sc').exists()
        checks.append(game + ': enable-only configured another save without changing any mod or the first save')

    # Check failure before writes when an included dependency fails integrity.
    instance, world = fixture(base/'bad-hash', '26.3')
    cached = package/'payload/dependencies/26.3'/next(x['name'] for x in manifest['games']['26.3'] if x['id']=='carpet')
    original = cached.read_bytes(); cached.write_bytes(b'corrupt')
    before = snapshot(instance)
    run(package, world, success=False)
    assert snapshot(instance) == before
    cached.write_bytes(original)
    checks.append('bad SHA-512: stopped before target writes')

    # Force a late filename collision: rollback must restore already moved files.
    instance, world = fixture(base/'rollback', '26.3')
    carpet_name = next(x['name'] for x in manifest['games']['26.3'] if x['id']=='carpet')
    with zipfile.ZipFile(instance/'mods'/carpet_name, 'w') as z:
        z.writestr('fabric.mod.json', json.dumps({'id':'unrelated-collision', 'version':'1'}))
    (world/'scripts/ms.sc').write_text('old-ms-preserve')
    before = snapshot(instance)
    run(package, world, success=False)
    after = snapshot(instance)
    assert all(after.get(name) == digest for name, digest in before.items())
    assert not (instance/'mods/mstrade-1.7.3.jar').exists()
    checks.append('late filename collision: rollback restored old scripts/config and preserved unrelated jar')

    # Invalid loader is rejected before dependencies or world changes.
    instance, world = fixture(base/'bad-loader', '26.3')
    profile = instance/'profile.json'
    profile.write_text(profile.read_text().replace('0.19.5','0.18.0'))
    before = snapshot(instance)
    run(package, world, success=False)
    assert snapshot(instance) == before
    checks.append('old Loader: rejected without target changes')

    instance, world = fixture(base/'enable-without-mods', '26.3')
    before = snapshot(instance)
    run(package, world, enable_only=True, success=False)
    assert snapshot(instance) == before
    checks.append('enable-only without installed tools: refused before changes')

    if '--network' in sys.argv:
        for game in ['26.2','26.3']:
            instance, world = fixture(base/('both-' + game), game)
            run(package, world, mode='Both')
            ids = []
            for jar in (instance/'mods').glob('*.jar'):
                with zipfile.ZipFile(jar) as z:
                    ids.append(json.loads(z.read('fabric.mod.json'))['id'])
            assert set(['carpet','fabric-api','trade_cycling','visibletraders','mstrade','trade_target_331']).issubset(ids)
            dependencies_before = {p.name: p.read_bytes() for p in (instance/'mods').glob('*.jar')
                                   if p.name not in ('mstrade-1.7.3.jar','trade-target-331-1.4.2.jar')}
            output = run(package, world, mode='Both')
            assert '官方下载' not in output
            assert all((instance/'mods'/name).read_bytes() == data for name, data in dependencies_before.items())
            checks.append(game + ': Both mode verified official Trade Cycling download; second run reused all four dependencies without download')

print(json.dumps({'result':'PASS', 'checks':checks,
                  'environment':platform.platform() + '; ' + PWSH + '; disposable fixtures; explicit save paths'}, ensure_ascii=False, indent=2))
