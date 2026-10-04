package com.emogoth.android.phone.mimi.adapter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class TabItemIdentityTest {
    @Test
    public void identityIsStableForTheSameTab() {
        final TabPagerAdapter.TabItem first = new TabPagerAdapter.TabItem(
                TabPagerAdapter.TabType.THREAD, null, 123L, "g", "123");
        final TabPagerAdapter.TabItem restored = new TabPagerAdapter.TabItem(
                TabPagerAdapter.TabType.THREAD, null, 123L, "g", "123");

        assertEquals(first.getStableId(), restored.getStableId());
    }

    @Test
    public void identityChangesWhenTabContentChanges() {
        final TabPagerAdapter.TabItem thread = new TabPagerAdapter.TabItem(
                TabPagerAdapter.TabType.THREAD, null, 123L, "g", "123");
        final TabPagerAdapter.TabItem anotherThread = new TabPagerAdapter.TabItem(
                TabPagerAdapter.TabType.THREAD, null, 456L, "g", "456");
        final TabPagerAdapter.TabItem anotherBoard = new TabPagerAdapter.TabItem(
                TabPagerAdapter.TabType.THREAD, null, 123L, "v", "123");

        assertNotEquals(thread.getStableId(), anotherThread.getStableId());
        assertNotEquals(thread.getStableId(), anotherBoard.getStableId());
    }
}
