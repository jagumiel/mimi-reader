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
import androidx.lifecycle.ViewModelProvider;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.widget.TextView;
import androidx.preference.PreferenceManager;
import com.emogoth.android.phone.mimi.fourchan.PostingProtocol;
import com.emogoth.android.phone.mimi.viewmodel.PostSubmissionViewModel;
import io.reactivex.Single;

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
import com.emogoth.android.phone.mimi.fourchan.FourChanConnector;
import com.emogoth.android.phone.mimi.util.Extras;
import com.emogoth.android.phone.mimi.util.HttpClientFactory;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import com.emogoth.android.phone.mimi.util.ResourceUtils;
import com.emogoth.android.phone.mimi.util.RxUtil;
import com.emogoth.android.phone.mimi.view.IconTextView;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mimireader.chanlib.ChanConnector;
import com.mimireader.chanlib.models.ChanPost;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.reactivex.disposables.Disposable;


public class PostFragment extends BottomSheetDialogFragment {
    private static final String LOG_TAG = PostFragment.class.getSimpleName();

    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_SUBJECT = "subject";
    private static final String EXTRA_EMAIL = "email";
    private static final String EXTRA_FILE = "file";

    public static final int PICK_IMAGE = 2;
    private static final String CAPTCHA_DIALOG_TAG = "captcha_dialog";

    private long threadId;
    private int threadSize;
    private String boardName;
    private String boardTitle;

    private AppCompatEditText commentField;
    private AppCompatTextView nameText;

    private AppCompatAutoCompleteTextView nameInput;
    private AppCompatEditText optionsInput;
    private AppCompatEditText subjectInput;

    private ViewSwitcher replyFormViewSwitcher;
    private AppCompatTextView doneEditingButton;

    private String name = "";
    private String comment;
    private String email = "";
    private String subject = "";

    private PostingProtocol.Answer captchaAnswer;
    private PostSubmissionViewModel submission;
    private TextView postError;
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
    private Disposable addPostSubscription;

    private String selectedFlag = null;
    private String spoilerValue = null;

