package io.logger.user.dialog

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch
import io.logger.R
import java.text.SimpleDateFormat
import java.util.*

interface OnFilterAppliedListener {
    fun onFiltersApplied(
        callTypes: Set<String>,
        phoneNumber: String?,
        startDate: Long?,
        endDate: Long?,
        dateRangeType: String?,
        isFilterApplied: Boolean = false
    )
}

class FilterBottomSheetFragment : BottomSheetDialogFragment() {

    private lateinit var switchSpecificNumber: MaterialSwitch
    private lateinit var editTextMobileNumber: EditText
    private lateinit var spinnerDateRange: Spinner
    private lateinit var layoutCustomDate: LinearLayout
    private lateinit var tvStartDate: TextView
    private lateinit var tvEndDate: TextView
    private lateinit var btnReset: Button
    private lateinit var btnApply: Button
    private lateinit var chipGroupCallType: ChipGroup
    private var isFilterApplied: Boolean = true

    private var selectedCallTypes = mutableSetOf<String>()
    private val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
    private var filterListener: OnFilterAppliedListener? = null

    fun setFilterAppliedListener(listener: OnFilterAppliedListener) {
        this.filterListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_filter, container, false)

        // Initialize views
        switchSpecificNumber = view.findViewById(R.id.switch_specific_number)
        editTextMobileNumber = view.findViewById(R.id.edit_text_mobile_number)
        spinnerDateRange = view.findViewById(R.id.spinner_date_range)
        layoutCustomDate = view.findViewById(R.id.layout_custom_date)
        tvStartDate = view.findViewById(R.id.tv_start_date)
        tvEndDate = view.findViewById(R.id.tv_end_date)
        btnReset = view.findViewById(R.id.btn_reset)
        btnApply = view.findViewById(R.id.btn_apply)
        chipGroupCallType = view.findViewById(R.id.chip_group_call_type)

        // Set up toggle for Specific Phone Number
        switchSpecificNumber.setOnCheckedChangeListener { _, isChecked ->
            editTextMobileNumber.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Set up call type chips
        val chips = listOf(
            view.findViewById<Chip>(R.id.chip_missed),
            view.findViewById<Chip>(R.id.chip_rejected),
            view.findViewById<Chip>(R.id.chip_incoming),
            view.findViewById<Chip>(R.id.chip_outgoing),
            view.findViewById<Chip>(R.id.chip_answered_externally),
            view.findViewById<Chip>(R.id.chip_blocked),
            view.findViewById<Chip>(R.id.chip_wifi_incoming),
            view.findViewById<Chip>(R.id.chip_wifi_outgoing),
            view.findViewById<Chip>(R.id.chip_voice_mail),
            view.findViewById<Chip>(R.id.chip_unknown)
        )

        // Pre-populate chips with last selected call types
        val lastCallTypes = arguments?.getStringArrayList("callTypes")?.toSet() ?: emptySet()
        chips.forEach { chip ->
            chip.isChecked = lastCallTypes.contains(chip.text.toString())
            if (chip.isChecked) {
                selectedCallTypes.add(chip.text.toString())
            }
        }

        chips.forEach { chip ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                val callType = chip.text.toString()
                if (isChecked) {
                    selectedCallTypes.add(callType)
                } else {
                    selectedCallTypes.remove(callType)
                }
            }
        }

