# AlphaWallet Kotlin 协程升级验证指南

## 🎯 验证概述

本文档详细说明了如何验证 AlphaWallet 从 Java + RxJava 到 Kotlin + 协程的升级是否成功，包括功能验证、性能验证、质量验证和用户体验验证。

## 📋 验证清单总览

### 快速验证清单

- [ ] **编译验证**: 项目能够成功编译
- [ ] **启动验证**: 应用能够正常启动
- [ ] **核心功能**: 钱包创建、导入、交易等核心功能正常
- [ ] **性能指标**: 启动时间、内存使用、响应速度达标
- [ ] **稳定性**: 无崩溃、无 ANR、无内存泄漏
- [ ] **兼容性**: 支持的 Android 版本和设备正常运行

## 🔧 技术验证

### 1. 代码质量验证

#### 1.1 Kotlin 转换验证

**验证脚本**:

```bash
#!/bin/bash
# verify-kotlin-conversion.sh

echo "🔍 验证 Kotlin 转换..."

# 检查 Java 文件数量
JAVA_COUNT=$(find app/src/main/java -name "*.java" | wc -l)
echo "剩余 Java 文件: $JAVA_COUNT"

# 检查 Kotlin 文件数量
KOTLIN_COUNT=$(find app/src/main/java -name "*.kt" | wc -l)
echo "Kotlin 文件: $KOTLIN_COUNT"

# 计算转换进度
TOTAL=$((JAVA_COUNT + KOTLIN_COUNT))
if [ $TOTAL -gt 0 ]; then
    PROGRESS=$((KOTLIN_COUNT * 100 / TOTAL))
    echo "转换进度: $PROGRESS%"
fi

# 检查 RxJava 残留
RXJAVA_IMPORTS=$(grep -r "import io.reactivex" app/src/main/java | wc -l)
echo "RxJava 导入残留: $RXJAVA_IMPORTS"

# 检查协程使用
CORROUTINE_IMPORTS=$(grep -r "import kotlinx.coroutines" app/src/main/java | wc -l)
echo "协程导入: $COROUTINE_IMPORTS"

# 验证结果
if [ $JAVA_COUNT -eq 0 ] && [ $RXJAVA_IMPORTS -eq 0 ] && [ $COROUTINE_IMPORTS -gt 0 ]; then
    echo "✅ Kotlin 转换验证通过"
    exit 0
else
    echo "❌ Kotlin 转换验证失败"
    exit 1
fi
```

#### 1.2 代码规范验证

**Detekt 配置验证**:

```bash
# 运行代码质量检查
./gradlew detekt

# 检查结果
if [ $? -eq 0 ]; then
    echo "✅ 代码规范验证通过"
else
    echo "❌ 代码规范验证失败，请查看 build/reports/detekt/detekt.html"
fi
```

**KtLint 格式验证**:

```bash
# 检查代码格式
./gradlew ktlintCheck

# 自动修复格式问题
./gradlew ktlintFormat
```

#### 1.3 依赖验证

**验证脚本**:

```bash
#!/bin/bash
# verify-dependencies.sh

echo "🔍 验证依赖配置..."

# 检查协程依赖
if grep -q "kotlinx-coroutines-core" app/build.gradle; then
    echo "✅ 协程核心依赖已添加"
else
    echo "❌ 缺少协程核心依赖"
fi

if grep -q "kotlinx-coroutines-android" app/build.gradle; then
    echo "✅ 协程 Android 依赖已添加"
else
    echo "❌ 缺少协程 Android 依赖"
fi

# 检查架构组件依赖
if grep -q "lifecycle-viewmodel-ktx" app/build.gradle; then
    echo "✅ ViewModel KTX 依赖已添加"
else
    echo "❌ 缺少 ViewModel KTX 依赖"
fi

# 检查 Hilt 依赖
if grep -q "hilt-android" app/build.gradle; then
    echo "✅ Hilt 依赖已添加"
else
    echo "❌ 缺少 Hilt 依赖"
fi

# 检查 RxJava 依赖是否移除
if grep -q "rxjava" app/build.gradle; then
    echo "❌ RxJava 依赖未完全移除"
else
    echo "✅ RxJava 依赖已移除"
fi
```

