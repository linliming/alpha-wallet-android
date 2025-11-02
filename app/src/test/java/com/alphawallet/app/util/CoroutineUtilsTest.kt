package com.alphawallet.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/**
 * CoroutineUtils 测试类
 *
 * 测试协程工具类的各种功能
 *
 * @author AlphaWallet Team
 * @since 2024
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoroutineUtilsTest {
    @Test
    fun `test launchSafely with success`() =
        runTest {
            var executed = false
            var errorCaught = false

            val scope = CoroutineScope(Dispatchers.Unconfined)

            CoroutineUtils.launchSafely(
                scope = scope,
                onError = { error ->
                    errorCaught = true
                },
            ) {
                executed = true
            }

            // 等待协程完成
            advanceUntilIdle()

            assertTrue("协程应该被执行", executed)
            assertFalse("不应该有错误", errorCaught)
        }

    @Test
    fun `test launchSafely with error`() =
        runTest {
            var errorCaught = false
            var errorMessage = ""

            val scope = CoroutineScope(Dispatchers.Unconfined)

            CoroutineUtils.launchSafely(
                scope = scope,
                onError = { error ->
                    errorCaught = true
                    errorMessage = error.message ?: ""
                },
            ) {
                throw RuntimeException("测试错误")
            }

            // 等待协程完成
            advanceUntilIdle()

            assertTrue("应该捕获到错误", errorCaught)
            assertEquals("错误消息应该匹配", "测试错误", errorMessage)
        }

    @Test
    fun `test launchSafely without error handler`() =
        runTest {
            var executed = false

            val scope = CoroutineScope(Dispatchers.Unconfined)

            CoroutineUtils.launchSafely(
                scope = scope,
            ) {
                executed = true
            }

            // 等待协程完成
            advanceUntilIdle()

            assertTrue("协程应该被执行", executed)
        }

    @Test
    fun `test launchSafely with custom dispatcher`() =
        runTest {
            var executed = false

            val scope = CoroutineScope(Dispatchers.Unconfined)

            CoroutineUtils.launchSafely(
                scope = scope,
                dispatcher = Dispatchers.Default,
            ) {
                executed = true
            }

            // 等待协程完成
            advanceUntilIdle()

            assertTrue("协程应该被执行", executed)
        }
}
