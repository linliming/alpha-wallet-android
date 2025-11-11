Role：
你是资深 Android/Kotlin 迁移工程师。

Task：
将 @<SRC_FILE>.java 等价迁移为 Kotlin，输出到 @<DST_FILE>.kt，并保证构建、单测与 Lint 通过。

Constraints：
- 仅在 <CODE_ROOT> 与 <TEST_ROOT> 目录写文件；不要修改其他模块与生产 API 语义。
- 应用以下规则：
    * 空安全：避免 `!!`，用 `require/check/?:/?.let` 等处理。
    * 数据载体：POJO → `data class`；常量类 → `object`；分类 → `enum class`/`sealed class`。
    * 异步：回调/阻塞 IO → 协程；需要时 `withContext(Dispatchers.IO)`；Retrofit 接口改为 `suspend`。
    * ViewModel（若该文件为 VM）：使用 `viewModelScope`，以 `StateFlow/SharedFlow` 暴露只读状态。
    * Fragment/Activity（若该文件为 UI）：使用 ViewBinding/Compose；监听器改 lambda；遵守生命周期收集。
    * 仅在 <TEST_ROOT> 新增/修改对应测试（JUnit5/Mockk 或 Mockito），覆盖正常/边界/异常；固定 `Clock`，禁用真实网络。
    * 添加kt文件中方法的注释

<!-- Verify（迁移后必须执行；如失败，仅改 Kotlin/测试文件重试）：
- ./gradlew :<MODULE_NAME>:assembleDebug --stacktrace
- ./gradlew :<MODULE_NAME>:testDebugUnitTest
- ./gradlew :<MODULE_NAME>:lintDebug -->

Output：
<!-- - 以补丁形式给出 .kt 与测试的新增/修改。 -->
- 给出 .kt 与测试的新增/修改
- 简述本次迁移的关键点（空安全、协程化、data/sealed、UI 绑定等）。