        // Set up date range spinner with custom adapter
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.date_range_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerDateRange.adapter = adapter
        }

        spinnerDateRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedOption = parent.getItemAtPosition(position).toString()
                layoutCustomDate.visibility = if (selectedOption == "Custom") View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Pre-populate phone number
        val lastPhoneNumber = arguments?.getString("phoneNumber")
        if (lastPhoneNumber != null) {
            switchSpecificNumber.isChecked = true
            editTextMobileNumber.visibility = View.VISIBLE
            editTextMobileNumber.setText(lastPhoneNumber)
        }

        // Pre-populate date fields and spinner
        val lastStartDate = arguments?.getLong("startDate")
        val lastEndDate = arguments?.getLong("endDate")
        val lastDateRangeType = arguments?.getString("dateRangeType")
        Log.d("TESTING", "lastStartDate: $lastStartDate, lastEndDate: $lastEndDate, lastDateRangeType: $lastDateRangeType")
        if (lastStartDate != 0L || lastEndDate != 0L) {
            Log.d("TESTING","------------------------")
            val startDateStr = lastStartDate?.let { dateFormat.format(Date(it)) } ?: "Start Date"
            val endDateStr = lastEndDate?.let { dateFormat.format(Date(it)) } ?: "End Date"
            tvStartDate.text = startDateStr
            tvEndDate.text = endDateStr

            // Set spinner based on lastDateRangeType
            if (lastDateRangeType != null && lastDateRangeType.contains("Custom")) {
                spinnerDateRange.setSelection(7)
                layoutCustomDate.visibility = if (lastDateRangeType == "Custom") View.VISIBLE else View.GONE
            } else {
                // Fallback to approximate matching if dateRangeType is not provided
                val calendar = Calendar.getInstance()
                if (lastStartDate != null && lastEndDate != null) {
                    calendar.timeInMillis = lastStartDate
                    val today = Calendar.getInstance()
                    when {
                        isSameDay(today, calendar) && lastEndDate - lastStartDate <= 24 * 60 * 60 * 1000 -> spinnerDateRange.setSelection(1) // Today
                        isSameDay(today.apply { add(Calendar.DAY_OF_YEAR, -1) }, calendar) && lastEndDate - lastStartDate <= 24 * 60 * 60 * 1000 -> spinnerDateRange.setSelection(2) // Yesterday
                        isSameMonth(today, calendar) -> spinnerDateRange.setSelection(3) // This Month
                        isSameMonth(today.apply { add(Calendar.MONTH, -1) }, calendar) -> spinnerDateRange.setSelection(4) // Past Month
                        isSameYear(today, calendar) -> spinnerDateRange.setSelection(5) // This Year
                        isSameYear(today.apply { add(Calendar.YEAR, -1) }, calendar) -> spinnerDateRange.setSelection(6) // Past Year
                        else -> {
                            spinnerDateRange.setSelection(7) // Custom
                            layoutCustomDate.visibility = View.VISIBLE
                        }
                    }
                }
            }
        } else {
            spinnerDateRange.setSelection(0)
            layoutCustomDate.visibility = View.GONE
        }

        // Set up date pickers
        tvStartDate.setOnClickListener { showDatePicker(tvStartDate) }
        tvEndDate.setOnClickListener { showDatePicker(tvEndDate) }

        // Reset button
        btnReset.setOnClickListener {
            isFilterApplied = false
            switchSpecificNumber.isChecked = false
            editTextMobileNumber.text.clear()
            selectedCallTypes = mutableSetOf(
                "Missed", "Rejected", "Incoming", "OutGoing", "Answered Externally",
                "Blocked", "Wifi Incoming", "Wifi Outgoing", "Voice Mail", "Unknown"
            )
            chips.forEach { it.isChecked = true }
            spinnerDateRange.setSelection(0)
            layoutCustomDate.visibility = View.GONE
            tvStartDate.text = "Start Date"
            tvEndDate.text = "End Date"

            filterListener?.onFiltersApplied(selectedCallTypes, null, null, null,
                null, isFilterApplied)
            dismiss()
        }

        // Apply button
        btnApply.setOnClickListener {
            val phoneNumber = if (switchSpecificNumber.isChecked) editTextMobileNumber.text.toString() else null
            val selectedDateRange = spinnerDateRange.selectedItem.toString()

            val (startDate, endDate) = when (selectedDateRange) {
                "Today" -> {
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val start = calendar.timeInMillis
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val end = calendar.timeInMillis
                    Pair(start, end)
                }
                "Yesterday" -> {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.DAY_OF_YEAR, -1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val start = calendar.timeInMillis
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val end = calendar.timeInMillis
                    Pair(start, end)
                }
                "This Month" -> {
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val start = calendar.timeInMillis
                    calendar.add(Calendar.MONTH, 1)
                    val end = calendar.timeInMillis
                    Pair(start, end)
                }
                "Past Month" -> {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.MONTH, -1)
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val start = calendar.timeInMillis
                    calendar.add(Calendar.MONTH, 1)
                    val end = calendar.timeInMillis
                    Pair(start, end)
                }
                "This Year" -> {
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.DAY_OF_YEAR, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val start = calendar.timeInMillis
                    calendar.add(Calendar.YEAR, 1)
                    val end = calendar.timeInMillis
                    Pair(start, end)
                }
                "Past Year" -> {
                    val calendar = Calendar.getInstance()
                    calendar.add(Calendar.YEAR, -1)
                    calendar.set(Calendar.DAY_OF_YEAR, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val start = calendar.timeInMillis
                    calendar.add(Calendar.YEAR, 1)
                    val end = calendar.timeInMillis
                    Pair(start, end)
                }
                "All Time" -> Pair(null, null)
                "Custom" -> {
                    val start = tvStartDate.text.toString().takeIf { it != "Start Date" }?.let {
                        dateFormat.parse(it)?.time
                    }
                    val end = tvEndDate.text.toString().takeIf { it != "End Date" }?.let {
                        dateFormat.parse(it)?.time
                    }
                    Pair(start, end)
                }
                else -> Pair(null, null)
            }

            filterListener?.onFiltersApplied(selectedCallTypes, phoneNumber, startDate, endDate,
                spinnerDateRange.selectedItem.toString(), isFilterApplied)
            dismiss()
        }

        return view
    }

    private fun showDatePicker(textView: TextView) {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.show(parentFragmentManager, "DATE_PICKER")

        datePicker.addOnPositiveButtonClickListener { selection ->
            val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault())
            val selectedDate = Date(selection)
            textView.text = dateFormat.format(selectedDate)
        }
    }

    // Helper functions for date comparison
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isSameMonth(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)
    }

    private fun isSameYear(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
    }
}