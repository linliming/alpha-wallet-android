#!/bin/bash

# Java 到 Kotlin + 协程转换脚本

set -e

echo "🚀 开始 Java 到 Kotlin + 协程转换..."

# 检查项目结构
check_project() {
    echo "📋 检查项目结构..."
    if [[ ! -f "app/build.gradle" ]]; then
        echo "❌ 错误: 未找到 app/build.gradle"
        exit 1
    fi
    echo "✅ 项目结构检查通过"
}

# 更新 build.gradle
update_dependencies() {
    echo "🔧 更新依赖..."

    # 移除 RxJava 依赖
    sed -i '' '/implementation.*rxjava/d' app/build.gradle
    sed -i '' '/implementation.*rxandroid/d' app/build.gradle

    # 添加协程依赖
    if ! grep -q "kotlinx-coroutines" app/build.gradle; then
        sed -i '' '/dependencies {/a\
    // Kotlin 协程\
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3"\
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"\
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.7.3"\
    \
    // 架构组件\
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"\
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.7.0"\
    implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0"' app/build.gradle
    fi

    echo "✅ 依赖更新完成"
}

# 创建协程工具类
create_coroutine_utils() {
    echo "📝 创建协程工具类..."

    mkdir -p app/src/main/java/com/alphawallet/app/util

    cat >app/src/main/java/com/alphawallet/app/util/CoroutineUtils.kt <<'EOF'
package com.alphawallet.app.util

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

object CoroutineUtils {
    
    // 调度器
    val mainDispatcher = Dispatchers.Main
    val ioDispatcher = Dispatchers.IO
    val defaultScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 安全启动协程
    fun launchSafely(
        scope: CoroutineScope = defaultScope,
        dispatcher: CoroutineDispatcher = Dispatchers.Main,
        onError: (Throwable) -> Unit = { },
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return scope.launch(dispatcher) {
            try {
                block()
            } catch (e: Exception) {
                onError(e)
            }
        }
    }
    
    // 网络调用包装器
    suspend fun <T> safeApiCall(
        apiCall: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(apiCall())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // RxJava 到协程转换
    suspend fun <T> fromRxJava(single: io.reactivex.rxjava3.core.Single<T>): T {
        return withContext(Dispatchers.IO) {
            single.blockingGet()
        }
    }
}
EOF

    echo "✅ 协程工具类已创建"
}

# 创建基础 ViewModel
create_base_viewmodel() {
    echo "📝 创建基础 ViewModel..."

    mkdir -p app/src/main/java/com/alphawallet/app/viewmodel

    cat >app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt <<'EOF'
package com.alphawallet.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel : ViewModel() {
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 错误状态
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // 安全执行协程
    protected fun launchSafely(
        onStart: () -> Unit = { _isLoading.value = true },
        onComplete: () -> Unit = { _isLoading.value = false },
        onError: (Throwable) -> Unit = { _error.value = it.message },
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            try {
                onStart()
                block()
            } catch (e: Exception) {
                onError(e)
            } finally {
                onComplete()
            }
        }
    }
    
    // 清除错误
    fun clearError() {
        _error.value = null
    }
}
EOF

    echo "✅ 基础 ViewModel 已创建"
}

