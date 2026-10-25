"""Compare a TinyCat baseline with Spire Ledger metrics (or another TinyCat baseline), table by table.

Every difference is classified by rules.json as (a) a registered metric decision, (b) a registered defect,
or (c) unclassified. Exit codes: 0 no unclassified difference, 1 unclassified differences, 2 invalid input.
Run with `python -I compare.py BASELINE OTHER [--report out.json]`; see README.md for the formats.
"""
import argparse
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
FIELDS = ("value", "numerator", "denominator", "sample_size")
CONDITIONS = ("any", "tinycat_ge_ledger", "tinycat_le_ledger", "row_only_in_tinycat", "row_only_in_ledger", "tinycat_zero")
LEDGER_FIELDS = ("value", "numerator", "denominator", "sample_size", "estimated", "exact", "denominator-numerator")


class InputError(Exception):
    pass


def load_json(path):
    try:
        with open(path, encoding="utf-8-sig") as f:
            return json.load(f)
    except (OSError, ValueError) as e:
        raise InputError(f"cannot read {path}: {e}")


def ident(table, scope, dims, metric):
    return (table, scope, tuple(sorted((k, "" if v is None else str(v)) for k, v in dims.items())), metric)


def index_rows(rows, what):
    out = {}
    for r in rows:
        for k in ("table", "scope", "dims", "metric"):
            if k not in r:
                raise InputError(f"{what}: row without {k}: {r}")
        i = ident(r["table"], r["scope"], r["dims"], r["metric"])
        if i in out:
            raise InputError(f"{what}: duplicate row {i}")
        out[i] = r
    return out


def zero_like(row, fields):
    return row is None or all(row.get(f) in (None, 0) for f in fields)


def same(a, b):
    if a is None or b is None:
        return a is None and b is None
    if isinstance(a, str) or isinstance(b, str):
        return a == b
    return math.isclose(float(a), float(b), rel_tol=1e-9, abs_tol=1e-9)


def diff_pair(i, t, l, fields):
    """Differences between a TinyCat row t and the aligned other-side row l (either may be None)."""
    table, scope, dims, metric = i
    base = {"table": table, "scope": scope, "dims": dict(dims), "metric": metric}
    if t is None or l is None:
        if zero_like(t if l is None else l, fields):
            return []
        return [dict(base, field="presence", tinycat=t is not None, ledger=l is not None)]
    return [dict(base, field=f, tinycat=t.get(f), ledger=l.get(f)) for f in fields if not same(t.get(f), l.get(f))]


# ---- rules ----

def check_ref(ref, repo, where):
    for k in ("doc", "section", "quote"):
        if not ref.get(k):
            raise InputError(f"{where}: ref needs {k}")
    path = os.path.join(repo, ref["doc"])
    try:
        with open(path, encoding="utf-8") as f:
            text = f.read()
    except OSError as e:
        raise InputError(f"{where}: cannot read cited doc {path}: {e}")
    if ref["quote"] not in text:
        raise InputError(f"{where}: quote not found in {ref['doc']}: {ref['quote']}")


def load_rules(path, repo, known_tables):
    rules = load_json(path).get("rules", [])
    for r in rules:
        where = f"rule {r.get('id')}"
        if r.get("category") not in ("a", "b"):
            raise InputError(f"{where}: category must be a or b")
        tables = r.get("tables")
        if not tables or not isinstance(tables, list):
            raise InputError(f"{where}: needs an explicit list of tables")
        for t in tables:
            if t not in known_tables:
                raise InputError(f"{where}: unknown table {t}")
        if not r.get("fields") or any(f not in FIELDS + ("presence",) for f in r["fields"]):
            raise InputError(f"{where}: fields must be a non-empty subset of {FIELDS + ('presence',)}")
        if r.get("condition") not in CONDITIONS:
            raise InputError(f"{where}: condition must be one of {CONDITIONS}")
        check_ref(r.get("ref") or {}, repo, where)
    return rules


def num(v):
    if isinstance(v, bool):
        return int(v)
    return v if isinstance(v, (int, float)) else None


def condition_holds(cond, d):
    t, l = d["tinycat"], d["ledger"]
    if cond == "any":
        return True
    if cond == "row_only_in_tinycat":
        return d["field"] == "presence" and t is True
    if cond == "row_only_in_ledger":
        return d["field"] == "presence" and l is True
    if cond == "tinycat_zero":
        return d["field"] != "presence" and t in (0, None)
    nt, nl = num(t), num(l)
    if nt is None or nl is None:
        return False
    return nt > nl if cond == "tinycat_ge_ledger" else nt < nl


