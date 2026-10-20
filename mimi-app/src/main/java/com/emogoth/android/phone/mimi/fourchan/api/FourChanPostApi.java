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

package com.emogoth.android.phone.mimi.fourchan.api;


import java.util.List;
import okhttp3.MultipartBody;

import io.reactivex.Single;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

public interface FourChanPostApi {

    @Multipart
    @POST("{boardName}/post")
    Single<Response<ResponseBody>> post(@Path("boardName") String boardName, @Part List<MultipartBody.Part> parts);

    @Multipart
    @POST("auth")
    Single<Response<ResponseBody>> login(@Part("id") RequestBody token, @Part("pin") RequestBody pin, @Part("long_login") RequestBody longLogin, @Part("act") RequestBody act);

}
