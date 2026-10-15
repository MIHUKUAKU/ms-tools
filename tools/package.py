"""Package own tools plus licensed, unmodified dependencies and their notices."""
import hashlib
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
dist = root / "dist"
dist.mkdir(exist_ok=True)
release_version = '1.7.5'
mods = [root / "ms-tools/build/libs/mstrade-1.7.3.jar",
        root / "trade-target/build/libs/trade-target-331-1.4.2.jar"]
for mod in mods:
    with zipfile.ZipFile(mod) as jar:
        assert jar.testzip() is None, mod
        info = json.loads(jar.read("fabric.mod.json"))
        assert info["depends"]["minecraft"] == ["26.2.x", "26.3.x"], info
    shutil.copy2(mod, dist / mod.name)

files = {name: root / name for name in ('README.md', 'README.en.md', 'LICENSE', '开始安装.cmd', '启用已有存档.cmd', '安装含交易筛选.cmd')}
for folder in ("docs", "installer"):
    for file in (root / folder).rglob("*"):
        if file.is_file() and file.suffix in (".md", ".ps1", ".cmd", ".json", ".txt"):
            files[file.relative_to(root).as_posix()] = file
for script in ("ms.sc", "fetch.sc", "put.sc", "throw.sc"):
    files[f"payload/scripts/{script}"] = root / "scripts" / script
for mod in mods:
    files[f"payload/mods/{mod.name}"] = mod
manifest = json.loads((root / 'installer/dependencies.json').read_text(encoding='utf-8'))
for game, dependencies in manifest['games'].items():
    for dependency in dependencies:
        if not dependency['bundled']:
            continue
        assert dependency['license'] in ('MIT', 'Apache-2.0')
        name = f"payload/dependencies/{game}/{dependency['name']}"
        file = root / name
        assert hashlib.sha512(file.read_bytes()).hexdigest() == dependency['sha512']
        files[name] = file

checksums = "".join(f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {name}\n"
                    for name, file in sorted(files.items()))
archive = dist / f"MS-Tools-{release_version}-Windows-Setup.zip"
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as package:
    for name, file in sorted(files.items()):
        package.writestr(name, file.read_bytes())
    package.writestr("SHA256SUMS.txt", checksums)
with zipfile.ZipFile(archive) as package:
    assert package.testzip() is None
    assert not any(name.endswith((".nbt", ".mca", "level.dat", "chains.json"))
                   for name in package.namelist())

source = dist / f'MS-Tools-{release_version}-Source.zip'
subprocess.run(['git', 'archive', '--format=zip', f'--output={source}', 'HEAD'], cwd=root, check=True)
outputs = [dist / mod.name for mod in mods] + [archive, source]
(dist / "SHA256SUMS.txt").write_text("".join(
    f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.name}\n" for file in outputs),
    encoding="utf-8")
for file in outputs:
    print(f"{file.name}: {file.stat().st_size} bytes")
