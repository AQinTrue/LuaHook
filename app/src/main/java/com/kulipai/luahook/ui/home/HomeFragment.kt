package com.kulipai.luahook.ui.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.R
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kulipai.luahook.core.pm.PackageUtils.getAppVersionCode
import com.kulipai.luahook.core.pm.PackageUtils.getAppVersionName
import com.kulipai.luahook.core.shell.ShellManager
import com.kulipai.luahook.core.theme.ColorUtils.getDynamicColor
import com.kulipai.luahook.core.xposed.XposedScope
import com.kulipai.luahook.databinding.FragmentHomeHomeBinding
import com.kulipai.luahook.ui.script.editor.global.EditActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {


    private var _binding: FragmentHomeHomeBinding? = null
    private val binding get() = _binding!!


    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var frameworkName = ""

        binding.version.text =
            getAppVersionName(requireContext()) + " (" + getAppVersionCode(requireContext()).toString() + ")"

        XposedScope.withService {
            frameworkName = " + " + it.frameworkName
        }



        lifecycleScope.launch {
            ShellManager.mode.collect { newMode ->
                if (newMode == ShellManager.Mode.ROOT) {

                    // 取消显示教程
                    binding.howToActivate.visibility = View.GONE

                    binding.status.text = "Root$frameworkName"
//                  status.text="Root模式"+resources.getString(R.string.Xposed_status_ok)
                    binding.card.setCardBackgroundColor(
                        getDynamicColor(
                            requireContext(),

                            R.attr.colorPrimary
                        )
                    )
                    binding.status.setTextColor(
                        getDynamicColor(
                            requireContext(),
                            com.google.android.material.R.attr.colorOnPrimary
                        )
                    )
                    binding.version.setTextColor(
                        getDynamicColor(
                            requireContext(),
                            com.google.android.material.R.attr.colorOnPrimary
                        )
                    )
                    binding.img.setImageResource(com.kulipai.luahook.R.drawable.check_circle_24px)
                    binding.img.setColorFilter(
                        getDynamicColor(
                            requireContext(),
                            com.google.android.material.R.attr.colorOnPrimary
                        )
                    )

                } else if (newMode == ShellManager.Mode.SHIZUKU) {

                    // 取消显示教程
                    binding.howToActivate.visibility = View.GONE

                    binding.status.text = "Shizuku$frameworkName"
                    binding.card.setCardBackgroundColor(
                        getDynamicColor(
                            requireContext(),
                            com.google.android.material.R.attr.colorTertiary
                        )
                    )
                    binding.status.setTextColor(
                        getDynamicColor(
                            requireContext(),
                            com.google.android.material.R.attr.colorOnTertiary
                        )
                    )
                    binding.version.setTextColor(
                        getDynamicColor(
                            requireContext(),
                            com.google.android.material.R.attr.colorOnTertiary
                        )
                    )
                    binding.img.setImageResource(com.kulipai.luahook.R.drawable.shizuku_logo)
                    binding.img.setColorFilter(
                        getDynamicColor(
                            requireContext(),
                            com.google.android.material.R.attr.colorOnTertiary
                        )
                    )

                } else {
                    //显示教程
                    launch {
                        delay(400)
                        binding.howToActivate.visibility = View.VISIBLE
                    }

                }

            }
        }


        binding.card1.setOnClickListener {
            if (ShellManager.mode.value != ShellManager.Mode.NONE) {
                val intent = Intent(requireActivity(), EditActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(
                    requireContext(),
                    resources.getString(com.kulipai.luahook.R.string.Inactive_modules),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHomeHomeBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}