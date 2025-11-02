# AlphaWallet Kotlin 协程升级要求

## 📋 总体要求

### 代码质量要求

#### 1. Kotlin 代码规范

- **命名规范**: 遵循 Kotlin 官方命名规范
- **代码风格**: 使用 ktlint 进行代码格式化
- **文档注释**: 所有公共 API 必须有 KDoc 注释
- **类型安全**: 充分利用 Kotlin 的类型系统
- **空安全**: 正确处理可空类型

```kotlin
// ✅ 正确示例
/**
 * 代币仓库，负责管理代币相关数据
 * @param apiService 网络服务
 * @param tokenDao 本地数据库访问对象
 */
class TokenRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenDao: TokenDao
) {
    /**
     * 获取指定地址的代币列表
     * @param address 钱包地址
     * @return 代币列表的结果
     */
    suspend fun getTokens(address: String): Result<List<Token>> {
        return withContext(Dispatchers.IO) {
            try {
                val tokens = apiService.getTokens(address)
                Result.success(tokens)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

// ❌ 错误示例
class tokenrepository {
    fun gettokens(addr: String?): List<Token>? {
        // 缺少文档，命名不规范，空安全处理不当
        return null
    }
}
```

#### 2. 协程使用规范

- **作用域管理**: 正确使用协程作用域
- **异常处理**: 统一的异常处理机制
- **线程调度**: 合理使用 Dispatchers
- **取消支持**: 支持协程取消
- **性能优化**: 避免不必要的协程创建

```kotlin
// ✅ 正确的协程使用
class HomeViewModel @Inject constructor(
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val _tokens = MutableStateFlow<UiState<List<Token>>>(UiState.Loading)
    val tokens: StateFlow<UiState<List<Token>>> = _tokens.asStateFlow()

    fun loadTokens(address: String) {
        viewModelScope.launch {
            _tokens.value = UiState.Loading

            tokenRepository.getTokens(address)
                .onSuccess { tokens -> _tokens.value = UiState.Success(tokens) }
                .onFailure { error -> _tokens.value = UiState.Error(error) }
        }
    }
}

// ❌ 错误的协程使用
class BadViewModel {
    fun loadTokens() {
        GlobalScope.launch { // 不应该使用 GlobalScope
            // 没有异常处理
            val tokens = apiService.getTokens()
            // 直接在后台线程更新 UI
            updateUI(tokens)
        }
    }
}
```

### 架构要求

#### 1. MVVM 架构

- **分层清晰**: View - ViewModel - Repository - DataSource
- **单一职责**: 每个类只负责一个职责
- **依赖注入**: 使用 Hilt 进行依赖注入
- **数据流**: 使用 StateFlow/SharedFlow 管理数据流

#### 2. Repository 模式

- **数据抽象**: Repository 作为数据访问的抽象层
- **缓存策略**: 实现合理的缓存策略
- **错误处理**: 统一的错误处理
- **数据同步**: 网络和本地数据的同步

```kotlin
// ✅ 标准 Repository 实现
interface TokenRepository {
    suspend fun getTokens(address: String): Result<List<Token>>
    fun getTokensFlow(address: String): Flow<List<Token>>
    suspend fun refreshTokens(address: String): Result<Unit>
}

@Singleton
class TokenRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val tokenDao: TokenDao,
    private val cacheManager: CacheManager
) : TokenRepository {

    override suspend fun getTokens(address: String): Result<List<Token>> {
        return withContext(Dispatchers.IO) {
            try {
                // 先检查缓存
                val cachedTokens = cacheManager.getTokens(address)
                if (cachedTokens.isNotEmpty() && !cacheManager.isExpired(address)) {
                    return@withContext Result.success(cachedTokens)
                }

                // 网络请求
                val tokens = apiService.getTokens(address)

                // 更新缓存和数据库
                cacheManager.saveTokens(address, tokens)
                tokenDao.insertTokens(tokens)

                Result.success(tokens)
            } catch (e: Exception) {
                // 网络失败时返回缓存数据
                val cachedTokens = tokenDao.getTokens(address)
                if (cachedTokens.isNotEmpty()) {
                    Result.success(cachedTokens)
                } else {
                    Result.failure(e)
                }
            }
        }
    }
}
```

