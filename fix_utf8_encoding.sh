#!/bin/bash

# 修复UTF-8编码问题的脚本
echo "🔧 修复UTF-8编码问题..."

# 修复compile_and_fix.sh中的编码问题
if [[ -f "compile_and_fix.sh" ]]; then
    echo "📝 修复 compile_and_fix.sh..."
    # 使用英文替换中文注释
    sed -i '' 's/✅ lib模块编译成功/✅ lib module compiled successfully/g' compile_and_fix.sh
    sed -i '' 's/❌ lib模块编译失败，尝试修复/❌ lib module compilation failed, trying to fix/g' compile_and_fix.sh
    sed -i '' 's/⚠️  发现Java版本的Attribute类，删除它/⚠️  Found Java version of Attribute class, deleting it/g' compile_and_fix.sh
    sed -i '' 's/✅ app模块编译成功/✅ app module compiled successfully/g' compile_and_fix.sh
    sed -i '' 's/❌ app模块编译失败，尝试修复/❌ app module compilation failed, trying to fix/g' compile_and_fix.sh
    sed -i '' 's/🎉 项目编译成功！/🎉 Project compiled successfully!/g' compile_and_fix.sh
    sed -i '' 's/✅ 所有编译错误已修复/✅ All compilation errors fixed/g' compile_and_fix.sh
    sed -i '' 's/📦 步骤 1: 清理项目/📦 Step 1: Clean project/g' compile_and_fix.sh
    sed -i '' 's/📝 步骤 2: 修复字符串资源问题/📝 Step 2: Fix string resource issues/g' compile_and_fix.sh
    sed -i '' 's/🔨 步骤 3: 编译lib模块/🔨 Step 3: Compile lib module/g' compile_and_fix.sh
    sed -i '' 's/🔨 步骤 4: 编译app模块/🔨 Step 4: Compile app module/g' compile_and_fix.sh
    sed -i '' 's/🔨 步骤 5: 完整构建/🔨 Step 5: Full build/g' compile_and_fix.sh
    sed -i '' 's/🔍 检查Attribute类问题/🔍 Check Attribute class issues/g' compile_and_fix.sh
    sed -i '' 's/清理项目: ✅/Clean project: ✅/g' compile_and_fix.sh
    sed -i '' 's/修复字符串资源: ✅/Fix string resources: ✅/g' compile_and_fix.sh
    sed -i '' 's/编译lib模块: /Compile lib module: /g' compile_and_fix.sh
    sed -i '' 's/编译app模块: /Compile app module: /g' compile_and_fix.sh
    sed -i '' 's/完整构建: /Full build: /g' compile_and_fix.sh
fi

