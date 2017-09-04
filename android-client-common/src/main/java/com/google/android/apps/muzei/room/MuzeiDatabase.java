/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.muzei.room;

import android.annotation.SuppressLint;
import android.arch.lifecycle.Observer;
import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.arch.persistence.room.migration.Migration;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.support.annotation.Nullable;

import com.google.android.apps.muzei.api.MuzeiContract;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Room Database for Muzei
 */
@Database(entities = {ProviderEntity.class, Artwork.class, Source.class}, version = 5)
public abstract class MuzeiDatabase extends RoomDatabase {
    private static final String USER_PROPERTY_SELECTED_PROVIDER = "selected_provider";
    private static final String USER_PROPERTY_SELECTED_PROVIDER_PACKAGE = "selected_provider_pkg";

    private static MuzeiDatabase sInstance;

    private Executor mExecutor = Executors.newSingleThreadExecutor();

    public abstract SourceDao sourceDao();

    public abstract ProviderDao providerDao();

    public abstract ArtworkDao artworkDao();

    public static MuzeiDatabase getInstance(Context context) {
        final Context applicationContext = context.getApplicationContext();
        if (sInstance == null) {
            sInstance = Room.databaseBuilder(applicationContext,
                    MuzeiDatabase.class, "muzei.db")
                    .allowMainThreadQueries()
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build();
            sInstance.providerDao().getCurrentProvider(context).observeForever(
                    new Observer<Provider>() {
                        @Override
                        public void onChanged(@Nullable final Provider provider) {
                            if (provider == null) {
                                return;
                            }
                            sendSelectedSourceAnalytics(applicationContext, provider.componentName);
                            applicationContext.getContentResolver()
                                    .notifyChange(MuzeiContract.Sources.CONTENT_URI,null);
                            applicationContext.sendBroadcast(
                                    new Intent(MuzeiContract.Sources.ACTION_SOURCE_CHANGED));
                        }
                    }
            );
        }
        return sInstance;
    }

    public interface ProviderCallback {
        void onProviderSelected();
    }

    public void selectProvider(final ComponentName componentName) {
        selectProvider(componentName, null);
    }

    @SuppressLint("StaticFieldLeak")
    public void selectProvider(final ComponentName componentName, final ProviderCallback callback) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(final Void... voids) {
                beginTransaction();
                providerDao().deleteAll();
                providerDao().insert(new ProviderEntity(componentName));
                setTransactionSuccessful();
                endTransaction();
                return null;
            }

            @Override
            protected void onPostExecute(final Void aVoid) {
                if (callback != null) {
                    callback.onProviderSelected();
                }
            }
        }.executeOnExecutor(mExecutor);
    }

    private static void sendSelectedSourceAnalytics(Context context, ComponentName selectedSource) {
        // The current limit for user property values
        final int MAX_VALUE_LENGTH = 36;
        String packageName = selectedSource.getPackageName();
        if (packageName.length() > MAX_VALUE_LENGTH) {
            packageName = packageName.substring(packageName.length() - MAX_VALUE_LENGTH);
        }
        FirebaseAnalytics.getInstance(context).setUserProperty(USER_PROPERTY_SELECTED_PROVIDER_PACKAGE,
                packageName);
        String className = selectedSource.flattenToShortString();
        className = className.substring(className.indexOf('/')+1);
        if (className.length() > MAX_VALUE_LENGTH) {
            className = className.substring(className.length() - MAX_VALUE_LENGTH);
        }
        FirebaseAnalytics.getInstance(context).setUserProperty(USER_PROPERTY_SELECTED_PROVIDER,
                className);
    }

    private static Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            // NO-OP
        }
    };

    private static Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            // We can't ALTER TABLE to add a foreign key and we wouldn't know what the FK should be
            // at this point anyways so we'll wipe and recreate the artwork table
            database.execSQL("DROP TABLE " + MuzeiContract.Artwork.TABLE_NAME);
            database.execSQL("CREATE TABLE sources ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "component_name TEXT,"
                    + "selected INTEGER,"
                    + "description TEXT,"
                    + "network INTEGER,"
                    + "supports_next_artwork INTEGER,"
                    + "commands TEXT);");
            database.execSQL("CREATE TABLE artwork ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "sourceComponentName TEXT,"
                    + "imageUri TEXT,"
                    + "title TEXT,"
                    + "byline TEXT,"
                    + "attribution TEXT,"
                    + "token TEXT,"
                    + "metaFont TEXT,"
                    + "date_added INTEGER,"
                    + "viewIntent TEXT,"
                    + " CONSTRAINT fk_source_artwork FOREIGN KEY "
                    + "(sourceComponentName) REFERENCES "
                    + "sources (component_name) ON DELETE CASCADE);");
        }
    };

    private static Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            // Handle Sources
            database.execSQL("CREATE TABLE sources2 ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "component_name TEXT UNIQUE NOT NULL,"
                    + "selected INTEGER NOT NULL,"
                    + "description TEXT,"
                    + "network INTEGER NOT NULL,"
                    + "supports_next_artwork INTEGER NOT NULL,"
                    + "commands TEXT NOT NULL);");
            database.execSQL("INSERT INTO sources2 "
                    + "SELECT * FROM sources");
            database.execSQL("DROP TABLE sources");
            database.execSQL("ALTER TABLE sources2 RENAME TO sources");

            // Handle Artwork
            database.execSQL("CREATE TABLE artwork2 ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "sourceComponentName TEXT,"
                    + "imageUri TEXT,"
                    + "title TEXT,"
                    + "byline TEXT,"
                    + "attribution TEXT,"
                    + "token TEXT,"
                    + "metaFont TEXT NOT NULL,"
                    + "date_added INTEGER NOT NULL,"
                    + "viewIntent TEXT,"
                    + " CONSTRAINT fk_source_artwork FOREIGN KEY "
                    + "(sourceComponentName) REFERENCES "
                    + "sources (component_name) ON DELETE CASCADE);");
            database.execSQL("INSERT INTO artwork2 "
                    + "SELECT * FROM artwork");
            database.execSQL("DROP TABLE artwork");
            database.execSQL("ALTER TABLE artwork2 RENAME TO artwork");
        }
    };

    private static Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(final SupportSQLiteDatabase database) {
            // Handle Provider
            database.execSQL("CREATE TABLE provider ("
                    + "componentName TEXT PRIMARY KEY NOT NULL);");

            // Handle Artwork
            database.execSQL("DROP TABLE artwork");
            database.execSQL("CREATE TABLE artwork ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                    + "sourceComponentName TEXT,"
                    + "imageUri TEXT,"
                    + "title TEXT,"
                    + "byline TEXT,"
                    + "attribution TEXT,"
                    + "token TEXT,"
                    + "metaFont TEXT NOT NULL,"
                    + "date_added INTEGER NOT NULL)");
        }
    };
}