### 2. 编译验证

#### 2.1 编译成功验证

```bash
#!/bin/bash
# verify-compilation.sh

echo "🔨 验证编译..."

# 清理项目
./gradlew clean

# 编译 Debug 版本
echo "编译 Debug 版本..."
if ./gradlew assembleDebug; then
    echo "✅ Debug 编译成功"
else
    echo "❌ Debug 编译失败"
    exit 1
fi

# 编译 Release 版本
echo "编译 Release 版本..."
if ./gradlew assembleRelease; then
    echo "✅ Release 编译成功"
else
    echo "❌ Release 编译失败"
    exit 1
fi

# 检查 APK 大小
DEBUG_SIZE=$(stat -f%z app/build/outputs/apk/debug/app-debug.apk)
RELEASE_SIZE=$(stat -f%z app/build/outputs/apk/release/app-release.apk)

echo "Debug APK 大小: $(($DEBUG_SIZE / 1024 / 1024)) MB"
echo "Release APK 大小: $(($RELEASE_SIZE / 1024 / 1024)) MB"

# APK 大小不应该显著增加
if [ $RELEASE_SIZE -lt 50000000 ]; then # 50MB
    echo "✅ APK 大小合理"
else
    echo "⚠️ APK 大小较大，需要优化"
fi
```

#### 2.2 编译时间验证

```bash
#!/bin/bash
# verify-build-time.sh

echo "⏱️ 验证编译时间..."

# 清理项目
./gradlew clean

# 测量编译时间
start_time=$(date +%s)
./gradlew assembleDebug
end_time=$(date +%s)

build_time=$((end_time - start_time))
echo "编译时间: ${build_time} 秒"

# 编译时间不应该显著增加
if [ $build_time -lt 300 ]; then # 5分钟
    echo "✅ 编译时间合理"
else
    echo "⚠️ 编译时间较长，需要优化"
fi
```

## 🧪 功能验证

### 1. 自动化功能测试

#### 1.1 单元测试验证

```bash
#!/bin/bash
# verify-unit-tests.sh

echo "🧪 运行单元测试..."

# 运行所有单元测试
if ./gradlew testDebugUnitTest; then
    echo "✅ 单元测试通过"
else
    echo "❌ 单元测试失败"
    exit 1
fi

# 生成测试报告
./gradlew jacocoTestReport

# 检查测试覆盖率
COVERAGE=$(grep -o 'Total.*[0-9]\+%' app/build/reports/jacoco/test/html/index.html | grep -o '[0-9]\+%' | head -1 | grep -o '[0-9]\+')

if [ "$COVERAGE" -ge 80 ]; then
    echo "✅ 测试覆盖率: ${COVERAGE}% (达标)"
else
    echo "❌ 测试覆盖率: ${COVERAGE}% (不达标，要求 ≥80%)"
fi
```

#### 1.2 协程测试验证

