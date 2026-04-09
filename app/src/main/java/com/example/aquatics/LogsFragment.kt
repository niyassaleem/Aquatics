package com.example.aquatics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch

class LogsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LogAdapter
    private lateinit var toolbar: MaterialToolbar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_logs, container, false)

        toolbar = view.findViewById(R.id.logToolbar)
        recyclerView = view.findViewById(R.id.recyclerLogs)
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = LogAdapter(emptyList())
        recyclerView.adapter = adapter

        toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        loadLogs()

        return view
    }

    private fun loadLogs() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val logs = db.logDao().getAllLogs()
            adapter.updateLogs(logs)
        }
    }
}
