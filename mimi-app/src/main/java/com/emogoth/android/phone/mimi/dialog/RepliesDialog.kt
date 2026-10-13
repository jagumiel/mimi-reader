package com.emogoth.android.phone.mimi.dialog

import android.app.Dialog
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.*
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.DialogFragment
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.interfaces.GoToPostListener
import com.emogoth.android.phone.mimi.util.Extras
import com.emogoth.android.phone.mimi.util.RxUtil
import com.emogoth.android.phone.mimi.util.ThreadRegistry
import com.mimireader.chanlib.models.ChanPost
import com.mimireader.chanlib.models.ChanThread
import io.reactivex.disposables.Disposable

class RepliesDialog : DialogFragment(), GoToPostListener {
    private var boardName: String = ""
    private var threadId: Long = 0
    private var currentPostId: Long = 0
    private var repliesSubscription: Disposable? = null
    private lateinit var dialogBackCallback: OnBackPressedCallback

    var toolbar: Toolbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
        arguments?.let { extractExtras(it) }
        childFragmentManager.addOnBackStackChangedListener {
            updateBackNavigationState()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return inflater.inflate(R.layout.dialog_replies, container, false)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val componentDialog = ComponentDialog(requireContext(), theme)
        dialogBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                childFragmentManager.popBackStack()
            }
        }
        componentDialog.onBackPressedDispatcher.addCallback(this, dialogBackCallback)
        return componentDialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val closeButton = view.findViewById<View>(R.id.close_button)

        toolbar = view.findViewById(R.id.reply_dialog_toolbar)
        toolbar?.navigationIcon = null
        toolbar?.logo = null
        toolbar?.setNavigationOnClickListener {
            (dialog as? ComponentDialog)?.onBackPressedDispatcher?.onBackPressed()
        }
        closeButton.setOnClickListener { v: View? ->
            dismiss()
        }

        val contentFragment = RepliesDialogContentFragment()
        contentFragment.arguments = arguments
        contentFragment.updateReplies = {
            currentPostId = it.no
            updateReplies(it)
        }

        val ft = childFragmentManager.beginTransaction()
        ft.replace(R.id.replies_dialog_content, contentFragment)
        ft.addToBackStack("${boardName}_${threadId}_${currentPostId}")
        ft.commit()
    }

    private fun updateBackNavigationState() {
        val hasNestedReply = childFragmentManager.backStackEntryCount > 1
        if (::dialogBackCallback.isInitialized) {
            dialogBackCallback.isEnabled = hasNestedReply
        }
        if (hasNestedReply) {
            toolbar?.setNavigationIcon(R.drawable.ic_nav_arrow_back)
        } else {
            toolbar?.navigationIcon = null
        }
    }

    fun updateReplies(post: ChanPost) {
        val bundle = packPost(post)
        updateReplies(bundle)
    }

    fun updateReplies(bundle: Bundle) {
        val fragmentId = "${boardName}_${threadId}_${currentPostId}"
        val contentFragment = RepliesDialogContentFragment()
        contentFragment.arguments = bundle
        contentFragment.updateReplies = {
            currentPostId = it.no
            updateReplies(it)
        }

        val ft = childFragmentManager.beginTransaction()
        ft.replace(R.id.replies_dialog_content, contentFragment)
        ft.addToBackStack(fragmentId)
        ft.commit()

        updateBackNavigationState()
    }

    fun packPost(post: ChanPost): Bundle {
        val boardName = boardName
        val args = Bundle()
        args.putString(Extras.EXTRAS_BOARD_NAME, boardName)
        val postNumbers = ArrayList<String>(post.repliesFrom.size)
        for (p in post.repliesFrom) {
            postNumbers.add(p.no.toString())
        }
        args.putLong(Extras.EXTRAS_POST_ID, post.no)
        args.putStringArrayList(Extras.EXTRAS_POST_LIST, postNumbers)
        args.putLong(Extras.EXTRAS_THREAD_ID, threadId)

        return args
    }

    private fun extractExtras(bundle: Bundle) {
        if (bundle.containsKey(Extras.EXTRAS_BOARD_NAME)) {
            boardName = bundle.getString(Extras.EXTRAS_BOARD_NAME) ?: ""
        }
        if (bundle.containsKey(Extras.EXTRAS_POST_ID)) {
            currentPostId = bundle.getLong(Extras.EXTRAS_POST_ID)
        }
        if (bundle.containsKey(Extras.EXTRAS_THREAD_ID)) {
            threadId = bundle.getLong(Extras.EXTRAS_THREAD_ID)
        }
    }

    override fun onPause() {
        super.onPause()
        RxUtil.safeUnsubscribe(repliesSubscription)
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setGravity(Gravity.CENTER)
    }

    fun close() {
        dismiss()
    }

    companion object {
        val LOG_TAG = RepliesDialog::class.java.simpleName
        const val DIALOG_TAG = "reply_dialog_tag"

        @JvmStatic
        fun newInstance(thread: ChanThread, postItem: ChanPost): RepliesDialog {
            val boardName = thread.boardName
            val dialog = RepliesDialog()
            val args = Bundle()
            args.putString(Extras.EXTRAS_BOARD_NAME, boardName)
            val postNumbers = ArrayList<String>(postItem.repliesFrom.size)
            for (post in postItem.repliesFrom) {
                postNumbers.add(post.no.toString())
            }
            args.putLong(Extras.EXTRAS_POST_ID, postItem.no)
            args.putStringArrayList(Extras.EXTRAS_POST_LIST, postNumbers)
            args.putLong(Extras.EXTRAS_THREAD_ID, thread.threadId)

            dialog.arguments = args
            return dialog
        }

        @JvmStatic
        fun newInstance(thread: ChanThread, id: String): RepliesDialog {
//            if (thread.posts.size > 0 && !TextUtils.isEmpty(id)) {
                val boardName = thread.boardName
                val dialog = RepliesDialog()
                val args = Bundle()
                val postNumbers = ArrayList<String>()
                for (post in thread.posts) {
                    if (id == post.id) {
                        postNumbers.add(post.no.toString())
                    }
                }
                args.putString(Extras.EXTRAS_BOARD_NAME, boardName)
                if (TextUtils.isDigitsOnly(id)) {
                    args.putLong(Extras.EXTRAS_POST_ID, id.toLong())
                }
                args.putStringArrayList(Extras.EXTRAS_POST_LIST, postNumbers)
                args.putLong(Extras.EXTRAS_THREAD_ID, thread.threadId)

                dialog.arguments = args
                return dialog
//            }
//            return null
        }
    }

    init {
        Log.d(LOG_TAG, "New replies dialog created")
    }

    override fun goToPost(postId: Long) {
        if (parentFragment is GoToPostListener) {
            val l: GoToPostListener = parentFragment as GoToPostListener
            l.goToPost(postId)
            close()
        }
    }
}
