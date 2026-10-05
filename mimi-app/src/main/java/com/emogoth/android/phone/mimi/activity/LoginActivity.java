package com.emogoth.android.phone.mimi.activity;

import android.os.Bundle;

import com.emogoth.android.phone.mimi.BuildConfig;
import com.google.android.material.snackbar.Snackbar;

import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.fourchan.FourChanConnector;
import com.emogoth.android.phone.mimi.fourchan.FourChanHtmlParser;
import com.emogoth.android.phone.mimi.util.HttpClientFactory;
import com.emogoth.android.phone.mimi.util.RxUtil;
import com.mimireader.chanlib.ChanConnector;

import java.io.IOException;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class LoginActivity extends MimiActivity {
    private static final String LOG_TAG = LoginActivity.class.getSimpleName();

    public static final String INCORRECT_TOKEN = "Incorrect Token or PIN";

    private Disposable loginSubscription;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.login_activity);

        final EditText tokenView = findViewById(R.id.token_text);
        if (tokenView == null) {
            return;
        }

        final EditText pinView = findViewById(R.id.pin_text);
        if (pinView == null) {
            return;
        }

        final TextView errorMessage = findViewById(R.id.error_message);
        if (errorMessage == null) {
            return;
        } else {
            errorMessage.setMovementMethod(LinkMovementMethod.getInstance());
        }

        final View loginButton = findViewById(R.id.login_button);
        if (loginButton != null) {
            loginButton.setOnClickListener(v -> {
                final String token = tokenView.getText().toString();
                final String pin = pinView.getText().toString();

                if (TextUtils.isEmpty(token) || TextUtils.isEmpty(pin)) {
                    Snackbar.make(errorMessage, R.string.login_error, Snackbar.LENGTH_SHORT).show();
                    return;
                }

                loginButton.setEnabled(false);

                errorMessage.setText(R.string.loading);

                final ChanConnector chanConnector = new FourChanConnector.Builder()
                        .setClient(HttpClientFactory.getInstance().getClient())
                        .setEndpoint(FourChanConnector.getDefaultEndpoint())
                        .setPostEndpoint(FourChanConnector.getDefaultPostEndpoint())
                        .build();

                RxUtil.safeUnsubscribe(loginSubscription);
                loginSubscription = chanConnector.login(token, pin)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(responseBodyResponse -> {
                            loginButton.setEnabled(true);

                            try {
                                String response;
                                if (responseBodyResponse.errorBody() != null) {
                                    response = responseBodyResponse.errorBody().string();
                                } else {
                                    response = responseBodyResponse.body().string();
                                }

                                if (BuildConfig.DEBUG) {
                                    Log.i(LOG_TAG, "response=" + response);
                                }

                                final FourChanHtmlParser.LoginResult result = FourChanHtmlParser.parseLoginResponse(response);
                                if (result.hasError()) {
                                    errorMessage.setText(Html.fromHtml(result.getErrorHtml()));
                                } else if (result.isSuccess()) {
                                    Toast.makeText(LoginActivity.this, R.string.auth_success, Toast.LENGTH_SHORT).show();
                                    finish();
                                }
                            } catch (IOException e) {
                                Log.e(LOG_TAG, "Error logging in with chanpass", e);
                            }
                        }, throwable -> {
                            loginButton.setEnabled(true);
                            Log.w(LOG_TAG, "Error logging in", throwable);
                        });

//                errorMessage.setText(null);
            });
        }

    }

    @Override
    protected void onStop() {
        super.onStop();

        RxUtil.safeUnsubscribe(loginSubscription);
    }

    @Override
    protected String getPageName() {
        return "login_page";
    }
}
