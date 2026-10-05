package com.emogoth.android.phone.mimi.db

import com.emogoth.android.phone.mimi.db.MimiDatabase.Companion.getInstance
import com.emogoth.android.phone.mimi.db.models.PostOption
import io.reactivex.Single

object PostOptionTableConnection {
    val LOG_TAG = PostOptionTableConnection::class.java.simpleName

    @JvmStatic
    fun fetchPostOptions(): Single<List<PostOption>> {
        return getInstance()?.postOptions()?.getAll() ?: Single.just(emptyList())
    }

    @JvmStatic
    fun deletePostOption(id: String): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val postOptionDao = getInstance()?.postOptions() ?: return@singleOnIo false
            postOptionDao.remove(id)
            true
        }
    }

    @JvmStatic
    fun putPostOption(option: String): Single<Boolean> {
        // Either increment() or insert() will fail
        return getInstance()?.postOptions()?.increment(option, System.currentTimeMillis())
                ?.map { integer: Int ->
                    if (integer <= 0) {
                        val postOption = PostOption(null, option, System.currentTimeMillis(), 1)
                        getInstance()!!.postOptions().insert(postOption)
                    }
                    true
                } ?: Single.just(false)
    }
}
