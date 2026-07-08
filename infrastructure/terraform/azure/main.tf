# infrastructure/terraform/azure/main.tf
# Terraform configuration for Azure POS Test Platform
#
# This defines the infrastructure for the POS test environment on Azure:
#   - Virtual Network with public/private subnets
#   - AKS cluster for running POS services
#   - ACR (Azure Container Registry) for container images
#   - Azure Database for PostgreSQL (optional)
#   - Managed identities and roles
#
# **WARNING: This will create Azure resources that incur costs.**
# Do not run `terraform apply` without reviewing and approving the plan.
#
# Usage:
#   terraform init
#   terraform plan -out=tfplan
#   terraform apply tfplan  # ONLY AFTER REVIEW

terraform {
  required_version = ">= 1.0.0"
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 3.0"
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
  # backend "azurerm" {
  #   resource_group_name  = "terraform-state-rg"
  #   storage_account_name = "postestterraformstate"
  #   container_name       = "tfstate"
  #   key                  = "azure/pos-test/terraform.tfstate"
  # }
}

# Provider configuration
provider "azurerm" {
  features {
    resource_group {
      prevent_deletion_if_contains_resources = false
    }
    key_vault {
      purge_soft_delete_on_destroy    = true
      recover_soft_deleted_key_vaults = true
    }
  }
}

# ============================================
# Resource Group
# ============================================

resource "azurerm_resource_group" "pos_test" {
  name     = "${var.environment}-pos-test-rg"
  location = var.location

  tags = {
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# ============================================
# Networking - Virtual Network
# ============================================

# Virtual Network
resource "azurerm_virtual_network" "pos_test" {
  name                = "${var.environment}-pos-vnet"
  location            = azurerm_resource_group.pos_test.location
  resource_group_name = azurerm_resource_group.pos_test.name
  address_space       = [var.vnet_cidr]

  tags = {
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# Public Subnets
resource "azurerm_subnet" "public" {
  count = length(var.availability_zones)

  name                 = "${var.environment}-pos-public-${var.availability_zones[count.index]}"
  resource_group_name  = azurerm_resource_group.pos_test.name
  virtual_network_name = azurerm_virtual_network.pos_test.name
  address_prefixes     = [cidrsubnet(var.vnet_cidr, 4, count.index)]
}

# Private Subnets
resource "azurerm_subnet" "private" {
  count = length(var.availability_zones)

  name                 = "${var.environment}-pos-private-${var.availability_zones[count.index]}"
  resource_group_name  = azurerm_resource_group.pos_test.name
  virtual_network_name = azurerm_virtual_network.pos_test.name
  address_prefixes     = [cidrsubnet(var.vnet_cidr, 4, count.index + length(var.availability_zones))]
}

# ============================================
# Network Security Groups
# ============================================

# AKS Node NSG
resource "azurerm_network_security_group" "aks_nodes" {
  name                = "${var.environment}-pos-aks-nodes-nsg"
  location            = azurerm_resource_group.pos_test.location
  resource_group_name = azurerm_resource_group.pos_test.name

  tags = {
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# AKS API Server NSG
resource "azurerm_network_security_group" "aks_api" {
  name                = "${var.environment}-pos-aks-api-nsg"
  location            = azurerm_resource_group.pos_test.location
  resource_group_name = azurerm_resource_group.pos_test.name

  # Allow HTTPS from anywhere for API server (restrict in production)
  security_rule {
    name                       = "AllowAPI"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "*"
    destination_address_prefix = "*"
  }

  tags = {
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# ============================================
# Azure Container Registry (ACR)
# ============================================

resource "azurerm_container_registry" "pos_test" {
  name                = "${var.environment}postestacr"
  resource_group_name = azurerm_resource_group.pos_test.name
  location            = azurerm_resource_group.pos_test.location
  sku                 = "Standard"
  admin_enabled       = false

  tags = {
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# ============================================
# Managed Identity for AKS
# ============================================

resource "azurerm_user_assigned_identity" "aks" {
  name                = "${var.environment}-pos-aks-identity"
  location            = azurerm_resource_group.pos_test.location
  resource_group_name = azurerm_resource_group.pos_test.name

  tags = {
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# Assign ACR pull permissions to AKS identity
resource "azurerm_role_assignment" "aks_acr" {
  principal_id         = azurerm_user_assigned_identity.aks.principal_id
  role_definition_name = "AcrPull"
  scope                = azurerm_container_registry.pos_test.id
}

# ============================================
# AKS Cluster
# ============================================

resource "azurerm_kubernetes_cluster" "pos_test" {
  name                = "${var.environment}-pos-aks"
  location            = azurerm_resource_group.pos_test.location
  resource_group_name = azurerm_resource_group.pos_test.name
  dns_prefix          = "${var.environment}-pos"
  kubernetes_version  = var.aks_version

  default_node_pool {
    name                 = "default"
    vm_size              = var.node_vm_size
    node_count           = var.node_count
    min_count            = var.node_min_count
    max_count            = var.node_max_count
    enable_auto_scaling  = true
    vnet_subnet_id       = azurerm_subnet.private[0].id
    orchestrator_version = var.aks_version
    os_disk_size_gb      = 50
    os_disk_type         = "Managed"
    type                 = "VirtualMachineScaleSets"
    
    tags = {
      Environment = var.environment
      Project     = "pos-test-platform"
      Terraform   = "true"
    }
  }

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.aks.id]
  }

  network_profile {
    network_plugin = "azure"
    network_policy = "calico"
    load_balancer_sku = "standard"
    service_cidr     = "10.100.0.0/16"
    dns_service_ip   = "10.100.0.10"
    docker_bridge_cidr = "172.17.0.1/16"
  }

  tags = {
    Name        = "${var.environment}-pos-aks"
    Environment = var.environment
    Project     = "pos-test-platform"
    Terraform   = "true"
  }
}

# ============================================
# Optional: Azure Database for PostgreSQL
# ============================================

# resource "azurerm_postgresql_flexible_server" "pos_transactions" {
#   name                   = "${var.environment}-pos-pgsql"
#   resource_group_name    = azurerm_resource_group.pos_test.name
#   location               = azurerm_resource_group.pos_test.location
#   version                = "15"
#   administrator_login    = var.pgsql_username
#   administrator_password = var.pgsql_password
#   sku_name               = "B_Standard_B1ms"
#   storage_mb             = 20480
#
#   tags = {
#     Environment = var.environment
#     Project     = "pos-test-platform"
#     Terraform   = "true"
#   }
# }
#
# resource "azurerm_postgresql_flexible_server_database" "pos_transactions" {
#   name      = "postransactions"
#   server_id = azurerm_postgresql_flexible_server.pos_transactions.id
#   charset   = "UTF8"
#   collation = "en_US.utf8"
# }

# ============================================
# Optional: Key Vault for Secrets
# ============================================

# resource "azurerm_key_vault" "pos_test" {
#   name                       = "${var.environment}-pos-kv"
#   location                   = azurerm_resource_group.pos_test.location
#   resource_group_name        = azurerm_resource_group.pos_test.name
#   tenant_id                  = data.azurerm_client_config.current.tenant_id
#   sku_name                   = "standard"
#   soft_delete_retention_days = 7
#   purge_protection_enabled   = false
#
#   tags = {
#     Environment = var.environment
#     Project     = "pos-test-platform"
#     Terraform   = "true"
#   }
# }