"""Private local capture controller and reducer. No upload, web server, HUD, or FPS counter."""
import argparse
import collections
import io
import json
import os
import pathlib
import shutil
import subprocess
import time
import uuid
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[3]
OUT = ROOT / "local/solace-audit"


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


def command(request):
    request["requestId"] = uuid.uuid4().hex
    write(OUT / "control.json", request)
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        try:
            status = read(OUT / "status.json")
            if status["requestId"] == request["requestId"]:
                if status["lastError"]:
                    raise RuntimeError(status["lastError"])
                print(json.dumps(status, indent=2))
                return status
        except (OSError, ValueError):
            pass
        time.sleep(.2)
    raise TimeoutError("Audit controller did not acknowledge. Launch the audit Solace jar first.")


def class_index(directory):
    inventory = {item["file"]: item for item in read(ROOT / "docs/performance-audit-2026-10-12/inventory.json")}
    metadata = read(directory / "metadata.json")
    active_files = {m["file"] for m in metadata["mods"]}
    active_ids = {m["id"] for m in metadata["mods"]}
    runtime_by_id = {m["id"]: m["file"] for m in metadata["mods"]}
    runtime_by_file = collections.defaultdict(list)
    for mod in metadata["mods"]:
        runtime_by_file[mod["file"]].append(mod["id"])
    owners = collections.defaultdict(set)
    indexed = []

    def index(data, label, depth=0):
        with zipfile.ZipFile(data) as archive:
            for entry in archive.namelist():
                if entry.endswith(".class") and not entry.startswith("META-INF/versions/") and entry != "module-info.class":
                    owners[entry[:-6].replace("/", ".")].add(label)
                elif entry.endswith(".jar") and entry.startswith("META-INF/") and depth < 3:
                    index(io.BytesIO(archive.read(entry)), "library:" + pathlib.PurePosixPath(entry).name, depth + 1)

    for jar in sorted((ROOT / "mods").glob("*.jar")):
        item = inventory.get(jar.name, {})
        ids = runtime_by_file.get(jar.name) or item.get("mod_ids", [])
        # Exclude a duplicate jar when the loaded file for its mod id can be resolved to another actual root jar.
        resolved = [runtime_by_id[m] for m in ids if m in runtime_by_id and (ROOT / "mods" / runtime_by_id[m]).is_file()]
        if resolved and jar.name not in resolved and jar.name not in active_files:
            continue
        label = "+".join(m for m in ids if m in active_ids) or "+".join(ids) or "jar:" + jar.name
        index(jar, label)
        indexed.append({"file": jar.name, "owner": label})
    path = directory / "class-owners.tsv"
    with path.open("w", encoding="utf-8") as handle:
        for name, candidates in sorted(owners.items()):
            handle.write(name + "\t" + (next(iter(candidates)) if len(candidates) == 1 else "ambiguous:" + "|".join(sorted(candidates))) + "\n")
    write(directory / "class-index.json", {"classes": len(owners), "ambiguousClasses": sum(len(v) > 1 for v in owners.values()), "jars": indexed})
    return path, owners


