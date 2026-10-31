#!/usr/bin/env python3
"""Verify production proxy routing/headers using a running local QA deployment."""
import os
import urllib.error
import urllib.request

base=os.environ.get('QA_WEB_URL','http://localhost:8180')

def get(path):
    try:r=urllib.request.urlopen(base+path,timeout=5)
    except urllib.error.HTTPError as e:r=e
    return r.status, r.headers, r.read().decode(errors='replace')

for path in ['/', '/dashboard', '/projects/00000000-0000-0000-0000-000000000001']:
    status,headers,body=get(path)
    assert status==200 and '<div id="root">' in body, f'SPA refresh failed: {path}'
    for name in ['Content-Security-Policy','X-Content-Type-Options','X-Frame-Options','Permissions-Policy','X-Robots-Tag']:
        assert headers.get(name), f'Missing {name}'
    assert "script-src 'self'" in headers['Content-Security-Policy']
    assert headers['X-Content-Type-Options']=='nosniff'
    assert headers['X-Robots-Tag']==('index, follow' if path=='/' else 'noindex, nofollow'), f'Wrong indexing policy: {path}'
for path in ['/.env','/.git/config','/config.sql','/assets/app.js.map','/actuator/health']:
    status,_,body=get(path)
    assert status in [403,404], f'Artifact path exposed: {path} ({status})'
status,headers,body=get('/api/v1/projects')
assert status==401 and '<html' not in body.lower(), 'API returned shell/private data without authentication'
assert headers.get('Cache-Control')=='no-store'
assert 'noindex' in headers.get('X-Robots-Tag','')
status,_,body=get('/robots.txt')
assert status==200 and 'Disallow: /' in body
print('PASS: production SPA deep-link refresh, CSP/headers/noindex, dotfile/map/backup/Actuator denial, and unauthenticated API401 without SPA fallback.')
