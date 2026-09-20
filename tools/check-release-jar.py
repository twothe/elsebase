"""Load the packaged mod, save and reopen an isolated world using the resolved NeoForge development runtime.

Run after `gradlew build runGameTestServer`, with JAVA_HOME pointing to Java 21.
No EULA file is modified. This checks the mod JAR, not a production NeoForge installer.
"""
import os
from pathlib import Path
import shutil
import subprocess
import time


def main():
    root = Path(__file__).resolve().parent.parent
    properties = dict(line.split("=", 1) for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines() if "=" in line and not line.startswith("#"))
    artifact = root / "build/libs" / f"{properties['mod_id']}-{properties['mod_version']}.jar"
    runtime = root / "build/moddev"
    fixture = root / "build/release-jar-check"
    mods = fixture / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    # One fixed filename prevents previous versioned test artifacts becoming duplicate mods.
    shutil.copy2(artifact, mods / "elsebase.jar")
    (fixture / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\n", encoding="utf-8")
    vm = (runtime / "gameTestServerRunVmArgs.txt").read_text(encoding="utf-8")
    vm = "\n".join(line for line in vm.splitlines() if not line.startswith("-Dneoforge."))
    (fixture / "vmargs.txt").write_text(vm, encoding="utf-8")
    classpath = (runtime / "gameTestServerLegacyClasspath.txt").read_text(encoding="utf-8").splitlines()
    classpath.append(str(runtime / "artifacts" / f"neoforge-{properties['neo_version']}.jar"))
    java = Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java")
    command = [str(java), "@" + str(fixture / "vmargs.txt"), "-cp", os.pathsep.join(filter(None, classpath)), "@" + str(runtime / "gameTestServerRunProgramArgs.txt"), "--nogui"]
    environment = os.environ.copy()
    for name in ("MOD_CLASSES", "MOD_FOLDERS"):
        environment.pop(name, None)
    for attempt in range(2):
        log_path = fixture / f"launch-{attempt + 1}.log"
        with log_path.open("w", encoding="utf-8") as output:
            process = subprocess.Popen(command, cwd=fixture, env=environment, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True)
            try:
                deadline = time.monotonic() + 120
                while time.monotonic() < deadline:
                    log = log_path.read_text(encoding="utf-8", errors="replace")
                    if "Done (" in log and "Elsebase initialized:" in log:
                        process.communicate("stop\n", timeout=30)
                        if process.returncode != 0:
                            raise RuntimeError(f"Server shutdown failed; inspect {log_path}")
                        break
                    if process.poll() is not None:
                        raise RuntimeError(f"Server exited before readiness; inspect {log_path}")
                    time.sleep(0.5)
                else:
                    raise RuntimeError(f"Server startup timed out; inspect {log_path}")
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()
        print(f"PASS: packaged mod startup, world {'load' if attempt else 'save'}, clean shutdown ({attempt + 1}/2)")


if __name__ == "__main__":
    main()
