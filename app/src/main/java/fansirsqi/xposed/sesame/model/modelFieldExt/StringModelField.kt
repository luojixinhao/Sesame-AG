package fansirsqi.xposed.sesame.model.modelFieldExt

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import fansirsqi.xposed.sesame.R
import fansirsqi.xposed.sesame.model.ModelField
import fansirsqi.xposed.sesame.ui.StringDialog

/**
 * String类型字段类
 * 该类用于表示字符串值字段，点击按钮弹出编辑对话框
 */
class StringModelField(code: String, name: String, value: String) : ModelField<String>(code, name, value) {

    override fun getType(): String = "STRING"

    override fun getConfigValue(): String? = value

    override fun setObjectValue(objectValue: Any?) {
        if (objectValue == null) {
            reset()
            return
        }
        value = objectValue.toString().trim()
    }

    override fun setConfigValue(configValue: String?) {
        setObjectValue(configValue)
    }

    override fun getView(context: Context): View {
        return Button(context).apply {
            text = name
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(ContextCompat.getColor(context, R.color.selection_color))
            background = ContextCompat.getDrawable(context, R.drawable.dialog_list_button)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minHeight = 150
            maxHeight = 180
            setPaddingRelative(40, 0, 40, 0)
            isAllCaps = false
            setOnClickListener { v ->
                StringDialog.showEditDialog(v.context, (v as Button).text, this@StringModelField)
            }
        }
    }
}
