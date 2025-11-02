# TokensService 功能梳理文档

## 概述

TokensService是AlphaWallet中最核心的服务类之一，负责管理所有与代币相关的操作。该服务采用Kotlin协程实现异步操作，提供高效、稳定的代币管理功能。

## 主要功能模块

### 1. 代币管理模块 (Token Management)

#### 核心功能

- **代币获取**: `getToken(chainId, address)` - 从数据库获取指定代币
- **代币存储**: `storeToken(token)` - 将代币存储到数据库
- **代币添加**: `addToken(info, walletAddress)` - 添加新代币
- **代币删除**: `deleteTokens(metasToDelete)` - 批量删除代币
- **代币信息更新**: `update(address, chainId, type)` - 更新代币信息

#### 特殊功能

- **基础代币创建**: `createBaseToken(chainId)` - 创建链的基础代币
- **服务代币获取**: `getServiceToken(chainId)` - 获取链服务代币
- **代币或基础货币**: `getTokenOrBase(chainId, address)` - 智能获取代币

### 2. 余额检查模块 (Balance Checking)

#### 核心机制

- **定期余额检查**: 每500ms执行一次余额检查周期
- **智能更新队列**: `getNextInBalanceUpdateQueue()` - 基于优先级算法选择下一个更新的代币
- **链余额同步**: `syncChainBalances()` - 同步所有链的余额

#### 更新策略

- **焦点代币**: 15秒更新周期，优先级最高
- **基础链代币**: 20秒更新周期
- **普通ERC20**: 30秒-5分钟更新周期
- **隐藏代币**: 降低更新频率

#### 余额变化处理

- **链可见性检查**: 有余额时自动启用链
- **ERC20检查触发**: 基础链余额变化时检查ERC20代币
- **OpenSea检查触发**: 余额变化时检查NFT

### 3. 价格管理模块 (Price/Ticker Management)

#### 功能特性

- **价格信息获取**: `getTokenTicker(token)` - 获取代币价格
- **批量价格同步**: `syncERC20Tickers()` - 同步ERC20代币价格
- **价格更新**: `updateTickers()` - 手动触发价格更新
- **法币价值计算**: `getFiatValuePair()`, `getTokenFiatValue()` - 计算法币价值

#### 价格来源

- 内部价格服务
- OKX API集成
- 第三方价格源

### 4. 网络管理模块 (Network Management)

#### 网络过滤

- **过滤器设置**: `setupFilter()` - 设置网络过滤器
- **网络列表获取**: `getNetworkFilters()` - 获取当前过滤的网络
- **动态网络启用**: 检测到余额时自动启用网络

#### 网络信息

- **网络名称**: `getNetworkName(chainId)` - 获取网络名称
- **网络符号**: `getNetworkSymbol(chainId)` - 获取网络符号
- **链可见性管理**: 基于余额和用户设置管理链的显示

### 5. NFT资产模块 (NFT Assets)

#### OpenSea集成

- **NFT获取**: `checkOpenSea()` - 从OpenSea获取NFT
- **API调用**: `callOpenSeaAPI()` - 调用OpenSea API
- **更新状态检查**: `openSeaUpdateInProgress()` - 检查更新状态

#### NFT管理

- **资产存储**: `storeAsset()` - 存储NFT资产
- **资产更新**: `updateAssets()` - 更新NFT资产列表
- **资产获取**: 通过Token对象获取NFT资产

### 6. OKX集成模块 (OKX Integration)

#### 支持的协议

- ERC-20代币
- ERC-721 NFT
- ERC-1155 多代币标准

#### 功能特性

- **代币检查**: `checkTokensOnOKx()` - 在OKX检查代币
- **链检查**: `checkChainOnOkx()` - 检查特定链上的代币
- **代币列表处理**: `processOkTokenList()` - 处理OKX返回的代币列表

### 7. 未知代币发现模块 (Unknown Token Discovery)

#### 自动发现机制

- **未知代币检查**: `checkUnknownTokens()` - 检查未知代币
- **优先级添加**: `addUnknownTokenToCheckPriority()` - 高优先级添加
- **合约类型判断**: 自动判断合约类型（ERC20/ERC721/ERC1155）

#### 处理流程

1. 添加未知合约地址到队列
2. 定期检查队列中的合约
3. 获取合约信息（名称、符号、精度等）
4. 判断合约类型
5. 创建并存储代币对象

### 8. 用户焦点管理模块 (Focus Management)

#### 焦点机制

