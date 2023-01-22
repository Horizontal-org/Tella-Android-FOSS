package rs.readahead.washington.mobile.views.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.IMyLocationConsumer;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;
import com.hzontal.tella_vault.Metadata;
import com.hzontal.tella_vault.MyLocation;
import com.hzontal.tella_vault.VaultFile;
import com.jakewharton.rxrelay2.PublishRelay;
import com.jakewharton.rxrelay2.Relay;

import org.hzontal.shared_ui.bottomsheet.BottomSheetUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.subjects.BehaviorSubject;
import kotlin.Unit;
import rs.readahead.washington.mobile.R;
import rs.readahead.washington.mobile.data.sharedpref.Preferences;
import rs.readahead.washington.mobile.mvp.contract.IMetadataAttachPresenterContract;
import rs.readahead.washington.mobile.presentation.entity.SensorData;
import rs.readahead.washington.mobile.util.DialogsUtil;
import rs.readahead.washington.mobile.util.LocationUtil;
import rs.readahead.washington.mobile.util.MetadataUtils;
import rs.readahead.washington.mobile.util.TelephonyUtils;
import rs.readahead.washington.mobile.views.base_ui.BaseLockActivity;


public abstract class MetadataActivity extends BaseLockActivity implements
        SensorEventListener, IMyLocationConsumer {
    private static final long LOCATION_REQUEST_INTERVAL = 5000; // aggressive
    private final static SensorData lightSensorData = new SensorData();
    private final static SensorData ambientTemperatureSensorData = new SensorData();
    private final static BehaviorSubject<MyLocation> locationSubject = BehaviorSubject.create();
    private static Location currentBestLocation;
    private final BehaviorSubject<List<String>> wifiSubject = BehaviorSubject.create();
    private SensorManager mSensorManager;
    private Sensor mLight;
    private Sensor mAmbientTemperature;
    //private FusedLocationProviderClient fusedLocationProviderClient;
    private GpsMyLocationProvider locationProvider;
   // private LocationCallback locationCallback;
    private WifiManager wifiManager;
    private BroadcastReceiver wifiScanResultReceiver;
    private boolean locationListenerRegistered = false;
    private boolean wifiReceiverRegistered = false;
    private boolean sensorListenerRegistered = false;
    private LocationManager locationManager;

    private AlertDialog metadataAlertDialog;
    private AlertDialog locationAlertDialog;
    private Relay<MetadataHolder> metadataCancelRelay;
    private CompositeDisposable disposables;

    private static void acceptBetterLocation(Location location) {
        if (!LocationUtil.isBetterLocation(location, currentBestLocation)) {
            return;
        }

        currentBestLocation = location;
        locationSubject.onNext(MyLocation.fromLocation(location));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Sensors
        mSensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        mAmbientTemperature = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

        // Location
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationProvider = new GpsMyLocationProvider(this);

        // Wifi
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiScanResultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                wifiSubject.onNext(getWifiStrings(wifiManager.getScanResults()));
            }
        };

        // UI stuff
        metadataCancelRelay = PublishRelay.create();
        disposables = new CompositeDisposable();
    }

    private List<String> getWifiStrings(List<ScanResult> results) {
        List<String> wifiStrings = new ArrayList<>(results.size());

        for (ScanResult result : results) {
            wifiStrings.add(result.SSID);
        }

        return wifiStrings;
    }

    protected void startSensorListening() {
        if (Preferences.isAnonymousMode()) {
            return;
        }

        mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mAmbientTemperature, SensorManager.SENSOR_DELAY_NORMAL);
        sensorListenerRegistered = true;
    }

    public void startLocationMetadataListening() {
        if (Preferences.isAnonymousMode()) {
            return;
        }

        startLocationListening();
        startWifiListening();
    }

    @SuppressWarnings("MissingPermission") // we have check
    private synchronized void startLocationListening() {
        if (isFineLocationPermissionDenied()) {
            return;
        }

        locationProvider.startLocationProvider(this);
        locationListenerRegistered = true;

        // get last known location to start with..
        getLastLocation();
    }

    @SuppressWarnings("MissingPermission") // we have check
    private void getLastLocation() {
        if (Preferences.isAnonymousMode()) {
            return;
        }

        if (isFineLocationPermissionDenied()) {
            return;
        }

        if (locationProvider.getLastKnownLocation() != null) {
            acceptBetterLocation(locationProvider.getLastKnownLocation());
        }
    }

    @Override
    public void onLocationChanged(Location location, IMyLocationProvider source) {
        acceptBetterLocation(location);
    }

    private synchronized void startWifiListening() {
        if (isFineLocationPermissionDenied()) {
            return;
        }

        if (wifiManager == null || wifiReceiverRegistered) {
            return;
        }

        // put what you know in subject..
        wifiSubject.onNext(getWifiStrings(wifiManager.getScanResults()));

        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);

        registerReceiver(wifiScanResultReceiver, filter);
        wifiReceiverRegistered = true;
    }

    protected synchronized void startWifiScan() {
        if (Preferences.isAnonymousMode()) {
            return;
        }

        if (wifiManager != null && wifiReceiverRegistered) {
            wifiManager.startScan();
        }
    }

    private void stopSensorListening() {
        if (!sensorListenerRegistered) {
            return;
        }

        mSensorManager.unregisterListener(this);
        sensorListenerRegistered = false;
    }

    public void stopLocationMetadataListening() {
        stopLocationListening();
        stopWifiListening();
    }

    private synchronized void stopLocationListening() {
        if (!locationListenerRegistered) {
            return;
        }

        locationProvider.stopLocationProvider();
        locationListenerRegistered = false;
    }

    private synchronized void stopWifiListening() {
        if (!wifiReceiverRegistered) {
            return;
        }

        unregisterReceiver(wifiScanResultReceiver);
        wifiReceiverRegistered = false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        startSensorListening();
    }

    @Override
    protected void onPause() {
        super.onPause();

        stopSensorListening();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (sensor.getType() == Sensor.TYPE_LIGHT) {
            lightSensorData.setValue(event.timestamp, event.values[0]);
        } else if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            ambientTemperatureSensorData.setValue(event.timestamp, event.values[0]);
        }
    }

    @Override
    protected void onDestroy() {
        if (disposables != null) {
            disposables.dispose();
        }
        hideLocationAlertDialog();
        wifiSubject.onComplete();

        super.onDestroy();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private boolean isFineLocationPermissionDenied() {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED);
    }

    private boolean isLocationProviderEnabled() {
        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return false;
        }

        // if we have GPS, we have location gathering..
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    protected void checkLocationSettings(final int requestCode, final LocationSettingsCheckDoneListener listener) {
        if (isFineLocationPermissionDenied()) {
            listener.onContinue();
            return;
        }

        if (!Preferences.isAnonymousMode() && !locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            showGpsMetadataDialog(requestCode, listener);
        } else {
            listener.onContinue();
        }
    }

    public void showGpsMetadataDialog(final int requestCode, final LocationSettingsCheckDoneListener listener) {
        maybeChangeTemporaryTimeout(() -> {
            BottomSheetUtils.showConfirmSheet(
                    getSupportFragmentManager(),
                    getString(R.string.verification_prompt_dialog_title),
                    getString(R.string.verification_prompt_dialog_expl),
                    getString(R.string.verification_prompt_action_enable_GPS),
                    getString(R.string.verification_prompt_action_ignore),
                    isConfirmed -> startEnableLocationIntent(requestCode, listener)
            );
            return Unit.INSTANCE;
        });
    }

    private void startEnableLocationIntent(final int requestCode, final LocationSettingsCheckDoneListener listener) {
        final Intent intent = LocationUtil.getDeviceLocationSettingIntent(this);
        if (intent != null)
        {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            this.startActivityForResult(intent,requestCode);
        }
    }

    public SensorData getLightSensorData() {
        return lightSensorData;
    }

    public SensorData getAmbientTemperatureSensorData() {
        return ambientTemperatureSensorData;
    }

    public Observable<List<String>> observeWifiData() {
        return wifiSubject;
    }

    public Observable<MyLocation> observeLocationData() {
        return locationSubject;
    }

    /**
     * Will emit combined object consisting of emitted both wifi and location data
     * combined, each time one of them changes. If there is no data for one of them,
     * empty data is in MetadataHolder object.
     *
     * @return stream of metadata holder objects
     */
    public Observable<MetadataHolder> observeMetadata() {
        return Observable.combineLatest(
                observeLocationData().startWith(MyLocation.createEmpty()),
                observeWifiData().startWith(Collections.<String>emptyList()),
                MetadataHolder::new
        )
                .filter(mh -> (!mh.getWifis().isEmpty() || !mh.getLocation().isEmpty()))
                .take((5 * 60 * 1000) / LOCATION_REQUEST_INTERVAL) // approx max 5 min of trying limit
                .takeUntil(mh -> !mh.getWifis().isEmpty() && !mh.getLocation().isEmpty());
    }

    // UI stuff
    public void attachMediaFileMetadata(final VaultFile vaultFile, final IMetadataAttachPresenterContract.IPresenter metadataAttacher) {
        // skip metadata if anonymous mode..
        if (Preferences.isAnonymousMode()) {
            return;
        }

        startWifiScan();

        final Metadata metadata = new Metadata();

        metadata.setFileName(vaultFile.name);
        metadata.setFileHashSHA256(vaultFile.hash);
        metadata.setTimestamp(System.currentTimeMillis());
        metadata.setAmbientTemperature(getAmbientTemperatureSensorData().hasValue() ? getAmbientTemperatureSensorData().getValue() : null);
        metadata.setLight(getLightSensorData().hasValue() ? getLightSensorData().getValue() : null);

        metadata.setDeviceID(MetadataUtils.getDeviceID());
        metadata.setWifiMac(MetadataUtils.getWifiMac());
        metadata.setIPv4(MetadataUtils.getIPv4());
        metadata.setIPv6(MetadataUtils.getIPv6());

        metadata.setDataType(MetadataUtils.getDataType(getBaseContext()));
        metadata.setNetwork(MetadataUtils.getNetwork(getBaseContext()));

        metadata.setNetworkType(MetadataUtils.getNetworkType(getBaseContext()));
        metadata.setHardware(MetadataUtils.getHardware());
        metadata.setManufacturer(MetadataUtils.getManufacturer());
        metadata.setScreenSize(MetadataUtils.getScreenSize(getBaseContext()));

        metadata.setLanguage(MetadataUtils.getLanguage());
        metadata.setLocale(MetadataUtils.getLocale());

        // set cells
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            metadata.setCells(TelephonyUtils.getCellInfo(this));
        }

        // if location gathering is not possible skip it
        if (!isLocationProviderEnabled() || isFineLocationPermissionDenied()) {
            metadataAttacher.attachMetadata(vaultFile, metadata);
            return;
        }

        // wait for set location metadata
        disposables.add(observeMetadata()
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe(disposable -> showMetadataProgressBarDialog())
                .takeUntil(metadataCancelRelay) // this observable emits when user press skip in dialog.
                .doFinally(this::hideMetadataProgressBarDialog)
                .subscribeWith(new DisposableObserver<MetadataHolder>() {
                    @Override
                    public void onNext(@NonNull MetadataActivity.MetadataHolder value) {
                        if (!value.getWifis().isEmpty()) {
                            metadata.setWifis(value.getWifis());
                            networkGatheringChecked();
                        }

                        if (!value.getLocation().isEmpty()) {
                            metadata.setMyLocation(value.getLocation());
                            locationGahteringChecked();
                        }

                        // skip if wifi gathering not possible in airplane mode
                        if ((Settings.Global.getInt(getBaseContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) != 0)
                                && !wifiManager.isWifiEnabled() && metadata.getWifis() == null) {
                            List<String> wifis = new ArrayList<>();
                            metadata.setWifis(wifis);
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        onComplete();
                    }

                    @Override
                    public void onComplete() {
                        metadataAttacher.attachMetadata(vaultFile, metadata);
                    }
                })
        );
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    protected void showMetadataProgressBarDialog() {
        metadataAlertDialog = DialogsUtil.showMetadataProgressBarDialog(this, (dialog, which) -> {
            metadataCancelRelay.accept(MetadataHolder.createEmpty()); // :)
        });
    }

    @SuppressWarnings("MethodOnlyUsedFromInnerClass")
    protected void hideMetadataProgressBarDialog() {
        if (metadataAlertDialog != null) {
            metadataAlertDialog.dismiss();
        }
    }

    private void networkGatheringChecked() {
        if (metadataAlertDialog != null) {
            //noinspection ConstantConditions
            metadataAlertDialog.findViewById(R.id.networkProgress).setVisibility(View.GONE);
            //noinspection ConstantConditions
            metadataAlertDialog.findViewById(R.id.networkCheck).setVisibility(View.VISIBLE);
        }
    }

    private void locationGahteringChecked() {
        if (metadataAlertDialog != null) {
            //noinspection ConstantConditions
            metadataAlertDialog.findViewById(R.id.locationProgress).setVisibility(View.GONE);
            //noinspection ConstantConditions
            metadataAlertDialog.findViewById(R.id.locationCheck).setVisibility(View.VISIBLE);
        }
    }

    protected void hideLocationAlertDialog() {
        if (locationAlertDialog != null) {
            locationAlertDialog.dismiss();
        }
    }

    interface LocationSettingsCheckDoneListener {
        void onContinue();
    }

   /* private static class MetadataLocationCallback extends LocationCallback {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            Location location = locationResult.getLastLocation();
            acceptBetterLocation(location);
        }
    }*/

    // Helper Classes
    static class MetadataHolder {
        private final MyLocation location;
        private List<String> wifis;


        MetadataHolder(MyLocation location, List<String> wifis) {
            this.location = location;
            setWifis(wifis);
        }

        static MetadataHolder createEmpty() {
            return new MetadataHolder(MyLocation.createEmpty(), Collections.emptyList());
        }

        MyLocation getLocation() {
            return location;
        }

        List<String> getWifis() {
            return wifis;
        }

        private void setWifis(final List<String> wifis) {
            this.wifis = new ArrayList<>();

            for (String wifi : wifis) {
                if (!this.wifis.contains(wifi)) {
                    this.wifis.add(wifi);
                }
            }
        }
    }
}
