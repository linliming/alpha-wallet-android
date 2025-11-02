#!/bin/bash

echo "=========================================="
echo "验证 resolveAttrs 方法协程转换"
echo "=========================================="

# 检查方法是否已转换为协程
echo "1. 检查方法签名..."
if grep -q "suspend fun resolveAttrs" app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt; then
    echo "✅ 方法已转换为 suspend 函数"
else
    echo "❌ 方法未转换为 suspend 函数"
    exit 1
fi

# 检查返回类型是否改为 Flow
echo ""
echo "2. 检查返回类型..."
if grep -q "Flow<TokenScriptResult.Attribute>" app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt; then
    echo "✅ 返回类型已改为 Flow"
else
    echo "❌ 返回类型未改为 Flow"
fi

# 检查是否使用了协程流
echo ""
echo "3. 检查协程流使用..."
if grep -q "flow {" app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt; then
    echo "✅ 使用了协程流"
else
    echo "❌ 未使用协程流"
fi

# 检查是否使用了并发处理
echo ""
echo "4. 检查并发处理..."
if grep -q "async {" app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt; then
    echo "✅ 使用了并发处理"
else
    echo "❌ 未使用并发处理"
fi

# 检查是否使用了 emit
echo ""
echo "5. 检查 emit 使用..."
if grep -q "emit(" app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt; then
    echo "✅ 使用了 emit"
else
    echo "❌ 未使用 emit"
fi

# 检查是否使用了 flowOn
echo ""
echo "6. 检查 flowOn 使用..."
if grep -q "flowOn(Dispatchers.IO)" app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt; then
    echo "✅ 使用了 flowOn"
else
    echo "❌ 未使用 flowOn"
fi

# 检查错误处理
echo ""
echo "7. 检查错误处理..."
if grep -q "catch.*Exception" app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt; then
    echo "✅ 包含错误处理"
else
    echo "❌ 缺少错误处理"
fi

# 检查导入
echo ""
echo "8. 检查协程导入..."
if grep -q "import kotlinx.coroutines.flow" app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt; then
    echo "✅ 包含协程流导入"
else
    echo "❌ 缺少协程流导入"
fi

# 检查测试文件
echo ""
echo "9. 检查测试文件..."
if [[ -f "app/src/test/java/com/alphawallet/app/service/AssetDefinitionServiceTest.kt" ]]; then
    echo "✅ 测试文件存在"
else
    echo "❌ 测试文件不存在"
fi

# 检查编译
echo ""
echo "10. 检查编译..."
if ./gradlew compileAnalyticsDebugKotlin --no-daemon >/dev/null 2>&1; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败"
fi

echo ""
echo "=========================================="
echo "验证完成"
echo "=========================================="
