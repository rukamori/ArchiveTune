import datetime
import json
import os
import re
import subprocess
import sys
import tempfile

VARIANTS = frozenset({
    'app-gms-mobile-arm64-release.apk',
    'app-gms-mobile-armeabi-release.apk',
    'app-gms-mobile-x86-release.apk',
    'app-gms-mobile-x86_64-release.apk',
    'app-gms-mobile-universal-release.apk',
    'app-gms-tv-universal-release.apk',
    'app-foss-mobile-universal-release.apk',
})
KEEP = 5

def aws(*args, stdin=None):
    result = subprocess.run(
        ['aws', 's3api', *args, '--endpoint-url', endpoint, '--output', 'json'],
        input=stdin, text=True, capture_output=True, check=False,
    )
    if result.returncode:
        raise RuntimeError(f'R2 operation failed: {args[0]}: {result.stderr.strip()}')
    return json.loads(result.stdout or '{}')

def objects(prefix):
    cursor = None
    while True:
        args = ['list-objects-v2', '--bucket', bucket, '--prefix', prefix, '--max-keys', '1000', '--no-paginate']
        if cursor:
            args += ['--continuation-token', cursor]
        result = aws(*args)
        yield from result.get('Contents', [])
        cursor = result.get('NextContinuationToken')
        if not result.get('IsTruncated'):
            break
        if not cursor:
            raise RuntimeError('R2 pagination ended without a continuation token')

def delete_group(commit):
    prefix = f'early-access/{branch}/{commit}/'
    targets = [item['Key'] for item in objects(prefix)]
    for i in range(0, len(targets), 1000):
        payload = json.dumps({'Objects': [{'Key': key} for key in targets[i:i + 1000]], 'Quiet': True})
        result = aws('delete-objects', '--bucket', bucket, '--delete', payload)
        if result.get('Errors'):
            raise RuntimeError(f'R2 refused deletion for commit {commit}')
    aws('delete-object', '--bucket', bucket, '--key', f'early-access/_manifests/{branch}/{commit}.json')
    print(f'Deleted old successful build {branch}/{commit}: {len(targets)} objects')

required = ('R2_ACCOUNT_ID', 'R2_BUCKET', 'GITHUB_REF_NAME', 'GITHUB_SHA')
for name in required:
    if not os.environ.get(name):
        sys.exit(f'Missing required configuration: {name}')
branch = os.environ['GITHUB_REF_NAME']
commit = os.environ['GITHUB_SHA']
bucket = os.environ['R2_BUCKET']
account = os.environ['R2_ACCOUNT_ID']
if branch not in ('main', 'dev') or not re.fullmatch(r'[a-f0-9]{40}', commit):
    sys.exit('Invalid branch or full commit SHA')
if not re.fullmatch(r'[a-z0-9-]+', bucket) or not re.fullmatch(r'[a-f0-9]{32}', account):
    sys.exit('Invalid R2 bucket or account ID')
endpoint = f'https://{account}.r2.cloudflarestorage.com'

prefix = f'early-access/{branch}/{commit}/'
files = list(objects(prefix))
found = [item['Key'][len(prefix):] for item in files]
if len(found) != len(VARIANTS) or set(found) != VARIANTS:
    sys.exit(f'Build not complete; expected seven Release APKs; found {len(found)} objects')

manifest_key = f'early-access/_manifests/{branch}/{commit}.json'
manifest = {
    'branch': branch,
    'commit': commit,
    'variants': sorted(VARIANTS),
    'publishedAt': datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00', 'Z'),
}
with tempfile.NamedTemporaryFile(mode='w', encoding='utf-8', suffix='.json') as temp:
    json.dump(manifest, temp, separators=(',', ':'))
    temp.flush()
    aws('put-object', '--bucket', bucket, '--key', manifest_key,
        '--body', temp.name, '--content-type', 'application/json')

markers = list(objects(f'early-access/_manifests/{branch}/'))
valid = []
for item in markers:
    matched = re.fullmatch(rf'early-access/_manifests/{branch}/([a-f0-9]{{40}})\.json', item['Key'])
    if matched:
        valid.append((item['LastModified'], matched.group(1)))
valid.sort(key=lambda item: (item[0], item[1]), reverse=True)
for _, previous in valid[KEEP:]:
    if previous == commit:
        raise RuntimeError('Current commit is outside retention window; refusing to remove active build')
    delete_group(previous)
print(f'Completed build {branch}/{commit}; retain up to {KEEP} successful commits per branch')
