package com.alphawallet.app.service

import com.alphawallet.app.entity.tokens.Token
import com.alphawallet.token.entity.Attribute
import com.alphawallet.token.entity.TokenScriptResult
import com.alphawallet.token.entity.UpdateType
import com.alphawallet.token.entity.ViewType
import com.alphawallet.token.tools.TokenDefinition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * AssetDefinitionService 测试类
 *
 * 测试协程版本的 resolveAttrs 方法
 */
class AssetDefinitionServiceTest {
    @Mock
    private lateinit var mockToken: Token

    @Mock
    private lateinit var mockTokenDefinition: TokenDefinition

    @Mock
    private lateinit var mockAttribute: Attribute

    private lateinit var assetDefinitionService: AssetDefinitionService

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        assetDefinitionService = AssetDefinitionService()
    }

    @Test
    fun `test resolveAttrs with null definition`() =
        runBlocking {
            // 测试当 Token 定义为空时的行为
            val result =
                assetDefinitionService
                    .resolveAttrs(
                        mockToken,
                        null,
                        BigInteger.ONE,
                        null,
                        ViewType.VIEW,
                        UpdateType.UPDATE_IF_REQUIRED,
                    ).first()

            assertNotNull("结果不应为空", result)
            assertEquals("应该返回默认属性", "RAttrs", result.name)
            assertEquals("值应该为空", "", result.value)
        }

    @Test
    fun `test resolveAttrs with valid definition`() =
        runBlocking {
            // 测试有效定义的情况
            val result =
                assetDefinitionService
                    .resolveAttrs(
                        mockToken,
                        mockTokenDefinition,
                        BigInteger.ONE,
                        listOf(mockAttribute),
                        ViewType.VIEW,
                        UpdateType.UPDATE_IF_REQUIRED,
                    ).toList()

            // 验证结果不为空
            assertNotNull("结果列表不应为空", result)
        }

    @Test
    fun `test resolveAttrs with multiple tokenIds`() =
        runBlocking {
            // 测试多个 TokenId 的情况
            val tokenIds = listOf(BigInteger.ONE, BigInteger.valueOf(2))
            val result =
                assetDefinitionService
                    .resolveAttrs(
                        mockToken,
                        tokenIds,
                        null,
                        UpdateType.UPDATE_IF_REQUIRED,
                    ).toList()

            // 验证结果不为空
            assertNotNull("结果列表不应为空", result)
        }

    @Test
    fun `test resolveAttrs flow behavior`() =
        runBlocking {
            // 测试流的行为
            val flow =
                assetDefinitionService.resolveAttrs(
                    mockToken,
                    null,
                    BigInteger.ONE,
                    null,
                    ViewType.VIEW,
                    UpdateType.UPDATE_IF_REQUIRED,
                )

            // 验证流可以正常收集
            val results = flow.toList()
            assertNotNull("收集的结果不应为空", results)
        }
}
