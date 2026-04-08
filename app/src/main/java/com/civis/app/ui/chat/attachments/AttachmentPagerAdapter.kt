package com.civis.app.ui.chat.attachments

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * Adapter para ViewPager2 que maneja las 5 pestañas del attachment sheet.
 */
class AttachmentPagerAdapter(
    activity: AppCompatActivity,
    private val galleryFragment: GalleryFragment,
    private val videoFragment: VideoFragment,
    private val documentFragment: DocumentFragment,
    private val audioFragment: AudioFragment,
    private val pollFragment: PollFragment
) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> galleryFragment
            1 -> videoFragment
            2 -> documentFragment
            3 -> audioFragment
            4 -> pollFragment
            else -> galleryFragment
        }
    }
}
