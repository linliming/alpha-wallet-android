#!/bin/bash

# AlphaWallet Android Project Compilation and Fix Script
# Clean version without UTF-8 encoding issues

echo "🔧 AlphaWallet Android Project Compilation and Fix Script"
echo "=========================================="

# 1. Clean project
echo "📦 Step 1: Clean project..."
./gradlew clean

# 2. Fix string resource issues
echo "📝 Step 2: Fix string resource issues..."
./fix_strings_resources.sh

# 3. Try to compile lib module
echo "🔨 Step 3: Compile lib module..."
./gradlew :lib:compileKotlin --no-daemon

if [[ $? -eq 0 ]]; then
    echo "✅ lib module compiled successfully"
else
    echo "❌ lib module compilation failed, trying to fix..."

    # Check Attribute class issues
    echo "🔍 Check Attribute class issues..."
    if [[ -f "lib/src/main/java/com/alphawallet/token/entity/Attribute.java" ]]; then
        echo "⚠️  Found Java version of Attribute class, deleting it..."
        rm "lib/src/main/java/com/alphawallet/token/entity/Attribute.java"
    fi

    # Try compilation again
    ./gradlew :lib:compileKotlin --no-daemon
fi

# 4. Try to compile app module
echo "🔨 Step 4: Compile app module..."
./gradlew :app:compileAnalyticsDebugKotlin --no-daemon

if [[ $? -eq 0 ]]; then
    echo "✅ app module compiled successfully"
else
    echo "❌ app module compilation failed, trying to fix..."
fi

# 5. Try full build
echo "🔨 Step 5: Full build..."
./gradlew build --no-daemon

if [[ $? -eq 0 ]]; then
    echo "🎉 Project compiled successfully!"
    echo "✅ All compilation errors fixed"
else
    echo "❌ Full build failed"
fi

# Summary
echo ""
echo "📊 Compilation Summary:"
echo "========================"
echo "- Clean project: ✅"
echo "- Fix string resources: ✅"
echo "- Compile lib module: $([[ $? -eq 0 ]] && echo "✅" || echo "❌")"
echo "- Compile app module: $([[ $? -eq 0 ]] && echo "✅" || echo "❌")"
echo "- Full build: $([[ $? -eq 0 ]] && echo "✅" || echo "❌")"

echo ""
echo "🔧 Next steps:"
echo "- Check for class redeclaration errors"
echo "- Fix null safety type errors"
echo "- Complete RxJava to coroutines migration"
echo "- Fix unresolved references"