## 🔧 技术要求

### 依赖管理

#### 1. 必需依赖

```gradle
// Kotlin 协程
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3"
implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"

// 架构组件
implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"
implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.7.0"
implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0"

// 依赖注入
implementation "com.google.dagger:hilt-android:2.48"
kapt "com.google.dagger:hilt-compiler:2.48"

// 网络
implementation "com.squareup.retrofit2:retrofit:2.9.0"
implementation "com.squareup.retrofit2:converter-gson:2.9.0"
implementation "com.squareup.okhttp3:logging-interceptor:4.11.0"

// 数据库
implementation "androidx.room:room-runtime:2.6.1"
implementation "androidx.room:room-ktx:2.6.1"
kapt "androidx.room:room-compiler:2.6.1"
```

#### 2. 禁用依赖

```gradle
// 移除 RxJava 相关依赖
// implementation 'io.reactivex.rxjava3:rxjava:3.1.5'
// implementation 'io.reactivex.rxjava3:rxandroid:3.0.2'
// implementation 'com.squareup.retrofit2:adapter-rxjava3:2.9.0'
```

### 代码转换要求

#### 1. Java 到 Kotlin 转换

**实体类转换**:

```kotlin
// 转换前 (Java)
public class Token {
    private String address;
    private String name;
    private String symbol;
    private BigDecimal balance;

    // 构造函数、getter、setter...

    @Override
    public boolean equals(Object obj) {
        // equals 实现
    }

    @Override
    public int hashCode() {
        // hashCode 实现
    }
}

// 转换后 (Kotlin)
@Parcelize
data class Token(
    val address: String,
    val name: String,
    val symbol: String,
    val balance: BigDecimal = BigDecimal.ZERO
) : Parcelable {

    fun isValid(): Boolean = address.isNotBlank() && name.isNotBlank()

    fun formatBalance(): String = balance.setScale(4, RoundingMode.HALF_UP).toString()
}
```

**ViewModel 转换**:

```kotlin
// 转换前 (Java + RxJava)
public class HomeViewModel extends ViewModel {
    private MutableLiveData<List<Token>> tokens = new MutableLiveData<>();
    private CompositeDisposable disposables = new CompositeDisposable();

    public void loadTokens(String address) {
        disposables.add(
            tokenRepository.getTokens(address)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    tokens::setValue,
                    this::handleError
                )
        );
    }

    @Override
    protected void onCleared() {
        disposables.clear();
        super.onCleared();
    }
}

// 转换后 (Kotlin + 协程)
class HomeViewModel @Inject constructor(
    private val tokenRepository: TokenRepository
) : ViewModel() {

    private val _tokens = MutableStateFlow<UiState<List<Token>>>(UiState.Loading)
    val tokens: StateFlow<UiState<List<Token>>> = _tokens.asStateFlow()

    fun loadTokens(address: String) {
        viewModelScope.launch {
            _tokens.value = UiState.Loading

            tokenRepository.getTokens(address)
                .onSuccess { tokens -> _tokens.value = UiState.Success(tokens) }
                .onFailure { error -> _tokens.value = UiState.Error(error) }
        }
    }

    // 协程会自动取消，无需手动清理
}
```

#### 2. RxJava 到协程转换

**网络请求转换**:

```kotlin
// 转换前 (RxJava)
interface ApiService {
    @GET("tokens")
    Single<List<Token>> getTokens(@Query("address") String address);
}

class TokenRepository {
    public Single<List<Token>> getTokens(String address) {
        return apiService.getTokens(address)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread());
    }
}

// 转换后 (协程)
interface ApiService {
    @GET("tokens")
    suspend fun getTokens(@Query("address") address: String): List<Token>
}

class TokenRepository {
    suspend fun getTokens(address: String): Result<List<Token>> {
        return withContext(Dispatchers.IO) {
            try {
                val tokens = apiService.getTokens(address)
                Result.success(tokens)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
```

**数据流转换**:

