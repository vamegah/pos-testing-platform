# infrastructure/terraform/azure/outputs.tf
# Outputs for Azure POS Test Platform

output "resource_group_name" {
  description = "Resource group name"
  value       = azurerm_resource_group.pos_test.name
}

output "vnet_id" {
  description = "Virtual Network ID"
  value       = azurerm_virtual_network.pos_test.id
}

output "public_subnet_ids" {
  description = "Public subnet IDs"
  value       = azurerm_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "Private subnet IDs"
  value       = azurerm_subnet.private[*].id
}

output "aks_cluster_name" {
  description = "AKS cluster name"
  value       = azurerm_kubernetes_cluster.pos_test.name
}

output "aks_cluster_fqdn" {
  description = "AKS cluster FQDN"
  value       = azurerm_kubernetes_cluster.pos_test.fqdn
}

output "aks_kube_config" {
  description = "Kubeconfig for AKS cluster (sensitive)"
  value       = azurerm_kubernetes_cluster.pos_test.kube_config_raw
  sensitive   = true
}

output "aks_get_credentials_command" {
  description = "Command to get credentials for AKS cluster"
  value       = "az aks get-credentials --resource-group ${azurerm_resource_group.pos_test.name} --name ${azurerm_kubernetes_cluster.pos_test.name}"
}

output "container_registry_name" {
  description = "ACR registry name"
  value       = azurerm_container_registry.pos_test.name
}

output "container_registry_login_server" {
  description = "ACR login server URL"
  value       = azurerm_container_registry.pos_test.login_server
}

output "container_registry_admin_enabled" {
  description = "Whether admin is enabled for ACR"
  value       = azurerm_container_registry.pos_test.admin_enabled
}

# Optional: PostgreSQL outputs
# output "postgresql_server_name" {
#   description = "PostgreSQL server name"
#   value       = azurerm_postgresql_flexible_server.pos_transactions.name
# }
#
# output "postgresql_database_name" {
#   description = "PostgreSQL database name"
#   value       = azurerm_postgresql_flexible_server_database.pos_transactions.name
# }
#
# output "postgresql_fqdn" {
#   description = "PostgreSQL server FQDN"
#   value       = azurerm_postgresql_flexible_server.pos_transactions.fqdn
# }