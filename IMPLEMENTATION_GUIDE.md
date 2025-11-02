# Kotlin 协程和架构组件实施指南

## 🎯 概述

本指南将帮助你逐步将 AlphaWallet 项目改造为使用现代 Android 开发技术：

- **Kotlin 协程** 替代 RxJava
- **Jetpack Compose** 替代传统 View
- **MVVM 架构** 替代现有架构
- **现代架构组件** (ViewModel, LiveData, Room, Hilt)

## 🚀 快速开始

### 第一步：运行协程迁移脚本

```bash
# 运行第一阶段协程迁移
./scripts/start-coroutines-migration.sh
```

这个脚本会自动：

1. 添加协程依赖
2. 创建协程工具类
3. 创建基础 ViewModel
4. 创建网络服务接口
5. 创建示例 Repository
6. 进行编译测试

## 📅 详细实施计划

### 第一阶段：协程集成 (2-3 周)

#### 第1周：基础设置

**目标**: 建立协程基础设施

**任务**:

1. ✅ 添加协程依赖
2. ✅ 创建协程工具类
3. ✅ 创建基础 ViewModel
4. 🔄 改造一个简单的网络调用

**具体步骤**:

1. **更新 build.gradle**

```gradle
dependencies {
    // Kotlin 协程
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-rx3:1.7.3"

    // 架构组件
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0"
    implementation "androidx.lifecycle:lifecycle-livedata-ktx:2.7.0"
    implementation "androidx.lifecycle:lifecycle-runtime-ktx:2.7.0"
}
```

2. **使用协程工具类**

```kotlin
// 在现有代码中使用协程
CoroutineUtils.launchSafely(
    dispatcher = CoroutineUtils.ioDispatcher,
    onError = { error ->
        Timber.e("Network error: $error")
    }
) {
    // 执行网络调用
    val result = apiService.getTokens(address)
    withContext(CoroutineUtils.mainDispatcher) {
        // 更新 UI
        updateUI(result)
    }
}
```

#### 第2周：网络层改造

**目标**: 将所有网络调用改造为使用协程

**任务**:

1. 改造 TokenService
2. 改造 WalletService
3. 改造 TransactionService
4. 添加错误处理

**示例改造**:

```kotlin
// 改造前 (RxJava)
apiService.getTokens(address)
    .subscribeOn(Schedulers.io())
    .observeOn(AndroidSchedulers.mainThread())
    .subscribe(
        { tokens -> updateTokens(tokens) },
        { error -> handleError(error) }
    )

// 改造后 (协程)
launchSafely(
    dispatcher = CoroutineUtils.ioDispatcher,
    onError = { error -> handleError(error) }
) {
    val tokens = apiService.getTokens(address)
    withContext(CoroutineUtils.mainDispatcher) {
        updateTokens(tokens)
    }
}
```

#### 第3周：数据库层改造

**目标**: 添加 Room 数据库支持

**任务**:

1. 添加 Room 依赖
2. 创建数据库实体
3. 创建 DAO 接口
4. 创建 Repository 层

**添加 Room 依赖**:

```gradle
dependencies {
    implementation "androidx.room:room-runtime:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"
}
```

**创建数据库实体**:

```kotlin
@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val address: String,
    val name: String,
    val type: String,
    val isBackedUp: Boolean,
    val lastBackupTime: Long
)
```

### 第二阶段：架构组件集成 (3-4 周)

#### 第4周：ViewModel 改造

**目标**: 将所有 ViewModel 改造为使用 StateFlow

**任务**:

1. 改造 HomeViewModel
2. 改造 WalletViewModel
3. 改造 TokenViewModel
4. 添加状态管理

