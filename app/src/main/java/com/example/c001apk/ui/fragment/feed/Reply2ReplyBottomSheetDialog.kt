package com.example.c001apk.ui.fragment.feed

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.absinthe.libraries.utils.extensions.dp
import com.absinthe.libraries.utils.utils.UiUtils
import com.example.c001apk.R
import com.example.c001apk.adapter.FooterAdapter
import com.example.c001apk.adapter.Reply2ReplyTotalAdapter
import com.example.c001apk.databinding.DialogReplyToReplyBottomSheetBinding
import com.example.c001apk.logic.model.TotalReplyResponse
import com.example.c001apk.ui.fragment.ReplyBottomSheetDialog
import com.example.c001apk.ui.fragment.minterface.IOnPublishClickListener
import com.example.c001apk.util.PrefManager
import com.example.c001apk.view.ReplyItemDecoration
import com.example.c001apk.view.StaggerItemDecoration
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class Reply2ReplyBottomSheetDialog : BottomSheetDialogFragment(), IOnPublishClickListener {

    private lateinit var binding: DialogReplyToReplyBottomSheetBinding
    private val viewModel by lazy { ViewModelProvider(this)[Reply2ReplyBottomSheetViewModel::class.java] }
    private lateinit var mAdapter: Reply2ReplyTotalAdapter
    private lateinit var footerAdapter: FooterAdapter
    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var bottomSheetDialog: ReplyBottomSheetDialog
    var oriReply: ArrayList<TotalReplyResponse.Data> = ArrayList()
    private lateinit var sLayoutManager: StaggeredGridLayoutManager

    companion object {
        fun newInstance(
            position: Int,
            fuid: String,
            uid: String,
            id: String
        ): Reply2ReplyBottomSheetDialog {
            val args = Bundle()
            args.putString("FUID", fuid)
            args.putString("UID", uid)
            args.putString("ID", id)
            args.putInt("POSITION", position)
            val fragment = Reply2ReplyBottomSheetDialog()
            fragment.arguments = args
            return fragment
        }
    }

    private fun setData() {
        arguments?.let {
            viewModel.id = it.getString("ID", "")
            viewModel.fuid = it.getString("FUID", "")
            viewModel.uid = it.getString("UID", "")
            viewModel.position = it.getInt("POSITION")
            viewModel.oriReply = oriReply
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        object : BottomSheetDialog(requireContext(), theme) {

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()

                window?.let {
                    it.attributes?.windowAnimations =
                        com.absinthe.libraries.utils.R.style.DialogAnimation
                    WindowCompat.setDecorFitsSystemWindows(it, false)
                    UiUtils.setSystemBarStyle(it)
                    WindowInsetsControllerCompat(it, it.decorView)
                        .isAppearanceLightNavigationBars = !UiUtils.isDarkMode()
                }

                findViewById<View>(com.google.android.material.R.id.container)?.fitsSystemWindows =
                    false
                findViewById<View>(com.google.android.material.R.id.coordinator)?.fitsSystemWindows =
                    false

            }

        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = DialogReplyToReplyBottomSheetBinding.inflate(inflater)
        return binding.root
    }

    @SuppressLint("InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setData()
        initView()
        initData()
        initScroll()
        initObserve()

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

        /*viewModel.postReplyData.observe(viewLifecycleOwner) { result ->
            if (viewModel.isPostReply) {
                viewModel.isPostReply = false

                val response = result.getOrNull()
                response?.let {
                    if (response.data != null) {
                        if (response.data.messageStatus == 1 || response.data.messageStatus == 2) {
                            bottomSheetDialog.editText.text = null
                            if (response.data.messageStatus == 1)
                                Toast.makeText(requireContext(), "回复成功", Toast.LENGTH_SHORT)
                                    .show()
                            bottomSheetDialog.cancel()
                            viewModel.replyTotalList.add(
                                viewModel.r2rPosition + 1,
                                TotalReplyResponse.Data(
                                    null,
                                    "feed_reply",
                                    viewModel.id.toString(),
                                    viewModel.ruid.toString(),
                                    PrefManager.uid,
                                    viewModel.id.toString(),
                                    URLDecoder.decode(PrefManager.username, "UTF-8"),
                                    viewModel.uname.toString(),
                                    viewModel.replyData["message"].toString(),
                                    "",
                                    null,
                                    System.currentTimeMillis() / 1000,
                                    "0",
                                    "0",
                                    PrefManager.userAvatar,
                                    ArrayList(),
                                    0,
                                    TotalReplyResponse.UserAction(0)
                                )
                            )
                            mAdapter.notifyItemInserted(viewModel.r2rPosition + 1)
                        }
                    } else {
                        Toast.makeText(requireContext(), response.message, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }*/

        /*viewModel.postDeleteData.observe(viewLifecycleOwner) { result ->
            if (viewModel.isNew) {
                viewModel.isNew = false

                val response = result.getOrNull()
                if (response != null) {
                    if (response.data == "删除成功") {
                        Toast.makeText(requireContext(), response.data, Toast.LENGTH_SHORT).show()
                        viewModel.replyTotalList.removeAt(viewModel.position)
                        mAdapter.notifyItemRemoved(viewModel.position)
                    } else if (!response.message.isNullOrEmpty()) {
                        Toast.makeText(requireContext(), response.message, Toast.LENGTH_SHORT)
                            .show()
                    }
                } else {
                    result.exceptionOrNull()?.printStackTrace()
                }
            }
        }*/

    }

    private fun initObserve() {
        viewModel.changeState.observe(viewLifecycleOwner) {
            footerAdapter.setLoadState(it.first, it.second)
            footerAdapter.notifyItemChanged(0)
            if (it.first != FooterAdapter.LoadState.LOADING) {
                binding.indicator.parent.isIndeterminate = false
                binding.indicator.parent.visibility = View.GONE
                viewModel.isLoadMore = false
                viewModel.isRefreshing = false
            }
        }

        viewModel.totalReplyData.observe(viewLifecycleOwner) {
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

    private fun showReplyErrorMessage() {
        binding.replyErrorMessage.visibility = View.VISIBLE
        binding.replyErrorMessage.text = viewModel.errorMessage
    }

    private fun initScroll() {
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {

                    if (viewModel.listSize != -1 && !viewModel.isEnd && isAdded)
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
        viewModel.fetchReplyTotal()
    }

    private fun initData() {
        if (viewModel.listSize == -1) {
            binding.indicator.parent.visibility = View.VISIBLE
            binding.indicator.parent.isIndeterminate = true
            viewModel.isEnd = false
            viewModel.isLoadMore = false
            viewModel.fetchReplyTotal()
        }
    }

    private fun initView() {
        mAdapter =
            Reply2ReplyTotalAdapter(
                requireContext(),
                viewModel.fuid.toString(),
                viewModel.uid.toString(),
                viewModel.position!!,
                //  viewModel.replyTotalList
            )
        footerAdapter = FooterAdapter(ReloadListener())
        mLayoutManager = LinearLayoutManager(requireContext())
        sLayoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)

        binding.recyclerView.apply {
            adapter = ConcatAdapter()
            layoutManager =
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                    mLayoutManager
                else sLayoutManager
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                addItemDecoration(ReplyItemDecoration(requireContext(), 1))
            else
                addItemDecoration(StaggerItemDecoration(10.dp))
        }
    }

    override fun onStart() {
        super.onStart()
        val view: FrameLayout =
            dialog?.findViewById(com.google.android.material.R.id.design_bottom_sheet)!!
        view.layoutParams.height = -1
        view.layoutParams.width = -1
        val behavior = BottomSheetBehavior.from(view)
        if (behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
            behavior.halfExpandedRatio = 0.75F
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }
    }


    @SuppressLint("InflateParams")
    private fun initReply() {
        bottomSheetDialog.apply {
            rid = viewModel.rid.toString()
            ruid = viewModel.ruid.toString()
            uname = viewModel.uname.toString()
            setData()
            show()
        }
    }

    override fun onPublish(message: String, replyAndForward: String) {
        viewModel.replyData["message"] = message
        viewModel.replyData["replyAndForward"] = replyAndForward
        viewModel.onPostReply()
    }

    inner class ReloadListener : FooterAdapter.FooterListener {
        override fun onReLoad() {
            loadMore()
        }
    }
}