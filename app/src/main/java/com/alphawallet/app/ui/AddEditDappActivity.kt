package com.alphawallet.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.alphawallet.app.R
import com.alphawallet.app.analytics.Analytics
import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.entity.DApp
import com.alphawallet.app.util.DappBrowserUtils
import com.alphawallet.app.util.Utils
import com.alphawallet.app.viewmodel.AddEditDappViewModel
import com.alphawallet.app.widget.InputView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

/**
 * Screen used to add a new DApp shortcut or edit an existing entry.
 */
@AndroidEntryPoint
class AddEditDappActivity : BaseActivity() {

    private lateinit var viewModel: AddEditDappViewModel
    private lateinit var titleView: TextView
    private lateinit var nameInput: InputView
    private lateinit var urlInput: InputView
    private lateinit var confirmButton: Button
    private lateinit var iconView: ImageView

    private var mode: Int = MODE_ADD
    private lateinit var dapp: DApp

    /**
     * Inflates the layout, initialises view model and populates UI depending on mode.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_dapp)
        toolbar()
        setTitle("")
        initViews()
        initViewModel()

        val intent: Intent = intent ?: run {
            finish()
            return
        }
        mode = intent.extras?.getInt(KEY_MODE) ?: MODE_ADD
        dapp = intent.getParcelableExtra(KEY_DAPP) ?: run {
            finish()
            return
        }

        loadIcon()
        when (mode) {
            MODE_ADD -> configureAddMode()
            MODE_EDIT -> configureEditMode()
            else -> finish()
        }
    }

    /**
     * Sends analytics when the screen becomes visible.
     */
    override fun onResume() {
        super.onResume()
        viewModel.track(if (mode == MODE_ADD) Analytics.Navigation.ADD_DAPP else Analytics.Navigation.EDIT_DAPP)
    }

    /**
     * Initialises UI widgets.
     */
    private fun initViews() {
        titleView = findViewById(R.id.title)
        nameInput = findViewById(R.id.dapp_title)
        urlInput = findViewById(R.id.dapp_url)
        confirmButton = findViewById(R.id.btn_confirm)
        iconView = findViewById(R.id.icon)
    }

    /**
     * Creates the view model instance.
     */
    private fun initViewModel() {
        viewModel = ViewModelProvider(this)[AddEditDappViewModel::class.java]
    }

    /**
     * Configures UI for adding a new dapp entry.
     */
    private fun configureAddMode() {
        setTitle(R.string.add_to_my_dapps)
        confirmButton.setText(R.string.action_add)
        nameInput.setText(dapp.name)
        nameInput.getEditText().setSelection(0)
        urlInput.setText(dapp.url)
        urlInput.getEditText().setSelection(0)
        confirmButton.setOnClickListener {
            dapp.name = nameInput.getText().toString()
            dapp.url = urlInput.getText().toString()
            addDapp(dapp)
        }
    }

    /**
     * Configures UI for editing an existing entry.
     */
    private fun configureEditMode() {
        setTitle(R.string.edit_dapp)
        confirmButton.setText(R.string.action_save)
        urlInput.setText(dapp.url)
        urlInput.getEditText().setSelection(0)
        nameInput.setText(dapp.name)
        nameInput.getEditText().setSelection(0)
        confirmButton.setOnClickListener { saveDapp(dapp) }
    }

    /**
     * Loads a favicon for display, falling back to the default icon.
     */
    private fun loadIcon() {
        val visibleUrl = Utils.getDomainName(dapp.url)
        if (!TextUtils.isEmpty(visibleUrl)) {
            val favicon = DappBrowserUtils.getIconUrl(visibleUrl)
            Glide.with(this)
                .load(favicon)
                .apply(RequestOptions().placeholder(R.drawable.ic_logo))
                .into(iconView)
        }
    }

    /**
     * Persists changes to an existing DApp in shared preferences.
     */
    private fun saveDapp(dapp: DApp) {
        try {
            val myDapps = DappBrowserUtils.getMyDapps(this)
            myDapps.forEach { item ->
                if (item.name == dapp.name && item.url == dapp.url) {
                    item.name = nameInput.getText().toString()
                    item.url = urlInput.getText().toString()
                }
            }
            DappBrowserUtils.saveToPrefs(this, myDapps)

            val props = AnalyticsProperties().apply {
                put(Analytics.PROPS_URL, dapp.url)
            }
            viewModel.track(Analytics.Action.DAPP_EDITED, props)
        } catch (e: Exception) {
            Timber.e(e)
        } finally {
            finish()
        }
    }

    /**
     * Adds a new DApp entry and records an analytics event.
     */
    private fun addDapp(dapp: DApp) {
        val myDapps = DappBrowserUtils.getMyDapps(this).apply {
            add(dapp)
        }
        DappBrowserUtils.saveToPrefs(this, myDapps)
        val props = AnalyticsProperties().apply {
            put(Analytics.PROPS_URL, dapp.url)
        }
        viewModel.track(Analytics.Action.DAPP_ADDED, props)
        finish()
    }

    companion object {
        const val KEY_MODE = "mode"
        const val KEY_DAPP = "dapp"
        const val MODE_ADD = 0
        const val MODE_EDIT = 1
    }
}
