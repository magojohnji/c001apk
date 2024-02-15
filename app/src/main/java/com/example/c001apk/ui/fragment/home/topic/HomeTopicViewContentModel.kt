package com.example.c001apk.ui.fragment.home.topic

import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.constant.Constants.LOADING_FAILED
import com.example.c001apk.logic.model.HomeFeedResponse
import com.example.c001apk.logic.model.Like
import com.example.c001apk.logic.network.Repository.getDataList
import kotlinx.coroutines.launch

class HomeTopicViewContentModel : ViewModel() {

    private var lastItem: String? = null
    val changeState = MutableLiveData<Pair<FooterAdapter.LoadState, String?>>()
    val topicData = MutableLiveData<List<HomeFeedResponse.Data>>()

    fun fetchTopicData() {
        viewModelScope.launch {
            getDataList(url.toString(), title.toString(), null, lastItem, page)
                .collect { result ->
                    val topicDataList = topicData.value?.toMutableList() ?: ArrayList()
                    val data = result.getOrNull()
                    if (!data?.message.isNullOrEmpty()) {
                        changeState.postValue(
                            Pair(FooterAdapter.LoadState.LOADING_ERROR, data?.message)
                        )
                        return@collect
                    } else if (!data?.data.isNullOrEmpty()) {
                        if (isRefreshing) topicDataList.clear()
                        if (isRefreshing || isLoadMore) {
                            listSize = topicDataList.size
                            for (element in data?.data!!)
                                if (element.entityType == "topic"
                                    || element.entityType == "product"
                                )
                                    topicDataList.add(
                                        element.also {
                                            it.description = "home"
                                        })
                            lastItem = topicDataList.last().id
                        }
                        changeState.postValue(Pair(FooterAdapter.LoadState.LOADING_COMPLETE, null))
                    } else if (data?.data?.isEmpty() == true) {
                        if (isRefreshing) topicDataList.clear()
                        changeState.postValue(Pair(FooterAdapter.LoadState.LOADING_END, null))
                        isEnd = true
                    } else {
                        changeState.postValue(
                            Pair(
                                FooterAdapter.LoadState.LOADING_ERROR,
                                LOADING_FAILED
                            )
                        )
                        isEnd = true
                        result.exceptionOrNull()?.printStackTrace()
                    }
                    topicData.postValue(topicDataList)
                }
        }
    }

    var page = 1
    var title: String? = null
    var url: String? = null
    var isInit = true
    var listSize = -1
    var isRefreshing: Boolean = true
    var isLoadMore: Boolean = false
    var isEnd: Boolean = false
    var lastVisibleItemPosition: Int = 0
    var firstVisibleItemPosition = 0


    inner class ItemClickListener : ItemListener {

    }

}