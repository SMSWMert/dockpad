package com.dockpad.oneui7

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AudioSessionAdapter(
    private val onVolumeChanged: (AudioSessionItem, Float, Boolean) -> Unit
) : RecyclerView.Adapter<AudioSessionAdapter.VH>() {

    private var items: List<AudioSessionItem> = emptyList()

    fun submitList(newItems: List<AudioSessionItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvAudioAppName)
        val seekVol: SeekBar = view.findViewById(R.id.seekAudioVol)
        val btnMute: ImageView = view.findViewById(R.id.btnAudioMute)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_audio_session, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.seekVol.setOnSeekBarChangeListener(null)
        holder.seekVol.progress = (item.volume * 100).toInt().coerceIn(0, 100)
        holder.btnMute.setImageResource(if (item.mute) R.drawable.ic_volume_off else R.drawable.ic_volume_on)

        holder.seekVol.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    onVolumeChanged(item, progress / 100f, item.mute)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        holder.btnMute.setOnClickListener {
            onVolumeChanged(item, item.volume, !item.mute)
        }
    }
}

class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.VH>() {

    private var items: List<NotificationItem> = emptyList()
    private var privacyMode: Boolean = false

    fun submitList(newItems: List<NotificationItem>, privacy: Boolean) {
        items = newItems
        privacyMode = privacy
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSender: TextView = view.findViewById(R.id.tvNotifSender)
        val tvTime: TextView = view.findViewById(R.id.tvNotifTime)
        val tvSummary: TextView = view.findViewById(R.id.tvNotifSummary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvSender.text = item.sender
        holder.tvTime.text = item.time
        holder.tvSummary.text = if (privacyMode) "••••••••••••••" else item.summary
    }
}

class ProcessAdapter(
    private val onKillClick: (ProcessItem) -> Unit
) : RecyclerView.Adapter<ProcessAdapter.VH>() {

    private var items: List<ProcessItem> = emptyList()

    fun submitList(newItems: List<ProcessItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvProcName)
        val tvRam: TextView = view.findViewById(R.id.tvProcRam)
        val btnKill: TextView = view.findViewById(R.id.btnKillProc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_process, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.name
        holder.tvRam.text = if (item.ramGb >= 1.0) {
            String.format("%.1f GB RAM", item.ramGb)
        } else {
            "${item.ramMb} MB RAM"
        }
        holder.btnKill.setOnClickListener { onKillClick(item) }
    }
}

class ClipboardAdapter(
    private val onPasteClick: (ClipboardItem) -> Unit
) : RecyclerView.Adapter<ClipboardAdapter.VH>() {

    private var items: List<ClipboardItem> = emptyList()

    fun submitList(newItems: List<ClipboardItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvPreview: TextView = view.findViewById(R.id.tvClipPreview)
        val tvTime: TextView = view.findViewById(R.id.tvClipTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_clipboard, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvPreview.text = item.preview
        holder.tvTime.text = item.time
        holder.itemView.setOnClickListener { onPasteClick(item) }
    }
}

class PromptAdapter(
    private val onPasteClick: (PromptItem) -> Unit
) : RecyclerView.Adapter<PromptAdapter.VH>() {

    private var items: List<PromptItem> = emptyList()

    fun submitList(newItems: List<PromptItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvPromptTitle)
        val tvDesc: TextView = view.findViewById(R.id.tvPromptDesc)
        val btnPaste: TextView = view.findViewById(R.id.btnPromptPaste)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_prompt_card, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvTitle.text = item.title
        holder.tvDesc.text = item.description
        holder.btnPaste.setOnClickListener { onPasteClick(item) }
        holder.itemView.setOnClickListener { onPasteClick(item) }
    }
}
