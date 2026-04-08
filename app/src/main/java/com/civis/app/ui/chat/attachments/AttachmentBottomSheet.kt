package com.civis.app.ui.chat.attachments

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import com.civis.app.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Bottom Sheet personalizado para adjuntar archivos multimedia y encuestas.
 * Se abre desde abajo en pantalla completa.
 * Contiene 5 pestañas: Galería, Video, Archivos, Audio, Encuestas.
 */
class AttachmentBottomSheet : com.google.android.material.bottomsheet.BottomSheetDialogFragment(R.layout.bottom_sheet_attachment) {

    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2
    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout

    /** Bandera que indica si el panel ya terminó de abrirse (evita que la animación inicial cierre el sheet) */
    private var isSheetReady = false

    private val galleryFragment = GalleryFragment()
    private val videoFragment = VideoFragment()
    private val documentFragment = DocumentFragment()
    private val audioFragment = AudioFragment()
    private val pollFragment = PollFragment()

    /** Callback cuando el usuario selecciona un archivo multimedia */
    var onMediaSelected: ((uri: Uri, mimeType: String, type: String) -> Unit)? = null

    /** Callback cuando el usuario toca el botón de cámara */
    var onCameraClick: (() -> Unit)? = null

    /** Callback cuando el usuario cancela el preview (para no cerrar el sheet) */
    var onPreviewCancelled: (() -> Unit)? = null

    /** Callback cuando el usuario crea una encuesta */
    var onPollCreated: ((question: String, options: List<String>, multiple: Boolean, optionColors: List<String>, style: String, images: Map<Int, Uri>) -> Unit)? = null

    /** Launcher para explorador de archivos (documentos) */
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val mimeType = requireContext().contentResolver.getType(it) ?: "application/octet-stream"
            onMediaSelected?.invoke(it, mimeType, "document")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): BottomSheetDialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setCanceledOnTouchOutside(false)

        // Configurar behavior ANTES de mostrar para evitar pausa en la animación
        dialog.behavior.apply {
            isFitToContents = false
            skipCollapsed = true
            isHideable = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }

        dialog.setOnShowListener { _ ->
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                sheet.layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
                sheet.requestLayout()

                // Drag hacia abajo cierra el panel
                behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(sheetView: View, newState: Int) {
                        if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                            // El panel terminó de abrirse, ahora se puede cerrar por arrastre
                            isSheetReady = true
                        }
                        if (newState == BottomSheetBehavior.STATE_COLLAPSED ||
                            newState == BottomSheetBehavior.STATE_HALF_EXPANDED
                        ) {
                            dismiss()
                        }
                    }
                    override fun onSlide(sheetView: View, slideOffset: Float) {
                        // Solo cerrar si el panel ya terminó de abrirse (evita conflicto con animación inicial)
                        if (isSheetReady && slideOffset < 0.3f) {
                            behavior.state = BottomSheetBehavior.STATE_HIDDEN
                        }
                    }
                })
            }
        }

        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        tabLayout = view.findViewById(R.id.tabLayout)

        // Mantener todos los fragments vivos para evitar reciclaje de FragmentStateAdapter
        viewPager.offscreenPageLimit = 4

        val pagerAdapter = AttachmentPagerAdapter(
            requireActivity() as androidx.appcompat.app.AppCompatActivity,
            galleryFragment,
            videoFragment,
            documentFragment,
            audioFragment,
            pollFragment
        )
        viewPager.adapter = pagerAdapter

        com.google.android.material.tabs.TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Galería"
                1 -> "Video"
                2 -> "Archivos"
                3 -> "Audio"
                4 -> "Encuestas"
                else -> ""
            }
        }.attach()

        setupFragmentCallbacks()
    }

    private fun setupFragmentCallbacks() {
        // NO hacer dismiss() aquí — el ChatActivity decide cuándo cerrar
        galleryFragment.onPhotoSelected = { uri, mimeType ->
            onMediaSelected?.invoke(uri, mimeType, "image")
        }

        galleryFragment.onCameraSelected = {
            dismiss()
            onCameraClick?.invoke()
        }

        videoFragment.onVideoSelected = { uri, mimeType ->
            onMediaSelected?.invoke(uri, mimeType, "video")
        }

        documentFragment.onFileSelected = { uri, mimeType ->
            onMediaSelected?.invoke(uri, mimeType, "document")
        }

        audioFragment.onAudioSelected = { uri, mimeType ->
            onMediaSelected?.invoke(uri, mimeType, "audio")
        }

        pollFragment.onPollCreated = { question, options, multiple, optionColors, style, images ->
            onPollCreated?.invoke(question, options, multiple, optionColors, style, images)
            dismiss()
        }
    }

    companion object {
        fun newInstance(
            onMediaSelected: (uri: Uri, mimeType: String, type: String) -> Unit
        ): AttachmentBottomSheet {
            return AttachmentBottomSheet().apply {
                this.onMediaSelected = onMediaSelected
            }
        }
    }
}
