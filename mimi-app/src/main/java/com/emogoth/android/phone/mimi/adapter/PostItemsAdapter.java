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

package com.emogoth.android.phone.mimi.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.activity.GalleryActivity2;
import com.emogoth.android.phone.mimi.app.MimiApplication;
import com.emogoth.android.phone.mimi.autorefresh.RefreshScheduler2;
import com.emogoth.android.phone.mimi.db.CatalogTableConnection;
import com.emogoth.android.phone.mimi.db.DatabaseUtils;
import com.emogoth.android.phone.mimi.db.HiddenThreadTableConnection;
import com.emogoth.android.phone.mimi.db.HistoryTableConnection;
import com.emogoth.android.phone.mimi.interfaces.GalleryMenuItemClickListener;
import com.emogoth.android.phone.mimi.interfaces.PostItemClickListener;
import com.emogoth.android.phone.mimi.model.HeaderFooterViewHolder;
import com.emogoth.android.phone.mimi.util.GlideApp;
import com.emogoth.android.phone.mimi.util.MimiPrefs;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.MediaContract;
import com.emogoth.android.phone.mimi.view.GridItemImageView;
import com.google.android.material.snackbar.Snackbar;
import com.mimireader.chanlib.models.ChanPost;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.SingleObserver;
import io.reactivex.SingleSource;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;


public class PostItemsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements Filterable {

    private static final String LOG_TAG = PostItemsAdapter.class.getSimpleName();

    private static final int VIEW_HEADER = 0;
    private static final int VIEW_LIST_ITEM = 1;
    private static final int VIEW_GRID_ITEM = 2;
    private static final int VIEW_FOOTER = 3;

    private final PostItemClickListener clickListener;

    private final VectorDrawableCompat pinDrawable;
    private final VectorDrawableCompat lockDrawable;

    protected List<ChanPost> postList = new ArrayList<>();

    protected String boardName;
    protected String boardTitle;
    private int postCount;
    private String flagUrl;
    private String trollUrl;
    private PostFilter postFilter;
    private RecyclerView.LayoutManager layoutManager;

    private String oneReply;
    private String oneImage;
    private String numberReplies;
    private String numberImages;

    private Boolean imageSpoilersEnabled;
    private String thumbUrl;
    private String spoilerUrl;
    private String customSpoilerUrl;


    public enum ManagerType {
        LIST(VIEW_LIST_ITEM), STAGGERED_GRID(VIEW_GRID_ITEM), GRID(VIEW_GRID_ITEM), OTHER(-1);

        final int type;

        ManagerType(int type) {
            this.type = type;
        }

        public int getItemViewType() {
            return type;
        }
    }

    protected ManagerType managerType = ManagerType.LIST;


    public PostItemsAdapter(final String boardName, final String boardTitle, final List<ChanPost> postList, final PostItemClickListener clickListener) {
        this.boardName = boardName;
        this.boardTitle = boardTitle;
        this.postList.addAll(postList);
        this.postCount = postList.size();
        this.clickListener = clickListener;

        setup();

        Pair<VectorDrawableCompat, VectorDrawableCompat> drawables = initMetadataDrawables();
        pinDrawable = drawables.first;
        lockDrawable = drawables.second;

        final Context appContext = MimiApplication.getInstance().getApplicationContext();

        oneReply = appContext.getString(R.string.one_reply);
        numberReplies = appContext.getString(R.string.number_replies);

        oneImage = appContext.getString(R.string.one_image);
        numberImages = appContext.getString(R.string.number_images);

        imageSpoilersEnabled = MimiPrefs.imageSpoilersEnabled(appContext);
        thumbUrl = MimiUtil.https() + appContext.getString(R.string.thumb_link) + appContext.getString(R.string.thumb_path);
        spoilerUrl = MimiUtil.https() + appContext.getString(R.string.spoiler_link) + appContext.getString(R.string.spoiler_path);
        customSpoilerUrl = MimiUtil.https() + appContext.getString(R.string.spoiler_link) + appContext.getString(R.string.custom_spoiler_path);
    }

