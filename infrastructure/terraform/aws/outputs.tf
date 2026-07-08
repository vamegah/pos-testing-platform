# infrastructure/terraform/aws/outputs.tf
# Outputs for AWS POS Test Platform

output "vpc_id" {
  description = "VPC ID"
  value       = aws_vpc.pos_test.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = aws_subnet.private[*].id
}

output "eks_cluster_name" {
  description = "EKS cluster name"
  value       = aws_eks_cluster.pos_test.name
}

output "eks_cluster_endpoint" {
  description = "EKS cluster endpoint"
  value       = aws_eks_cluster.pos_test.endpoint
}

output "eks_cluster_security_group_id" {
  description = "EKS cluster security group ID"
  value       = aws_security_group.eks_cluster.id
}

output "eks_node_group_name" {
  description = "EKS node group name"
  value       = aws_eks_node_group.pos_test.node_group_name
}

output "ecr_repository_urls" {
  description = "ECR repository URLs"
  value       = {
    for i, repo in var.ecr_repositories :
    repo => aws_ecr_repository.pos_services[i].repository_url
  }
}

output "kubeconfig_command" {
  description = "Command to get kubeconfig for EKS cluster"
  value       = "aws eks update-kubeconfig --region ${var.aws_region} --name ${aws_eks_cluster.pos_test.name}"
}

# Optional: RDS outputs
# output "rds_endpoint" {
#   description = "RDS endpoint"
#   value       = aws_db_instance.pos_transactions.endpoint
# }
#
# output "rds_db_name" {
#   description = "RDS database name"
#   value       = aws_db_instance.pos_transactions.db_name
# }