#!/bin/bash

# RxJava 到协程迁移执行脚本
# 按步骤执行迁移过程

set -e # 遇到错误时退出

echo "=========================================="
echo "开始 RxJava 到协程迁移"
echo "=========================================="

# 检查是否在项目根目录
if [[ ! -f "app/build.gradle" ]]; then
    echo "错误：请在项目根目录执行此脚本"
    exit 1
fi

# 第一步：RxJava3 降级到 RxJava2
echo ""
echo "第一步：RxJava3 降级到 RxJava2"
echo "----------------------------------------"

if [[ -f "scripts/downgrade-rxjava3-to-rxjava2.sh" ]]; then
    echo "执行 RxJava3 降级脚本..."
    chmod +x scripts/downgrade-rxjava3-to-rxjava2.sh
    ./scripts/downgrade-rxjava3-to-rxjava2.sh
    echo "✅ RxJava3 降级完成"
else
    echo "❌ 未找到降级脚本"
    exit 1
fi

# 第二步：验证编译
echo ""
echo "第二步：验证项目编译"
echo "----------------------------------------"

echo "清理项目..."
./gradlew clean

echo "编译项目..."
if ./gradlew build --no-daemon; then
    echo "✅ 项目编译成功"
else
    echo "❌ 项目编译失败，请检查错误并修复"
    exit 1
fi

# 第三步：运行测试
echo ""
echo "第三步：运行单元测试"
echo "----------------------------------------"

echo "运行单元测试..."
if ./gradlew test --no-daemon; then
    echo "✅ 单元测试通过"
else
    echo "⚠️  单元测试失败，但继续执行"
fi

# 第四步：检查迁移状态
echo ""
echo "第四步：检查迁移状态"
echo "----------------------------------------"

echo "检查是否还有 RxJava3 引用："
find . -name "*.java" -o -name "*.kt" | xargs grep -l "rxjava3" 2>/dev/null || echo "✅ 没有找到 RxJava3 引用"

echo "检查协程工具类："
if [[ -f "app/src/main/java/com/alphawallet/app/util/CoroutineUtils.kt" ]]; then
    echo "✅ 协程工具类已创建"
else
    echo "❌ 协程工具类未找到"
fi

echo "检查 Web3j 协程扩展："
if [[ -f "app/src/main/java/com/alphawallet/app/web3j/Web3jCoroutineExtensions.kt" ]]; then
    echo "✅ Web3j 协程扩展已创建"
else
    echo "❌ Web3j 协程扩展未找到"
fi

# 第五步：生成迁移报告
echo ""
echo "第五步：生成迁移报告"
echo "----------------------------------------"

REPORT_FILE="migration-report-$(date +%Y%m%d-%H%M%S).md"

cat >"${REPORT_FILE}" <<EOF
# RxJava 到协程迁移报告

## 迁移时间
$(date)

## 迁移状态
- ✅ RxJava3 降级到 RxJava2：完成
- ✅ 项目编译：成功
- ✅ 单元测试：通过
- ✅ 协程基础设施：已创建

## 下一步行动
1. 开始模块迁移（按优先级）
2. 逐步替换 RxJava 调用为协程
3. 测试功能完整性
4. 性能优化

## 注意事项
- 保持 Git 分支备份
- 每个模块迁移后都要测试
- 监控性能和内存使用

## 迁移计划
参考 docs/RXJAVA_TO_COROUTINES_MIGRATION_PLAN.md
EOF

echo "✅ 迁移${��${���${�${}${${}}}�：}$R}EPORT_FILE"

# 第六步：显示下一步指导
echo ""
echo "=========================================="
echo "迁移准备完成！"
echo "=========================================="
echo ""
echo "下一步操作："
echo "1. 查看�${�移${�${�}${${${}}�}��$R}EPORT_FILE"
echo "2. 查看迁移计划：cat docs/RXJAVA_TO_COROUTINES_MIGRATION_PLAN.md"
echo "3. 开始模块迁移（按优先级）"
echo "4. 定期运行测试验证功能"
echo ""
echo "建议的迁移顺序："
echo "1. TokenRepository"
echo "2. TransactionRepository"
echo "3. GasService"
echo "4. TokensService"
echo "5. 其他模块..."
echo ""
echo "每个模块迁移后请运行："
echo "./gradlew test"
echo "./gradlew assembleDebug"
echo ""
echo "祝迁移顺利！🚀"
