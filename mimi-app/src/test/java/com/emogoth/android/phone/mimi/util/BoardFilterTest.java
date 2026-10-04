package com.emogoth.android.phone.mimi.util;

import com.mimireader.chanlib.models.ChanBoard;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class BoardFilterTest {
    private final ChanBoard sfw = board("g", 1);
    private final ChanBoard nsfw = board("gif", 0);
    private final List<ChanBoard> boards = Arrays.asList(sfw, nsfw);

    @Test
    public void allKeepsEveryBoardInOrder() {
        final List<ChanBoard> filtered = BoardFilter.apply(boards, BoardFilter.ALL);

        assertEquals(Arrays.asList(sfw, nsfw), filtered);
        assertNotSame(boards, filtered);
    }

    @Test
    public void sfwOnlyKeepsWorkSafeBoards() {
        assertEquals(Arrays.asList(sfw), BoardFilter.apply(boards, BoardFilter.SFW_ONLY));
    }

    @Test
    public void nsfwOnlyKeepsNonWorkSafeBoards() {
        assertEquals(Arrays.asList(nsfw), BoardFilter.apply(boards, BoardFilter.NSFW_ONLY));
    }

    @Test
    public void unknownFilterFallsBackToAllBoards() {
        assertEquals(boards, BoardFilter.apply(boards, 99));
        assertEquals(BoardFilter.ALL, BoardFilter.normalize(99));
    }

    private static ChanBoard board(String name, int wsBoard) {
        final ChanBoard board = new ChanBoard();
        board.setName(name);
        board.setTitle(name);
        board.setWsBoard(wsBoard);
        return board;
    }
}
