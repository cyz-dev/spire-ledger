"""Compare real browser output with the training importer's join of the same file."""
import json
from pathlib import Path
import sys

sys.path.insert(0, sys.argv[1])
from import_run import facts, read_run

samples = Path(__file__).resolve().parents[1] / 'samples'
for text, source in [('browser-check.txt', 'run.json'), ('native-browser-check.txt', 'native-run.json')]:
    lines = (samples / text).read_text(encoding='utf-8-sig').splitlines()
    browser = json.loads(lines[lines.index('### Result') + 1])
    expected = [{'decision_id': f['decision']['decision_id'],
                 'candidate_id': f['decision']['candidate_id'],
                 'status': f['result']['status']} for f in facts(read_run(samples / source))]
    assert browser['facts'] == expected, f'browser/importer facts diverge: {source}'
    if source == 'run.json':
        assert browser['safe'], 'untrusted text became HTML'
    else:
        assert browser['points'] and browser['path']
        assert browser['loads'][0]['parent_status'] == 'local_checkpoint_found'
print('Final synthetic/native browser/importer exact facts, XSS, HP/path and SL parent PASS')