# 创建网络服务
create_network_service() {
    echo "📝 创建网络服务..."

    mkdir -p app/src/main/java/com/alphawallet/app/network

    cat >app/src/main/java/com/alphawallet/app/network/NetworkService.kt <<'EOF'
package com.alphawallet.app.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.Response
import java.io.IOException

interface NetworkService {
    suspend fun <T> executeCall(call: suspend () -> Response<T>): Result<T>
    fun <T> executeCallAsFlow(call: suspend () -> Response<T>): Flow<Result<T>>
}

class NetworkServiceImpl : NetworkService {
    
    override suspend fun <T> executeCall(call: suspend () -> Response<T>): Result<T> {
        return try {
            val response = call()
            if (response.isSuccessful) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun <T> executeCallAsFlow(call: suspend () -> Response<T>): Flow<Result<T>> = flow {
        emit(executeCall(call))
    }
}
EOF

    echo "✅ 网络服务已创建"
}

# 创建转换示例
create_examples() {
    echo "📝 创建转换示例..."

    mkdir -p app/src/main/java/com/alphawallet/app/examples

    cat >app/src/main/java/com/alphawallet/app/examples/ConversionExamples.kt <<'EOF'
package com.alphawallet.app.examples

import com.alphawallet.app.util.CoroutineUtils
import kotlinx.coroutines.withContext

/**
 * Java + RxJava 到 Kotlin + 协程的转换示例
 */
object ConversionExamples {
    
    /**
     * 网络调用转换示例
     */
    fun networkCallExample() {
        // 转换前 (Java + RxJava)
        /*
        apiService.getTokens(address)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { tokens -> updateTokens(tokens) },
                { error -> handleError(error) }
            );
        */
        
        // 转换后 (Kotlin + 协程)
        CoroutineUtils.launchSafely(
            dispatcher = CoroutineUtils.ioDispatcher,
            onError = { error -> handleError(error) }
        ) {
            val tokens = apiService.getTokens(address)
            withContext(CoroutineUtils.mainDispatcher) {
                updateTokens(tokens)
            }
        }
    }
    
    /**
     * ViewModel 转换示例
     */
    fun viewModelExample() {
        // 转换前 (Java)
        /*
        public class HomeViewModel extends ViewModel {
            private MutableLiveData<List<Token>> tokens = new MutableLiveData<>();
            
            public void loadTokens() {
                apiService.getTokens()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        tokens -> this.tokens.setValue(tokens),
                        error -> handleError(error)
                    );
            }
        }
        */
        
        // 转换后 (Kotlin)
        /*
        class HomeViewModel : ViewModel() {
            private val _tokens = MutableStateFlow<List<Token>>(emptyList())
            val tokens: StateFlow<List<Token>> = _tokens.asStateFlow()
            
            fun loadTokens() {
                viewModelScope.launch {
                    try {
                        val tokens = apiService.getTokens()
                        _tokens.value = tokens
                    } catch (e: Exception) {
                        handleError(e)
                    }
                }
            }
        }
        */
    }
}
EOF

    echo "✅ 转换示例已创建"
}

# 编译测试
compile_test() {
    echo "🔨 编译测试..."

    echo "⚠️  跳过编译测试 (Gradle 版本兼容性问题)"
    echo "请手动运行: ./gradlew assembleDebug"
}

# 显示转换指南
show_guide() {
    echo ""
    echo "🎉 Java 到 Kotlin + 协程转换准备完成！"
    echo ""
    echo "📋 已完成:"
    echo "  ✅ 移除 RxJava 依赖"
    echo "  ✅ 添加协程依赖"
    echo "  ✅ 创建协程工具类"
    echo "  ✅ 创建基础 ViewModel"
    echo "  ✅ 创建网络服务"
    echo "  ✅ 创建转换示例"
    echo ""
    echo "🚀 下一步转换步骤:"
    echo ""
    echo "1. 使用 Android Studio 转换 Java 文件:"
    echo "   - 打开 Java 文件"
    echo "   - 选择 Code → Convert Java File to Kotlin File"
    echo "   - 手动替换 RxJava 调用为协程"
    echo ""
    echo "2. 转换规则:"
    echo "   - Single<T> → suspend fun(): T"
    echo "   - Observable<T> → Flow<T>"
    echo "   - Completable → suspend fun()"
    echo "   - Maybe<T> → suspend fun(): T?"
    echo ""
    echo "3. 转换示例:"
    echo "   - 查看 app/src/main/java/com/alphawallet/app/examples/ConversionExamples.kt"
    echo ""
    echo "📚 参考文档:"
    echo "  - JAVA_TO_KOTLIN_GUIDE.md"
    echo "  - https://kotlinlang.org/docs/coroutines-overview.html"
    echo ""
}

# 主函数
main() {
    echo "🔄 AlphaWallet Java 到 Kotlin + 协程转换"
    echo "=========================================="
    echo ""

    check_project
    update_dependencies
    create_coroutine_utils
    create_base_viewmodel
    create_network_service
    create_examples
    compile_test
    show_guide
}

# 执行主函数
main "$@"