# 修复comprehensive_fix.sh中的编码问题
if [[ -f "comprehensive_fix.sh" ]]; then
    echo "📝 修复 comprehensive_fix.sh..."
    sed -i '' 's/AlphaWallet Android 项目全面修复脚本/AlphaWallet Android Comprehensive Fix Script/g' comprehensive_fix.sh
    sed -i '' 's/==========================================/==========================================/g' comprehensive_fix.sh
    sed -i '' 's/📦 步骤 1: 清理项目/📦 Step 1: Clean project/g' comprehensive_fix.sh
    sed -i '' 's/📝 步骤 2: 修复字符串资源问题/📝 Step 2: Fix string resource issues/g' comprehensive_fix.sh
    sed -i '' 's/🗑️  步骤 3: 删除重复的Java文件/🗑️  Step 3: Delete duplicate Java files/g' comprehensive_fix.sh
    sed -i '' 's/🔧 步骤 4: 修复空安全类型错误/🔧 Step 4: Fix null safety type errors/g' comprehensive_fix.sh
    sed -i '' 's/🔄 步骤 5: 修复RxJava到协程的迁移问题/🔄 Step 5: Fix RxJava to coroutines migration/g' comprehensive_fix.sh
    sed -i '' 's/🔍 步骤 6: 修复未解析的引用/🔍 Step 6: Fix unresolved references/g' comprehensive_fix.sh
    sed -i '' 's/⚠️  发现重复文件: /⚠️  Found duplicate file: /g' comprehensive_fix.sh
    sed -i '' 's/存在对应的Kotlin版本/with corresponding Kotlin version/g' comprehensive_fix.sh
    sed -i '' 's/🗑️  删除Java版本: /🗑️  Delete Java version: /g' comprehensive_fix.sh
    sed -i '' 's/🔧 修复 /🔧 Fix /g' comprehensive_fix.sh
    sed -i '' 's/中的 ZERO_ADDRESS 引用/ ZERO_ADDRESS reference in/g' comprehensive_fix.sh
    sed -i '' 's/✅ lib模块编译成功/✅ lib module compiled successfully/g' comprehensive_fix.sh
    sed -i '' 's/❌ lib模块编译失败/❌ lib module compilation failed/g' comprehensive_fix.sh
    sed -i '' 's/✅ app模块编译成功/✅ app module compiled successfully/g' comprehensive_fix.sh
    sed -i '' 's/❌ app模块编译失败/❌ app module compilation failed/g' comprehensive_fix.sh
    sed -i '' 's/🎉 项目编译成功！/🎉 Project compiled successfully!/g' comprehensive_fix.sh
    sed -i '' 's/✅ 所有编译错误已修复/✅ All compilation errors fixed/g' comprehensive_fix.sh
    sed -i '' 's/修复类重复声明: ⚠️ (需要手动修复)/Fix class redeclaration: ⚠️ (manual fix needed)/g' comprehensive_fix.sh
    sed -i '' 's/修复空安全类型: ⚠️ (需要手动修复)/Fix null safety types: ⚠️ (manual fix needed)/g' comprehensive_fix.sh
    sed -i '' 's/修复RxJava迁移: ⚠️ (需要手动修复)/Fix RxJava migration: ⚠️ (manual fix needed)/g' comprehensive_fix.sh
    sed -i '' 's/修复未解析引用: ⚠️ (需要手动修复)/Fix unresolved references: ⚠️ (manual fix needed)/g' comprehensive_fix.sh
    sed -i '' 's/编译lib模块: /Compile lib module: /g' comprehensive_fix.sh
    sed -i '' 's/编译app模块: /Compile app module: /g' comprehensive_fix.sh
    sed -i '' 's/完整构建: /Full build: /g' comprehensive_fix.sh
fi

# 修复fix_strings_resources.sh中的编码问题
if [[ -f "fix_strings_resources.sh" ]]; then
    echo "📝 修复 fix_strings_resources.sh..."
    sed -i '' 's/修复字符串资源格式化问题的脚本/Script to fix string resource formatting issues/g' fix_strings_resources.sh
    sed -i '' 's/🔧 开始修复字符串资源格式化问题/🔧 Start fixing string resource formatting issues/g' fix_strings_resources.sh
    sed -i '' 's/为包含多个替换符的字符串添加 formatted="false" 属性/Add formatted="false" attribute for strings with multiple placeholders/g' fix_strings_resources.sh
    sed -i '' 's/需要修复的字符串列表/List of strings to fix/g' fix_strings_resources.sh
    sed -i '' 's/需要修复的语言目录/Language directories to fix/g' fix_strings_resources.sh
    sed -i '' 's/修复函数/Fix function/g' fix_strings_resources.sh
    sed -i '' 's/检查字符串是否包含多个替换符/Check if string contains multiple placeholders/g' fix_strings_resources.sh
    sed -i '' 's/如果字符串包含多个替换符但没有 formatted="false"，则添加/If string contains multiple placeholders but no formatted="false", add it/g' fix_strings_resources.sh
    sed -i '' 's/📝 修复 /📝 Fix /g' fix_strings_resources.sh
    sed -i '' 's/中的 / in /g' fix_strings_resources.sh
    sed -i '' 's/使用sed替换，添加formatted="false"属性/Use sed to replace, add formatted="false" attribute/g' fix_strings_resources.sh
    sed -i '' 's/遍历所有语言目录/Iterate through all language directories/g' fix_strings_resources.sh
    sed -i '' 's/🔍 检查 /🔍 Check /g' fix_strings_resources.sh
    sed -i '' 's/修复每个需要修复的字符串/Fix each string that needs fixing/g' fix_strings_resources.sh
    sed -i '' 's/✅ 字符串资源修复完成/✅ String resource fix completed/g' fix_strings_resources.sh
fi

echo "✅ UTF-8编码问题修复完成"
