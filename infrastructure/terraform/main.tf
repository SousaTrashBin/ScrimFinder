locals {
  zone = "${var.region}-${var.zone_suffix}"

  required_apis = toset([
    "artifactregistry.googleapis.com",
    "cloudbuild.googleapis.com",
    "cloudfunctions.googleapis.com",
    "compute.googleapis.com",
    "container.googleapis.com",
    "eventarc.googleapis.com",
    "iam.googleapis.com",
    "run.googleapis.com",
    "secretmanager.googleapis.com",
  ])

  workload_identity_services = toset([
    "analysis-service",
    "jwt-manager",
    "scrimfinder-secrets-reader",
    "training-service",
  ])
}

resource "google_project_service" "required" {
  for_each = local.required_apis

  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

resource "google_compute_network" "scrimfinder" {
  name                    = "scrimfinder-vpc-${var.environment}"
  project                 = var.project_id
  auto_create_subnetworks = false
  description             = "ScrimFinder VPC (${var.environment})"

  depends_on = [google_project_service.required]
}

resource "google_compute_subnetwork" "scrimfinder" {
  name                     = "scrimfinder-subnet-${var.environment}"
  project                  = var.project_id
  region                   = var.region
  network                  = google_compute_network.scrimfinder.id
  ip_cidr_range            = "10.0.0.0/20"
  private_ip_google_access = true

  secondary_ip_range {
    range_name    = "pods"
    ip_cidr_range = "10.4.0.0/14"
  }

  secondary_ip_range {
    range_name    = "services"
    ip_cidr_range = "10.8.0.0/20"
  }
}

resource "google_container_cluster" "scrimfinder" {
  name     = var.cluster_name
  project  = var.project_id
  location = local.zone

  remove_default_node_pool = true
  initial_node_count       = 1

  network    = google_compute_network.scrimfinder.self_link
  subnetwork = google_compute_subnetwork.scrimfinder.self_link

  ip_allocation_policy {
    cluster_secondary_range_name  = "pods"
    services_secondary_range_name = "services"
  }

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  release_channel {
    channel = var.gke_channel
  }

  private_cluster_config {
    enable_private_nodes    = true
    enable_private_endpoint = false
    master_ipv4_cidr_block  = "172.16.0.0/28"
  }

  master_authorized_networks_config {
    cidr_blocks {
      cidr_block   = var.master_cidr
      display_name = "allowed"
    }
  }

  addons_config {
    http_load_balancing {
      disabled = false
    }
    horizontal_pod_autoscaling {
      disabled = false
    }
  }

  depends_on = [google_project_service.required]
}

resource "google_service_account" "node" {
  account_id   = "sf-node-${var.environment}"
  project      = var.project_id
  display_name = "ScrimFinder GKE Node SA (${var.environment})"
}

resource "google_project_iam_member" "node" {
  for_each = toset([
    "roles/artifactregistry.reader",
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
    "roles/monitoring.viewer",
  ])

  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.node.email}"
}

resource "google_container_node_pool" "default" {
  name     = "default-pool-${var.environment}"
  project  = var.project_id
  location = local.zone
  cluster  = google_container_cluster.scrimfinder.name

  initial_node_count = var.node_count

  autoscaling {
    min_node_count = var.min_nodes
    max_node_count = var.max_nodes
  }

  management {
    auto_repair  = true
    auto_upgrade = true
  }

  node_config {
    machine_type    = var.node_machine_type
    disk_size_gb    = var.disk_size_gb
    disk_type       = "pd-standard"
    spot            = var.enable_spot
    service_account = google_service_account.node.email
    oauth_scopes    = ["https://www.googleapis.com/auth/cloud-platform"]

    workload_metadata_config {
      mode = "GKE_METADATA"
    }

    shielded_instance_config {
      enable_secure_boot          = true
      enable_integrity_monitoring = true
    }
  }

  depends_on = [google_project_iam_member.node]
}

resource "google_artifact_registry_repository" "scrimfinder" {
  project       = var.project_id
  location      = var.region
  repository_id = var.repo_name
  format        = "DOCKER"
  description   = "ScrimFinder Docker images (${var.environment})"

  depends_on = [google_project_service.required]
}

resource "google_service_account" "workload" {
  for_each = local.workload_identity_services

  account_id   = substr("sf-${each.value}-${var.environment}", 0, 30)
  project      = var.project_id
  display_name = "ScrimFinder ${each.value} GSA (${var.environment})"
}

resource "google_service_account_iam_member" "workload_identity" {
  for_each = local.workload_identity_services

  service_account_id = google_service_account.workload[each.value].name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[${var.namespace}/${each.value}]"
}

resource "google_project_iam_member" "secrets_reader" {
  project = var.project_id
  role    = "roles/secretmanager.secretAccessor"
  member  = "serviceAccount:${google_service_account.workload["scrimfinder-secrets-reader"].email}"
}
