package com.alphawallet.app.repository

import com.alphawallet.app.entity.NetworkInfo

/**
 * OnNetworkChangeListener - 网络变更监听器接口
 *
 * 这个接口定义了网络变更时的回调方法。
 * 当用户切换网络或网络状态发生变化时，会触发此监听器。
 *
 * @author AlphaWallet Team
 * @since 2024
 */
interface OnNetworkChangeListener {
    /**
     * 网络变更回调方法
     *
     * @param networkInfo 变更后的网络信息
     */
    fun onNetworkChanged(networkInfo: NetworkInfo)
}
