#!/usr/bin/env python3
"""Independent check of Standardized.computeBoss against a Java dump.

Parses the native .run files itself, rebuilds every boss relic screen as
[picked, other offers..., SKIP] in act order, reruns the same Plackett-Luce
MM fit, and compares the strength shares with build/boss-shares.tsv written by
build/probe/spireledger/stats/BossDump.java.

Usage: python test/check_boss_standardized.py --runs <runs dir> --slot 0 --java build/boss-shares.tsv
"""

import argparse
import hashlib
import json
import math
import os
import re
import sys

SKIP = "SKIP"
PRIOR_WINS = 1.0
PRIOR_SURVIVAL = 0.01
TOL = 1e-9
FLOOR = 1e-12
MAX_ITER = 2000
SLOT_DIR = re.compile(r"^(\d+)_")


def read_run(path):
    raw = open(path, "rb").read()
    for codec in ("utf-8", "gbk"):
        try:
            text = raw.decode(codec)
            break
        except UnicodeDecodeError:
            continue
    else:
        text = raw.decode(sys.getdefaultencoding(), errors="replace")
    if text.startswith("\ufeff"):
        text = text[1:]
    return json.loads(text)


def outcome(run):
    killed = run.get("killed_by") or ""
    abandoned = (run.get("abandoned") is True or run.get("is_abandoned") is True
                 or killed.lower() == "abandoned")
    if abandoned:
        return "abandon"
    victory = run.get("victory")
    if victory is None:
        return None
    if victory:
        return "victory"
    return "abandon" if not killed else "death"


def reason(run, dirname, slot):
    if run.get("dataset_origin") == "test_fixture":
        return "fixture"
    if run.get("session_kind") == "practice":
        return "practice"
    if any(run.get(k) is True for k in ("is_daily", "is_endless", "is_trial")):
        return "mode"
    match = SLOT_DIR.match(dirname)
    if (int(match.group(1)) if match else 0) != slot:
        return "slot"
    result = outcome(run)
    if result == "abandon":
        return "abandon"
    if result == "incomplete":
        return "incomplete"
    return None


def blocks(run):
    """Every boss screen as [picked, other offers..., SKIP], matching Native + computeBoss."""
    out = []
    for index, entry in enumerate(run.get("boss_relics") or []):
        if not isinstance(entry, dict):
            continue
        picked = entry.get("picked") or ""
        if picked:
            block = [picked]
        else:
            picked = SKIP
            block = [SKIP]
        seen = set(block)
        for relic in entry.get("not_picked") or []:
            if relic and relic not in seen:
                seen.add(relic)
                block.append(relic)
        if SKIP not in seen:
            block.append(SKIP)
        out.append((index + 1, block))
    return out


