package com.emogoth.android.phone.mimi.db

import io.reactivex.Flowable
import io.reactivex.FlowableTransformer
import io.reactivex.Single
import io.reactivex.SingleTransformer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers

object DatabaseUtils {
    val LOG_TAG = DatabaseUtils::class.java.simpleName

    /** Defers synchronous Room work and always executes it away from the caller thread. */
    @JvmStatic
    fun <T> singleOnIo(operation: () -> T): Single<T> {
        return Single.fromCallable(operation).subscribeOn(Schedulers.io())
    }

    @JvmStatic
    fun <T> applySchedulers(): FlowableTransformer<T, T> {
        return FlowableTransformer { f: Flowable<T> ->
            f.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
        }
    }

    @JvmStatic
    fun <T> applySingleSchedulers(): SingleTransformer<T, T> {
        return SingleTransformer { f: Single<T> ->
            f.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
        }
    }
}
