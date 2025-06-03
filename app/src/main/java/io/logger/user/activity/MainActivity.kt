package io.logger.user.activity

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.logger.R
import io.logger.databinding.ActivityMainBinding
import io.logger.user.dialog.FilterBottomSheetFragment
import io.logger.user.dialog.OnFilterAppliedListener
import io.logger.user.fragment.AnalyticsFragment
import io.logger.user.fragment.LogsFragment
import io.logger.utility.CallTypes
import io.logger.utility.Utils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), OnFilterAppliedListener {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!
    private lateinit var navController: NavController
    private var currentSelectedBg: View? = null
    private var pendingCsvContent: StringBuilder? = null

    private var onCallLogPermissionGranted: (() -> Unit)? = null
    private var onStoragePermissionGranted: (() -> Unit)? = null

    private val requestCallLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onCallLogPermissionGranted?.invoke()
        } else {
            showPermissionDialog()
        }
    }

    private val requestStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStoragePermissionGranted?.invoke()
        } else {
            showStoragePermissionDialog()
        }
    }

    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        onCallLogPermissionGranted?.let {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                == PackageManager.PERMISSION_GRANTED
            ) {
                it.invoke()
            }
        }
        onStoragePermissionGranted?.let {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED
            ) {
                it.invoke()
            }
        }
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { saveUri ->
            try {
                pendingCsvContent?.let { csvContent ->
                    contentResolver.openOutputStream(saveUri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(csvContent.toString())
                            writer.flush()
                        }
                    }
                    Toast.makeText(this, "CSV saved successfully", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Toast.makeText(this, "Failed to save CSV file", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error saving CSV file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            pendingCsvContent = null
        }
    }

    private var lastCallTypes: Set<String> = CallTypes.ALL_TYPES
    private var lastPhoneNumber: String? = null
    private var lastStartDate: Long? = null
    private var lastEndDate: Long? = null
    private var lastDateRangeType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
                as NavHostFragment
        navController = navHostFragment.navController

        setUpNotificationStyle()
        setupBottomNavigation()
        setupAppBarIcons()
    }

    private fun setupAppBarIcons() {
        binding.ivDownload.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
                ?.childFragmentManager?.fragments?.firstOrNull()
            if (currentFragment is LogsFragment) {
                requestStoragePermission {
                    generateAndSaveCsv()
                }
            } else {
                Toast.makeText(this, "Download not available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.ivShare.setOnClickListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
                ?.childFragmentManager?.fragments?.firstOrNull()
            if (currentFragment is LogsFragment) {
                requestStoragePermission {
                    shareCsv()
                }
            } else {
                Toast.makeText(this, "Share not available", Toast.LENGTH_SHORT).show()
            }
        }

        binding.ivFilter.setOnClickListener {
            val bottomSheet = FilterBottomSheetFragment().apply {
                setFilterAppliedListener(this@MainActivity)
                arguments = Bundle().apply {
                    putStringArrayList("callTypes", ArrayList(lastCallTypes))
                    putString("phoneNumber", lastPhoneNumber)
                    putLong("startDate", lastStartDate ?: 0L)
                    putLong("endDate", lastEndDate ?: 0L)
                    putString("dateRangeType", lastDateRangeType)
                }
            }
            bottomSheet.show(supportFragmentManager, bottomSheet.tag)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.ivDownload.visibility = View.GONE
            binding.ivShare.visibility = View.GONE
            binding.ivFilter.visibility = View.GONE

            when (destination.id) {
                R.id.logsFragment -> {
                    binding.filterDot.visibility = View.GONE
                    binding.ivDownload.visibility = View.VISIBLE
                    binding.ivShare.visibility = View.VISIBLE
                    binding.ivFilter.visibility = View.VISIBLE
                }
                R.id.analyticsFragment -> {
                    binding.filterDot.visibility = View.GONE
                    binding.ivFilter.visibility = View.VISIBLE
                }
                R.id.settingsFragment -> {
                    binding.filterDot.visibility = View.GONE
                    binding.ivFilter.visibility = View.GONE
                }
                R.id.aboutFragment -> {
                    binding.filterDot.visibility = View.GONE
                    binding.ivFilter.visibility = View.GONE
                }
            }
        }
    }

    fun requestCallLogPermission(onGranted: () -> Unit) {
        onCallLogPermissionGranted = onGranted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED -> {
                    onGranted()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG) -> {
                    requestCallLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                }
                else -> {
                    requestCallLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                }
            }
        } else {
            onGranted()
        }
    }

    private fun requestStoragePermission(onGranted: () -> Unit) {
        onStoragePermissionGranted = onGranted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onGranted()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            when {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
                    onGranted()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else -> {
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        } else {
            onGranted()
        }
    }

    private fun showPermissionDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onCallLogPermissionGranted?.invoke()
            return
        }
        val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)
        if (showRationale) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Permission Required")
                .setMessage("This app needs access to your call logs. Please grant permission to continue.")
                .setCancelable(false)
                .setPositiveButton("Grant") { _, _ ->
                    requestCallLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    finish()
                }
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("Permission Permanently Denied")
                .setMessage("You have permanently denied the call log permission. Please enable it manually from settings.")
                .setCancelable(false)
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    appSettingsLauncher.launch(intent)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    finish()
                }
                .show()
        }
    }

    private fun showStoragePermissionDialog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            onStoragePermissionGranted?.invoke()
            return
        }
        val showRationale = shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (showRationale) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Storage Permission Required")
                .setMessage("This app needs access to storage to save the CSV file. Please grant permission to continue.")
                .setCancelable(false)
                .setPositiveButton("Grant") { _, _ ->
                    requestStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
                .show()
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("Storage Permission Permanently Denied")
                .setMessage("You have permanently denied the storage permission. Please enable it manually from settings.")
                .setCancelable(false)
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    appSettingsLauncher.launch(intent)
                }
                .setNegativeButton("Cancel") { _, _ ->
                    Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun generateAndSaveCsv() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
            ?.childFragmentManager?.fragments?.firstOrNull()
        if (currentFragment !is LogsFragment) return

        val dailyLogsList = currentFragment.fetchCallLogs()

        val csvContent = StringBuilder()
        csvContent.append("name,duration,number,call_type,timestamp\n")

        dailyLogsList.forEach { dailyLog ->
            dailyLog.calls.forEach { call ->
                val name = call.name?.replace(",", "") ?: ""
                val duration = call.duration
                val number = call.number?.replace(",", "") ?: ""
                val callType = call.callType
                val timestamp = call.time

                csvContent.append("$name,$duration,$number,$callType,$timestamp\n")
            }
        }

        pendingCsvContent = csvContent
        val fileName = "logger-${SimpleDateFormat("ddMMyy-HHmmss", Locale.getDefault()).format(Date())}.csv"
        createDocumentLauncher.launch(fileName)
    }

    private fun shareCsv() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
            ?.childFragmentManager?.fragments?.firstOrNull()
        if (currentFragment !is LogsFragment) return

        val dailyLogsList = currentFragment.fetchCallLogs()

        val csvContent = StringBuilder()
        csvContent.append("name,duration,number,call_type,timestamp\n")

        dailyLogsList.forEach { dailyLog ->
            dailyLog.calls.forEach { call ->
                val name = call.name?.replace(",", "") ?: ""
                val duration = call.duration
                val number = call.number?.replace(",", "") ?: ""
                val callType = call.callType
                val timestamp = call.time

                csvContent.append("$name,$duration,$number,$callType,$timestamp\n")
            }
        }

        val fileName = "logger-${SimpleDateFormat("ddMMyy-HHmmss", Locale.getDefault()).format(Date())}.csv"
        val file = File(cacheDir, fileName)
        FileOutputStream(file).use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(csvContent.toString())
                writer.flush()
            }
        }

        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share CSV File"))
    }

    private fun setupBottomNavigation() {
        binding.logsFragment.setOnClickListener {

            if (navController.currentDestination?.id != R.id.logsFragment) {
                lastCallTypes = CallTypes.ALL_TYPES
                lastPhoneNumber = null
                lastStartDate = null
                lastEndDate = null
                lastDateRangeType = null
                binding.filterDot.visibility = View.GONE

                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
                    ?.childFragmentManager?.fragments?.firstOrNull()
                if (currentFragment is LogsFragment) {
                    currentFragment.applyFilters(lastCallTypes, lastPhoneNumber, lastStartDate, lastEndDate)
                }

                val navOptions = NavOptions.Builder()
                    .setPopUpTo(R.id.logsFragment, inclusive = false)
                    .setLaunchSingleTop(true)
                    .setEnterAnim(R.anim.fade_in)
                    .setExitAnim(R.anim.fade_out)
                    .build()

                navController.navigate(R.id.logsFragment, null, navOptions)
                selectTab(binding.bgLogs)
            }
        }

        binding.analyticsFragment.setOnClickListener {

            if (navController.currentDestination?.id != R.id.analyticsFragment) {
                lastCallTypes = CallTypes.ALL_TYPES
                lastPhoneNumber = null
                lastStartDate = null
                lastEndDate = null
                lastDateRangeType = null
                binding.filterDot.visibility = View.GONE

                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
                    ?.childFragmentManager?.fragments?.firstOrNull()
                if (currentFragment is AnalyticsFragment) {
                    currentFragment.applyFilters(lastCallTypes, lastPhoneNumber, lastStartDate, lastEndDate)
                }

                selectTab(binding.bgAnalytics)
                navController.navigate(R.id.analyticsFragment)
            }
        }

        binding.settingsFragment.setOnClickListener {
            selectTab(binding.bgSettings)
            navController.navigate(R.id.settingsFragment)
        }

        binding.aboutFragment.setOnClickListener {
            selectTab(binding.bgAbout)
            navController.navigate(R.id.aboutFragment)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.logsFragment -> selectTab(binding.bgLogs)
                R.id.analyticsFragment -> selectTab(binding.bgAnalytics)
                R.id.settingsFragment -> selectTab(binding.bgSettings)
                R.id.aboutFragment -> selectTab(binding.bgAbout)
            }
        }
    }

    private fun selectTab(bgView: View) {
        currentSelectedBg?.isSelected = false
        bgView.isSelected = true
        currentSelectedBg = bgView
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun setUpNotificationStyle() {
        val isDarkTheme = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        Utils.setStatusBarColor(this, isDarkTheme)
    }

    override fun onDestroy() {
        super.onDestroy()
        lastCallTypes = emptySet()
        lastPhoneNumber = null
        lastStartDate = null
        lastEndDate = null
        lastDateRangeType = null
        _binding = null
        pendingCsvContent = null
    }

    override fun onFiltersApplied(
        callTypes: Set<String>,
        phoneNumber: String?,
        startDate: Long?,
        endDate: Long?,
        dateRangeType: String?,
        isFilterApplied: Boolean
    ) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView)
            ?.childFragmentManager?.fragments?.firstOrNull()
        when (currentFragment) {
            is LogsFragment -> {
                Log.d(
                    "MYTESTING",
                    "MainActivity => Applying filters to LogsFragment: selectedCallTypes: $callTypes, phoneNumber: $phoneNumber, startDate: $startDate, endDate: $endDate"
                )
                currentFragment.applyFilters(callTypes, phoneNumber, startDate, endDate)
            }
            is AnalyticsFragment -> {
                Log.d(
                    "MYTESTING",
                    "MainActivity => Applying filters to AnalyticsFragment: selectedCallTypes: $callTypes, phoneNumber: $phoneNumber, startDate: $startDate, endDate: $endDate"
                )
                currentFragment.applyFilters(callTypes, phoneNumber, startDate, endDate)
            }
        }
        lastCallTypes = callTypes
        lastPhoneNumber = phoneNumber
        lastStartDate = startDate
        lastEndDate = endDate
        lastDateRangeType = dateRangeType
        binding.filterDot.visibility = if (isFilterApplied) View.VISIBLE else View.GONE
    }
}