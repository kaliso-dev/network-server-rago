"""Runs bwsetup.py on every map folder given, in parallel, into an output folder."""
import concurrent.futures
import os
import subprocess
import sys

out = sys.argv[1]
maps = sys.argv[2:]
os.makedirs(out, exist_ok=True)
here = os.path.dirname(os.path.abspath(__file__))


def one(path):
    name = os.path.basename(path.rstrip('/'))
    r = subprocess.run([sys.executable, '-s', os.path.join(here, 'bwsetup.py'), path, name,
                        os.path.join(out, name + '.yml'), os.path.join(out, name + '.png')],
                       capture_output=True, text=True, timeout=600, cwd=here)
    with open(os.path.join(out, name + '.json'), 'w') as f:
        f.write(r.stdout)
    if r.returncode:
        with open(os.path.join(out, name + '.err'), 'w') as f:
            f.write(r.stderr)
    return name, r.returncode


with concurrent.futures.ThreadPoolExecutor(4) as ex:
    for name, code in ex.map(one, maps):
        print(name, code, flush=True)
