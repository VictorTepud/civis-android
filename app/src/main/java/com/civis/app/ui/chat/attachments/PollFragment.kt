package com.civis.app.ui.chat.attachments

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.civis.app.R
import com.civis.app.databinding.FragmentAttachmentPollBinding

class PollFragment : Fragment() {

    private var _binding: FragmentAttachmentPollBinding? = null
    private val binding get() = _binding!!

    private var selectedStyle = "bars"

    // Para tarjetas: map de index de opción -> URI de imagen
    private val optionImages = mutableMapOf<Int, Uri>()
    private var pendingImageIndex = -1

    // Colores por opción: map de index -> colorName
    private val optionColorMap = mutableMapOf<Int, String>()
    private var pendingColorIndex = -1

    var onPollCreated: ((question: String, options: List<String>, multiple: Boolean, optionColors: List<String>, style: String, images: Map<Int, Uri>) -> Unit)? = null

    private val pollColors = listOf(
        "blue" to "#2196F3",
        "green" to "#4CAF50",
        "orange" to "#FF9800",
        "red" to "#E53935",
        "purple" to "#9C27B0",
        "teal" to "#009688",
        "brown" to "#795548",
        "blue_grey" to "#607D8B"
    )

    private val pollStyles = listOf(
        "bars" to "Barras",
        "vertical" to "Verticales",
        "cards" to "Tarjetas"
    )

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            if (pendingImageIndex >= 0) {
                optionImages[pendingImageIndex] = it
                refreshOptionImages()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAttachmentPollBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Asignar colores por defecto a las 2 opciones iniciales
        optionColorMap[0] = pollColors[0].first  // blue
        optionColorMap[1] = pollColors[1].first  // green

        setupStyleSelector()

        addOptionRow("Opción 1", 0)
        addOptionRow("Opción 2", 1)

        binding.btnAddOption.setOnClickListener {
            val count = binding.optionsContainer.childCount
            if (count >= 10) {
                Toast.makeText(requireContext(), "Máximo 10 opciones", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Asignar color cíclico
            optionColorMap[count] = pollColors[count % pollColors.size].first
            addOptionRow("Opción ${count + 1}", count)
        }

        binding.btnSendPoll.setOnClickListener {
            createPoll()
        }
    }

    // ===== PER-OPTION COLOR PICKER (popup) =====

    private fun showOptionColorPicker(optionIndex: Int) {
        val dp = resources.displayMetrics.density
        val popup = android.widget.PopupWindow(requireContext())
        popup.isOutsideTouchable = true
        popup.isFocusable = true

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(12 * dp)
                setColor(Color.parseColor("#2A2A2A"))
                setStroke((1 * dp).toInt(), Color.parseColor("#444444"))
            }
        }

        val currentColor = optionColorMap[optionIndex] ?: pollColors[optionIndex % pollColors.size].first

        pollColors.forEach { (name, hexColor) ->
            val colorInt = Color.parseColor(hexColor)
            val sizePx = (36 * dp).toInt()
            val marginPx = (4 * dp).toInt()
            val circle = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    leftMargin = marginPx
                    rightMargin = marginPx
                }
                scaleType = ImageView.ScaleType.CENTER
                setImageResource(if (name == currentColor) R.drawable.ic_poll_color_selected else R.drawable.ic_poll_color_normal)
                imageTintList = android.content.res.ColorStateList.valueOf(colorInt)
                setOnClickListener {
                    optionColorMap[optionIndex] = name
                    // Actualizar el indicador de color en la fila
                    updateRowColorIndicator(optionIndex, name, hexColor)
                    popup.dismiss()
                }
            }
            container.addView(circle)
        }

        popup.contentView = container
        popup.width = LinearLayout.LayoutParams.WRAP_CONTENT
        popup.height = LinearLayout.LayoutParams.WRAP_CONTENT

        // Mostrar debajo del botón de color de la opción
        val row = binding.optionsContainer.findViewWithTag<LinearLayout>("row_$optionIndex")
        val colorBtn = row?.findViewWithTag<View>("btn_color_$optionIndex")
        if (colorBtn != null) {
            val loc = IntArray(2)
            colorBtn.getLocationOnScreen(loc)
            popup.showAtLocation(binding.root, android.view.Gravity.NO_GRAVITY, loc[0], loc[1] + colorBtn.height)
        }
    }

    private fun updateRowColorIndicator(index: Int, colorName: String, hexColor: String) {
        val row = binding.optionsContainer.findViewWithTag<LinearLayout>("row_$index") ?: return
        val btn = row.findViewWithTag<ImageButton>("btn_color_$index") ?: return
        btn.imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(hexColor))
    }

    // ===== STYLE SELECTOR =====

    private fun setupStyleSelector() {
        val container = binding.styleSelector
        container.removeAllViews()

        val padH = (14 * resources.displayMetrics.density).toInt()
        val padV = (10 * resources.displayMetrics.density).toInt()
        val marginPx = (6 * resources.displayMetrics.density).toInt()

        pollStyles.forEach { (id, label) ->
            val chip = TextView(requireContext()).apply {
                text = label
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(padH, padV, padH, padV)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = marginPx
                    rightMargin = marginPx
                    topMargin = (2 * resources.displayMetrics.density).toInt()
                    bottomMargin = (2 * resources.displayMetrics.density).toInt()
                }
                val isSelected = (id == selectedStyle)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setCornerRadius(20 * resources.displayMetrics.density)
                    setColor(if (isSelected) Color.parseColor("#0066FF") else Color.parseColor("#2A2A2A"))
                    setStroke((1 * resources.displayMetrics.density).toInt(),
                        if (isSelected) Color.parseColor("#0066FF") else Color.parseColor("#444444"))
                }
                setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#AAAAAA"))
                tag = id
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    false
                }
                setOnClickListener {
                    selectedStyle = id
                    setupStyleSelector()
                    refreshOptionImages() // Mostrar/ocultar botón de imagen según estilo
                }
            }
            container.addView(chip)
        }
    }

    // ===== OPTION ROWS =====

    private fun addOptionRow(hintText: String, index: Int) {
        val dp = resources.displayMetrics.density

        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * dp).toInt()
                bottomMargin = (4 * dp).toInt()
            }
            tag = "row_$index"
        }

        // Botón de color para esta opción
        val assignedColor = optionColorMap[index] ?: pollColors[index % pollColors.size]
        val assignedHex = pollColors.find { it.first == assignedColor }?.second ?: "#2196F3"
        val btnColor = ImageButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                marginEnd = (4 * dp).toInt()
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            scaleType = ImageView.ScaleType.CENTER
            setImageResource(R.drawable.ic_poll_color_normal)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(assignedHex))
            contentDescription = "Color de opción"
            tag = "btn_color_$index"
            setOnClickListener {
                showOptionColorPicker(index)
            }
        }
        row.addView(btnColor)

        // Si es estilo tarjetas, botón de imagen a la izquierda
        if (selectedStyle == "cards") {
            val btnImage = ImageButton(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                    marginEnd = (4 * dp).toInt()
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                setImageResource(R.drawable.ic_image)
                setColorFilter(Color.parseColor("#888888"))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setCornerRadius(8 * dp)
                    setColor(Color.parseColor("#2A2A2A"))
                    setStroke((1 * dp).toInt(), Color.parseColor("#444444"))
                }
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                contentDescription = "Agregar imagen"
                tag = "btn_image_$index"
                setOnClickListener {
                    pendingImageIndex = index
                    imagePickerLauncher.launch("image/*")
                }
            }
            row.addView(btnImage)
        }

        val et = EditText(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setHint(hintText)
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            setSingleLine(true)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(8 * dp)
                setColor(Color.parseColor("#2A2A2A"))
                setStroke((1 * dp).toInt(), Color.parseColor("#444444"))
            }
            val padPx = (12 * dp).toInt()
            setPadding(padPx, padPx, padPx, padPx)
            tag = "option_edit"
        }

        val btnDelete = ImageButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt())
            setImageResource(R.drawable.ic_close)
            setColorFilter(Color.parseColor("#AAAAAA"))
            val outValue = TypedValue()
            requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            setBackgroundResource(outValue.resourceId)
            contentDescription = "Eliminar opción"
            setOnClickListener {
                if (binding.optionsContainer.childCount > 2) {
                    binding.optionsContainer.removeView(row)
                } else {
                    Toast.makeText(requireContext(), "Mínimo 2 opciones", Toast.LENGTH_SHORT).show()
                }
            }
        }

        row.addView(et)
        row.addView(btnDelete)
        binding.optionsContainer.addView(row)

        // Si ya hay imagen para esta opción, mostrarla
        if (optionImages.containsKey(index)) {
            updateRowImage(row, index)
        }
    }

    private fun refreshOptionImages() {
        // Reconstruir todas las filas de opciones con/sin botón de imagen
        val texts = mutableListOf<String>()
        for (i in 0 until binding.optionsContainer.childCount) {
            val row = binding.optionsContainer.getChildAt(i) as? LinearLayout ?: continue
            val et = row.findViewWithTag<EditText>("option_edit") ?: continue
            texts.add(et.text.toString())
        }

        binding.optionsContainer.removeAllViews()
        texts.forEachIndexed { index, text ->
            addOptionRow("Opción ${index + 1}", index)
            // Restaurar texto
            val row = binding.optionsContainer.getChildAt(index) as? LinearLayout ?: return@forEachIndexed
            val et = row.findViewWithTag<EditText>("option_edit") ?: return@forEachIndexed
            et.setText(text)
        }
    }

    private fun updateRowImage(row: LinearLayout, index: Int) {
        val btnImage = row.findViewWithTag<ImageButton>("btn_image_$index")
        if (btnImage != null && optionImages.containsKey(index)) {
            try {
                btnImage.setImageURI(optionImages[index])
                btnImage.setColorFilter(null)
                btnImage.scaleType = ImageView.ScaleType.CENTER_CROP
                btnImage.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setCornerRadius(8 * resources.displayMetrics.density)
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#0066FF"))
                }
            } catch (_: Exception) {}
        }
    }

    // ===== CREATE POLL =====

    private fun createPoll() {
        val question = binding.etQuestion.text.toString().trim()
        if (question.isEmpty()) {
            binding.etQuestion.error = "Escribe una pregunta"
            return
        }

        val options = mutableListOf<String>()
        for (i in 0 until binding.optionsContainer.childCount) {
            val row = binding.optionsContainer.getChildAt(i) as LinearLayout
            val et = row.findViewWithTag<EditText>("option_edit")
                ?: row.getChildAt(0) as? EditText
            if (et == null) continue
            val text = et.text.toString().trim()
            if (text.isEmpty()) {
                et.error = "Escribe una opción"
                return
            }
            options.add(text)
        }

        if (options.size < 2) return

        val multiple = binding.switchMultiple.isChecked
        // Recoger colores por opción en orden
        val orderedColors = mutableListOf<String>()
        for (i in 0 until binding.optionsContainer.childCount) {
            orderedColors.add(optionColorMap[i] ?: pollColors[i % pollColors.size].first)
        }
        onPollCreated?.invoke(question, options, multiple, orderedColors, selectedStyle, optionImages.toMap())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
