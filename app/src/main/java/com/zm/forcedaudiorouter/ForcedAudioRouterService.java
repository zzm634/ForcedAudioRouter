package com.zm.forcedaudiorouter;

import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaRouter;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

public class ForcedAudioRouterService extends Service {

    private final boolean DEBUG_TOASTS = true;

    private boolean enabled;

    private String priorityMacAddress = null;

    private BluetoothManager bt;

    public static final String PREFS_NAMESPACE = "com.zm.forcedaudiorouter";

    private void selectPriorityDevice() {
        if (!enabled || priorityMacAddress == null || bt == null || bt.getAdapter() == null) {
            return;
        }

        bt.getAdapter().getProfileProxy(getApplicationContext(), new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                BluetoothA2dp a2dp = (BluetoothA2dp) proxy;
                Map<String, BluetoothDevice> devicesByAddress = new HashMap<>();
                for (BluetoothDevice d : a2dp.getConnectedDevices()) {
                    if(d.getAddress().equals(priorityMacAddress) && !a2dp.isA2dpPlaying(d)) {
                        setActiveDevice(a2dp, d);
                    }
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
            }
        }, BluetoothProfile.A2DP);
    }

    public static final String PREF_ENABLED = "enabled";

    public static final class BTDevice {

        public BTDevice(String serialized) {
            int pipe = serialized.indexOf('|');
            this.address = serialized.substring(0,pipe);
            this.name = serialized.substring(pipe+1);
        }

        public BTDevice(String name, String address) {
            this.name = name;
            this.address = address;
        }

        public final String name;
        public final String address;

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }

        @NonNull
        @Override
        public String toString() {
            return String.format("%s [%s]", name, address);
        }

        public String serialize() { return this.address + '|' + this.name; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BTDevice btDevice = (BTDevice) o;
            return Objects.equals(address, btDevice.address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(address);
        }
    }

    private static final int[] BT_CONNECTION_STATES = {
            BluetoothProfile.STATE_CONNECTED,
            BluetoothProfile.STATE_CONNECTING,
            BluetoothProfile.STATE_DISCONNECTED,
            BluetoothProfile.STATE_DISCONNECTING
    };

    public static CompletableFuture<List<? extends BTDevice>> scanForNewDevices(Context context) {
        Log.d("d","scanning for new devices");
        SharedPreferences prefs = context.getSharedPreferences(ForcedAudioRouterService.PREFS_NAMESPACE, Context.MODE_PRIVATE);

       final CompletableFuture<List<? extends BTDevice>> devicesFuture = new CompletableFuture<>();

        // Scan for devices and add any unknown ones to the preferences
        BluetoothManager bt = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        if(bt.getAdapter() == null) {
            // no bluetooth adapter, fake some shit
            List<BTDevice> fakeDevices = Arrays.asList(
                    new BTDevice("Fake Device 1", "00:11:22:33:44:55"),
                    new BTDevice("Fake Device 2", "AA:BB:CC:DD:EE:FF")
            );
            devicesFuture.complete(fakeDevices);
        } else {
            bt.getAdapter().getProfileProxy(context, new BluetoothProfile.ServiceListener() {
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    BluetoothA2dp a2dp = (BluetoothA2dp) proxy;

                    List<ForcedAudioRouterService.BTDevice> devices = new ArrayList<>();

                    boolean updated = false;
                    // all known a2dp devices
                    for (BluetoothDevice device : a2dp.getDevicesMatchingConnectionStates(BT_CONNECTION_STATES)) {
                        ForcedAudioRouterService.BTDevice btDevice = new ForcedAudioRouterService.BTDevice(device.getName(), device.getAddress());
                        if (!devices.contains(btDevice)) {
                            Log.d("d", "found device: " + btDevice);
                            devices.add(btDevice);
                        }
                    }

                    devicesFuture.complete(devices);
                }

                @Override
                public void onServiceDisconnected(int profile) {
                    if (!devicesFuture.isDone()) {
                        devicesFuture.completeExceptionally(new RuntimeException("No devices found?"));
                    }
                }
            }, BluetoothProfile.A2DP);
        }

        return devicesFuture;
    }

    public static final String PREF_PRIORITY_DEVICE = "pri_device";

    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    private static String getPriorityDeviceMacAddress(SharedPreferences prefs) {
        String priDeviceSer = prefs.getString(PREF_PRIORITY_DEVICE,null);
        if(priDeviceSer == null) {
            return null;
        } else {
            return (new BTDevice(priDeviceSer)).getAddress();
        }
    }

    @Override
    public void onCreate() {
        // Set up bluetooth manager handle and media router callbacks
        Context context = getApplicationContext();

        if(bt != null) {
            // service already started
            if(DEBUG_TOASTS) {
                Toast.makeText(context, "FARService already running", Toast.LENGTH_SHORT).show();
            }

            return;
        }

        bt = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAMESPACE, Context.MODE_PRIVATE);
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if (key.equals(PREF_ENABLED)) {
                    enabled = sharedPreferences.getBoolean(PREF_ENABLED, false);

                    if(DEBUG_TOASTS) {
                        Toast.makeText(context, "FARService " + (enabled ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
                    }
                } else if (key.equals(PREF_PRIORITY_DEVICE)) {
                    priorityMacAddress = getPriorityDeviceMacAddress(prefs);

                    if(DEBUG_TOASTS) {
                        Toast.makeText(context, "FARService Primary Device: " + priorityMacAddress, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        enabled = prefs.getBoolean(PREF_ENABLED, false);
        priorityMacAddress = getPriorityDeviceMacAddress(prefs);


        MediaRouter router = (MediaRouter) context.getSystemService(Context.MEDIA_ROUTER_SERVICE);
        router.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO, new MediaRouter.Callback() {
            @Override
            public void onRouteSelected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
                selectPriorityDevice();
            }

            @Override
            public void onRouteUnselected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
            }

            @Override
            public void onRouteAdded(MediaRouter router, MediaRouter.RouteInfo info) {
                selectPriorityDevice();
            }

            @Override
            public void onRouteRemoved(MediaRouter router, MediaRouter.RouteInfo info) {
                selectPriorityDevice();
            }

            @Override
            public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo info) {
                selectPriorityDevice();
            }

            @Override
            public void onRouteGrouped(MediaRouter router, MediaRouter.RouteInfo info, MediaRouter.RouteGroup group, int index) {
            }

            @Override
            public void onRouteUngrouped(MediaRouter router, MediaRouter.RouteInfo info, MediaRouter.RouteGroup group) {
            }

            @Override
            public void onRouteVolumeChanged(MediaRouter router, MediaRouter.RouteInfo info) {
            }
        });

        selectPriorityDevice();

        if(DEBUG_TOASTS)
        Toast.makeText(context, "RouterServiceStarted",Toast.LENGTH_SHORT).show();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        return START_STICKY;
    }

    private static void setActiveDevice(BluetoothA2dp bta2dp, BluetoothDevice device) {
        try {
            Method forName = Class.forName("java.lang.Class").getMethod("forName", String.class);
            Method getMethod = Class.forName("java.lang.Class").getMethod("getMethod", String.class, Class[].class);

            Class bta2dpclass = (Class) forName.invoke(null, "android.bluetooth.BluetoothA2dp");
            Class[] setActiveParams = {BluetoothDevice.class};
            Method setActiveDevice = (Method) getMethod.invoke(bta2dpclass, "setActiveDevice", setActiveParams);

            setActiveDevice.invoke(bta2dp, device);

        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }
}
