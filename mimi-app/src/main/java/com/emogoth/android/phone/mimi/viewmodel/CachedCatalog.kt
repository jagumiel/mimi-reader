package com.emogoth.android.phone.mimi.viewmodel

import com.mimireader.chanlib.models.ChanCatalog
import com.mimireader.chanlib.models.ChanPost

/** Catalog content restored from the local database after a refresh failure. */
class CachedCatalog(
    boardName: String,
    posts: List<ChanPost>,
    val error: Throwable
) : ChanCatalog() {
    init {
        setBoardName(boardName)
        setPosts(posts)
    }
}