def fit(blocks_for_act):
    universe = {}
    for block in blocks_for_act:
        if len(block) < 2:
            continue
        for relic in block:
            if relic and relic not in universe:
                universe[relic] = len(universe)
    if len(universe) < 2:
        return None, 0
    groups = []
    for block in blocks_for_act:
        ids = []
        for relic in block:
            index = universe.get(relic)
            if index is None or index in ids:
                continue
            ids.append(index)
        if len(ids) >= 2:
            groups.append(ids)
    if len(groups) < 2:
        return None, len(groups)

    size = len(universe)
    wins = [0.0] * size
    seen = [False] * size
    for group in groups:
        wins[group[0]] += 1.0
        for index in group:
            seen[index] = True

    strength = [1.0] * size
    for _ in range(MAX_ITER):
        denom = [0.0] * size
        for group in groups:
            total = 0.0
            for index in group:
                total += strength[index]
            if total <= 0.0:
                continue
            part = 1.0 / total
            for index in group:
                denom[index] += part
        updated = [0.0] * size
        log_sum = 0.0
        active = 0
        for index in range(size):
            if not seen[index]:
                continue
            value = max((wins[index] + PRIOR_WINS) / (denom[index] + PRIOR_SURVIVAL), FLOOR)
            updated[index] = value
            log_sum += math.log(value)
            active += 1
        if active == 0:
            break
        geo = math.exp(log_sum / active)
        max_rel = 0.0
        for index in range(size):
            if not seen[index]:
                continue
            value = max(updated[index] / geo, FLOOR)
            max_rel = max(max_rel, abs(value / strength[index] - 1.0))
            strength[index] = value
        if max_rel < TOL:
            break

    total = sum(strength[i] for relic, i in universe.items() if relic != SKIP and seen[i])
    if total <= 0.0:
        return {}, len(groups)
    share = {relic: 100.0 * strength[i] / total
             for relic, i in universe.items() if relic != SKIP and seen[i]}
    return share, len(groups)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", required=True)
    parser.add_argument("--slot", type=int, default=0)
    parser.add_argument("--java", required=True, help="BossDump TSV")
    args = parser.parse_args()

    # Same play_id dedupe as Stats.build, before the slot filter: identical copies collapse to the
    # first file in directory order (a run copied into another slot keeps that slot's directory).
    by_play, files = {}, 0
    for dirname in sorted(os.listdir(args.runs)):
        directory = os.path.join(args.runs, dirname)
        if not os.path.isdir(directory):
            continue
        for name in sorted(os.listdir(directory)):
            if not name.endswith(".run"):
                continue
            files += 1
            try:
                run = read_run(os.path.join(directory, name))
            except Exception as error:  # noqa: BLE001 - reported like Stats.scan
                print("unreadable", name, error)
                continue
            key = run.get("play_id") or "native-file:" + dirname + "/" + name
            digest = hashlib.sha256(json.dumps(run, sort_keys=True, ensure_ascii=False).encode("utf-8")).hexdigest()
            by_play.setdefault(key, []).append((digest, dirname, name, run))

    selected, excluded, conflicts = [], {}, 0
    for copies in by_play.values():
        if len({copy[0] for copy in copies}) > 1:
            conflicts += 1
            excluded["conflict"] = excluded.get("conflict", 0) + 1
            continue
        digest, dirname, name, run = copies[0]
        why = reason(run, dirname, args.slot)
        if why:
            excluded[why] = excluded.get(why, 0) + 1
            continue
        selected.append((dirname, name, run))
    runs = [run for _, _, run in selected]
    duplicates = files - len(by_play)
    if conflicts:
        print("conflicts", conflicts)

    per_act = {}
    for run in runs:
        for act, block in blocks(run):
            per_act.setdefault(act, []).append(block)

    java = {}
    for line in open(args.java, encoding="utf-8").read().splitlines()[1:]:
        act, relic, share = line.split("\t")[:3]
        java[(int(act), relic)] = float(share)

    worst = 0.0
    failures = 0
    print("files=%d duplicates=%d selected=%d excluded=%s blocks=%s" % (
        files, duplicates, len(runs), dict(sorted(excluded.items())),
        {k: len(v) for k, v in sorted(per_act.items())}))
    for act in sorted(per_act):
        share, screens = fit(per_act[act])
        if share is None:
            print("act %d: not fittable" % act)
            continue
        rows = sorted(share.items(), key=lambda row: -row[1])
        java_rows = {relic: value for (a, relic), value in java.items() if a == act}
        diffs = [abs(value - java_rows.get(relic, float("nan"))) for relic, value in share.items()]
        missing = set(java_rows) ^ set(share)
        act_worst = max(diffs) if diffs else 0.0
        worst = max(worst, act_worst)
        ok = act_worst < 1e-7 and not missing and len(java_rows) == len(share)
        failures += 0 if ok else 1
        print("act %d: screens=%d cards=%d max|java-python|=%.3e %s" % (
            act, screens, len(share), act_worst, "OK" if ok else "MISMATCH"))
        if missing:
            print("  rows only in one side:", sorted(missing))
        for relic, value in rows[:8]:
            print("  %-30s java=%9.5f  python=%9.5f" % (relic, java_rows.get(relic, float("nan")), value))
    if len(java) != sum(1 for _ in java) or failures:
        pass
    print("overall max|java-python|=%.3e" % worst)
    print("PASS" if failures == 0 and worst < 1e-7 else "FAIL")
    return 0 if failures == 0 and worst < 1e-7 else 1


if __name__ == "__main__":
    sys.exit(main())
