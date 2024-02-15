package com.example.c001apk.ui.activity

import android.content.res.Configuration
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.absinthe.libraries.utils.extensions.dp
import com.example.c001apk.R
import com.example.c001apk.adapter.AppAdapter
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.databinding.ActivityUserBinding
import com.example.c001apk.util.BlackListUtil
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.Utils.getColorFromAttr
import com.example.c001apk.view.LinearItemDecoration
import com.example.c001apk.view.StaggerItemDecoration
import com.google.android.material.dialog.MaterialAlertDialogBuilder


class UserActivity : BaseActivity<ActivityUserBinding>() {

    private val viewModel by lazy { ViewModelProvider(this)[UserViewModel::class.java] }
    private lateinit var mAdapter: AppAdapter
    private lateinit var footerAdapter: FooterAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var sLayoutManager: StaggeredGridLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        if (viewModel.errorMessage != null) {
            showErrorMessage()
        }

        initView()
        initData()
        initRefresh()
        initScroll()
        initObserve()

        binding.errorLayout.retry.setOnClickListener {
            binding.errorLayout.parent.visibility = View.GONE
            binding.indicator.parent.visibility = View.VISIBLE
            binding.indicator.parent.isIndeterminate = true
            refreshData()
        }

        binding.followBtn.setOnClickListener {
            if (PrefManager.isLogin)
                if (viewModel.followType) {
                    viewModel.url = "/v6/user/unfollow"
                    viewModel.onPostFollowUnFollow()
                } else {
                    viewModel.url = "/v6/user/follow"
                    viewModel.onPostFollowUnFollow()
                }
        }

    }

    private fun initObserve() {

        viewModel.afterFollow.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (viewModel.followType) {
                    binding.followBtn.text = "已关注"
                } else {
                    binding.followBtn.text = "关注"
                }
            }
        }

        viewModel.showError.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it) {
                    binding.indicator.parent.isIndeterminate = false
                    binding.indicator.parent.visibility = View.GONE
                    showErrorMessage()
                } else {
                    binding.indicator.parent.isIndeterminate = false
                    binding.indicator.parent.visibility = View.GONE
                    binding.errorLayout.parent.visibility = View.VISIBLE
                }
            }
        }

        viewModel.showUser.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it) {
                    binding.userData = viewModel.userData
                    binding.listener = viewModel.ItemClickListener()
                }
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

        viewModel.feedData.observe(this) {
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

    private fun showErrorMessage() {
        binding.swipeRefresh.isEnabled = false
        binding.errorMessage.parent.visibility = View.VISIBLE
        binding.errorMessage.parent.text = viewModel.errorMessage
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
        viewModel.fetchUserFeed()
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
        if (viewModel.isInit) {
            viewModel.isInit = false
            binding.indicator.parent.visibility = View.VISIBLE
            binding.indicator.parent.isIndeterminate = true
            refreshData()
        } else if (viewModel.uid == null) {
            binding.errorLayout.parent.visibility = View.VISIBLE
        }
    }

    private fun refreshData() {
        viewModel.firstVisibleItemPosition = 0
        viewModel.lastVisibleItemPosition = 0
        viewModel.page = 1
        viewModel.isRefreshing = true
        viewModel.isEnd = false
        if (viewModel.uid.isNullOrEmpty())
            viewModel.uid = intent.getStringExtra("id")
        viewModel.fetchUser()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.user_menu, menu)

        val itemBlock = menu?.findItem(R.id.block)
        val spannableString = SpannableString(itemBlock?.title)
        spannableString.setSpan(
            ForegroundColorSpan(
                this.getColorFromAttr(
                    rikka.preference.simplemenu.R.attr.colorControlNormal
                )
            ),
            0,
            spannableString.length,
            0
        )
        itemBlock?.title = spannableString


        val itemShare = menu?.findItem(R.id.share)
        val spannableString1 = SpannableString(itemShare?.title)
        spannableString1.setSpan(
            ForegroundColorSpan(
                this.getColorFromAttr(
                    rikka.preference.simplemenu.R.attr.colorControlNormal
                )
            ),
            0,
            spannableString1.length,
            0
        )
        itemShare?.title = spannableString1

        val itemReport = menu?.findItem(R.id.report)
        val spannableString2 = SpannableString(itemReport?.title)
        spannableString2.setSpan(
            ForegroundColorSpan(
                this.getColorFromAttr(
                    rikka.preference.simplemenu.R.attr.colorControlNormal
                )
            ),
            0,
            spannableString2.length,
            0
        )
        itemReport?.title = spannableString2
        itemReport?.isVisible = PrefManager.isLogin

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()

            R.id.search -> {
                IntentUtil.startActivity<SearchActivity>(this) {
                    putExtra("pageType", "user")
                    putExtra("pageParam", viewModel.uid)
                    putExtra("title", binding.name.text)
                }
            }

            R.id.block -> {
                MaterialAlertDialogBuilder(this).apply {
                    setTitle("确定将 ${viewModel.uname} 加入黑名单？")
                    setNegativeButton(android.R.string.cancel, null)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        BlackListUtil.saveUid(viewModel.uid.toString())
                    }
                    show()
                }
            }

            R.id.share -> {
                IntentUtil.shareText(this, "https://www.coolapk.com/u/${viewModel.uid}")
            }

            R.id.report -> {
                IntentUtil.startActivity<WebViewActivity>(this) {
                    putExtra(
                        "url",
                        "https://m.coolapk.com/mp/do?c=user&m=report&id=${viewModel.uid}"
                    )
                }
            }

        }
        return true
    }


    inner class ReloadListener : FooterAdapter.FooterListener {
        override fun onReLoad() {
            loadMore()
        }
    }

}