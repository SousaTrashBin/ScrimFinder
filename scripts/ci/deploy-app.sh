#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# shellcheck source=../lib/cloud-functions.sh
source "$ROOT_DIR/scripts/lib/cloud-functions.sh"

: "${SCRIM_PROJECT_ID:?}"
: "${SCRIM_REGION:?}"
: "${SCRIM_REPO_NAME:?}"
: "${SCRIM_CLUSTER_NAME:?}"
: "${SCRIM_NAMESPACE:?}"
: "${SCRIM_IMAGE_TAG:?}"
: "${RIOT_API_KEY:?}"
: "${SCRIM_DB_USER:?}"
: "${SCRIM_DB_PASSWORD:?}"

SCRIM_REDIS_PASSWORD="${SCRIM_REDIS_PASSWORD:-redispassword}"
SCRIM_RABBITMQ_USER="${SCRIM_RABBITMQ_USER:-user}"
SCRIM_RABBITMQ_PASSWORD="${SCRIM_RABBITMQ_PASSWORD:-rabbitmqpassword}"
SCRIM_RABBITMQ_ERLANG_COOKIE="${SCRIM_RABBITMQ_ERLANG_COOKIE:-erlangcookie}"
SCRIM_RABBITMQ_HOST="${SCRIM_RABBITMQ_HOST:-scrimfinder-rabbitmq-broker}"
SCRIM_RABBITMQ_PORT="${SCRIM_RABBITMQ_PORT:-5672}"
SCRIM_SECRET_NAME_PREFIX="${SCRIM_SECRET_NAME_PREFIX:-}"
SCRIM_SECRETS_SERVICE_ACCOUNT_ID="${SCRIM_SECRETS_SERVICE_ACCOUNT_ID:-secrets-service-account}"

zone="${SCRIM_REGION}-a"
registry="${SCRIM_REGION}-docker.pkg.dev/${SCRIM_PROJECT_ID}/${SCRIM_REPO_NAME}"

gcloud container clusters get-credentials "$SCRIM_CLUSTER_NAME" --zone "$zone" --project "$SCRIM_PROJECT_ID"

FUNCTIONS_BUILD_SERVICE_ACCOUNT="$(functions_build_service_account "$SCRIM_PROJECT_ID")"

ensure_riot_api_secret "$SCRIM_PROJECT_ID" "$RIOT_API_KEY"
package_detail_filling_function_source "$ROOT_DIR" false clean package -DskipTests -Dspotless.skip=true
deploy_detail_filling_functions "$ROOT_DIR" "$SCRIM_REGION" "$FUNCTIONS_BUILD_SERVICE_ACCOUNT"
discover_detail_filling_domain "$SCRIM_REGION"

echo "installing VPA CRDs..."
git clone https://github.com/kubernetes/autoscaler.git || true
cp -f ./scripts/gencerts.sh ./autoscaler/vertical-pod-autoscaler/pkg/admission-controller/
cd autoscaler/vertical-pod-autoscaler
./hack/vpa-up.sh
cd ../..
rm -r autoscaler &
wait

helm dependency update "$ROOT_DIR/k8s/charts/scrimfinder"

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

#if ! helm upgrade --install scrimfinder "$ROOT_DIR/k8s/charts/scrimfinder" \
#  --namespace "$SCRIM_NAMESPACE" \
#  --create-namespace \
#  --wait \
#  --timeout 25m \
#  --set global.namespace="$SCRIM_NAMESPACE" \
#  --set global.projectID="$SCRIM_PROJECT_ID" \
#  --set global.microservicesRegistry="$registry" \
#  --set global.imageTag="$SCRIM_IMAGE_TAG" \
#  --set global.useSecretManager=true \
#  --set global.useArgoApplications=true \
#  --set global.useVerticalPodAutoscaler=true \
#  --set detailFillingExternal.enabled=true \
#  --set detailFillingExternal.externalName="${DETAIL_FILLING_DOMAIN}" \
#  --set global.rabbitmqHost="$SCRIM_RABBITMQ_HOST" \
#  --set global.rabbitmqPort="$SCRIM_RABBITMQ_PORT" \
#  --set matchmaking-db.readReplicas.count=0 \
#  --set ranking-db.readReplicas.count=0 \
#  --set history-db.readReplicas.count=0 \
#  --set secrets.riotApiKey="$RIOT_API_KEY" \
#  --set secrets.dbUser="$SCRIM_DB_USER" \
#  --set secrets.dbPassword="$SCRIM_DB_PASSWORD" \
#  --set secrets.redisPassword="$SCRIM_REDIS_PASSWORD" \
#  --set secrets.rabbitmqUser="$SCRIM_RABBITMQ_USER" \
#  --set secrets.rabbitmqPassword="$SCRIM_RABBITMQ_PASSWORD" \
#  --set secrets.rabbitmqErlangCookie="$SCRIM_RABBITMQ_ERLANG_COOKIE" \
#  --set services.detail-filling-service.enabled=false \
#  --set services.ranking-service.env.DETAIL_FILLING_SERVICE_URL="http://scrimfinder-traefik/api/v1/riot" \
#  --set services.match-history-service.env.PLAYER_FILLING_SVC_URL="http://scrimfinder-traefik/api/v1/riot" \
#  --set services.training-service.env.DETAIL_FILLING_URL="http://scrimfinder-traefik/api/v1/riot"; then
#  echo "helm deploy failed; collecting kubernetes diagnostics for namespace: $SCRIM_NAMESPACE"
#  kubectl get deploy,sts,po,svc -n "$SCRIM_NAMESPACE" -o wide || true
#  kubectl get events -n "$SCRIM_NAMESPACE" --sort-by=.metadata.creationTimestamp | tail -n 200 || true
#  kubectl describe deployment analysis-service -n "$SCRIM_NAMESPACE" || true

