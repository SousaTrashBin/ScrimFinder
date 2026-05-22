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
