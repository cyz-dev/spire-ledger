"""Self-check for compare.py on a real TinyCat baseline.

1. baseline vs itself: zero differences, exit 0.
2. baseline projected into the Spire Ledger format (inverse of mapping.json) and compared back: no value
   differences and no unregistered TinyCat tables (exit 0).
3. the projection with one planted change per class: (a) holding counted per run, (b) Boss relic SKIP rows
   dropped, (c) average deck size changed. Each must land in its class.
4. invalid rules (wildcard table, quote missing from the doc) are refused with exit 2.
Usage: python -I selftest.py BASELINE.json
"""
import copy
import json
import os
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import compare  # noqa: E402


def to_ledger(base, mapping):
    """Write each compared TinyCat row as the Ledger row it should come from (merged acts use their parts)."""
    rows = {}
    skip_tables = {"cardPicksAfterAct1"}  # = act 2 + acts 3-4; rebuilt by the comparator's merge
    for r in base["rows"]:
        e = compare.entry_for(mapping["entries"], r["table"], r["dims"].get("key"))
        if not e or not e.get("ledger_metric") or r["metric"] != e["tinycat_metric"] or r["table"] in skip_tables:
            continue
        dims = {k: (v[0] if isinstance(v, list) else v) for k, v in e["tables"][r["table"]].items()}
        if e.get("act_dim"):
            dims[e["act_dim"]] = r["dims"]["act"]
        if e.get("key_dim"):
            dims[e["key_dim"]] = r["dims"]["key"]
        k = (e["ledger_metric"], r["scope"], json.dumps(dims, sort_keys=True))
        out = rows.setdefault(k, {"metric": e["ledger_metric"], "scope": r["scope"], "dims": dims, "status": "ok"})
        for tf, lf in e["fields"].items():
            out["_losses" if lf == "denominator-numerator" else lf] = r[tf]
    for out in rows.values():
        if "_losses" in out:
            out["denominator"] = out["numerator"] + out.pop("_losses")
    filters = {k: v for k, v in mapping["required_ledger_filters"].items()}
    filters["save_slot"] = base["meta"]["filters"]["save_slot"]
    meta = {"format": "spire-ledger-metrics", "format_version": 1, "metrics_version": "metrics-v1",
            "source": "selftest projection", "filters": filters, "run_ids": base["meta"]["run_ids"]}
    return {"meta": meta, "rows": list(rows.values())}


def write(tmp, name, doc):
    p = os.path.join(tmp, name)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False)
    return p


def check(cond, msg):
    print(("ok   " if cond else "FAIL ") + msg)
    if not cond:
        check.failed = True


check.failed = False


def main(baseline):
    mapping = compare.load_json(os.path.join(HERE, "mapping.json"))
    base = compare.load_json(baseline)
    with tempfile.TemporaryDirectory() as tmp:
        rep = os.path.join(tmp, "r.json")

        code = compare.main([baseline, baseline, "--report", rep])
        s = compare.load_json(rep)["summary"]
        check(code == 0 and s["differences"] == 0, f"self compare: exit {code}, {s['differences']} differences")

        ledger = to_ledger(base, mapping)
        p = write(tmp, "ledger.json", ledger)
        code = compare.main([baseline, p, "--report", rep])
        r = compare.load_json(rep)
        nc_c = sorted({x["table"] for x in r["not_compared"] if x["category"] == "c"})
        check(code == 0 and r["summary"]["differences"] == 0 and not nc_c,
              f"projection round trip: exit {code}, {r['summary']['differences']} differences, unregistered tables {nc_c}")

        planted = copy.deepcopy(ledger)
        for row in planted["rows"]:
            if row["metric"] == "holding" and row["scope"] == "ALL" and row.get("denominator", 0) > 1:
                row["denominator"] -= 1
                row["numerator"] = min(row["numerator"], row["denominator"])
            if row["metric"] == "deck_size" and row["scope"] == "IRONCLAD":
                row["value"] += 0.5
        planted["rows"] = [x for x in planted["rows"] if not (x["metric"] == "boss_pick" and x["dims"].get("relic") == "SKIP")]
        p = write(tmp, "planted.json", planted)
        code = compare.main([baseline, p, "--report", rep])
        r = compare.load_json(rep)
        d = r["differences"]
        a_ok = d["a"] and all(x["rule"] == "holding_counts_copies" for x in d["a"])
        b_ok = d["b"] and all(x["rule"] == "boss_skip_not_counted" and x["dims"]["key"] == "SKIP" for x in d["b"])
        c_ok = len(d["c"]) == 1 and d["c"][0]["table"] == "averageDeckSize" and d["c"][0]["scope"] == "IRONCLAD"
        check(code == 1 and a_ok and b_ok and c_ok,
              f"planted: exit {code}, a={len(d['a'])} b={len(d['b'])} c={[(x['table'], x['scope'], x['field']) for x in d['c']]}")

        rules = compare.load_json(os.path.join(HERE, "rules.json"))
        for label, mutate in (("wildcard table", lambda r: r["rules"][0].update(tables=["*"])),
                              ("missing quote", lambda r: r["rules"][0]["ref"].update(quote="not in metrics.md"))):
            bad = copy.deepcopy(rules)
            mutate(bad)
            code = compare.main([baseline, baseline, "--rules", write(tmp, "bad.json", bad)])
            check(code == 2, f"invalid rule ({label}) refused: exit {code}")
    return 1 if check.failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
