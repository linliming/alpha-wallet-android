#!/bin/bash

# RxJava3 降级到 RxJava2 脚本
# 此脚本将项目中的 RxJava3 导入语句替换为 RxJava2

echo "开始 RxJava3 降级到 RxJava2..."

# 替换 Java 文件中的 RxJava3 导入
echo "处理 Java 文件..."

# 替换 Single 导入
find . -name "*.java" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.core\.Single;/import io.reactivex.Single;/g' {} \;

# 替换 Observable 导入
find . -name "*.java" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.core\.Observable;/import io.reactivex.Observable;/g' {} \;

# 替换 Completable 导入
find . -name "*.java" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.core\.Completable;/import io.reactivex.Completable;/g' {} \;

# 替换 Disposable 导入
find . -name "*.java" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.disposables\.Disposable;/import io.reactivex.disposables.Disposable;/g' {} \;

# 替换 Schedulers 导入
find . -name "*.java" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.schedulers\.Schedulers;/import io.reactivex.schedulers.Schedulers;/g' {} \;

# 替换 AndroidSchedulers 导入
find . -name "*.java" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.android\.schedulers\.AndroidSchedulers;/import io.reactivex.android.schedulers.AndroidSchedulers;/g' {} \;

# 替换 Kotlin 文件中的 RxJava3 导入
echo "处理 Kotlin 文件..."

# 替换 Single 导入
find . -name "*.kt" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.core\.Single/import io.reactivex.Single/g' {} \;

# 替换 Observable 导入
find . -name "*.kt" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.core\.Observable/import io.reactivex.Observable/g' {} \;

# 替换 Completable 导入
find . -name "*.kt" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.core\.Completable/import io.reactivex.Completable/g' {} \;

# 替换 Disposable 导入
find . -name "*.kt" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.disposables\.Disposable/import io.reactivex.disposables.Disposable/g' {} \;

# 替换 Schedulers 导入
find . -name "*.kt" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.schedulers\.Schedulers/import io.reactivex.schedulers.Schedulers/g' {} \;

# 替换 AndroidSchedulers 导入
find . -name "*.kt" -type f -exec sed -i '' 's/import io\.reactivex\.rxjava3\.android\.schedulers\.AndroidSchedulers/import io.reactivex.android.schedulers.AndroidSchedulers/g' {} \;

echo "RxJava3 降级完成！"
echo "请检查以下文件是否还有 RxJava3 的引用："
find . -name "*.java" -o -name "*.kt" | xargs grep -l "rxjava3" 2>/dev/null || echo "没有找到 RxJava3 引用"
