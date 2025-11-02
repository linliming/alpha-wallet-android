# AlphaWallet Android 项目开发规则

## 代码规范与最佳实践

### 1. ViewModel 设计原则

#### 1.1 构造函数优化

- **避免过多参数**：构造函数参数不应超过10个
- **参数分组**：将相关依赖项分组到专门的配置类中

    ```kotlin
    // 推荐：使用服务组合类
    class WalletServices(
        val genericWalletInteract: GenericWalletInteract,
        val fetchWalletsInteract: FetchWalletsInteract
    )

    // 避免：过多单独参数
    class HomeViewModel(
        param1: Type1, param2: Type2, ..., param23: Type23
    )
    ```

#### 1.2 依赖注入最佳实践

- 直接在构造函数参数中使用 `private val` 声明
- 避免在 `init` 块中重复赋值

    ```kotlin
    // 推荐
    class HomeViewModel(
        private val preferenceRepository: PreferenceRepositoryType
    )

    // 避免
    class HomeViewModel(preferenceRepository: PreferenceRepositoryType) {
        private val preferenceRepository = preferenceRepository
    }
    ```

### 2. LiveData 使用规范

#### 2.1 声明简化

- 利用 Kotlin 类型推断简化声明

    ```kotlin
    // 推荐
    private val transactions = MutableLiveData<Array<Transaction>>()

    // 避免
    private val transactions: MutableLiveData<Array<Transaction>> = MutableLiveData<Array<Transaction>>()
    ```

#### 2.2 访问器方法

- 为 LiveData 提供只读访问器
    ```kotlin
    private val _walletName = MutableLiveData<String?>()
    fun walletName(): LiveData<String?> = _walletName
    ```

### 3. 变量初始化优化

#### 3.1 延迟初始化

- 对于昂贵的对象使用 `lazy` 延迟初始化

    ```kotlin
    // 推荐
    private val cryptoFunctions by lazy { CryptoFunctions() }

    // 避免
    private var cryptoFunctions: CryptoFunctions? = null
    ```

#### 3.2 可空性处理

- 优先使用非空类型和默认值
- 必要时使用安全调用操作符 `?.` 和 Elvis 操作符 `?:`

### 4. 方法设计原则

#### 4.1 单一职责原则

- 每个方法应该只做一件事
- 复杂方法应拆分为多个小方法

    ```kotlin
    // 推荐：职责分离
    fun handleQRCode(qrCode: String) {
        if (!isValidQRCode(qrCode)) return
        val result = parseQRCode(qrCode)
        processQRResult(result)
    }

    private fun isValidQRCode(qrCode: String): Boolean { ... }
    private fun parseQRCode(qrCode: String): QRResult { ... }
    private fun processQRResult(result: QRResult) { ... }
    ```

#### 4.2 错误处理统一化

- 创建统一的错误处理机制
- 使用高阶函数封装通用逻辑
    ```kotlin
    inline fun <T> safeExecute(
        onError: (Throwable) -> Unit = ::defaultErrorHandler,
        action: () -> T
    ): T? {
        return try {
            action()
        } catch (e: Exception) {
            onError(e)
            null
        }
    }
    ```

### 5. 协程使用规范

#### 5.1 异步操作

- 使用 `launchSafely` 进行安全的协程启动
- 在 UI 更新前使用 `withMain` 切换到主线程
    ```kotlin
    fun loadData() {
        launchSafely(
            onError = { throwable -> handleError(throwable) }
        ) {
            val data = fetchDataFromNetwork()
            withMain {
                updateUI(data)
            }
        }
    }
    ```

### 6. 文件I/O操作规范

#### 6.1 资源管理

- 使用 Kotlin 的 `use` 扩展函数进行自动资源管理
- 使用 `copyTo` 方法进行高效数据传输

    ```kotlin
    // 推荐：使用 use 和 copyTo
    contentResolver.openInputStream(uri)?.use { inputStream ->
        FileOutputStream(newFileName).use { fos ->
            inputStream.copyTo(fos)
        }
    }

    // 避免：手动资源管理
    val inputStream = contentResolver.openInputStream(uri)
    val fos = FileOutputStream(newFileName)
    // 手动复制和关闭资源
    ```

### 7. 常量和配置管理

#### 7.1 常量定义

- 将常量定义在 `companion object` 中
- 使用有意义的命名和注释
    ```kotlin
    companion object {
        const val ALPHAWALLET_DIR = "AlphaWallet"
        private const val ECHO_MAX_MILLIS = 250L // QR码重复扫描防护时间
        private const val DEFAULT_DECIMALS = 18 // 默认以太坊代币精度
    }
    ```

