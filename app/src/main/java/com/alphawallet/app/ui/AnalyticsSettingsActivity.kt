package com.alphawallet.app.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.CompoundButton
import androidx.lifecycle.ViewModelProvider
import com.alphawallet.app.R
import com.alphawallet.app.viewmodel.AnalyticsSettingsViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AnalyticsSettingsActivity : BaseActivity() {
    var viewModel: AnalyticsSettingsViewModel? = null
    var analyticsSwitch: SwitchMaterial? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_analytics_settings)

        toolbar()

        setTitle(getString(R.string.settings_title_analytics))

        viewModel =
            ViewModelProvider(this).get<AnalyticsSettingsViewModel>(AnalyticsSettingsViewModel::class.java)

        initViews()
    }

    private fun initViews() {
        analyticsSwitch = findViewById<SwitchMaterial>(R.id.switch_analytics)
        analyticsSwitch!!.setChecked(viewModel!!.isAnalyticsEnabled())
        analyticsSwitch!!.setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            viewModel!!.toggleAnalytics(isChecked)
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
//        getMenuInflater().inflate(R.menu.menu_help, menu);
        return true
    }

    public override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.getItemId() == R.id.action_help) {
            showHelpUi()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showHelpUi() {
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setCancelable(true)
        bottomSheetDialog.setCanceledOnTouchOutside(true)
        //        bottomSheetDialog.setContentView(contentView);
//        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from((View) contentView.getParent());
//        bottomSheetDialog.setOnShowListener(dialog -> behavior.setPeekHeight(contentView.getHeight()));
        bottomSheetDialog.show()
    }
}