    private String imagePath;
    private Uri imageUri;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getChildFragmentManager().setFragmentResultListener(CaptchaDialog.RESULT, this, (key, result) -> {
            getChildFragmentManager().clearFragmentResult(CaptchaDialog.RESULT);
            if (submission == null || submission.isSending()) return;
            try {
                captchaAnswer = new PostingProtocol.Answer(boardName, isNewPost ? 0 : threadId,
                        result.getString("challenge"), result.getString("response"), result.getLong("expiresAt"));
                startPostCall();
            } catch (Exception error) { showPostError(getString(R.string.captcha_not_ready)); }
        });
    }

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

            final String restoredFlag = selectedFlag;
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
            if (restoredFlag != null) for (int i = 0; i < flagKeys.size(); i++) {
                if (restoredFlag.equals(flagMap.get(flagKeys.get(i)))) { flagSelector.setSelection(i); break; }
            }
        }

        spoilerSelection = v.findViewById(R.id.spoiler_selection);
        if ("a".equals(boardName) || "lit".equals(boardName)) {
            spoilerSelection.setVisibility(View.VISIBLE);
            spoilerSelection.setOnCheckedChangeListener((compoundButton, checked) -> spoilerValue = checked ? "on" : null);
            spoilerSelection.setChecked("on".equals(spoilerValue));
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
            // A queued request must not reopen the composer's IME underneath
            // the child CAPTCHA window (especially during Android 10 insets).
            if (getActivity() != null && hasFocus && commentField.hasFocus()
                    && commentField.hasWindowFocus() && commentField.isAttachedToWindow()
                    && getChildFragmentManager().findFragmentByTag(CAPTCHA_DIALOG_TAG) == null) {
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
            cleanUpTempImage();
            clearAttachedImage();
        });

        attachedImageContainer = view.findViewById(R.id.image_container);
        attachedImageContainer.setVisibility(View.GONE);

        attachImageButton = view.findViewById(R.id.attach_image_button);
        attachImageButton.setOnClickListener(v -> pickImage());
        if (!TextUtils.isEmpty(imagePath) && new File(imagePath).isFile()) {
            attachedImage.setImageURI(Uri.fromFile(new File(imagePath)));
            attachedImageContainer.setVisibility(View.VISIBLE);
            attachImageButton.setVisibility(View.GONE);
        }
        postError = view.findViewById(R.id.post_error);
        submission = new ViewModelProvider(this).get(PostSubmissionViewModel.class);
        if (savedInstanceState != null && savedInstanceState.getBoolean("post_sending")
                && !submission.isSending() && submission.getState().getValue().receipt == null) {
            showPostError(getString(R.string.post_interrupted));
        }
        submission.getState().observe(getViewLifecycleOwner(), state -> {
            setSending(state.sending);
            if (state.receipt != null) {
                submission.consume();
                completePost(state.receipt);
            } else if (state.error != null) {
                submission.consume();
                captchaAnswer = null;
                showPostError(getString(R.string.post_attempt_failed, state.error.getLocalizedMessage()));
                if (postListener != null) postListener.onError(state.error);
            }
        });

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

    private void completePost(PostingProtocol.Receipt receipt) {
        String postId = Long.toString(receipt.post);
        if (isNewPost) {
            threadId = receipt.thread;
            ChanPost firstPost = new ChanPost();
            firstPost.setName(TextUtils.isEmpty(name) ? "Anonymous" : name);
            firstPost.setCom(comment);
            firstPost.setComment(comment);
            firstPost.setSub(subject);
            firstPost.setSubject(subject);
            firstPost.setNo(receipt.post);
            HistoryTableConnection.putHistory(boardName, threadId, firstPost, 1)
                    .compose(DatabaseUtils.applySingleSchedulers())
                    .subscribe(ignored -> {}, error -> Log.w(LOG_TAG, "Could not save posted thread locally"));
        }
        BoardTableConnection.incrementPostCount(boardName)
                .subscribe(ignored -> {}, error -> Log.w(LOG_TAG, "Could not update local post count"));
        if (!TextUtils.isEmpty(name)) PostOptionTableConnection.putPostOption(name)
                .compose(DatabaseUtils.applySingleSchedulers())
                .subscribe(ignored -> {}, error -> Log.w(LOG_TAG, "Could not store name suggestion"));
        savePost(postId);
        cleanUpTempImage();
        clearForm();
        if (postListener != null) postListener.onSuccess(postId);
        dismissAllowingStateLoss();
    }

    public void cleanUpTempImage() {
        deleteTemporaryAttachment(imagePath);
    }

    private void deleteTemporaryAttachment(String path) {
        try {
            if (!TextUtils.isEmpty(path)) {
                File imageFile = new File(path);
                File uploadRoot = new File(requireContext().getCacheDir(), "upload");
                if (imageFile.getCanonicalPath().startsWith(uploadRoot.getCanonicalPath() + File.separator) && imageFile.exists()) {
                    imageFile.delete();
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Could not clean up posted image after submission", e);
        }
    }

    private void savePost(final String postId) {
        if (postId == null) {
            Exception e = new Exception("Post ID is null");
            Log.e(LOG_TAG, "Cannot convert null post ID to an integer", e);
            return;
        }
        final long id = Long.parseLong(postId);

        RxUtil.safeUnsubscribe(addPostSubscription);
        addPostSubscription = UserPostTableConnection.addPost(boardName, threadId, id)
                .compose(DatabaseUtils.applySingleSchedulers())
                .subscribe(success -> {
                    if (success) {
                        Log.d(LOG_TAG, "Added post to database: board=" + boardName + ", thread=" + threadId + ", post=" + postId);
                    } else {
                        Log.e(LOG_TAG, "Error Adding post to database: board=" + boardName + ", thread=" + threadId + ", post=" + postId);
                    }
                }, error -> Log.w(LOG_TAG, "Could not record posted ID locally"));
    }

    private void post() {
        Map<String, Object> params = new HashMap<>();
        if (!TextUtils.isEmpty(name)) params.put("name", name);
        if (!TextUtils.isEmpty(email)) params.put("email", email);
        if (!TextUtils.isEmpty(subject)) params.put("sub", subject);
        params.put("com", comment == null ? "" : comment);
        android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String password = prefs.getString("posting_delete_password", null);
        if (password == null) {
            password = java.util.UUID.randomUUID().toString();
            prefs.edit().putString("posting_delete_password", password).apply();
        }
        params.put("pwd", password);
        params.put("mode", "regist");
        if (selectedFlag != null) params.put("flag", selectedFlag);
        if (spoilerValue != null) params.put("spoiler", spoilerValue);
        if (!isNewPost) params.put("resto", Long.toString(threadId));
        if (captchaRequired) PostingProtocol.addCaptcha(params, captchaAnswer, boardName, isNewPost ? 0 : threadId, System.currentTimeMillis());
        captchaAnswer = null; // One explicit submission only; never reuse after an uncertain result.
        if (!TextUtils.isEmpty(imagePath)) params.put("upfile", new File(imagePath));
        PostingProtocol.board(boardName);
        ChanConnector connector = new FourChanConnector.Builder()
                .setEndpoint(FourChanConnector.getDefaultEndpoint())
                .setPostEndpoint(FourChanConnector.getDefaultPostEndpoint())
                .setClient(createPostingClient())
                .build();
        submission.submit(Single.defer(() -> connector.post(boardName, params)).map(PostingProtocol::parseResponse));
    }

    protected okhttp3.OkHttpClient createPostingClient() {
        return HttpClientFactory.getInstance().getPostingClient(WebSettings.getDefaultUserAgent(requireContext()),
                CookieManager.getInstance().getCookie(PostingProtocol.SYSTEM_ORIGIN),
                PostingProtocol.browserUrl(boardName, isNewPost ? 0 : threadId));
    }

    protected CaptchaDialog createCaptchaDialog(String board, long thread) {
        return CaptchaDialog.newInstance(board, thread);
    }

    private void setSending(boolean sending) {
        setCancelable(!sending);
        if (getView() != null) setEnabledRecursively(getView(), !sending);
    }

    private void setEnabledRecursively(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            setEnabledRecursively(((ViewGroup) view).getChildAt(i), enabled);
        }
    }

    private void showPostError(String message) {
        if (postError != null) { postError.setText(message); postError.setVisibility(View.VISIBLE); }
    }

    private void saveFormData() {
        if (commentField != null) comment = commentField.getText().toString();
        if (subjectInput != null) subject = subjectInput.getText().toString();
        if (optionsInput != null) email = optionsInput.getText().toString();
        if (nameInput != null) name = nameInput.getText().toString();
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

        // Material's bottom sheet needs a full-size host window. Shrinking the
        // window itself to wrap-content makes its insets/height feed back into
        // each other when a child CAPTCHA window takes focus on Android 10.
        // The sheet's own content still determines the visible form height.
        getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

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
        saveFormData();
        super.onPause();

        RxUtil.safeUnsubscribe(fetchPostOptionsSubscription);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        saveFormData();
        outState.putBoolean("post_sending", submission != null && submission.isSending());
        outState.putString("post_flag", selectedFlag);
        outState.putString("post_spoiler", spoilerValue);
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

        state.putString("post_flag", selectedFlag);
        state.putString("post_spoiler", spoilerValue);
        state.putBoolean(Extras.EXTRAS_POST_NEW, isNewPost);

        return state;
    }

    private void extractExtras(final Bundle bundle) {
        if (bundle == null) return;
        if (bundle.containsKey(Extras.EXTRAS_THREAD_ID)) {
            threadId = bundle.getLong(Extras.EXTRAS_THREAD_ID);
        } else {
            isNewPost = true;
        }
        if (bundle.containsKey(Extras.EXTRAS_BOARD_NAME)) {
            boardName = bundle.getString(Extras.EXTRAS_BOARD_NAME);
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
        isNewPost = bundle.getBoolean(Extras.EXTRAS_POST_NEW, !bundle.containsKey(Extras.EXTRAS_THREAD_ID) || threadId <= 0);
        selectedFlag = bundle.getString("post_flag");
        spoilerValue = bundle.getString("post_spoiler");
        imagePath = bundle.getString(EXTRA_FILE, null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == android.app.Activity.RESULT_OK && data != null) {
            imageUri = data.getData();

            final String previousImage = imagePath;
            try {
                copyDocumentFileLocally(imageUri);
            } catch (Exception e) {
                imagePath = previousImage;
                showPostError(getString(R.string.post_attempt_failed, "Could not read attachment"));
                return;
            }
            if (previousImage != null && !previousImage.equals(imagePath)) deleteTemporaryAttachment(previousImage);

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

            if (fileName.isEmpty() || ".".equals(fileName) || "..".equals(fileName)) throw new IOException("Invalid attachment name");
            File selectionDir = new File(uploadDir, java.util.UUID.randomUUID().toString());
            if (!selectionDir.mkdir()) throw new IOException("Could not create attachment directory");
            localFile = new File(selectionDir, fileName);
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
            } catch (Exception error) {
                localFile.delete();
                selectionDir.delete();
                throw error;
            }

            imagePath = localFile.getAbsolutePath();
        }

        return localFile;
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
        if (submission == null || submission.isSending()) return;
        saveFormData();
        if (TextUtils.isEmpty(comment) && TextUtils.isEmpty(imagePath)) {
            showPostError(getString(R.string.post_empty)); return;
        }
        if (captchaRequired) {
            if (getChildFragmentManager().findFragmentByTag(CAPTCHA_DIALOG_TAG) != null) return;
            try {
                // The challenge owns the next input focus. Keep the draft, but
                // do not leave a pending keyboard request on the sheet below it.
                InputMethodManager input = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (input != null) input.hideSoftInputFromWindow(commentField.getWindowToken(), 0);
                commentField.clearFocus();
                createCaptchaDialog(boardName, isNewPost ? 0 : threadId)
                        .show(getChildFragmentManager(), CAPTCHA_DIALOG_TAG);
            } catch (Exception error) { showPostError(getString(R.string.captcha_not_ready)); }
        } else startPostCall();
    }

    private void startPostCall() {
        if (!isAdded() || submission == null || submission.isSending()) return;
        try {
            saveFormData();
            if (postError != null) postError.setVisibility(View.GONE);
            post();
            if (postListener != null) postListener.onStartPost();
        } catch (Exception error) {
            captchaAnswer = null;
            showPostError(getString(R.string.post_attempt_failed, error.getLocalizedMessage()));
        }
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
