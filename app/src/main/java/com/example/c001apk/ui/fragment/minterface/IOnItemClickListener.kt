package com.example.c001apk.ui.fragment.minterface

interface IOnItemClickListener {
    fun onItemClick(keyword: String)
    fun onItemDeleteClick(position: Int, keyword: String)
}