package io.logger.adapter

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import io.logger.R
import io.logger.databinding.ItemDailyLogsBinding
import io.logger.databinding.ItemLogBinding
import io.logger.model.DailyLogs
import io.logger.utility.Constants

class DailyLogsAdapter : RecyclerView.Adapter<DailyLogsAdapter.DailyLogsViewHolder>() {

    class DailyLogsViewHolder(val binding: ItemDailyLogsBinding) : RecyclerView.ViewHolder(binding.root)

    // DiffUtil callback for DailyLogs
    private val diffCallback = object : DiffUtil.ItemCallback<DailyLogs>() {
        override fun areItemsTheSame(oldItem: DailyLogs, newItem: DailyLogs): Boolean {
            return oldItem.date == newItem.date // Use date as unique identifier
        }

        override fun areContentsTheSame(oldItem: DailyLogs, newItem: DailyLogs): Boolean {
            return oldItem == newItem // Compare entire object for content equality
        }
    }

    // AsyncListDiffer for efficient list updates
    private val differ = AsyncListDiffer(this, diffCallback)

    // Submit new list to update RecyclerView
    fun submitList(newList: List<DailyLogs>) {
        differ.submitList(newList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DailyLogsViewHolder {
        val binding = ItemDailyLogsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DailyLogsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DailyLogsViewHolder, position: Int) {
        val dailyLog = differ.currentList[position]
        with(holder.binding) {
            textDate.text = dailyLog.date

            // Set up the child RecyclerView
            childRecyclerView.layoutManager = LinearLayoutManager(holder.itemView.context)
            childRecyclerView.adapter = LogsAdapter().apply {
                submitList(dailyLog.calls)
            }
            childRecyclerView.setHasFixedSize(true)
        }
    }

    override fun getItemCount(): Int = differ.currentList.size
}

class LogsAdapter : RecyclerView.Adapter<LogsAdapter.LogsViewHolder>() {

    class LogsViewHolder(val binding: ItemLogBinding) : RecyclerView.ViewHolder(binding.root)

    // DiffUtil callback for DailyLogs.Call
    private val diffCallback = object : DiffUtil.ItemCallback<DailyLogs.Call>() {
        override fun areItemsTheSame(oldItem: DailyLogs.Call, newItem: DailyLogs.Call): Boolean {
            // Use a combination of number and time as a unique identifier
            return oldItem.number == newItem.number && oldItem.time == newItem.time
        }

        override fun areContentsTheSame(oldItem: DailyLogs.Call, newItem: DailyLogs.Call): Boolean {
            return oldItem == newItem // Compare entire object for content equality
        }
    }

    // AsyncListDiffer for efficient list updates
    private val differ = AsyncListDiffer(this, diffCallback)

    // Submit new list to update RecyclerView
    fun submitList(newList: List<DailyLogs.Call>) {
        differ.submitList(newList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogsViewHolder {
        val binding = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LogsViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LogsViewHolder, position: Int) {
        val call = differ.currentList[position]
        val totalItems = differ.currentList.size
        with(holder.binding) {
            tvName.text = call.name
            tvNumber.text = call.number
            tvTime.text = call.time

            lineView.visibility = if (totalItems == 1) {
                View.GONE
            } else {
                if ((position+1) < totalItems)
                    View.VISIBLE
                else
                    View.GONE
            }

            when (call.callType) {
                Constants.CALL_TYPE_INCOMING -> {
                    clCall.setBackgroundResource(R.drawable.item_iv_call_incoming)
                    ivCallIcon.setImageResource(R.drawable.arrow_bottom_left)
                }
                Constants.CALL_TYPE_OUTGOING -> {
                    clCall.setBackgroundResource(R.drawable.item_iv_call_outgoing)
                    ivCallIcon.setImageResource(R.drawable.arrow_top_right)
                }
                Constants.CALL_TYPE_MISSED_CALL -> {
                    clCall.setBackgroundResource(R.drawable.item_iv_missed_call)
                    ivCallIcon.setImageResource(R.drawable.ic_call)
                }
                else -> {
                    clCall.setBackgroundResource(R.drawable.item_iv_call_incoming)
                    ivCallIcon.setImageResource(R.drawable.phone)
                }
            }

            // Set up SwipeLayout
            swipeLayout.setShowMode(SwipeLayout.ShowMode.PullOut)
            swipeLayout.isSwipeEnabled = true

            // Register left_view and right_view for swipe directions
            swipeLayout.addDrag(SwipeLayout.DragEdge.Left, leftView)
            swipeLayout.addDrag(SwipeLayout.DragEdge.Right, rightView)

            // Ensure swipe views match the height of surface_view
            leftView.minimumHeight = surfaceView.height
            rightView.minimumHeight = surfaceView.height

            // Handle clicks on swipe views Share Logs
            leftView.setOnClickListener {
                val uri = Uri.parse("tel:${call.number}")
                val intent = Intent(Intent.ACTION_DIAL, uri)
                it.context.startActivity(intent)
                swipeLayout.close(true)
            }

            smsWrapper.setOnClickListener {
                val uri = Uri.parse("sms:${call.number}")
                val intent = Intent(Intent.ACTION_SENDTO, uri)
                it.context.startActivity(intent)
                swipeLayout.close(true)
            }

            whatsappWrapper.setOnClickListener {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=${call.number}")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                it.context.startActivity(intent)
                swipeLayout.close(true)
            }

            // Add swipe listener to handle open/close events
            swipeLayout.addSwipeListener(object : SwipeLayout.SwipeListener {
                override fun onClose(layout: SwipeLayout?) {
                    // Swipe closed
                }

                override fun onUpdate(layout: SwipeLayout?, leftOffset: Int, topOffset: Int) {
                    // Swipe in progress
                }

                override fun onStartOpen(layout: SwipeLayout?) {
                    // Swipe starting to open
                }

                override fun onOpen(layout: SwipeLayout?) {
                    // Swipe fully opened
                }

                override fun onStartClose(layout: SwipeLayout?) {
                    // Swipe starting to close
                }

                override fun onHandRelease(layout: SwipeLayout?, xvel: Float, yvel: Float) {
                    // Hand released after swipe
                }
            })

            // Ensure the swipe views are measured after the surface view
            surfaceView.post {
                leftView.minimumHeight = surfaceView.height
                rightView.minimumHeight = surfaceView.height
            }
        }
    }

    override fun getItemCount(): Int = differ.currentList.size
}