**示例改造**:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val tokenRepository: TokenRepository
) : BaseViewModel() {

    private val _wallets = MutableStateFlow<List<WalletEntity>>(emptyList())
    val wallets: StateFlow<List<WalletEntity>> = _wallets.asStateFlow()

    private val _currentWallet = MutableStateFlow<WalletEntity?>(null)
    val currentWallet: StateFlow<WalletEntity?> = _currentWallet.asStateFlow()

    init {
        loadWallets()
    }

    private fun loadWallets() {
        launchSafely {
            walletRepository.getAllWallets()
                .collect { wallets ->
                    _wallets.value = wallets
                    if (wallets.isNotEmpty() && _currentWallet.value == null) {
                        _currentWallet.value = wallets.first()
                    }
                }
        }
    }
}
```

#### 第5周：Repository 层改造

**目标**: 创建完整的 Repository 层

**任务**:

1. 创建 WalletRepository
2. 创建 TokenRepository
3. 创建 TransactionRepository
4. 添加缓存策略

**示例 Repository**:

```kotlin
class WalletRepository @Inject constructor(
    private val walletDao: WalletDao,
    private val networkService: NetworkService
) {

    fun getAllWallets(): Flow<List<WalletEntity>> {
        return walletDao.getAllWallets()
    }

    suspend fun refreshWallets() {
        launchSafely {
            val wallets = networkService.getWallets()
            walletDao.insertAll(wallets)
        }
    }

    suspend fun backupWallet(address: String) {
        launchSafely {
            networkService.backupWallet(address)
            walletDao.updateBackupStatus(address, true, System.currentTimeMillis())
        }
    }
}
```

#### 第6周：Hilt 依赖注入

**目标**: 集成 Hilt 依赖注入

**任务**:

1. 添加 Hilt 依赖
2. 创建 Hilt 模块
3. 配置依赖注入
4. 测试依赖注入

**添加 Hilt 依赖**:

```gradle
dependencies {
    implementation "com.google.dagger:hilt-android:2.48"
    kapt "com.google.dagger:hilt-compiler:2.48"
}
```

**创建 Hilt 模块**:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "alphawallet_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideNetworkService(): NetworkService {
        return NetworkServiceImpl()
    }
}
```

### 第三阶段：Jetpack Compose 集成 (4-5 周)

#### 第7周：Compose 基础设置

**目标**: 建立 Compose 开发环境

**任务**:

1. 添加 Compose 依赖
2. 创建 Compose 主题
3. 设置导航
4. 创建基础组件

**添加 Compose 依赖**:

```gradle
android {
    buildFeatures {
        compose true
    }
    composeOptions {
        kotlinCompilerExtensionVersion "1.5.3"
    }
}

dependencies {
    implementation "androidx.compose.ui:ui:1.5.4"
    implementation "androidx.compose.material3:material3:1.1.2"
    implementation "androidx.activity:activity-compose:1.8.2"
    implementation "androidx.navigation:navigation-compose:2.7.5"
}
```

#### 第8-9周：创建 Compose UI 组件

**目标**: 创建主要的 UI 组件

**任务**:

1. 创建钱包列表组件
2. 创建代币详情组件
3. 创建设置组件
4. 创建交易组件

**示例组件**:

```kotlin
@Composable
fun WalletListScreen(
    viewModel: HomeViewModel = viewModel(),
    onWalletClick: (String) -> Unit
) {
    val wallets by viewModel.wallets.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        error?.let { errorMessage ->
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        LazyColumn {
            items(wallets) { wallet ->
                WalletItem(
                    wallet = wallet,
                    onClick = { onWalletClick(wallet.address) }
                )
            }
        }
    }
}
```

#### 第10周：导航集成

**目标**: 实现 Compose 导航

**任务**:

1. 创建导航图
2. 实现页面跳转
3. 处理深层链接
4. 添加动画效果

**示例导航**:

```kotlin
@Composable
fun AppNavigation(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = "wallet_list"
    ) {
        composable("wallet_list") {
            WalletListScreen(
                onWalletClick = { address ->
                    navController.navigate("wallet_detail/$address")
                }
            )
        }

        composable("wallet_detail/{address}") { backStackEntry ->
            val address = backStackEntry.arguments?.getString("address")
            address?.let { addr ->
                WalletDetailScreen(
                    address = addr,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
```

### 第四阶段：测试和优化 (2-3 周)

#### 第11周：单元测试

**目标**: 为所有组件添加单元测试

**任务**:

1. 测试 ViewModel
2. 测试 Repository
3. 测试网络层
4. 测试工具类

**示例测试**:

```kotlin
@ExperimentalCoroutinesApi
class HomeViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    @Test
    fun `loadWallets should update wallets state`() = runTest {
        val mockWalletRepository = mockk<WalletRepository>()
        val mockTokenRepository = mockk<TokenRepository>()

        coEvery { mockWalletRepository.getAllWallets() } returns flowOf(
            listOf(WalletEntity("0x123", "Test Wallet", "KEYSTORE", false, 0))
        )

        val viewModel = HomeViewModel(mockWalletRepository, mockTokenRepository)

        assert(viewModel.wallets.value.size == 1)
        assert(viewModel.wallets.value.first().name == "Test Wallet")
    }
}
```

#### 第12周：集成测试

**目标**: 添加集成测试

**任务**:

1. 测试数据库操作
2. 测试网络调用
3. 测试 UI 交互
4. 测试端到端流程

