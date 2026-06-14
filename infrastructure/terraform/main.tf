locals {
  zone = "${var.region}-a"

  common_labels = {
    app           = "scrimfinder"
    env           = "ci"
    environment   = var.environment_name
    github_run_id = var.github_run_id
    github_pr     = var.github_pr
  }

  required_services = toset([
    "artifactregistry.googleapis.com",
    "bigquery.googleapis.com",
    "cloudbuild.googleapis.com",
    "cloudfunctions.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "compute.googleapis.com",
    "container.googleapis.com",
    "eventarc.googleapis.com",
    "iam.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
  ])

  secret_values = {
    "${var.secret_name_prefix}riot-api-key"           = var.riot_api_key
    "${var.secret_name_prefix}db-user"                = var.db_user
    "${var.secret_name_prefix}db-password"            = var.db_password
    "${var.secret_name_prefix}redis-password"         = var.redis_password
    "${var.secret_name_prefix}rabbitmq-user"          = var.rabbitmq_user
    "${var.secret_name_prefix}rabbitmq-password"      = var.rabbitmq_password
    "${var.secret_name_prefix}rabbitmq-erlang-cookie" = var.rabbitmq_erlang_cookie
    "${var.secret_name_prefix}jwt-secret"              = var.jwt_secret
  }

  cloud_functions_deployer_roles = (!var.manage_cloud_functions_iam || var.cloud_functions_deployer_member == "") ? toset([]) : toset([
    "roles/cloudbuild.builds.editor",
    "roles/cloudfunctions.admin",
    "roles/iam.serviceAccountUser",
    "roles/run.admin",
    "roles/secretmanager.admin"
  ])
}

resource "google_project_service" "required" {
  for_each           = local.required_services
  project            = var.project_id
  service            = each.key
  disable_on_destroy = false
}

resource "google_artifact_registry_repository" "docker_repo" {
  count         = var.manage_artifact_registry_repository ? 1 : 0
  project       = var.project_id
  location      = var.region
  repository_id = var.repo_name
  description   = "Docker repository for ScrimFinder"
  format        = "DOCKER"
  labels        = local.common_labels

  depends_on = [google_project_service.required]
}

resource "google_container_cluster" "scrim_cluster" {
  name                     = var.cluster_name
  project                  = var.project_id
  location                 = local.zone
  deletion_protection      = false
  remove_default_node_pool = true
  initial_node_count       = 1
  resource_labels          = local.common_labels

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  depends_on = [
    google_project_service.required,
    google_project_iam_member.cloud_functions_deployer
  ]
}

resource "google_container_node_pool" "default_pool" {
  name       = "default-pool"
  project    = var.project_id
  location   = local.zone
  cluster    = google_container_cluster.scrim_cluster.name
  node_count = 2

  autoscaling {
    min_node_count = 1
    max_node_count = 5
  }

  node_config {
    machine_type = "e2-standard-4"
    disk_size_gb = 40
    disk_type    = "pd-standard"
    spot         = true
    labels       = local.common_labels
    oauth_scopes = ["https://www.googleapis.com/auth/cloud-platform"]
    workload_metadata_config {
      mode = "GKE_METADATA"
    }
  }
}

data "google_project" "current" {
  project_id = var.project_id
}

resource "google_project_iam_member" "functions_build_builder" {
  count   = var.manage_cloud_functions_iam ? 1 : 0
  project = var.project_id
  role    = "roles/cloudbuild.builds.builder"
  member  = "serviceAccount:${data.google_project.current.number}-compute@developer.gserviceaccount.com"
}

resource "google_project_iam_member" "functions_runtime_secret_accessor" {
  count   = var.manage_cloud_functions_iam ? 1 : 0
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${data.google_project.current.number}-compute@developer.gserviceaccount.com"
}

resource "google_project_iam_member" "cloud_functions_deployer" {
  for_each = local.cloud_functions_deployer_roles
  project  = var.project_id
  role     = each.key
  member   = var.cloud_functions_deployer_member
}

# ── GKE Service Accounts & IAM ───────────────────────────────────────────────

resource "google_service_account" "gke_nodes_sa" {
  project      = var.project_id
  account_id   = "scrim-gke-nodes-sa"
  display_name = "ScrimFinder GKE Nodes Service Account"
}

