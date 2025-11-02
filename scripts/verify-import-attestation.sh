#!/bin/bash

# ImportAttestation 转换验证脚本
# 验证从 Java 到 Kotlin 的转换以及 RxJava 到协程的迁移

set -e

echo "🔍 开始验证 ImportAttestation 转换..."

# 检查文件是否存在
if [[ ! -f "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt" ]]; then
    echo "❌ ImportAttestation.kt 文件不存在"
    exit 1
fi

if [[ ! -f "app/src/test/java/com/alphawallet/app/entity/attestation/ImportAttestationTest.kt" ]]; then
    echo "❌ ImportAttestationTest.kt 文件不存在"
    exit 1
fi

echo "✅ 文件存在性检查通过"

# 检查 Kotlin 语法
echo "🔍 检查 Kotlin 语法..."
if ! grep -q "class ImportAttestation" "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt"; then
    echo "❌ 未找到 ImportAttestation 类定义"
    exit 1
fi

echo "✅ Kotlin 类定义检查通过"

# 检查协程使用
echo "🔍 检查协程使用..."
if ! grep -q "suspend fun" "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt"; then
    echo "❌ 未找到 suspend 函数"
    exit 1
fi

if ! grep -q "withContext" "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt"; then
    echo "❌ 未找到 withContext 使用"
    exit 1
fi

if ! grep -q "launchSafely" "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt"; then
    echo "❌ 未找到 launchSafely 使用"
    exit 1
fi

echo "✅ 协程使用检查通过"

# 检查中文注释
echo "🔍 检查中文注释..."
if ! grep -q "认证导入服务类" "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt"; then
    echo "❌ 未找到中文注释"
    exit 1
fi

echo "✅ 中文注释检查通过"

# 检查 RxJava 移除
echo "🔍 检查 RxJava 移除..."
if grep -q "import io.reactivex" "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt"; then
    echo "❌ 仍然存在 RxJava 导入"
    exit 1
fi

if grep -q "Single<" "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt"; then
    echo "❌ 仍然存在 RxJava Single 使用"
    exit 1
fi

if grep -q "Observable<" "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt"; then
    echo "❌ 仍然存在 RxJava Observable 使用"
    exit 1
fi

echo "✅ RxJava 移除检查通过"

# 检查数据类定义
echo "🔍 检查数据类定义..."
if ! grep -q "data class SchemaRecord" "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt"; then
    echo "❌ 未找到 SchemaRecord 数据类"
    exit 1
fi

if ! grep -q "enum class SmartPassReturn" "app/src/main/java/com/alphawallet/app/entity/attestation/ImportAttestation.kt"; then
    echo "❌ 未找到 SmartPassReturn 枚举"
    exit 1
fi

echo "✅ 数据类定义检查通过"

# 检查测试文件
echo "🔍 检查测试文件..."
if ! grep -q "class ImportAttestationTest" "app/src/test/java/com/alphawallet/app/entity/attestation/ImportAttestationTest.kt"; then
    echo "❌ 未找到 ImportAttestationTest 类"
    exit 1
fi

if ! grep -q "@Test" "app/src/test/java/com/alphawallet/app/entity/attestation/ImportAttestationTest.kt"; then
    echo "❌ 未找到测试方法"
    exit 1
fi

echo "✅ 测试文件检查通过"

# 尝试编译
echo "🔍 尝试编译..."
if ! ./gradlew compileAnalyticsDebugKotlin --no-daemon >/dev/null 2>&1; then
    echo "❌ 编译失败"
    echo "编译错误："
    ./gradlew compileAnalyticsDebugKotlin --no-daemon 2>&1 | grep -A 5 -B 5 "error\|Error" || true
    exit 1
fi

echo "✅ 编译检查通过"

# 运行测试
echo "🔍 运行测试..."
if ! ./gradlew test --tests "*ImportAttestationTest*" --no-daemon >/dev/null 2>&1; then
    echo "❌ 测试失败"
    echo "测试错误："
    ./gradlew test --tests "*ImportAttestationTest*" --no-daemon 2>&1 | grep -A 5 -B 5 "error\|Error\|FAILED" || true
    exit 1
fi

echo "✅ 测试检查通过"

echo ""
echo "🎉 ImportAttestation 转换验证完成！"
echo ""
echo "✅ 转换总结："
echo "   - Java 到 Kotlin 转换完成"
echo "   - RxJava 到协程迁移完成"
echo "   - 中文注释添加完成"
echo "   - 测试文件创建完成"
echo "   - 编译和测试通过"
echo ""
echo "📋 主要改进："
echo "   - 使用 suspend 函数替代 RxJava Single"
echo "   - 使用 withContext(Dispatchers.IO) 替代 subscribeOn(Schedulers.io())"
echo "   - 使用 launchSafely 替代 subscribe()"
echo "   - 添加了详细的中文注释"
echo "   - 创建了完整的测试覆盖"
echo ""
echo "🚀 ImportAttestation 类已成功升级为 Kotlin 协程版本！"
