package com.zm.forcedaudiorouter;

import android.app.Application;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.MediaRouter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class RouterApp extends Application {


    private static Context context;

    public static void registerListener(Consumer<List<? extends Pair<Boolean, BluetoothDevice>>> listener) {
        btListeners.add(listener);
        scanDevices();
    }

    private static void scanDevices() {
        btMan.getAdapter().getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                BluetoothA2dp a2dp = (BluetoothA2dp) proxy;

                List<? extends BluetoothDevice> a2dpDevices = a2dp.getConnectedDevices();
                List<Pair<Boolean, BluetoothDevice>> activeDevices = new ArrayList<>(a2dpDevices.size());
                for(BluetoothDevice d : a2dpDevices) {
                    activeDevices.add(new Pair<>(a2dp.isA2dpPlaying(d), d));
                }

                for (Consumer<List<? extends Pair<Boolean,BluetoothDevice>>> list : btListeners) {
                    list.accept(activeDevices);
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {

            }
        }, BluetoothProfile.A2DP);
    }

    private static List<String> devicePriorities = Arrays.asList("00:0A:9B:83:80:72");

    public static void selectPriorityDevice() {
        btMan.getAdapter().getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                // get a list of connected devices
                BluetoothA2dp a2dp = (BluetoothA2dp) proxy;
                Map<String, BluetoothDevice> devicesByAddress = new HashMap<>();
                for(BluetoothDevice d : a2dp.getConnectedDevices()) {
                    devicesByAddress.put(d.getAddress(), d);
                }

                for(String deviceAddress : devicePriorities) {
                    BluetoothDevice d = devicesByAddress.get(deviceAddress);
                    if(d != null) {
                        // only set if not currently active device.
                        if(!a2dp.isA2dpPlaying(d)) {
                            setActiveDevice(a2dp, d);
                        }
                        return;
                    }
                }


            }
            @Override
            public void onServiceDisconnected(int profile) {
            }
        }, BluetoothProfile.A2DP);
    }

    public static void setActiveDevice(BluetoothDevice device) {
        btMan.getAdapter().getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
               setActiveDevice((BluetoothA2dp)proxy, device);
            }

            @Override
            public void onServiceDisconnected(int profile) {

            }
        }, BluetoothProfile.A2DP);
    }

    private static void setActiveDevice(BluetoothA2dp bta2dp, BluetoothDevice device) {
        try {
            Method forName = Class.forName("java.lang.Class").getMethod("forName", String.class);
            Method getMethod = Class.forName("java.lang.Class").getMethod("getMethod", String.class, Class[].class);

            Class bta2dpclass = (Class) forName.invoke(null, "android.bluetooth.BluetoothA2dp");
            Class[] setActiveParams = { BluetoothDevice.class};
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


    private static BluetoothManager btMan;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        btMan = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);


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
    }

    public static final class Pair<A,B> {
        public Pair(A first, B second) {
            this.first = first;
            this.second = second;
        }

        public final A first;
        public final B second;
    }

    private static List<Consumer<List<? extends Pair<Boolean,BluetoothDevice>>>> btListeners = new ArrayList<>();

}
