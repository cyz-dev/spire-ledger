#!/usr/bin/env python3
"""Independent check of the overview means and 95% intervals.

Reads the native .run files directly, rebuilds the same slot/selection rules as
Stats.select, recomputes TinyCat's deck size, score and removal families, and
compares the means plus their intervals with build/overview.tsv written by
build/probe/spireledger/stats/OverviewDump.java.

Usage: python test/check_overview.py --runs <runs dir> --slot 0 --java build/overview.tsv --ids build/overview-ids.tsv
"""

import argparse
import hashlib
import json
import math
import os
import re
import sys

SLOT_DIR = re.compile(r"^(\d+)_")
Z = 1.96


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


def heart_win(run):
    """Stats.win(heart=True): only a victory that killed the Heart wins; any death or act-3 win loses."""
    if run.get("victory") is not True:
        return False
    return any((fight.get("enemies") == "The Heart") for fight in run.get("damage_taken") or [] if isinstance(fight, dict))


def purges(run):
    """TinyCat: shop + event + Neow + campfire PURGE + Empty Cage(x2); None when no field is recorded."""
    if not any(k in run for k in ("items_purged", "event_choices", "campfire_choices", "relics", "neow_bonus_log")):
        return None
    shop = len(run.get("items_purged") or [])
    event = sum(len(choice.get("cards_removed") or []) for choice in run.get("event_choices") or [] if isinstance(choice, dict))
    neow_log = run.get("neow_bonus_log")
    neow = len(neow_log.get("cards_removed") or []) if isinstance(neow_log, dict) else 0
    campfire = sum(1 for choice in run.get("campfire_choices") or []
                   if isinstance(choice, dict) and choice.get("key") == "PURGE" and choice.get("data"))
    cage = 2 if "Empty Cage" in (run.get("relics") or []) else 0
    return {"shop": shop, "event": event, "total": shop + event + neow + campfire + cage}


def mean_interval(values):
    n = len(values)
    mean = sum(values) / n
    if n < 2:
        return mean, 0.0, mean, mean
    variance = (sum(value * value for value in values) - sum(values) ** 2 / n) / (n - 1)
    variance = max(0.0, variance)
    half = Z * math.sqrt(variance / n)
    return mean, variance, max(0.0, mean - half), mean + half


def wilson(wins, n):
    p = wins / n
    z2 = Z * Z
    center = (p + z2 / (2 * n)) / (1 + z2 / n)
    half = Z * math.sqrt(p * (1 - p) / n + z2 / (4 * n * n)) / (1 + z2 / n)
    return max(0.0, center - half) * 100, min(1.0, center + half) * 100


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", required=True)
    parser.add_argument("--slot", type=int, default=0)
    parser.add_argument("--java", required=True)
    parser.add_argument("--ids", required=True)
    args = parser.parse_args()

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
        selected.append((run.get("play_id") or "native-file:" + dirname + "/" + name, run))

    java = {}
    for line in open(args.java, encoding="utf-8").read().splitlines()[1:]:
        key, numerator, denominator, samples, value, variance, low, high = line.split("\t")
        java[key] = {"n": int(float(denominator)), "samples": int(samples), "value": float(value),
                     "variance": float(variance), "low": float(low), "high": float(high)}
    java_ids = [line for line in open(args.ids, encoding="utf-8").read().splitlines() if line]
    python_ids = sorted(play for play, _ in selected)
    java_ids_sorted = sorted(java_ids)

    wins = [1 if heart_win(run) else 0 for _, run in selected]
    decks = [len(run.get("master_deck") or []) for _, run in selected if "master_deck" in run]
    scores = [run["score"] for _, run in selected if run.get("score") is not None]
    purge_rows = [purges(run) for _, run in selected]
    purge_rows = [row for row in purge_rows if row is not None]

    python = {}
    low, high = wilson(sum(wins), len(wins))
    python["win"] = {"n": len(wins), "value": sum(wins) / len(wins), "low": low, "high": high}
    for key, values in (("deck_size", decks), ("score", scores)):
        mean, variance, low, high = mean_interval(values)
        python[key] = {"n": len(values), "value": mean, "variance": variance, "low": low, "high": high}
    for key, field in (("purge_shop", "shop"), ("purge_event", "event"), ("purge_total", "total")):
        mean, variance, low, high = mean_interval([row[field] for row in purge_rows])
        python[key] = {"n": len(purge_rows), "value": mean, "variance": variance, "low": low, "high": high}

    failures = 0
    print("files=%d selected=%d excluded=%s duplicates=%d" % (files, len(selected), dict(sorted(excluded.items())), files - len(by_play)))
    if python_ids != java_ids_sorted:
        failures += 1
        print("selected play_id list differs: java=%d python=%d" % (len(java_ids_sorted), len(python_ids)))
        print("  only java:", sorted(set(java_ids_sorted) - set(python_ids))[:5])
        print("  only python:", sorted(set(python_ids) - set(java_ids_sorted))[:5])
    for key in ("win", "deck_size", "score", "purge_total", "purge_shop", "purge_event"):
        row = python[key]
        expected = java[key]
        diffs = [abs(row["value"] - expected["value"]), abs(row["low"] - expected["low"]), abs(row["high"] - expected["high"])]
        ok = row["n"] == expected["n"] and max(diffs) < 1e-6
        failures += 0 if ok else 1
        print("%-12s n=%d  java=%12.6f [%.6f, %.6f]  python=%12.6f [%.6f, %.6f]  maxdiff=%.2e %s" % (
            key, row["n"], expected["value"], expected["low"], expected["high"], row["value"], row["low"], row["high"], max(diffs), "OK" if ok else "MISMATCH"))
    print("PASS" if failures == 0 else "FAIL")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
