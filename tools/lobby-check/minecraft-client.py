#!/usr/bin/env python3
"""Downloads the Minecraft 1.8.9 client for Linux from Mojang, for the billboard test.

Usage: minecraft-client.py <cache dir>

Into the cache dir: the client jar, its libraries and their native libraries (extracted into
natives/), each file checked against the SHA-1 of Mojang's metadata, and kept for the next run.
Prints the classpath (the libraries, then the client) on its last line. No assets: the client
draws with the textures of its jar, without sounds or other languages.
"""
import hashlib
import json
import os
import sys
import time
import urllib.request
import zipfile

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
VERSION = "1.8.9"


def fetch(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(url, timeout=60) as response:
                return response.read()
        except OSError as error:
            if attempt == 3:
                raise
            print("retrying %s (%s)" % (url, error), file=sys.stderr)
            time.sleep(5 * (attempt + 1))


def sha1(data):
    return hashlib.sha1(data).hexdigest()


def download(entry, path):
    """The file of a download entry (url, sha1) at path, fetched only if missing or different."""
    if os.path.isfile(path):
        with open(path, "rb") as file:
            if sha1(file.read()) == entry["sha1"]:
                return path
    data = fetch(entry["url"])
    if sha1(data) != entry["sha1"]:
        sys.exit("SHA-1 of %s is %s, expected %s" % (entry["url"], sha1(data), entry["sha1"]))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path + ".part", "wb") as file:
        file.write(data)
    os.replace(path + ".part", path)
    return path


def allowed(library):
    """The rules of a library for Linux: without rules, always."""
    rules = library.get("rules")
    if not rules:
        return True
    result = False
    for rule in rules:
        system = rule.get("os", {}).get("name")
        if system is None or system == "linux":
            result = rule["action"] == "allow"
    return result


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    cache = os.path.abspath(sys.argv[1])
    manifest = json.loads(fetch(MANIFEST))
    version = next(v for v in manifest["versions"] if v["id"] == VERSION)
    data = fetch(version["url"])
    if sha1(data) != version["sha1"]:
        sys.exit("SHA-1 of the metadata of %s is %s, expected %s" % (VERSION, sha1(data), version["sha1"]))
    meta = json.loads(data)
    natives = os.path.join(cache, "natives")
    os.makedirs(natives, exist_ok=True)
    classpath = []
    for library in meta["libraries"]:
        if not allowed(library):
            continue
        downloads = library.get("downloads", {})
        if "artifact" in downloads:
            artifact = downloads["artifact"]
            classpath.append(download(artifact, os.path.join(cache, "libraries", artifact["path"])))
        classifier = library.get("natives", {}).get("linux")
        if classifier:
            entry = downloads["classifiers"][classifier.replace("${arch}", "64")]
            jar = download(entry, os.path.join(cache, "libraries", entry["path"]))
            excluded = library.get("extract", {}).get("exclude", [])
            with zipfile.ZipFile(jar) as archive:
                for name in archive.namelist():
                    if name.endswith("/") or any(name.startswith(prefix) for prefix in excluded):
                        continue
                    target = os.path.join(natives, os.path.basename(name))
                    with open(target, "wb") as file:
                        file.write(archive.read(name))
    classpath.append(download(meta["downloads"]["client"], os.path.join(cache, "versions", VERSION + ".jar")))
    print(os.pathsep.join(classpath))


if __name__ == "__main__":
    main()
