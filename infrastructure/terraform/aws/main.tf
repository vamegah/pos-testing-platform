# infrastructure/terraform/aws/main.tf
# Terraform configuration for AWS POS Test Platform
#
# This defines the infrastructure for the POS test environment on AWS:
#   - VPC with public/private subnets
#   - EKS cluster for running POS services
#   - ECR repository for container images
#   - RDS for transaction data (optional)
#   - IAM roles and policies
#
# **WARNING: This will create AWS resources that incur costs.**
# Do not run `terraform apply` without reviewing and approving the plan.
#
# Usage:
#   terraform init
#   terraform plan -out=tfplan
#   terraform apply tfplan  # ONLY AFTER REVIEW

terraform {
  required_version = ">= 1.0.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.23"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.10"
    }
  }
  # Uncomment for remote state storage (recommended)
  # backend "s3" {
  #   bucket         = "pos-test-terraform-state"
  #   key            = "aws/pos-test/terraform.tfstate"
  #   region         = "us-west-2"
  #   encrypt        = true
  #   dynamodb_table = "terraform-state-lock"
  # }
}

# Provider configuration
provider "aws" {
  region = var.aws_region
}

# ============================================
# Networking - VPC
# ============================================

# VPC
resource "aws_vpc" "pos_test" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "${var.environment}-pos-vpc"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# Public Subnets
resource "aws_subnet" "public" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.pos_test.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 4, count.index)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name        = "${var.environment}-pos-public-${var.availability_zones[count.index]}"
    Environment = var.environment
    Project     = "pos-test-platform"
    Type        = "public"
    Terraform   = "true"
    # Required for EKS
    "kubernetes.io/role/elb" = "1"
  }
}

# Private Subnets
resource "aws_subnet" "private" {
  count = length(var.availability_zones)

  vpc_id            = aws_vpc.pos_test.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 4, count.index + length(var.availability_zones))
  availability_zone = var.availability_zones[count.index]

  tags = {
    Name        = "${var.environment}-pos-private-${var.availability_zones[count.index]}"
    Environment = var.environment
    Project     = "pos-test-platform"
    Type        = "private"
    Terraform   = "true"
    # Required for EKS
    "kubernetes.io/role/internal-elb" = "1"
  }
}

