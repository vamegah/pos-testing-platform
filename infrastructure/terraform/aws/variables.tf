# infrastructure/terraform/aws/variables.tf
# Variables for AWS POS Test Platform

variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-west-2"
}

variable "environment" {
  description = "Environment name (dev, test, staging, prod)"
  type        = string
  default     = "test"
}

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "Availability zones for subnets"
  type        = list(string)
  default     = ["us-west-2a", "us-west-2b", "us-west-2c"]
}

variable "eks_version" {
  description = "Kubernetes version for EKS"
  type        = string
  default     = "1.28"
}

variable "node_instance_types" {
  description = "Instance types for EKS worker nodes"
  type        = list(string)
  default     = ["t3.medium", "t3.large"]
}

variable "node_desired_size" {
  description = "Desired number of worker nodes"
  type        = number
  default     = 2
}

variable "node_min_size" {
  description = "Minimum number of worker nodes"
  type        = number
  default     = 1
}

variable "node_max_size" {
  description = "Maximum number of worker nodes"
  type        = number
  default     = 5
}

variable "ssh_key_name" {
  description = "SSH key name for EC2 instances"
  type        = string
  default     = "pos-test-key"
}

variable "ecr_repositories" {
  description = "ECR repository names for POS services"
  type        = list(string)
  default     = [
    "pricing-service",
    "promotions-service",
    "tax-service",
    "payment-gateway",
    "pos-worker"
  ]
}

# Optional: RDS variables (commented out by default)
# variable "rds_username" {
#   description = "RDS username"
#   type        = string
#   sensitive   = true
# }
#
# variable "rds_password" {
#   description = "RDS password"
#   type        = string
#   sensitive   = true
# }