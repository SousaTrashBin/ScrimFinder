variable "project_id" {
  type = string
}

variable "region" {
  type = string
}

variable "repo_name" {
  type = string
}

variable "cluster_name" {
  type = string
}

variable "namespace" {
  type    = string
  default = "scrimfinder"
}

variable "riot_api_key" {
  type      = string
  sensitive = true
}

variable "db_user" {
  type      = string
  sensitive = true
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "redis_password" {
  type      = string
  sensitive = true
}

variable "rabbitmq_user" {
  type      = string
  sensitive = true
}

variable "rabbitmq_password" {
  type      = string
  sensitive = true
}

variable "rabbitmq_erlang_cookie" {
  type      = string
  sensitive = true
}

variable "manage_artifact_registry_repository" {
  type    = bool
  default = true
}

variable "manage_secret_manager" {
  type    = bool
  default = true
}

variable "secret_name_prefix" {
  type        = string
  default     = ""
  description = "Optional prefix for Secret Manager secret IDs. CI uses this to avoid cross-run collisions."
}

variable "secrets_service_account_id" {
  type        = string
  default     = "secrets-service-account"
  description = "Google service account ID used by GKE workloads to access Secret Manager."
}

variable "manage_cloud_functions_iam" {
  type    = bool
  default = true
}

variable "environment_name" {
  type    = string
  default = "manual"
}

variable "github_run_id" {
  type    = string
  default = ""
}

variable "github_pr" {
  type    = string
  default = ""
}

variable "cloud_functions_deployer_member" {
  type        = string
  default     = ""
  description = "Optional IAM member, for example serviceAccount:name@project.iam.gserviceaccount.com, allowed to deploy Cloud Functions in CI."
}
