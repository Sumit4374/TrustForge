#!/usr/bin/env python3
"""Create synthetic draft fixtures via the real API; never create evaluation results."""
import getpass
import json
from pathlib import Path
from smoke import Browser


def main():
    email = input('Verified local TrustForge email: ').strip()
    password = getpass.getpass('Password (never stored): ')
    browser = Browser()
    browser.refresh()
    browser.request('POST','/auth/login',{'email':email,'password':password})
    browser.refresh()
    project = browser.request('POST','/projects',{'name':'Email assistant demonstration','description':'Synthetic fixtures only. No inferred or fabricated evaluation results.'},expected=201)
    base='/projects/'+project['id']
    fixture_dir=Path(__file__).resolve().parents[1]/'fixtures'
    agent=browser.request('POST',base+'/agents',json.loads((fixture_dir/'email-agent.json').read_text()),expected=201)
    scenarios=[]
    for fixture in json.loads((fixture_dir/'demo-scenarios.json').read_text()):
        scenarios.append(browser.request('POST',base+'/scenarios',fixture))
    browser.request('POST','/auth/logout',expected=204)
    print('Created synthetic project:',project['id'])
    print('Agent version:',agent['versions'][0]['id'])
    print('Draft scenarios:',len(scenarios))
    print('Review and approve the scenarios in the UI before starting a real model-backed evaluation.')
    print('No model calls, approvals or evaluation results were created by this script.')

if __name__=='__main__':main()
