#!/bin/bash

echo "=========================================="
echo "验证 generateTransactionFunction 方法实现"
echo "=========================================="

# 检查方法是否存在
echo "1. 检查方法是否存在..."
if grep -q "fun generateTransactionFunction" app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt; then
    echo "✅ 方法存在"
else
    echo "❌ 方法不存在"
    exit 1
fi

# 检查是否为具体实现（不是抽象方法）
echo ""
echo "2. 检查是否为具体实现..."
if grep -q "fun generateTransactionFunction.*{" app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt; then
    echo "✅ 是具体实现"
else
    echo "❌ 是抽象方法"
    exit 1
fi

# 检查参数验证
echo ""
echo "3. 检查参数验证..."
if grep -q "requireNotNull.*function" app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt; then
    echo "✅ 包含参数验证"
else
    echo "❌ 缺少参数验证"
fi

# 检查 tokenId 处理
echo ""
echo "4. 检查 tokenId 处理..."
if grep -q "tokenId.bitCount.*256" app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt; then
    echo "✅ 包含 tokenId 处理"
else
    echo "❌ 缺少 tokenId 处理"
fi

# 检查参数类型处理
echo ""
echo "5. 检查参数类型处理..."
type_count=$(grep -c "params.add" app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt)
if [[ "${type_count}" -gt 0 ]]; then
    echo "✅ �${�${�� $}type}_count 个参数类型处理"
else
    echo "❌ 没有参数类型处理"
fi

# 检查返回类型处理
echo ""
echo "6. 检查返回类型处理..."
return_count=$(grep -c "returnTypes.add" app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt)
if [[ "${return_count}" -gt 0 ]]; then
    echo "✅ �${�${�${ $}re}turn}_count 个返回类型处理"
else
    echo "❌ 没有返回类型处理"
fi

# 检查错误处理
echo ""
echo "7. 检查错误处理..."
if grep -q "catch.*Exception" app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt; then
    echo "✅ 包含错误处理"
else
    echo "❌ 缺少错误处理"
fi

# 检查编译
echo ""
echo "8. 检查编译..."
if ./gradlew compileAnalyticsDebugKotlin --no-daemon >/dev/null 2>&1; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败"
fi

# 检查测试
echo ""
echo "9. 检查测试..."
if grep -q "test generateTransactionFunction" app/src/test/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunctionTest.kt; then
    echo "✅ 包含测试"
else
    echo "❌ 缺少测试"
fi

echo ""
echo "=========================================="
echo "验证完成"
echo "=========================================="