def analyze(directory, java):
    directory = directory.resolve()
    owner_file, owners = class_index(directory)
    jfr = directory / "profile.jfr"
    if jfr.is_file():
        subprocess.run([java, str(pathlib.Path(__file__).with_name("AnalyzeRecording.java")), str(jfr), str(owner_file),
                        str(directory / "jfr-summary.json")], check=True)
    content = read(directory / "content.json")
    one_in = content["sampleOneIn"]
    merged = {}
    for row in content["rows"]:
        key = (row["category"], row["content"], row["implementation"], row["dimension"])
        target = merged.setdefault(key, dict(zip(("category", "content", "implementation", "dimension"), key),
                samples=0, wallNanos=0, cpuNanos=0, allocatedBytes=0, maxWallNanos=0, threads=[], sampleOneIn=row.get("sampleOneIn", one_in)))
        for field in ("samples", "wallNanos", "cpuNanos", "allocatedBytes"):
            target[field] += row[field]
        if row["maxWallNanos"] > target["maxWallNanos"]:
            target["maxWallNanos"] = row["maxWallNanos"]
            target["worstLocation"] = row.get("worstLocation")
        if row["thread"] not in target["threads"]:
            target["threads"].append(row["thread"])
    for row in merged.values():
        rate = row["sampleOneIn"]
        row["estimatedWallMs"] = row["wallNanos"] * rate / 1e6
        row["estimatedCpuMs"] = row["cpuNanos"] * rate / 1e6
        row["estimatedAllocatedBytes"] = row["allocatedBytes"] * rate
        row["meanObservedWallUs"] = row["wallNanos"] / max(1, row["samples"]) / 1000
        row["sampleConfidence"] = "all observed calls" if rate == 1 else "thin" if row["samples"] < 30 else "sampled"
        row["implementationOwners"] = sorted(owners.get(row["implementation"].split("/", 1)[0].split("$$Lambda", 1)[0], {"unmapped"}))
        row["contentNamespace"] = row["content"].split(":", 1)[0] if ":" in row["content"] else None
    grouped = collections.defaultdict(list)
    for row in merged.values():
        grouped[row["category"]].append(row)
    for category in grouped:
        grouped[category].sort(key=lambda r: r["estimatedWallMs"], reverse=True)
    gpu = sorted(content["gpuRenderNanos"])
    gpu_summary = {"samples": len(gpu), "skipped": content["gpuSkipped"]}
    if gpu:
        for label, fraction in (("medianMs", .5), ("p95Ms", .95), ("p99Ms", .99)):
            gpu_summary[label] = gpu[min(len(gpu) - 1, int(len(gpu) * fraction))] / 1e6
    result = {"schema": 1, "label": content["label"], "complete": content["complete"], "sampleOneIn": one_in,
              "elapsedSeconds": content["elapsedSeconds"], "contentByCategory": grouped, "gpuRender": gpu_summary,
              "scene": content["scene"], "interpretation": content["interpretation"],
              "limits": ["Rare unsampled content is invisible; absent rows cannot establish zero cost.",
                         "Captured scenes determine the ranking. Removal gains require controlled repeats.",
                         "Thread CPU counters can have coarse granularity on Windows; short calls need wall samples and JFR together.",
                         "This machine cannot establish the exact savings on another CPU/GPU."]}
    if (directory / "jfr-summary.json").is_file():
        reduced = read(directory / "jfr-summary.json")
        cpu = collections.Counter()
        native = collections.Counter()
        allocated = collections.Counter()
        for row in reduced["cpu"]:
            target = cpu if row["event"] == "jdk.ExecutionSample" else native
            target[(row["thread"], row["nearestOwner"])] += row["samples"]
        for row in reduced["allocations"]:
            allocated[(row["thread"], row["nearestOwner"])] += row["weightedBytes"]
        result["jfrCpuByThreadAndOwner"] = [dict(thread=t, owner=o, samples=n) for (t, o), n in cpu.most_common()]
        result["jfrNativeByThreadAndOwner"] = [dict(thread=t, owner=o, samples=n) for (t, o), n in native.most_common()]
        result["jfrAllocationByThreadAndOwner"] = [dict(thread=t, owner=o, weightedBytes=n) for (t, o), n in allocated.most_common()]
        result["jfrTotals"] = {k: reduced[k] for k in ("cpuSamplesTotal", "executionSamplesTotal", "nativeSamplesTotal", "allocationWeightedBytesTotal", "eventCounts")}
        result["jfrReductionTruncated"] = reduced["cpuUniqueRows"] > 4000 or reduced["allocationUniqueRows"] > 4000
    write(directory / "attribution.json", result)
    print(json.dumps({"saved": str(directory / "attribution.json"), "contentCategories": list(grouped), "gpu": gpu_summary}, indent=2))


def monitor():
    """Optional coarse NVIDIA pressure context; local executable only, never per-mod attribution."""
    smi = shutil.which("nvidia-smi")
    if not smi:
        raise RuntimeError("nvidia-smi unavailable")
    samples = []
    directory = None
    for _ in range(65):
        status = read(OUT / "status.json")
        if not status["active"]:
            break
        directory = pathlib.Path(status["directory"])
        query = "name,utilization.gpu,utilization.memory,memory.used,memory.total,power.draw,temperature.gpu,clocks.current.graphics"
        result = subprocess.run([smi, "--query-gpu=" + query, "--format=csv,noheader,nounits"], capture_output=True, text=True, timeout=8)
        samples.append({"utc": time.time(), "query": query, "exit": result.returncode, "csv": result.stdout.strip()})
        time.sleep(5)
    if directory:
        write(directory / "gpu-pressure.json", {"scope": "whole adapter; other processes may contribute", "samples": samples})


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="action", required=True)
    start = commands.add_parser("start")
    start.add_argument("label"); start.add_argument("--seconds", type=int, default=60)
    start.add_argument("--sample-one-in", type=int, default=32)
    start.add_argument("--no-probes", action="store_true"); start.add_argument("--no-gpu", action="store_true"); start.add_argument("--no-jfr", action="store_true")
    commands.add_parser("stop"); commands.add_parser("status"); commands.add_parser("monitor-gpu")
    reducer = commands.add_parser("analyze"); reducer.add_argument("directory", type=pathlib.Path)
    reducer.add_argument("--java", default=str(pathlib.Path(os.environ.get("JAVA_HOME", "C:/Program Files/Java/jdk-17")) / "bin/java.exe"))
    args = parser.parse_args()
    if args.action == "analyze":
        analyze(args.directory, args.java)
    elif args.action == "monitor-gpu":
        monitor()
    else:
        request = {"action": args.action}
        if args.action == "start":
            request.update(label=args.label, seconds=args.seconds, sampleOneIn=args.sample_one_in,
                           probes=not args.no_probes, gpu=not args.no_gpu, jfr=not args.no_jfr)
        command(request)
