"""Resolve official Fabric dependencies and cache licensed redistributable files.

Run explicitly when preparing a release, then review/commit dependencies.json.
Trade Cycling is downloaded by the installer, never redistributed in our ZIP.
"""
import hashlib
import io
import base64
import json
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / "payload" / "dependencies"
CACHE.mkdir(parents=True, exist_ok=True)

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "ms-tools-release/1.7.4"})
    with urllib.request.urlopen(req, timeout=120) as response:
        return response.read()

projects = [("carpet", "carpet", "0", True),
            ("fabric-api", "fabric-api", "0", True),
            ("trade-cycling", "trade_cycling", "1.0.21", False),
            ("visible-traders", "visibletraders", "2.5.0", True)]
manifest = {"schema": 1, "release": "1.7.4", "games": {}}
for game in ("26.2", "26.3"):
    records = []
    for slug, mod_id, minimum, bundle in projects:
        project = json.loads(get(f"https://api.modrinth.com/v2/project/{slug}"))
        license_id = project["license"]["id"]
        if bundle and license_id not in ("MIT", "Apache-2.0"):
            raise RuntimeError(f"Review redistribution license: {slug}: {license_id}")
        query = urllib.parse.urlencode({"loaders": '["fabric"]', "game_versions": json.dumps([game])})
        versions = json.loads(get(f"https://api.modrinth.com/v2/project/{slug}/version?{query}"))
        releases = sorted((v for v in versions if v["version_type"] == "release"),
                          key=lambda v: v["date_published"], reverse=True)
        version = releases[0]
        file = next((f for f in version["files"] if f["primary"]), version["files"][0])
        name = file["filename"]
        assert Path(name).name == name and name.endswith(".jar")
        data = get(file["url"])
        assert hashlib.sha512(data).hexdigest() == file["hashes"]["sha512"]
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            info = json.loads(jar.read("fabric.mod.json"))
            assert info["id"] == mod_id
        if bundle:
            target = CACHE / game / name
            target.parent.mkdir(exist_ok=True)
            target.write_bytes(data)
            if slug == "visible-traders":
                # The Modrinth license link can point to an obsolete branch.
                license_metadata = json.loads(get("https://api.github.com/repos/Ramixin/VisibleTraders/license"))
                license_url = license_metadata["download_url"]
                license_text = base64.b64decode(license_metadata['content'])
                assert b"MIT License" in license_text and b"Copyright" in license_text
                notices = ROOT / "installer" / "licenses"
                notices.mkdir(exist_ok=True)
                (notices / "Visible-Traders-LICENSE.txt").write_bytes(license_text)
        records.append(dict(slug=slug, id=mod_id, minimum=minimum,
                            version=info["version"], versionId=version["id"],
                            name=name, url=file["url"], sha512=file["hashes"]["sha512"],
                            projectUrl=f"https://modrinth.com/mod/{slug}", license=license_id,
                            bundled=bundle))
        print(game, slug, version["version_number"], license_id, "bundled" if bundle else "official download")
    manifest["games"][game] = records
(ROOT / "installer" / "dependencies.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
