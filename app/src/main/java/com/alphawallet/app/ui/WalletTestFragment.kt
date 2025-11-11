package com.alphawallet.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.alphawallet.app.R
import dagger.hilt.android.AndroidEntryPoint

/**
 * Created by justindeguzman on 2/28/18.
 */
@AndroidEntryPoint
class WalletTestFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wallet_test, container, false)
    }
}
