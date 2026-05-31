#!/bin/bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# shellcheck source=lib/cloud-functions.sh
source "$ROOT_DIR/scripts/lib/cloud-functions.sh"

REQUIRED_VARS="SCRIM_PROJECT_ID SCRIM_REGION SCRIM_REPO_NAME SCRIM_CLUSTER_NAME RIOT_API_KEY SCRIM_DB_USER SCRIM_DB_PASSWORD"

for var in $REQUIRED_VARS; do
    if [ -z "${!var:-}" ]; then
        echo "error: $var is not set. please set it in your system environment."
        exit 1
    fi
done

PROJECT_ID="$SCRIM_PROJECT_ID"
REGION="$SCRIM_REGION"
REPO_NAME="$SCRIM_REPO_NAME"
CLUSTER_NAME="$SCRIM_CLUSTER_NAME"

SCRIM_NAMESPACE="${SCRIM_NAMESPACE:-scrimfinder}"

SCRIM_RABBITMQ_HOST="${SCRIM_RABBITMQ_HOST:-scrimfinder-rabbitmq-broker}"
SCRIM_RABBITMQ_PORT="${SCRIM_RABBITMQ_PORT:-5672}"
SCRIM_IMAGE_TAG="${SCRIM_IMAGE_TAG:-latest}"
SCRIM_SECRET_NAME_PREFIX="${SCRIM_SECRET_NAME_PREFIX:-}"

echo "checking GCP configuration..."
gcloud config set project "$PROJECT_ID" --quiet

echo "provisioning infrastructure with Terraform..."
"$ROOT_DIR/scripts/deploy-infra.sh"

FUNCTIONS_BUILD_SERVICE_ACCOUNT="$(functions_build_service_account "$PROJECT_ID")"
SECRETS_SERVICE_ACCOUNT_EMAIL="secrets-service-account@${PROJECT_ID}.iam.gserviceaccount.com"

ZONE="${REGION}-a"
echo "fetching GKE credentials..."
gcloud container clusters get-credentials "$CLUSTER_NAME" \
    --zone "$ZONE" \
    --project "$PROJECT_ID"

echo "authenticating Docker to Artifact Registry..."
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet

if [ -z "${SERVICES:-}" ]; then
    SERVICES="matchmaking-service ranking_service match_history_service training_service analysis_service"
fi

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "building and pushing services in parallel..."

    for SERVICE in $SERVICES; do
        (
            IMAGE_NAME=$(echo "$SERVICE" | tr '_' '-')
            IMAGE_PATH="$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$IMAGE_NAME:$SCRIM_IMAGE_TAG"

            echo "building $SERVICE..."
            docker buildx build \
                --platform linux/amd64 \
                -t "$IMAGE_PATH" \
                "$ROOT_DIR/$SERVICE" \
                --push \
                --quiet

            echo "finished $SERVICE."
        ) &
    done

    wait
    echo "all builds complete."
else
    echo "skipping build phase."
fi

ensure_riot_api_secret "$PROJECT_ID" "$RIOT_API_KEY"
package_detail_filling_function_source "$ROOT_DIR" true clean package
deploy_detail_filling_functions "$ROOT_DIR" "$REGION" "$FUNCTIONS_BUILD_SERVICE_ACCOUNT" "$SECRETS_SERVICE_ACCOUNT_EMAIL"
echo "all serverless functions done deploying."
discover_detail_filling_domain "$REGION"

echo "installing VPA CRDs..."
git clone https://github.com/kubernetes/autoscaler.git || true
cp -f ./scripts/gencerts.sh ./autoscaler/vertical-pod-autoscaler/pkg/admission-controller/
cd autoscaler/vertical-pod-autoscaler
./hack/vpa-up.sh
cd ../..
rm -r autoscaler &
wait

echo "updating Helm dependencies..."
helm dependency update k8s/charts/scrimfinder

