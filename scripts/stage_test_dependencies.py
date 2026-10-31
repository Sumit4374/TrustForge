#!/usr/bin/env python3
"""Stage the actual resolved Surefire JAR classpath for test-scope SCA.
Copies only public dependency JARs; never Maven settings, environment or test values.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import xml.etree.ElementTree as ET

parser=argparse.ArgumentParser()
parser.add_argument('--maven-repo',type=Path,default=Path.home()/'.m2/repository')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
reports=sorted((root/'server/target/surefire-reports').glob('TEST-*.xml'))
if not reports:raise SystemExit('Run backend tests before staging the resolved classpath.')
tree=ET.parse(reports[0]).getroot()
properties={p.get('name'):p.get('value') for p in tree.findall('./properties/property')}
remote_repo=Path(properties.get('maven.repo.local',str(Path.home()/'.m2/repository')))
output=root/'.run/test-dependency-image'
marker=output/'.trustforge-generated'
if output.exists():
    if not marker.exists():raise SystemExit('Refusing to replace an unmarked staging directory.')
    shutil.rmtree(output)
output.mkdir(parents=True)
marker.write_text('Generated public dependency staging only.\n')
metadata=[]
for raw in properties.get('java.class.path','').split(':'):
    path=Path(raw)
    if path.suffix!='.jar':continue
    try:relative=path.relative_to(remote_repo)
    except ValueError:raise SystemExit('Unexpected JAR outside Maven repository; review before copying.')
    source=args.maven_repo/relative
    if not source.is_file():raise SystemExit(f'Missing resolved dependency: {relative}')
    destination=output/'deps'/relative
    destination.parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(source,destination)
    metadata.append({'repositoryPath':str(relative),'sha256':hashlib.sha256(source.read_bytes()).hexdigest()})
if not metadata:raise SystemExit('No resolved JARs found; refusing an empty dependency assessment.')
(output/'Dockerfile').write_text('FROM scratch\nCOPY deps/ /dependencies/\n')
(root/'docs/verification/test-dependencies.json').write_text(json.dumps({'javaVersion':properties.get('java.version'),'resolvedJars':metadata},indent=2)+'\n')
print('Staged',len(metadata),'resolved public JARs for test-scope scanning.')
