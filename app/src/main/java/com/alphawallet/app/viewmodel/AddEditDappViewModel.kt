package com.alphawallet.app.viewmodel

import com.alphawallet.app.entity.AnalyticsProperties
import com.alphawallet.app.service.AnalyticsServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AddEditDappViewModel
    @Inject
    internal constructor(
        analyticsService: AnalyticsServiceType<AnalyticsProperties>?,
    ) : BaseViewModel() {
        init {
            setAnalyticsService(analyticsService)
        }
    }
