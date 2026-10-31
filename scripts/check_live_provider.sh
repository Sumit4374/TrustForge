#!/usr/bin/env bash
set +x
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
if [[ "${TRUSTFORGE_LIVE_BHARATCODE:-}" != true || -z "${BHARATCODE_API_KEY:-}" ]]; then
  echo 'BLOCKED: set TRUSTFORGE_LIVE_BHARATCODE=true and a real server-side BHARATCODE_API_KEY to enable live requests.' >&2
  exit 2
fi
mkdir -p .run/phase2
maven=(mvn)
if [[ -n "${TRUSTFORGE_MAVEN_REPO:-}" ]]; then maven+=("-Dmaven.repo.local=$TRUSTFORGE_MAVEN_REPO"); fi
"${maven[@]}" -Dapi.version=1.44 -Dtest=BharatCodeLiveIT -Dtrustforge.live.report="$PWD/.run/phase2/live-provider.json" -f server/pom.xml -B -ntp test
