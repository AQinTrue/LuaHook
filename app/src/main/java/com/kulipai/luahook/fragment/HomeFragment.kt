package com.kulipai.luahook.fragment

import AViewModel
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.google.android.material.card.MaterialCardView
import com.kulipai.luahook.activity.EditActivity
import com.kulipai.luahook.R
import com.kulipai.luahook.util.ShellManager
import com.kulipai.luahook.util.XposedScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class HomeFragment : Fragment() {

    private val viewModel by viewModels<AViewModel>()

    private fun getAppVersionName(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName!!
        } catch (_: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }


    fun getAppVersionCode(context: Context): Long {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.longVersionCode // 注意这里使用 longVersionCode，在旧版本中是 versionCode (Int)
        } catch (_: PackageManager.NameNotFoundException) {
            -1 // 或者其他表示未找到的数值
        }
    }

    fun getDynamicColor(context: Context, @AttrRes colorAttributeResId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(colorAttributeResId, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }


    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("MissingInflatedId", "SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.activity_main_home, container, false)
        val card1: MaterialCardView by lazy { view.findViewById(R.id.card1) }
        val howToActivateCard: MaterialCardView by lazy { view.findViewById(R.id.how_to_activate) }
        val card: MaterialCardView by lazy { view.findViewById(R.id.card) }
        val img: ImageView by lazy { view.findViewById(R.id.img) }
        val status: TextView by lazy { view.findViewById(R.id.status) }
        val version: TextView by lazy { view.findViewById(R.id.version) }

        var frameworkName = ""

        version.text =
            getAppVersionName(requireContext()) + " (" + getAppVersionCode(requireContext()).toString() + ")"

        XposedScope.withService {
            frameworkName = " + " + it.frameworkName
        }
        // 观察 LiveData，当数据变化时自动更新 UI
        viewModel.data.observe(requireActivity()) {
            if (ShellManager.getMode() == ShellManager.Mode.ROOT) {


                status.text = "Root$frameworkName"
//            status.text="Root模式"+resources.getString(R.string.Xposed_status_ok)
                card.setCardBackgroundColor(
                    getDynamicColor(
                        requireContext(),
                        androidx.appcompat.R.attr.colorPrimary
                    )
                )
                status.setTextColor(
                    getDynamicColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorOnPrimary
                    )
                )
                version.setTextColor(
                    getDynamicColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorOnPrimary
                    )
                )
                img.setImageResource(R.drawable.check_circle_24px)
                img.setColorFilter(
                    getDynamicColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorOnPrimary
                    )
                )

            } else if (ShellManager.getMode() == ShellManager.Mode.SHIZUKU) {
                status.text = "Shizuku$frameworkName"
                card.setCardBackgroundColor(
                    getDynamicColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorTertiary
                    )
                )
                status.setTextColor(
                    getDynamicColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorOnTertiary
                    )
                )
                version.setTextColor(
                    getDynamicColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorOnTertiary
                    )
                )
                img.setImageResource(R.drawable.shizuku_logo)
                img.setColorFilter(
                    getDynamicColor(
                        requireContext(),
                        com.google.android.material.R.attr.colorOnTertiary
                    )
                )

            } else {

                //显示教程
                GlobalScope.launch(Dispatchers.Main) {
                    delay(400)
                    howToActivateCard.visibility = View.VISIBLE
                }
            }

        }


        card1.setOnClickListener {
            if (ShellManager.getMode() != ShellManager.Mode.NONE) {
                val intent = Intent(requireActivity(), EditActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(requireContext(), resources.getString(R.string.Inactive_modules), Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }
}