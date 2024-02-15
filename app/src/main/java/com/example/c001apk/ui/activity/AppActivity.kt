package com.example.c001apk.ui.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.c001apk.R
import com.example.c001apk.databinding.ActivityAppBinding
import com.example.c001apk.ui.fragment.AppFragment
import com.example.c001apk.ui.fragment.minterface.IOnTabClickContainer
import com.example.c001apk.ui.fragment.minterface.IOnTabClickListener
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.TopicBlackListUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayoutMediator

class AppActivity : BaseActivity<ActivityAppBinding>(), IOnTabClickContainer {

    private val viewModel by lazy { ViewModelProvider(this)[ApkViewModel::class.java] }
    private var subscribe: MenuItem? = null
    override var tabController: IOnTabClickListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setSupportActionBar(binding.toolBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        if (!viewModel.title.isNullOrEmpty()) {
            binding.appData = viewModel.appData
            binding.appLayout.visibility = View.VISIBLE
        } else if (viewModel.errorMessage != null) {
            showErrorMessage()
        }
        initData()
        initObserve()

        binding.errorLayout.retry.setOnClickListener {
            binding.errorLayout.parent.visibility = View.GONE
            binding.indicator.parent.visibility = View.VISIBLE
            binding.indicator.parent.isIndeterminate = true
            refreshData()
        }

    }

    private fun initObserve() {
        viewModel.showError.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it) {
                    binding.appLayout.visibility = View.GONE
                    binding.tabLayout.visibility = View.GONE
                    showErrorMessage()
                }
            }
        }

        viewModel.showAppInfo.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it) {
                    binding.appData = viewModel.appData
                } else {
                    binding.appLayout.visibility = View.GONE
                    binding.errorLayout.parent.visibility = View.VISIBLE
                }
                binding.indicator.parent.isIndeterminate = false
                binding.indicator.parent.visibility = View.GONE
            }
        }

        viewModel.doNext.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it) {
                    initView()
                } else {
                    binding.tabLayout.visibility = View.GONE
                    showErrorMessage()
                }
            }
        }

        viewModel.download.observe(this) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it)
                    downloadApp()
            }
        }

        viewModel.toastText.observe(this){event->
            event.getContentIfNotHandledOrReturnNull()?.let {
                initSub()
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initView() {
        binding.viewPager.offscreenPageLimit = viewModel.tabList.size
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun createFragment(position: Int) =
                when (position) {
                    0 -> AppFragment.newInstance("reply", viewModel.appId.toString())
                    1 -> AppFragment.newInstance("pub", viewModel.appId.toString())
                    2 -> AppFragment.newInstance("hot", viewModel.appId.toString())
                    else -> throw IllegalArgumentException()
                }

            override fun getItemCount() = viewModel.tabList.size
        }
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = viewModel.tabList[position]
        }.attach()

        binding.tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {}

            override fun onTabUnselected(tab: TabLayout.Tab?) {}

            override fun onTabReselected(tab: TabLayout.Tab?) {
                tabController?.onReturnTop(null)
            }

        })
    }

    private fun showErrorMessage() {
        binding.errorMessage.parent.visibility = View.VISIBLE
        binding.errorMessage.parent.text = viewModel.errorMessage
        binding.indicator.parent.isIndeterminate = false
        binding.indicator.parent.visibility = View.GONE
    }

    private fun downloadApp() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(viewModel.collectionUrl)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this@AppActivity, "下载失败", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun initData() {
        if (viewModel.isInit) {
            viewModel.isInit = false
            binding.indicator.parent.isIndeterminate = true
            binding.indicator.parent.visibility = View.VISIBLE
            refreshData()
        } else if (viewModel.tabList.isEmpty()) {
            binding.errorLayout.parent.visibility = View.VISIBLE
        } else if (viewModel.tabList.isNotEmpty()) {
            initView()
        } else if (viewModel.commentStatusText != "允许评论" && viewModel.type != "appForum") {
            showErrorMessage()
        }
    }

    private fun refreshData() {
        intent.getStringExtra("id")?.let {
            viewModel.fetchAppInfo(it)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.topic_product_menu, menu)
        subscribe = menu?.findItem(R.id.subscribe)
        subscribe?.isVisible = PrefManager.isLogin
        menu?.findItem(R.id.order)?.isVisible = false
        return true
    }

    private fun initSub() {
        subscribe?.title = if (viewModel.isFollow) "取消关注"
        else "关注"
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        initSub()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()

            R.id.search -> {
                if (viewModel.appId.isNullOrEmpty() || viewModel.title.isNullOrEmpty()) {
                    Toast.makeText(this, "加载中...", Toast.LENGTH_SHORT).show()
                } else {
                    IntentUtil.startActivity<SearchActivity>(this) {
                        putExtra("pageType", "apk")
                        putExtra("pageParam", viewModel.appId)
                        putExtra("title", viewModel.title)
                    }
                }
            }

            R.id.subscribe -> {
                viewModel.followUrl =
                    if (viewModel.isFollow) "/v6/apk/unFollow"
                    else "/v6/apk/follow"
                viewModel.onGetFollow()
            }

            R.id.block -> {
                MaterialAlertDialogBuilder(this).apply {
                    val title =
                        if (viewModel.type == "topic") viewModel.url.toString()
                            .replace("/t/", "")
                        else viewModel.title
                    setTitle("确定将 $title 加入黑名单？")
                    setNegativeButton(android.R.string.cancel, null)
                    setPositiveButton(android.R.string.ok) { _, _ ->
                        TopicBlackListUtil.saveTopic(viewModel.title.toString())
                    }
                    show()
                }
            }
        }
        return true
    }

}