def classify(d, rules):
    for r in rules:
        if d["table"] not in r["tables"] or d["field"] not in r["fields"]:
            continue
        if r.get("scopes") and d["scope"] not in r["scopes"]:
            continue
        if any(d["dims"].get(k) not in vals for k, vals in (r.get("match_dims") or {}).items()):
            continue
        if condition_holds(r["condition"], d):
            return r
    return None


# ---- mapping ----

def load_mapping(path, repo):
    m = load_json(path)
    for e in m["entries"]:
        where = f"mapping {e.get('id')}"
        if e.get("ledger_metric") is None:
            if e.get("category") not in ("a", "b", "c"):
                raise InputError(f"{where}: not-compared entry needs category a, b or c")
            if e["category"] != "c":
                if not e.get("refs"):
                    raise InputError(f"{where}: category {e['category']} needs refs")
                for ref in e["refs"]:
                    check_ref(ref, repo, where)
        else:
            for tf, lf in e["fields"].items():
                if tf not in FIELDS or lf not in LEDGER_FIELDS:
                    raise InputError(f"{where}: bad field mapping {tf}->{lf}")
    return m


def key_ok(e, key):
    if "key_value" in e:
        return key == e["key_value"]
    if "only_keys" in e:
        return key in e["only_keys"]
    if "exclude_keys" in e:
        return key not in e["exclude_keys"]
    return True


def entry_for(entries, table, key):
    hits = [e for e in entries
            if (table in e.get("tables", {}) or (e.get("table_prefix") and table.startswith(e["table_prefix"])))
            and key_ok(e, key)]
    if len(hits) > 1:
        raise InputError(f"mapping: table {table} key {key} matches {[e['id'] for e in hits]}")
    return hits[0] if hits else None


def ledger_value(row, expr):
    if expr == "denominator-numerator":
        n, d = row.get("numerator"), row.get("denominator")
        return None if n is None or d is None else d - n
    return row.get(expr)


def dim_match(actual, want):
    return actual in want if isinstance(want, list) else actual == want


def convert_ledger(rows, mapping):
    """Project Spire Ledger metric rows into TinyCat table space; returns {ident: row}, unmatched metric names."""
    entries = [e for e in mapping["entries"] if e.get("ledger_metric")]
    aliases = mapping.get("aliases", {})
    out, merged, used = {}, set(), set()
    for r in rows:
        for e in entries:
            if r.get("metric") != e["ledger_metric"]:
                continue
            for table, fixed in e["tables"].items():
                dims = r.get("dims") or {}
                allowed = set(fixed) | {x for x in (e.get("act_dim"), e.get("key_dim")) if x}
                if set(dims) != allowed or not all(dim_match(dims.get(k), v) for k, v in fixed.items()):
                    continue
                tdims = {}
                if e.get("act_dim"):
                    tdims["act"] = str(dims[e["act_dim"]])
                if e.get("key_dim"):
                    key = dims[e["key_dim"]]
                    key = aliases.get(e.get("alias"), {}).get(key, key)
                    if not key_ok(e, key):
                        continue
                    tdims["key"] = key
                elif "key_value" in e:
                    tdims["key"] = e["key_value"]
                i = ident(table, r["scope"], tdims, e["tinycat_metric"])
                proj = {tf: ledger_value(r, lf) for tf, lf in e["fields"].items()}
                used.add(r["metric"])
                if i in out:
                    if not any(isinstance(v, list) for v in fixed.values()):
                        raise InputError(f"ledger: two rows map to {i}")
                    for f, v in proj.items():
                        old = out[i].get(f)
                        out[i][f] = None if old is None or v is None else old + v
                    merged.add(i)
                else:
                    out[i] = dict(proj)
    return out, sorted({r.get("metric") for r in rows} - used)


# ---- compare ----

def compare_runs(base_meta, other_meta):
    diffs = []
    reasons = {rid: reason for reason, ids in base_meta.get("excluded_runs", {}).items() for rid in ids}
    b, o = base_meta.get("run_ids") or {}, other_meta.get("run_ids")
    if o is None:
        raise InputError("other input: meta.run_ids is required")
    for scope in sorted(set(b) | set(o)):
        bs, os_ = set(b.get(scope, [])), set(o.get(scope, []))
        for rid in sorted(bs ^ os_):
            dims = {"key": rid, "reason": "included" if rid in bs else reasons.get(rid, "not_scanned")}
            diffs.append({"table": "_runs", "scope": scope, "dims": dims, "metric": "run", "field": "presence",
                          "tinycat": rid in bs, "ledger": rid in os_})
    return diffs


