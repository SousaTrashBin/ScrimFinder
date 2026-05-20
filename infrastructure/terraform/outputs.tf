output "cluster_name" {
  value = google_container_cluster.scrimfinder.name
}

output "cluster_endpoint" {
  value = google_container_cluster.scrimfinder.endpoint
}

output "zone" {
  value = local.zone
}

output "region" {
  value = var.region
}

output "project" {
  value = var.project_id
}

output "artifact_registry" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.scrimfinder.repository_id}"
}

output "node_sa_email" {
  value = google_service_account.node.email
}

output "secrets_reader_gsa_email" {
  value = google_service_account.workload["scrimfinder-secrets-reader"].email
}