```kotlin
// CoroutineTestVerification.kt
@ExperimentalCoroutinesApi
class CoroutineTestVerification {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `verify coroutine cancellation works`() = runTest {
        val repository = mockk<TokenRepository>()
        val viewModel = HomeViewModel(repository)

        // 启动一个长时间运行的协程
        val job = launch {
            viewModel.loadTokens("0x123")
            delay(10000) // 模拟长时间操作
        }

        // 取消协程
        job.cancel()

        // 验证协程被正确取消
        assertTrue(job.isCancelled)
    }

    @Test
    fun `verify error handling in coroutines`() = runTest {
        val repository = mockk<TokenRepository>()
        coEvery { repository.getTokens(any()) } throws IOException("Network error")

        val viewModel = HomeViewModel(repository)
        viewModel.loadTokens("0x123")

        // 验证错误状态
        val state = viewModel.tokens.value
        assertTrue(state is UiState.Error)
    }

    @Test
    fun `verify StateFlow behavior`() = runTest {
        val repository = mockk<TokenRepository>()
        val expectedTokens = listOf(Token("0x123", "Test", "TEST"))
        coEvery { repository.getTokens(any()) } returns Result.success(expectedTokens)

        val viewModel = HomeViewModel(repository)

        // 收集状态变化
        val states = mutableListOf<UiState<List<Token>>>()
        val job = launch {
            viewModel.tokens.collect { states.add(it) }
        }

        viewModel.loadTokens("0x123")

        // 验证状态变化序列
        assertEquals(UiState.Loading, states[0])
        assertTrue(states[1] is UiState.Success)

        job.cancel()
    }
}
```

### 2. 手动功能测试

#### 2.1 核心功能测试清单

**钱包功能**:

- [ ] 创建新钱包
- [ ] 导入钱包（助记词、私钥、Keystore）
- [ ] 钱包备份
- [ ] 钱包删除
- [ ] 多钱包切换

**代币功能**:

- [ ] 代币列表显示
- [ ] 代币余额更新
- [ ] 添加自定义代币
- [ ] 代币搜索和过滤
- [ ] 代币详情查看

**交易功能**:

- [ ] 发送代币
- [ ] 接收代币
- [ ] 交易历史查看
- [ ] 交易详情查看
- [ ] 交易状态更新

**DApp 功能**:

- [ ] DApp 浏览器
- [ ] WalletConnect 连接
- [ ] DApp 交互
- [ ] 签名授权
- [ ] 合约调用

#### 2.2 功能测试脚本

```kotlin
// FunctionalTestSuite.kt
@RunWith(AndroidJUnit4::class)
class FunctionalTestSuite {

    @get:Rule
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Test
    fun testWalletCreationFlow() {
        // 点击创建钱包
        onView(withId(R.id.btn_create_wallet))
            .perform(click())

        // 输入钱包名称
        onView(withId(R.id.et_wallet_name))
            .perform(typeText("Test Wallet"))

        // 点击创建
        onView(withId(R.id.btn_create))
            .perform(click())

        // 验证钱包创建成功
        onView(withText("钱包创建成功"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testTokenListDisplay() {
        // 等待代币列表加载
        onView(withId(R.id.rv_tokens))
            .check(matches(isDisplayed()))

        // 验证至少有一个代币
        onView(withId(R.id.rv_tokens))
            .check(matches(hasMinimumChildCount(1)))
    }

    @Test
    fun testSendTokenFlow() {
        // 点击第一个代币
        onView(withId(R.id.rv_tokens))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

        // 点击发送按钮
        onView(withId(R.id.btn_send))
            .perform(click())

        // 输入接收地址
        onView(withId(R.id.et_recipient_address))
            .perform(typeText("0x742d35Cc6634C0532925a3b8D4C9db96c4b4d8b"))

        // 输入金额
        onView(withId(R.id.et_amount))
            .perform(typeText("0.1"))

        // 点击发送
        onView(withId(R.id.btn_send_confirm))
            .perform(click())

        // 验证交易确认页面
        onView(withText("确认交易"))
            .check(matches(isDisplayed()))
    }
}
```

## 📊 性能验证

### 1. 启动性能验证

#### 1.1 启动时间测量

