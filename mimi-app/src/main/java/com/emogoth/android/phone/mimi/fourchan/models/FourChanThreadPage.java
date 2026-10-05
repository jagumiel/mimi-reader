/*
 * Copyright (c) 2016. Eli Connelly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.emogoth.android.phone.mimi.fourchan.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class FourChanThreadPage {
    @SerializedName("page")
    @Expose
    private int page;
    @SerializedName("threads")
    @Expose
    private List<FourChanPost> threads = new ArrayList<>();

    /**
     * @return The page
     */
    public int getPage() {
        return page;
    }

    /**
     * @param page The page
     */
    public void setPage(int page) {
        this.page = page;
    }

    /**
     * @return The threads
     */
    public List<FourChanPost> getThreads() {
        return threads == null ? Collections.emptyList() : threads;
    }

    /**
     * @param threads The threads
     */
    public void setThreads(List<FourChanPost> threads) {
        this.threads = threads == null ? new ArrayList<>() : threads;
    }
}
