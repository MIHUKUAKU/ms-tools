"""Package only public documentation, own mods and Scarpet scripts."""
import hashlib
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
dist = root / "dist"
dist.mkdir(exist_ok=True)
mods = [root / "ms-tools/build/libs/mstrade-1.7.3.jar",
        root / "trade-target/build/libs/trade-target-331-1.4.2.jar"]
for mod in mods:
    with zipfile.ZipFile(mod) as jar:
        assert jar.testzip() is None, mod
        info = json.loads(jar.read("fabric.mod.json"))
        assert info["depends"]["minecraft"] == ["26.2.x", "26.3.x"], info
    shutil.copy2(mod, dist / mod.name)

files = {"README.md": root / "README.md", "LICENSE": root / "LICENSE"}
for folder in ("docs", "installer"):
    for file in (root / folder).rglob("*"):
        if file.is_file() and file.suffix in (".md", ".ps1", ".cmd"):
            files[file.relative_to(root).as_posix()] = file
for script in ("ms.sc", "fetch.sc", "put.sc", "throw.sc"):
    files[f"payload/scripts/{script}"] = root / "scripts" / script
for mod in mods:
    files[f"payload/mods/{mod.name}"] = mod

checksums = "".join(f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {name}\n"
                    for name, file in sorted(files.items()))
archive = dist / "MS-Tools-1.7.3-Windows-Installer.zip"
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as package:
    for name, file in sorted(files.items()):
        package.writestr(name, file.read_bytes())
    package.writestr("SHA256SUMS.txt", checksums)
with zipfile.ZipFile(archive) as package:
    assert package.testzip() is None
    assert not any(name.endswith((".nbt", ".mca", "level.dat", "chains.json"))
                   for name in package.namelist())

source = dist / 'MS-Tools-1.7.3-Source.zip'
subprocess.run(['git', 'archive', '--format=zip', f'--output={source}', 'HEAD'], cwd=root, check=True)
outputs = [dist / mod.name for mod in mods] + [archive, source]
(dist / "SHA256SUMS.txt").write_text("".join(
    f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {file.name}\n" for file in outputs),
    encoding="utf-8")
for file in outputs:
    print(f"{file.name}: {file.stat().st_size} bytes")
