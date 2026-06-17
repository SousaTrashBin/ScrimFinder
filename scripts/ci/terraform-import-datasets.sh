#!/usr/bin/env bash
set -euo pipefail

: "${SCRIM_PROJECT_ID:?}"

echo "Importing persistent resources into Terraform state..."

cd infrastructure/terraform

# Import BigQuery datasets (fixed names, persist across ephemeral stacks)
terraform import google_bigquery_dataset.scrimfinder "${SCRIM_PROJECT_ID}:scrimfinder" || true
terraform import google_bigquery_dataset.scrimfinder_platform "${SCRIM_PROJECT_ID}:scrimfinder_platform" || true
terraform import google_bigquery_dataset.ml_db "${SCRIM_PROJECT_ID}:ml_db" || true

# Import GKE node service account (fixed name, shared across stacks)
terraform import google_service_account.gke_nodes_sa "projects/${SCRIM_PROJECT_ID}/serviceAccounts/scrim-gke-nodes-sa@${SCRIM_PROJECT_ID}.iam.gserviceaccount.com" || true

# Import GCS bucket (fixed name per project)
terraform import google_storage_bucket.models_bucket "${SCRIM_PROJECT_ID}/scrimfinder-models-${SCRIM_PROJECT_ID}" || true

echo "Persistent resources imported successfully!"