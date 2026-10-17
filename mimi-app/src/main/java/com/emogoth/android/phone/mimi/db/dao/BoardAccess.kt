package com.emogoth.android.phone.mimi.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import com.emogoth.android.phone.mimi.db.MimiDatabase
import com.emogoth.android.phone.mimi.db.models.Board
import io.reactivex.Flowable
import io.reactivex.Single

@Dao
abstract class BoardAccess : BaseDao<Board>() {
    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE}")
    abstract fun getAll(): Flowable<List<Board>>

    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE}")
    abstract fun getAllSnapshot(): List<Board>

    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE} WHERE ${Board.VISIBLE} == 1")
    abstract fun getVisibleBoards(): Flowable<List<Board>>

    @Query(value = "SELECT * FROM ${MimiDatabase.BOARDS_TABLE} WHERE ${Board.VISIBLE} == 1 ORDER BY ${Board.NAME} ASC")
    abstract fun getAllOrderByName(): Flowable<List<Board>>

    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE} WHERE ${Board.VISIBLE} == 1 ORDER BY ${Board.TITLE} ASC")
    abstract fun getAllOrderByTitle(): Flowable<List<Board>>

    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE} WHERE ${Board.VISIBLE} == 1 ORDER BY ${Board.ACCESS_COUNT} DESC")
    abstract fun getAllOrderByAccessCount(): Flowable<List<Board>>

    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE} WHERE ${Board.VISIBLE} == 1 ORDER BY ${Board.POST_COUNT} DESC")
    abstract fun getAllOrderByPostCount(): Flowable<List<Board>>

    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE} WHERE ${Board.VISIBLE} == 1 ORDER BY ${Board.LAST_ACCESSED} DESC")
    abstract fun getAllOrderByLastAccessed(): Flowable<List<Board>>

    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE} WHERE ${Board.VISIBLE} == 1 ORDER BY ${Board.FAVORITE} DESC")
    abstract fun getAllOrderByFavorite(): Flowable<List<Board>>

    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE} WHERE ${Board.VISIBLE} == 1 ORDER BY ${Board.ORDER_INDEX} ASC")
    abstract fun getAllOrderByCustom(): Flowable<List<Board>>

    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE} WHERE ${Board.VISIBLE} == 1 ORDER BY ${Board.CATEGORY} ASC, ${Board.NAME} ASC")
    abstract fun getAllOrderByCategory(): Flowable<List<Board>>

    @Query("SELECT * FROM ${MimiDatabase.BOARDS_TABLE} WHERE ${Board.NAME} = :boardName")
    abstract fun getBoard(boardName: String): Single<Board>

    @Update
    abstract fun updateBoard(vararg board: Board): Int
}
