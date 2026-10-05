package com.emogoth.android.phone.mimi.db

import android.content.Context
import android.util.Log
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.db.models.Board
import com.mimireader.chanlib.models.ChanBoard
import io.reactivex.Flowable
import io.reactivex.Single
import kotlin.collections.ArrayList

object BoardTableConnection {
    const val LOG_TAG = "BoardTableConnection"

    @JvmStatic
    fun fetchBoards(orderBy: Int): Single<List<Board>> {
        val db = MimiDatabase.getInstance()?.boards() ?: return Single.just(emptyList())

        val boardsObs: Flowable<List<Board>> =
                when (orderBy) {
                    0 -> db.getAllOrderByName()
                    1 -> db.getAllOrderByTitle()
                    2 -> db.getAllOrderByName()
                    3 -> db.getAllOrderByAccessCount()
                    4 -> db.getAllOrderByPostCount()
                    5 -> db.getAllOrderByLastAccessed()
                    6 -> db.getAllOrderByFavorite()
                    7 -> db.getAllOrderByCustom()
                    else -> db.getAllOrderByName()
                }
        return boardsObs.first(emptyList())
    }

    @JvmStatic
    fun observeBoards(orderBy: Int): Flowable<List<ChanBoard>> {
        val db = MimiDatabase.getInstance()?.boards()
                ?: return Flowable.just(emptyList())

        val boardsObs: Flowable<List<Board>> =
                when (orderBy) {
                    0 -> db.getAllOrderByName()
                    1 -> db.getAllOrderByTitle()
                    2 -> db.getAllOrderByName()
                    3 -> db.getAllOrderByAccessCount()
                    4 -> db.getAllOrderByPostCount()
                    5 -> db.getAllOrderByLastAccessed()
                    6 -> db.getAllOrderByFavorite()
                    7 -> db.getAllOrderByCustom()
                    else -> db.getAllOrderByName()
                }
        return boardsObs.flatMap { Flowable.just(convertBoardDbModelsToChanBoards(it)) }
    }

    @JvmStatic
    fun fetchBoard(boardName: String): Single<ChanBoard> {
        return if (boardName.isEmpty()) {
            Single.just(ChanBoard())
        } else {
            MimiDatabase.getInstance()?.boards()?.getBoard(boardName)?.onErrorReturn { Board() }?.flatMap { Single.just(convertBoardDbModelToBoard(it)) }
                    ?: Single.just(ChanBoard())
        }

    }

    @JvmStatic
    fun setBoardFavorite(boardName: String, favorite: Boolean): Single<Boolean> {
        return MimiDatabase.getInstance()?.boards()?.getBoard(boardName)?.onErrorReturn { Board() }?.flatMap { board ->
            if (board.id == null) {
                Single.just(false)
            } else {
                val b = Board(
                        id = board.id,
                        title = board.title,
                        name = board.name,
                        favorite = favorite,
                        visible = board.visible,
                        nsfw = board.nsfw,
                        pages = board.pages,
                        perPage = board.perPage,
                        maxFileSize = board.maxFileSize,
                        accessCount = board.accessCount,
                        postCount = board.postCount,
                        lastAccessed = board.lastAccessed,
                        orderIndex = board.orderIndex,
                        category = board.category)

                val updated = MimiDatabase.getInstance()?.boards()?.updateBoard(b) ?: 0 > 0
                Single.just(updated)
            }
        } ?: Single.just(false)
    }

    @JvmStatic
    fun incrementAccessCount(boardName: String): Single<Boolean> {
        return MimiDatabase.getInstance()?.boards()?.getBoard(boardName)?.onErrorReturn { Board() }?.flatMap { board ->
            if (board.id == null) {
                Single.just(false)
            } else {
                val b = Board(
                        id = board.id,
                        title = board.title,
                        name = board.name,
                        favorite = board.favorite,
                        visible = board.visible,
                        nsfw = board.nsfw,
                        pages = board.pages,
                        perPage = board.perPage,
                        maxFileSize = board.maxFileSize,
                        accessCount = board.accessCount + 1,
                        postCount = board.postCount,
                        lastAccessed = board.lastAccessed,
                        orderIndex = board.orderIndex,
                        category = board.category)

                val updated = MimiDatabase.getInstance()?.boards()?.updateBoard(b) ?: 0 > 0
                Single.just(updated)
            }
        } ?: Single.just(false)
    }

