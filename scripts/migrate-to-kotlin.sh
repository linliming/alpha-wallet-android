#!/bin/bash

# Kotlin 迁移脚本
# 用于将 HomeActivity 从 Java 版本迁移到 Kotlin 版本

set -e

echo "🚀 开始 Kotlin 迁移..."

# 检查必要的工具
check_requirements() {
    echo "📋 检查系统要求..."

    if ! command -v gradle &>/dev/null; then
        echo "❌ 错误: 未找到 Gradle"
        exit 1
    fi

    if ! command -v git &>/dev/null; then
        echo "❌ 错误: 未找到 Git"
        exit 1
    fi

    echo "✅ 系统要求检查通过"
}

# 备份原始文件
backup_files() {
    echo "💾 备份原始文件..."

    if [[ -f "app/src/main/java/com/alphawallet/app/ui/HomeActivity.java" ]]; then
        cp "app/src/main/java/com/alphawallet/app/ui/HomeActivity.java" \
            "app/src/main/java/com/alphawallet/app/ui/HomeActivity.java.backup"
        echo "✅ 已备份 HomeActivity.java"
    fi

    if [[ -f "app/src/main/AndroidManifest.xml" ]]; then
        cp "app/src/main/AndroidManifest.xml" \
            "app/src/main/AndroidManifest.xml.backup"
        echo "✅ 已备份 AndroidManifest.xml"
    fi
}

# 更新 build.gradle
update_build_gradle() {
    echo "🔧 更新 build.gradle..."

    # 检查是否已经添加了 Kotlin 插件
    if ! grep -q "kotlin-android" "app/build.gradle"; then
        echo "添加 Kotlin 插件..."
        sed -i '' 's/apply plugin: '\''kotlin-android'\''/apply plugin: '\''kotlin-android'\''\napply plugin: '\''kotlin-kapt'\''/' "app/build.gradle"
    fi

    # 添加 kotlinOptions
    if ! grep -q "kotlinOptions" "app/build.gradle"; then
        echo "添加 Kotlin 选项..."
        sed -i '' '/compileOptions {/a\
    kotlinOptions {\
        jvmTarget = '\''21'\''\
    }' "app/build.gradle"
    fi

    echo "✅ build.gradle 更新完成"
}

# 更新 AndroidManifest.xml
update_manifest() {
    echo "📱 更新 AndroidManifest.xml..."

    if [[ -f "app/src/main/AndroidManifest.xml" ]]; then
        # 替换 HomeActivity 为 HomeActivityKt
        sed -i '' 's/android:name=".ui.HomeActivity"/android:name=".ui.HomeActivityKt"/g' \
            "app/src/main/AndroidManifest.xml"
        echo "✅ AndroidManifest.xml 更新完成"
    else
        echo "⚠️  警告: 未找到 AndroidManifest.xml"
    fi
}

# 检查 Kotlin 文件是否存在
check_kotlin_file() {
    echo "🔍 检查 Kotlin 文件..."

    if [[ ! -f "app/src/main/java/com/alphawallet/app/ui/HomeActivityKt.kt" ]]; then
        echo "❌ 错误: 未找到 HomeActivityKt.kt 文件"
        echo "请确保 Kotlin 版本的文件已正确创建"
        exit 1
    fi

    echo "✅ Kotlin 文件存在"
}

# 编译测试
compile_test() {
    echo "🔨 编译测试..."

    if ./gradlew assembleDebug; then
        echo "✅ 编译成功"
    else
        echo "❌ 编译失败"
        echo "请检查错误信息并修复问题"
        exit 1
    fi
}

# 运行测试
run_tests() {
    echo "🧪 运行测试..."

    if ./gradlew test; then
        echo "✅ 测试通过"
    else
        echo "⚠️  测试失败，但继续执行"
    fi
}

# 清理备份文件
cleanup_backup() {
    echo "🧹 清理备份文件..."

    if [[ $1 == "--keep-backup" ]]; then
        echo "保留备份文件"
    else
        rm -f "app/src/main/java/com/alphawallet/app/ui/HomeActivity.java.backup"
        rm -f "app/src/main/AndroidManifest.xml.backup"
        echo "✅ 备份文件已清理"
    fi
}

# 显示迁移结果
show_migration_result() {
    echo ""
    echo "🎉 Kotlin 迁移完成！"
    echo ""
    echo "📋 迁移摘要:"
    echo "  ✅ 备份原始文件"
    echo "  ✅ 更新 build.gradle"
    echo "  ✅ 更新 AndroidManifest.xml"
    echo "  ✅ 编译测试通过"
    echo ""
    echo "📁 文件变更:"
    echo "  - app/src/main/java/com/alphawallet/app/ui/HomeActivityKt.kt (新增)"
    echo "  - app/build.gradle (已更新)"
    echo "  - app/src/main/AndroidManifest.xml (已更新)"
    echo ""
    echo "🚀 下一步:"
    echo "  1. 运行应用测试功能"
    echo "  2. 验证所有功能正常工作"
    echo "  3. 查看 KOTLIN_MIGRATION.md 了解更多信息"
    echo ""
    echo "📚 相关文档:"
    echo "  - KOTLIN_MIGRATION.md"
    echo "  - app/src/main/AndroidManifest.xml.example"
    echo ""
}

# 主函数
main() {
    echo "🔄 AlphaWallet Kotlin 迁移工具"
    echo "=================================="
    echo ""

    # 检查参数
    KEEP_BACKUP=false
    while [[ $# -gt 0 ]]; do
        case $1 in
        --keep-backup)
            KEEP_BACKUP=true
            shift
            ;;
        --help)
            echo "用法: $0 [选项]"
            echo ""
            echo "选项:"
            echo "  --keep-backup    保留备份文件"
            echo "  --help           显示帮助信息"
            echo ""
            echo "示例:"
            echo "  $0               执行迁移并清理备份"
            echo "  $0 --keep-backup 执行迁移并保留备份"
            exit 0
            ;;
        *)
            echo "未知选项: $1"
            echo "使用 --help 查看帮助信息"
            exit 1
            ;;
        esac
    done

    # 执行迁移步骤
    check_requirements
    backup_files
    update_build_gradle
    update_manifest
    check_kotlin_file
    compile_test
    run_tests
    cleanup_backup "${KEEP_BACKUP}"
    show_migration_result
}

# 执行主函数
main "$@"
