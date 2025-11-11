package com.alphawallet.app.widget

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.WebBackForwardList
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import com.alphawallet.app.R
import com.alphawallet.app.entity.DApp
import com.alphawallet.app.ui.widget.adapter.DappBrowserSuggestionsAdapter
import com.alphawallet.app.ui.widget.entity.ItemClickListener
import com.alphawallet.app.util.DappBrowserUtils
import com.alphawallet.app.util.KeyboardUtils
import com.alphawallet.app.util.KeyboardUtils.showKeyboard
import com.alphawallet.app.util.Utils
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

class AddressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : MaterialToolbar(context, attrs, defStyleAttr), ItemClickListener {

    companion object {
        private const val ANIMATION_DURATION = 100L
        private const val SUGGESTION_DELAY_MS = 600L
    }

    private val urlTv: AutoCompleteTextView
    private val btnClear: ImageView
    private val layoutNavigation: View
    private val back: ImageView
    private val next: ImageView
    private val home: ImageView?

    private val supervisorJob = SupervisorJob()
    private val uiScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + supervisorJob)
    private var suggestionJob: Job? = null

    private lateinit var adapter: DappBrowserSuggestionsAdapter
    private lateinit var listener: AddressBarListener

    private var focused = false

    init {
        inflate(context, R.layout.layout_url_bar_full, this)
        urlTv = findViewById(R.id.url_tv)
        btnClear = findViewById(R.id.clear_url)
        layoutNavigation = findViewById(R.id.layout_navigator)
        back = findViewById(R.id.back)
        next = findViewById(R.id.next)
        home = findViewById(R.id.home)

        initView()
    }

    /**
     * 配置地址栏的建议列表与事件监听器。
     */
    fun setup(dappList: List<DApp>, addressBarListener: AddressBarListener) {
        adapter = DappBrowserSuggestionsAdapter(context, dappList, this)
        listener = addressBarListener
        urlTv.setAdapter(null)

        urlTv.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                load(urlTv.text.toString())
            }
            false
        }

        urlTv.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && focused) {
                openURLInputView()
            }
        }

        urlTv.setOnClickListener {
            openURLInputView()
        }

        urlTv.setShowSoftInputOnFocus(true)
        urlTv.setOnLongClickListener {
            urlTv.dismissDropDown()
            false
        }
    }

    /**
     * 触发指定地址的加载流程并关闭编辑模式。
     */
    private fun load(url: String) {
        if (::listener.isInitialized) {
            listener.onLoad(url)
        }
        expandCollapseView(layoutNavigation, expand = true)
        leaveEditMode()
    }

    /**
     * 初始化工具栏上的按钮行为。
     */
    private fun initView() {
        home?.setOnClickListener {
            if (!::listener.isInitialized) return@setOnClickListener
            disableNavigationButtons()
            listener.onHomePagePressed()?.let { updateNavigationButtons(it) }
        }

        btnClear.setOnClickListener {
            clearAddressBar()
        }

        back.setOnClickListener {
            if (!::listener.isInitialized) return@setOnClickListener
            disableNavigationButtons()
            listener.loadPrevious()?.let { updateNavigationButtons(it) }
        }

        next.setOnClickListener {
            if (!::listener.isInitialized) return@setOnClickListener
            disableNavigationButtons()
            listener.loadNext()?.let { updateNavigationButtons(it) }
        }
    }

    /**
     * 清理输入框内容，必要时退出浏览并隐藏键盘。
     */
    private fun clearAddressBar() {
        val text = urlTv.text?.toString().orEmpty()
        if (text.isEmpty()) {
            KeyboardUtils.hideKeyboard(urlTv)
            if (::listener.isInitialized) {
                listener.onClear()
            }
        } else {
            urlTv.text?.clear()
            openURLInputView()
            showKeyboard(urlTv)
        }
    }

    /**
     * 显示地址输入模式并延迟开启搜索建议。
     */
    private fun openURLInputView() {
        urlTv.setAdapter(null)
        expandCollapseView(layoutNavigation, expand = false)
        suggestionJob?.cancel()
        if (!::adapter.isInitialized) {
            return
        }
        suggestionJob = uiScope.launch {
            delay(SUGGESTION_DELAY_MS)
            postBeginSearchSession(btnClear)
        }
    }

    /**
     * 开始搜索建议流程并展开清除按钮。
     */
    private fun postBeginSearchSession(clearButton: ImageView) {
        if (!::adapter.isInitialized) return
        urlTv.setAdapter(adapter)
        urlTv.showDropDown()
        if (clearButton.visibility == View.GONE) {
            expandCollapseView(clearButton, expand = true)
            showKeyboard(urlTv)
        }
    }

    /**
     * 根据目标状态展开或折叠指定视图。
     */
    private fun expandCollapseView(target: View, expand: Boolean) {
        val isExpanded = target.visibility == View.VISIBLE
        when {
            isExpanded && !expand -> {
                val finalWidth = target.width
                slideAnimator(finalWidth, 0, target).apply {
                    addListener(object : Animator.AnimatorListener {
                        override fun onAnimationStart(animation: Animator) = Unit

                        override fun onAnimationEnd(animation: Animator) {
                            target.visibility = View.GONE
                        }

                        override fun onAnimationCancel(animation: Animator) = Unit

                        override fun onAnimationRepeat(animation: Animator) = Unit
                    })
                    start()
                }
            }

            !isExpanded && expand -> {
                target.visibility = View.VISIBLE
                val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                target.measure(widthSpec, heightSpec)
                val width = target.measuredWidth
                slideAnimator(0, width, target).start()
            }
        }
    }

    /**
     * 创建用于调整视图宽度的补间动画。
     */
    private fun slideAnimator(start: Int, end: Int, target: View): ValueAnimator {
        return ValueAnimator.ofInt(start, end).apply {
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                val params: ViewGroup.LayoutParams = target.layoutParams
                params.width = value
                target.layoutParams = params
            }
            duration = ANIMATION_DURATION
        }
    }

    /**
     * 从建议列表中移除指定的 DApp。
     */
    fun removeSuggestion(dApp: DApp) {
        if (::adapter.isInitialized) {
            adapter.removeSuggestion(dApp.url)
        }
    }

    /**
     * 将新的 DApp 加入建议列表。
     */
    fun addSuggestion(dApp: DApp) {
        if (::adapter.isInitialized) {
            adapter.addSuggestion(dApp)
        }
    }

    /**
     * 将地址栏恢复为非编辑状态。
     */
    fun shrinkSearchBar() {
        expandCollapseView(layoutNavigation, expand = true)
        btnClear.visibility = View.GONE
        urlTv.dismissDropDown()
    }

    /**
     * 销毁过程中取消未完成的异步任务。
     */
    fun destroy() {
        suggestionJob?.cancel()
        supervisorJob.cancelChildren()
    }

    /**
     * 清空当前输入的地址。
     */
    fun clear() {
        urlTv.text?.clear()
    }

    /**
     * 从编辑模式退出并隐藏键盘。
     */
    fun leaveEditMode() {
        urlTv.clearFocus()
        KeyboardUtils.hideKeyboard(urlTv)
        btnClear.visibility = View.GONE
        focused = true
    }

    /**
     * 丢失焦点后复位内部状态。
     */
    fun leaveFocus() {
        urlTv.clearFocus()
        focused = false
    }

    /**
     * 更新地址栏文本。
     */
    fun setUrl(newUrl: String?) {
        urlTv.setText(newUrl)
    }

    /**
     * 根据浏览历史更新前进/后退按钮状态。
     */
    fun updateNavigationButtons(backForwardList: WebBackForwardList?) {
        val history = backForwardList ?: return
        val isLast = history.currentIndex + 1 > history.size - 1
        if (isLast) disableButton(next) else enableButton(next)

        val isFirst = history.currentIndex == 0
        if (isFirst) disableButton(back) else enableButton(back)
    }

    /**
     * 判断当前地址是否为默认首页。
     */
    fun isOnHomePage(): Boolean {
        return DappBrowserUtils.isDefaultDapp(urlTv.text?.toString().orEmpty())
    }

    /**
     * 读取当前输入的地址文本。
     */
    fun getUrl(): String {
        return urlTv.text?.toString().orEmpty()
    }

    /**
     * 禁用所有导航按钮。
     */
    private fun disableNavigationButtons() {
        disableButton(back)
        disableButton(next)
    }

    /**
     * 启用指定的导航按钮。
     */
    private fun enableButton(button: ImageView) {
        button.isEnabled = true
        button.alpha = 1.0f
    }

    /**
     * 禁用指定的导航按钮。
     */
    private fun disableButton(button: ImageView) {
        button.isEnabled = false
        button.alpha = 0.3f
    }

    /**
     * 长按建议条目时移除对应历史记录。
     */
    override fun onItemLongClick(url: String?) {
        if (!::adapter.isInitialized || url.isNullOrEmpty()) return
        adapter.removeSuggestion(url)
        DappBrowserUtils.removeFromHistory(context, url)
        adapter.notifyDataSetChanged()
    }

    /**
     * 点击建议条目时尝试加载该地址。
     */
    override fun onItemClick(url: String?) {
        if (url != null && Utils.isValidUrl(url)) {
            load(url)
        }
    }

    /**
     * 视图从窗口移除时清理协程任务，防止内存泄漏。
     */
    override fun onDetachedFromWindow() {
        suggestionJob?.cancel()
        super.onDetachedFromWindow()
    }
}
