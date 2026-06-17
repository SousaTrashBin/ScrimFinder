#!/bin/bash
set -e

PROJECT_ID="${SCRIM_PROJECT_ID}"

echo "Importing BigQuery datasets into Terraform state..."

cd infrastructure/terraform

# Import the three persistent datasets
# Using || true to prevent failures if already imported
terraform import google_bigquery_dataset.scrimfinder "${PROJECT_ID}:scrimfinder" || true
terraform import google_bigquery_dataset.scrimfinder_platform "${PROJECT_ID}:scrimfinder_platform" || true
terraform import google_bigquery_dataset.ml_db "${PROJECT_ID}:ml_db" || true

echo "BigQuery datasets imported successfully!"