def check_filters(mapping, base_meta, other_meta):
    want = mapping.get("required_ledger_filters", {})
    got = other_meta.get("filters") or {}
    bad = []
    for k, v in want.items():
        if v == "$baseline.save_slot":
            v = base_meta["filters"]["save_slot"]
        if got.get(k) != v:
            bad.append(f"{k}: expected {v!r}, got {got.get(k)!r}")
    if bad:
        raise InputError("ledger export filters do not match the TinyCat alignment: " + "; ".join(bad))


def run(baseline_path, other_path, mapping_path, rules_path, repo):
    base = load_json(baseline_path)
    other = load_json(other_path)
    if base.get("meta", {}).get("format") != "tinycat-baseline":
        raise InputError("first input must be a tinycat-baseline file")
    mapping = load_mapping(mapping_path, repo)
    known = {t for e in mapping["entries"] for t in e.get("tables", {})} | {"_runs"}
    rules = load_rules(rules_path, repo, known)
    brows = index_rows(base["rows"], "baseline")
    fmt = other.get("meta", {}).get("format")
    diffs, not_compared, ledger_only = [], {}, []

    if fmt == "tinycat-baseline":
        orows = index_rows(other["rows"], "other")
        for i in sorted(set(brows) | set(orows)):
            diffs += diff_pair(i, brows.get(i), orows.get(i), FIELDS)
    elif fmt == "spire-ledger-metrics":
        check_filters(mapping, base["meta"], other["meta"])
        orows, ledger_only = convert_ledger(other["rows"], mapping)
        compared = set()
        for i, row in brows.items():
            table, scope, dims, metric = i
            e = entry_for(mapping["entries"], table, dict(dims).get("key"))
            if e is None or (e.get("ledger_metric") and metric != e["tinycat_metric"]):
                e = {"id": "unmapped", "category": "c", "reason": "TinyCat 表或指标没有对应的映射条目"}
            if not e.get("ledger_metric"):
                rec = not_compared.setdefault((table, scope, e["id"]), {"table": table, "scope": scope, "entry": e["id"],
                                                                     "category": e["category"], "reason": e.get("reason"),
                                                                     "refs": e.get("refs", []), "rows": 0})
                rec["rows"] += 1
                continue
            compared.add(i)
            diffs += diff_pair(i, row, orows.get(i), list(e["fields"]))
        for i in sorted(set(orows) - compared):
            e = entry_for(mapping["entries"], i[0], dict(i[2]).get("key"))
            diffs += diff_pair(i, None, orows[i], list(e["fields"]))
    else:
        raise InputError(f"second input has unknown meta.format {fmt!r}")
    diffs += compare_runs(base["meta"], other["meta"])

    classified = {"a": [], "b": [], "c": []}
    for d in diffs:
        r = classify(d, rules)
        if r:
            d["rule"] = r["id"]
            d["ref"] = r["ref"]
            classified[r["category"]].append(d)
        else:
            classified["c"].append(d)
    nc = sorted(not_compared.values(), key=lambda x: (x["table"], x["scope"]))
    return {
        "baseline": os.path.abspath(baseline_path),
        "other": os.path.abspath(other_path),
        "other_format": fmt,
        "summary": {
            "differences": len(diffs),
            "a_registered_decision": len(classified["a"]),
            "b_registered_defect": len(classified["b"]),
            "c_unclassified": len(classified["c"]),
            "not_compared_tables": {c: sum(1 for x in nc if x["category"] == c) for c in "abc"},
            "ledger_only_metrics": ledger_only,
        },
        "differences": classified,
        "not_compared": nc,
    }


def main(argv=None):
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("baseline")
    p.add_argument("other")
    p.add_argument("--mapping", default=os.path.join(HERE, "mapping.json"))
    p.add_argument("--rules", default=os.path.join(HERE, "rules.json"))
    p.add_argument("--repo", default=os.path.dirname(os.path.dirname(HERE)), help="repository root holding the cited docs")
    p.add_argument("--report", help="write the full report as JSON here")
    a = p.parse_args(argv)
    try:
        report = run(a.baseline, a.other, a.mapping, a.rules, a.repo)
    except InputError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    if a.report:
        with open(a.report, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=1)
    s = report["summary"]
    print(json.dumps(s, ensure_ascii=False))
    nc_c = [x for x in report["not_compared"] if x["category"] == "c"]
    for d in report["differences"]["c"][:20]:
        print("UNCLASSIFIED", json.dumps(d, ensure_ascii=False))
    for x in nc_c[:20]:
        print("UNREGISTERED TABLE", x["table"], x["scope"], x["reason"])
    return 1 if s["c_unclassified"] or nc_c else 0


if __name__ == "__main__":
    sys.exit(main())