#  for pod in $(kubectl get pods -n "$SCRIM_NAMESPACE" -l app=analysis-service -o name 2>/dev/null); do
#    echo "=== describe $pod ==="
#    kubectl describe "$pod" -n "$SCRIM_NAMESPACE" || true
#    echo "=== logs $pod (current) ==="
#    kubectl logs "$pod" -n "$SCRIM_NAMESPACE" --all-containers=true --tail=300 || true
#    echo "=== logs $pod (previous) ==="
#    kubectl logs "$pod" -n "$SCRIM_NAMESPACE" --all-containers=true --previous --tail=300 || true
#  done

#  exit 1
#fi

echo "deploying Argo CD..."

export SCRIM_NAMESPACE="${SCRIM_NAMESPACE}"
export PROJECT_ID="${SCRIM_PROJECT_ID}"
export REGION="${SCRIM_REGION}"
export REPO_NAME="${SCRIM_REPO_NAME}"
export SCRIM_IMAGE_TAG="${SCRIM_IMAGE_TAG:-latest}"
export SCRIM_RABBITMQ_HOST="${SCRIM_RABBITMQ_HOST}"
export SCRIM_RABBITMQ_PORT="${SCRIM_RABBITMQ_PORT}"
export SCRIM_SECRET_NAME_PREFIX="${SCRIM_SECRET_NAME_PREFIX}"
export SCRIM_SECRETS_SERVICE_ACCOUNT_ID="${SCRIM_SECRETS_SERVICE_ACCOUNT_ID}"
export DETAIL_FILLING_DOMAIN="${DETAIL_FILLING_DOMAIN}"
export TARGET_REVISION="${TARGET_REVISION:-${GITHUB_HEAD_REF:-${GITHUB_REF_NAME:-main}}}"

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

collect_argocd_diagnostics() {
    echo "collecting Argo CD diagnostics..."
    kubectl -n argocd get svc,endpoints,pods -l app.kubernetes.io/name=argocd-repo-server || true
    kubectl -n argocd logs deploy/argocd-repo-server --tail=200 || true
    kubectl -n argocd logs statefulset/argocd-application-controller --tail=200 || true
}

run_argocd_with_retries() {
    local description="$1"
    shift

    local max_attempts=5
    local retry_delay_seconds=20
    local attempt

    for attempt in $(seq 1 "$max_attempts"); do
        echo "${description} (attempt ${attempt}/${max_attempts})..."
        if "$@"; then
            return 0
        fi

        if [ "$attempt" -lt "$max_attempts" ]; then
            echo "${description} failed; retrying in ${retry_delay_seconds}s..."
            sleep "$retry_delay_seconds"
        fi
    done

    echo "${description} failed after ${max_attempts} attempts."
    collect_argocd_diagnostics
    return 1
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
run_argocd_with_retries \
    "syncing Argo CD application 'scrimfinder'" \
    argocd --core app sync scrimfinder --app-namespace argocd --prune --timeout 2400

if grep -q "::1" /etc/hosts; then
    sudo sed -i '/::1/d' /etc/hosts || true
fi

run_argocd_with_retries \
    "waiting for Argo CD application 'scrimfinder'" \
    argocd --core app wait scrimfinder --app-namespace argocd --sync --health --timeout 2400

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

base_url=""
for _ in $(seq 1 90); do
  base_url="$(kubectl get svc scrimfinder-traefik -n "$SCRIM_NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].ip}' 2>/dev/null || true)"
  if [ -z "$base_url" ]; then
    base_url="$(kubectl get svc scrimfinder-traefik -n "$SCRIM_NAMESPACE" -o jsonpath='{.status.loadBalancer.ingress[0].hostname}' 2>/dev/null || true)"
  fi
  [ -n "$base_url" ] && break
  sleep 10
done

if [ -z "$base_url" ]; then
  echo "error: Traefik LoadBalancer address was not assigned"
  exit 1
fi

system_base_url="http://${base_url}"
echo "SCRIM_SYSTEM_BASE_URL=$system_base_url"
echo "BASE_URL=$system_base_url"

if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "SCRIM_SYSTEM_BASE_URL=$system_base_url"
    echo "BASE_URL=$system_base_url"
  } >> "$GITHUB_ENV"
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "base_url=$system_base_url" >> "$GITHUB_OUTPUT"
fi
