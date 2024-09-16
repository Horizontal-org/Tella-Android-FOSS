package rs.readahead.washington.mobile.views.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hzontal.tella_vault.MyLocation;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider;

import java.util.ArrayList;

import rs.readahead.washington.mobile.R;
import rs.readahead.washington.mobile.databinding.ActivityLocationMapBinding;
import rs.readahead.washington.mobile.mvp.contract.ILocationGettingPresenterContract;
import rs.readahead.washington.mobile.mvp.presenter.LocationGettingPresenter;
import rs.readahead.washington.mobile.util.C;
import rs.readahead.washington.mobile.views.custom.MapViewOverlay;


public class LocationMapActivity extends MetadataActivity implements ILocationGettingPresenterContract.IView {
    public static final String SELECTED_LOCATION = "sl";
    public static final String CURRENT_LOCATION_ONLY = "ro";
    private final int PERMISSIONS_REQUEST_CODE = 1051;
    private MapViewOverlay map;
    private ActivityLocationMapBinding binding;

    Toolbar toolbar;
    ProgressBar progressBar;
    TextView hint;
    FloatingActionButton faButton;
    @Nullable
    private MyLocation myLocation;
    private Marker selectedMarker;
    private boolean virginMap = true;
    private LocationGettingPresenter locationGettingPresenter;
    private boolean readOnly;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLocationMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initView();

        myLocation = (MyLocation) getIntent().getSerializableExtra(SELECTED_LOCATION);
        readOnly = getIntent().getBooleanExtra(CURRENT_LOCATION_ONLY, true);
        locationGettingPresenter = new LocationGettingPresenter(this, readOnly);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.collect_form_geopoint_app_bar);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white);
        }

        Context ctx = this.getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        map.setTileSource(TileSourceFactory.MAPNIK);
        map.getController().setZoom(18.0);

        requestPermissionsIfNecessary(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.INTERNET
        });
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.ALWAYS);
        map.setMultiTouchControls(true);

        CompassOverlay compassOverlay = new CompassOverlay(this, map);
        compassOverlay.enableCompass();
        map.getOverlays().add(compassOverlay);

        map.addTapListener(new MapViewOverlay.OnTapListener() {
            @Override
            public void onMapTapped(GeoPoint geoPoint) {
            }

            @Override
            public void onMapLongPress(Location location) {
                if (!readOnly) {
                    showMyLocation(MyLocation.fromLocation(location));
                }
            }
        });
        faButton.setOnClickListener(view -> {
                    if (locationGettingPresenter.isGPSProviderEnabled()) {
                        startGettingLocation();
                    } else {
                        checkLocationSettings(C.GPS_PROVIDER, this::startGettingLocation);
                    }
                }
        );
        initMapLocationAndCamera();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.location_map_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            myLocation = null;
            setCancelAndFinish();
            return true;
        }

        if (id == R.id.menu_item_select) {
            setResultAndFinish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationGettingPresenter.destroy();
    }

    @Override
    public void onGettingLocationStart() {
        progressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onGettingLocationEnd() {
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onLocationSuccess(Location location) {
        if (location != null && virginMap) {
            virginMap = false;
            myLocation = MyLocation.fromLocation(location);
            showMyLocation(myLocation);
        }
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onNoLocationPermissions() {
        setCancelAndFinish();
    }

    @Override
    public void onGPSProviderDisabled() {
        showGpsMetadataDialog(C.GPS_PROVIDER, this::startGettingLocation);
    }

    @Override
    public Context getContext() {
        return this;
    }

    private void showMyLocation(@NonNull MyLocation myLocation) {
        GeoPoint point = new GeoPoint(myLocation.getLatitude(), myLocation.getLongitude());

        if (selectedMarker != null) {
            selectedMarker.setPosition(point);
            selectedMarker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER);
        } else {
            Marker startMarker = new Marker(map);
            startMarker.setPosition(point);
            startMarker.setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER);
            selectedMarker = startMarker;
            map.getOverlays().add(selectedMarker);
        }

        map.getController().animateTo(point);
        selectedMarker.setDraggable(!readOnly);
        //map.getController().setCenter(point);
    }

    private void initMapLocationAndCamera() {
        if (!readOnly) {
            hint.setVisibility(View.VISIBLE);
        }

        if (myLocation == null || readOnly) {
            locationGettingPresenter.startGettingLocation(!readOnly);
        }

        if (myLocation != null) {
            showMyLocation(myLocation);
        }
    }

    private void startGettingLocation() {
        locationGettingPresenter.startGettingLocation(!readOnly);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == C.GPS_PROVIDER && resultCode == RESULT_OK) {
            startGettingLocation();
        }
    }

    private void setResultAndFinish() {
        if (selectedMarker == null) {
            setCancelAndFinish();
        } else {
            myLocation = new MyLocation();
            myLocation.setLatitude(selectedMarker.getPosition().getLatitude());
            myLocation.setLongitude(selectedMarker.getPosition().getLongitude());
            setResult(Activity.RESULT_OK, new Intent().putExtra(SELECTED_LOCATION, myLocation));
            finish();
        }
    }

    private void setCancelAndFinish() {
        setResult(Activity.RESULT_CANCELED, new Intent().putExtra(SELECTED_LOCATION, myLocation));
        finish();
    }

    @Override
    public void onLocationChanged(Location location, IMyLocationProvider source) {
        showMyLocation(MyLocation.fromLocation(location));
    }

    private void initView() {
        toolbar = binding.toolbar;
        progressBar = binding.content.progressBar;
        hint = binding.content.info;
        faButton = binding.fabButton;
        map = binding.content.mapView;
    }
}