```kotlin
// StartupPerformanceTest.kt
@RunWith(AndroidJUnit4::class)
class StartupPerformanceTest {

    @Test
    fun measureColdStartupTime() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext

        // 杀死应用进程
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.killBackgroundProcesses(context.packageName)

        // 测量启动时间
        val startTime = System.currentTimeMillis()

        val intent = Intent(context, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        // 等待首屏渲染完成
        onView(withId(R.id.main_container))
            .check(matches(isDisplayed()))

        val endTime = System.currentTimeMillis()
        val startupTime = endTime - startTime

        // 验证启动时间
        assertTrue("冷启动时间应该小于 2.5 秒，实际: ${startupTime}ms", startupTime < 2500)

        Log.i("Performance", "冷启动时间: ${startupTime}ms")
    }

    @Test
    fun measureHotStartupTime() {
        // 先启动应用
        val activityRule = ActivityScenarioRule(HomeActivity::class.java)
        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        // 将应用移到后台
        activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        // 测量热启动时间
        val startTime = System.currentTimeMillis()

        activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        val endTime = System.currentTimeMillis()
        val startupTime = endTime - startTime

        // 验证热启动时间
        assertTrue("热启动时间应该小于 1 秒，实际: ${startupTime}ms", startupTime < 1000)

        Log.i("Performance", "热启动时间: ${startupTime}ms")
    }
}
```

#### 1.2 内存使用验证

```kotlin
// MemoryUsageTest.kt
@RunWith(AndroidJUnit4::class)
class MemoryUsageTest {

    @Test
    fun measureMemoryUsage() {
        val activityRule = ActivityScenarioRule(HomeActivity::class.java)

        activityRule.scenario.onActivity { activity ->
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val usedMemoryMB = usedMemory / 1024 / 1024

            // 验证内存使用
            assertTrue("内存使用应该小于 120MB，实际: ${usedMemoryMB}MB", usedMemoryMB < 120)

            Log.i("Performance", "内存使用: ${usedMemoryMB}MB")
        }
    }

    @Test
    fun detectMemoryLeaks() {
        // 多次创建和销毁 Activity
        repeat(10) {
            val scenario = ActivityScenario.launch(HomeActivity::class.java)
            scenario.close()
        }

        // 强制垃圾回收
        System.gc()
        Thread.sleep(1000)
        System.gc()

        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val usedMemoryMB = usedMemory / 1024 / 1024

        // 验证没有明显的内存泄漏
        assertTrue("可能存在内存泄漏，内存使用: ${usedMemoryMB}MB", usedMemoryMB < 150)
    }
}
```

### 2. 运行时性能验证

#### 2.1 网络性能测试

```kotlin
// NetworkPerformanceTest.kt
class NetworkPerformanceTest {

    @Test
    fun measureApiResponseTime() = runTest {
        val apiService = // 获取 API 服务实例

        val startTime = System.currentTimeMillis()

        try {
            val tokens = apiService.getTokens("0x123")
            val endTime = System.currentTimeMillis()
            val responseTime = endTime - startTime

            // 验证响应时间
            assertTrue("API 响应时间应该小于 600ms，实际: ${responseTime}ms", responseTime < 600)

            Log.i("Performance", "API 响应时间: ${responseTime}ms")
        } catch (e: Exception) {
            fail("API 调用失败: ${e.message}")
        }
    }

    @Test
    fun measureConcurrentRequests() = runTest {
        val apiService = // 获取 API 服务实例
        val addresses = listOf("0x123", "0x456", "0x789")

        val startTime = System.currentTimeMillis()

        // 并发请求
        val results = addresses.map { address ->
            async {
                apiService.getTokens(address)
            }
        }.awaitAll()

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime

        // 验证并发性能
        assertTrue("并发请求时间应该小于 1 秒，实际: ${totalTime}ms", totalTime < 1000)
        assertTrue("所有请求都应该成功", results.all { it.isNotEmpty() })

        Log.i("Performance", "并发请求时间: ${totalTime}ms")
    }
}
```

#### 2.2 UI 性能测试

