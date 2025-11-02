/**
 * 获取TokenScript文件
 *
 * @param chainId 链ID
 * @param address 合约地址
 * @return TokenScriptFile对象
 */
fun getTokenScriptFile(
    chainId: Long,
    address: String,
): TokenScriptFile =
    try {
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            val tsData: RealmTokenScriptData? =
                realm
                    .where(RealmTokenScriptData::class.java)
                    .equalTo("instanceKey", getTSDataKey(chainId, address))
                    .findFirst()

            // 方案1: 使用if-else直接返回
            if (tsData?.filePath != null) {
                TokenScriptFile(context, tsData.filePath)
            } else {
                TokenScriptFile(context)
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "获取 TokenScript 文件失败: chainId=$chainId, address=$address")
        TokenScriptFile(context)
    }

/**
 * 优化方案2: 使用run函数简化逻辑
 */
fun getTokenScriptFileOptimized(
    chainId: Long,
    address: String,
): TokenScriptFile =
    try {
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            realm
                .where(RealmTokenScriptData::class.java)
                .equalTo("instanceKey", getTSDataKey(chainId, address))
                .findFirst()
                ?.filePath
                ?.let { filePath -> TokenScriptFile(context, filePath) }
                ?: TokenScriptFile(context)
        }
    } catch (e: Exception) {
        Timber.e(e, "获取 TokenScript 文件失败: chainId=$chainId, address=$address")
        TokenScriptFile(context)
    }

/**
 * 优化方案3: 使用when表达式
 */
fun getTokenScriptFileWithWhen(
    chainId: Long,
    address: String,
): TokenScriptFile =
    try {
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            val tsData =
                realm
                    .where(RealmTokenScriptData::class.java)
                    .equalTo("instanceKey", getTSDataKey(chainId, address))
                    .findFirst()

            when {
                tsData?.filePath != null -> TokenScriptFile(context, tsData.filePath)
                else -> TokenScriptFile(context)
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "获取 TokenScript 文件失败: chainId=$chainId, address=$address")
        TokenScriptFile(context)
    }

/**
 * 优化方案4: 使用扩展函数简化
 */
fun getTokenScriptFileWithExtension(
    chainId: Long,
    address: String,
): TokenScriptFile =
    try {
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            realm.findTokenScriptData(chainId, address)?.let { tsData ->
                if (tsData.filePath != null) {
                    TokenScriptFile(context, tsData.filePath)
                } else {
                    TokenScriptFile(context)
                }
            } ?: TokenScriptFile(context)
        }
    } catch (e: Exception) {
        Timber.e(e, "获取 TokenScript 文件失败: chainId=$chainId, address=$address")
        TokenScriptFile(context)
    }

/**
 * 扩展函数：简化Realm查询
 */
private fun Realm.findTokenScriptData(
    chainId: Long,
    address: String,
): RealmTokenScriptData? =
    where(RealmTokenScriptData::class.java)
        .equalTo("instanceKey", getTSDataKey(chainId, address))
        .findFirst()

/**
 * 优化方案5: 最简洁的版本
 */
fun getTokenScriptFileSimplified(
    chainId: Long,
    address: String,
): TokenScriptFile =
    try {
        realmManager.getRealmInstance(ASSET_DEFINITION_DB).use { realm ->
            realm
                .where(RealmTokenScriptData::class.java)
                .equalTo("instanceKey", getTSDataKey(chainId, address))
                .findFirst()
                ?.filePath
                ?.let { TokenScriptFile(context, it) }
                ?: TokenScriptFile(context)
        }
    } catch (e: Exception) {
        Timber.e(e, "获取 TokenScript 文件失败: chainId=$chainId, address=$address")
        TokenScriptFile(context)
    }
