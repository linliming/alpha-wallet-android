#!/bin/bash

echo "验证 launchSafely 方法..."

# 检查 CoroutineUtils 中是否有 launchSafely 方法
echo "检查 CoroutineUtils.kt 中的 launchSafely 方法："
grep -n "fun launchSafely" app/src/main/java/com/alphawallet/app/util/CoroutineUtils.kt

echo ""
echo "检查 AssetDefinitionService.kt 中的 launchSafely 使用："
grep -n "launchSafely" app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt | head -5

echo ""
echo "验证完成！"
