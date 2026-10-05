package com.emogoth.android.phone.mimi.db

import com.emogoth.android.phone.mimi.db.MimiDatabase.Companion.getInstance
import com.emogoth.android.phone.mimi.db.models.Filter
import io.reactivex.Single

object FilterTableConnection {
    @JvmStatic
    fun fetchFilter(): Single<List<Filter>> {
        return getInstance()?.filters()?.getAll() ?: Single.just(emptyList())
    }

    @JvmStatic
    fun fetchFilter(boardName: String, filterName: String): Single<Filter> {
        return getInstance()?.filters()?.getFilter(boardName, filterName) ?: Single.just(Filter())
    }

    @JvmStatic
    fun fetchFiltersByBoard(boardName: String): Single<List<Filter>> {
        return getInstance()?.filters()?.getFiltersByBoard(boardName) ?: Single.just(emptyList())
    }

    @JvmStatic
    fun fetchFiltersByName(filterName: String): Single<List<Filter>> {
        return getInstance()?.filters()?.getFiltersByName(filterName) ?: Single.just(emptyList())
    }

    @JvmStatic
    fun addFilter(filter: Filter): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val filterDao = getInstance()?.filters() ?: return@singleOnIo false
            filterDao.upsert(filter)
            true
        }
    }

    @JvmStatic
    fun removeFilter(boardName: String, name: String): Single<Boolean> {
        return getInstance()?.filters()?.removeFilter(boardName, name)?.map { value: Int -> value > 0 }
                ?: Single.just(false)
    }
}