    @JvmStatic
    fun incrementPostCount(boardName: String): Single<Boolean> {
        return MimiDatabase.getInstance()?.boards()?.getBoard(boardName)?.onErrorReturn { Board() }?.flatMap { board ->
            if (board.id == null) {
                Single.just(false)
            } else {
                val b = Board(
                        id = board.id,
                        title = board.title,
                        name = board.name,
                        favorite = board.favorite,
                        visible = board.visible,
                        nsfw = board.nsfw,
                        pages = board.pages,
                        perPage = board.perPage,
                        maxFileSize = board.maxFileSize,
                        accessCount = board.accessCount,
                        postCount = board.postCount + 1,
                        lastAccessed = board.lastAccessed,
                        orderIndex = board.orderIndex,
                        category = board.category)

                val updated = MimiDatabase.getInstance()?.boards()?.updateBoard(b) ?: 0 > 0
                Single.just(updated)
            }
        } ?: Single.just(false)
    }

    @JvmStatic
    fun updateLastAccess(boardName: String): Single<Boolean> {
        return MimiDatabase.getInstance()?.boards()?.getBoard(boardName)?.onErrorReturn { Board() }?.flatMap { board ->
            if (board.id == null) {
                Single.just(false)
            } else {
                val b = Board(
                        id = board.id,
                        title = board.title,
                        name = board.name,
                        favorite = board.favorite,
                        visible = board.visible,
                        nsfw = board.nsfw,
                        pages = board.pages,
                        perPage = board.perPage,
                        maxFileSize = board.maxFileSize,
                        accessCount = board.accessCount,
                        postCount = board.postCount,
                        lastAccessed = System.currentTimeMillis(),
                        orderIndex = board.orderIndex,
                        category = board.category)

                val updated = MimiDatabase.getInstance()?.boards()?.updateBoard(b) ?: 0 > 0
                Single.just(updated)
            }
        } ?: Single.just(false)
    }

    @JvmStatic
    fun resetStats(): Single<Boolean> {
        return MimiDatabase.getInstance()?.boards()?.getVisibleBoards()?.flatMapIterable {
            val allBoards = ArrayList<Board>()
            allBoards.addAll(it)
            allBoards
        }?.single(Board())
                ?.flatMap { board ->
                    val b = Board(
                            id = board.id,
                            title = board.title,
                            name = board.name,
                            favorite = board.favorite,
                            visible = board.visible,
                            nsfw = board.nsfw,
                            pages = board.pages,
                            perPage = board.perPage,
                            maxFileSize = board.maxFileSize,
                            accessCount = 0,
                            postCount = 0,
                            lastAccessed = 0,
                            orderIndex = board.orderIndex,
                            category = board.category)

                    val updated = MimiDatabase.getInstance()?.boards()?.updateBoard(b) ?: 0 > 0
                    Single.just(updated)
                } ?: Single.just(false)
    }

    @JvmStatic
    fun updateBoardOrder(boards: List<ChanBoard>): Single<Boolean> {
        return MimiDatabase.getInstance()?.boards()?.getVisibleBoards()?.first(emptyList())?.flatMap { boardList ->

            val orderedList = ArrayList<Board>(boards.size)
            for (i in boards.indices) {
                for (j in boardList.indices) {
                    val board = boardList[j]
                    if (boards[i].name == board.name) {
                        val b = Board(
                                id = board.id,
                                title = board.title,
                                name = board.name,
                                favorite = board.favorite,
                                visible = board.visible,
                                nsfw = board.nsfw,
                                pages = board.pages,
                                perPage = board.perPage,
                                maxFileSize = board.maxFileSize,
                                accessCount = board.accessCount,
                                postCount = board.postCount,
                                lastAccessed = board.lastAccessed,
                                orderIndex = i,
                                category = board.category)

                        orderedList.add(b)
                        break
                    }
                }
            }

            Single.just(orderedList)
        }?.flatMap {
            val updated = MimiDatabase.getInstance()?.boards()?.update(it) ?: 0 > 0
            Single.just(updated)
        } ?: Single.just(false)
    }

    fun convertBoardDbModelToBoard(oldBoard: Board): ChanBoard {
        if (oldBoard.id == null) {
            return ChanBoard()
        }

        val newBoard = ChanBoard()
        val boardName = oldBoard.name // Path is now Name
        val boardTitle = oldBoard.title // Name is now Title
        val ws = if (oldBoard.nsfw) 0 else 1
        val favorite = oldBoard.favorite
        val postsPerPage = oldBoard.perPage
        val pages = oldBoard.pages
        newBoard.name = boardName
        newBoard.title = boardTitle
        newBoard.wsBoard = ws
        newBoard.isFavorite = favorite
        newBoard.perPage = postsPerPage
        newBoard.pages = pages
        return newBoard
    }

