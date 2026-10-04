#!/bin/bash

BASE_URL="http://localhost:8080/api/workflow"
USER_ID="1"

echo "========================================="
echo "工作流接口联调测试"
echo "========================================="
echo ""

# 测试 1: 流程回退
echo "【测试 1】流程回退接口"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/tasks/1000/rollback" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: ${USER_ID}" \
  -d '{
    "targetNodeCode": "manager_approval",
    "reason": "材料不完整，请补充后重新提交"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 2: 任务转办
echo "【测试 2】任务转办接口"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/tasks/1001/transfer" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: 10002" \
  -d '{
    "newAssigneeId": 10003,
    "reason": "本周出差，请帮忙代为审批"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 3: 任务委派
echo "【测试 3】任务委派接口"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/tasks/1002/delegate" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: 10002" \
  -d '{
    "delegateToId": 10004,
    "reason": "请先进行初审，审核通过后我再终审"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 4: 挂起流程
echo "【测试 4】挂起流程实例接口"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/instances/100/suspend" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: ${USER_ID}" \
  -d '{
    "reason": "发现业务数据异常，暂停审批，待修复后恢复"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 5: 恢复流程
echo "【测试 5】恢复流程实例接口"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/instances/100/resume" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: ${USER_ID}" \
  -d '{
    "reason": "业务数据已修复，恢复审批流程"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 6: 参数校验 - 空 reason
echo "【测试 6】参数校验 - 空原因"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/tasks/1000/rollback" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: ${USER_ID}" \
  -d '{
    "targetNodeCode": "manager_approval",
    "reason": ""
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

# 测试 7: 参数校验 - 缺少字段
echo "【测试 7】参数校验 - 缺少字段"
echo "----------------------------------------"
curl -X POST "${BASE_URL}/tasks/1000/transfer" \
  -H 'Content-Type: application/json' \
  -H "X-User-Id: ${USER_ID}" \
  -d '{
    "reason": "test"
  }' \
  -w "\nHTTP Status: %{http_code}\n" \
  -s
echo ""
echo ""

echo "========================================="
echo "测试完成"
echo "========================================="
