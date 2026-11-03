package com.mazem3dia.mztx6home;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

/**
 * The Wi-Fi / Ethernet / No internet / Offline label, with "· VPN" while a VPN runs on top and "· DNS" while
 * Private DNS (DNS over TLS, e.g. one.one.one.one) is in use. Listens only between start() and stop(), which Home
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
    /** The default network's DNS goes over TLS (Android 9+): Private DNS, set by hand or found automatically. */
    private boolean privateDns;
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
    public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
        boolean encrypted = Build.VERSION.SDK_INT >= 28 && properties.isPrivateDnsActive();
        main.post(() -> {
            privateDns = encrypted;
            show(shown);
        });
    }

    @Override
    public void onLost(Network network) {
        main.post(() -> {
            privateDns = false;
            show(null);
        });
    }

    private void show(NetworkCapabilities capabilities) {
        shown = capabilities;
        // A VPN (1.1.1.1 in DNS or WARP mode, and the like) becomes the default network on top of the Wi-Fi or
        // Ethernet: name the real link and add "VPN". Which app owns the VPN, or its mode, apps can't tell.
        boolean vpn = capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        NetworkCapabilities link = capabilities;
        if (vpn) {
            NetworkCapabilities under = underlying();
            if (under != null) link = under;
        }
        int text, icon = 0, color = R.color.text_secondary;
        if (link == null) {
            text = R.string.network_offline;
            color = R.color.offline;
        } else {
            if (link.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                text = R.string.network_ethernet;
                icon = R.drawable.ic_ethernet;
            } else if (link.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                text = R.string.network_wifi;
                icon = R.drawable.ic_wifi;
            } else {
                text = vpn ? R.string.network_vpn : R.string.network_other;
            }
            // Validated: Android's own check reached the internet. Connected but not validated is a router with
            // no internet, or a hotel Wi-Fi waiting for its login page.
            if (!link.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                text = R.string.network_no_internet;
                color = R.color.no_internet;
            }
        }
        Context context = label.getContext();
        String name = context.getString(text);
        // "Wi-Fi · VPN", "Wi-Fi · DNS", "Wi-Fi · VPN · DNS"; with button labels off the icon and "VPN · DNS".
        // No internet and Offline show their own words only.
        String extras = null;
        if (color == R.color.text_secondary && icon != 0) {
            if (vpn) extras = context.getString(R.string.network_vpn);
            if (privateDns) extras = extras == null ? context.getString(R.string.network_dns)
                    : context.getString(R.string.network_with, extras, context.getString(R.string.network_dns));
        }
        String full = extras == null ? name : context.getString(R.string.network_with, name, extras);
        String words = null;
        if (extras != null) words = iconOnly ? extras : full;
        else if (!iconOnly || color != R.color.text_secondary || icon == 0) words = name;
        label.setText(words);
        label.setContentDescription(full);
        label.setTextColor(context.getColor(color));
        label.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        label.setCompoundDrawablePadding(words != null ? padding : 0);
    }

    /** The network under a VPN: Ethernet before Wi-Fi, as Android prefers; null if none has internet. */
    @SuppressWarnings("deprecation")   // getAllNetworks: its replacement needs Android 12; the TX6 runs 9
    private NetworkCapabilities underlying() {
        NetworkCapabilities found = null;
        for (Network network : connectivity.getAllNetworks()) {
            NetworkCapabilities c = connectivity.getNetworkCapabilities(network);
            if (c == null || c.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                    || !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return c;
            if (found == null) found = c;
        }
        return found;
    }
}
