package io.logger.user.fragment

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import io.logger.R
import io.logger.databinding.FragmentAnalyticsBinding
import io.logger.model.DailyLogs
import io.logger.user.activity.MainActivity
import io.logger.user.repository.CallLogRepository
import io.logger.utility.CallTypes
import java.util.concurrent.TimeUnit

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private var callTypesFilter = CallTypes.ALL_TYPES
    private var phoneNumberFilter: String? = null
    private var startDateFilter: Long? = null
    private var endDateFilter: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as? MainActivity)?.requestCallLogPermission {
            loadAnalytics()
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
        Log.d("MYTESTING", "AnalyticsFragment => selectedCallTypes: $callTypes, phoneNumber: $phoneNumber, startDate: $startDate, endDate: $endDate")
        loadAnalytics()
    }

    private fun loadAnalytics() {
        val callLogs = fetchCallLogs()
        updateCallStatistics(callLogs)
        updateCallDuration(callLogs)
        updateCallDirectionAnalysis(callLogs)
        updateMostCalledNumber(callLogs)
        updateMostReceivedNumber(callLogs)
    }

    private fun fetchCallLogs(): List<DailyLogs> {
        return CallLogRepository.fetchCallLogs(
            requireContext(),
            callTypesFilter,
            phoneNumberFilter,
            startDateFilter,
            endDateFilter
        )
    }

    private fun updateCallStatistics(callLogs: List<DailyLogs>) {
        var callsMade = 0
        var callsRejected = 0
        var callsMissed = 0
        var callsBlocked = 0

        callLogs.forEach { dailyLog ->
            dailyLog.calls.forEach { call ->
                when (call.callType) {
                    CallTypes.OUTGOING -> callsMade++
                    CallTypes.REJECTED -> callsRejected++
                    CallTypes.MISSED -> callsMissed++
                    CallTypes.BLOCKED -> callsBlocked++
                }
            }
        }

        binding.tvCallsMade.text = callsMade.toString()
        binding.tvCallsRejected.text = callsRejected.toString()
        binding.tvCallsMissed.text = callsMissed.toString()
        binding.tvCallsBlocked.text = callsBlocked.toString()
    }

    private fun updateCallDuration(callLogs: List<DailyLogs>) {
        val durations = callLogs.flatMap { dailyLog ->
            dailyLog.calls.mapNotNull { call ->
                call.duration.toLongOrNull()
            }
        }

        if (durations.isNotEmpty()) {
            val totalDuration = durations.sum()
            val averageDuration = totalDuration / durations.size
            val longestDuration = durations.maxOrNull() ?: 0L

            binding.tvAverageDuration.text = formatDuration(averageDuration)
            binding.tvLongestDuration.text = formatDuration(longestDuration)
        } else {
            binding.tvAverageDuration.text = "0 min 0 s"
            binding.tvLongestDuration.text = "0 min 0 s"
        }
    }

    private fun updateCallDirectionAnalysis(callLogs: List<DailyLogs>) {
        var outgoingCalls = 0
        var incomingCalls = 0

        callLogs.forEach { dailyLog ->
            dailyLog.calls.forEach { call ->
                when (call.callType) {
                    CallTypes.OUTGOING -> outgoingCalls++
                    CallTypes.INCOMING -> incomingCalls++
                }
            }
        }

        val totalCalls = outgoingCalls + incomingCalls
        binding.chartContainer.removeAllViews()

        if (totalCalls > 0) {
            val pieChart = PieChart(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setUsePercentValues(false)
                description.isEnabled = false
                legend.isEnabled = false
                setDrawEntryLabels(false)
                isDrawHoleEnabled = true
                setHoleColor(Color.TRANSPARENT)
                holeRadius = 55f
                transparentCircleRadius = 0f
            }

            val entries = listOf(
                PieEntry(outgoingCalls.toFloat(), outgoingCalls.toString()),
                PieEntry(incomingCalls.toFloat(), incomingCalls.toString())
            )

            val dataSet = PieDataSet(entries, "").apply {
                colors = listOf(
                    Color.parseColor("#BB86FC"),
                    Color.parseColor("#03DAC6")
                )
                setDrawValues(true)
                sliceSpace = resources.getDimension(com.intuit.sdp.R.dimen._2sdp) / resources.displayMetrics.density
                valueTextSize = resources.getDimension(com.intuit.ssp.R.dimen._9ssp) / resources.displayMetrics.density
                valueTextColor = ContextCompat.getColor(requireContext(), R.color.btn_color)
            }

            val pieData = PieData(dataSet)
            pieChart.data = pieData

            val formattedTotal = if (totalCalls >= 1000) {
                "%.2fK".format(totalCalls / 1000f)
            } else {
                totalCalls.toString()
            }

            pieChart.centerText = formattedTotal
            pieChart.setCenterTextSize(resources.getDimension(com.intuit.ssp.R.dimen._13ssp) / resources.displayMetrics.density)
            pieChart.setCenterTextColor(ContextCompat.getColor(requireContext(), R.color.btn_color))
            pieChart.setCenterTextTypeface(Typeface.DEFAULT_BOLD)

            pieChart.invalidate()
            binding.chartContainer.addView(pieChart)
        }
    }

    private fun updateMostCalledNumber(callLogs: List<DailyLogs>) {
        val numberCallCount = mutableMapOf<String, Pair<String, Int>>()

        callLogs.forEach { dailyLog ->
            dailyLog.calls.forEach { call ->
                val number = call.number
                val name = call.name
                if (number.isNotEmpty()) {
                    val currentCount = numberCallCount[number]?.second ?: 0
                    numberCallCount[number] = Pair(name, currentCount + 1)
                }
            }
        }

        val mostCalled = numberCallCount.maxByOrNull { it.value.second }
        if (mostCalled != null) {
            binding.apply {
                tvFirstText.text = mostCalled.value.first.first().toString().uppercase()
                tvMostCallNumberName.text = mostCalled.value.first
                tvMostCallNumber.text = mostCalled.key
                tvMostCallNumberDigit.text = "${mostCalled.value.second}x"
            }
        } else {
            binding.apply {
                tvFirstText.text = "?"
                tvMostCallNumberName.text = "Unknown"
                tvMostCallNumber.text = "-"
                tvMostCallNumberDigit.text = "0x"
            }
        }
    }

    private fun formatDuration(seconds: Long): String {
        val minutes = TimeUnit.SECONDS.toMinutes(seconds)
        val remainingSeconds = seconds - TimeUnit.MINUTES.toSeconds(minutes)
        return "$minutes min $remainingSeconds s"
    }

    private fun updateMostReceivedNumber(callLogs: List<DailyLogs>) {
        val numberCallCount = mutableMapOf<String, Pair<String, Int>>()

        callLogs.forEach { dailyLog ->
            dailyLog.calls.forEach { call ->
                if (call.callType == CallTypes.INCOMING) {
                    val number = call.number
                    val name = call.name
                    if (number.isNotEmpty()) {
                        val currentCount = numberCallCount[number]?.second ?: 0
                        numberCallCount[number] = Pair(name, currentCount + 1)
                    }
                }
            }
        }

        val mostReceived = numberCallCount.maxByOrNull { it.value.second }
        if (mostReceived != null) {
            binding.apply {
                tvFirstTextMostReceivedNum.text = mostReceived.value.first.first().toString().uppercase()
                tvMostReceivedNumName.text = mostReceived.value.first
                tvMostReceivedNum.text = mostReceived.key
                tvMostReceivedNumDigit.text = "${mostReceived.value.second}x"
            }
        } else {
            binding.apply {
                tvFirstTextMostReceivedNum.text = "?"
                tvMostReceivedNumName.text = "Unknown"
                tvMostReceivedNum.text = "-"
                tvMostReceivedNumDigit.text = "0x"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}