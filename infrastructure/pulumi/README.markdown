# ScrimFinder — GKE Infrastructure (Pulumi)

Provisions the full GCP infrastructure for ScrimFinder using Pulumi (Python).
Replaces the ad-hoc `gcloud container clusters create` block that was in `deploy.sh`.

## What gets created

| Resource | Description |
|---|---|
| VPC + Subnet | Dedicated network with secondary ranges for GKE pods and services |
| GKE Cluster | Zonal cluster, private nodes, Workload Identity, release channel |
| Node Pool | Autoscaling (Spot in dev, on-demand in prod), shielded instances |
| Artifact Registry | Docker repo for all service images |
| Node Service Account | Least-privilege GSA for the node pool (4 roles only) |
| Workload Identity bindings | KSA → GSA for `training-service`, `analysis-service`, `jwt-manager` |

## Prerequisites

```bash
# 1. Pulumi CLI
curl -fsSL https://get.pulumi.com | sh

#or for windows you can use chocolatey

choco install pulumi

# 2. gke-gcloud-auth-plugin (required by kubectl for GKE auth)
gcloud components install gke-gcloud-auth-plugin

# 3. Authenticate to GCP
gcloud auth application-default login

# 4. Pulumi backend — pick one:
pulumi login --local    # stores state in ~/.pulumi  
pulumi login            # uses app.pulumi.com        (free for individuals, has history UI)
```

## First-time setup

```bash
cd infrastructure/pulumi

# Create virtualenv and install deps
python3 -m venv venv
source venv/bin/activate        # Windows: venv\Scripts\activate
pip install -r requirements.txt

# Create and select the dev stack
pulumi stack select dev --create

# Set your GCP project ID (everything else is already in Pulumi.dev.yaml)
pulumi config set gcp:project YOUR_PROJECT_ID

# Preview what will be created (~20 resources)
pulumi preview

# Apply
pulumi up
```

## Switching between environments

```bash
pulumi stack select dev    # development
pulumi stack select prod   # production
```

Each stack reads its own `Pulumi.<stack>.yaml` for node size, replica count, Spot setting, etc.

## Reading outputs after `pulumi up`

```bash
pulumi stack output cluster_name
pulumi stack output artifact_registry
pulumi stack output kubeconfig --show-secrets > ~/.kube/config
```

`deploy.sh` runs these automatically — you only need them manually for debugging.

## How `deploy.sh` uses Pulumi

`deploy.sh` calls `pulumi up --yes` before every Helm deploy.
This is fully idempotent — if the cluster exists and nothing changed, Pulumi
no-ops in under 10 seconds. If you changed a config value (e.g. `max_nodes`),
only that resource is updated; the control plane is untouched.

The Artifact Registry URL is read from `pulumi stack output artifact_registry`
and passed directly to Helm as `global.microservicesRegistry`, so project IDs
and region strings are never hardcoded outside of Pulumi.

### New required env var for `deploy.sh`

```bash
export SCRIM_PULUMI_STACK=dev    # or prod
```

Add this alongside the other `SCRIM_*` vars in your shell profile or CI secrets.

## Customising per environment

Edit `Pulumi.dev.yaml` or `Pulumi.prod.yaml`. All available keys and their
defaults are documented in `Pulumi.yaml`. Common changes:

| What to change | Key | Example value |
|---|---|---|
| Larger nodes | `scrimfinder:node_machine_type` | `e2-standard-8` |
| More replicas | `scrimfinder:max_nodes` | `6` |
| Lock down kubectl access | `scrimfinder:master_cidr` | `203.0.113.0/24` |
| Stable GKE releases | `scrimfinder:gke_channel` | `STABLE` |
| Disable Spot VMs | `scrimfinder:enable_spot` | `false` |

## Workload Identity

Three Kubernetes Service Accounts get a GCP Service Account binding out of the box:
`training-service`, `analysis-service`, and `jwt-manager`. They currently have no
extra IAM roles — if a service later needs GCS, BigQuery, or Secret Manager access,
add `gcp.projects.IAMMember` inside `_workload_identity()` in `__main__.py`.

## Destroying the stack

```bash
pulumi destroy --stack dev    # removes all GCP resources for dev
pulumi stack rm dev           # also removes the Pulumi state record
```

> **Warning:** `pulumi destroy` on prod removes the live cluster.
> The Bitnami PostgreSQL PVCs are managed by Helm, not Pulumi, so they survive
> a `pulumi destroy` — but always take a database snapshot before destroying a
> production cluster.