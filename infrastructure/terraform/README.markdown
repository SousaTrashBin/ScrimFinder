# ScrimFinder GKE Infrastructure (Terraform)

This directory provisions the GCP infrastructure used by ScrimFinder:

- GKE zonal cluster with Workload Identity
- VPC and secondary ranges for pods/services
- least-privilege node service account
- Google Artifact Registry Docker repository
- Workload Identity service accounts for Python services and secret sync

## Usage

```bash
cd infrastructure/terraform
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan
terraform apply
```

For CI or scripted deploys, pass variables explicitly:

```bash
terraform -chdir=infrastructure/terraform init
terraform -chdir=infrastructure/terraform apply -auto-approve \
  -var="project_id=$SCRIM_PROJECT_ID" \
  -var="region=$SCRIM_REGION" \
  -var="cluster_name=$SCRIM_CLUSTER_NAME" \
  -var="repo_name=$SCRIM_REPO_NAME" \
  -var="environment=$SCRIM_ENV_TAG"
```

After apply:

```bash
gcloud container clusters get-credentials "$(terraform output -raw cluster_name)" \
  --zone "$(terraform output -raw zone)" \
  --project "$(terraform output -raw project)"
```

Terraform owns cloud infrastructure. Helm owns Kubernetes application resources and
database PVCs, so destroying this Terraform stack can remove the cluster while
leaving cloud disks depending on your Kubernetes reclaim policy.
