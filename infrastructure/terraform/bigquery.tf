# BigQuery datasets for persistent data storage
# These datasets contain important application data and should NOT be destroyed

resource "google_bigquery_dataset" "scrimfinder" {
  dataset_id    = "scrimfinder"
  project       = var.project_id
  location      = "EU"  # <-- FIXED: Match actual GCP location
  friendly_name = "ScrimFinder Main Database"
  description   = "Core ScrimFinder database for game and player data"

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [access]
  }

  labels = merge(
    local.common_labels,
    {
      purpose = "production-data"
    }
  )
}

resource "google_bigquery_dataset" "scrimfinder_platform" {
  dataset_id    = "scrimfinder_platform"
  project       = var.project_id
  location      = "EU"  # <-- FIXED: Match actual GCP location
  friendly_name = "ScrimFinder Platform Database"
  description   = "Platform-level data and analytics"

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [access]
  }

  labels = merge(
    local.common_labels,
    {
      purpose = "production-data"
    }
  )
}

resource "google_bigquery_dataset" "ml_db" {
  dataset_id    = "ml_db"
  project       = var.project_id
  location      = "EU"  # <-- FIXED: Match actual GCP location
  friendly_name = "Machine Learning Database"
  description   = "ML models and training data"

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [access]
  }

  labels = merge(
    local.common_labels,
    {
      purpose = "production-data"
    }
  )
}

# ── BigQuery Dataset IAM ─────────────────────────────────────────────────────

resource "google_bigquery_dataset_iam_member" "scrimfinder_viewer" {
  count      = var.manage_secret_manager ? 1 : 0
  project    = var.project_id
  dataset_id = google_bigquery_dataset.scrimfinder.dataset_id
  role       = "roles/bigquery.dataViewer"
  member     = "serviceAccount:${google_service_account.secrets_sa[0].email}"
}

resource "google_bigquery_dataset_iam_member" "platform_editor" {
  count      = var.manage_secret_manager ? 1 : 0
  project    = var.project_id
  dataset_id = google_bigquery_dataset.scrimfinder_platform.dataset_id
  role       = "roles/bigquery.dataEditor"
  member     = "serviceAccount:${google_service_account.secrets_sa[0].email}"
}