- **焦点代币设置**: `setFocusToken()` - 设置当前关注的代币
- **焦点代币清除**: `clearFocusToken()` - 清除焦点
- **应用焦点管理**: `walletInFocus()`, `walletOutOfFocus()` - 应用焦点状态

#### 焦点优化

- 焦点代币享有最高更新优先级
- 应用失去焦点时降低更新频率
- 智能资源管理

### 9. 异步操作管理模块 (Async Operations)

#### 协程管理

- **服务作用域**: `serviceScope` - 管理所有异步操作
- **任务取消**: 统一的任务取消机制
- **错误处理**: 集中的错误处理和日志记录

#### 主要异步任务

- `updateCycleJob`: 主更新周期任务
- `unknownTokenCheckJob`: 未知代币检查任务
- `imageWriteJob`: 图片写入任务
- `okxCheckJob`: OKX检查任务
- `balanceCheckJob`: 余额检查任务

### 10. 图片管理模块 (Image Management)

#### 功能特性

- **图片URL添加**: `addTokenImageUrl()` - 添加代币图片URL
- **批量写入**: `writeImages()` - 批量写入图片信息
- **后备URL**: `getFallbackUrlForToken()` - 获取后备图片URL

### 11. 数据持久化模块 (Data Persistence)

#### Realm数据库

- **Realm实例管理**: `getRealmInstance()`, `getWalletRealmInstance()`
- **代币存储**: 持久化代币信息
- **价格存储**: 持久化价格信息

#### 数据同步

- **钱包切换**: 切换钱包时的数据迁移
- **本地地址更新**: `updateLocalAddress()` - 更新本地地址信息

### 12. 分析统计模块 (Analytics)

#### 用户行为跟踪

- **Gas使用统计**: `track(gasSpeed)` - 跟踪Gas使用情况
- **功能使用分析**: 集成分析服务

## 服务生命周期

### 初始化阶段

1. 依赖注入设置
2. 网络过滤器初始化
3. 当前钱包地址设置
4. 数据结构初始化

### 运行阶段

1. **启动更新周期**: `startUpdateCycle()`
2. **定期余额检查**: 每500ms执行
3. **优先级算法**: 选择下一个更新的代币
4. **异步任务管理**: 并行执行多个任务

### 销毁阶段

1. **停止更新周期**: `stopUpdateCycle()`
2. **取消所有协程**: 清理资源
3. **清空数据结构**: 释放内存

## 性能优化特性

### 1. 智能更新策略

- 基于代币重要性的优先级算法
- 焦点代币优先更新
- 应用失去焦点时降低更新频率

### 2. 并发处理

- 使用Kotlin协程实现高效并发
- 线程安全的数据结构
- 异步操作不阻塞主线程

### 3. 资源管理

- 自动取消无用的任务
- 内存使用优化
- 网络请求去重

### 4. 缓存机制

- 代币信息缓存
- 价格信息缓存
- 图片URL缓存

## 错误处理与容错

### 1. 网络错误处理

- 自动重试机制
- 降级处理
- 错误日志记录

### 2. 数据一致性

- 事务性操作
- 数据校验
- 回滚机制

### 3. 异常恢复

- 服务自动重启
- 状态恢复
- 用户友好的错误提示

## 扩展性设计

### 1. 模块化架构

- 功能模块独立
- 接口分离
- 依赖注入

### 2. 插件化支持

- 新链集成
- 第三方API集成
- 自定义代币类型

### 3. 配置化管理

- 更新频率配置
- 网络超时配置
- 功能开关配置

## 技术亮点

### 1. Kotlin协程优势

- 取代RxJava，提供更简洁的异步编程
- 结构化并发，更好的错误处理
- 取消机制，避免内存泄漏

### 2. 智能算法

- 代币更新优先级算法
- 自适应更新频率
- 智能网络选择

### 3. 多源数据集成

- OpenSea NFT数据
- OKX代币数据
- 内部价格服务
- 区块链直接查询

## 使用示例

```kotlin
// 获取代币
val token = tokensService.getToken(1L, "0x...")

// 设置焦点代币
tokensService.setFocusToken(token)

// 启动更新周期
tokensService.startUpdateCycle()

// 获取价格信息
val ticker = tokensService.getTokenTicker(token)

// 添加未知代币
tokensService.addUnknownTokenToCheck(ContractAddress(1L, "0x..."))
```

这个TokensService提供了完整的代币生态管理功能，是AlphaWallet钱包应用的核心组件。通过Kotlin协程的引入，大大提升了性能和代码可维护性。
