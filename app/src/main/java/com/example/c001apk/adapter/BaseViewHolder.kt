package com.example.c001apk.adapter

import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView

open class BaseViewHolder<T : ViewDataBinding>(@JvmField val dataBinding: T) :
    RecyclerView.ViewHolder(dataBinding.root) {
    open fun bind() {}
}