```kotlin
// UIPerformanceTest.kt
@RunWith(AndroidJUnit4::class)
class UIPerformanceTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Test
    fun measureListScrollPerformance() {
        // 等待列表加载
        onView(withId(R.id.rv_tokens))
            .check(matches(isDisplayed()))

        // 测量滚动性能
        val frameMetrics = mutableListOf<Long>()

        activityRule.scenario.onActivity { activity ->
            val frameMetricsListener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                val frameDuration = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION)
                frameMetrics.add(frameDuration)
            }

            activity.window.addOnFrameMetricsAvailableListener(frameMetricsListener, Handler(Looper.getMainLooper()))

            // 执行滚动操作
            onView(withId(R.id.rv_tokens))
                .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(50))

            Thread.sleep(2000) // 等待滚动完成

            activity.window.removeOnFrameMetricsAvailableListener(frameMetricsListener)
        }

        // 分析帧率
        val averageFrameTime = frameMetrics.average()
        val fps = 1_000_000_000.0 / averageFrameTime // 纳秒转换为 FPS

        // 验证帧率
        assertTrue("列表滚动 FPS 应该大于 55，实际: $fps", fps > 55)

        Log.i("Performance", "列表滚动 FPS: $fps")
    }
}
```

## 🔒 安全验证

### 1. 代码安全扫描

```bash
#!/bin/bash
# security-scan.sh

echo "🔒 执行安全扫描..."

# 检查硬编码密钥
echo "检查硬编码密钥..."
HARDCODED_KEYS=$(grep -r "password\|secret\|key\|token" app/src/main/java --include="*.kt" --include="*.java" | grep -v "// " | wc -l)
if [ $HARDCODED_KEYS -eq 0 ]; then
    echo "✅ 未发现硬编码密钥"
else
    echo "⚠️ 发现可能的硬编码密钥: $HARDCODED_KEYS 处"
fi

# 检查网络安全
echo "检查网络安全配置..."
if grep -q "android:usesCleartextTraffic=\"false\"" app/src/main/AndroidManifest.xml; then
    echo "✅ 禁用明文网络传输"
else
    echo "⚠️ 未禁用明文网络传输"
fi

# 检查权限使用
echo "检查权限配置..."
PERMISSIONS=$(grep "uses-permission" app/src/main/AndroidManifest.xml | wc -l)
echo "应用权限数量: $PERMISSIONS"

# 检查代码混淆
echo "检查代码混淆配置..."
if grep -q "minifyEnabled true" app/build.gradle; then
    echo "✅ 已启用代码混淆"
else
    echo "⚠️ 未启用代码混淆"
fi
```

### 2. 数据安全验证

```kotlin
// DataSecurityTest.kt
class DataSecurityTest {

    @Test
    fun testSensitiveDataEncryption() {
        val securityManager = SecurityManager()
        val sensitiveData = "test_private_key"

        // 加密敏感数据
        val encryptedData = securityManager.encryptSensitiveData(sensitiveData)

        // 验证数据已加密
        assertNotEquals("敏感数据应该被加密", sensitiveData, encryptedData)

        // 验证可以正确解密
        val decryptedData = securityManager.decryptSensitiveData(encryptedData)
        assertEquals("解密后的数据应该与原数据一致", sensitiveData, decryptedData)
    }

    @Test
    fun testNoPlaintextStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedPrefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)

        // 检查 SharedPreferences 中是否有明文敏感数据
        val allPrefs = sharedPrefs.all

        for ((key, value) in allPrefs) {
            if (key.contains("password") || key.contains("private_key") || key.contains("mnemonic")) {
                // 敏感数据应该被加密
                assertTrue("敏感数据 $key 应该被加密存储", value.toString().length > 50)
            }
        }
    }
}
```

## 📱 兼容性验证

### 1. Android 版本兼容性

