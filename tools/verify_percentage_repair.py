"""Read-only post-repair scope and API verification. Never prints authentication data."""
import argparse
import json
import urllib.request
from pathlib import Path
from registry_percentage_repair import COLUMNS, FIELDS, equivalent


def verify(before, after, manifest):
    old = {r['id']: r for r in before}
    new = {r['id']: r for r in after}
    assert old.keys() == new.keys(), 'Registry row population changed'
    changed = {r['id']: r for r in manifest['changes']}
    for identifier, row in new.items():
        previous = old[identifier]
        for key in row.keys() - {'registry', *COLUMNS}:
            assert row[key] == previous[key], (identifier, key)
        prior_payload, payload = json.loads(previous['registry']), json.loads(row['registry'])
        assert {k: v for k, v in prior_payload.items() if k not in FIELDS} == {
            k: v for k, v in payload.items() if k not in FIELDS}, (identifier, 'unrelated source values')
        expected = {f['field']: f['new'] for f in changed.get(identifier, {}).get('fields', [])}
        for key in FIELDS:
            if key in expected:
                assert equivalent(payload.get(key), expected[key]), (identifier, key, 'registry')
                if key in COLUMNS:
                    assert equivalent(row[key], expected[key]), (identifier, key, 'typed')
            else:
                assert payload.get(key) == prior_payload.get(key), (identifier, key, 'unexpected change')
                if key in COLUMNS:
                    assert row[key] == previous[key], (identifier, key, 'unexpected typed change')
    return len(old), len(changed)


def request(path, token=None):
    headers = {'Cache-Control': 'no-cache'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request('https://ua-oms.com' + path, headers=headers,
                                 data=b'' if path.endswith('/auth/guest') else None)
    with urllib.request.urlopen(req, timeout=90) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('before', type=Path)
    parser.add_argument('after', type=Path)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--api', action='store_true')
    args = parser.parse_args()
    rows = lambda p: [json.loads(line) for line in p.read_text(encoding='utf-8-sig').splitlines() if line.strip()]
    manifest = json.loads(args.manifest.read_text(encoding='utf-8-sig'))
    compared, repaired = verify(rows(args.before), rows(args.after), manifest)
    result = {'verifiedRegistryRows': compared, 'repairedRows': repaired, 'unrelatedValuesUnchanged': True}
    if args.api:
        token = request('/api/v1/auth/guest')['accessToken']
        projects = request('/api/v1/projects?pageSize=500', token)['data']
        by_code = {p['siteNumber'].upper(): p for p in projects}
        checked = 0
        for change in manifest['changes']:
            project = by_code[change['code'].upper()]
            for field in change['fields']:
                # Blank source approved funding can be derived by DTO fallback;
                # this repair changes only proven numeric values or truly empty progress.
                assert equivalent(project['registry'].get(field['field']), field['new']), (change['code'], field['field'])
                checked += 1
        problem = by_code['MY08_02']
        detail = request('/api/v1/projects/' + problem['uuid'], token)['data']
        assert detail['registry'].get('completedWorkPct', '') == ''
        assert detail['registry'].get('paidFinancingPct') == '0%'
        result.update(apiPercentageChecks=checked, my08_02_detailVerified=True, productionRevision=request('/version')['revision'])
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
