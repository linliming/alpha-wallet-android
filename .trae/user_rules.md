# 个人Android开发规则

基于Google Android编码规范和AlphaWallet项目最佳实践

## 1. 命名规范

### 1.1 包名

- 使用小写字母，单词间用点分隔
- 遵循反向域名规则：`com.company.project.module`
- 避免使用下划线或连字符

### 1.2 类名

- 使用PascalCase（首字母大写的驼峰命名）
- 类名应该是名词，清晰描述类的用途
- Activity类以"Activity"结尾：`MainActivity`
- Fragment类以"Fragment"结尾：`HomeFragment`
- ViewModel类以"ViewModel"结尾：`HomeViewModel`
- Repository类以"Repository"结尾：`WalletRepository`

### 1.3 方法名

- 使用camelCase（首字母小写的驼峰命名）
- 方法名应该是动词或动词短语
- 布尔值返回的方法以`is`、`has`、`can`、`should`开头

```kotlin
// 推荐
fun calculateBalance(): BigDecimal
fun isWalletValid(): Boolean
fun hasBackup(): Boolean
fun canPerformTransaction(): Boolean

// 避免
fun CalculateBalance(): BigDecimal
fun wallet_valid(): Boolean
```

### 1.4 变量名

- 使用camelCase
- 常量使用SCREAMING_SNAKE_CASE
- 私有成员变量可以使用下划线前缀（LiveData模式）

```kotlin
// 推荐
val walletAddress: String
val transactionCount: Int
const val MAX_RETRY_COUNT = 3
private val _walletName = MutableLiveData<String>()
fun walletName(): LiveData<String> = _walletName

// 避免
val WalletAddress: String
val transaction_count: Int
val maxRetryCount = 3 // 应该是const val
```

### 1.5 资源命名

- 使用小写字母和下划线
- 按类型和功能分组命名

```xml
<!-- 布局文件 -->
activity_main.xml
fragment_wallet.xml
item_transaction.xml

<!-- 字符串资源 -->
<string name="wallet_balance_title">钱包余额</string>
<string name="error_network_connection">网络连接错误</string>

<!-- 颜色资源 -->
<color name="primary_blue">#2196F3</color>
<color name="text_secondary">#757575</color>

<!-- 尺寸资源 -->
<dimen name="margin_standard">16dp</dimen>
<dimen name="text_size_large">18sp</dimen>
```

## 2. 代码格式化

### 2.1 缩进和空格

- 使用4个空格进行缩进，不使用Tab
- 操作符前后添加空格
- 逗号后添加空格
- 冒号后添加空格（Kotlin）

### 2.2 行长度

- 每行最多100个字符
- 超长行应该在合适的位置换行

```kotlin
// 推荐
val result = someVeryLongMethodName(
    parameter1,
    parameter2,
    parameter3
)

// 避免
val result = someVeryLongMethodName(parameter1, parameter2, parameter3, parameter4, parameter5)
```

### 2.3 大括号

- 左大括号不换行（K&R风格）
- 右大括号单独一行

```kotlin
// 推荐
if (condition) {
    doSomething()
} else {
    doSomethingElse()
}

// 避免
if (condition)
{
    doSomething()
}
else
{
    doSomethingElse()
}
```

## 3. Kotlin特定规范

### 3.1 可空性

- 优先使用非空类型
- 合理使用安全调用操作符`?.`和Elvis操作符`?:`
- 避免使用`!!`操作符，除非确定不会为null

```kotlin
// 推荐
val length = text?.length ?: 0
val result = nullableValue?.let { processValue(it) }

// 避免
val length = text!!.length // 危险
```

### 3.2 数据类

- 对于简单的数据容器使用data class
- 合理使用解构声明

```kotlin
data class Transaction(
    val hash: String,
    val amount: BigDecimal,
    val timestamp: Long
)

// 使用解构
val (hash, amount, _) = transaction
```

### 3.3 扩展函数

- 合理使用扩展函数增强可读性
- 避免过度使用扩展函数

```kotlin
// 推荐
fun String.isValidAddress(): Boolean {
    return this.matches(Regex("^0x[a-fA-F0-9]{40}$"))
}

// 使用
if (address.isValidAddress()) {
    // 处理有效地址
}
```

## 4. Android特定规范

### 4.1 Activity和Fragment

- Activity应该尽可能轻量，主要负责UI导航
- 业务逻辑应该放在ViewModel中
- 使用Fragment进行模块化UI设计

### 4.2 ViewModel设计

- 遵循MVVM架构模式
- 使用LiveData或StateFlow暴露数据
- 避免在ViewModel中持有Context引用

