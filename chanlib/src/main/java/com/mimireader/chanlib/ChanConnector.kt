package com.mimireader.chanlib

import android.webkit.MimeTypeMap
import com.mimireader.chanlib.models.*
import io.reactivex.Single
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

abstract class ChanConnector {
    abstract fun fetchBoards(): Single<List<ChanBoard>>
    abstract fun fetchCatalog(boardName: String): Single<ChanCatalog>
    abstract fun fetchThread(boardName: String, threadId: Long, cacheControl: String): Single<ChanThread>
    abstract fun fetchArchives(): Single<List<ChanArchive>>
    abstract fun fetchArchivedThread(board: String, threadId: Long, name: String, domain: String, url: String): Single<ArchivedChanThread>
    abstract fun post(boardName: String, params: Map<String, Any>): Single<Response<ResponseBody>>
    abstract fun login(token: String, pin: String): Single<Response<ResponseBody>>
    abstract fun getThumbUrl(boardName: String, id: String): String
    abstract val imageBaseUrl: String
    abstract fun getImageCountText(imageCount: Int): String
    abstract fun getRepliesCountText(repliesCount: Int): String
    protected fun getPartsFromMap(map: Map<String, Any>): Map<String, RequestBody> {
        val parts: MutableMap<String, RequestBody> = HashMap()
        for (entry in map.entries) {
            var key = entry.key
            var rb: RequestBody?
            try {
                if (entry.value is String) {
                    rb = RequestBody.create("text/plain".toMediaType(), entry.value as String)
                } else if (entry.value is Int) {
                    rb = RequestBody.create("text/plain".toMediaType(), entry.value.toString())
                } else if (entry.value is Long) {
                    rb = RequestBody.create("text/plain".toMediaType(), entry.value.toString())
                } else if (entry.value is File) {
                    if (entry.value != null) {
                        val f = entry.value as File?
                        if (f?.exists() == true) {
                            var extension: String? = null
                            val i = f.absolutePath.lastIndexOf('.')
                            if (i > 0) {
                                extension = f.absolutePath.substring(i + 1)
                            }
                            if (extension != null) {
                                val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                                rb = RequestBody.create("application/octet-stream".toMediaType(), f)
                                key = entry.key + "\"; filename=\"" + f.name
                            } else {
                                rb = null
                            }
                        } else {
                            rb = null
                        }
                    } else {
                        rb = null
                    }
                } else {
                    rb = null
                }
                if (rb != null) {
                    parts[key] = rb
                }
            } catch (e: Exception) {
            }
        }
        return parts
    }

    abstract class ChanConnectorBuilder {
        private var endpoint: String? = null
        private var postEndpoint: String? = null
        private var client: OkHttpClient? = null
        private var cacheDir: File? = null
        private var cacheSize = 10 * 1024 * 1024
        fun setCacheDirectory(dir: File?): ChanConnectorBuilder {
            cacheDir = dir
            return this
        }

        fun setEndpoint(endpoint: String?): ChanConnectorBuilder {
            this.endpoint = endpoint
            return this
        }

        fun setPostEndpoint(endpoint: String?): ChanConnectorBuilder {
            postEndpoint = endpoint
            return this
        }

        fun setClient(client: OkHttpClient?): ChanConnectorBuilder {
            this.client = client
            return this
        }

        fun setMaxCacheSize(size: Int): ChanConnectorBuilder {
            cacheSize = size
            return this
        }

        protected fun initRetrofit(isPost: Boolean): Retrofit {
            if (client == null) {
                val builder = OkHttpClient.Builder()
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .callTimeout(20, TimeUnit.SECONDS)
                        .writeTimeout(20, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                val directory = cacheDir
                if (directory != null && directory.exists()) {
                    builder.cache(Cache(directory, cacheSize.toLong()))
                }
                client = builder.build()
            }
            return Retrofit.Builder()
                    .baseUrl(if (isPost) postEndpoint else endpoint)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .build()
        }

        abstract fun <T : ChanConnector?> build(): T
    }

    companion object {
        const val CACHE_DEFAULT = ""
        const val CACHE_FORCE_NETWORK = "no-cache"
    }
}
