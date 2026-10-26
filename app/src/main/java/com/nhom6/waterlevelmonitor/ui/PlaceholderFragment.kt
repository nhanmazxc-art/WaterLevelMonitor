package com.nhom6.waterlevelmonitor.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.nhom6.waterlevelmonitor.R

class PlaceholderFragment : Fragment(R.layout.fragment_placeholder) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val title = arguments?.getString(ARG_TITLE).orEmpty()
        view.findViewById<TextView>(R.id.tvPlaceholder).text =
            "$title\nSẽ được làm ở giai đoạn sau"
    }

    companion object {
        private const val ARG_TITLE = "title"

        fun newInstance(title: String): PlaceholderFragment {
            val fragment = PlaceholderFragment()
            fragment.arguments = Bundle().apply { putString(ARG_TITLE, title) }
            return fragment
        }
    }
}