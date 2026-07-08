# infrastructure/terraform/azure/variables.tf
# Variables for Azure POS Test Platform

variable "environment" {
  description = "Environment name (dev, test, staging, prod)"
  type        = string
  default     = "test"
}

variable "location" {
  description = "Azure region for resources"
  type        = string
  default     = "eastus"
}

variable "availability_zones" {
  description = "Availability zones for subnets"
  type        = list(string)
  default     = ["1", "2", "3"]
}

variable "vnet_cidr" {
  description = "CIDR block for Virtual Network"
  type        = string
  default     = "10.0.0.0/16"
}

variable "aks_version" {
  description = "Kubernetes version for AKS"
  type        = string
  default     = "1.27.7"
}

variable "node_vm_size" {
  description = "VM size for AKS worker nodes"
  type        = string
  default     = "Standard_B2s"
}

variable "node_count" {
  description = "Initial number of worker nodes"
  type        = number
  default     = 2
}

variable "node_min_count" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 1
}

variable "node_max_count" {
  description = "Maximum number of worker nodes"
  type        = number
  default     = 5
}

# Optional: PostgreSQL variables (commented out by default)
# variable "pgsql_username" {
#   description = "PostgreSQL administrator username"
#   type        = string
#   sensitive   = true
# }
#
# variable "pgsql_password" {
#   description = "PostgreSQL administrator password"
#   type        = string
#   sensitive   = true
# }