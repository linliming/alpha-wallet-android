#!/bin/bash

# 修复 AssetDefinitionService.kt 文件中的 RxJava 导入问题

echo "修复 AssetDefinitionService.kt 文件..."

# 备份原文件
cp app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt.backup

# 替换 RxJava 导入语句
sed -i '' 's/import io\.reactivex\.rxjava3\.core\.Observable/import io.reactivex.Observable/g' app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt
sed -i '' 's/import io\.reactivex\.rxjava3\.core\.Single/import io.reactivex.Single/g' app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt
sed -i '' 's/import io\.reactivex\.rxjava3\.core\.Completable/import io.reactivex.Completable/g' app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt
sed -i '' 's/import io\.reactivex\.rxjava3\.schedulers\.Schedulers/import io.reactivex.schedulers.Schedulers/g' app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt
sed -i '' 's/import io\.reactivex\.rxjava3\.android\.schedulers\.AndroidSchedulers/import io.reactivex.android.schedulers.AndroidSchedulers/g' app/src/main/java/com/alphawallet/app/service/AssetDefinitionService.kt

echo "AssetDefinitionService.kt 修复完成"
