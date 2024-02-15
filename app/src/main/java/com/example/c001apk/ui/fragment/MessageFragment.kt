package com.example.c001apk.ui.fragment

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.absinthe.libraries.utils.extensions.dp
import com.example.c001apk.R
import com.example.c001apk.adapter.Event
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.adapter.MessageAdapter
import com.example.c001apk.adapter.MessageFirstAdapter
import com.example.c001apk.adapter.MessageSecondAdapter
import com.example.c001apk.adapter.MessageThirdAdapter
import com.example.c001apk.databinding.FragmentMessageBinding
import com.example.c001apk.ui.activity.LoginActivity
import com.example.c001apk.ui.activity.MainActivity
import com.example.c001apk.ui.activity.WebViewActivity
import com.example.c001apk.ui.fragment.minterface.INavViewContainer
import com.example.c001apk.util.ActivityCollector
import com.example.c001apk.util.BlackListUtil
import com.example.c001apk.util.CookieUtil.atcommentme
import com.example.c001apk.util.CookieUtil.atme
import com.example.c001apk.util.CookieUtil.contacts_follow
import com.example.c001apk.util.CookieUtil.feedlike
import com.example.c001apk.util.CookieUtil.message
import com.example.c001apk.util.ImageUtil
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.Utils.getColorFromAttr
import com.example.c001apk.view.LinearItemDecoration
import com.example.c001apk.view.StaggerMessItemDecoration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.URLDecoder


class MessageFragment : BaseFragment<FragmentMessageBinding>() {

    private val viewModel by lazy { ViewModelProvider(this)[MessageViewModel::class.java] }
    private val messageFirstAdapter by lazy { MessageFirstAdapter() }
    private val messageSecondAdapter by lazy { MessageSecondAdapter() }
    private val messageThirdAdapter by lazy { MessageThirdAdapter() }
    private lateinit var mAdapter: MessageAdapter
    private lateinit var footerAdapter: FooterAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var sLayoutManager: StaggeredGridLayoutManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.clickToLogin.setOnClickListener {
            IntentUtil.startActivity<LoginActivity>(requireContext()) {
            }
        }

        initRefresh()
        initView()
        initScroll()
        initMenu()
        initObserve()

