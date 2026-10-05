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

package com.emogoth.android.phone.mimi.fragment;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ViewSwitcher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.documentfile.provider.DocumentFile;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.activity.MimiActivity;
import com.emogoth.android.phone.mimi.adapter.PostOptionAdapter;
import com.emogoth.android.phone.mimi.app.MimiApplication;
import com.emogoth.android.phone.mimi.db.BoardTableConnection;
import com.emogoth.android.phone.mimi.db.DatabaseUtils;
import com.emogoth.android.phone.mimi.db.HistoryTableConnection;
import com.emogoth.android.phone.mimi.db.PostOptionTableConnection;
import com.emogoth.android.phone.mimi.db.UserPostTableConnection;
import com.emogoth.android.phone.mimi.dialog.CaptchaDialog;
import com.emogoth.android.phone.mimi.fourchan.FourChanCommentParser;
import com.emogoth.android.phone.mimi.fourchan.FourChanHtmlParser;
import com.emogoth.android.phone.mimi.fourchan.FourChanConnector;
import com.emogoth.android.phone.mimi.util.Extras;
import com.emogoth.android.phone.mimi.util.HttpClientFactory;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.ResourceUtils;
import com.emogoth.android.phone.mimi.util.RxUtil;
import com.emogoth.android.phone.mimi.view.IconTextView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
;
import com.mimireader.chanlib.ChanConnector;
import com.mimireader.chanlib.models.ChanBoard;
import com.mimireader.chanlib.models.ChanPost;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;
import retrofit2.Response;


public class PostFragment extends BottomSheetDialogFragment {
    private static final String LOG_TAG = PostFragment.class.getSimpleName();

    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_COMMENT = "comment";
    private static final String EXTRA_SUBJECT = "subject";
    private static final String EXTRA_EMAIL = "email";
    private static final String EXTRA_FILE = "file";

    public static final int PICK_IMAGE = 2;
    private static final String CAPTCHA_DIALOG_TAG = "captcha_dialog";

    private long threadId;
    private int threadSize;
    private String boardName;
    private String postUrl;
    private String boardTitle;

    private ViewGroup shrinkFormContainer;
    //    private ViewGroup headerContainer;
    private AppCompatEditText commentField;
    private AppCompatTextView nameText;

    private AppCompatAutoCompleteTextView nameInput;
    private AppCompatEditText optionsInput;
    private AppCompatEditText subjectInput;

    private ViewSwitcher replyFormViewSwitcher;
    private ImageView editUserInfoButton;
    private AppCompatTextView doneEditingButton;

    private String name = "";
    private String comment;
    private String email = "";
    private String subject = "";
    private boolean isEditMode = false;

    public String captchaChallenge = null;
    public String captchaVerification;
    private ImageView attachedImage;
    private PostListener postListener;
    private boolean isNewPost = false;
    private boolean captchaRequired = true;
    private IconTextView clearImage;
    private ViewGroup attachedImageContainer;
    private AppCompatTextView attachImageButton;
    private AppCompatTextView sageButton;
    private boolean formCleared = false;
    private View sendButton;
    private View cancelButton;
    private AppCompatSpinner flagSelector;
    private AppCompatCheckBox spoilerSelection;

    private PostOptionAdapter adapter;

    private Disposable fetchPostOptionsSubscription;
    private Disposable fetchPostSubscription;
    private Disposable boardInfoSubscription;
    private Disposable addPostSubscription;

    private String selectedFlag = null;
    private String spoilerValue = null;