```kotlin
// CompatibilityTest.kt
@RunWith(Parameterized::class)
class CompatibilityTest(private val apiLevel: Int) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf(24), // Android 7.0
                arrayOf(26), // Android 8.0
                arrayOf(28), // Android 9.0
                arrayOf(29), // Android 10
                arrayOf(30), // Android 11
                arrayOf(31), // Android 12
                arrayOf(33), // Android 13
                arrayOf(34)  // Android 14
            )
        }
    }

    @Test
    fun testApiCompatibility() {
        // 模拟不同 API 级别的行为
        if (Build.VERSION.SDK_INT >= apiLevel) {
            // 测试在该 API 级别下的功能
            testCoreFeatures()
        }
    }

    private fun testCoreFeatures() {
        // 测试核心功能在不同 Android 版本下的兼容性
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 测试生物识别功能
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val biometricManager = BiometricManager.from(context)
            // 验证生物识别功能可用性
        }

        // 测试通知功能
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // 验证通知渠道创建
        }
    }
}
```

### 2. 设备兼容性验证

```bash
#!/bin/bash
# device-compatibility.sh

echo "📱 验证设备兼容性..."

# 获取连接的设备列表
DEVICES=$(adb devices | grep -v "List of devices" | grep "device" | cut -f1)

if [ -z "$DEVICES" ]; then
    echo "❌ 没有连接的设备"
    exit 1
fi

for device in $DEVICES; do
    echo "测试设备: $device"

    # 获取设备信息
    BRAND=$(adb -s $device shell getprop ro.product.brand)
    MODEL=$(adb -s $device shell getprop ro.product.model)
    API_LEVEL=$(adb -s $device shell getprop ro.build.version.sdk)

    echo "设备信息: $BRAND $MODEL (API $API_LEVEL)"

    # 安装应用
    if adb -s $device install -r app/build/outputs/apk/debug/app-debug.apk; then
        echo "✅ 应用安装成功"
    else
        echo "❌ 应用安装失败"
        continue
    fi

    # 启动应用
    adb -s $device shell am start -n com.alphawallet.app/.ui.HomeActivity
    sleep 5

    # 检查应用是否正常运行
    RUNNING=$(adb -s $device shell ps | grep com.alphawallet.app | wc -l)
    if [ $RUNNING -gt 0 ]; then
        echo "✅ 应用正常运行"
    else
        echo "❌ 应用启动失败"
    fi

    # 卸载应用
    adb -s $device uninstall com.alphawallet.app
done
```

## 📈 用户体验验证

### 1. 用户界面测试

```kotlin
// UIExperienceTest.kt
@RunWith(AndroidJUnit4::class)
class UIExperienceTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Test
    fun testUIResponsiveness() {
        // 测试按钮点击响应
        onView(withId(R.id.btn_refresh))
            .perform(click())

        // 验证加载指示器出现
        onView(withId(R.id.progress_bar))
            .check(matches(isDisplayed()))

        // 等待加载完成
        onView(withId(R.id.progress_bar))
            .check(waitForCondition(not(isDisplayed()), 5000))

        // 验证内容更新
        onView(withId(R.id.rv_tokens))
            .check(matches(isDisplayed()))
    }

    @Test
    fun testErrorHandling() {
        // 模拟网络错误
        // 这里需要使用 Mock 或者断网测试

        // 验证错误提示显示
        onView(withText("网络连接失败"))
            .check(matches(isDisplayed()))

        // 验证重试按钮可用
        onView(withId(R.id.btn_retry))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    @Test
    fun testAccessibility() {
        // 验证内容描述
        onView(withId(R.id.btn_send))
            .check(matches(hasContentDescription()))

        // 验证文本大小适配
        onView(withId(R.id.tv_balance))
            .check(matches(isDisplayed()))

        // 验证颜色对比度
        // 这里需要使用 Accessibility Scanner 或类似工具
    }
}
```

### 2. 性能感知测试

