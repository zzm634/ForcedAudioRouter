package com.zm.forcedaudiorouter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Layout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Switch enableSwitch;
    private Spinner devicesSpinner;
    private Button refreshButton;
    private Button saveButton;
    private TextView priDeviceName;
    private TextView priDeviceMac;
    private BTDeviceSpinnerAdapterAll spinnerAdapter;

    private SharedPreferences prefs;

    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    private ForcedAudioRouterService.BTDevice primaryDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Context context = getApplicationContext();

        prefs = getApplicationContext().getSharedPreferences(ForcedAudioRouterService.PREFS_NAMESPACE, MODE_PRIVATE);

        setContentView(R.layout.activity_main);

        enableSwitch = findViewById(R.id.enabled_switch);
        devicesSpinner = findViewById(R.id.device_spinner);
        spinnerAdapter = new BTDeviceSpinnerAdapterAll();
        devicesSpinner.setAdapter(spinnerAdapter);

        refreshButton = findViewById(R.id.refresh);
        saveButton = findViewById(R.id.save);
        priDeviceName = findViewById(R.id.priority_device_name);
        priDeviceMac = findViewById(R.id.priority_device_mac);

        prefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                Log.d("p", "main activity pref change: " + key);
                if (key.equals(ForcedAudioRouterService.PREF_ENABLED)) {
                    boolean checked = sharedPreferences.getBoolean(ForcedAudioRouterService.PREF_ENABLED, false);
                    if (enableSwitch.isChecked() != checked) {
                        runOnUiThread(() -> {
                            enableSwitch.setChecked(checked);
                        });
                    }
                } else if (key.equals(ForcedAudioRouterService.PREF_PRIORITY_DEVICE)) {
                    String serDevice = sharedPreferences.getString(ForcedAudioRouterService.PREF_PRIORITY_DEVICE, null);
                    updatePrimaryDeviceTextBoxes(serDevice);
                }
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        updatePrimaryDeviceTextBoxes(prefs.getString(ForcedAudioRouterService.PREF_PRIORITY_DEVICE, null));
        enableSwitch.setChecked(prefs.getBoolean(ForcedAudioRouterService.PREF_ENABLED, false));

        enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(ForcedAudioRouterService.PREF_ENABLED, isChecked).commit();
            }
        });

        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scan(v.getContext());
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ForcedAudioRouterService.BTDevice priDevice = (ForcedAudioRouterService.BTDevice)
                devicesSpinner.getSelectedItem();

                String ser = priDevice == null ? null : priDevice.serialize();
                prefs.edit().putString(ForcedAudioRouterService.PREF_PRIORITY_DEVICE, ser).commit();
            }
        });

        // bind or start the service?
        context.startService(new Intent(context, ForcedAudioRouterService.class));

        scan(context);
    }

    private void updatePrimaryDeviceTextBoxes(String serDevice) {
        if(serDevice == null) {
            priDeviceName.setText("None");
            priDeviceMac.setText("N/A");
        } else {
            ForcedAudioRouterService.BTDevice priDevice = new ForcedAudioRouterService.BTDevice(serDevice);
            priDeviceName.setText(priDevice.getName());
            priDeviceMac.setText(priDevice.getAddress());
        }
    }

    private void scan(Context context) {
        ForcedAudioRouterService.scanForNewDevices(context)
                .whenComplete((devices, ex) -> {
                    if (devices != null) {
                        runOnUiThread(() -> {
                            spinnerAdapter.setDevices(devices);
                        });
                    }
                });
    }

    public void setPrimaryDevice(ForcedAudioRouterService.BTDevice device) {
        if (!Objects.equals(primaryDevice, device)) {
            runOnUiThread(() -> {
                this.primaryDevice = device;
                prefs.edit().putString(ForcedAudioRouterService.PREF_PRIORITY_DEVICE, device.serialize()).commit();
            });
        }
    }

    private static class BTDeviceSpinnerAdapterAll extends BaseAdapter {

        private static final Comparator<ForcedAudioRouterService.BTDevice> BTDEVICE_COMPARATOR = Comparator.comparing(ForcedAudioRouterService.BTDevice::getName).thenComparing(ForcedAudioRouterService.BTDevice::getAddress);
        private List<ForcedAudioRouterService.BTDevice> otherDevices = Collections.emptyList();

        public void setDevices(Collection<? extends ForcedAudioRouterService.BTDevice> devices) {
            SortedSet<ForcedAudioRouterService.BTDevice> uniqueSortedDevices = new TreeSet<>(BTDEVICE_COMPARATOR);
            uniqueSortedDevices.addAll(devices);
            otherDevices = new ArrayList<>(uniqueSortedDevices);
            this.notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return otherDevices.size();
        }

        @Override
        public ForcedAudioRouterService.BTDevice getItem(int position) {
            return otherDevices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public synchronized View getView(int position, View convertView, ViewGroup parent) {
            final View v;
            if (convertView == null) {
                // initialize convertview
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.spinner_device_view, parent, false);
            } else {
                v = convertView;
            }

            TextView deviceName = (TextView) v.findViewById(R.id.spinner_dev_name);
            TextView deviceMac = (TextView) v.findViewById(R.id.spinner_dev_mac);

            ForcedAudioRouterService.BTDevice device = this.getItem(position);

            deviceName.setText(device.getName());
            deviceMac.setText(device.getAddress());

            return v;
        }
    }

    private static class BTDeviceSpinnerAdapter extends BaseAdapter {

        private static final Comparator<ForcedAudioRouterService.BTDevice> BTDEVICE_COMPARATOR = Comparator.comparing(ForcedAudioRouterService.BTDevice::getName).thenComparing(ForcedAudioRouterService.BTDevice::getAddress);
        private static final ForcedAudioRouterService.BTDevice NULL_DEVICE = new ForcedAudioRouterService.BTDevice("None", "N/A");
        private ForcedAudioRouterService.BTDevice primaryDevice = null;
        private final TreeSet<ForcedAudioRouterService.BTDevice> otherDevices = new TreeSet<>(BTDEVICE_COMPARATOR);

        public synchronized void setPrimaryDevice(ForcedAudioRouterService.BTDevice device) {
            if (primaryDevice != null) {
                otherDevices.add(primaryDevice);
            }
            if (device != null) {
                otherDevices.remove(device);
            }
            primaryDevice = device;
        }

        public synchronized void setOtherDevices(Collection<? extends ForcedAudioRouterService.BTDevice> devices) {
            otherDevices.clear();
            otherDevices.addAll(devices);
            if (primaryDevice != null) otherDevices.remove(primaryDevice);
        }

        @Override
        public synchronized int getCount() {
            return 1 + otherDevices.size();
        }

        @Override
        public synchronized ForcedAudioRouterService.BTDevice getItem(int position) {
            if (position == 0) {
                if (primaryDevice == null) {
                    return NULL_DEVICE;
                } else {
                    return primaryDevice;
                }
            } else {
                return get(position - 1);
            }
        }

        @Override
        public synchronized long getItemId(int position) {
            return 0;
        }

        @Override
        public synchronized View getView(int position, View convertView, ViewGroup parent) {
            ConstraintLayout v = (ConstraintLayout) convertView;
            if (v == null) {
                // initialize convertview
                v = (ConstraintLayout) LayoutInflater.from(parent.getContext()).inflate(R.layout.spinner_device_view, parent, false);
            }

            TextView deviceName = (TextView) v.getViewById(R.id.spinner_dev_name);
            TextView deviceMac = (TextView) v.getViewById(R.id.spinner_dev_mac);

            ForcedAudioRouterService.BTDevice device = this.getItem(position);

            deviceName.setText(device.getName());
            deviceMac.setText(device.getAddress());

            return v;
        }

        private synchronized ForcedAudioRouterService.BTDevice get(int position) {
            Iterator<ForcedAudioRouterService.BTDevice> devItr = otherDevices.iterator();
            while (devItr.hasNext() && position > 0) {
                position--;
                devItr.next();
            }

            if (devItr.hasNext()) {
                return devItr.next();
            } else {
                throw new IndexOutOfBoundsException();
            }
        }

    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.enabled_switch) {
            Switch enabled = (Switch) v;
            prefs.edit().putBoolean(ForcedAudioRouterService.PREF_ENABLED, enabled.isChecked()).commit();
        }
    }
}
