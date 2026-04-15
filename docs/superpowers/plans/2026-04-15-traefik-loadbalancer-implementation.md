# Traefik Load Balancer & GKE Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transition ScrimFinder ingress to a GKE LoadBalancer and automate the full deployment lifecycle.

**Architecture:** Update Traefik to `type: LoadBalancer` for external access via GCP Cloud Load Balancer. Automate build, push, and deploy steps in `diogo_deploy.sh` for all microservices.

**Tech Stack:** Kubernetes (GKE), Traefik v3, Google Cloud SDK, Bash, Docker.

---

### Task 1: Update Traefik Service to LoadBalancer

**Files:**
- Modify: `k8s/traefik/deployment.yaml`

- [ ] **Step 1: Change Service type and remove NodePorts**

Modify the `Service` part of `k8s/traefik/deployment.yaml`:
```yaml
kind: Service
apiVersion: v1
metadata:
  name: traefik
  namespace: kube-system
spec:
  type: LoadBalancer
  selector:
    app: traefik
  ports:
    - protocol: TCP
      port: 80
      name: web
      targetPort: 80
    - protocol: TCP
      port: 8080
      name: admin
      targetPort: 8080
```

- [ ] **Step 2: Verify YAML syntax**

Run: `kubectl apply --dry-run=client -f k8s/traefik/deployment.yaml`
Expected: `deployment.apps/traefik configured (dry run)`, `service/traefik configured (dry run)`

- [ ] **Step 3: Commit change**

```bash
git add k8s/traefik/deployment.yaml
git commit -m "feat(k8s): change traefik service type to LoadBalancer for GKE"
```

---

### Task 2: Update Application Manifests with GCP Image Paths

**Files:**
- Modify: `k8s/apps/scrimfinder-apps.yaml`

- [ ] **Step 1: Update image paths for all deployments**

Replace `scrimfinder/<service>:latest` with `europe-west3-docker.pkg.dev/scrimfinder-12345/scrimfinder/<service>:latest` for:
- `matchmaking-service`
- `ranking-service`
- `match-history-service`
- `detail-filling-service`
- `training-service`
- `analysis-service`

Example for `matchmaking-service`:
```yaml
      containers:
      - name: matchmaking-service
        image: europe-west3-docker.pkg.dev/scrimfinder-12345/scrimfinder/matchmaking-service:latest
```

- [ ] **Step 2: Verify YAML syntax**

Run: `kubectl apply --dry-run=client -f k8s/apps/scrimfinder-apps.yaml`
Expected: YAML is valid and dry-run succeeds.

- [ ] **Step 3: Commit change**

```bash
git add k8s/apps/scrimfinder-apps.yaml
git commit -m "feat(k8s): update application manifests with GCP Artifact Registry image paths"
```

---

### Task 3: Automate Full Build and Push in `diogo_deploy.sh`

**Files:**
- Modify: `docs/diogo_deploy.sh`

- [ ] **Step 1: Add build and push logic for all services**

Update `docs/diogo_deploy.sh` to include all 6 services:
```bash
SERVICES=("matchmaking-service" "ranking-service" "match-history-service" "detail-filling-service" "training-service" "analysis-service")

for SERVICE in "${SERVICES[@]}"; do
    echo "Building and pushing $SERVICE..."
    docker build -t "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$SERVICE:latest" "./$SERVICE"
    docker push "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO_NAME/$SERVICE:latest"
done
```

- [ ] **Step 2: Commit change**

```bash
git add docs/diogo_deploy.sh
git commit -m "feat(deploy): automate build and push for all microservices"
```

---

### Task 4: Add Kubernetes Deployment Logic to `diogo_deploy.sh`

**Files:**
- Modify: `docs/diogo_deploy.sh`

- [ ] **Step 1: Add GKE credentials and kubectl apply commands**

Add the following to the end of `docs/diogo_deploy.sh`:
```bash
CLUSTER_NAME="scrimfinder-cluster" # Adjust if necessary

echo "Fetching GKE credentials..."
gcloud container clusters get-credentials "$CLUSTER_NAME" --region "$REGION" --project "$PROJECT_ID"

echo "Applying Kubernetes manifests..."
kubectl apply -f k8s/traefik/crds.yaml
kubectl apply -f k8s/traefik/rbac.yaml
kubectl apply -f k8s/apps/infrastructure.yaml
kubectl apply -f k8s/traefik/deployment.yaml
kubectl apply -f k8s/apps/scrimfinder-apps.yaml
kubectl apply -f k8s/traefik/ingressroutes.yaml

echo "Waiting for Traefik LoadBalancer External IP..."
EXTERNAL_IP=""
while [ -z "$EXTERNAL_IP" ]; do
    echo "Waiting for IP..."
    EXTERNAL_IP=$(kubectl get svc traefik -n kube-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
    [ -z "$EXTERNAL_IP" ] && sleep 10
done

echo "Deployment complete! Traefik External IP: $EXTERNAL_IP"
```

- [ ] **Step 2: Verify script syntax**

Run: `bash -n docs/diogo_deploy.sh`
Expected: No syntax errors.

- [ ] **Step 3: Commit change**

```bash
git add docs/diogo_deploy.sh
git commit -m "feat(deploy): add GKE deployment and IP verification to diogo_deploy.sh"
```

---

### Task 5: Final Verification

- [ ] **Step 1: Run the deployment script (Dry Run simulation)**

Since we don't want to actually push/deploy unless ready, we'll verify the steps.
Run: `cat docs/diogo_deploy.sh` and manually verify the flow matches the design.

- [ ] **Step 2: Check Traefik IngressRoutes for consistency**

Verify `k8s/traefik/ingressroutes.yaml` has all services correctly mapped.

- [ ] **Step 3: Final Commit**

```bash
git commit --allow-empty -m "docs: traefik loadbalancer and automated deployment ready for use"
```