### 8. 注释规范

#### 8.1 类级别注释

- 使用 KDoc 格式描述类的职责和功能
- 说明主要的技术架构和设计模式
- 使用中文注释提高可读性
    ```kotlin
    /**
     * 主页视图模型类
     *
     * 负责管理主页的所有业务逻辑和数据状态，包括：
     * - 钱包管理和切换
     * - QR码扫描和处理
     * - 交易数据管理
     * - UI状态控制
     *
     * 技术特点：
     * - 使用 Hilt 进行依赖注入
     * - 继承自 BaseViewModel 提供基础功能
     * - 使用协程处理异步操作
     */
    ```

#### 8.2 方法注释

- 为所有公共方法添加详细注释
- 包含参数说明、返回值描述和异常情况
    ```kotlin
    /**
     * 处理QR码扫描结果
     * @param activity 当前活动上下文
     * @param qrCode 扫描到的QR码字符串
     * @throws IllegalArgumentException 当QR码格式无效时
     */
    fun handleQRCode(activity: Activity, qrCode: String) { ... }
    ```

#### 8.3 变量注释

- 对重要的成员变量进行分类注释

    ```kotlin
    // LiveData 数据状态管理
    private val transactions = MutableLiveData<Array<Transaction>>()
    private val backUpMessage = MutableLiveData<String>()

    // 依赖注入的服务和仓库
    private val preferenceRepository: PreferenceRepositoryType
    private val genericWalletInteract: GenericWalletInteract

    // 临时状态变量
    private var currentWalletAddress: String? = null
    private var isProcessing = false
    ```

### 9. 性能优化原则

#### 9.1 内存管理

- 及时释放不需要的资源
- 避免内存泄漏，特别是 Context 引用

#### 9.2 网络请求优化

- 使用协程进行异步网络请求
- 实现适当的缓存机制
- 处理网络异常和超时

### 10. 测试友好设计

#### 10.1 依赖注入

- 所有外部依赖都应通过构造函数注入
- 避免在类内部创建依赖对象

#### 10.2 方法可测试性

- 避免静态方法调用
- 将复杂逻辑提取为纯函数

### 11. 代码审查检查清单

- [ ] 构造函数参数数量是否合理（≤10个）
- [ ] 是否正确使用了依赖注入
- [ ] LiveData 声明是否简洁
- [ ] 是否使用了适当的延迟初始化
- [ ] 方法是否遵循单一职责原则
- [ ] 错误处理是否统一和完善
- [ ] 协程使用是否安全
- [ ] 文件I/O操作是否使用了资源管理
- [ ] 常量定义是否合理
- [ ] 注释是否完整和准确（特别是中文注释）
- [ ] 是否考虑了性能优化
- [ ] 代码是否易于测试

## 文件组织规范

### ViewModel 文件结构

```kotlin
// 1. 包声明和导入
package com.alphawallet.app.viewmodel
import ...

// 2. 类注释（中文KDoc格式）
/**
 * 类功能描述
 *
 * 主要功能：
 * - 功能1描述
 * - 功能2描述
 *
 * 技术特点：
 * - 使用的框架和模式
 */
@HiltViewModel
class SomeViewModel @Inject constructor(
    // 3. 构造函数参数（按功能分组，使用private val）
    private val repository: Repository,
    private val service: Service
) : BaseViewModel() {

    // 4. LiveData 声明（分类注释）
    // LiveData 数据状态管理
    private val _data = MutableLiveData<Type>()

    // 5. 依赖注入的服务（按功能分组，分类注释）
    // 依赖注入的服务和仓库

    // 6. 临时状态变量（分类注释）
    // 临时状态变量

    // 7. 初始化块（带注释说明）
    /**
     * 初始化块
     * 设置依赖注入的服务和仓库，初始化基础配置
     */
    init { ... }

    // 8. 生命周期方法（带注释）
    /**
     * 清理资源，取消所有正在进行的操作
     */
    override fun onCleared() { ... }

    // 9. 公共访问器方法（带注释）
    /**
     * 获取数据的LiveData
     * @return 数据的只读LiveData
     */
    fun data(): LiveData<Type> = _data

    // 10. 公共业务方法（按功能分组，带详细注释）

    // 11. 私有辅助方法（带注释）

    // 12. 伴生对象和常量
    companion object {
        // 常量定义（带注释说明用途）
    }
}
```

---

_此规则文件基于 HomeViewModel 代码审查结果制定，包含了文件I/O优化、中文注释规范等最佳实践，应定期更新以反映最新的开发标准。_
