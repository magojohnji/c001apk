package com.example.c001apk.ui.fragment.feed

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.absinthe.libraries.utils.extensions.dp
import com.example.c001apk.R
import com.example.c001apk.adapter.FeedDataAdapter
import com.example.c001apk.adapter.FeedFixAdapter
import com.example.c001apk.adapter.FeedReplyAdapter
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.adapter.ItemListener
import com.example.c001apk.adapter.ReplyRefreshListener
import com.example.c001apk.constant.Constants.SZLM_ID
import com.example.c001apk.databinding.FragmentFeedBinding
import com.example.c001apk.databinding.ItemCaptchaBinding
import com.example.c001apk.logic.database.FeedFavoriteDatabase
import com.example.c001apk.logic.model.FeedFavorite
import com.example.c001apk.logic.model.Like
import com.example.c001apk.ui.activity.CopyActivity
import com.example.c001apk.ui.activity.WebViewActivity
import com.example.c001apk.ui.fragment.BaseFragment
import com.example.c001apk.ui.fragment.ReplyBottomSheetDialog
import com.example.c001apk.ui.fragment.minterface.INavViewContainer
import com.example.c001apk.ui.fragment.minterface.IOnPublishClickListener
import com.example.c001apk.util.BlackListUtil
import com.example.c001apk.util.ClipboardUtil
import com.example.c001apk.util.DensityTool
import com.example.c001apk.util.IntentUtil
import com.example.c001apk.util.PrefManager
import com.example.c001apk.util.ToastUtil
import com.example.c001apk.util.Utils.getColorFromAttr
import com.example.c001apk.view.OffsetLinearLayoutManager
import com.example.c001apk.view.StickyItemDecorator
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class FeedFragment : BaseFragment<FragmentFeedBinding>(), IOnPublishClickListener {

    private val viewModel by lazy { ViewModelProvider(requireActivity())[FeedViewModel::class.java] }
    private lateinit var bottomSheetDialog: ReplyBottomSheetDialog
    private lateinit var feedDataAdapter: FeedDataAdapter
    private lateinit var feedReplyAdapter: FeedReplyAdapter
    private lateinit var feedFixAdapter: FeedFixAdapter
    private lateinit var footerAdapter: FooterAdapter
    private lateinit var mLayoutManager: OffsetLinearLayoutManager
    private lateinit var sLayoutManager: StaggeredGridLayoutManager
    private val feedFavoriteDao by lazy {
        FeedFavoriteDatabase.getDatabase(requireContext()).feedFavoriteDao()
    }
    private val fabViewBehavior by lazy { HideBottomViewOnScrollBehavior<FloatingActionButton>() }
    private var dialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initToolBar()
        initView()
        initData()
        initScroll()
        initReplyBtn()
        initBottomSheet()
        initObserve()

    }

    @SuppressLint("InflateParams")
    private fun initBottomSheet() {
        if (PrefManager.isLogin) {
            val view1 = LayoutInflater.from(context)
                .inflate(R.layout.dialog_reply_bottom_sheet, null, false)
            bottomSheetDialog = ReplyBottomSheetDialog(requireContext(), view1)
            bottomSheetDialog.setIOnPublishClickListener(this)
            bottomSheetDialog.apply {
                setContentView(view1)
                setCancelable(false)
                setCanceledOnTouchOutside(true)
                window?.apply {
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
                type = "reply"
            }
        }
    }

    private fun initReplyBtn() {
        if (PrefManager.isLogin) {
            binding.reply.apply {
                visibility = View.VISIBLE
                val lp = CoordinatorLayout.LayoutParams(
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT,
                    CoordinatorLayout.LayoutParams.WRAP_CONTENT
                )
                lp.setMargins(
                    0, 0, 25.dp,
                    if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                        DensityTool.getNavigationBarHeight(requireContext()) + 25.dp
                    else 25.dp
                )
                lp.gravity = Gravity.BOTTOM or Gravity.END
                layoutParams = lp
                (layoutParams as CoordinatorLayout.LayoutParams).behavior = fabViewBehavior

                setOnClickListener {
                    if (PrefManager.SZLMID == "") {
                        Toast.makeText(requireContext(), SZLM_ID, Toast.LENGTH_SHORT)
                            .show()
                    } else {
                        viewModel.rid = viewModel.id
                        viewModel.ruid = viewModel.uid
                        viewModel.uname = viewModel.funame
                        viewModel.type = "feed"
                        initReply()
                    }
                }
            }
        } else
            binding.reply.visibility = View.GONE
    }

    private fun initReply() {
        bottomSheetDialog.apply {
            rid = viewModel.rid.toString()
            ruid = viewModel.ruid.toString()
            uname = viewModel.uname.toString()
            setData()
            show()
        }
    }

    private fun initScroll() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {

                    if (viewModel.listSize != -1 && isAdded) {
                        viewModel.lastVisibleItemPosition =
                            mLayoutManager.findLastVisibleItemPosition()
                        viewModel.firstVisibleItemPosition =
                            mLayoutManager.findFirstVisibleItemPosition()
                    }

                    if (viewModel.lastVisibleItemPosition == viewModel.listSize + viewModel.itemCount + 1
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
        viewModel.fetchFeedReply()
    }

    private fun initObserve() {

        viewModel.notify.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it) {
                    feedReplyAdapter.notifyItemChanged(viewModel.rPosition!!)
                }
            }
        }

        viewModel.scroll.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it) {
                    mLayoutManager.scrollToPositionWithOffset(viewModel.itemCount, 0)
                }
            }
        }

        viewModel.closeSheet.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                if (it && ::bottomSheetDialog.isInitialized && bottomSheetDialog.isShowing) {
                    bottomSheetDialog.editText.text = null
                    bottomSheetDialog.dismiss()
                }
            }
        }

        viewModel.toastText.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandledOrReturnNull()?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.createDialog.observe(viewLifecycleOwner) { event ->
            event?.getContentIfNotHandledOrReturnNull()?.let {
                val binding = ItemCaptchaBinding.inflate(
                    LayoutInflater.from(requireContext()), null, false
                )
                binding.captchaImg.setImageBitmap(it)
                binding.captchaText.highlightColor = ColorUtils.setAlphaComponent(
                    requireContext().getColorFromAttr(rikka.preference.simplemenu.R.attr.colorPrimaryDark),
                    128
                )
                MaterialAlertDialogBuilder(requireContext()).apply {
                    setView(binding.root)
                    setTitle("captcha")
                    setNegativeButton(android.R.string.cancel, null)
                    setPositiveButton("验证并继续") { _, _ ->
                        viewModel.requestValidateData = HashMap()
                        viewModel.requestValidateData["type"] = "err_request_captcha"
                        viewModel.requestValidateData["code"] = binding.captchaText.text.toString()
                        viewModel.requestValidateData["mobile"] = ""
                        viewModel.requestValidateData["idcard"] = ""
                        viewModel.requestValidateData["name"] = ""
                        viewModel.onPostRequestValidate()
                    }
                    show()
                }
            }
        }

        viewModel.changeState.observe(viewLifecycleOwner) {
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

        viewModel.feedReplyData.observe(viewLifecycleOwner) {
            viewModel.listSize = it.size
            feedReplyAdapter.submitList(it)
            if (viewModel.isViewReply == true) {
                viewModel.isViewReply = false
                mLayoutManager.scrollToPositionWithOffset(viewModel.itemCount, 0)
            }
            if (dialog != null) {
                dialog?.dismiss()
                dialog === null
            }
        }

    }

    private fun initData() {
        if (viewModel.isInit) {
            viewModel.isInit = false
            viewModel.isTop?.let { feedReplyAdapter.setHaveTop(it, viewModel.topReplyId) }
            binding.titleProfile.visibility = View.GONE
            refresh()
        } else {
            if (getScrollYDistance() >= 50.dp) {
                // showTitleProfile()
            } else {
                binding.titleProfile.visibility = View.GONE
            }
        }
    }

    private fun refresh() {
        viewModel.firstVisibleItemPosition = 0
        viewModel.lastVisibleItemPosition = 0
        viewModel.firstItem = null
        viewModel.lastItem = null
        viewModel.page = 1
        viewModel.isEnd = false
        viewModel.isRefreshing = true
        viewModel.isLoadMore = false
        viewModel.fetchFeedReply()
    }


    private fun getScrollYDistance(): Int {
        val position = mLayoutManager.findFirstVisibleItemPosition()
        val firstVisibleChildView = mLayoutManager.findViewByPosition(position)
        var itemHeight = 0
        var top = 0
        firstVisibleChildView?.let {
            itemHeight = firstVisibleChildView.height
            top = firstVisibleChildView.top
        }
        return position * itemHeight - top
    }

    @SuppressLint("SetTextI18n")
    private fun initView() {
        feedDataAdapter = FeedDataAdapter(
            ItemClickListener(),
            viewModel.feedDataList,
            viewModel.articleList
        )
        feedReplyAdapter = FeedReplyAdapter(ItemClickListener())
        feedFixAdapter =
            FeedFixAdapter(viewModel.replyCount.toString(), RefreshReplyListener())

        binding.listener = RefreshReplyListener()
        footerAdapter = FooterAdapter(ReloadListener())

        binding.replyCount.text = "共 ${viewModel.replyCount} 回复"
        setListType()
        mLayoutManager = OffsetLinearLayoutManager(requireContext())

        if (viewModel.isViewReply == true) {
            viewModel.isViewReply = false
            mLayoutManager.scrollToPositionWithOffset(viewModel.itemCount, 0)
        }

        binding.recyclerView.apply {
            adapter =
                ConcatAdapter(feedDataAdapter, feedFixAdapter, feedReplyAdapter, footerAdapter)
            layoutManager = mLayoutManager
            if (itemDecorationCount == 0)
                addItemDecoration(
                    StickyItemDecorator(requireContext(), 1, viewModel.itemCount,
                        object : StickyItemDecorator.SortShowListener {
                            override fun showSort(show: Boolean) {
                                binding.tabLayout.visibility =
                                    if (show) View.VISIBLE else View.GONE
                            }
                        })
                )
        }
    }

    private fun initToolBar() {
        binding.toolBar.apply {
            title = viewModel.feedTypeName
            setNavigationIcon(R.drawable.ic_back)
            setNavigationOnClickListener {
                requireActivity().finish()
            }
            setOnClickListener {
                binding.recyclerView.stopScroll()
                binding.titleProfile.visibility = View.GONE
                mLayoutManager.scrollToPositionWithOffset(0, 0)
                //sLayoutManager.scrollToPositionWithOffset(0, 0)
            }
            inflateMenu(R.menu.feed_menu)
            menu.findItem(R.id.showReply).isVisible =
                resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            menu.findItem(R.id.report).isVisible = PrefManager.isLogin
            val favorite = menu.findItem(R.id.favorite)
            CoroutineScope(Dispatchers.IO).launch {
                if (feedFavoriteDao.isFavorite(viewModel.id.toString())) {
                    withContext(Dispatchers.Main) {
                        favorite.title = "取消收藏"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        favorite.title = "收藏"
                    }
                }
            }
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.showReply -> {
                        binding.recyclerView.stopScroll()
                        if (viewModel.firstVisibleItemPosition <= viewModel.itemCount - 1) {
                            mLayoutManager.scrollToPositionWithOffset(viewModel.itemCount, 0)
                            viewModel.firstVisibleItemPosition = viewModel.itemCount
                        } else {
                            binding.titleProfile.visibility = View.GONE
                            mLayoutManager.scrollToPositionWithOffset(0, 0)
                            viewModel.firstVisibleItemPosition = 0
                        }
                    }

                    R.id.block -> {
                        MaterialAlertDialogBuilder(requireContext()).apply {
                            setTitle("确定将 ${viewModel.funame} 加入黑名单？")
                            setNegativeButton(android.R.string.cancel, null)
                            setPositiveButton(android.R.string.ok) { _, _ ->
                                BlackListUtil.saveUid(viewModel.uid.toString())
                            }
                            show()
                        }
                    }

                    R.id.share -> {
                        IntentUtil.shareText(
                            requireContext(),
                            "https://www.coolapk1s.com/feed/${viewModel.id}"
                        )
                    }

                    R.id.copyLink -> {
                        ClipboardUtil.copyText(
                            requireContext(),
                            "https://www.coolapk1s.com/feed/${viewModel.id}"
                        )
                    }

                    R.id.report -> {
                        IntentUtil.startActivity<WebViewActivity>(requireContext()) {
                            putExtra(
                                "url",
                                "https://m.coolapk.com/mp/do?c=feed&m=report&type=feed&id=${viewModel.id}"
                            )
                        }
                    }


                    R.id.favorite -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            if (feedFavoriteDao.isFavorite(viewModel.id.toString())) {
                                feedFavoriteDao.delete(viewModel.id.toString())
                                withContext(Dispatchers.Main) {
                                    favorite.title = "收藏"
                                    ToastUtil.toast(requireContext(), "已取消收藏")
                                }
                            } else {
                                try {
                                    val fav = FeedFavorite(
                                        viewModel.id.toString(),
                                        viewModel.uid.toString(),
                                        viewModel.funame.toString(),
                                        viewModel.avatar.toString(),
                                        viewModel.device.toString(),
                                        if (viewModel.feedType == "feedArticle") viewModel.articleMsg.toString()
                                        else viewModel.feedDataList!![0].message, // 还未加载完会空指针
                                        if (viewModel.feedType == "feedArticle") viewModel.articleDateLine.toString()
                                        else viewModel.feedDataList!![0].dateline.toString()
                                    )
                                    feedFavoriteDao.insert(fav)
                                    withContext(Dispatchers.Main) {
                                        favorite.title = "取消收藏"
                                        ToastUtil.toast(requireContext(), "已收藏")
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    ToastUtil.toast(requireContext(), "请稍后再试")
                                }
                            }

                        }
                    }
                }
                return@setOnMenuItemClickListener true
            }
        }
    }

    inner class ReloadListener : FooterAdapter.FooterListener {
        override fun onReLoad() {
            loadMore()
        }
    }

    inner class RefreshReplyListener : ReplyRefreshListener {
        @SuppressLint("InflateParams")
        override fun onRefreshReply(listType: String) {
            viewModel.listType = listType
            setListType()
            viewModel.firstItem = null
            viewModel.lastItem = null
            if (listType == "lastupdate_desc" && viewModel.feedTopReplyList.isNotEmpty())
                viewModel.isTop?.let { feedReplyAdapter.setHaveTop(it, viewModel.topReplyId) }
            else
                feedReplyAdapter.setHaveTop(false, null)
            binding.recyclerView.stopScroll()
            if (viewModel.firstVisibleItemPosition > 1)
                viewModel.isViewReply = true
            viewModel.fromFeedAuthor = if (listType == "") 1
            else 0
            viewModel.page = 1
            viewModel.isEnd = false
            viewModel.isRefreshing = true
            viewModel.isLoadMore = false
            viewModel.isRefreshReply = true
            dialog = MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_MaterialAlertDialog_Rounded
            ).apply {
                setView(
                    LayoutInflater.from(requireContext())
                        .inflate(R.layout.dialog_refresh, null, false)
                )
                setCancelable(false)
            }.create()
            dialog?.window?.setLayout(150.dp, LinearLayout.LayoutParams.WRAP_CONTENT)
            dialog?.show()
            viewModel.fetchFeedReply()
        }
    }

    private fun setListType() {
        when (viewModel.listType) {
            "lastupdate_desc" -> binding.buttonToggle.check(R.id.lastUpdate)
            "dateline_desc" -> binding.buttonToggle.check(R.id.dateLine)
            "popular" -> binding.buttonToggle.check(R.id.popular)
            "" -> binding.buttonToggle.check(R.id.author)
        }
        feedFixAdapter.setListType(viewModel.listType)
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
                    //  menu.findItem(R.id.delete).isVisible = PrefManager.uid == uid
                    menu.findItem(R.id.report).isVisible = PrefManager.isLogin
                }
                setOnMenuItemClickListener(
                    PopClickListener(
                        id,
                        uid,
                        text,
                        position,
                        rPosition
                    )
                )
                show()
            }
        }

        override fun onLikeClick(type: String, id: String, position: Int, likeData: Like) {


        }

        override fun showTotalReply(id: String, uid: String, position: Int, rPosition: Int?) {
            val mBottomSheetDialogFragment =
                Reply2ReplyBottomSheetDialog.newInstance(
                    position,
                    viewModel.uid.toString(),
                    uid,
                    id
                )
            val feedReplyList = viewModel.feedReplyData.value!!
            if (rPosition == null || rPosition == -1)
                mBottomSheetDialogFragment.oriReply.add(feedReplyList[position - viewModel.itemCount - 1])
            else
                mBottomSheetDialogFragment.oriReply.add(
                    feedReplyList[position - viewModel.itemCount - 1].replyRows!![rPosition]
                )

            mBottomSheetDialogFragment.show(childFragmentManager, "Dialog")
        }

    }

    override fun onPublish(message: String, replyAndForward: String) {
        viewModel.replyData["message"] = message
        viewModel.replyData["replyAndForward"] = replyAndForward
        viewModel.onPostReply()
    }

    inner class PopClickListener(
        val id: String,
        val uid: String,
        val text: String?,
        val position: Int,
        val rPosition: Int?
    ) :
        PopupMenu.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem?): Boolean {
            when (item?.itemId) {
                R.id.block -> {
                    BlackListUtil.saveUid(uid)
                    val replyList = viewModel.feedReplyData.value?.toMutableList() ?: ArrayList()
                    if (rPosition == null || rPosition == -1) {
                        replyList.removeAt(position - viewModel.itemCount - 1)
                    } else {
                        replyList[position - viewModel.itemCount - 1].replyRows?.removeAt(rPosition)
                    }
                    viewModel.feedReplyData.postValue(replyList)
                    feedReplyAdapter.notifyItemChanged(position - viewModel.itemCount - 1)
                }

                R.id.report -> {
                    IntentUtil.startActivity<WebViewActivity>(requireContext()) {
                        putExtra(
                            "url",
                            "https://m.coolapk.com/mp/do?c=feed&m=report&type=feed_reply&id=$id"
                        )
                    }
                }

                R.id.delete -> {
                    val replyList =
                        viewModel.feedReplyData.value?.toMutableList() ?: ArrayList()
                    if (rPosition == null || rPosition == -1) {
                        replyList.removeAt(position - viewModel.itemCount - 1)
                    } else {
                        replyList[position - viewModel.itemCount - 1].replyRows?.removeAt(rPosition)
                    }
                    viewModel.feedReplyData.postValue(replyList)
                    feedReplyAdapter.notifyItemChanged(position - viewModel.itemCount - 1)
                    // appListener?.onDeleteFeedReply(id, position, rPosition)
                }

                R.id.copy -> {
                    IntentUtil.startActivity<CopyActivity>(requireContext()) {
                        putExtra("text", text)
                    }
                }

                R.id.show -> {
                    //appListener?.onShowTotalReply(position, ruid, id, rPosition)
                    ItemClickListener().showTotalReply(
                        id,
                        uid,
                        position,
                        rPosition
                    )
                }
            }
            return true
        }
    }
}