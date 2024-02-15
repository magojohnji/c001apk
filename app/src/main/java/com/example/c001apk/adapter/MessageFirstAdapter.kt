package com.example.c001apk.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.c001apk.R
import com.example.c001apk.databinding.ItemMessageFffBinding
import com.example.c001apk.ui.activity.FFFListActivity
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager


class MessageFirstAdapter
    : RecyclerView.Adapter<MessageFirstAdapter.FirstViewHolder>() {

    private var ffflist: MutableList<String>? = null

    fun setFFFList(ffflist: MutableList<String>) {
        this.ffflist = ffflist
        notifyItemChanged(0)
    }

    private val fffTitle = ArrayList<String>()

    init {
        fffTitle.apply {
            add("动态")
            add("关注")
            add("粉丝")
        }
    }

    inner class FirstViewHolder(val binding: ItemMessageFffBinding) :
        RecyclerView.ViewHolder(binding.root), View.OnClickListener {
        fun bind() {
            if (PrefManager.isLogin) {
                binding.feedLayout.setOnClickListener(this)
                binding.followLayout.setOnClickListener(this)
                binding.fansLayout.setOnClickListener(this)
            }
            ffflist?.let {
                binding.apply {
                    feedCount.text = it[0]
                    feedTitle.text = fffTitle[0]
                    followCount.text = it[1]
                    followTitle.text = fffTitle[1]
                    fansCount.text = it[2]
                    fansTitle.text = fffTitle[2]
                }
                binding.executePendingBindings()
            }
        }

        override fun onClick(view: View?) {
            when (view?.id) {
                R.id.feedLayout ->
                    IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                        putExtra("uid", PrefManager.uid)
                        putExtra("isEnable", false)
                        putExtra("type", "feed")
                    }

                R.id.followLayout ->
                    IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                        putExtra("uid", PrefManager.uid)
                        putExtra("isEnable", true)
                        putExtra("type", "follow")
                    }

                R.id.fansLayout ->
                    IntentUtil.startActivity<FFFListActivity>(itemView.context) {
                        putExtra("uid", PrefManager.uid)
                        putExtra("isEnable", false)
                        putExtra("type", "fans")
                    }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FirstViewHolder {
        val binding = ItemMessageFffBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val lp = binding.root.layoutParams
        if (lp is StaggeredGridLayoutManager.LayoutParams) {
            lp.isFullSpan = true
        }
        return FirstViewHolder(binding)
    }

    override fun getItemCount() = 1

    override fun onBindViewHolder(holder: FirstViewHolder, position: Int) {
        holder.bind()
    }

}