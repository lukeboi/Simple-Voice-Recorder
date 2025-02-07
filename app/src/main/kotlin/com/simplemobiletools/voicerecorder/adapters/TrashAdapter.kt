package com.simplemobiletools.voicerecorder.adapters

import android.view.*
import android.widget.PopupMenu
import android.widget.TextView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.voicerecorder.R
import com.simplemobiletools.voicerecorder.activities.SimpleActivity
import com.simplemobiletools.voicerecorder.extensions.deleteRecordings
import com.simplemobiletools.voicerecorder.extensions.restoreRecordings
import com.simplemobiletools.voicerecorder.interfaces.RefreshRecordingsListener
import com.simplemobiletools.voicerecorder.models.Events
import com.simplemobiletools.voicerecorder.models.Recording
import kotlinx.android.synthetic.main.item_recording.view.*
import org.greenrobot.eventbus.EventBus

class TrashAdapter(
    activity: SimpleActivity,
    var recordings: ArrayList<Recording>,
    val refreshListener: RefreshRecordingsListener,
    recyclerView: MyRecyclerView
) :
    MyRecyclerViewAdapter(activity, recyclerView, {}), RecyclerViewFastScroller.OnPopupTextUpdate {

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_trash

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_restore -> restoreRecordings()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = recordings.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = recordings.getOrNull(position)?.id

    override fun getItemKeyPosition(key: Int) = recordings.indexOfFirst { it.id == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_recording, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recording = recordings[position]
        holder.bindView(recording, true, true) { itemView, layoutPosition ->
            setupView(itemView, recording)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = recordings.size

    private fun getItemWithKey(key: Int): Recording? = recordings.firstOrNull { it.id == key }

    fun updateItems(newItems: ArrayList<Recording>) {
        if (newItems.hashCode() != recordings.hashCode()) {
            recordings = newItems
            notifyDataSetChanged()
            finishActMode()
        }
    }

    private fun restoreRecordings() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val recordingsToRestore = recordings.filter { selectedKeys.contains(it.id) } as ArrayList<Recording>
        val positions = getSelectedItemPositions()

        activity.restoreRecordings(recordingsToRestore) { success ->
            if (success) {
                doDeleteAnimation(recordingsToRestore, positions)
                EventBus.getDefault().post(Events.RecordingTrashUpdated())
            }
        }
    }

    private fun askConfirmDelete() {
        val itemsCnt = selectedKeys.size
        val firstItem = getSelectedItems().firstOrNull() ?: return
        val items = if (itemsCnt == 1) {
            "\"${firstItem.title}\""
        } else {
            resources.getQuantityString(R.plurals.delete_recordings, itemsCnt, itemsCnt)
        }

        val baseString = R.string.delete_recordings_confirmation
        val question = String.format(resources.getString(baseString), items)

        ConfirmationDialog(activity, question) {
            ensureBackgroundThread {
                deleteMediaStoreRecordings()
            }
        }
    }

    private fun deleteMediaStoreRecordings() {
        if (selectedKeys.isEmpty()) {
            return
        }

        val recordingsToRemove = recordings.filter { selectedKeys.contains(it.id) } as ArrayList<Recording>
        val positions = getSelectedItemPositions()

        activity.deleteRecordings(recordingsToRemove) { success ->
            if (success) {
                doDeleteAnimation(recordingsToRemove, positions)
            }
        }
    }

    private fun doDeleteAnimation(recordingsToRemove: ArrayList<Recording>, positions: ArrayList<Int>) {
        recordings.removeAll(recordingsToRemove.toSet())
        activity.runOnUiThread {
            if (recordings.isEmpty()) {
                refreshListener.refreshRecordings()
                finishActMode()
            } else {
                positions.sortDescending()
                removeSelectedItems(positions)
            }
        }
    }

    private fun getSelectedItems() = recordings.filter { selectedKeys.contains(it.id) } as ArrayList<Recording>

    private fun setupView(view: View, recording: Recording) {
        view.apply {
            setupViewBackground(activity)
            recording_frame?.isSelected = selectedKeys.contains(recording.id)

            arrayListOf<TextView>(recording_title, recording_date, recording_duration, recording_size).forEach {
                it.setTextColor(textColor)
            }

            recording_title.text = recording.title
            recording_date.text = recording.timestamp.formatDate(context)
            recording_duration.text = recording.duration.getFormattedDuration()
            recording_size.text = recording.size.formatSize()

            overflow_menu_icon.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }

            overflow_menu_icon.setOnClickListener {
                showPopupMenu(overflow_menu_anchor, recording)
            }
        }
    }

    override fun onChange(position: Int) = recordings.getOrNull(position)?.title ?: ""

    private fun showPopupMenu(view: View, recording: Recording) {
        if (selectedKeys.isNotEmpty()) {
            selectedKeys.clear()
            notifyDataSetChanged()
        }

        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(getActionMenuId())
            menu.findItem(R.id.cab_select_all).isVisible = false
            menu.findItem(R.id.cab_restore).title = resources.getString(R.string.restore_this_file)
            setOnMenuItemClickListener { item ->
                val recordingId = recording.id
                when (item.itemId) {
                    R.id.cab_restore -> {
                        executeItemMenuOperation(recordingId, removeAfterCallback = false) {
                            restoreRecordings()
                        }
                    }

                    R.id.cab_delete -> {
                        executeItemMenuOperation(recordingId, removeAfterCallback = false) {
                            askConfirmDelete()
                        }
                    }
                }

                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(callId: Int, removeAfterCallback: Boolean = true, callback: () -> Unit) {
        selectedKeys.add(callId)
        callback()
        if (removeAfterCallback) {
            selectedKeys.remove(callId)
        }
    }
}
