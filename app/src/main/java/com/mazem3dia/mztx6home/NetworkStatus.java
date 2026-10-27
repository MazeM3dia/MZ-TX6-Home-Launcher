package com.mazem3dia.mztx6home;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

/**
 * The Wi-Fi / Ethernet / No internet / Offline label. Listens only between start() and stop(), which Home
 * calls in onStart and onStop: nothing runs while an app is on screen. Reads the network state only
 * (ACCESS_NETWORK_STATE); the launcher has no internet access.
 */
final class NetworkStatus extends ConnectivityManager.NetworkCallback {
    private final ConnectivityManager connectivity;
    private final TextView label;
    /** Button labels off: the icon alone while all is well; No internet and Offline keep their words. */
    private boolean iconOnly;
    /** The last state shown, so a change of iconOnly can show it again. */
    private NetworkCapabilities shown;
    private final int padding;
    private final Handler main = new Handler(Looper.getMainLooper());

    NetworkStatus(Context context, TextView label, boolean iconOnly) {
        this.connectivity = context.getSystemService(ConnectivityManager.class);
        this.label = label;
        this.iconOnly = iconOnly;
        this.padding = label.getCompoundDrawablePadding();
    }

    void start() {
        // Offline until the system reports a network; registering reports the current one straight away.
        show(null);
        connectivity.registerDefaultNetworkCallback(this);
    }

    /** Customize > Button labels, applied at once. */
    void setIconOnly(boolean iconOnly) {
        this.iconOnly = iconOnly;
        show(shown);
    }

    void stop() {
        connectivity.unregisterNetworkCallback(this);
        main.removeCallbacksAndMessages(null);
    }

    // The callbacks arrive on a system thread; the label is changed on the main thread.
    @Override
    public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
        main.post(() -> show(capabilities));
    }

    @Override
    public void onLost(Network network) {
        main.post(() -> show(null));
    }

    private void show(NetworkCapabilities capabilities) {
        shown = capabilities;
        int text, icon = 0, color = R.color.text_secondary;
        if (capabilities == null) {
            text = R.string.network_offline;
            color = R.color.offline;
        } else {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = R.string.network_ethernet;
                icon = R.drawable.ic_ethernet;
            } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                text = R.string.network_wifi;
                icon = R.drawable.ic_wifi;
            } else {
                text = R.string.network_other;
            }
            // Validated: Android's own check reached the internet. Connected but not validated is a router with
            // no internet, or a hotel Wi-Fi waiting for its login page.
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                text = R.string.network_no_internet;
                color = R.color.no_internet;
            }
        }
        boolean words = !iconOnly || color != R.color.text_secondary || icon == 0;
        label.setText(words ? label.getContext().getString(text) : null);
        label.setContentDescription(label.getContext().getString(text));
        label.setTextColor(label.getContext().getColor(color));
        label.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        label.setCompoundDrawablePadding(words ? padding : 0);
    }
}
