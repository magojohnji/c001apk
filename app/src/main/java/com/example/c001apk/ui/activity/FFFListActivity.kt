package com.example.c001apk.ui.activity

import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.absinthe.libraries.utils.extensions.dp
import com.example.c001apk.adapter.AppAdapter
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.databinding.ActivityFfflistBinding
import com.example.c001apk.ui.fragment.FollowFragment
import com.example.c001apk.ui.fragment.FollowViewModel
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.Utils.getColorFromAttr
import com.example.c001apk.view.LinearItemDecoration
import com.example.c001apk.view.StaggerItemDecoration
import com.google.android.material.tabs.TabLayoutMediator

class FFFListActivity : BaseActivity<ActivityFfflistBinding>() {

    private val viewModel by lazy { ViewModelProvider(this)[FollowViewModel::class.java] }
    private lateinit var mAdapter: AppAdapter
    private lateinit var footerAdapter: FooterAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var sLayoutManager: StaggeredGridLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.isEnable = intent.getBooleanExtra("isEnable", false)
        viewModel.type = intent.getStringExtra("type")
        viewModel.uid = intent.getStringExtra("uid")

        initBar()
        if (viewModel.isEnable == true) {
            binding.tabLayout.visibility = View.VISIBLE
            binding.viewPager.visibility = View.VISIBLE
            binding.swipeRefresh.visibility = View.GONE
            binding.recyclerView.visibility = View.GONE
            if (viewModel.tabList.isEmpty()) {
                if (viewModel.type == "follow") {
                    viewModel.tabList.apply {
                        add("用户")
                        add("话题")
                        add("数码")
                        add("应用")
                    }
                } else if (viewModel.type == "reply") {
                    viewModel.tabList.apply {
                        add("我的回复")
                        add("我收到的回复")
                    }
                }
            }
            initViewPager()
        } else {
            binding.tabLayout.visibility = View.GONE
            binding.viewPager.visibility = View.GONE
            binding.swipeRefresh.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.VISIBLE
            initView()
            initData()
            initRefresh()
            initScroll()
            initObserve()
        }


       /* viewModel.postDeleteData.observe(this) { result ->
            if (viewModel.isNew) {
                viewModel.isNew = false

                val response = result.getOrNull()
                if (response != null) {
                    if (response.data == "删除成功") {
                        Toast.makeText(this, response.data, Toast.LENGTH_SHORT).show()
                        viewModel.dataList.removeAt(viewModel.position)
                        mAdapter.notifyItemRemoved(viewModel.position)
                    } else if (!response.message.isNullOrEmpty()) {
                        Toast.makeText(this, response.message, Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    result.exceptionOrNull()?.printStackTrace()
                }
            }
        }*/

    }

    private fun initObserve() {
        viewModel.changeState.observe(this) {
            footerAdapter.setLoadState(it.first, it.second)
            footerAdapter.notifyItemChanged(0)
            if (it.first != FooterAdapter.LoadState.LOADING) {
                binding.swipeRefresh.isRefreshing = false
                binding.indicator.parent.isIndeterminate = false
                binding.indicator.parent.visibility = View.GONE
                viewModel.isLoadMore = false
                viewModel.isRefreshing = false
            }
        }

        viewModel.dataListData.observe(this) {
            viewModel.listSize = it.size
            mAdapter.submitList(it)

            val adapter = binding.recyclerView.adapter as ConcatAdapter
            if (!adapter.adapters.contains(mAdapter)) {
                adapter.apply {
                    addAdapter(mAdapter)
                    addAdapter(footerAdapter)
                }
            }
        }
    }

    private fun initViewPager() {
        binding.viewPager.offscreenPageLimit = viewModel.tabList.size
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int) =
                if (viewModel.type == "follow") {
                    when (position) {
                        0 -> FollowFragment.newInstance("follow")
                        1 -> FollowFragment.newInstance("topic")
                        2 -> FollowFragment.newInstance("product")
                        3 -> FollowFragment.newInstance("apk")
                        else -> throw IllegalArgumentException()
                    }
                } else {
                    when (position) {
                        0 -> FollowFragment.newInstance("reply")
                        1 -> FollowFragment.newInstance("replyToMe")
                        else -> throw IllegalArgumentException()
                    }
                }

            override fun getItemCount() = viewModel.tabList.size

        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = viewModel.tabList[position]
        }.attach()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
        }
        return true
    }

    private fun initBar() {
        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolBar.title = when (viewModel.type) {
            "feed" -> "我的动态"

            "follow" -> {
                if (viewModel.uid == PrefManager.uid)
                    "我的关注"
                else
                    "TA关注的人"
            }

            "fans" -> {
                if (viewModel.uid == PrefManager.uid)
                    "关注我的人"
                else
                    "TA的粉丝"
            }

            "like" -> "我的赞"

            "reply" -> "我的回复"

            "recentHistory" -> "我的常去"

            else -> viewModel.type

        }
    }

    private fun initScroll() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {

                    if (viewModel.listSize != -1 && !viewModel.isEnd) {
                        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                            viewModel.lastVisibleItemPosition =
                                mLayoutManager.findLastVisibleItemPosition()
                        } else {
                            val positions = sLayoutManager.findLastVisibleItemPositions(null)
                            viewModel.lastVisibleItemPosition = positions[0]
                            for (pos in positions) {
                                if (pos > viewModel.lastVisibleItemPosition) {
                                    viewModel.lastVisibleItemPosition = pos
                                }
                            }
                        }
                    }

                    if (viewModel.lastVisibleItemPosition == viewModel.listSize
                        && !viewModel.isEnd && !viewModel.isRefreshing && !viewModel.isLoadMore
                    ) {
                        viewModel.page++

                        loadMore()
                    }
                }
            }
        })
    }

    private fun loadMore() {
        viewModel.isLoadMore = true
        viewModel.fetchFeedList()
    }

    private fun initRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            this.getColorFromAttr(
                rikka.preference.simplemenu.R.attr.colorPrimary
            )
        )
        binding.swipeRefresh.setOnRefreshListener {
            binding.indicator.parent.isIndeterminate = false
            binding.indicator.parent.visibility = View.GONE
            refreshData()
        }
    }

    private fun initData() {
        if (viewModel.listSize == -1) {
            binding.indicator.parent.isIndeterminate = true
            binding.indicator.parent.visibility = View.VISIBLE
            refreshData()
        }
    }

    private fun initView() {
        mAdapter = AppAdapter(viewModel.ItemClickListener())
        footerAdapter = FooterAdapter(ReloadListener())
        mLayoutManager = LinearLayoutManager(this)
        sLayoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        binding.recyclerView.apply {
            adapter = ConcatAdapter()
            layoutManager =
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                    mLayoutManager
                else sLayoutManager
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                addItemDecoration(LinearItemDecoration(10.dp))
            else
                addItemDecoration(StaggerItemDecoration(10.dp))
        }
    }

    private fun refreshData() {
        viewModel.firstVisibleItemPosition = -1
        viewModel.lastVisibleItemPosition = -1
        viewModel.page = 1
        viewModel.isRefreshing = true
        viewModel.isEnd = false
        viewModel.fetchFeedList()
    }

    inner class ReloadListener : FooterAdapter.FooterListener {
        override fun onReLoad() {
            loadMore()
        }
    }

}
