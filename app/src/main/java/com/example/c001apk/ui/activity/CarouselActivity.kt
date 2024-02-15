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
import com.example.c001apk.R
import com.example.c001apk.adapter.AppAdapter
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.databinding.ActivityCarouselBinding
import com.example.c001apk.ui.fragment.topic.TopicContentFragment
import com.example.c001apk.util.Utils.getColorFromAttr
import com.example.c001apk.view.LinearItemDecoration
import com.example.c001apk.view.StaggerItemDecoration
import com.google.android.material.tabs.TabLayoutMediator

class CarouselActivity : BaseActivity<ActivityCarouselBinding>() {

    private val viewModel by lazy { ViewModelProvider(this)[CarouselViewModel::class.java] }
    private lateinit var mAdapter: AppAdapter
    private lateinit var footerAdapter: FooterAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var sLayoutManager: StaggeredGridLayoutManager

    override fun onResume() {
        super.onResume()
        if (viewModel.isResume) {
            viewModel.isResume = false
            initData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.url = intent.getStringExtra("url")
        viewModel.title = intent.getStringExtra("title")

        binding.errorLayout.retry.setOnClickListener {
            binding.errorLayout.parent.visibility = View.GONE
            binding.indicator.parent.visibility = View.VISIBLE
            binding.indicator.parent.isIndeterminate = true
            refreshData()
        }

        if (!viewModel.isResume && viewModel.isInit) {
            binding.errorLayout.parent.visibility = View.VISIBLE
        }

        if (!viewModel.isInit) {
            if (viewModel.tabList.isNotEmpty()) {
                binding.tabLayout.visibility = View.VISIBLE
                binding.viewPager.visibility = View.VISIBLE
                initView()
            } else {
                binding.swipeRefresh.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.VISIBLE
                initRvView()
                initData()
                initRefresh()
                initScroll()
            }
            if (!viewModel.barTitle.isNullOrEmpty())
                initBar(viewModel.barTitle.toString())
            else
                initBar(viewModel.title.toString())
        }

        initObserve()

    }

    private fun initObserve() {
        viewModel.initBar.observe(this){event->
            event.getContentIfNotHandledOrReturnNull()?.let {
                initBar(viewModel.barTitle.toString())
            }
        }

        viewModel.showView.observe(this){event->
            event.getContentIfNotHandledOrReturnNull()?.let {
                binding.tabLayout.visibility = View.VISIBLE
                binding.viewPager.visibility = View.VISIBLE
            }
        }

        viewModel.initView.observe(this){event->
            event.getContentIfNotHandledOrReturnNull()?.let {
                initView()
            }
        }

        viewModel.initRvView.observe(this){event->
            event.getContentIfNotHandledOrReturnNull()?.let {
                initRvView()
                initData()
                initRefresh()
                initScroll()
                binding.swipeRefresh.visibility = View.VISIBLE
                binding.recyclerView.visibility = View.VISIBLE
            }
        }

        viewModel.error.observe(this){event->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (::mAdapter.isInitialized) {
                    viewModel.changeState.postValue(
                        Pair(
                            FooterAdapter.LoadState.LOADING_ERROR,
                            getString(R.string.loading_failed)
                        )
                    )
                } else if (viewModel.listSize == -1)
                    binding.errorLayout.parent.visibility = View.VISIBLE
            }
        }

        viewModel.finish.observe(this){event->
            event.getContentIfNotHandledOrReturnNull()?.let {
                binding.indicator.parent.isIndeterminate = false
                binding.indicator.parent.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }

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

        viewModel.carouselData.observe(this) {
            if (::mAdapter.isInitialized) {
                mAdapter.submitList(it)
            }
            viewModel.listSize = it.size

            val adapter = binding.recyclerView.adapter as ConcatAdapter
            if (!adapter.adapters.contains(mAdapter)) {
                adapter.addAdapter(mAdapter)
                adapter.addAdapter(footerAdapter)
            }

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
        viewModel.fetchCarouselList()
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

    private fun initRvView() {
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

    private fun initView() {
        binding.viewPager.offscreenPageLimit = viewModel.tabList.size
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int) =
                TopicContentFragment.newInstance(
                    viewModel.topicList[position].url,
                    viewModel.topicList[position].title,
                    false
                )

            override fun getItemCount() = viewModel.tabList.size
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = viewModel.tabList[position]
        }.attach()
    }

    private fun initData() {
        if (viewModel.listSize == -1) {
            binding.indicator.parent.visibility = View.VISIBLE
            binding.indicator.parent.isIndeterminate = true
            refreshData()
        }
    }

    private fun refreshData() {
        viewModel.firstVisibleItemPosition = 0
        viewModel.lastVisibleItemPosition = 0
        viewModel.page = 1
        viewModel.isEnd = false
        viewModel.isRefreshing = true
        viewModel.isLoadMore = false
        viewModel.fetchCarouselList()
    }

    private fun initBar(title: String) {
        binding.toolBar.title = title
        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
        }
        return true
    }

    inner class ReloadListener : FooterAdapter.FooterListener {
        override fun onReLoad() {
            loadMore()
        }
    }
}