#!/usr/bin/env bash
# deploy-branch: main
set -euo pipefail
# Phase 3: node0 (M4 Mac mini) builds the arm64 image natively and auto-deploys.
# This script pushes main, then triggers node0's build-deploy pipeline over SSH:
#   build linux/arm64 -> push ghcr.io/michaellambgelo/kotlin-tutorial -> update-kotlin-tutorial.yml (node5) -> health.
# (Replaces the old "push + watch GH Actions build" flow; GH build workflow is now workflow_dispatch-only.)
NODE0="${NODE0_HOST:-node0}"

echo "::deploy:target=image:start"
if [[ "${DEPLOY_DRY_RUN:-}" = 1 ]]; then
  echo "would: git push origin main"
  echo "would: ssh $NODE0 'bash ~/Workspace/cluster-ops/scripts/node0-build-deploy.sh kotlin-tutorial'"
  echo "::deploy:target=image:end:status=skip"
  echo "::deploy:target=cluster:end:status=skip"
  exit 0
fi

# Push code so node0 builds the current main.
git push origin main
echo "::deploy:target=image:end:status=ok"

# node0 builds (arm64, native) + pushes GHCR + runs the update playbook (health-gated).
echo "::deploy:target=cluster:start"
ssh "$NODE0" 'bash ~/Workspace/cluster-ops/scripts/node0-build-deploy.sh kotlin-tutorial'
echo "::deploy:target=cluster:url=https://kotlin-tutorial.michaellamb.dev"
echo "::deploy:target=cluster:end:status=ok"
