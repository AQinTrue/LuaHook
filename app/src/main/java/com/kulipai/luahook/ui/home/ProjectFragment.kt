package com.kulipai.luahook.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import com.kulipai.luahook.core.base.BaseFragment
import com.kulipai.luahook.databinding.FragmentHomeProjectBinding

class ProjectFragment: BaseFragment<FragmentHomeProjectBinding>() {
    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentHomeProjectBinding {
        return FragmentHomeProjectBinding.inflate(inflater, container, false)

    }



}