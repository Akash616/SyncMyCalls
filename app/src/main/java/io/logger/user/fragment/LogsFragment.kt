package io.logger.user.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import io.logger.adapter.DailyLogsAdapter
import io.logger.databinding.FragmentLogsBinding
import io.logger.model.DailyLogs
import io.logger.user.activity.MainActivity
import io.logger.user.repository.CallLogRepository
import io.logger.utility.CallTypes

class LogsFragment : Fragment() {

    private var _binding: FragmentLogsBinding? = null
    private val binding get() = _binding!!
    private val adapter = DailyLogsAdapter()

    private var callTypesFilter = CallTypes.ALL_TYPES
    private var phoneNumberFilter: String? = null
    private var startDateFilter: Long? = null
    private var endDateFilter: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvCallLogs.layoutManager = LinearLayoutManager(context)
        binding.rvCallLogs.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            (requireActivity() as? MainActivity)?.requestCallLogPermission {
                loadCallLogs()
            }
            binding.swipeRefreshLayout.isRefreshing = false
        }

        (requireActivity() as? MainActivity)?.requestCallLogPermission {
            loadCallLogs()
        }
    }

    fun applyFilters(
        callTypes: Set<String>,
        phoneNumber: String?,
        startDate: Long?,
        endDate: Long?
    ) {
        this.callTypesFilter = callTypes
        this.phoneNumberFilter = phoneNumber
        this.startDateFilter = startDate
        this.endDateFilter = endDate
        Log.d("MYTESTING", "LogsFragment => selectedCallTypes: $callTypes, phoneNumber: $phoneNumber, startDate: $startDate, endDate: $endDate")
        loadCallLogs()
    }

    private fun loadCallLogs() {
        val dailyLogsList = CallLogRepository.fetchCallLogs(
            requireContext(),
            callTypesFilter,
            phoneNumberFilter,
            startDateFilter,
            endDateFilter
        )
        if (dailyLogsList.isNotEmpty()) {
            adapter.submitList(dailyLogsList)
            binding.tvNothingToDisplay.visibility = View.GONE
            binding.rvCallLogs.visibility = View.VISIBLE
            binding.rvCallLogs.smoothScrollToPosition(0)
        } else {
            adapter.submitList(emptyList())
            binding.rvCallLogs.visibility = View.GONE
            binding.tvNothingToDisplay.visibility = View.VISIBLE
            Log.w("MYTESTING", "No call logs found after applying filters: callTypes=$callTypesFilter, phoneNumber=$phoneNumberFilter, startDate=$startDateFilter, endDate=$endDateFilter")
        }
    }

    internal fun fetchCallLogs(): List<DailyLogs> {
        return CallLogRepository.fetchCallLogs(
            requireContext(),
            callTypesFilter,
            phoneNumberFilter,
            startDateFilter,
            endDateFilter
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}