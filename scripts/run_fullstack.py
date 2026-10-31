#!/usr/bin/env python3
"""Real local application verifier; only inference is a clearly labeled test-classpath fixture.
Run Maven verify and npm build first. --serve keeps this isolated test stack up until Ctrl+C.
"""
import argparse
import hashlib
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
work=root/'.run/phase2'
parser=argparse.ArgumentParser()
parser.add_argument('--serve',action='store_true')
args=parser.parse_args()
def interrupted(_signal,_frame):raise KeyboardInterrupt
signal.signal(signal.SIGTERM,interrupted)
work.mkdir(parents=True,exist_ok=True)
marker=hashlib.sha256(str(root).encode()).hexdigest()[:8]
project='trustforge-phase2-'+marker
ports={'POSTGRES_PORT':55442,'REDIS_PORT':56479,'CACHE_REDIS_PORT':56480,'SMTP_PORT':51125,'MAILPIT_HTTP_PORT':58125}
api_port,web_port=18080,8180
for port in [*ports.values(),api_port,web_port]:
    with socket.socket() as probe:
        # A recently closed owned SMTP connection can leave TIME_WAIT; an active listener still fails bind.
        probe.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        try:probe.bind(('127.0.0.1',port))
        except OSError:raise SystemExit(f'Port {port} is occupied; this verifier never stops unrelated services.')
repo=Path(os.environ.get('TRUSTFORGE_MAVEN_REPO',str(root/'.run/maven-repository'))).resolve()
reports=sorted((root/'server/target/surefire-reports').glob('TEST-*.xml'))
if not reports:raise SystemExit('Run Maven verify with the configured Maven repository before full-stack verification.')
for report in reports:
    suite=ET.parse(report).getroot()
    if any(int(suite.get(k,'0')) for k in ['failures','errors']):raise SystemExit('Backend reports contain failures; fix them before full-stack verification.')
props={p.get('name'):p.get('value') for p in ET.parse(reports[0]).getroot().findall('./properties/property')}
recorded_repo=Path(props.get('maven.repo.local',str(Path.home()/'.m2/repository')))
classpath=['/workspace/target/test-classes','/workspace/target/classes']
for value in props.get('java.class.path','').split(':'):
    path=Path(value)
    if path.suffix!='.jar':continue
    try:relative=path.relative_to(recorded_repo)
    except ValueError:raise SystemExit('Unexpected dependency outside the recorded public Maven repository.')
    if not (repo/relative).is_file():raise SystemExit('Resolved dependency missing; use the same Maven cache as verification.')
    classpath.append('/m2/'+str(relative))
if len(classpath)<10:raise SystemExit('Refusing an incomplete test classpath.')
if not (root/'server/target/test-classes/com/sumit/trustforge/testinfra/TestOnlyApplication.class').exists():raise SystemExit('Compile the test-only launcher with Maven verify first.')
(work/'fullstack-classpath.txt').write_text(':'.join(classpath))
config={**{k:str(v) for k,v in ports.items()},'DB_USERNAME':'trustforge','DB_PASSWORD':secrets.token_hex(24),'REDIS_PASSWORD':secrets.token_hex(24),'CACHE_REDIS_PASSWORD':secrets.token_hex(24),'REDIS_HOST':'127.0.0.1','CACHE_REDIS_HOST':'127.0.0.1','SMTP_HOST':'127.0.0.1','SMTP_FROM':'trustforge@example.test','SMTP_AUTH':'false','SMTP_TLS':'false','APP_BASE_URL':f'http://127.0.0.1:{web_port}','APP_SECURE_COOKIES':'false','PRODUCTION':'false','DB_URL':f'jdbc:postgresql://127.0.0.1:{ports["POSTGRES_PORT"]}/trustforge','BHARATCODE_API_KEY':''}
env_file=work/'fullstack.env'
fd=os.open(env_file,os.O_CREAT|os.O_WRONLY|os.O_TRUNC,0o600)
with os.fdopen(fd,'w') as handle:handle.write(''.join(f'{key}={value}\n' for key,value in config.items()))
env_file.chmod(0o600)
compose=['docker','compose','--project-name',project,'--env-file',str(env_file),'-f',str(root/'compose.yaml')]
containers=[]
def run(command,**kwargs):return subprocess.run(command,check=True,cwd=root,text=True,**kwargs)
def logrun(command,name):
    with (work/name).open('w') as output:return run(command,stdout=output,stderr=subprocess.STDOUT)
def launch(command):
    result=run(command,capture_output=True)
    identifier=result.stdout.strip()
    if not identifier:raise RuntimeError('Container launch returned no identifier')
    containers.append(identifier)
    return identifier
try:
    logrun([*compose,'up','-d','--wait','--wait-timeout','120'],'fullstack-stores.log')
    image='trustforge-phase2-client:'+marker
    logrun(['docker','build','-t',image,'client'],'fullstack-client-build.log')
    api=launch(['docker','run','-d','--network','host','--user',f'{os.getuid()}:{os.getgid()}','--label','trustforge.test-only=phase2','--env-file',str(env_file),'-v',f'{root}/server:/workspace:ro','-v',f'{repo}:/m2:ro','-v',f'{work}/fullstack-classpath.txt:/classpath:ro','--entrypoint','/bin/sh','maven:3.9-eclipse-temurin-21','-c',f'exec java -Dtrustforge.test.fixture=true -cp "$(cat /classpath)" com.sumit.trustforge.testinfra.TestOnlyApplication --server.address=127.0.0.1 --server.port={api_port} --debug=false --trace=false'])
    for attempt in range(80):
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{api_port}/actuator/health/readiness',timeout=2) as response:
                if response.status==200:break
        except Exception:time.sleep(1)
    else:raise RuntimeError('Test-only API readiness timeout; inspect sanitized fullstack-api.log')
    nginx=(root/'client/nginx.conf').read_text().replace('listen 8080;',f'listen 127.0.0.1:{web_port};').replace('http://server:8080',f'http://127.0.0.1:{api_port}')
    (work/'fullstack-nginx.conf').write_text(nginx)
    launch(['docker','run','-d','--network','host','--label','trustforge.test-only=phase2','-v',f'{work}/fullstack-nginx.conf:/etc/nginx/conf.d/default.conf:ro',image])
    for attempt in range(30):
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{web_port}/login',timeout=2) as response:
                if response.status==200:break
        except Exception:time.sleep(1)
    else:raise RuntimeError('Compiled frontend readiness timeout')
    print(f'TEST ONLY: compiled frontend {web_port}, real Java21 API {api_port}, isolated stores/SMTP; provider=CONTROLLED_TEST_FIXTURE.',flush=True)
    if args.serve:
        while True:time.sleep(1)
    else:
        test_env={**os.environ,'PLAYWRIGHT_BASE_URL':f'http://127.0.0.1:{web_port}','FULLSTACK_BACKEND':'1','LIVE_BACKEND':'1','SMOKE_MAIL_URL':f'http://127.0.0.1:{ports["MAILPIT_HTTP_PORT"]}'}
        result=run(['npm','--prefix','client','run','test:e2e'],env=test_env)
except KeyboardInterrupt:
    pass
finally:
    if containers:
        with (work/'fullstack-api.log').open('w') as output:subprocess.run(['docker','logs',containers[0]],stdout=output,stderr=subprocess.STDOUT,check=False)
    for identifier in reversed(containers):subprocess.run(['docker','rm','-f',identifier],check=False,stdout=subprocess.DEVNULL)
    subprocess.run([*compose,'down','--volumes'],check=False,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