# Internet Gateway
resource "aws_internet_gateway" "pos_test" {
  vpc_id = aws_vpc.pos_test.id

  tags = {
    Name        = "${var.environment}-pos-igw"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# Elastic IP for NAT Gateway
resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name        = "${var.environment}-pos-nat-eip"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# NAT Gateway
resource "aws_nat_gateway" "pos_test" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  tags = {
    Name        = "${var.environment}-pos-nat"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }

  depends_on = [aws_internet_gateway.pos_test]
}

# Public Route Table
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.pos_test.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.pos_test.id
  }

  tags = {
    Name        = "${var.environment}-pos-public-rt"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# Private Route Table
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.pos_test.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.pos_test.id
  }

  tags = {
    Name        = "${var.environment}-pos-private-rt"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# Route Table Associations - Public
resource "aws_route_table_association" "public" {
  count = length(var.availability_zones)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# Route Table Associations - Private
resource "aws_route_table_association" "private" {
  count = length(var.availability_zones)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# ============================================
# Security Groups
# ============================================

# EKS Cluster Security Group
resource "aws_security_group" "eks_cluster" {
  name        = "${var.environment}-pos-eks-cluster"
  description = "Security group for EKS cluster"
  vpc_id      = aws_vpc.pos_test.id

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "${var.environment}-pos-eks-cluster-sg"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# EKS Node Security Group
resource "aws_security_group" "eks_nodes" {
  name        = "${var.environment}-pos-eks-nodes"
  description = "Security group for EKS worker nodes"
  vpc_id      = aws_vpc.pos_test.id

  ingress {
    from_port       = 0
    to_port         = 0
    protocol        = "-1"
    security_groups = [aws_security_group.eks_cluster.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name        = "${var.environment}-pos-eks-nodes-sg"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# ============================================
# IAM - EKS Cluster Role
# ============================================

resource "aws_iam_role" "eks_cluster" {
  name = "${var.environment}-pos-eks-cluster-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "eks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

resource "aws_iam_role_policy_attachment" "eks_cluster_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
  role       = aws_iam_role.eks_cluster.name
}

resource "aws_iam_role_policy_attachment" "eks_vpc_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSVPCResourceController"
  role       = aws_iam_role.eks_cluster.name
}

# ============================================
# IAM - EKS Node Role
# ============================================

resource "aws_iam_role" "eks_node" {
  name = "${var.environment}-pos-eks-node-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

resource "aws_iam_role_policy_attachment" "eks_worker_node_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKSWorkerNodePolicy"
  role       = aws_iam_role.eks_node.name
}

resource "aws_iam_role_policy_attachment" "eks_cni_policy" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEKS_CNI_Policy"
  role       = aws_iam_role.eks_node.name
}

resource "aws_iam_role_policy_attachment" "eks_container_registry" {
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
  role       = aws_iam_role.eks_node.name
}

# ============================================
# EKS Cluster
# ============================================

resource "aws_eks_cluster" "pos_test" {
  name     = "${var.environment}-pos-test"
  role_arn = aws_iam_role.eks_cluster.arn
  version  = var.eks_version

  vpc_config {
    subnet_ids         = concat(aws_subnet.public[*].id, aws_subnet.private[*].id)
    security_group_ids = [aws_security_group.eks_cluster.id]
    endpoint_public_access = true
    endpoint_private_access = true
  }

  tags = {
    Name        = "${var.environment}-pos-eks"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy,
    aws_iam_role_policy_attachment.eks_vpc_policy,
  ]
}

# ============================================
# EKS Node Group
# ============================================

resource "aws_eks_node_group" "pos_test" {
  cluster_name    = aws_eks_cluster.pos_test.name
  node_group_name = "${var.environment}-pos-node-group"
  node_role_arn   = aws_iam_role.eks_node.arn
  subnet_ids      = aws_subnet.private[*].id

  scaling_config {
    desired_size = var.node_desired_size
    max_size     = var.node_max_size
    min_size     = var.node_min_size
  }

  instance_types = var.node_instance_types

  remote_access {
    ec2_ssh_key = var.ssh_key_name
  }

  ami_type = "AL2_x86_64"
  capacity_type = "ON_DEMAND"

  tags = {
    Name        = "${var.environment}-pos-node-group"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy,
    aws_iam_role_policy_attachment.eks_cni_policy,
    aws_iam_role_policy_attachment.eks_container_registry,
  ]
}

# ============================================
# ECR Repository for POS Services
# ============================================

resource "aws_ecr_repository" "pos_services" {
  count = length(var.ecr_repositories)

  name = "${var.environment}-${var.ecr_repositories[count.index]}"

  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# ============================================
# Optional: RDS PostgreSQL for Transaction Data
# ============================================

# resource "aws_db_instance" "pos_transactions" {
#   identifier     = "${var.environment}-pos-transactions"
#   engine         = "postgres"
#   engine_version = "15.3"
#   instance_class = "db.t3.medium"
#   allocated_storage = 20
#   storage_encrypted = true
#   db_name  = "postransactions"
#   username = var.rds_username
#   password = var.rds_password
#   port     = 5432
#
#   vpc_security_group_ids = [aws_security_group.rds.id]
#   db_subnet_group_name   = aws_db_subnet_group.pos_test.name
#
#   backup_retention_period = 7
#   backup_window          = "03:00-04:00"
#   maintenance_window     = "sun:04:00-sun:05:00"
#
#   skip_final_snapshot = true
#   deletion_protection = false
#
#   tags = {
#     Environment = var.environment
#     Project     = "pos-test-platform"
#     Terraform   = "true"
#   }
# }
#
# resource "aws_db_subnet_group" "pos_test" {
#   name       = "${var.environment}-pos-db-subnet-group"
#   subnet_ids = aws_subnet.private[*].id
#
#   tags = {
#     Environment = var.environment
#     Project     = "pos-test-platform"
#     Terraform   = "true"
#   }
# }
#
# resource "aws_security_group" "rds" {
#   name        = "${var.environment}-pos-rds-sg"
#   description = "Security group for RDS"
#   vpc_id      = aws_vpc.pos_test.id
#
#   ingress {
#     from_port       = 5432
#     to_port         = 5432
#     protocol        = "tcp"
#     security_groups = [aws_security_group.eks_nodes.id]
#   }
#
#   tags = {
#     Name        = "${var.environment}-pos-rds-sg"
#     Environment = var.environment
#     Project     = "pos-test-platform"
#     Terraform   = "true"
#   }
# }