echo "bootstrapping Traefik CRDs..."
helm show crds "$ROOT_DIR/k8s/charts/scrimfinder/charts/traefik-35.2.0.tgz" | kubectl apply --server-side --force-conflicts -f -
for crd in \
    accesscontrolpolicies.hub.traefik.io \
    aiservices.hub.traefik.io \
    apibundles.hub.traefik.io \
    apicatalogitems.hub.traefik.io \
    apiplans.hub.traefik.io \
    apiportals.hub.traefik.io \
    apiratelimits.hub.traefik.io \
    apis.hub.traefik.io \
    apiversions.hub.traefik.io \
    gatewayclasses.gateway.networking.k8s.io \
    gateways.gateway.networking.k8s.io \
    grpcroutes.gateway.networking.k8s.io \
    httproutes.gateway.networking.k8s.io \
    ingressroutes.traefik.io \
    ingressroutetcps.traefik.io \
    ingressrouteudps.traefik.io \
    managedsubscriptions.hub.traefik.io \
    middlewares.traefik.io \
    middlewaretcps.traefik.io \
    referencegrants.gateway.networking.k8s.io \
    serverstransports.traefik.io \
    serverstransporttcps.traefik.io \
    tlsoptions.traefik.io \
    tlsstores.traefik.io \
    traefikservices.traefik.io; do
    kubectl wait --for=condition=Established "crd/${crd}" --timeout=120s
done

echo "bootstrapping Secrets Store CSI driver..."
helm upgrade --install secrets-store-csi-driver \
    "$ROOT_DIR/k8s/charts/scrimfinder/charts/secrets-store-csi-driver-1.6.0.tgz" \
    --namespace kube-system \
    --create-namespace \
    --set syncSecret.enabled=true \
    --set enableSecretRotation=true \
    --set rotationPollInterval=2m \
    --wait \
    --timeout 5m
kubectl apply -f https://raw.githubusercontent.com/GoogleCloudPlatform/secrets-store-csi-driver-provider-gcp/main/deploy/provider-gcp-plugin.yaml
kubectl wait --for=condition=Established crd/secretproviderclasses.secrets-store.csi.x-k8s.io --timeout=120s
kubectl wait --for=condition=Established crd/secretproviderclasspodstatuses.secrets-store.csi.x-k8s.io --timeout=120s
kubectl -n kube-system rollout status daemonset/secrets-store-csi-driver --timeout=300s
kubectl -n kube-system rollout status daemonset/csi-secrets-store-provider-gcp --timeout=300s

#echo "deploying application with local Helm chart..."
#helm upgrade --install scrimfinder "$ROOT_DIR/k8s/charts/scrimfinder" \
#    --namespace "$SCRIM_NAMESPACE" \
#    --create-namespace \
#    --wait \
#    --timeout 25m \
#    --set global.namespace="${SCRIM_NAMESPACE}" \
#    --set global.projectID="${PROJECT_ID}" \
#    --set global.microservicesRegistry="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO_NAME}" \
#    --set global.imageTag="${SCRIM_IMAGE_TAG}" \
#    --set global.region="${REGION}" \
#    --set global.projectId="${PROJECT_ID}" \
#    --set global.repoName="${REPO_NAME}" \
#    --set global.rabbitmqHost="${SCRIM_RABBITMQ_HOST}" \
#    --set global.rabbitmqPort="${SCRIM_RABBITMQ_PORT}" \
#    --set global.useSecretManager=true \
#    --set global.useArgoApplications=true \
#    --set global.useVerticalPodAutoscaler=true \
#    --set detailFillingExternal.enabled=true \
#    --set detailFillingExternal.externalName="${DETAIL_FILLING_DOMAIN}" \
#    --set services.detail-filling-service.enabled=false \
#    --set services.ranking-service.env.DETAIL_FILLING_SERVICE_URL="http://scrimfinder-traefik/api/v1/riot" \
#    --set services.match-history-service.env.PLAYER_FILLING_SVC_URL="http://scrimfinder-traefik/api/v1/riot" \
#    --set services.training-service.env.DETAIL_FILLING_URL="http://scrimfinder-traefik/api/v1/riot"#

echo "deploying Argo CD..."

export SCRIM_NAMESPACE="${SCRIM_NAMESPACE}"
export PROJECT_ID="${PROJECT_ID}"
export REGION="${REGION}"
export REPO_NAME="${REPO_NAME}"
export SCRIM_IMAGE_TAG="${SCRIM_IMAGE_TAG:-latest}"
export SCRIM_RABBITMQ_HOST="${SCRIM_RABBITMQ_HOST}"
export SCRIM_RABBITMQ_PORT="${SCRIM_RABBITMQ_PORT}"
export SCRIM_SECRET_NAME_PREFIX="${SCRIM_SECRET_NAME_PREFIX}"
export DETAIL_FILLING_DOMAIN="${DETAIL_FILLING_DOMAIN}"
export TARGET_REVISION="${TARGET_REVISION:-$(git -C "$ROOT_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || echo main)}"

