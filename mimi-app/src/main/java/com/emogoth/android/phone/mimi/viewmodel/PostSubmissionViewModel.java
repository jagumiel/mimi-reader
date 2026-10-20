package com.emogoth.android.phone.mimi.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.emogoth.android.phone.mimi.fourchan.PostingProtocol;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/** One user-confirmed attempt, retained across view/activity recreation. */
public final class PostSubmissionViewModel extends ViewModel {
    public static final class State {
        public final boolean sending;
        public final PostingProtocol.Receipt receipt;
        public final Throwable error;
        State(boolean sending, PostingProtocol.Receipt receipt, Throwable error) { this.sending = sending; this.receipt = receipt; this.error = error; }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, null, null));
    private Disposable request;
    public LiveData<State> getState() { return state; }
    public boolean isSending() { return state.getValue() != null && state.getValue().sending; }
    public void submit(Single<PostingProtocol.Receipt> operation) {
        if (isSending()) return;
        state.setValue(new State(true, null, null));
        request = operation.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> state.setValue(new State(false, result, null)), error -> state.setValue(new State(false, null, error)));
    }
    public void consume() { state.setValue(new State(false, null, null)); }
    @Override protected void onCleared() { if (request != null) request.dispose(); }
}