```kotlin
// 转换前 (RxJava)
class TokenRepository {
    private PublishSubject<List<Token>> tokensSubject = PublishSubject.create();

    public Observable<List<Token>> observeTokens() {
        return tokensSubject.distinctUntilChanged();
    }

    public void updateTokens(List<Token> tokens) {
        tokensSubject.onNext(tokens);
    }
}

// 转换后 (协程)
class TokenRepository {
    private val _tokensFlow = MutableSharedFlow<List<Token>>(replay = 1)
    val tokensFlow: SharedFlow<List<Token>> = _tokensFlow.asSharedFlow()

    suspend fun updateTokens(tokens: List<Token>) {
        _tokensFlow.emit(tokens)
    }
}
```

### 性能要求

#### 1. 启动性能

- **冷启动时间**: < 2.5 秒
- **热启动时间**: < 1.0 秒
- **首屏渲染**: < 1.5 秒

#### 2. 运行时性能

- **内存使用**: < 120MB (正常使用)
- **CPU 使用**: < 15% (空闲状态)
- **网络响应**: < 600ms (平均)
- **列表滚动**: 60fps 稳定

#### 3. 电池优化

- **后台活动**: 最小化后台任务
- **网络请求**: 合并和缓存策略
- **定位服务**: 按需使用
- **传感器**: 及时释放资源

### 测试要求

#### 1. 单元测试

- **覆盖率**: 最低 80%
- **核心逻辑**: 100% 覆盖
- **边界条件**: 充分测试
- **异常情况**: 完整覆盖

```kotlin
// 单元测试示例
@ExperimentalCoroutinesApi
class TokenRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mockApiService = mockk<ApiService>()
    private val mockTokenDao = mockk<TokenDao>()
    private val repository = TokenRepositoryImpl(mockApiService, mockTokenDao)

    @Test
    fun `getTokens should return success when api call succeeds`() = runTest {
        // Given
        val address = "0x123"
        val expectedTokens = listOf(Token("0x456", "Test Token", "TEST"))
        coEvery { mockApiService.getTokens(address) } returns expectedTokens
        coEvery { mockTokenDao.insertTokens(any()) } just Runs

        // When
        val result = repository.getTokens(address)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(expectedTokens, result.getOrNull())
        coVerify { mockTokenDao.insertTokens(expectedTokens) }
    }

    @Test
    fun `getTokens should return cached data when api call fails`() = runTest {
        // Given
        val address = "0x123"
        val cachedTokens = listOf(Token("0x789", "Cached Token", "CACHE"))
        coEvery { mockApiService.getTokens(address) } throws IOException("Network error")
        coEvery { mockTokenDao.getTokens(address) } returns cachedTokens

        // When
        val result = repository.getTokens(address)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(cachedTokens, result.getOrNull())
    }
}
```

#### 2. 集成测试

- **API 集成**: 真实网络环境测试
- **数据库集成**: 数据持久化测试
- **UI 集成**: 端到端用户流程测试

#### 3. 性能测试

- **压力测试**: 高并发场景
- **内存测试**: 内存泄漏检测
- **电池测试**: 电量消耗测试

## 📱 兼容性要求

### Android 版本

- **最低支持**: Android 7.0 (API 24)
- **目标版本**: Android 14 (API 34)
- **测试覆盖**: API 24-34 全版本测试

### 设备兼容性

- **内存**: 最低 2GB RAM
- **存储**: 最低 100MB 可用空间
- **网络**: 支持 2G/3G/4G/5G/WiFi
- **屏幕**: 支持 4.0" - 7.0" 屏幕

### 功能兼容性

- **生物识别**: 指纹、面部识别
- **NFC**: 近场通信支持
- **相机**: 二维码扫描
- **传感器**: 加速度计、陀螺仪

## 🔒 安全要求

### 代码安全

- **密钥管理**: 不在代码中硬编码密钥
- **数据加密**: 敏感数据必须加密存储
- **网络安全**: 使用 HTTPS 和证书固定
- **代码混淆**: 发布版本必须混淆

