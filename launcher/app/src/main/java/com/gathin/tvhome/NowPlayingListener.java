package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Empty on purpose. Android only hands the active media sessions (what is playing, e.g. Spotify) to an app
 * with an enabled notification listener, so this exists to hold that grant. It reads no notifications.
 * Enable over adb:
 *   cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener
 */
public class NowPlayingListener extends NotificationListenerService {
}
