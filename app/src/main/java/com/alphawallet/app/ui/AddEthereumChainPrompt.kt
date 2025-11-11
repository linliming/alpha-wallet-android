package com.alphawallet.app.ui

import android.content.Context
import android.widget.TextView
import com.alphawallet.app.R
import com.alphawallet.app.entity.StandardFunctionInterface
import com.alphawallet.app.web3.entity.WalletAddEthereumChainObject
import com.alphawallet.app.widget.FunctionButtonBar
import com.google.android.material.bottomsheet.BottomSheetDialog

class AddEthereumChainPrompt private constructor(
    context: Context,
    private val chainObject: WalletAddEthereumChainObject,
    private val listener: AddChainListener
) : BottomSheetDialog(context), StandardFunctionInterface {

    /**
     * 定义一个函数式接口 (fun interface)，
     * 这允许我们在调用 newInstance 时直接使用 Lambda 表达式。
     */
    fun interface AddChainListener {
        fun onAdd(chainObject: WalletAddEthereumChainObject)
    }

    init {
        // 使用 R.layout.dialog_add_ethereum_chain 设置视图
        setContentView(R.layout.dialog_add_ethereum_chain)

        // 使用更安全的 findViewById 和 Kotlin 属性访问
        val message = findViewById<TextView>(R.id.message)
        message?.text = context.getString(
            R.string.add_chain_dialog_message,
            chainObject.chainName,
            chainObject.getChainId().toString() // 使用 .toString()
        )

        val functionBar = findViewById<FunctionButtonBar>(R.id.layoutButtons)
        functionBar?.setupFunctions(
            this,
            // 使用 mutableListOf() 创建列表
            mutableListOf(R.string.action_enable_switch_reload)
        )
        functionBar?.revealButtons()
    }

    /**
     * 处理 FunctionButtonBar 的点击事件。
     */
    override fun handleClick(action: String?, actionId: Int) {
        // 点击“添加”按钮
        listener.onAdd(chainObject)
    }

    /**
     * 伴生对象 (Companion Object)，用于实现静态工厂方法
     */
    companion object {
        /**
         * 创建 AddEthereumChainPrompt 实例的公共工厂方法。
         *
         * @param context Context
         * @param chainObject 要添加的链的配置对象
         * @param listener 点击“添加”按钮时的回调 (Lambda)
         * @return 一个配置好的 AddEthereumChainPrompt 实例
         */
        @JvmStatic // 确保 Java 代码可以像调用静态方法一样调用它
        fun newInstance(
            context: Context,
            chainObject: WalletAddEthereumChainObject,
            listener: AddChainListener
        ): AddEthereumChainPrompt {
            // 调用私有的构造函数来创建实例
            return AddEthereumChainPrompt(context, chainObject, listener)
        }
    }
}
