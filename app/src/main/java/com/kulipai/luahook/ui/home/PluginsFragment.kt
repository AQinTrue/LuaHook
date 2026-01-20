package com.kulipai.luahook.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.kulipai.luahook.core.base.BaseFragment
import com.kulipai.luahook.databinding.FragmentHomePluginsBinding

class PluginsFragment: BaseFragment<FragmentHomePluginsBinding>() {

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomePluginsBinding {
        return FragmentHomePluginsBinding.inflate(inflater, container, false)
    }

    override fun initView() {
        // TODO: Implement plugin UI
    }

}