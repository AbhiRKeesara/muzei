/*
 * Copyright 2014 Google Inc.
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

package com.google.android.apps.muzei;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.app.RemoteInput;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.apps.muzei.api.MuzeiContract;
import com.google.android.apps.muzei.api.UserCommand;
import com.google.android.apps.muzei.event.ArtDetailOpenedClosedEvent;
import com.google.android.apps.muzei.render.ImageUtil;
import com.google.android.apps.muzei.room.Artwork;
import com.google.android.apps.muzei.room.MuzeiDatabase;
import com.google.android.apps.muzei.room.Provider;

import net.nurik.roman.muzei.R;

import org.greenrobot.eventbus.EventBus;

import java.io.FileNotFoundException;
import java.util.List;

public class NewWallpaperNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "NewWallpaperNotif";

    public static final String PREF_ENABLED = "new_wallpaper_notification_enabled";
    private static final String PREF_LAST_READ_NOTIFICATION_ARTWORK_ID
            = "last_read_notification_artwork_id";
    private static final String PREF_LAST_READ_NOTIFICATION_ARTWORK_IMAGE_URI
            = "last_read_notification_artwork_image_uri";
    private static final String PREF_LAST_READ_NOTIFICATION_ARTWORK_TOKEN
            = "last_read_notification_artwork_token";

    private static final String NOTIFICATION_CHANNEL = "new_wallpaper";
    private static final int NOTIFICATION_ID = 1234;

    private static final String ACTION_MARK_NOTIFICATION_READ
            = "com.google.android.apps.muzei.action.NOTIFICATION_DELETED";

    private static final String ACTION_NEXT_ARTWORK
            = "com.google.android.apps.muzei.action.NOTIFICATION_NEXT_ARTWORK";

    private static final String ACTION_USER_COMMAND
            = "com.google.android.apps.muzei.action.NOTIFICATION_USER_COMMAND";

    private static final String EXTRA_USER_COMMAND
            = "com.google.android.apps.muzei.extra.USER_COMMAND";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_MARK_NOTIFICATION_READ.equals(action)) {
                markNotificationRead(context);
            } else if (ACTION_NEXT_ARTWORK.equals(action)) {
                Provider.nextArtwork(context);
            } else if (ACTION_USER_COMMAND.equals(action)) {
                triggerUserCommandFromRemoteInput(context, intent);
            }
        }
    }

    private void triggerUserCommandFromRemoteInput(final Context context, Intent intent) {
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        if (remoteInput == null) {
            return;
        }
        final String selectedCommand = remoteInput.getCharSequence(EXTRA_USER_COMMAND).toString();
        final PendingResult pendingResult = goAsync();
        final LiveData<Provider> providerLiveData = MuzeiDatabase.getInstance(context).providerDao()
                .getCurrentProvider(context);
        providerLiveData.observeForever(new Observer<Provider>() {
            @Override
            public void onChanged(@Nullable final Provider provider) {
                providerLiveData.removeObserver(this);
                if (provider != null) {
                    provider.getCommands(new Provider.CommandsCallback() {
                        @Override
                        public void onCallback(@NonNull final List<UserCommand> commands) {
                            for (UserCommand action : commands) {
                                if (TextUtils.equals(selectedCommand, action.getTitle())) {
                                    provider.sendAction(action.getId());
                                    break;
                                }
                                pendingResult.finish();
                            }
                        }
                    });
                }
            }
        });
    }

    public static void markNotificationRead(final Context context) {
        final LiveData<Artwork> artworkLiveData = MuzeiDatabase.getInstance(context).artworkDao().getCurrentArtwork();
        artworkLiveData.observeForever(new Observer<Artwork>() {
            @Override
            public void onChanged(@Nullable final Artwork lastArtwork) {
                artworkLiveData.removeObserver(this);
                if (lastArtwork != null) {
                    SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
                    sp.edit()
                            .putLong(PREF_LAST_READ_NOTIFICATION_ARTWORK_ID, lastArtwork.id)
                            .putString(PREF_LAST_READ_NOTIFICATION_ARTWORK_IMAGE_URI, lastArtwork.imageUri.toString())
                            .putString(PREF_LAST_READ_NOTIFICATION_ARTWORK_TOKEN, lastArtwork.token)
                            .apply();
                }

                cancelNotification(context);
            }
        });
    }

    public static void cancelNotification(Context context) {
        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.cancel(NOTIFICATION_ID);
    }

    public static void maybeShowNewArtworkNotification(Context context) {
        ArtDetailOpenedClosedEvent adoce = EventBus.getDefault().getStickyEvent(
                ArtDetailOpenedClosedEvent.class);
        if (adoce != null && adoce.isArtDetailOpened()) {
            return;
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        if (!sp.getBoolean(PREF_ENABLED, true)) {
            return;
        }

        ContentResolver contentResolver = context.getContentResolver();
        Artwork artwork = MuzeiDatabase.getInstance(context)
                .artworkDao()
                .getCurrentArtworkBlocking();
        if (artwork == null) {
            return;
        }

        long currentArtworkId = artwork.id;
        long lastReadArtworkId = sp.getLong(PREF_LAST_READ_NOTIFICATION_ARTWORK_ID, -1);
        String currentImageUri = artwork.imageUri.toString();
        String lastReadImageUri = sp.getString(PREF_LAST_READ_NOTIFICATION_ARTWORK_IMAGE_URI, null);
        String currentToken = artwork.token;
        String lastReadToken = sp.getString(PREF_LAST_READ_NOTIFICATION_ARTWORK_TOKEN, null);
        // We've already dismissed the notification if the IDs match
        boolean previouslyDismissedNotification = lastReadArtworkId == currentArtworkId;
        // We've already dismissed the notification if the image URIs match and both are not empty
        previouslyDismissedNotification = previouslyDismissedNotification ||
                (!TextUtils.isEmpty(lastReadImageUri) && !TextUtils.isEmpty(currentImageUri) &&
                    TextUtils.equals(lastReadImageUri, currentImageUri));
        // We've already dismissed the notification if the tokens match and both are not empty
        previouslyDismissedNotification = previouslyDismissedNotification ||
                (!TextUtils.isEmpty(lastReadToken) && !TextUtils.isEmpty(currentToken) &&
                        TextUtils.equals(lastReadToken, currentToken));
        if (previouslyDismissedNotification) {
            return;
        }

        Bitmap largeIcon;
        Bitmap background;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(contentResolver.openInputStream(
                    MuzeiContract.Artwork.CONTENT_URI), null, options);
            int width = options.outWidth;
            int height = options.outHeight;
            int shortestLength = Math.min(width, height);
            options.inJustDecodeBounds = false;
            int largeIconHeight = context.getResources()
                    .getDimensionPixelSize(android.R.dimen.notification_large_icon_height);
            options.inSampleSize = ImageUtil.calculateSampleSize(shortestLength, largeIconHeight);
            largeIcon = BitmapFactory.decodeStream(contentResolver.openInputStream(
                    MuzeiContract.Artwork.CONTENT_URI), null, options);

            // Use the suggested 400x400 for Android Wear background images per
            // http://developer.android.com/training/wearables/notifications/creating.html#AddWearableFeatures
            options.inSampleSize = ImageUtil.calculateSampleSize(height, 400);
            background = BitmapFactory.decodeStream(contentResolver.openInputStream(
                    MuzeiContract.Artwork.CONTENT_URI), null, options);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Unable to read artwork to show notification", e);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(context);
        }

        String artworkTitle = artwork.title;
        String title = TextUtils.isEmpty(artworkTitle)
                ? context.getString(R.string.app_name)
                : artworkTitle;
        NotificationCompat.Builder nb = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_muzei)
                .setColor(ContextCompat.getColor(context, R.color.notification))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.notification_new_wallpaper))
                .setLargeIcon(largeIcon)
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        Intent.makeMainActivity(new ComponentName(context, MuzeiActivity.class)),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .setDeleteIntent(PendingIntent.getBroadcast(context, 0,
                        new Intent(context, NewWallpaperNotificationReceiver.class)
                                .setAction(ACTION_MARK_NOTIFICATION_READ),
                        PendingIntent.FLAG_UPDATE_CURRENT));
        NotificationCompat.BigPictureStyle style = new NotificationCompat.BigPictureStyle()
                .bigLargeIcon(null)
                .setBigContentTitle(title)
                .setSummaryText(artwork.byline)
                .bigPicture(background);
        nb.setStyle(style);

        NotificationCompat.WearableExtender extender = new NotificationCompat.WearableExtender();

        // Support Next Artwork
        Provider provider = new Provider(context, artwork.sourceComponentName);
        if (provider.getSupportsNextArtworkBlocking()) {
            PendingIntent nextPendingIntent = PendingIntent.getBroadcast(context, 0,
                    new Intent(context, NewWallpaperNotificationReceiver.class)
                            .setAction(ACTION_NEXT_ARTWORK),
                    PendingIntent.FLAG_UPDATE_CURRENT);
            nb.addAction(
                    R.drawable.ic_notif_next_artwork,
                    context.getString(R.string.action_next_artwork_condensed),
                    nextPendingIntent);
            // Android Wear uses larger action icons so we build a
            // separate action
            extender.addAction(new NotificationCompat.Action.Builder(
                    R.drawable.ic_notif_full_next_artwork,
                    context.getString(R.string.action_next_artwork_condensed),
                    nextPendingIntent)
                    .extend(new NotificationCompat.Action.WearableExtender().setAvailableOffline(false))
                    .build());
        }
        List<UserCommand> commands = provider.getCommandsBlocking();
        // Show custom actions as a selectable list on Android Wear devices
        if (!commands.isEmpty()) {
            String[] actions = new String[commands.size()];
            for (int h=0; h<commands.size(); h++) {
                actions[h] = commands.get(h).getTitle();
            }
            PendingIntent userCommandPendingIntent = PendingIntent.getBroadcast(context, 0,
                    new Intent(context, NewWallpaperNotificationReceiver.class)
                            .setAction(ACTION_USER_COMMAND),
                    PendingIntent.FLAG_UPDATE_CURRENT);
            RemoteInput remoteInput = new RemoteInput.Builder(EXTRA_USER_COMMAND)
                    .setAllowFreeFormInput(false)
                    .setLabel(context.getString(R.string.action_user_command_prompt))
                    .setChoices(actions)
                    .build();
            extender.addAction(new NotificationCompat.Action.Builder(
                    R.drawable.ic_notif_full_user_command,
                    context.getString(R.string.action_user_command),
                    userCommandPendingIntent).addRemoteInput(remoteInput)
                    .extend(new NotificationCompat.Action.WearableExtender().setAvailableOffline(false))
                    .build());
        }
        Intent viewIntent = artwork.viewIntent;
        if (viewIntent != null) {
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                PendingIntent nextPendingIntent = PendingIntent.getActivity(context, 0,
                        viewIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                nb.addAction(
                        R.drawable.ic_notif_info,
                        context.getString(R.string.action_artwork_info),
                        nextPendingIntent);
                // Android Wear uses larger action icons so we build a
                // separate action
                extender.addAction(new NotificationCompat.Action.Builder(
                        R.drawable.ic_notif_full_info,
                        context.getString(R.string.action_artwork_info),
                        nextPendingIntent)
                        .extend(new NotificationCompat.Action.WearableExtender()
                                .setAvailableOffline(false))
                        .build());
            } catch (RuntimeException ignored) {
                // This is actually meant to catch a FileUriExposedException, but you can't
                // have catch statements for exceptions that don't exist at your minSdkVersion
            }
        }
        nb.extend(extender);

        // Hide the image and artwork title for the public version
        NotificationCompat.Builder publicBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_muzei)
                .setColor(ContextCompat.getColor(context, R.color.notification))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setAutoCancel(true)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notification_new_wallpaper))
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        Intent.makeMainActivity(new ComponentName(context, MuzeiActivity.class)),
                        PendingIntent.FLAG_UPDATE_CURRENT))
                .setDeleteIntent(PendingIntent.getBroadcast(context, 0,
                        new Intent(context, NewWallpaperNotificationReceiver.class)
                                .setAction(ACTION_MARK_NOTIFICATION_READ),
                        PendingIntent.FLAG_UPDATE_CURRENT));
        nb.setPublicVersion(publicBuilder.build());


        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.notify(NOTIFICATION_ID, nb.build());
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private static void createNotificationChannel(Context context) {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL,
                context.getString(R.string.notification_new_wallpaper_channel_name),
                NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }
}
