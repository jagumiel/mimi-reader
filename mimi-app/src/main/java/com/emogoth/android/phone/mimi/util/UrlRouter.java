package com.emogoth.android.phone.mimi.util;

import android.net.Uri;

import java.util.List;

public class UrlRouter {
    public static final int UNSET = -1;
    public static final int SINGLE_BOARD = 1;
    public static final int CATALOG = 2;
    public static final int THREAD = 3;

    private static final String FOURCHAN_BOARDS_AUTHORITY = "boards.4chan.org";

    public static int matchUri(Uri uri) {
        if (uri == null) {
            return UNSET;
        }
        return match(uri.getAuthority(), uri.getPathSegments());
    }

    static int match(String authority, List<String> pathSegments) {
        if (!FOURCHAN_BOARDS_AUTHORITY.equalsIgnoreCase(authority)
                || pathSegments == null
                || pathSegments.isEmpty()
                || isBlank(pathSegments.get(0))) {
            return UNSET;
        }

        if (pathSegments.size() == 1) {
            return SINGLE_BOARD;
        }
        if (pathSegments.size() == 2 && "catalog".equals(pathSegments.get(1))) {
            return CATALOG;
        }
        if ((pathSegments.size() == 3 || pathSegments.size() == 4)
                && "thread".equals(pathSegments.get(1))
                && isPositiveNumber(pathSegments.get(2))) {
            return THREAD;
        }
        return UNSET;
    }

    private static boolean isPositiveNumber(String value) {
        if (isBlank(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !"0".equals(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

//    public static Intent getIntent(Uri uri) {
//        int val = uriMatcher.match(uri);
//
//        switch (val) {
//            case SINGLE_BOARD:
//
//        }
//    }
//
//    private static Intent singleBoardIntent(Uri uri) {
//
//    }
}
