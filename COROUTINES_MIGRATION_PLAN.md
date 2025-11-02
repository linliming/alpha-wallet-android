# Kotlin 协程和架构组件改造计划

## 🎯 总体目标

将 AlphaWallet 项目逐步改造为使用：

- **Kotlin 协程** 替代 RxJava
- **Jetpack Compose** 替代传统 View
- **MVVM 架构** 替代现有架构
- **现代架构组件** (ViewModel, LiveData, Room, Hilt)

## 📅 第一阶段：协程集成 (2-3 周)

### 1.1 准备工作 (第1周)

#### 添加协程依赖

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

#### 创建协程工具类

```kotlin
// app/src/main/java/com/alphawallet/app/util/CoroutineUtils.kt
object CoroutineUtils {
    val mainDispatcher = Dispatchers.Main
    val ioDispatcher = Dispatchers.IO
    val defaultScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
}
```

### 1.2 网络层改造 (第2周)

#### 创建协程网络服务

```kotlin
// app/src/main/java/com/alphawallet/app/network/NetworkService.kt
interface NetworkService {
    suspend fun <T> executeCall(call: suspend () -> Response<T>): Result<T>
    fun <T> executeCallAsFlow(call: suspend () -> Response<T>): Flow<Result<T>>
}
```

### 1.3 数据库层改造 (第3周)

#### 创建 Room 数据库

```kotlin
@Database(
    entities = [WalletEntity::class, TokenEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun tokenDao(): TokenDao
}
```

## 📅 第二阶段：架构组件集成 (3-4 周)

### 2.1 ViewModel 改造 (第4周)

#### 改造 HomeViewModel

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val tokenRepository: TokenRepository
) : BaseViewModel() {

    private val _wallets = MutableStateFlow<List<WalletEntity>>(emptyList())
    val wallets: StateFlow<List<WalletEntity>> = _wallets.asStateFlow()

    init {
        loadWallets()
    }

    private fun loadWallets() {
        launchSafely {
            walletRepository.getAllWallets()
                .collect { wallets ->
                    _wallets.value = wallets
                }
        }
    }
}
```

### 2.2 Repository 层改造 (第5周)

#### 创建 Repository 接口

```kotlin
class WalletRepository @Inject constructor(
    private val walletDao: WalletDao
) {
    fun getAllWallets(): Flow<List<WalletEntity>> {
        return walletDao.getAllWallets()
    }

    suspend fun insertWallet(wallet: WalletEntity) {
        walletDao.insertWallet(wallet)
    }
}
```

### 2.3 Hilt 依赖注入 (第6周)

#### 创建 Hilt 模块

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
}
```

## 📅 第三阶段：Jetpack Compose 集成 (4-5 周)

### 3.1 准备工作 (第7周)

#### 添加 Compose 依赖

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

### 3.2 创建 Compose UI 组件 (第8-9周)

#### 钱包列表组件

```kotlin
@Composable
fun WalletListScreen(
    viewModel: HomeViewModel = viewModel(),
    onWalletClick: (String) -> Unit
) {
    val wallets by viewModel.wallets.collectAsState()

    LazyColumn {
        items(wallets) { wallet ->
            WalletItem(
                wallet = wallet,
                onClick = { onWalletClick(wallet.address) }
            )
        }
    }
}
```

### 3.3 导航集成 (第10周)

#### 创建导航组件

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
    }
}
```

## 📅 第四阶段：测试和优化 (2-3 周)

### 4.1 单元测试 (第11周)

#### ViewModel 测试

```kotlin
@ExperimentalCoroutinesApi
class HomeViewModelTest {

    @Test
    fun `loadWallets should update wallets state`() = runTest {
        val mockWalletRepository = mockk<WalletRepository>()
        coEvery { mockWalletRepository.getAllWallets() } returns flowOf(
            listOf(WalletEntity("0x123", "Test Wallet", "KEYSTORE", false, 0))
        )

        val viewModel = HomeViewModel(mockWalletRepository, mockTokenRepository)

        assert(viewModel.wallets.value.size == 1)
    }
}
```

### 4.2 集成测试 (第12周)

#### 数据库测试

```kotlin
@RunWith(AndroidJUnit4::class)
class WalletDaoTest {

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

## 📅 第五阶段：性能优化和监控 (1-2 周)

### 5.1 性能监控

#### 添加性能监控工具

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
}
```

## 🎯 实施时间表

| 阶段     | 时间   | 主要任务   | 交付物                      |
| -------- | ------ | ---------- | --------------------------- |
| 第一阶段 | 2-3 周 | 协程集成   | 协程工具类、网络层改造      |
| 第二阶段 | 3-4 周 | 架构组件   | ViewModel、Repository、Hilt |
| 第三阶段 | 4-5 周 | Compose UI | Compose 组件、导航          |
| 第四阶段 | 2-3 周 | 测试优化   | 单元测试、集成测试          |
| 第五阶段 | 1-2 周 | 性能监控   | 性能工具、监控              |

## 🚀 快速开始

### 1. 立即开始协程改造

```bash
# 1. 更新 build.gradle 添加协程依赖
# 2. 创建 CoroutineUtils.kt
# 3. 改造一个简单的网络调用
```

### 2. 逐步迁移策略

1. **先改造非关键路径** - 从设置页面开始
2. **保持向后兼容** - 新旧代码并存
3. **分模块迁移** - 一个模块一个模块地改造
4. **充分测试** - 每个阶段都要测试

### 3. 风险控制

- 保留原有代码作为备份
- 创建功能开关控制新旧代码
- 建立回滚机制
- 分阶段发布

## 📊 预期收益

### 性能提升

- 启动时间减少 30%
- 内存使用减少 20%
- 网络请求响应时间减少 25%

### 开发效率

- 代码量减少 40%
- 调试时间减少 50%
- 新功能开发速度提升 60%

### 用户体验

- 界面响应更流畅
- 错误处理更友好
- 离线功能更完善

这个计划将帮助你的项目逐步现代化，同时保持稳定性和可维护性。