    private Pair<VectorDrawableCompat, VectorDrawableCompat> initMetadataDrawables() {
        final Resources.Theme theme = MimiApplication.getInstance().getTheme();
        final Resources res = MimiApplication.getInstance().getResources();
        final int drawableColor = MimiUtil.getInstance().getTheme() == MimiUtil.THEME_LIGHT ? R.color.md_grey_800 : R.color.md_green_50;
        final VectorDrawableCompat pin;
        final VectorDrawableCompat lock;

        pin = VectorDrawableCompat.create(res, R.drawable.ic_pin, theme);
        lock = VectorDrawableCompat.create(res, R.drawable.ic_lock, theme);

        if (pin != null) {
            pin.setTint(res.getColor(drawableColor));
        }

        if (lock != null) {
            lock.setTint(res.getColor(drawableColor));
        }

        return Pair.create(pin, lock);
    }

    private void setup() {
        if (postList.size() == 0) {
            return;
        }

        Resources res = MimiApplication.getInstance().getResources();

        this.flagUrl = MimiUtil.https() + res.getString(R.string.flag_int_link);
        this.trollUrl = MimiUtil.https() + res.getString(R.string.flag_pol_link);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View v;
        switch (viewType) {
            case VIEW_LIST_ITEM:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.catalog_post_list_item, parent, false);
                return new ViewHolderListItem(v);

            case VIEW_GRID_ITEM:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.catalog_post_grid_item, parent, false);
                return new ViewHolderGridItem(v);