```kotlin
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository
) : BaseViewModel() {

    private val _walletBalance = MutableLiveData<BigDecimal>()
    fun walletBalance(): LiveData<BigDecimal> = _walletBalance

    fun loadBalance() {
        launchSafely {
            val balance = walletRepository.getBalance()
            _walletBalance.postValue(balance)
        }
    }
}
```

### 4.3 依赖注入

- 使用Hilt进行依赖注入
- 构造函数参数不超过10个
- 相关依赖可以分组到配置类中

### 4.4 资源管理

- 使用`use`扩展函数管理资源
- 及时释放不需要的资源
- 避免内存泄漏

## 5. 注释规范

### 5.1 类注释

- 使用KDoc格式
- 描述类的职责和主要功能
- 说明技术架构和设计模式

```kotlin
/**
 * 钱包管理视图模型
 *
 * 负责管理钱包相关的业务逻辑，包括：
 * - 钱包创建和导入
 * - 余额查询和更新
 * - 交易历史管理
 *
 * 技术特点：
 * - 使用Hilt进行依赖注入
 * - 继承自BaseViewModel
 * - 使用协程处理异步操作
 */
@HiltViewModel
class WalletViewModel @Inject constructor(
    // ...
) : BaseViewModel() {
    // ...
}
```

### 5.2 方法注释

- 公共方法必须添加注释
- 包含参数说明、返回值和异常情况

```kotlin
/**
 * 创建新钱包
 * @param walletName 钱包名称
 * @param password 钱包密码
 * @return 创建成功返回钱包地址，失败返回null
 * @throws IllegalArgumentException 当密码强度不足时
 */
fun createWallet(walletName: String, password: String): String? {
    // 实现
}
```

### 5.3 变量注释

- 重要的成员变量添加分类注释
- 复杂的业务逻辑添加行内注释

```kotlin
// LiveData数据状态管理
private val _walletList = MutableLiveData<List<Wallet>>()
private val _isLoading = MutableLiveData<Boolean>()

// 依赖注入的服务
private val walletRepository: WalletRepository
private val cryptoService: CryptoService

// 临时状态变量
private var currentWalletAddress: String? = null
private var isProcessing = false // 防止重复操作
```

## 6. 错误处理

### 6.1 异常处理

- 使用具体的异常类型
- 提供有意义的错误信息
- 统一错误处理机制

```kotlin
// 推荐
try {
    val result = performNetworkCall()
    processResult(result)
} catch (e: NetworkException) {
    handleNetworkError(e)
} catch (e: ValidationException) {
    handleValidationError(e)
} catch (e: Exception) {
    handleUnknownError(e)
}

// 统一错误处理
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

### 6.2 日志记录

- 使用适当的日志级别
- 避免在生产环境输出敏感信息
- 使用结构化日志格式

```kotlin
// 推荐
Timber.d("Loading wallet balance for address: %s", walletAddress)
Timber.w("Network request failed, retrying... Attempt: %d", retryCount)
Timber.e(throwable, "Failed to process transaction")

// 避免
Log.d("TAG", "Loading wallet balance for address: " + walletAddress)
System.out.println("Debug info") // 不要使用
```

## 7. 性能优化

### 7.1 内存管理

- 避免内存泄漏
- 合理使用缓存
- 及时释放大对象

### 7.2 UI性能

- 避免在主线程进行耗时操作
- 使用RecyclerView的ViewHolder模式
- 合理使用图片缓存

### 7.3 网络优化

- 使用连接池
- 实现请求缓存
- 处理网络异常和超时

## 8. 测试规范

### 8.1 单元测试

- 测试方法命名：`should_ExpectedBehavior_When_StateUnderTest`
- 使用Given-When-Then模式
- 保持测试的独立性

```kotlin
@Test
fun should_ReturnTrue_When_AddressIsValid() {
    // Given
    val validAddress = "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8e"

    // When
    val result = addressValidator.isValid(validAddress)

    // Then
    assertTrue(result)
}
```

### 8.2 集成测试

- 测试关键业务流程
- 使用模拟数据
- 验证组件间交互

## 9. 安全规范

### 9.1 敏感数据处理

- 不在日志中输出敏感信息
- 使用加密存储敏感数据
- 避免硬编码密钥和密码

### 9.2 网络安全

- 使用HTTPS进行网络通信
- 验证SSL证书
- 实现请求签名验证

## 10. 代码审查检查清单

- [ ] 命名是否符合规范
- [ ] 代码格式是否正确
- [ ] 是否遵循MVVM架构
- [ ] 依赖注入是否合理
- [ ] 错误处理是否完善
- [ ] 注释是否完整准确
- [ ] 是否存在内存泄漏风险
- [ ] 性能是否优化
- [ ] 安全性是否考虑
- [ ] 测试覆盖是否充分

---

_此规则基于Google Android编码规范和AlphaWallet项目实践制定，应根据项目需求和团队约定进行调整。_
