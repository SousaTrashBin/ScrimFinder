variable "project_id" {
  description = "GCP project ID."
  type        = string
}

variable "region" {
  description = "GCP region."
  type        = string
  default     = "europe-west1"
}

variable "zone_suffix" {
  description = "Zone suffix appended to the region."
  type        = string
  default     = "a"
}

variable "cluster_name" {
  description = "GKE cluster name."
  type        = string
  default     = "scrimfinder"
}

variable "node_machine_type" {
  description = "Machine type for the default node pool."
  type        = string
  default     = "e2-standard-4"
}

variable "node_count" {
  description = "Initial node count."
  type        = number
  default     = 1
}

variable "min_nodes" {
  description = "Minimum autoscaled nodes."
  type        = number
  default     = 1
}

variable "max_nodes" {
  description = "Maximum autoscaled nodes."
  type        = number
  default     = 3
}

variable "disk_size_gb" {
  description = "GKE node boot disk size."
  type        = number
  default     = 40
}

variable "repo_name" {
  description = "Artifact Registry Docker repository name."
  type        = string
  default     = "scrimfinder"
}

variable "enable_spot" {
  description = "Whether to use Spot VMs for the node pool."
  type        = bool
  default     = true
}

variable "gke_channel" {
  description = "GKE release channel."
  type        = string
  default     = "REGULAR"
}

variable "master_cidr" {
  description = "CIDR allowed to access the public GKE control plane endpoint."
  type        = string
  default     = "0.0.0.0/0"
}

variable "namespace" {
  description = "Kubernetes namespace used by Workload Identity bindings."
  type        = string
  default     = "scrimfinder"
}

variable "environment" {
  description = "Environment suffix used in resource names."
  type        = string
  default     = "dev"
}
