# Design Spec: Traefik Load Balancer & Automated GKE Deployment

## 1. Overview
This specification outlines the transition of the ScrimFinder platform's ingress from a local `NodePort` setup to a cloud-native `LoadBalancer` on Google Kubernetes Engine (GKE). It also defines the automation for building, pushing, and deploying all microservices using the `diogo_deploy.sh` script.

## 2. Architecture Changes

### 2.1 Traefik Ingress Controller
*   **Service Type:** The `traefik` service in `k8s/traefik/deployment.yaml` will be changed from `type: NodePort` to `type: LoadBalancer`.
*   **External IP:** In GKE, `type: LoadBalancer` automatically provisions a Google Cloud Load Balancer (GCLB) with an external IP.
*   **Reverse Proxy & Load Balancing:** Traefik will continue to act as a reverse proxy, routing traffic based on path prefixes (e.g., `/api/v1/matchmaking` -> `matchmaking-service`). It will also load balance traffic between multiple service replicas.
*   **Namespace:** All Traefik components (Deployment, Service, IngressRoute, RBAC) will be consolidated into the `kube-system` namespace.

### 2.2 Microservices Deployment
*   **Image Registry:** All application deployments in `k8s/apps/scrimfinder-apps.yaml` will be updated to use the GCP Artifact Registry path: `europe-west3-docker.pkg.dev/scrimfinder-12345/scrimfinder/<service-name>:latest`.
*   **Consistency:** Each microservice will have a corresponding `Deployment`, `Service`, and (where applicable) `HorizontalPodAutoscaler` (HPA).

## 3. Automation: `diogo_deploy.sh`

The deployment script will be expanded to handle the full lifecycle:

1.  **GCP Setup:** Enable required APIs (Artifact Registry, GKE, Compute).
2.  **Artifact Registry:** Create the repository if it doesn't exist.
3.  **Build & Push:** Iterate through all services (Matchmaking, Ranking, Match History, Detail Filling, Training, Analysis), build their Docker images, and push them to the registry.
4.  **GKE Credentials:** Authenticate `kubectl` with the target GKE cluster.
5.  **Kubernetes Deployment:**
    *   Apply Traefik CRDs and RBAC.
    *   Apply Infrastructure (Postgres, Redis, PVCs).
    *   Apply Traefik Deployment and Service.
    *   Apply Application Deployments and IngressRoutes.
6.  **Verification:** Wait for the Traefik LoadBalancer to receive an external IP and display it to the user.

## 4. Implementation Plan

### Step 1: Update Traefik Manifests
*   Modify `k8s/traefik/deployment.yaml` to set `type: LoadBalancer`.
*   Update `k8s/traefik/ingressroutes.yaml` to ensure all path prefixes are correctly mapped to the app services.

### Step 2: Update Application Manifests
*   Update `k8s/apps/scrimfinder-apps.yaml` with the correct GCP image paths for all 6 microservices.

### Step 3: Enhance `diogo_deploy.sh`
*   Add build/push logic for all services.
*   Add `kubectl apply` commands for all manifests.
*   Add logic to retrieve and display the Traefik external IP.

## 5. Verification Strategy
*   **Build Check:** Verify all images are successfully pushed to GCP Artifact Registry.
*   **Deployment Check:** Run `kubectl get pods -n kube-system` and `kubectl get pods` to ensure all components are running.
*   **Ingress Check:** Retrieve the external IP with `kubectl get svc traefik -n kube-system` and test a sample endpoint (e.g., `curl http://<EXTERNAL_IP>/api/v1/matchmaking/health`).