```kotlin
// PerformancePerceptionTest.kt
class PerformancePerceptionTest {

    @Test
    fun testLoadingStates() {
        val activityRule = ActivityScenarioRule(HomeActivity::class.java)

        activityRule.scenario.onActivity { activity ->
            val viewModel = ViewModelProvider(activity)[HomeViewModel::class.java]

            // 观察加载状态变化
            val states = mutableListOf<UiState<*>>()

            lifecycleScope.launch {
                viewModel.tokens.collect { state ->
                    states.add(state)
                }
            }

            // 触发数据加载
            viewModel.loadTokens("0x123")

            // 等待状态变化
            Thread.sleep(3000)

            // 验证状态变化序列
            assertTrue("应该有加载状态", states.any { it is UiState.Loading })
            assertTrue("应该有成功或错误状态", states.any { it is UiState.Success || it is UiState.Error })
        }
    }

    @Test
    fun testSmoothAnimations() {
        val activityRule = ActivityScenarioRule(HomeActivity::class.java)

        // 测试页面切换动画
        onView(withId(R.id.bottom_navigation))
            .perform(click())

        // 验证动画流畅性
        // 这里需要使用 GPU 渲染分析工具

        // 测试列表滚动动画
        onView(withId(R.id.rv_tokens))
            .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(20))

        // 验证滚动流畅性
        // 帧率应该保持在 60fps
    }
}
```

## 📊 验证报告生成

### 1. 自动化验证报告

```bash
#!/bin/bash
# generate-verification-report.sh

REPORT_DIR="verification-reports"
REPORT_FILE="$REPORT_DIR/verification-report-$(date +%Y%m%d-%H%M%S).html"

mkdir -p $REPORT_DIR

cat > $REPORT_FILE << EOF
<!DOCTYPE html>
<html>
<head>
    <title>AlphaWallet Kotlin 协程升级验证报告</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background-color: #f0f0f0; padding: 20px; border-radius: 5px; }
        .section { margin: 20px 0; }
        .pass { color: green; font-weight: bold; }
        .fail { color: red; font-weight: bold; }
        .warning { color: orange; font-weight: bold; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>AlphaWallet Kotlin 协程升级验证报告</h1>
        <p>生成时间: $(date)</p>
        <p>版本: $(git rev-parse --short HEAD)</p>
    </div>
EOF

# 执行各项验证并记录结果
echo "    <div class='section'>" >> $REPORT_FILE
echo "        <h2>代码质量验证</h2>" >> $REPORT_FILE
echo "        <table>" >> $REPORT_FILE
echo "            <tr><th>检查项</th><th>结果</th><th>详情</th></tr>" >> $REPORT_FILE

# Kotlin 转换验证
if ./scripts/verify-kotlin-conversion.sh > /dev/null 2>&1; then
    echo "            <tr><td>Kotlin 转换</td><td class='pass'>通过</td><td>所有 Java 文件已转换</td></tr>" >> $REPORT_FILE
else
    echo "            <tr><td>Kotlin 转换</td><td class='fail'>失败</td><td>仍有 Java 文件未转换</td></tr>" >> $REPORT_FILE
fi

# 代码规范验证
if ./gradlew detekt > /dev/null 2>&1; then
    echo "            <tr><td>代码规范</td><td class='pass'>通过</td><td>符合 Kotlin 代码规范</td></tr>" >> $REPORT_FILE
else
    echo "            <tr><td>代码规范</td><td class='fail'>失败</td><td>存在代码规范问题</td></tr>" >> $REPORT_FILE
fi

# 编译验证
if ./gradlew assembleDebug > /dev/null 2>&1; then
    echo "            <tr><td>编译</td><td class='pass'>通过</td><td>编译成功</td></tr>" >> $REPORT_FILE
else
    echo "            <tr><td>编译</td><td class='fail'>失败</td><td>编译失败</td></tr>" >> $REPORT_FILE
fi

# 单元测试验证
if ./gradlew testDebugUnitTest > /dev/null 2>&1; then
    echo "            <tr><td>单元测试</td><td class='pass'>通过</td><td>所有测试通过</td></tr>" >> $REPORT_FILE
else
    echo "            <tr><td>单元测试</td><td class='fail'>失败</td><td>部分测试失败</td></tr>" >> $REPORT_FILE
fi

echo "        </table>" >> $REPORT_FILE
echo "    </div>" >> $REPORT_FILE

# 性能验证结果
echo "    <div class='section'>" >> $REPORT_FILE
echo "        <h2>性能验证</h2>" >> $REPORT_FILE
echo "        <table>" >> $REPORT_FILE
echo "            <tr><th>指标</th><th>目标值</th><th>实际值</th><th>结果</th></tr>" >> $REPORT_FILE

# 这里添加性能测试结果
# 启动时间、内存使用、网络响应等

echo "        </table>" >> $REPORT_FILE
echo "    </div>" >> $REPORT_FILE

echo "</body></html>" >> $REPORT_FILE

echo "✅ 验证报告已生成: $REPORT_FILE"
open $REPORT_FILE # macOS
```

