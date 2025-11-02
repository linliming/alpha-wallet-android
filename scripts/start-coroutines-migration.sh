#!/bin/bash

# 协程迁移启动脚本
# 第一阶段：协程集成

set -e

echo "🚀 开始协程迁移第一阶段..."

# 检查项目结构
check_project_structure() {
    echo "📋 检查项目结构..."

    if [[ ! -f "app/build.gradle" ]]; then
        echo "❌ 错误: 未找到 app/build.gradle"
        exit 1
    fi

    if [[ ! -d "app/src/main/java/com/alphawallet/app" ]]; then
        echo "❌ 错误: 未找到源代码目录"
        exit 1
    fi

    echo "✅ 项目结构检查通过"
}

# 更新 build.gradle
update_build_gradle() {
    echo "🔧 更新 build.gradle..."

    # 检查是否已经添加了协程依赖
    if ! grep -q "kotlinx-coroutines" "app/build.gradle"; then
        echo "添加协程依赖..."

        # 在 dependencies 块中添加协程依赖
        sed -i '' '/dependencies {/a\
    // Kotlin 协程\
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3"\
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"\
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.7.3"\
    \
    // 架构组件\
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"\
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.7.0"\
    implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0"' "app/build.gradle"

        echo "✅ 协程依赖已添加"
    else
        echo "✅ 协程依赖已存在"
    fi
}

# 创建协程工具类
create_coroutine_utils() {
    echo "📝 创建协程工具类..."

    local utils_dir="app/src/main/java/com/alphawallet/app/util"
    local utils_file="${utils_dir}/CoroutineUtils.kt"

    # 创建目录
    mkdir -p "${utils_dir}"

    # 创建文件
    cat >"${utils_file}" <<'EOF'
package com.alphawallet.app.util

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

object CoroutineUtils {
    
    // 主线程调度器
    val mainDispatcher = Dispatchers.Main
    
    // IO 调度器
    val ioDispatcher = Dispatchers.IO
    
    // 默认协程作用域
    val defaultScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // 安全启动协程
    fun launchSafely(
        scope: CoroutineScope = defaultScope,
        dispatcher: CoroutineDispatcher = Dispatchers.Main,
        onError: (Throwable) -> Unit = { /* 默认错误处理 */ },
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
    
    // 延迟执行
    suspend fun delay(duration: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) {
        kotlinx.coroutines.delay(unit.toMillis(duration))
    }
    
    // 超时处理
    suspend fun <T> withTimeout(
        timeMillis: Long,
        block: suspend CoroutineScope.() -> T
    ): T {
        return withTimeout(timeMillis) {
            block()
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
}
EOF

    echo "✅ 协程�${具�${�已${}���}�: $}utils_file"
}

# 创建基础 ViewModel
create_base_viewmodel() {
    echo "📝 创建基础 ViewModel..."

    local viewmodel_dir="app/src/main/java/com/alphawallet/app/viewmodel"
    local viewmodel_file="${viewmodel_dir}/BaseViewModel.kt"

    # 创建目录
    mkdir -p "${viewmodel_dir}"

    # 创建文件
    cat >"${viewmodel_file}" <<'EOF'
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
    
    // 设置加载状态
    protected fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }
    
    // 设置错误
    protected fun setError(error: String?) {
        _error.value = error
    }
}
EOF

    echo "✅ 基础 ViewModel$${�${��${${${${}}}建: $vi}ewmodel_file"
}

# 创建网络服务接口
create_network_service() {
    echo "📝 创建网络服务接口..."

    local network_dir="app/src/main/java/com/alphawallet/app/network"
    local network_file="${network_dir}/NetworkService.kt"

    # 创建目录
    mkdir -p "${network_dir}"

    # 创建文件
    cat >"${network_file}" <<'EOF'
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

    echo "✅ 网络服${���${口�${��}���}�: $}network_file"
}

# 创建示例 Repository
create_sample_repository() {
    echo "📝 创建示例 Repository..."

    local repository_dir="app/src/main/java/com/alphawallet/app/repository"
    local repository_file="${repository_dir}/TokenRepository.kt"

    # 创建目录
    mkdir -p "${repository_dir}"

    # 创建文件
    cat >"${repository_file}" <<'EOF'
package com.alphawallet.app.repository

import com.alphawallet.app.network.NetworkService
import com.alphawallet.app.entity.tokens.Token
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TokenRepository @Inject constructor(
    private val networkService: NetworkService
) {
    
    suspend fun getTokens(address: String): Result<List<Token>> {
        return networkService.executeCall {
            // 原有的网络调用逻辑
            // apiService.getTokens(address)
            emptyList() // 临时返回空列表
        }
    }
    
    fun getTokensAsFlow(address: String): Flow<Result<List<Token>>> {
        return networkService.executeCallAsFlow {
            // apiService.getTokens(address)
            emptyList() // 临时返回空列表
        }
    }
}
EOF

    echo "✅ 示例 Repository$${${��${}�${}�${${��}}: $rep}ository_file"
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

# 显示下一步
show_next_steps() {
    echo ""
    echo "🎉 协程迁移第一阶段完成！"
    echo ""
    echo "📋 已完成:"
    echo "  ✅ 添加协程依赖"
    echo "  ✅ 创建协程工具类"
    echo "  ✅ 创建基础 ViewModel"
    echo "  ✅ 创建网络服务接口"
    echo "  ✅ 创建示例 Repository"
    echo ""
    echo "📁 创建的文件:"
    echo "  - app/src/main/java/com/alphawallet/app/util/CoroutineUtils.kt"
    echo "  - app/src/main/java/com/alphawallet/app/viewmodel/BaseViewModel.kt"
    echo "  - app/src/main/java/com/alphawallet/app/network/NetworkService.kt"
    echo "  - app/src/main/java/com/alphawallet/app/repository/TokenRepository.kt"
    echo ""
    echo "🚀 下一步:"
    echo "  1. 改造现有的网络调用使用协程"
    echo "  2. 更新 ViewModel 使用 StateFlow"
    echo "  3. 添加 Room 数据库支持"
    echo "  4. 集成 Hilt 依赖注入"
    echo ""
    echo "📚 参考文档:"
    echo "  - COROUTINES_MIGRATION_PLAN.md"
    echo "  - https://kotlinlang.org/docs/coroutines-overview.html"
    echo ""
}

# 主函数
main() {
    echo "🔄 AlphaWallet 协程迁移 - 第一阶段"
    echo "=================================="
    echo ""

    check_project_structure
    update_build_gradle
    create_coroutine_utils
    create_base_viewmodel
    create_network_service
    create_sample_repository
    compile_test
    show_next_steps
}

# 执行主函数
main "$@"
