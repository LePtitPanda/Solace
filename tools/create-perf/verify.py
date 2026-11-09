"""Apply the actual built mixin to the installed jar offline, then compare Forge slot routing.

Run after gradlew build: py tools/create-perf/verify.py
All generated files stay in this worktree's build/create-perf/verification directory.
"""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/create-perf/verification'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
JAVA = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-17')) / 'bin'

def dependency(group, artifact):
    jars = sorted((CACHE / group / artifact).glob('*/*/*.jar'))
    jars = [p for p in jars if not any(s in p.name for s in ['-sources', '-javadoc', '-processor'])]
    if not jars:
        raise FileNotFoundError(f'Build first: missing {group}:{artifact}')
    return jars[-1]

OUT.mkdir(parents=True, exist_ok=True)
providers = OUT / 'META-INF/services'
providers.mkdir(parents=True, exist_ok=True)
(providers / 'org.spongepowered.asm.service.IMixinService').write_text('VerifyMountedStorage$Service\n')
(providers / 'org.spongepowered.asm.service.IGlobalPropertyService').write_text('VerifyMountedStorage$Properties\n')
(OUT / 'verify.mixins.json').write_text('''{
  "required": true, "minVersion": "0.8.5", "package": "com.solace.mixin",
  "compatibilityLevel": "JAVA_17", "mixins": ["CreateMountedStorageLookupMixin"],
  "injectors": {"defaultRequire": 1}
}''')
# Only a type token: Forge wrapper routing never invokes any Minecraft methods.
# Keeps the verifier independent of game bootstrapping and registry initialization.
(OUT / 'ItemStack.java').write_text('package net.minecraft.world.item; public final class ItemStack {}')
jars = [dependency('org.spongepowered', 'mixin'), dependency('com.google.guava', 'guava'),
        dependency('com.google.code.gson', 'gson')]
jars += [dependency('org.ow2.asm', a) for a in ['asm', 'asm-tree', 'asm-commons', 'asm-util', 'asm-analysis']]
forge = Path.home() / '.gradle/caches/forge_gradle/maven_downloader/net/minecraftforge/forge/1.20.1-47.4.15/forge-1.20.1-47.4.15-universal.jar'
classpath = os.pathsep.join(map(str, [OUT, ROOT / 'build/libs/solace-1.0.jar',
    ROOT.parent / 'mods/create-1.20.1-6.0.8.jar', forge, *jars]))
subprocess.run([str(JAVA / 'javac.exe'), '-proc:none', '-cp', classpath, '-d', str(OUT),
    str(ROOT / 'tools/create-perf/VerifyMountedStorage.java'), str(OUT / 'ItemStack.java')], cwd=ROOT, check=True)
subprocess.run([str(JAVA / 'java.exe'), '-cp', classpath, 'VerifyMountedStorage', str(OUT)], cwd=ROOT, check=True)
