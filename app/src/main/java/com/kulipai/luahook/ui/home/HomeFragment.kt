package com.kulipai.luahook.ui.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.appcompat.R
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.kulipai.luahook.databinding.ActivityMainHomeBinding
import com.kulipai.luahook.ui.script.editor.global.EditActivity
import com.kulipai.luahook.core.shell.ShellManager
import com.kulipai.luahook.core.xposed.XposedScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {


    private var _binding: ActivityMainHomeBinding? = null
    private val binding get() = _binding!!

    // TODO)) 封装
    private fun getAppVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName!!
        } catch (_: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    // TODO)) 封装
    fun getAppVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode // 注意这里使用 longVersionCode，在旧版本中是 versionCode (Int)
        } catch (_: PackageManager.NameNotFoundException) {
            -1 // 或者其他表示未找到的数值
        }
    }

    // TODO)) 封装
    fun getDynamicColor(context: Context, @AttrRes colorAttributeResId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(colorAttributeResId, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

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


    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = ActivityMainHomeBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}