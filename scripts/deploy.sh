#!/usr/bin/env bash
# deploy-branch: main
set -euo pipefail

# Phase 1: push code; CI (build-and-push.yml) builds the multi-arch image and
# pushes it to ghcr.io/michaellambgelo/kotlin-tutorial. Router watches the
# workflow run after this script exits 0.
echo "::deploy:target=image:start"
echo "::deploy:target=image:watch=build-and-push.yml"
if [[ "${DEPLOY_TARGET:-}" != "" && "${DEPLOY_TARGET}" != "image" && "${DEPLOY_TARGET}" != "cluster" ]]; then
  echo "unknown DEPLOY_TARGET: ${DEPLOY_TARGET} (expected: image, cluster, or unset)" >&2
  exit 2
fi

if [[ "${DEPLOY_TARGET:-}" != "cluster" ]]; then
  if [[ "${DEPLOY_DRY_RUN:-}" = 1 ]]; then
    echo "would: git push origin main"
  else
    git push origin main
  fi
  echo "::deploy:target=image:end:status=ok"
else
  echo "::deploy:target=image:end:status=skip"
fi

# Phase 2: run the Ansible playbook from ~/Workspace/cluster-ops to pull the
# new image on node5 and restart the container.
echo "::deploy:target=cluster:start"
if [[ "${DEPLOY_TARGET:-}" != "image" ]]; then
  if [[ "${DEPLOY_DRY_RUN:-}" = 1 ]]; then
    echo "would: (cd ~/Workspace/cluster-ops && ansible-playbook playbooks/update-kotlin-tutorial.yml)"
  else
    ( cd "$HOME/Workspace/cluster-ops" && ansible-playbook playbooks/update-kotlin-tutorial.yml )
  fi
  echo "::deploy:target=cluster:url=https://kotlin-tutorial.michaellamb.dev"
  echo "::deploy:target=cluster:end:status=ok"
else
  echo "::deploy:target=cluster:end:status=skip"
fi
