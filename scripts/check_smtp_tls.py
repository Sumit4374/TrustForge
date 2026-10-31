#!/usr/bin/env python3
"""Run only against an isolated API with SMTP_TLS=true and non-TLS local Mailpit.
Verifies that mail cannot silently downgrade to plaintext. Never prints credentials.
"""
import secrets
import uuid
from smoke import Browser

browser=Browser()
browser.refresh()
browser.request('POST','/auth/register',{
    'name':'Synthetic TLS fail-closed probe',
    'email':f'tls-probe-{uuid.uuid4()}@example.test',
    'password':secrets.token_urlsafe(24)
},expected=503)
print('PASS: required SMTP STARTTLS rejected non-TLS Mailpit with503; no delivery was claimed.')
