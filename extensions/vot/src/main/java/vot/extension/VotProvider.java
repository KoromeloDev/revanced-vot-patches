package vot.extension;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

/** Запускается VOT при старте приложения и размещается кнопка перевода. */
public final class VotProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        try {
            Context context = getContext();
            if (context != null) {
                VotController.init(context);
                VotOverlay.install(context.getApplicationContext());
            }
        } catch (Throwable error) {
            android.util.Log.e("VOT", "provider init failed", error);
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
