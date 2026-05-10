#!/bin/bash
set -e

# We only need the stack name and project ID for a clean Pulumi destroy
REQUIRED_VARS="SCRIM_PULUMI_STACK SCRIM_PROJECT_ID"

for var in $REQUIRED_VARS; do
    if [ -z "$(eval echo \${$var:-})" ]; then
        echo "error: $var is not set."
        exit 1
    fi
done

PULUMI_STACK="${SCRIM_PULUMI_STACK}"
PULUMI_DIR="$(cd "$(dirname "$0")/../infrastructure/pulumi" && pwd)"

echo "Starting shutdown for stack: $PULUMI_STACK..."

# 1. Clean up Kubernetes resources first (Optional but good practice)
# This ensures LoadBalancers and PVs are detached before the cluster vanishes
if command -v helm &>/dev/null && command -v kubectl &>/dev/null; then
    echo "Uninstalling Helm release..."
    helm uninstall scrimfinder -n scrimfinder || true
fi

# 2. Use Pulumi to tear down the infrastructure
echo "Destroying infrastructure via Pulumi..."
(
    cd "$PULUMI_DIR"
    # Ensure venv is active for the automation
    [ -d venv ] && source venv/bin/activate || source venv/Scripts/activate

    pulumi stack select "$PULUMI_STACK"
    # --yes avoids interactive prompts
    # --remove-pending-state helps if a previous update crashed
    pulumi destroy --yes --stack "$PULUMI_STACK"
)

echo "Shutdown complete! Infrastructure destroyed and Pulumi state updated."