        if (PrefManager.isLogin) {
            binding.clickToLogin.visibility = View.GONE
            binding.avatar.visibility = View.VISIBLE
            binding.name.visibility = View.VISIBLE
            binding.levelLayout.visibility = View.VISIBLE
            binding.progress.visibility = View.VISIBLE
            showProfile()
            if (viewModel.isInit) {
                viewModel.isInit = false
                getData()
            } else {
                if (viewModel.countList.isNotEmpty())
                    messageFirstAdapter.setFFFList(viewModel.countList)
                if (viewModel.messCountList.isNotEmpty())
                    messageThirdAdapter.setBadgeList(viewModel.messCountList)
            }
            viewModel.messCountList.apply {
                add(atme)
                add(atcommentme)
                add(feedlike)
                add(contacts_follow)
                add(message)
            }
        } else {
            binding.clickToLogin.visibility = View.VISIBLE
            binding.avatar.visibility = View.INVISIBLE
            binding.name.visibility = View.INVISIBLE
            binding.levelLayout.visibility = View.INVISIBLE
            binding.progress.visibility = View.INVISIBLE
        }
    }

    private fun initObserve() {
        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.doWhat.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                when (it) {
                    "showProfile" -> showProfile()

                    "isRefreshing" -> binding.swipeRefresh.isRefreshing = false

                    "countList" -> messageFirstAdapter.setFFFList(viewModel.countList)

                    "messCountList" -> messageThirdAdapter.setBadgeList(viewModel.messCountList)
                }
            }
        }

        viewModel.changeState.observe(viewLifecycleOwner) {
            footerAdapter.setLoadState(it.first, it.second)
            footerAdapter.notifyItemChanged(0)
            if (it.first != FooterAdapter.LoadState.LOADING) {
                binding.swipeRefresh.isRefreshing = false
                viewModel.isLoadMore = false
                viewModel.isRefreshing = false
            }
        }

        viewModel.messageData.observe(viewLifecycleOwner) {
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

    private fun initScroll() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {

                    if (viewModel.listSize != -1 && !viewModel.isEnd && isAdded) {
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

                    if (viewModel.lastVisibleItemPosition == viewModel.listSize + 6
                        && !viewModel.isEnd && !viewModel.isRefreshing && !viewModel.isLoadMore
                    ) {
                        viewModel.page++
                        loadMore()
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (viewModel.listSize != -1) {
                    if (dy > 0) {
                        (activity as? INavViewContainer)?.hideNavigationView()
                    } else if (dy < 0) {
                        (activity as? INavViewContainer)?.showNavigationView()
                    }
                }
            }
        })
    }

    private fun loadMore() {
        viewModel.isLoadMore = true
        viewModel.fetchMessage()
    }

    private fun initView() {
        mAdapter = MessageAdapter(ItemClickListener())
        footerAdapter = FooterAdapter(ReloadListener())
        mLayoutManager = LinearLayoutManager(requireContext())
        sLayoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        binding.recyclerView.apply {
            adapter = ConcatAdapter(messageFirstAdapter, messageSecondAdapter, messageThirdAdapter)
            layoutManager =
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                    mLayoutManager
                else sLayoutManager
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                addItemDecoration(LinearItemDecoration(10.dp))
            else
                addItemDecoration(StaggerMessItemDecoration(10.dp))
        }
    }

    private fun initRefresh() {
        binding.swipeRefresh.setColorSchemeColors(
            requireContext().getColorFromAttr(
                rikka.preference.simplemenu.R.attr.colorPrimary
            )
        )
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.isRefreshing = true
            getData()
            if (PrefManager.isLogin) {
                viewModel.messCountList.clear()
                viewModel.fetchCheckLoginInfo()
            }
        }
    }

    private fun getData() {
        if (PrefManager.isLogin) {
            binding.swipeRefresh.isRefreshing = true
            viewModel.uid = PrefManager.uid
            viewModel.page = 1
            viewModel.isEnd = false
            viewModel.isRefreshing = true
            viewModel.isLoadMore = false
            viewModel.fetchProfile()
        } else
            binding.swipeRefresh.isRefreshing = false
    }


    private fun initMenu() {
        binding.toolBar.apply {
            inflateMenu(R.menu.message_menu)
            menu.findItem(R.id.logout).isVisible = PrefManager.isLogin
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.logout -> {
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            setTitle(R.string.logoutTitle)
                            setNegativeButton(android.R.string.cancel, null)
                            setPositiveButton(android.R.string.ok) { _, _ ->
                                viewModel.countList.clear()
                                viewModel.messCountList.clear()
                                viewModel.doWhat.postValue(Event("countList"))
                                viewModel.doWhat.postValue(Event("messCountList"))
                                viewModel.messageData.postValue(emptyList())
                                viewModel.isInit = true
                                viewModel.isEnd = false
                                PrefManager.isLogin = false
                                PrefManager.uid = ""
                                PrefManager.username = ""
                                PrefManager.token = ""
                                PrefManager.userAvatar = ""
                                ActivityCollector.recreateActivity(MainActivity::class.java.name)
                            }
                            show()
                        }
                    }
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showProfile() {
        binding.name.text = URLDecoder.decode(PrefManager.username, "UTF-8")
        binding.level.text = "Lv.${PrefManager.level}"
        binding.exp.text = "${PrefManager.experience}/${PrefManager.nextLevelExperience}"
        binding.progress.max =
            if (PrefManager.nextLevelExperience != "" && PrefManager.nextLevelExperience != "null") PrefManager.nextLevelExperience.toInt()
            else -1
        binding.progress.progress =
            if (PrefManager.experience != "" && PrefManager.experience != "null") PrefManager.experience.toInt()
            else -1
        if (PrefManager.userAvatar.isNotEmpty())
            ImageUtil.showIMG(binding.avatar, PrefManager.userAvatar)
    }

    inner class ReloadListener : FooterAdapter.FooterListener {
        override fun onReLoad() {
            loadMore()
        }
    }

    inner class ItemClickListener : ItemListener {
        override fun onExpand(
            view: View,
            id: String,
            uid: String,
            text: String?,
            position: Int,
            rPosition: Int?
        ) {
            PopupMenu(view.context, view).apply {
                menuInflater.inflate(R.menu.feed_reply_menu, menu).apply {
                    menu.findItem(R.id.copy)?.isVisible = false
                    menu.findItem(R.id.show)?.isVisible = false
                    menu.findItem(R.id.delete)?.isVisible = false
                }
                setOnMenuItemClickListener(PopClickListener(uid))
                show()
            }
        }

        override fun onMessLongClicked(uname: String, id: String, position: Int): Boolean {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle("删除来自 $uname 的通知？")
                setNegativeButton(android.R.string.cancel, null)
                setPositiveButton(android.R.string.ok) { _, _ ->
                    viewModel.onPostDelete(position, id)
                }
                show()
            }
            return true
        }
    }

    inner class PopClickListener(val uid: String) :
        PopupMenu.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem?): Boolean {
            when (item?.itemId) {
                R.id.block -> {
                    BlackListUtil.saveUid(uid)
                }

                R.id.report -> {
                    IntentUtil.startActivity<WebViewActivity>(requireContext()) {
                        putExtra(
                            "url",
                            "https://m.coolapk.com/mp/do?c=user&m=report&id=$uid"
                        )
                    }
                }
            }
            return true
        }
    }


}