```kotlin
// ✅ 安全的密钥管理
class SecurityManager @Inject constructor(
    private val keyStore: AndroidKeyStore
) {

    fun encryptSensitiveData(data: String): String {
        val key = keyStore.getOrCreateKey("sensitive_data_key")
        return AESUtil.encrypt(data, key)
    }

    fun decryptSensitiveData(encryptedData: String): String {
        val key = keyStore.getKey("sensitive_data_key")
        return AESUtil.decrypt(encryptedData, key)
    }
}

// ❌ 不安全的做法
class BadSecurityManager {
    private val SECRET_KEY = "hardcoded_secret_key" // 不要这样做

    fun savePassword(password: String) {
        // 明文存储密码 - 不安全
        sharedPreferences.edit().putString("password", password).apply()
    }
}
```

### 数据保护

- **用户隐私**: 遵循 GDPR 和相关法规
- **数据最小化**: 只收集必要的数据
- **数据删除**: 提供数据删除功能
- **权限管理**: 最小权限原则

## 📊 监控要求

### 性能监控

```kotlin
// 性能监控实现
object PerformanceMonitor {

    suspend fun <T> trackOperation(
        operationName: String,
        operation: suspend () -> T
    ): T {
        val startTime = System.currentTimeMillis()
        val startMemory = getUsedMemory()

        return try {
            operation()
        } finally {
            val endTime = System.currentTimeMillis()
            val endMemory = getUsedMemory()

            val duration = endTime - startTime
            val memoryDelta = endMemory - startMemory

            // 记录性能数据
            Analytics.trackPerformance(
                operation = operationName,
                duration = duration,
                memoryUsage = memoryDelta
            )

            // 性能警告
            if (duration > 1000) {
                Timber.w("Slow operation: $operationName took ${duration}ms")
            }

            if (memoryDelta > 10 * 1024 * 1024) { // 10MB
                Timber.w("High memory usage: $operationName used ${memoryDelta / 1024 / 1024}MB")
            }
        }
    }

    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
```

### 错误监控

```kotlin
// 错误监控和上报
object ErrorReporter {

    fun reportError(
        error: Throwable,
        context: String,
        additionalData: Map<String, Any> = emptyMap()
    ) {
        // 记录到本地日志
        Timber.e(error, "Error in $context")

        // 上报到崩溃分析服务
        Crashlytics.recordException(error)

        // 添加上下文信息
        Crashlytics.setCustomKeys {
            key("context", context)
            additionalData.forEach { (key, value) ->
                key(key, value.toString())
            }
        }

        // 非致命错误统计
        Analytics.trackError(
            errorType = error.javaClass.simpleName,
            context = context,
            message = error.message ?: "Unknown error"
        )
    }
}
```

## 📝 文档要求

### 代码文档

- **KDoc 注释**: 所有公共 API
- **内联注释**: 复杂逻辑说明
- **README**: 模块使用说明
- **CHANGELOG**: 版本变更记录

### 架构文档

- **系统设计**: 整体架构图
- **数据流**: 数据流向图
- **API 文档**: 接口说明
- **部署指南**: 部署和配置说明

### 用户文档

- **功能说明**: 新功能使用指南
- **迁移指南**: 版本升级说明
- **故障排除**: 常见问题解决
- **性能优化**: 使用建议

## ✅ 验收标准

### 功能验收

- [ ] 所有现有功能正常工作
- [ ] 新功能按需求实现
- [ ] 用户界面响应流畅
- [ ] 数据同步正确
- [ ] 离线功能正常

### 性能验收

- [ ] 启动时间 < 2.5s
- [ ] 内存使用 < 120MB
- [ ] 网络响应 < 600ms
- [ ] 列表滚动 60fps
- [ ] 电池消耗合理

### 质量验收

- [ ] 单元测试覆盖率 > 80%
- [ ] 集成测试通过率 > 95%
- [ ] 代码审查通过
- [ ] 安全扫描通过
- [ ] 性能测试通过

### 兼容性验收

- [ ] Android 7.0-14 兼容
- [ ] 主流设备兼容
- [ ] 网络环境兼容
- [ ] 功能特性兼容

---

**文档版本**: v1.0  
**最后更新**: 2025年1月  
**审核状态**: 待审核
