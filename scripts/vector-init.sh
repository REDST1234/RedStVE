#!/usr/bin/env bash
# ============================================================================
# 向量数据初始化脚本
# 将 MySQL / 文件系统中的模板、BGM、品类知识同步至 Chroma 向量库
#
# 用法：
#   bash scripts/vector-init.sh                  # 默认 http://localhost:8080
#   bash scripts/vector-init.sh http://host:port  # 自定义服务地址
# ============================================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
ENDPOINT="${BASE_URL}/api/v1/admin/vector/init"

echo ">>> 向量数据初始化"
echo ">>> 目标: ${ENDPOINT}"
echo ""

RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "${ENDPOINT}" \
  -H "Content-Type: application/json")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ]; then
  echo ">>> 初始化完成"
  echo "$BODY" | python3 -m json.tool 2>/dev/null || echo "$BODY"
else
  echo ">>> 初始化失败 (HTTP ${HTTP_CODE})"
  echo "$BODY"
  exit 1
fi