resource "google_project_iam_member" "gke_nodes_standard" {
  for_each = toset([
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/monitoring.viewer",
    "roles/stackdriver.resourceMetadata.writer",
    "roles/artifactregistry.reader",
  ])
  project = var.project_id
  role    = each.key
  member  = "serviceAccount:${google_service_account.gke_nodes_sa.email}"
}

# Permission for the CI runner to use the GKE node service account
resource "google_service_account_iam_member" "ci_gke_node_user" {
  count              = var.cloud_functions_deployer_member != "" ? 1 : 0
  service_account_id = google_service_account.gke_nodes_sa.name
  role               = "roles/iam.serviceAccountUser"
  member             = var.cloud_functions_deployer_member
}

resource "google_service_account" "secrets_sa" {
  count        = var.manage_secret_manager ? 1 : 0
  project      = var.project_id
  account_id   = var.secrets_service_account_id
  display_name = "ScrimFinder Secrets & BigQuery SA"
  description  = "Service account with access to ScrimFinder secrets and BigQuery datasets"
}

resource "google_service_account_iam_member" "workload_identity_user" {
  count              = var.manage_secret_manager ? 1 : 0
  service_account_id = google_service_account.secrets_sa[0].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[${var.namespace}/scrimfinder-secrets-reader]"
}

resource "google_service_account_iam_member" "workload_identity_token_creator" {
  count              = var.manage_secret_manager ? 1 : 0
  service_account_id = google_service_account.secrets_sa[0].name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[${var.namespace}/scrimfinder-secrets-reader]"
}

resource "google_project_iam_member" "secrets_sa_secret_accessor" {
  count   = var.manage_secret_manager ? 1 : 0
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.secrets_sa[0].email}"
}

resource "google_secret_manager_secret" "scrim_secrets" {
  for_each  = var.manage_secret_manager ? local.secret_values : {}
  project   = var.project_id
  secret_id = each.key
  labels    = local.common_labels

  replication {
    auto {}
  }

  depends_on = [google_project_service.required]
}

resource "google_secret_manager_secret_version" "scrim_secret_versions" {
  for_each    = var.manage_secret_manager ? local.secret_values : {}
  secret      = google_secret_manager_secret.scrim_secrets[each.key].id
  secret_data = each.value
}

resource "google_secret_manager_secret_iam_member" "secrets_access" {
  for_each  = var.manage_secret_manager ? google_secret_manager_secret.scrim_secrets : {}
  project   = var.project_id
  secret_id = each.key
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.secrets_sa[0].email}"
}

# ── BigQuery Datasets ────────────────────────────────────────────────────────

resource "google_bigquery_dataset" "scrimfinder" {
  project                    = var.project_id
  dataset_id                 = "scrimfinder"
  location                   = "EU"
  description                = "League data (read-only in ML services)"
  labels                     = local.common_labels
  delete_contents_on_destroy = false
  depends_on                 = [google_project_service.required]
}

resource "google_bigquery_dataset" "scrimfinder_platform" {
  project                    = var.project_id
  dataset_id                 = "scrimfinder_platform"
  location                   = "EU"
  description                = "ML platform metadata (read-write)"
  labels                     = local.common_labels
  delete_contents_on_destroy = false
  depends_on                 = [google_project_service.required]
}

# ── GCS Bucket for ML Models ─────────────────────────────────────────────────

resource "google_storage_bucket" "models_bucket" {
  project                     = var.project_id
  name                        = "scrimfinder-models-${var.project_id}"
  location                    = "EU"
  force_destroy               = true
  uniform_bucket_level_access = true
  labels                      = local.common_labels
  depends_on                  = [google_project_service.required]
}

# ── IAM for BigQuery and Storage ─────────────────────────────────────────────

resource "google_project_iam_member" "bigquery_job_user" {
  count   = var.manage_secret_manager ? 1 : 0
  project = var.project_id
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${google_service_account.secrets_sa[0].email}"
}

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

resource "google_storage_bucket_iam_member" "models_bucket_admin" {
  count  = var.manage_secret_manager ? 1 : 0
  bucket = google_storage_bucket.models_bucket.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.secrets_sa[0].email}"
}