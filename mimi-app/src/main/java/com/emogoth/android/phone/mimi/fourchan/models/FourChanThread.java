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


public class FourChanThread {
    @SerializedName("posts")
    @Expose
    private List<FourChanPost> posts = new ArrayList<>();

    /**
     * @return The posts
     */
    public List<FourChanPost> getPosts() {
        return posts == null ? Collections.emptyList() : posts;
    }

    /**
     * @param posts The posts
     */
    public void setPosts(List<FourChanPost> posts) {
        this.posts = posts == null ? new ArrayList<>() : posts;
    }
}
