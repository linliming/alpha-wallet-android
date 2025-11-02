#!/bin/bash

echo "=========================================="
echo "验证 Attribute 类改造结果"
echo "=========================================="

# 检查 Kotlin 文件是否存在
echo "1. 检查 Kotlin 文件是否存在..."
if [[ -f "lib/src/main/java/com/alphawallet/token/entity/Attribute.kt" ]]; then
    echo "✅ Kotlin 文件存在"
else
    echo "❌ Kotlin 文件不存在"
    exit 1
fi

# 检查 Java 文件是否还存在
echo ""
echo "2. 检查原始 Java 文件..."
if [[ -f "lib/src/main/java/com/alphawallet/token/entity/Attribute.java" ]]; then
    echo "⚠️  原始 Java 文件仍然存在（可以删除）"
else
    echo "✅ 原始 Java 文件已删除"
fi

# 检查 Kotlin 语法特性
echo ""
echo "3. 检查 Kotlin 语法特性..."
kotlin_features=$(grep -r "var\|val\|when\|?:\\|!!\\|?\\." lib/src/main/java/com/alphawallet/token/entity/Attribute.kt | wc -l)
if [[ "${kotlin_features}" -gt 0 ]]; then
    echo "✅ �${�${�${ $kot}li}n_fe}atures 个 Kotlin 语法特性"
else
    echo "❌ 没有发现 Kotlin 语法特性"
fi

# 检查中文注释
echo ""
echo "4. 检查中文注释..."
comment_count=$(grep -r "[\u4e00-\u9fff]" lib/src/main/java/com/alphawallet/token/entity/Attribute.kt | wc -l)
if [[ "${comment_count}" -gt 0 ]]; then
    echo "✅ �${�${�${ $c}om}ment}_count 行中文注释"
else
    echo "❌ 没有发现中文注释"
fi

# 检查空安全特性
echo ""
echo "5. 检查空安全特性..."
null_safety_count=$(grep -r '?\|!!\|?:\|?.' lib/src/main/java/com/alphawallet/token/entity/Attribute.kt | wc -l)
if [[ "${null_safety_count}" -gt 0 ]]; then
    echo "✅ �${�${�${ $null_}sa}fety}_count 个空安全特性"
else
    echo "❌ 没有发现空安全特性"
fi

# 检查测试文件
echo ""
echo "6. 检查测试文件..."
if [[ -f "lib/src/test/java/com/alphawallet/token/entity/AttributeTest.kt" ]]; then
    echo "✅ 测试文件存在"
else
    echo "❌ 测试文件不存在"
fi

# 检查编译
echo ""
echo "7. 检查编译..."
if ./gradlew compileLibDebugKotlin --no-daemon >/dev/null 2>&1; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败"
fi

# 检查方法数量
echo ""
echo "8. 检查方法数量..."
method_count=$(grep -r "fun " lib/src/main/java/com/alphawallet/token/entity/Attribute.kt | wc -l)
echo "✅ �${�${�${ $}me}thod}_count 个方法"

# 检查属性数量
echo ""
echo "9. 检查属性数量..."
property_count=$(grep -r "var " lib/src/main/java/com/alphawallet/token/entity/Attribute.kt | wc -l)
echo "✅ �${�${�${ $pr}op}erty}_count 个属性"

echo ""
echo "=========================================="
echo "验证完成"
echo "=========================================="
