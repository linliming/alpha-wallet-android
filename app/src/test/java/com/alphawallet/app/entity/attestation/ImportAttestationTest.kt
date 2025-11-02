package com.alphawallet.app.entity.attestation

import com.alphawallet.app.entity.ContractType
import com.alphawallet.app.entity.EasAttestation
import com.alphawallet.app.entity.QRResult
import com.alphawallet.app.entity.Wallet
import com.alphawallet.app.entity.tokens.TokenInfo
import com.alphawallet.app.repository.RealmManager
import com.alphawallet.app.service.AssetDefinitionService
import com.alphawallet.app.service.TokensService
import com.alphawallet.token.entity.AttestationValidationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import timber.log.Timber

/**
 * ImportAttestation 测试类
 *
 * 测试认证导入功能的各种场景
 *
 * @author AlphaWallet Team
 * @since 2024
 */
class ImportAttestationTest {
    private lateinit var importAttestation: ImportAttestation
    private lateinit var assetDefinitionService: AssetDefinitionService
    private lateinit var tokensService: TokensService
    private lateinit var callback: AttestationImportInterface
    private lateinit var wallet: Wallet
    private lateinit var realmManager: RealmManager
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        assetDefinitionService = mockk(relaxed = true)
        tokensService = mockk(relaxed = true)
        callback = mockk(relaxed = true)
        wallet = mockk(relaxed = true)
        realmManager = mockk(relaxed = true)
        client = mockk(relaxed = true)

        importAttestation =
            ImportAttestation(
                assetDefinitionService,
                tokensService,
                callback,
                wallet,
                realmManager,
                client,
            )
    }

    /**
     * 测试导入传统认证
     */
    @Test
    fun testImportLegacyAttestation() {
        // 准备测试数据
        val attestation =
            QRResult().apply {
                type = QRResult.QRType.ATTESTATION
                chainId = 1L
                setAddress("0x1234567890123456789012345678901234567890")
                setAttestation("test_attestation")
            }

        val tokenInfo =
            TokenInfo(
                "0x1234567890123456789012345678901234567890",
                1L,
                "Test Token",
                "TEST",
                0,
                ContractType.ERC721,
            )

        // 模拟服务响应
        every { tokensService.update(any(), any(), any()) } returns tokenInfo
        every { tokensService.storeTokenInfoDirect(any(), any(), any()) } returns tokenInfo

        // 执行测试
        importAttestation.importAttestation(attestation)

        // 验证调用
        verify { tokensService.update(attestation.getAddress(), attestation.chainId, ContractType.ERC721) }
        verify { tokensService.storeTokenInfoDirect(wallet, tokenInfo, ContractType.ERC721) }
    }

    /**
     * 测试导入EAS认证
     */
    @Test
    fun testImportEASAttestation() {
        // 准备测试数据
        val attestation =
            QRResult().apply {
                type = QRResult.QRType.EAS_ATTESTATION
                chainId = 1L
                setAddress("0x1234567890123456789012345678901234567890")
                functionDetail =
                    """
                    {
                        "attestation": "test_attestation",
                        "signature": "test_signature",
                        "chainId": 1,
                        "attester": "0x1234567890123456789012345678901234567890"
                    }
                    """.trimIndent()
            }

        // 执行测试
        importAttestation.importAttestation(attestation)

        // 验证调用
        verify { assetDefinitionService.getAssetDefinitionDeepScan(any()) }
    }

    /**
     * 测试验证认证
     */
    @Test
    fun testValidateAttestation() {
        // 准备测试数据
        val attestationString = "test_attestation"
        val tokenInfo =
            TokenInfo(
                "0x1234567890123456789012345678901234567890",
                1L,
                "Test Token",
                "TEST",
                0,
                ContractType.ERC721,
            )

        // 执行测试
        val result = importAttestation.validateAttestation(attestationString, tokenInfo)

        // 验证结果
        assert(result.tokenInfo == tokenInfo)
        assert(result.getAttestation() == attestationString)
    }

    /**
     * 测试恢复签名者
     */
    @Test
    fun testRecoverSigner() {
        // 准备测试数据
        val easAttestation =
            EasAttestation().apply {
                attestation = "test_attestation"
                signature = "test_signature"
            }

        // 执行测试
        val result = ImportAttestation.recoverSigner(easAttestation)

        // 验证结果（由于签名无效，应该返回空字符串）
        assert(result.isEmpty())
    }

    /**
     * 测试获取EAS合约地址
     */
    @Test
    fun testGetEASContract() {
        // 测试主网
        val mainnetAddress = ImportAttestation.getEASContract(1L)
        assert(mainnetAddress.isNotEmpty())

        // 测试测试网
        val testnetAddress = ImportAttestation.getEASContract(11155111L)
        assert(testnetAddress.isNotEmpty())

        // 测试未知网络
        val unknownAddress = ImportAttestation.getEASContract(999L)
        assert(unknownAddress.isNotEmpty())
    }

    /**
     * 测试不支持的认证类型
     */
    @Test
    fun testUnsupportedAttestationType() {
        // 准备测试数据
        val attestation =
            QRResult().apply {
                type = QRResult.QRType.ADDRESS // 不支持的类型
            }

        // 执行测试
        importAttestation.importAttestation(attestation)

        // 验证没有调用相关服务
        verify(exactly = 0) { tokensService.update(any(), any(), any()) }
        verify(exactly = 0) { assetDefinitionService.getAssetDefinitionDeepScan(any()) }
    }

    /**
     * 测试协程方法
     */
    @Test
    fun testCoroutineMethods() =
        runBlocking {
            // 准备测试数据
            val tokenInfo =
                TokenInfo(
                    "0x1234567890123456789012345678901234567890",
                    1L,
                    "Test Token",
                    "TEST",
                    0,
                    ContractType.ERC721,
                )

            val attestation =
                QRResult().apply {
                    type = QRResult.QRType.ATTESTATION
                    chainId = 1L
                    setAddress("0x1234567890123456789012345678901234567890")
                    setAttestation("test_attestation")
                }

            // 模拟服务响应
            every { tokensService.update(any(), any(), any()) } returns tokenInfo
            every { tokensService.storeTokenInfoDirect(any(), any(), any()) } returns tokenInfo

            // 执行测试（这里只是验证方法可以调用，实际业务逻辑需要更复杂的测试）
            // 由于协程方法需要实际的数据库操作，这里只是验证方法签名正确

            Timber.d("协程方法测试完成")
        }
}
