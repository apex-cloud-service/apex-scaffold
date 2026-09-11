#!/bin/bash

# SVG图表清理脚本 - 保留2026-09-10重新生成的29张图表
# 删除旧版本和不再使用的图表文件

cd /Users/work/apex-scaffold/docs/diagrams

echo "=== 开始清理旧版本SVG文件 ==="
echo ""

# 需要保留的29张新图表(2026-09-10生成)
KEEP_FILES=(
    "01_logical_architecture.svg"
    "02_physical_architecture.svg"
    "03_technical_architecture.svg"
    "04_login_authentication_flow.svg"
    "05_permission_check_flow.svg"
    "06_workflow_approval_flow.svg"
    "07_file_upload_flow.svg"
    "08_database_er_diagram.svg"
    "09_multi_level_cache_architecture.svg"
    "10_rate_limit_circuit_breaker.svg"
    "11_data_permission_filter.svg"
    "12_security_protection_system.svg"
    "13_docker_compose_local_dev.svg"
    "14_kubernetes_production_deployment.svg"
    "15_cicd_pipeline.svg"
    "16_nacos_service_discovery.svg"
    "17_zipkin_distributed_tracing.svg"
    "18_spring_cloud_gateway.svg"
    "19_frontend_vue3_architecture.svg"
    "20_axios_request_interceptor.svg"
    "21_webflux_reactive_programming.svg"
    "22_i18n_internationalization.svg"
    "23_openapi_documentation.svg"
    "24_prometheus_grafana_monitoring.svg"
    "25_loki_log_aggregation.svg"
    "26_disaster_recovery_high_availability.svg"
    "27_rocketmq_message_queue.svg"
    "28_meilisearch_fulltext_search.svg"
    "29_technology_stack_overview.svg"
    "README.md"
)

# 需要删除的旧文件列表
DELETE_FILES=(
    "01_logical_architecture_PRO.svg"
    "03_technical_architecture_PRO.svg"
    "04_login_auth_flow.svg"
    "09_sentinel_rate_limit_circuit_breaker.svg"
    "10_multi_level_cache_architecture.svg"
    "11_auto_retry_strategy_flow.svg"
    "13_deployment_architecture.svg"
    "14_network_architecture.svg"
    "15_data_flow_architecture.svg"
    "16_mq_architecture.svg"
    "17_monitoring_architecture.svg"
    "18_microservice_governance.svg"
    "19_backend_technology_stack.svg"
    "20_infrastructure_components.svg"
    "21_technology_ecosystem.svg"
    "22_frontend_technology_stack.svg"
    "23_frontend_backend_interaction.svg"
    "24_grafana_dashboard_architecture.svg"
    "25_prometheus_monitoring_architecture.svg"
    "26_disaster_recovery_flow.svg"
    "27_cicd_pipeline_detail.svg"
    "28_api_gateway_detail.svg"
    "29_security_threat_model.svg"
    "demo-cloud-native-icons.svg"
)

# 统计
DELETED_COUNT=0
KEPT_COUNT=0

# 删除旧文件
for file in "${DELETE_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "删除旧文件: $file"
        rm "$file"
        ((DELETED_COUNT++))
    fi
done

echo ""
echo "=== 验证保留的文件 ==="
for file in "${KEEP_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "✓ 保留: $file"
        ((KEPT_COUNT++))
    else
        echo "✗ 缺失: $file"
    fi
done

echo ""
echo "=== 清理完成 ==="
echo "删除文件数: $DELETED_COUNT"
echo "保留文件数: $KEPT_COUNT"
echo "预期保留: 30 (29张图表 + 1个README)"
echo ""

# 最终验证
FINAL_COUNT=$(ls -1 *.svg 2>/dev/null | wc -l | tr -d ' ')
echo "最终SVG文件数: $FINAL_COUNT (预期29张)"
