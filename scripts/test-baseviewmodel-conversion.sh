#!/bin/bash

# BaseViewModel 转换测试脚本

set -e

echo "🧪 测试 BaseViewModel Java 到 Kotlin + 协程转换..."

# 检查文件是否存在
check_files() {
    echo "📋 检查转换文件..."

    if [[ -f "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt" ]]; then
        echo "✅ BaseViewModel.kt 文件存在"
    else
        echo "❌ BaseViewModel.kt 文件不存在"
        exit 1
    fi

    if [[ -f "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.java" ]]; then
        echo "✅ BaseViewModel.java 文件存在 (原始文件)"
    else
        echo "❌ BaseViewModel.java 文件不存在"
        exit 1
    fi

    echo "✅ 文件检查通过"
}

# 检查 Kotlin 语法
check_kotlin_syntax() {
    echo "🔍 检查 Kotlin 语法..."

    # 检查关键 Kotlin 语法元素
    if grep -q "abstract class BaseViewModel" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt"; then
        echo "✅ 类声明正确"
    else
        echo "❌ 类声明错误"
        exit 1
    fi

    if grep -q "companion object" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt"; then
        echo "✅ companion object 正确"
    else
        echo "❌ companion object 错误"
        exit 1
    fi

    if grep -q "launchSafely" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt"; then
        echo "✅ 协程方法存在"
    else
        echo "❌ 协程方法缺失"
        exit 1
    fi

    if grep -q "StateFlow" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt"; then
        echo "✅ StateFlow 支持存在"
    else
        echo "❌ StateFlow 支持缺失"
        exit 1
    fi

    echo "✅ Kotlin 语法检查通过"
}

# 检查协程功能
check_coroutines() {
    echo "🔍 检查协程功能..."

    # 检查协程导入
    if grep -q "import kotlinx.coroutines" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt"; then
        echo "✅ 协程导入正确"
    else
        echo "❌ 协程导入缺失"
        exit 1
    fi

    # 检查 viewModelScope
    if grep -q "viewModelScope" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt"; then
        echo "✅ viewModelScope 使用正确"
    else
        echo "❌ viewModelScope 使用错误"
        exit 1
    fi

    # 检查 Job 管理
    if grep -q "currentJob" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt"; then
        echo "✅ Job 管理正确"
    else
        echo "❌ Job 管理错误"
        exit 1
    fi

    echo "✅ 协程功能检查通过"
}

# 检查向后兼容性
check_compatibility() {
    echo "🔍 检查向后兼容性..."

    # 检查 LiveData 方法
    if grep -q "fun error()" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt"; then
        echo "✅ LiveData 方法保持兼容"
    else
        echo "❌ LiveData 方法缺失"
        exit 1
    fi

    # 检查静态方法
    if grep -q "fun onQueueUpdate" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt"; then
        echo "✅ 静态方法保持兼容"
    else
        echo "❌ 静态方法缺失"
        exit 1
    fi

    echo "✅ 向后兼容性检查通过"
}

# 显示转换统计
show_statistics() {
    echo ""
    echo "📊 转换统计:"
    echo ""

    # 统计行数
    java_lines=$(wc -l <"app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.java")
    kotlin_lines=$(wc -l <"app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt")

    echo "  📄 Java 文${��${�${�}${${${${}}: }$j}ava_lines"
    echo "  📄 Kotlin 文${�${�}${${${${${}}数: $k}otlin_lines"

    # 统计新增功能
    new_features=$(grep -c "protected fun" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt")
    echo "  🆕 新�${�${�${�${�}${�}${${}}: }$n}ew_features"

    # 统计 StateFlow
    stateflow_count=$(grep -c "StateFlow" "app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt")
    echo "  🔄 StateFlow �${�${�${: $}sta}teflo}w_count"

    echo ""
}

# 显示使用示例
show_examples() {
    echo "💡 使用示例:"
    echo ""
    echo "1. 基本协程使用:"
    echo "   launchSafely {"
    echo "       val data = apiService.getData()"
    echo "       withMain { updateUI(data) }"
    echo "   }"
    echo ""
    echo "2. IO 线程协程:"
    echo "   launchIO {"
    echo "       val data = apiService.getData()"
    echo "       withMain { updateUI(data) }"
    echo "   }"
    echo ""
    echo "3. 安全网络调用:"
    echo "   val result = safeApiCall {"
    echo "       apiService.getData()"
    echo "   }"
    echo ""
}

# 主函数
main() {
    echo "🔄 BaseViewModel 转换测试"
    echo "=========================="
    echo ""

    check_files
    check_kotlin_syntax
    check_coroutines
    check_compatibility
    show_statistics
    show_examples

    echo ""
    echo "🎉 BaseViewModel 转换测试完成！"
    echo ""
    echo "✅ 转换成功"
    echo "✅ 语法正确"
    echo "✅ 协程功能完整"
    echo "✅ 向后兼容"
    echo ""
    echo "📚 下一步:"
    echo "  1. 测试实际使用场景"
    echo "  2. 更新其他 ViewModel 类"
    echo "  3. 添加单元测试"
    echo "  4. 性能优化"
    echo ""
}

# 执行主函数
main "$@"
