#!/usr/bin/env python3
"""Passive ZAP baseline, restricted to the owned isolated full-stack app on loopback8180.
Raw warnings are retained; scanner failures and High/Critical alerts block the check.
"""
import json
import datetime
import uuid
import os
from pathlib import Path
import subprocess
import sys
import urllib.request

root=Path(__file__).resolve().parents[1]
work=root/'.run/phase2/zap'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%f')
work.mkdir(parents=True,exist_ok=True)
owned=subprocess.run(['docker','ps','--filter','label=trustforge.test-only=phase2','--format','{{.ID}}'],capture_output=True,text=True,check=True).stdout.split()
if len(owned)<2:raise SystemExit('Start scripts/run_fullstack.py --serve first; only that owned local test target is authorized.')
target='http://127.0.0.1:8180'
with urllib.request.urlopen(target,timeout=5) as response:
    if b'TrustForge' not in response.read(10000):raise SystemExit('Owned TrustForge test target could not be verified.')
image='ghcr.io/zaproxy/zaproxy:stable'
container_name='trustforge-zap-'+uuid.uuid4().hex[:16]
command=['docker','run','--rm','--name',container_name,'--label','trustforge.test-only=phase2','--network','host','--user',f'{os.getuid()}:{os.getgid()}','--workdir','/zap/wrk','--cap-drop','ALL','--security-opt','no-new-privileges','--memory','2g','--cpus','2','-v',f'{work}:/zap/wrk:rw','-v',f'{root}/scripts/zap_loopback.py:/zap-loopback.py:ro',image,'zap-baseline.py','-t',target,'-m','1','-T','3','-P','18090','--autooff','-z','-dir /zap/wrk/profile','--hook','/zap-loopback.py','-J','baseline.json','-r','baseline.html','-w','baseline.md']
try:
    with (work/'scan.log').open('w') as log:
        result=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=360)
finally:
    subprocess.run(['docker','rm','-f',container_name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=False)
report=work/'baseline.json'
if result.returncode not in (0,1,2) or not report.is_file():raise SystemExit('ZAP failed to execute or produce findings; inspect .run/phase2/zap/scan.log')
data=json.loads(report.read_text());alerts=[]
for site in data.get('site',[]):
    for alert in site.get('alerts',[]):alerts.append({'pluginId':alert['pluginid'],'name':alert['alert'],'riskCode':int(alert['riskcode']),'risk':alert.get('riskdesc'),'instances':len(alert.get('instances',[]))})
high=[alert for alert in alerts if alert['riskCode']>=3]
identity=subprocess.run(['docker','image','inspect','--format','{{.Id}}',image],capture_output=True,text=True,check=True).stdout.strip()
summary={'executedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'target':target,'scope':'Unauthenticated isolated synthetic test app; traditional spider and passive rules only; no active scan','tool':'OWASP ZAP','version':data.get('@version'),'imageId':identity,'scannerExitCode':result.returncode,'highCriticalFindings':len(high),'alerts':alerts,'assessmentStatus':'REVIEW_REQUIRED' if alerts else 'VERIFIED_NO_PASSIVE_ALERTS','rawReport':str(report.relative_to(root)),'managementListener':'127.0.0.1:18090; hook changes binding only; no disabled rules or ignored findings'}
assessment_file=root/'docs/verification/phase2-zap-assessment.json'
reviewed=[]
if assessment_file.is_file():
    reviewed=json.loads(assessment_file.read_text()).get('assessments',[])
unreviewed=[alert for alert in alerts if not any(record.get('pluginId')==alert['pluginId'] and record.get('name')==alert['name'] and alert['riskCode']<=record.get('maxRiskCode',-1) and record.get('owner') and record.get('reason') for record in reviewed)]
if alerts and not unreviewed and not high:summary['assessmentStatus']='VERIFIED_REVIEWED_ALERTS'
(root/'docs/verification/phase2-zap.json').write_text(json.dumps(summary,indent=2)+'\n')
print('ZAP baseline completed:',len(alerts),'alerts;',len(high),'High/Critical. Raw warnings remain recorded for assessment.')
if high or result.returncode==1:sys.exit(1)
if unreviewed:sys.exit(2)