            case VIEW_FOOTER:
                FrameLayout footerFrameLayout = new FrameLayout(parent.getContext());
                //make sure it fills the space
                footerFrameLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, MimiUtil.dpToPx(60)));
                return new HeaderFooterViewHolder(footerFrameLayout);

            case VIEW_HEADER:
            default:
                FrameLayout defaultFrameLayout = new FrameLayout(parent.getContext());
                //make sure it fills the space
                defaultFrameLayout.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new HeaderFooterViewHolder(defaultFrameLayout);
        }

    }

    public void setLayoutManager(RecyclerView.LayoutManager manager) {
        layoutManager = manager;
        if (layoutManager instanceof GridLayoutManager) {
            managerType = ManagerType.GRID;
            ((GridLayoutManager) layoutManager).setSpanSizeLookup(gridSpanSizeLookup);
        } else if (layoutManager instanceof LinearLayoutManager) {
            managerType = ManagerType.LIST;
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            managerType = ManagerType.STAGGERED_GRID;
            ((StaggeredGridLayoutManager) layoutManager).setGapStrategy(StaggeredGridLayoutManager.GAP_HANDLING_MOVE_ITEMS_BETWEEN_SPANS);
        } else {
            managerType = ManagerType.OTHER;
        }

        Log.d(LOG_TAG, "Set manager type: value=" + managerType.name());
    }

    public int getGridSpan(int position) {
        if (layoutManager instanceof GridLayoutManager) {
            return ((GridLayoutManager) layoutManager).getSpanSizeLookup().getSpanSize(position);
        }

        return 1;
    }

    private int getSpan() {
        if (layoutManager instanceof GridLayoutManager) {
            return ((GridLayoutManager) layoutManager).getSpanCount();
        } else if (layoutManager instanceof StaggeredGridLayoutManager) {
            return ((StaggeredGridLayoutManager) layoutManager).getSpanCount();
        }
        return 1;
    }

    private GridLayoutManager.SpanSizeLookup gridSpanSizeLookup = new GridLayoutManager.SpanSizeLookup() {
        @Override
        public int getSpanSize(int position) {
            return getGridSpan(position);
        }
    };

    @Override
    public long getItemId(final int position) {
        return position - 1;
    }

    @Override
    public int getItemCount() {
        if (postList != null) {
            return postList.size();
        }

        return 0;
    }

    @Override
    public int getItemViewType(int position) {
        return managerType.getItemViewType();
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        final ChanPost threadItem = postList.get(position);
        final long threadId = threadItem.getNo();
        final boolean sticky = threadItem.isSticky();
        final HolderAbstractor viewHolder = new HolderAbstractor(holder);

        if (clickListener != null) {
            viewHolder.cardRoot.setOnClickListener(v -> clickListener.onPostItemClick(v, postList, position, boardTitle, boardName, threadId));
        }

        if (viewHolder.flagIcon != null) {
            final String country;
            final String url;
            if (threadItem.getCountry() == null) {
                country = threadItem.getTrollCountry();
                if (country != null) {
                    url = trollUrl + country.toLowerCase() + ".gif";
                } else {
                    url = null;
                }
            } else {
                country = threadItem.getCountry();
                if (country != null) {
                    url = flagUrl + country.toLowerCase() + ".gif";
                } else {
                    url = null;
                }
            }
            if (country != null) {
                viewHolder.flagIcon.setVisibility(View.VISIBLE);

                MimiUtil.loadImageWithFallback(viewHolder.flagIcon.getContext(), viewHolder.flagIcon, url, null, null, new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object o, Target<Drawable> target, boolean b) {
                        if (viewHolder.flagIcon != null) {
                            viewHolder.flagIcon.setVisibility(View.GONE);
                        }
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable drawable, Object o, Target<Drawable> target, DataSource dataSource, boolean b) {
                        return false;
                    }
                });
            } else {
                viewHolder.flagIcon.setVisibility(View.GONE);
            }

        }

        if (viewHolder.lockIcon != null) {
            if (threadItem.isClosed()) {
                viewHolder.lockIcon.setImageDrawable(lockDrawable);
                viewHolder.lockIcon.setVisibility(View.VISIBLE);
            } else {
                viewHolder.lockIcon.setVisibility(View.GONE);
            }
        }

        if (viewHolder.pinIcon != null) {
            if (threadItem.isSticky()) {
                viewHolder.pinIcon.setImageDrawable(pinDrawable);
                viewHolder.pinIcon.setVisibility(View.VISIBLE);
            } else {
                viewHolder.pinIcon.setVisibility(View.GONE);
            }
        }

        viewHolder.threadNo.setText("" + threadId);

        if (MediaContract.hasRemoteMedia(threadItem)) {
            if (managerType == ManagerType.GRID || managerType == ManagerType.STAGGERED_GRID) {
                viewHolder.thumbUrl.setAspectRatio(threadItem.getThumbnailWidth(), threadItem.getThumbnailHeight());
            } else {
                viewHolder.thumbUrl.setAspectRatioEnabled(false);
            }
            viewHolder.thumbUrl.setVisibility(View.VISIBLE);
            viewHolder.thumbUrl.setOnClickListener(v -> GalleryActivity2.start(v.getContext(), GalleryActivity2.GALLERY_TYPE_PAGER, 0, boardName, threadId, new long[0]));

            final String url;
            if (!imageSpoilersEnabled) {
                url = String.format(thumbUrl, boardName, threadItem.getTim());
            } else if (threadItem.spoiler > 0 && threadItem.getCustomSpoiler() > 0) {
                url = String.format(customSpoilerUrl, boardName, threadItem.getCustomSpoiler());
            } else if (threadItem.spoiler > 0) {
                url = spoilerUrl;
            } else {
                url = String.format(thumbUrl, boardName, threadItem.getTim());
            }
//            final String url = chanConnector.getThumbUrl(boardName, threadItem.getTim());
            GlideApp.with(viewHolder.thumbUrl.getContext())
                    .load(url)
                    .into(viewHolder.thumbUrl);

        } else {
            viewHolder.thumbUrl.setVisibility(View.GONE);
            GlideApp.with(viewHolder.thumbUrl.getContext()).clear(viewHolder.thumbUrl);
        }

        if (threadItem.getSubject() != null) {
            viewHolder.subject.setText(threadItem.getSubject());
            viewHolder.subject.setVisibility(View.VISIBLE);
        } else {
            viewHolder.subject.setVisibility(View.GONE);
        }

        if (viewHolder.name != null) {
            if (threadItem.getName() != null) {
                viewHolder.name.setText(threadItem.getName());
                viewHolder.name.setVisibility(View.VISIBLE);
            } else {
                viewHolder.name.setVisibility(View.GONE);
            }
        }

        if (viewHolder.userId != null) {
            if (threadItem.getId() != null) {
                viewHolder.userId.setText(threadItem.getId());
                viewHolder.userId.setVisibility(View.VISIBLE);
            } else {
                viewHolder.userId.setVisibility(View.GONE);
            }
        }

        viewHolder.comment.setText(threadItem.getComment());

        if (managerType == ManagerType.LIST) {
            final String replyCountString;
            if (threadItem.getReplies() == 1) {
                replyCountString = oneReply;
            } else {
                replyCountString = String.format(numberReplies, threadItem.getReplies());
            }

            final String imageCountString;
            if (threadItem.getImages() == 1) {
                imageCountString = oneImage;
            } else {
                imageCountString = String.format(numberImages, threadItem.getImages());
            }
            viewHolder.replyCount.setText(replyCountString);
            viewHolder.imageCount.setText(imageCountString);
        } else {
            viewHolder.replyCount.setText(String.valueOf(threadItem.getReplies()));
            viewHolder.imageCount.setText(String.valueOf(threadItem.getImages()));
        }

        if (viewHolder.timestamp != null) {
            viewHolder.timestamp.setText(threadItem.getNow());
        }

        viewHolder.menuIcon.setOnClickListener(v -> {
            final Context context = v.getContext();
            final PopupMenu menu = new PopupMenu(v.getContext(), v);
            final Activity activity = context instanceof Activity ? (Activity) context : null;
            menu.inflate(R.menu.catalog_item_menu);
            menu.setOnMenuItemClickListener(menuItem -> {
                if (menuItem.getItemId() == R.id.gallery_menu && activity instanceof GalleryMenuItemClickListener) {
                    ((GalleryMenuItemClickListener) activity).onGalleryMenuItemClick(boardName, threadId);
                    return true;
                } else if (menuItem.getItemId() == R.id.hide_thread_menu) {
                    HiddenThreadTableConnection.hideThread(boardName, threadId, sticky)
                            .flatMap((Function<Boolean, SingleSource<Boolean>>) aBoolean -> CatalogTableConnection.removeThread(threadId))
                            .compose(DatabaseUtils.applySingleSchedulers())
                            .subscribe(new SingleObserver<Boolean>() {
                                @Override
                                public void onSubscribe(Disposable d) {

                                }

                                @Override
                                public void onSuccess(Boolean success) {
                                    int msgId = R.string.thread_hidden;
                                    if (!success) {
                                        msgId = R.string.error_hiding_thread;
                                    } else {
                                        for (int i = 0; i < postList.size(); i++) {
                                            if (postList.get(i).getNo() == threadId) {
                                                removePost(i);
                                            }
                                        }

                                    }

                                    Snackbar.make(viewHolder.cardRoot, msgId, Snackbar.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(Throwable e) {
                                    Log.w(LOG_TAG, "Error hiding thread", e);
                                }
                            });
                } else if (menuItem.getItemId() == R.id.report_menu) {
                    final String url = "https://" + activity.getString(R.string.sys_link);
                    final String path = activity.getString(R.string.report_path, boardName, threadId);
                    final Intent reportIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url + path));

                    activity.startActivity(reportIntent);

                    return true;

                } else if (menuItem.getItemId() == R.id.copy_link_menu) {
                    final String url = "https://" + activity.getString(R.string.board_link);
                    final String path = activity.getString(R.string.raw_thread_path, boardName, threadId);

                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("thread link", url + path);
                    clipboard.setPrimaryClip(clip);

                    Toast.makeText(activity, R.string.text_copied_to_clipboard, Toast.LENGTH_SHORT).show();

                    return true;

                } else if (menuItem.getItemId() == R.id.open_link_menu) {
                    final String url = "https://" + activity.getString(R.string.board_link);
                    final String path = activity.getString(R.string.raw_thread_path, boardName, threadId);
                    final Intent openLinkIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url + path));

                    activity.startActivity(openLinkIntent);

                    return true;

                }

                return false;
            });
            menu.show();
        });

        if (viewHolder.bookmarkButton != null) {
            if (threadItem.isWatched()) {
                viewHolder.bookmarkButton.setVisibility(View.VISIBLE);
                viewHolder.bookmarkButton.setText(R.string.ic_bookmark_set);
            } else {
                viewHolder.bookmarkButton.setVisibility(View.INVISIBLE);
                viewHolder.bookmarkButton.setText(R.string.ic_bookmark_unset);
            }

            viewHolder.bookmarkButton.setOnClickListener(v -> {
                final boolean watched = threadItem.isWatched();
                threadItem.setWatched(!watched);

                if (watched) {
                    viewHolder.bookmarkButton.setText(R.string.ic_bookmark_unset);
                    HistoryTableConnection.removeHistory(boardName, threadId)
                            .compose(DatabaseUtils.applySingleSchedulers())
                            .subscribe();
                    RefreshScheduler2.removeThread(boardName, threadId);
//                    RefreshScheduler.getInstance().removeThread(boardName, threadId);
                } else {
                    viewHolder.bookmarkButton.setText(R.string.ic_bookmark_set);
                    RefreshScheduler2.addThread(boardName, threadId);
//                    RefreshScheduler.getInstance().addThread(boardName, threadId, true);
                }

                HistoryTableConnection.setBookmark(boardName, threadId, !watched)
                        .compose(DatabaseUtils.applySingleSchedulers())
                        .subscribe();
            });
        }


    }

    protected void removePost(final int pos) {
        if (postList.size() > pos) {
            final List<ChanPost> posts = new ArrayList<>(postList);
            posts.remove(pos);
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return postList.size();
                }

                @Override
                public int getNewListSize() {
                    return posts.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {

                    return postList.get(oldItemPosition).getNo() == posts.get(newItemPosition).getNo();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return postList.get(oldItemPosition).equals(posts.get(newItemPosition));
                }
            }, false);
            postList.remove(pos);
            result.dispatchUpdatesTo(this);
        }
    }

    public void addPosts(final List<ChanPost> posts) {
        if (postList.size() > 0) {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return postList.size();
                }

                @Override
                public int getNewListSize() {
                    return posts.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {

                    return postList.get(oldItemPosition).getNo() == posts.get(newItemPosition).getNo();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return postList.get(oldItemPosition).equals(posts.get(newItemPosition));
                }
            }, false);
            postList.clear();
            postList.addAll(posts);
            result.dispatchUpdatesTo(this);
        } else {
            postList.addAll(posts);
            notifyDataSetChanged();
        }
    }

    public void clear() {
        postFilter = null;
        if (postList == null) {
            postList = new ArrayList<>();
            notifyDataSetChanged();
        } else {
            final List<ChanPost> posts = new ArrayList<>();
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return postList.size();
                }

                @Override
                public int getNewListSize() {
                    return posts.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {

                    return postList.get(oldItemPosition).getNo() == posts.get(newItemPosition).getNo();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return postList.get(oldItemPosition).equals(posts.get(newItemPosition));
                }
            }, false);
            postList.clear();
            result.dispatchUpdatesTo(this);
        }

    }

    public void destroy() {
        postList = null;
        layoutManager = null;

    }

    public void setPosts(List<ChanPost> posts, String boardName, String boardTitle) {
        if (posts == null) {
            return;
        }

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return postList.size();
            }

            @Override
            public int getNewListSize() {
                return posts.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {

                return postList.get(oldItemPosition).getNo() == posts.get(newItemPosition).getNo();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return postList.get(oldItemPosition).equals(posts.get(newItemPosition));
            }
        }, false);

        if (!TextUtils.isEmpty(boardName)) {
            this.boardName = boardName;
        }

        if (!TextUtils.isEmpty(boardTitle)) {
            this.boardTitle = boardTitle;
        }
        postList.clear();
        postList.addAll(posts);
        postCount = posts.size();
        setup();
        result.dispatchUpdatesTo(this);
    }

    public List<ChanPost> getPosts() {
        return postList;
    }

    protected class ViewHolderListItem extends RecyclerView.ViewHolder {
        TextView threadNo;
        TextView name;
        TextView userId;
        TextView subject;
        TextView comment;
        GridItemImageView thumbUrl;
        TextView replyCount;
        TextView imageCount;
        TextView timestamp;
        View menuIcon;
        ViewGroup postHeader;
        ImageView flagIcon;
        ImageView lockIcon;
        ImageView pinIcon;
        TextView bookmarkButton;
        CardView cardRoot;

        private ViewHolderListItem(final View root) {
            super(root);

            name = root.findViewById(R.id.user_name);
            threadNo = root.findViewById(R.id.thread_id);
            userId = root.findViewById(R.id.user_id);
            subject = root.findViewById(R.id.subject);
            comment = root.findViewById(R.id.comment);
            thumbUrl = root.findViewById(R.id.header_thumbnail);
            replyCount = root.findViewById(R.id.reply_count);
            imageCount = root.findViewById(R.id.image_count);
            timestamp = root.findViewById(R.id.timestamp);
            menuIcon = root.findViewById(R.id.post_item_menu);
            postHeader = root.findViewById(R.id.post_header);
            flagIcon = root.findViewById(R.id.flag_icon);
            lockIcon = root.findViewById(R.id.lock_icon);
            pinIcon = root.findViewById(R.id.pin_icon);
            bookmarkButton = root.findViewById(R.id.bookmark_button);
            cardRoot = root.findViewById(R.id.post_card);
        }
    }

    protected class ViewHolderGridItem extends RecyclerView.ViewHolder {
        TextView threadNo;
        TextView name;
        TextView userId;
        TextView subject;
        TextView comment;
        GridItemImageView thumbUrl;
        TextView replyCount;
        TextView imageCount;
        TextView timestamp;
        View menuIcon;
        ViewGroup postHeader;
        ImageView flagIcon;
        ImageView lockIcon;
        ImageView pinIcon;
        TextView bookmarkButton;
        CardView cardRoot;

        private ViewHolderGridItem(final View root) {
            super(root);

            name = root.findViewById(R.id.user_name);
            threadNo = root.findViewById(R.id.thread_id);
            userId = root.findViewById(R.id.user_id);
            subject = root.findViewById(R.id.subject);
            comment = root.findViewById(R.id.comment);
            thumbUrl = root.findViewById(R.id.header_thumbnail);
            replyCount = root.findViewById(R.id.reply_count);
            imageCount = root.findViewById(R.id.image_count);
            timestamp = root.findViewById(R.id.timestamp);
            menuIcon = root.findViewById(R.id.post_item_menu);
            postHeader = root.findViewById(R.id.post_header);
            flagIcon = root.findViewById(R.id.flag_icon);
            lockIcon = root.findViewById(R.id.lock_icon);
            pinIcon = root.findViewById(R.id.pin_icon);
            bookmarkButton = root.findViewById(R.id.bookmark_button);
            cardRoot = root.findViewById(R.id.post_card);
        }
    }

    private static class HolderAbstractor {
        public final TextView threadNo;
        public final TextView name;
        public final TextView userId;
        public final TextView subject;
        public final TextView comment;
        public final GridItemImageView thumbUrl;
        public final TextView replyCount;
        public final TextView imageCount;
        public final TextView timestamp;
        public final View menuIcon;
        public final ViewGroup postHeader;
        public final ImageView flagIcon;
        public final ImageView lockIcon;
        public final ImageView pinIcon;
        public final TextView bookmarkButton;
        public final CardView cardRoot;

        public HolderAbstractor(RecyclerView.ViewHolder item) {
            if (item instanceof ViewHolderGridItem) {
                threadNo = ((ViewHolderGridItem) item).threadNo;
                name = ((ViewHolderGridItem) item).name;
                userId = ((ViewHolderGridItem) item).userId;
                subject = ((ViewHolderGridItem) item).subject;
                comment = ((ViewHolderGridItem) item).comment;
                thumbUrl = ((ViewHolderGridItem) item).thumbUrl;
                replyCount = ((ViewHolderGridItem) item).replyCount;
                imageCount = ((ViewHolderGridItem) item).imageCount;
                timestamp = ((ViewHolderGridItem) item).timestamp;
                menuIcon = ((ViewHolderGridItem) item).menuIcon;
                postHeader = ((ViewHolderGridItem) item).postHeader;
                flagIcon = ((ViewHolderGridItem) item).flagIcon;
                lockIcon = ((ViewHolderGridItem) item).lockIcon;
                pinIcon = ((ViewHolderGridItem) item).pinIcon;
                bookmarkButton = ((ViewHolderGridItem) item).bookmarkButton;
                cardRoot = ((ViewHolderGridItem) item).cardRoot;
            } else if (item instanceof ViewHolderListItem) {
                threadNo = ((ViewHolderListItem) item).threadNo;
                name = ((ViewHolderListItem) item).name;
                userId = ((ViewHolderListItem) item).userId;
                subject = ((ViewHolderListItem) item).subject;
                comment = ((ViewHolderListItem) item).comment;
                thumbUrl = ((ViewHolderListItem) item).thumbUrl;
                replyCount = ((ViewHolderListItem) item).replyCount;
                imageCount = ((ViewHolderListItem) item).imageCount;
                timestamp = ((ViewHolderListItem) item).timestamp;
                menuIcon = ((ViewHolderListItem) item).menuIcon;
                postHeader = ((ViewHolderListItem) item).postHeader;
                flagIcon = ((ViewHolderListItem) item).flagIcon;
                lockIcon = ((ViewHolderListItem) item).lockIcon;
                pinIcon = ((ViewHolderListItem) item).pinIcon;
                bookmarkButton = ((ViewHolderListItem) item).bookmarkButton;
                cardRoot = ((ViewHolderListItem) item).cardRoot;
            } else {
                threadNo = null;
                name = null;
                userId = null;
                subject = null;
                comment = null;
                thumbUrl = null;
                replyCount = null;
                imageCount = null;
                timestamp = null;
                menuIcon = null;
                postHeader = null;
                flagIcon = null;
                lockIcon = null;
                pinIcon = null;
                bookmarkButton = null;
                cardRoot = null;
            }
        }
    }

    @Override
    public Filter getFilter() {
        if (postFilter == null) {
            postFilter = new PostFilter(postList);
        }

        return postFilter;
    }

    private class PostFilter extends Filter {
        private List<ChanPost> posts;

        PostFilter(List<ChanPost> posts) {
            if (posts == null) {
                throw new NullPointerException("Cannot pass null post list into constructor");
            }
            this.posts = new ArrayList<>(posts);
        }

        @Override
        protected FilterResults performFiltering(final CharSequence constraint) {
            final FilterResults results = new FilterResults();

            if (constraint == null || constraint.length() == 0) {
                results.count = posts.size();
                results.values = posts;
            } else {
                final ArrayList<ChanPost> filteredList = new ArrayList<>(posts.size());
                for (ChanPost post : posts) {
                    if (((post.getComment() != null) && (post.getComment().toString().toLowerCase().contains(constraint.toString().toLowerCase()))) ||
                            ((post.getSubject() != null) && post.getSubject().toString().toLowerCase().contains(constraint.toString().toLowerCase()))) {
                        filteredList.add(post);
                    }
                }

                results.count = filteredList.size();
                results.values = filteredList;
            }

            return results;
        }

        @Override
        protected void publishResults(final CharSequence constraint, final FilterResults results) {
            List<ChanPost> filteredPosts = (List<ChanPost>) results.values;
            setPosts(filteredPosts, null, null);
//            DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
//                @Override
//                public int getOldListSize() {
//                    return posts.size();
//                }
//
//                @Override
//                public int getNewListSize() {
//                    return filteredPosts.size();
//                }
//
//                @Override
//                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
//
//                    return posts.get(oldItemPosition).getNo() == filteredPosts.get(newItemPosition).getNo();
//                }
//
//                @Override
//                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
//                    return posts.get(oldItemPosition).equals(filteredPosts.get(newItemPosition));
//                }
//            }, false);
//            postList.clear();
//            postList.addAll(filteredPosts);
//            result.dispatchUpdatesTo(adapter);
        }
    }

}