### 2. 持续集成验证

```yaml
# .github/workflows/verification.yml
name: Kotlin 协程升级验证

on:
    push:
        branches: [main, develop]
    pull_request:
        branches: [main]

jobs:
    verification:
        runs-on: ubuntu-latest

        steps:
            - uses: actions/checkout@v3

            - name: 设置 JDK 17
              uses: actions/setup-java@v3
              with:
                  java-version: "17"
                  distribution: "temurin"

            - name: 缓存 Gradle 依赖
              uses: actions/cache@v3
              with:
                  path: |
                      ~/.gradle/caches
                      ~/.gradle/wrapper
                  key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
                  restore-keys: |
                      ${{ runner.os }}-gradle-

            - name: 代码质量检查
              run: |
                  ./gradlew detekt
                  ./gradlew ktlintCheck

            - name: 编译验证
              run: |
                  ./gradlew assembleDebug
                  ./gradlew assembleRelease

            - name: 单元测试
              run: ./gradlew testDebugUnitTest

            - name: 生成测试报告
              run: ./gradlew jacocoTestReport

            - name: 上传测试报告
              uses: actions/upload-artifact@v3
              with:
                  name: test-reports
                  path: |
                      app/build/reports/
                      app/build/test-results/

            - name: 安全扫描
              run: ./scripts/security-scan.sh

            - name: 性能基准测试
              run: ./gradlew connectedAndroidTest

            - name: 生成验证报告
              run: ./scripts/generate-verification-report.sh

            - name: 上传验证报告
              uses: actions/upload-artifact@v3
              with:
                  name: verification-report
                  path: verification-reports/
```

## 🎯 验证总结

### 验证成功标准

**必须满足的条件**:

- [ ] 所有 Java 文件已转换为 Kotlin
- [ ] 所有 RxJava 调用已替换为协程
- [ ] 编译成功（Debug 和 Release）
- [ ] 单元测试覆盖率 ≥ 80%
- [ ] 所有功能测试通过
- [ ] 启动时间 < 2.5 秒
- [ ] 内存使用 < 120MB
- [ ] 无安全漏洞
- [ ] 支持 Android 7.0-14

**推荐满足的条件**:

- [ ] 代码规范检查通过
- [ ] 性能测试通过
- [ ] 用户体验测试通过
- [ ] 兼容性测试通过
- [ ] 文档完整

### 验证流程

1. **开发阶段**: 每次提交都运行基础验证
2. **测试阶段**: 运行完整的验证套件
3. **发布前**: 运行所有验证项目
4. **发布后**: 监控生产环境指标

### 问题处理

**验证失败时的处理流程**:

1. 分析失败原因
2. 修复问题
3. 重新运行验证
4. 更新文档
5. 通知相关人员

---

**文档版本**: v1.0  
**最后更新**: 2025年1月  
**维护人员**: 开发团队