    private String imagePath;
    private Uri imageUri;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (!formCleared) {
                extractExtras(savedInstanceState);
            } else {
                formCleared = false;
            }
        } else {
            extractExtras(getArguments());
        }

        if (MimiUtil.getInstance().isLoggedIn()) {
            disableCaptcha();
        } else {
            enableCaptcha();
        }

        final View v = inflater.inflate(R.layout.reply_form_layout, container, false);
        sendButton = v.findViewById(R.id.submit_button);
        cancelButton = v.findViewById(R.id.cancel_button);
        flagSelector = v.findViewById(R.id.flag_selector);
        if ("pol".equals(boardName)) {
            flagSelector.setVisibility(View.VISIBLE);
            final Map<String, String> flagMap = ResourceUtils.getHashMapResource(getActivity(), R.xml.flags);
            final List<String> flagKeys = new ArrayList<>(flagMap.keySet());

            selectedFlag = "0";
            flagSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    String key = flagKeys.get(i);
                    selectedFlag = flagMap.get(key);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    selectedFlag = "0";
                }
            });

            ArrayAdapter<String> flagAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_dropdown_item, flagKeys);
            flagSelector.setAdapter(flagAdapter);
        }

        spoilerSelection = v.findViewById(R.id.spoiler_selection);
        if ("a".equals(boardName) || "lit".equals(boardName)) {
            spoilerSelection.setVisibility(View.VISIBLE);
            spoilerSelection.setOnCheckedChangeListener((compoundButton, checked) -> spoilerValue = checked ? "on" : null);
        }

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sendButton.setOnClickListener(v -> sendPost());

        cancelButton.setOnClickListener(v -> cancelPost());

        nameText = view.findViewById(R.id.edit_user_info);
        nameText.setOnClickListener(v -> replyFormViewSwitcher.setDisplayedChild(1));
        if (!TextUtils.isEmpty(name)) {
            nameText.setText(name);
        } else {
            nameText.setText("Anonymous");
        }

        nameInput = view.findViewById(R.id.name_input);
        if (!TextUtils.isEmpty(name)) {
            nameInput.setText(name);
        }
        nameInput.setThreshold(1);

        optionsInput = view.findViewById(R.id.options_input);

        if (!TextUtils.isEmpty(email)) {
            optionsInput.setText(email);
        }

        sageButton = view.findViewById(R.id.sage_info);
        sageButton.setOnClickListener(v -> {
            if (optionsInput != null) {
                optionsInput.setText("sage");
            }
        });

        subjectInput = view.findViewById(R.id.subject_input);
        if (!TextUtils.isEmpty(subject)) {
            subjectInput.setText(subject);
        }

        replyFormViewSwitcher = view.findViewById(R.id.reply_form_switcher);
        replyFormViewSwitcher.setInAnimation(getActivity(), R.anim.abc_fade_in);
        replyFormViewSwitcher.setOutAnimation(getActivity(), R.anim.abc_fade_out);
        replyFormViewSwitcher.setOnClickListener(v -> Log.i(LOG_TAG, "form clicked"));

        doneEditingButton = view.findViewById(R.id.done_info);
        doneEditingButton.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(nameInput.getText().toString())) {
                nameText.setText(nameInput.getText().toString());
            } else {
                nameText.setText("Anonymous");
            }

            setEmail(optionsInput.getText().toString());
            setName(nameInput.getText().toString());
            setSubject(subjectInput.getText().toString());

            replyFormViewSwitcher.setDisplayedChild(0);
        });

        commentField = view.findViewById(R.id.comment_input);
        commentField.setOnFocusChangeListener((v, hasFocus) -> commentField.post(() -> {
            if (getActivity() != null && hasFocus) {
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(commentField, InputMethodManager.SHOW_IMPLICIT);
            }
        }));

        commentField.requestFocus();

        if (!TextUtils.isEmpty(comment)) {
            commentField.setText(comment);
            commentField.setSelection(comment.length());
        }

        attachedImage = view.findViewById(R.id.attached_image);

        clearImage = view.findViewById(R.id.clear_image);
        clearImage.setOnClickListener(v -> {
            clearAttachedImage();
            cleanUpTempImage();
        });

        attachedImageContainer = view.findViewById(R.id.image_container);
        attachedImageContainer.setVisibility(View.GONE);

        attachImageButton = view.findViewById(R.id.attach_image_button);
        attachImageButton.setOnClickListener(v -> pickImage());

    }

    private void pickImage() {
        Intent intent = new Intent();
        intent.setType("*/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String[] mimetypes = {"image/*", "video/*"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimetypes);
        }

        if (getActivity() != null && getActivity() instanceof MimiActivity) {
            startActivityForResult(Intent.createChooser(intent, getString(R.string.select_picture)), PICK_IMAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void clearAttachedImage() {
        imagePath = null;
        attachedImageContainer.setVisibility(View.GONE);
        attachImageButton.setVisibility(View.VISIBLE);
    }

    private void disableCaptcha() {
        captchaRequired = false;
    }

    private void enableCaptcha() {
        captchaRequired = true;
    }

    private void processResponse(Response<ResponseBody> response) {
        String html = null;

        try {
            if (response == null) {
                html = MimiApplication.getInstance().getString(R.string.empty_response_error);
                if (postListener != null) {
                    postListener.onError(new Exception(html));
                }
                return;
            } else if (response.isSuccessful()) {
                html = response.body().string();
            } else {
                html = response.errorBody().string();
            }
        } catch (Exception e) {
            if (postListener != null) {
                postListener.onError(e);
            }
            Log.e(LOG_TAG, "Error processing response", e);

            return;
        }

        final String errorMsg = FourChanHtmlParser.parsePostError(html);
        if (errorMsg != null) {
            Log.i(LOG_TAG, errorMsg);

            if (postListener != null) {
                postListener.onError(new Exception(errorMsg));
            }
        } else {
            String postId = null;
            int index = html.indexOf("no:");
            if (index >= 0) {
                postId = html.substring(index + 3, html.lastIndexOf(" "));
            } else {
                index = html.indexOf("thread:");
                if (index >= 0) {
                    postId = html.substring(index + 3, html.lastIndexOf(" "));
                }
            }

            if (postId != null && isNewPost) {
                final String fileName = MimiUtil.removeExtention(imagePath);
                final String fileExt = imagePath.substring(imagePath.lastIndexOf("."));
                final String name = TextUtils.isEmpty(getName()) ? "Anonymous" : getName();
                final ChanPost firstPost = new ChanPost();
                final FourChanCommentParser.Builder parserBuilder = new FourChanCommentParser.Builder();

                parserBuilder.setContext(getActivity())
                        .setComment(getComment())
                        .setQuoteColor(MimiUtil.getInstance().getQuoteColor())
                        .setReplyColor(MimiUtil.getInstance().getReplyColor())
                        .setHighlightColor(MimiUtil.getInstance().getHighlightColor())
                        .setLinkColor(MimiUtil.getInstance().getLinkColor());

                firstPost.setName(name);
                firstPost.setTim(Calendar.getInstance(Locale.getDefault()).getTime().toString());
                firstPost.setCom(getComment());
                firstPost.setComment(parserBuilder.build().parse());
                firstPost.setEmail(getEmail());
                firstPost.setSub(getSubject());
                firstPost.setSubject(getSubject());
                firstPost.setNo(Integer.parseInt(postId));
                firstPost.setFilename(fileName);
                firstPost.setExt(fileExt);

                HistoryTableConnection.putHistory(boardName, threadId, firstPost, 1)
                        .compose(DatabaseUtils.applySingleSchedulers())
                        .doOnSuccess(aBoolean -> Log.e(LOG_TAG, "Post returned with success=" + aBoolean))
                        .doOnError(throwable -> {
                            Log.e(LOG_TAG, "Error posting", throwable);
                        })
                        .subscribe();
            }

            BoardTableConnection.incrementPostCount(boardName).subscribe();

            String option = getName();
            if (!TextUtils.isEmpty(option)) {
                PostOptionTableConnection.putPostOption(option)
                        .compose(DatabaseUtils.applySingleSchedulers())
                        .subscribe();
            }

            savePost(postId, true);

            if (postListener != null) {
                postListener.onSuccess(postId);
            }
            cleanUpTempImage();
            clearForm();
        }
    }

    public void cleanUpTempImage() {
        try {
            if (!TextUtils.isEmpty(imagePath)) {
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    Log.d(LOG_TAG, "Deleting temporary image file [" + imagePath + "]");
                    imageFile.delete();
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not clean up posted image after submission", e);
        }
    }

    private void savePost(final String postId, final boolean watch) {
        if (postId == null) {
            Exception e = new Exception("Post ID is null");
            Log.e(LOG_TAG, "Cannot convert null post ID to an integer", e);
            return;
        }
        final int id = Integer.parseInt(postId);
//        RxUtil.safeUnsubscribe(fetchPostSubscription);
//        fetchPostSubscription = HistoryTableConnection.fetchPost(boardName, threadId)
//                .compose(DatabaseUtils.applySchedulers())
//                .subscribe(history -> {
//                    if (history.threadId == -1) {
//                        ThreadRegistry.getInstance().add(boardName, threadId, id, threadSize + 1, watch);
//                    }
//                });

        RxUtil.safeUnsubscribe(addPostSubscription);
        addPostSubscription = UserPostTableConnection.addPost(boardName, threadId, id)
                .compose(DatabaseUtils.applySingleSchedulers())
                .subscribe(success -> {
                    if (success) {
                        Log.d(LOG_TAG, "Added post to database: board=" + boardName + ", thread=" + threadId + ", post=" + postId);
                    } else {
                        Log.e(LOG_TAG, "Error Adding post to database: board=" + boardName + ", thread=" + threadId + ", post=" + postId);
                    }
                });
    }

    private Consumer<Response<ResponseBody>> onPostComplete() {
        return this::processResponse;
    }

    private Consumer<Throwable> onPostFail() {
        return throwable -> {
            if (postListener != null) {
                postListener.onError(throwable);
            }
        };
    }

    public void post() {
        final ChanConnector chanConnector = new FourChanConnector.Builder()
                .setEndpoint(FourChanConnector.getDefaultEndpoint())
                .setPostEndpoint(FourChanConnector.getDefaultPostEndpoint())
                .setClient(HttpClientFactory.getInstance().getClient())
                .build();

        final File imageLocation;
        if (TextUtils.isEmpty(imagePath)) {
            imageLocation = null;
        } else {
            imageLocation = new File(imagePath);
        }

        RxUtil.safeUnsubscribe(boardInfoSubscription);
        boardInfoSubscription = BoardTableConnection.fetchBoard(boardName)
                .observeOn(Schedulers.io())
                .onErrorReturn(throwable -> {
                    Log.e(LOG_TAG, "Error fetching board info for " + boardName, throwable);
                    return new ChanBoard();
                })
//                .flatMap((Function<ChanBoard, SingleSource<File>>) chanBoard -> {
//                    return Single.just(copyDocumentFileLocally(imageUri));
//                })
                .map(chanBoard -> {
                    final Map<String, Object> params = new HashMap<>();
                    if (!TextUtils.isEmpty(name)) {
                        params.put("name", name);
                    }

                    if (!TextUtils.isEmpty(email)) {
                        params.put("email", email);
                    }

                    if (!TextUtils.isEmpty(subject)) {
                        params.put("sub", subject);
                    }

                    params.put("com", comment);
                    params.put("pwd", "password");
                    params.put("mode", "regist");

                    if (selectedFlag != null) {
                        params.put("flag", selectedFlag);
                    }

                    if (spoilerValue != null) {
                        params.put("spoiler", spoilerValue);
                    }

                    if (!isNewPost) {
                        params.put("resto", String.valueOf(threadId));
                    }

                    if (captchaRequired) {
                        params.put("g-recaptcha-response", captchaChallenge);
                    }

                    if (imageLocation != null) {
                        params.put("upfile", imageLocation);
                    }
                    return params;
                })
                .flatMap(params -> chanConnector.post(boardName, params))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onPostComplete(), onPostFail());
    }

    private void saveFormData() {
        comment = commentField.getText().toString();
        subject = subjectInput.getText().toString();
        email = optionsInput.getText().toString();
        name = nameInput.getText().toString();
    }

    private void clearForm() {
        formCleared = true;
        nameInput.setText(null);
        commentField.setText(null);
        subjectInput.setText(null);
        optionsInput.setText(null);

        name = null;
        comment = null;
        subject = null;
        email = null;

        clearAttachedImage();
    }

    @Override
    public void onResume() {
        super.onResume();

        int deviceWidth = getResources().getDisplayMetrics().widthPixels;
        getDialog().getWindow().setLayout(deviceWidth - 4, ViewGroup.LayoutParams.WRAP_CONTENT);

        if (MimiUtil.getInstance().isLoggedIn()) {
            disableCaptcha();
        } else {
            enableCaptcha();
        }

        fetchPostOptionsSubscription = PostOptionTableConnection.fetchPostOptions()
                .compose(DatabaseUtils.applySingleSchedulers())
                .subscribe(postOptions -> {
                    if (getActivity() == null) {
                        return;
                    }

                    if (postOptions != null) {
                        adapter = new PostOptionAdapter(getActivity(), R.layout.post_option_item, postOptions);
                        nameInput.setAdapter(adapter);
                    }
                }, throwable -> Log.e(LOG_TAG, "Error fetching post options", throwable));

    }

    @Override
    public void onPause() {
        super.onPause();

        RxUtil.safeUnsubscribe(fetchPostOptionsSubscription);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putLong(Extras.EXTRAS_THREAD_ID, threadId);
        outState.putString(Extras.EXTRAS_BOARD_NAME, boardName);
        outState.putString(Extras.EXTRAS_BOARD_TITLE, boardTitle);
        outState.putInt(Extras.EXTRAS_THREAD_SIZE, threadSize);

        outState.putString(Extras.EXTRAS_POST_COMMENT, comment);
        outState.putString(EXTRA_SUBJECT, subject);
        outState.putString(EXTRA_EMAIL, email);
        outState.putString(EXTRA_NAME, name);
        outState.putString(EXTRA_FILE, imagePath);

        outState.putBoolean(Extras.EXTRAS_POST_NEW, isNewPost);

        super.onSaveInstanceState(outState);
    }

    public Bundle saveState() {
        saveFormData();

        Bundle state = new Bundle();
        state.putLong(Extras.EXTRAS_THREAD_ID, threadId);
        state.putString(Extras.EXTRAS_BOARD_NAME, boardName);
        state.putString(Extras.EXTRAS_BOARD_TITLE, boardTitle);
        state.putInt(Extras.EXTRAS_THREAD_SIZE, threadSize);

        state.putString(Extras.EXTRAS_POST_COMMENT, comment);
        state.putString(EXTRA_SUBJECT, subject);
        state.putString(EXTRA_EMAIL, email);
        state.putString(EXTRA_NAME, name);
        state.putString(EXTRA_FILE, imagePath);

        state.putBoolean(Extras.EXTRAS_POST_NEW, isNewPost);

        return state;
    }

    private void extractExtras(final Bundle bundle) {
        if (bundle.containsKey(Extras.EXTRAS_THREAD_ID)) {
            threadId = bundle.getLong(Extras.EXTRAS_THREAD_ID);
        } else {
            isNewPost = true;
        }
        if (bundle.containsKey(Extras.EXTRAS_BOARD_NAME)) {
            boardName = bundle.getString(Extras.EXTRAS_BOARD_NAME);
            if (getActivity() != null) {
                final String url = getResources().getString(R.string.sys_link);
                postUrl = "https://" + url + "/" + boardName + "/post";
            }
        }

        boardTitle = bundle.getString(Extras.EXTRAS_BOARD_TITLE, null);
        threadSize = bundle.getInt(Extras.EXTRAS_THREAD_SIZE, 0);

        this.comment = bundle.getString(Extras.EXTRAS_POST_COMMENT, "");

        if (bundle.containsKey(Extras.EXTRAS_POST_REPLY)) {
            String replyText = bundle.getString(Extras.EXTRAS_POST_REPLY);
            if (!TextUtils.isEmpty(replyText)) {
                if (!TextUtils.isEmpty(this.comment)) {
                    this.comment = this.comment + "\n\n";
                }
                this.comment = this.comment + replyText;
            }
        }

        subject = bundle.getString(EXTRA_SUBJECT, null);
        email = bundle.getString(EXTRA_EMAIL, null);
        name = bundle.getString(EXTRA_NAME, null);
        isNewPost = bundle.getBoolean(Extras.EXTRAS_POST_NEW, false);
        imagePath = bundle.getString(EXTRA_FILE, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_IMAGE && data != null) {
            imageUri = data.getData();

            try {
                copyDocumentFileLocally(imageUri);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (attachedImage != null) {
                attachedImage.setImageURI(imageUri);
                attachedImageContainer.setVisibility(View.VISIBLE);
                attachImageButton.setVisibility(View.GONE);
            }
        }
    }

    private File copyDocumentFileLocally(Uri imageUri) throws Exception {
        File localFile = null;
        final Context context = getContext();
        if (context != null && imageUri != null) {
            final DocumentFile f = DocumentFile.fromSingleUri(context, imageUri);
            if (f == null) {
                throw new IOException("Could not open selected attachment");
            }

            String fileName = f.getName();
            if (fileName == null) {
                MimeTypeMap mtm = MimeTypeMap.getSingleton();
                fileName = "unknown." + mtm.getExtensionFromMimeType(f.getType());
            }
            fileName = new File(fileName).getName();
            File uploadDir = new File(context.getCacheDir(), "upload");
            if (!uploadDir.exists() && !uploadDir.mkdirs()) {
                throw new IOException("Could not create attachment cache directory");
            }

            localFile = new File(uploadDir, fileName);
            try (InputStream input = context.getContentResolver().openInputStream(f.getUri());
                 OutputStream output = new FileOutputStream(localFile, false)) {
                if (input == null) {
                    throw new IOException("Could not read selected attachment");
                }

                final byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
            }

            imagePath = localFile.getAbsolutePath();
        }

        return localFile;
    }

    private void enableEditMode(final boolean enabled) {
        if (isEditMode != enabled) {
//            if (enabled) {
//                headerContainer.setVisibility(View.GONE);
//            } else {
//                headerContainer.setVisibility(View.VISIBLE);
//            }

            isEditMode = enabled;
        }
    }

    public String getName() {
        if (name == null && nameInput != null) {
            name = nameInput.getText().toString();
        }

        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        if (TextUtils.isEmpty(comment) && commentField != null) {
            comment = commentField.getText().toString();
        }
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getEmail() {
        if (email == null && optionsInput != null) {
            email = optionsInput.getText().toString();
        }

        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setPostListener(final PostListener listener) {
        postListener = listener;
    }

    private void cancelPost() {
        cleanUpTempImage();
        clearForm();

        if (postListener != null) {
            postListener.onCanceled();
        } else if (getActivity() != null) {
            //getActivity().onBackPressed();
            dismiss();
        }
    }

    private void sendPost() {
//        setEmail(optionsInput.getText().toString());
//        setName(nameInput.getText().toString());
//        setSubject(subjectInput.getText().toString());
//        setComment(commentField.getText().toString());

        saveFormData();

        if (captchaRequired) {
            final CaptchaDialog dialog = new CaptchaDialog();
            dialog.setOnRecaptchaResponseCallback(response -> {
                if (getActivity() != null) {
                    dialog.dismiss();
                }
                captchaChallenge = response;
                startPostCall();
            });

            dialog.show(getActivity().getSupportFragmentManager(), CAPTCHA_DIALOG_TAG);
        } else {
            startPostCall();
        }
    }

    private void startPostCall() {
        if (getActivity() == null) {
            return;
        }
        dismiss();

        if (postListener != null) {
            postListener.onStartPost();
        }

        post();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        if (postListener != null) {
            postListener.onDismiss();
        }
        super.onDismiss(dialog);
    }

    public interface PostListener {
        void onDismiss();

        void onCanceled();

        void onStartPost();

        void onSuccess(final String postId);

        void onError(final Throwable error);
    }
}