    @JvmStatic
    fun convertBoardDbModelsToChanBoards(boards: List<Board>): List<ChanBoard> {
        if (boards.isEmpty()) {
            return emptyList()
        }

        val chanBoards = ArrayList<ChanBoard>(boards.size)
        for (board in boards) {
            val b = convertBoardDbModelToBoard(board)
            if (!b.isEmpty) {
                chanBoards.add(b)
            }
        }
        return chanBoards
    }

    private fun convertChanBoardToDbModel(chanBoard: ChanBoard): Board {
        return Board(title = chanBoard.title, name = chanBoard.name, favorite = chanBoard.isFavorite, nsfw = chanBoard.wsBoard == 0, pages = chanBoard.pages, perPage = chanBoard.perPage, maxFileSize = chanBoard.maxFilesize)
    }

    private fun convertChanBoardListToDbModel(chanBoards: List<ChanBoard>): List<Board> {
        val boards = ArrayList<Board>(chanBoards.size)
        for (cb in chanBoards) {
            boards.add(convertChanBoardToDbModel(cb))
        }

        return boards
    }

    @JvmStatic
    fun filterVisibleBoards(context: Context, boards: List<ChanBoard>): List<ChanBoard> {
        val visibleBoardNames = ArrayList<String>()
        if (boards.isNotEmpty()) {
            val boardsArray = context.resources.getStringArray(R.array.boards)
            for (i in boardsArray.indices) {
                visibleBoardNames.add(boardsArray[i].replace("/".toRegex(), ""))
            }
            val filteredBoards: MutableList<ChanBoard> = ArrayList(boards.size)
            for (board in boards) {
                if (visibleBoardNames.indexOf(board.name) >= 0) {
                    filteredBoards.add(board)
                }
            }
            return filteredBoards
        }
        return emptyList()
    }

    @JvmStatic
    fun initDefaultBoards(context: Context): Single<List<ChanBoard>> {
        return Single
                .defer { Single.just(context.resources.getStringArray(R.array.boards)) }
                .map { strings: Array<String> ->
                    val boards: MutableList<ChanBoard> = ArrayList(strings.size)
                    for (boardName in strings) {
                        val board = ChanBoard()
                        board.name = boardName
                        board.title = ""
                        board.wsBoard = 1
                        boards.add(board)
                    }
                    boards
                }
                .flatMap { boards -> saveBoards(boards).map { boards } }
                .toFlowable()
                .flatMapIterable { board: List<ChanBoard> -> board }
                .flatMap { s: ChanBoard -> setBoardVisibility(s, true).toFlowable() }
                .toList()
    }

    @JvmStatic
    fun saveBoards(chanBoards: List<ChanBoard>): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            Log.d(LOG_TAG, "Calling saveBoards()")
            if (chanBoards.isEmpty()) {
                return@singleOnIo false
            }
            val boards = convertChanBoardListToDbModel(chanBoards)
            val boardDao = MimiDatabase.getInstance()?.boards() ?: return@singleOnIo false
            boardDao.upsert(boards)
            true
        }
    }

    @JvmStatic
    fun setBoardVisibility(board: ChanBoard, visible: Boolean): Single<ChanBoard> {
        Log.d(LOG_TAG, "Calling setBoardVisibility(${board.name}, $visible)")
        return MimiDatabase.getInstance()?.boards()?.getBoard(board.name)?.onErrorReturn { Board() }?.flatMap { bd ->
            if (bd.id == null) {
                Single.just(ChanBoard())
            } else {
                val b = Board(
                        id = bd.id,
                        title = bd.title,
                        name = bd.name,
                        favorite = bd.favorite,
                        visible = visible,
                        nsfw = bd.nsfw,
                        pages = bd.pages,
                        perPage = bd.perPage,
                        maxFileSize = bd.maxFileSize,
                        accessCount = bd.accessCount,
                        postCount = bd.postCount,
                        lastAccessed = bd.lastAccessed,
                        orderIndex = bd.orderIndex,
                        category = bd.category)
                if (bd.visible != visible) {
                    val updated = MimiDatabase.getInstance()?.boards()?.updateBoard(b) ?: 0 > 0
                }
                Single.just(convertBoardDbModelToBoard(b))
            }
        } ?: Single.just(ChanBoard())
    }
}
