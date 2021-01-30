package com.welie.btserver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

import timber.log.Timber;

class BluetoothServer {
    private static BluetoothServer instance = null;

    @NotNull
    private final BluetoothPeripheralManager peripheralManager;

    public static synchronized BluetoothServer getInstance(Context context) {
        if (instance == null) {
            instance = new BluetoothServer(context.getApplicationContext());
        }
        return instance;
    }

    private final BluetoothPeripheralManagerCallback peripheralManagerCallback = new BluetoothPeripheralManagerCallback() {
        @Override
        public void onServiceAdded(int status, @NotNull BluetoothGattService service) {

        }

        @Override
        public void onCharacteristicRead(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
            Service serviceImplementation = serviceImplementations.get(characteristic.getService());
            if (serviceImplementation != null) {
                serviceImplementation.onCharacteristicRead(central, characteristic);
            }
        }

        @Override
        public GattStatus onCharacteristicWrite(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic, @NotNull byte[] value) {
            Service serviceImplementation = serviceImplementations.get(characteristic.getService());
            if (serviceImplementation != null) {
                return serviceImplementation.onCharacteristicWrite(central, characteristic, value);
            }
            return GattStatus.REQUEST_NOT_SUPPORTED;
        }

        @Override
        public void onDescriptorRead(@NotNull Central central, @NotNull BluetoothGattDescriptor descriptor) {
            BluetoothGattCharacteristic characteristic = Objects.requireNonNull(descriptor.getCharacteristic(), "Descriptor has no Characteristic");
            BluetoothGattService service = Objects.requireNonNull(characteristic.getService(), "Characteristic has no Service");
            Service serviceImplementation = serviceImplementations.get(service);
            if (serviceImplementation != null) {
                serviceImplementation.onDescriptorRead(central, descriptor);
            }
        }

        @Override
        public GattStatus onDescriptorWrite(@NotNull Central central, @NotNull BluetoothGattDescriptor descriptor, @NotNull byte[] value) {
            BluetoothGattCharacteristic characteristic = Objects.requireNonNull(descriptor.getCharacteristic(), "Descriptor has no Characteristic");
            BluetoothGattService service = Objects.requireNonNull(characteristic.getService(), "Characteristic has no Service");
            Service serviceImplementation = serviceImplementations.get(service);
            if (serviceImplementation != null) {
                return serviceImplementation.onDescriptorWrite(central, descriptor, value);
            }
            return GattStatus.REQUEST_NOT_SUPPORTED;
        }

        @Override
        public void onNotifyingEnabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
            Service serviceImplementation = serviceImplementations.get(characteristic.getService());
            if (serviceImplementation != null) {
                serviceImplementation.onNotifyingEnabled(central, characteristic);
            }
        }

        @Override
        public void onNotifyingDisabled(@NotNull Central central, @NotNull BluetoothGattCharacteristic characteristic) {
            Service serviceImplementation = serviceImplementations.get(characteristic.getService());
            if (serviceImplementation != null) {
                serviceImplementation.onNotifyingDisabled(central, characteristic);
            }
        }

        @Override
        void onNotificationSent(@NotNull Central central, @NotNull byte[] value, @NotNull BluetoothGattCharacteristic characteristic, @NotNull GattStatus status) {
            Service serviceImplementation = serviceImplementations.get(characteristic.getService());
            if (serviceImplementation != null) {
                serviceImplementation.onNotificationSent(central, value, characteristic, status);
            }
        }

        @Override
        public void onCentralConnected(@NotNull Central central) {
            for (Service serviceImplementation : serviceImplementations.values()) {
                serviceImplementation.onCentralConnected(central);
            }
        }

        @Override
        public void onCentralDisconnected(@NotNull Central central) {
            for (Service serviceImplementation : serviceImplementations.values()) {
                serviceImplementation.onCentralDisconnected(central);
            }
        }

        @Override
        public void onAdvertisingStarted(@NotNull AdvertiseSettings settingsInEffect) {

        }

        @Override
        public void onAdvertiseFailure(@NotNull AdvertiseError advertiseError) {

        }

        @Override
        public void onAdvertisingStopped() {

        }
    };

    public void startAdvertising(UUID serviceUUID) {
        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData advertiseData = new AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(new ParcelUuid(serviceUUID))
                .build();

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        peripheralManager.startAdvertising(advertiseSettings, scanResponse, advertiseData);
    }

    private void setupServices() {
        for (BluetoothGattService service : serviceImplementations.keySet()) {
            peripheralManager.add(service);
        }
    }

    private final HashMap<BluetoothGattService, Service> serviceImplementations = new HashMap<>();

    BluetoothServer(Context context) {
        // Plant a tree
        Timber.plant(new Timber.DebugTree());

        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {
            Timber.e("not supporting advertising");
        }

        bluetoothAdapter.setName(Build.MODEL);
        this.peripheralManager = new BluetoothPeripheralManager(context, bluetoothManager, peripheralManagerCallback);
        this.peripheralManager.removeAllServices();

        DeviceInformationService dis = new DeviceInformationService(peripheralManager);
        CurrentTimeService cts = new CurrentTimeService(peripheralManager);
        HeartRateService hrs = new HeartRateService(peripheralManager);
        serviceImplementations.put(dis.getService(), dis);
        serviceImplementations.put(cts.getService(), cts);
        serviceImplementations.put(hrs.getService(), hrs);

        setupServices();
        startAdvertising(hrs.getService().getUuid());
    }
}
