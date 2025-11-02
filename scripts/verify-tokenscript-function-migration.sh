#!/bin/bash

echo "=========================================="
echo "验证 TokenscriptFunction 改造结果"
echo "=========================================="

# 检查 Kotlin 文件是否存在
echo "1. 检查 Kotlin 文件是否存在..."
if [[ -f "app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt" ]]; then
    echo "✅ Kotlin 文件存在"
else
    echo "❌ Kotlin 文件不存在"
    exit 1
fi

# 检查 Java 文件是否还存在
echo ""
echo "2. 检查原始 Java 文件..."
if [[ -f "app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.java" ]]; then
    echo "⚠️  原始 Java 文件仍然存在（可以删除）"
else
    echo "✅ 原始 Java 文件已删除"
fi

# 检查 RxJava 使用
echo ""
echo "3. 检查 RxJava 使用情况..."
rxjava_count=$(grep -r "Single\|Observable\|Completable" app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt | wc -l)
if [[ "${rxjava_count}" -eq 0 ]]; then
    echo "✅ 没有发现 RxJava 使用"
else
    echo "❌ �${�${�${ $}rx}java}_count 个 RxJava 使用"
fi

# 检查协程使用
echo ""
echo "4. 检查协程使用情况..."
coroutine_count=$(grep -r "suspend\|withContext\|Dispatchers" app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt | wc -l)
if [[ "${coroutine_count}" -gt 0 ]]; then
    echo "✅ �${�${�${ $cor}ou}tine}_count 个协程使用"
else
    echo "❌ 没有发现协程使用"
fi

# 检查中文注释
echo ""
echo "5. 检查中文注释..."
comment_count=$(grep -r "[\u4e00-\u9fff]" app/src/main/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunction.kt | wc -l)
if [[ "${comment_count}" -gt 0 ]]; then
    echo "✅ �${�${�${ $c}om}ment}_count 行中文注释"
else
    echo "❌ 没有发现中文注释"
fi

# 检查测试文件
echo ""
echo "6. 检查测试文件..."
if [[ -f "app/src/test/java/com/alphawallet/app/entity/tokenscript/TokenscriptFunctionTest.kt" ]]; then
    echo "✅ 测试文件存在"
else
    echo "❌ 测试文件不存在"
fi

# 检查编译
echo ""
echo "7. 检查编译..."
if ./gradlew compileAnalyticsDebugKotlin --no-daemon >/dev/null 2>&1; then
    echo "✅ 编译成功"
else
    echo "❌ 编译失败"
fi

echo ""
echo "=========================================="
echo "验证完成"
echo "=========================================="