**示例集成测试**:

```kotlin
@RunWith(AndroidJUnit4::class)
class WalletDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var walletDao: WalletDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        walletDao = database.walletDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun insertAndGetWallet() = runTest {
        val wallet = WalletEntity("0x123", "Test Wallet", "KEYSTORE", false, 0)
        walletDao.insertWallet(wallet)
        val result = walletDao.getWalletByAddress("0x123")

        assert(result != null)
        assert(result!!.name == "Test Wallet")
    }
}
```

### 第五阶段：性能优化和监控 (1-2 周)

#### 第13周：性能优化

**目标**: 优化应用性能

**任务**:

1. 添加性能监控
2. 优化内存使用
3. 优化启动时间
4. 优化网络请求

**性能监控工具**:

```kotlin
object PerformanceMonitor {

    suspend fun <T> measureTime(operationName: String, block: suspend () -> T): T {
        val startTime = System.currentTimeMillis()
        return try {
            block()
        } finally {
            val endTime = System.currentTimeMillis()
            Timber.d("Performance: $operationName took ${endTime - startTime}ms")
        }
    }

    fun <T> Flow<T>.monitorPerformance(operationName: String): Flow<T> {
        return map { result ->
            Timber.d("Performance: $operationName completed")
            result
        }
    }
}
```

## 🛠️ 工具和脚本

### 自动化脚本

1. **协程迁移脚本**

```bash
./scripts/start-coroutines-migration.sh
```

2. **Compose 迁移脚本**

```bash
./scripts/start-compose-migration.sh
```

3. **测试脚本**

```bash
./scripts/run-tests.sh
```

### 开发工具

1. **Android Studio** - 主要开发环境
2. **Kotlin Plugin** - Kotlin 语言支持
3. **Compose Preview** - Compose UI 预览
4. **Layout Inspector** - UI 调试工具

## 📊 进度跟踪

### 检查清单

#### 第一阶段：协程集成

- [ ] 添加协程依赖
- [ ] 创建协程工具类
- [ ] 改造网络调用
- [ ] 添加错误处理
- [ ] 创建基础 ViewModel

#### 第二阶段：架构组件

- [ ] 添加 Room 数据库
- [ ] 创建 Repository 层
- [ ] 集成 Hilt 依赖注入
- [ ] 改造 ViewModel
- [ ] 添加状态管理

#### 第三阶段：Compose UI

- [ ] 添加 Compose 依赖
- [ ] 创建基础组件
- [ ] 实现导航
- [ ] 添加主题
- [ ] 优化 UI

#### 第四阶段：测试

- [ ] 添加单元测试
- [ ] 添加集成测试
- [ ] 添加 UI 测试
- [ ] 性能测试

#### 第五阶段：优化

- [ ] 性能监控
- [ ] 内存优化
- [ ] 启动优化
- [ ] 网络优化

## 🚨 风险控制

### 回滚策略

1. **功能开关**: 使用 Feature Flags 控制新旧代码
2. **分支管理**: 保持主分支稳定
3. **分阶段发布**: 逐步发布新功能
4. **监控告警**: 实时监控应用状态

### 测试策略

1. **单元测试**: 覆盖所有核心逻辑
2. **集成测试**: 测试组件间交互
3. **UI 测试**: 测试用户界面
4. **性能测试**: 监控性能指标

## 📚 学习资源

### 官方文档

- [Kotlin 协程官方文档](https://kotlinlang.org/docs/coroutines-overview.html)
- [Jetpack Compose 官方文档](https://developer.android.com/jetpack/compose)
- [Android 架构组件](https://developer.android.com/topic/libraries/architecture)
- [Hilt 依赖注入](https://dagger.dev/hilt/)

### 最佳实践

- [Android 开发最佳实践](https://developer.android.com/topic/architecture)
- [Kotlin 编码规范](https://kotlinlang.org/docs/coding-conventions.html)
- [Compose 最佳实践](https://developer.android.com/jetpack/compose/performance)

## 🎯 成功指标

### 技术指标

- 启动时间减少 30%
- 内存使用减少 20%
- 代码量减少 40%
- 测试覆盖率 > 80%

### 业务指标

- 用户满意度提升
- 崩溃率降低
- 新功能开发速度提升
- 维护成本降低

## 📞 支持

如果在实施过程中遇到问题：

1. 查看相关文档
2. 运行测试脚本
3. 检查错误日志
4. 参考示例代码

记住：这是一个渐进式的改造过程，可以分阶段进行，确保每个阶段都经过充分测试后再进入下一阶段。
