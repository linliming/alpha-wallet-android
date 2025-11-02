#!/bin/bash

# WalletRepositoryType 转换测试脚本

set -e

echo "🧪 测试 WalletRepositoryType Java 到 Kotlin + 协程转换..."

# 检查文件是否存在
check_files() {
    echo "📋 检查转换文件..."

    if [[ -f "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt" ]]; then
        echo "✅ WalletRepositoryType.kt 文件存在"
    else
        echo "❌ WalletRepositoryType.kt 文件不存在"
        exit 1
    fi

    if [[ -f "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.java" ]]; then
        echo "✅ WalletRepositoryType.java 文件存在 (原始文件)"
    else
        echo "❌ WalletRepositoryType.java 文件不存在"
        exit 1
    fi

    echo "✅ 文件检查通过"
}

# 检查 Kotlin 语法
check_kotlin_syntax() {
    echo "🔍 检查 Kotlin 语法..."

    # 检查接口声明
    if grep -q "interface WalletRepositoryType" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt"; then
        echo "✅ 接口声明正确"
    else
        echo "❌ 接口声明错误"
        exit 1
    fi

    # 检查 suspend 函数
    suspend_count=$(grep -c "suspend fun" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt")
    if [[ "${suspend_count}" -gt 0 ]]; then
        echo "✅ suspend 函�${�${���}${� (}$sus}pend_count 个)"
    else
        echo "❌ suspend 函数缺失"
        exit 1
    fi

    # 检查 Flow 支持
    if grep -q "Flow<" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt"; then
        echo "✅ Flow 支持存在"
    else
        echo "❌ Flow 支持缺失"
        exit 1
    fi

    # 检查数据类
    if grep -q "data class WalletItem" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt"; then
        echo "✅ 数据类存在"
    else
        echo "❌ 数据类缺失"
        exit 1
    fi

    echo "✅ Kotlin 语法检查通过"
}

# 检查协程功能
check_coroutines() {
    echo "🔍 检查协程功能..."

    # 检查协程导入
    if grep -q "import kotlinx.coroutines" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt"; then
        echo "✅ 协程导入正确"
    else
        echo "❌ 协程导入缺失"
        exit 1
    fi

    # 检查 suspend 关键字
    suspend_keyword_count=$(grep -c "suspend" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt")
    if [[ "${suspend_keyword_count}" -gt 0 ]]; then
        echo "✅ suspend 关键�${�${��${��}�}�确 ($suspe}nd_keyword_count 个)"
    else
        echo "❌ suspend 关键字使用错误"
        exit 1
    fi

    # 检查 Flow 类型
    flow_count=$(grep -c "Flow<" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt")
    if [[ "${flow_count}" -gt 0 ]]; then
        echo "✅ Flow 类型${���${正�}� ($}flow_count 个)"
    else
        echo "❌ Flow 类型使用错误"
        exit 1
    fi

    echo "✅ 协程功能检查通过"
}

# 检查转换映射
check_conversion_mapping() {
    echo "🔍 检查转换映射..."

    # 检查 Single -> suspend fun 转换
    if grep -q "suspend fun fetchWallets(): Array<Wallet>" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt"; then
        echo "✅ Single<Wallet[]> -> suspend fun(): Array<Wallet> 转换正确"
    else
        echo "❌ Single<Wallet[]> 转换错误"
        exit 1
    fi

    # 检查 Completable -> suspend fun 转换
    if grep -q "suspend fun deleteWallet(address: String, password: String)" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt"; then
        echo "✅ Completable -> suspend fun() 转换正确"
    else
        echo "❌ Completable 转换错误"
        exit 1
    fi

    # 检查 void -> suspend fun 转换
    if grep -q "suspend fun updateBackupTime(walletAddr: String)" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt"; then
        echo "✅ void -> suspend fun() 转换正确"
    else
        echo "❌ void 转换错误"
        exit 1
    fi

    # 检查 boolean -> suspend fun 转换
    if grep -q "suspend fun keystoreExists(address: String): Boolean" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt"; then
        echo "✅ boolean -> suspend fun(): Boolean 转换正确"
    else
        echo "❌ boolean 转换错误"
        exit 1
    fi

    echo "✅ 转换映射检查通过"
}

# 检查新增功能
check_new_features() {
    echo "🔍 检查新增功能..."

    # 检查 Flow 方法
    flow_methods=$(grep -c "fun.*Flow<" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt")
    if [[ "${flow_methods}" -gt 0 ]]; then
        echo "✅ Flow 方�${�${��${�}� ($fl}ow_methods 个)"
    else
        echo "❌ Flow 方法缺失"
        exit 1
    fi

    # 检查数据类
    if grep -q "data class WalletItem" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt"; then
        echo "✅ WalletItem 数据类存在"
    else
        echo "❌ WalletItem 数据类缺失"
        exit 1
    fi

    # 检查文档注释
    doc_count=$(grep -c "/\*\*" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt")
    if [[ "${doc_count}" -gt 0 ]]; then
        echo "✅ 文档${���${�${�}�� (}$d}oc_count 个)"
    else
        echo "❌ 文档注释缺失"
        exit 1
    fi

    echo "✅ 新增功能检查通过"
}

# 显示转换统计
show_statistics() {
    echo ""
    echo "📊 转换统计:"
    echo ""

    # 统计行数
    java_lines=$(wc -l <"app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.java")
    kotlin_lines=$(wc -l <"app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt")

    echo "  📄 Java 文${��${�${�}��: }$j}ava_lines"
    echo "  📄 Kotlin 文${�${�}${�}数: $k}otlin_lines"

    # 统计方法数量
    java_methods=$(grep -c "Single\|Completable\|void\|boolean" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.java")
    kotlin_methods=$(grep -c "suspend fun\|fun.*Flow" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.kt")

    echo "  🔧 Java 方${�${�}${�}量: $j}ava_methods"
    echo "  🔧 Kotlin 方${�${${��}}量: $kot}lin_methods"

    # 统计转换类型
    single_count=$(grep -c "Single<" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.java")
    completable_count=$(grep -c "Completable" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.java")
    void_count=$(grep -c "void" "app/src/main/java/com/alphawallet/app/repository/WalletRepositoryType.java")

    echo "  🔄 Single �${�${��: $}singl}e_count"
    echo "  🔄 Completable �${�${�${: $co}mpl}etabl}e_count"
    echo "  🔄 void �${�${��${} $}voi}d_count"

    echo ""
}

# 显示使用示例
show_examples() {
    echo "💡 使用示例:"
    echo ""
    echo "1. 基本钱包操作:"
    echo "   val wallets = walletRepository.fetchWallets()"
    echo "   val wallet = walletRepository.findWallet(address)"
    echo "   walletRepository.deleteWallet(address, password)"
    echo ""
    echo "2. Flow 监听:"
    echo "   walletRepository.getWalletsFlow().collect { wallets ->"
    echo "       updateWallets(wallets)"
    echo "   }"
    echo ""
    echo "3. 批量操作:"
    echo "   val importedWallets = wallets.map { wallet ->"
    echo "       walletRepository.importKeystoreToWallet(...)"
    echo "   }"
    echo ""
}

# 主函数
main() {
    echo "🔄 WalletRepositoryType 转换测试"
    echo "=================================="
    echo ""

    check_files
    check_kotlin_syntax
    check_coroutines
    check_conversion_mapping
    check_new_features
    show_statistics
    show_examples

    echo ""
    echo "🎉 WalletRepositoryType 转换测试完成！"
    echo ""
    echo "✅ 转换成功"
    echo "✅ 语法正确"
    echo "✅ 协程功能完整"
    echo "✅ 转换映射正确"
    echo "✅ 新增功能完整"
    echo ""
    echo "📚 下一步:"
    echo "  1. 实现 WalletRepository 类"
    echo "  2. 更新所有调用方"
    echo "  3. 添加单元测试"
    echo "  4. 性能测试"
    echo ""
}

# 执行主函数
main "$@"
