"""Run the published CMD entry points with real Windows folder/confirm dialogs.

Only disposable instances are used. The default selected folder is a test save;
this exercises accepting the picker, not a human's manual folder navigation.
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import hashlib
import psutil
from pywinauto import Desktop
from PIL import ImageGrab

package = Path(sys.argv[1]).resolve()
base = Path(sys.argv[2]).resolve()
base.mkdir(parents=True, exist_ok=True)
instance = base / 'Windows 玩家 Fabric 26.3'
world = instance / 'saves' / '.minecraft'
world.mkdir(parents=True)
(world / 'level.dat').write_bytes(b'DISPOSABLE-FIXTURE-NOT-A-REAL-WORLD')
(instance / 'profile.json').write_text(json.dumps({
    'id': 'fabric-26.3', 'inheritsFrom': '26.3',
    'libraries': [{'name': 'net.fabricmc:fabric-loader:0.19.5'}]}), encoding='utf-8')
desktop = Desktop(backend='uia')
checks = []

def snapshot(folder):
    return {str(p.relative_to(folder)): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in folder.rglob('*') if p.is_file()}

def wait_dialog(process, button_name, seconds=150):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise AssertionError('CMD exited before the expected ' + button_name + ' dialog')
        try:
            pids = {process.pid} | {p.pid for p in psutil.Process(process.pid).children(recursive=True)}
            for window in desktop.windows(visible_only=True):
                if window.process_id() not in pids:
                    continue
                for button in window.descendants(control_type='Button'):
                    if button.window_text().replace('&', '') == button_name:
                        window.capture_as_image().save(base / (button_name + '-dialog.png'))
                        button.invoke()
                        return
        except (psutil.NoSuchProcess, RuntimeError):
            pass
        time.sleep(0.4)
    ImageGrab.grab().save(base / 'timeout-desktop.png')
    (base / 'windows.txt').write_text('\n'.join(str(w) for w in desktop.windows()), encoding='utf-8')
    raise AssertionError('Timed out waiting for real Windows dialog button ' + button_name)

def launch(name, accept=True):
    env = os.environ.copy()
    # Choose-WorldFolder starts in APPDATA/.minecraft. Place the disposable save
    # there so the native picker can select it without any real player files.
    env['APPDATA'] = str(instance / 'saves')
    with (base / (name + '.log')).open('wb') as log:
        process = subprocess.Popen(['cmd.exe', '/d', '/c', str(package / name)],
                                   cwd=package, env=env, stdin=subprocess.PIPE,
                                   stdout=log, stderr=subprocess.STDOUT)
        try:
            wait_dialog(process, 'OK' if accept else 'Cancel')
            if accept:
                wait_dialog(process, 'Yes')
            process.communicate(input=b'\r\n', timeout=180)
        finally:
            if process.poll() is None:
                subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'], check=False)

before = snapshot(instance)
launch('开始安装.cmd', accept=False)
assert snapshot(instance) == before, 'Cancel modified the instance'
checks.append('Actual CMD + Windows PowerShell 5.1 + folder picker cancellation: no changes')
launch('开始安装.cmd')
assert (instance / 'mods/mstrade-1.7.3.jar').is_file()
assert (world / 'scripts/ms.sc').is_file()
assert 'commandPlayer true' in (world / 'carpet.conf').read_text(encoding='utf-8-sig')
assert 'scriptsAutoload true' in (world / 'carpet.conf').read_text(encoding='utf-8-sig')
checks.append('Actual start-install CMD: picker OK + confirmation Yes installed MS and enabled save rules')
old_world = world.with_name('原来的存档')
world.rename(old_world)
world.mkdir()
(world / 'level.dat').write_bytes(b'SECOND-DISPOSABLE-FIXTURE')
old_before = snapshot(old_world)
mods_before = snapshot(instance / 'mods')
launch('启用已有存档.cmd')
assert snapshot(old_world) == old_before
assert snapshot(instance / 'mods') == mods_before
assert (world / 'scripts/ms.sc').is_file()
checks.append('Actual enable-save CMD: another save enabled with all mod JARs and original save unchanged')
launch('安装含交易筛选.cmd')
assert (instance / 'mods/trade-target-331-1.4.2.jar').is_file()
assert len(list((instance / 'mods').glob('*.jar'))) == 6
assert snapshot(old_world) == old_before
checks.append('Actual combined-install CMD: all six mods present, including officially downloaded Trade Cycling')
(base / 'ui-result.json').write_text(json.dumps({'result': 'PASS', 'checks': checks,
    'limits': 'Disposable save markers; no Minecraft client launched; picker accepted its preselected test save.'},
    ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'result': 'PASS', 'checks': checks}, ensure_ascii=False, indent=2))
