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

package com.emogoth.android.phone.mimi.fourchan;

import android.util.Log;

import androidx.annotation.NonNull;

import com.emogoth.android.phone.mimi.BuildConfig;
import com.emogoth.android.phone.mimi.app.MimiApplication;
import com.emogoth.android.phone.mimi.fourchan.api.FourChanApi;
import com.emogoth.android.phone.mimi.fourchan.api.FourChanPostApi;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanArchive;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanBoard;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanBoards;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanPost;
import com.emogoth.android.phone.mimi.fourchan.models.archives.FoolFuukaThreadConverter;
import com.mimireader.chanlib.ChanConnector;
import com.mimireader.chanlib.models.ArchivedChanThread;
import com.mimireader.chanlib.models.ChanArchive;
import com.mimireader.chanlib.models.ChanBoard;
import com.mimireader.chanlib.models.ChanCatalog;
import com.mimireader.chanlib.models.ChanPost;
import com.mimireader.chanlib.models.ChanThread;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class FourChanConnector extends ChanConnector {
    private static final String LOG_TAG = FourChanConnector.class.getSimpleName();

    private final FourChanApi api;
    private final FourChanPostApi postApi;

    private FourChanConnector(FourChanApi chanInterface, FourChanPostApi postInterface) {
        this.api = chanInterface;
        this.postApi = postInterface;
    }

    @NonNull
    @Override
    public Single<List<ChanBoard>> fetchBoards() {
        return api.fetchBoards()
                .observeOn(Schedulers.io())
                .doOnSuccess(fourChanBoards -> {

                    if (BuildConfig.DEBUG) {
                        final StringBuilder boards = new StringBuilder("Fetched Boards:\n");

                        if (fourChanBoards == null || fourChanBoards.getBoards() == null || fourChanBoards.getBoards().size() == 0) {
                            boards.append("<NO BOARDS FOUND>");
                        } else {
                            for (FourChanBoard board : fourChanBoards.getBoards()) {
                                boards.append(board.getTitle()).append(" (/").append(board.getName()).append("/)\n");
                            }
                        }

                        Log.d(LOG_TAG, boards.toString());
                    } else if (fourChanBoards == null || fourChanBoards.getBoards() == null || fourChanBoards.getBoards().size() == 0) {
                        Log.e(LOG_TAG, "No boards returned from API");
                    } else {
                        Log.d(LOG_TAG, "Fetched Boards");
                    }
                })
                .map(FourChanBoards::toBoardList);
    }

    @NonNull
    @Override
    public Single<ChanCatalog> fetchCatalog(@NonNull final String boardName) {
        return api.fetchCatalog(boardName)
                .observeOn(Schedulers.io())
                .map(fourChanThreadPages -> {
                    Log.d(LOG_TAG, "Fetched catalog");
                    ChanCatalog cat = new ChanCatalog();
                    cat.setBoardName(boardName);
                    List<ChanPost> posts = new ArrayList<>();
                    for (FourChanPost fourChanPost : FourChanResponseValidator.requireCatalogPosts(fourChanThreadPages)) {
                        fourChanPost.processComment(MimiApplication.getInstance().getApplicationContext(), boardName, 0);
                        posts.add(fourChanPost.toPost());
                    }
                    cat.addPosts(posts);

                    return cat;
                });
    }

    @NonNull
    @Override
    public Single<ChanThread> fetchThread(@NonNull final String boardName, final long threadId, final String cacheControl) {
        final String cache = cacheControl == null || cacheControl.isEmpty() ? null : cacheControl;
        return api.fetchThread(boardName, threadId, cache)
                .map(fourChanThread -> {
                    List<ChanPost> posts = new ArrayList<>();
                    for (FourChanPost fourChanPost : FourChanResponseValidator.requireThreadPosts(fourChanThread, threadId)) {
                        posts.add(fourChanPost.toPost());
                    }

                    return new ChanThread(boardName, threadId, posts);
                });
    }

    @NonNull
    @Override
    public Single<List<ChanArchive>> fetchArchives() {
        return api.fetchArchives("https://raw.githubusercontent.com/ccd0/4chan-x/master/src/Archive/archives.json")
                .observeOn(Schedulers.io())
                .toObservable()
                .flatMapIterable((Function<List<FourChanArchive>, Iterable<FourChanArchive>>) fourChanArchives -> fourChanArchives)
                .flatMap((Function<FourChanArchive, ObservableSource<ChanArchive>>) fourChanArchive -> {
                    final ChanArchive chanArchive = new ChanArchive.Builder()
                            .boards(fourChanArchive.getBoards())
                            .files(fourChanArchive.getFiles())
                            .domain(fourChanArchive.getDomain())
                            .http(fourChanArchive.getHttp())
                            .https(fourChanArchive.getHttps())
                            .software(fourChanArchive.getSoftware())
                            .uid(fourChanArchive.getUid())
                            .name(fourChanArchive.getName())
                            .reports(fourChanArchive.getReports())
                            .build();

                    return Observable.just(chanArchive);
                })
                .toList();
    }

    @NonNull
    @Override
    public Single<ArchivedChanThread> fetchArchivedThread(@NonNull String board, long threadId, @NonNull String name, @NonNull String domain, @NonNull String url) {
        return api.fetchArchivedThread(url)
                .observeOn(Schedulers.io())
                .map(foolFuukaThread -> new FoolFuukaThreadConverter(foolFuukaThread).toArchivedThread(board, threadId, name, domain));
    }

    @NonNull
    @Override
    public Single<Response<ResponseBody>> post(@NonNull String boardName, @NonNull Map<String, ?> params) {
        final Map<String, RequestBody> parts = getPartsFromMap(params);

        return postApi.post(boardName, parts)
                .observeOn(Schedulers.io())
                .onErrorReturn(throwable -> {
                    Log.e(LOG_TAG, "Error posting comment", throwable);
                    return badResponse(throwable);
                });

    }

    @NonNull
    @Override
    public Single<Response<ResponseBody>> login(@NonNull String token, @NonNull String pin) {
        MediaType type = MediaType.parse("text/plain");
        RequestBody tokenRequestBody = RequestBody.create(type, token);
        RequestBody pinRequestBody = RequestBody.create(type, pin);
        RequestBody longLoginRequestBody = RequestBody.create(type, "no");
        RequestBody actRequestBody = RequestBody.create(type, "do_login");
        return postApi.login(tokenRequestBody, pinRequestBody, longLoginRequestBody, actRequestBody)
                .observeOn(Schedulers.io())
                .onErrorReturn(FourChanConnector::badResponse);
    }

    @NonNull
    public String getThumbUrl(@NonNull String boardName, @NonNull String id) {
        return FourChanEndpoints.Image + "/" + boardName + "/" + id + "s.jpg";
    }

    @NonNull
    public String getImageBaseUrl() {
        return FourChanEndpoints.Image;
    }

    @NonNull
    @Override
    public String getImageCountText(int imageCount) {

        if (imageCount == 1) {
            return "1 image";
        }

        return imageCount + " images";
    }

    @NonNull
    @Override
    public String getRepliesCountText(int repliesCount) {
        if (repliesCount == 1) {
            return "1 reply";
        }

        return repliesCount + " replies";
    }

    public static String getDefaultEndpoint() {
        return FourChanEndpoints.Default;
    }

    public static String getDefaultPostEndpoint() {
        return FourChanEndpoints.Post;
    }

    private static Response<ResponseBody> badResponse(Throwable throwable) {
        return Response.error(-1, ResponseBody.create(MediaType.parse("text/plain"), throwable.getLocalizedMessage()));
    }

    public static class Builder extends ChanConnectorBuilder {

        @Override
        @SuppressWarnings("unchecked")
        public FourChanConnector build() {
            FourChanApi api = initRetrofit(false).create(FourChanApi.class);
            FourChanPostApi postApi = initRetrofit(true).create(FourChanPostApi.class);
            return new FourChanConnector(api, postApi);
        }
    }

}