kubectl create namespace argocd --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl patch svc argocd-server -n argocd -p '{"spec": {"type": "LoadBalancer"}}'
kubectl create namespace "$SCRIM_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

if command -v envsubst >/dev/null 2>&1; then
    envsubst < k8s/application.yaml | kubectl apply -n argocd --server-side --force-conflicts -f -
else
    echo "warning: envsubst not found. applying manifests without variable substitution..."
    kubectl apply -n argocd --server-side --force-conflicts -f k8s/application.yaml
fi

install_argocd_cli() {
    if command -v argocd >/dev/null 2>&1; then
        return 0
    fi
    local os arch url
    os="$(uname -s | tr '[:upper:]' '[:lower:]')"
    arch="$(uname -m)"
    if [ "$arch" = "x86_64" ]; then
        arch="amd64"
    elif [ "$arch" = "aarch64" ]; then
        arch="arm64"
    fi
    url="https://github.com/argoproj/argo-cd/releases/latest/download/argocd-${os}-${arch}"
    curl -fsSL "$url" -o /tmp/argocd
    chmod +x /tmp/argocd
    if command -v sudo >/dev/null 2>&1; then
        sudo mv /tmp/argocd /usr/local/bin/argocd
    else
        mkdir -p "$HOME/.local/bin"
        mv /tmp/argocd "$HOME/.local/bin/argocd"
        export PATH="$HOME/.local/bin:$PATH"
    fi
}

echo "waiting for Argo CD control plane..."
kubectl -n argocd rollout status statefulset/argocd-application-controller --timeout=300s
kubectl -n argocd rollout status deployment/argocd-repo-server --timeout=300s
kubectl -n argocd rollout status deployment/argocd-redis --timeout=300s
kubectl -n argocd wait --for=condition=Ready pod -l app.kubernetes.io/name=argocd-application-controller --timeout=300s
kubectl -n argocd wait --for=condition=Ready pod -l app.kubernetes.io/name=argocd-repo-server --timeout=300s
install_argocd_cli
kubectl config set-context --current --namespace=argocd
echo "syncing Argo CD application 'scrimfinder' from targetRevision=${TARGET_REVISION}..."
argocd --core app sync scrimfinder --app-namespace argocd --prune --timeout 1800
argocd --core app wait scrimfinder --app-namespace argocd --sync --health --timeout 1800

echo "waiting for Argo CD LoadBalancer External IP/Hostname..."

EXTERNAL_ARGOCD_IP=""

while [ -z "$EXTERNAL_ARGOCD_IP" ]; do
    echo "waiting for argocd IP..."

    EXTERNAL_ARGOCD_IP=$(kubectl get svc argocd-server \
        -n argocd \
        -o=jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

    if [ -z "$EXTERNAL_ARGOCD_IP" ]; then
        echo "checking for Hostname..."

        EXTERNAL_ARGOCD_IP=$(kubectl get svc argocd-server \
            -n argocd \
            -o=jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
    fi

    [ -z "$EXTERNAL_ARGOCD_IP" ] && sleep 10
done

INITIAL_ARGOCD_PASSWORD=$(kubectl -n argocd get secret argocd-initial-admin-secret \
    -o jsonpath="{.data.password}" | base64 -d)

echo "Argo CD External IP/Hostname: ${EXTERNAL_ARGOCD_IP}; username: admin; initial password: $INITIAL_ARGOCD_PASSWORD"

echo "waiting for Traefik LoadBalancer External IP/Hostname..."
echo "this might take a few minutes..."

EXTERNAL_IP=""

while [ -z "$EXTERNAL_IP" ]; do
    echo "waiting for IP..."

    EXTERNAL_IP=$(kubectl get svc scrimfinder-traefik \
        -n "$SCRIM_NAMESPACE" \
        -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || echo "")

    if [ -z "$EXTERNAL_IP" ]; then
        echo "checking for Hostname..."

        EXTERNAL_IP=$(kubectl get svc scrimfinder-traefik \
            -n "$SCRIM_NAMESPACE" \
            -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || echo "")
    fi

    [ -z "$EXTERNAL_IP" ] && sleep 10
done

echo "Traefik External IP/Hostname: $EXTERNAL_IP"
echo "deployment complete!"
