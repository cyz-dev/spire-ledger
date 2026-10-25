"""Compare complete serialized gameplay fields and finite supplementary state.

Only the named screen-layout fields are omitted. Existing card UUIDs must match
at the saved point; future generated UUIDs are incidental and compared by card
state and pile position instead. This is a native fixture check, not run coverage.
"""
import json
import sys
from pathlib import Path

VISUAL = {'inspect_hb', 'hb', 'health_hb', 'intent_hb', 'hb_x', 'hb_y', 'hb_w',
          'hb_h', 'hb_alpha', 'draw_x', 'draw_y', 'dialog_x', 'dialog_y',
          'anim_x', 'anim_y', 'reticle_alpha', 'reticle_rendered', 'intent_alpha',
          'intent_alpha_target', 'intent_offset_x', 'previous_screen_name'}

def load(root, name):
    return json.loads((root / name).read_text(encoding='utf-8'))

EXISTING_UUIDS = set()

def uuids(value):
    if isinstance(value, list):
        for v in value:
            yield from uuids(v)
    elif isinstance(value, dict):
        if isinstance(value.get('uuid'), str):
            yield value['uuid']
        for v in value.values():
            yield from uuids(v)

def logical(value, future=False, new_ids=None):
    if new_ids is None:
        new_ids = {}
    if isinstance(value, list):
        return [logical(v, future, new_ids) for v in value]
    if isinstance(value, dict):
        out = {k: logical(v, future, new_ids) for k, v in value.items() if k not in VISUAL}
        if future and 'uuid' in out and out['uuid'] not in EXISTING_UUIDS:
            out['uuid'] = 'new:' + str(new_ids.setdefault(out['uuid'], len(new_ids)))
        if out.get('id') == 'Potion Slot':
            out.pop('slot', None)  # Empty UI placeholders -1 -> actual slot.
        if isinstance(out.get('wait_timer'), (int, float)) and out['wait_timer'] <= 0:
            out['wait_timer'] = 0
        if out.get('creature', {}).get('is_dead') and out.get('death_timer', 0) < 0:
            out['death_timer'] = 0  # Completed corpse animation, never a live timer.
        return out
    return value

def differences(a, b, path=''):
    if type(a) is not type(b):
        yield (path, a, b)
    elif isinstance(a, dict):
        for k in sorted(a.keys() | b.keys()):
            if k not in a or k not in b:
                yield (path + '/' + k, a.get(k), b.get(k))
            else:
                yield from differences(a[k], b[k], path + '/' + k)
    elif isinstance(a, list):
        if len(a) != len(b):
            yield (path + '/length', len(a), len(b))
        for i, (x, y) in enumerate(zip(a, b)):
            yield from differences(x, y, path + '/' + str(i))
    elif a != b:
        yield (path, a, b)

def main():
    source, restored = map(Path, sys.argv[1:3])
    EXISTING_UUIDS.update(uuids(load(source, 'turn.json')))
    checks = {}
    detail = {}
    for name, x, y, future in [
        ('serialized_before', 'turn.json', 'restored-state.json', False),
        ('serialized_after', 'after-state.json', 'after-state.json', True),
        ('supplement_before', 'supplement.json', 'restored-supplement.json', False),
        ('supplement_after', 'after-supplement.json', 'after-supplement.json', True),
        ('facts_before', 'before-facts.json', 'restored-facts.json', False),
        ('facts_after', 'after-facts.json', 'after-facts.json', False),
    ]:
        a, b = load(source, x), load(restored, y)
        if '--production' in sys.argv and name.startswith('serialized_'):
            # Production explicitly anonymizes only the profile-derived player name.
            for state in (a, b):
                state['player_state']['creature']['name'] = state['player_state']['chosen_class_name']
        diff = list(differences(logical(a, future), logical(b, future)))
        checks[name] = not diff
        detail[name] = diff
    report = {'status': 'pass' if all(checks.values()) else 'fail',
              'dataset_origin': 'test_fixture', 'checks': checks, 'differences': detail,
              'visual_fields_omitted': sorted(VISUAL),
              'future_generated_uuid_comparison': 'new-instance ordinal with identity graph, state and pile order',
              'player_name_anonymized': '--production' in sys.argv}
    (restored / 'comparison.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print(json.dumps({'status': report['status'], 'checks': checks}, ensure_ascii=False))
    if not all(checks.values()):
        raise SystemExit(1)

if __name__ == '__main__':
    main()
