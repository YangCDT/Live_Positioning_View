package com.openpositioning.PositionMe.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.openpositioning.PositionMe.R;
import com.openpositioning.PositionMe.sensors.SensorFusion;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.Locale;

/**
 * A simple {@link Fragment} subclass. The startLocation fragment is displayed before the trajectory
 * recording starts. This fragment displays a map in which the user can adjust their location to
 * correct the PDR when it is complete
 *
 * @see HomeFragment the previous fragment in the nav graph.
 * @see RecordingFragment the next fragment in the nav graph.
 * @see SensorFusion the class containing sensors and recording.
 *
 * @author Virginia Cangelosi
 */

public class StartLocationFragment extends Fragment implements GoogleMap.OnMyLocationButtonClickListener {

    private Button button;
    private SensorFusion sensorFusion = SensorFusion.getInstance();
    private LatLng position;
    private float[] startPosition = new float[2];
    private float zoom = 19f;
    private GoogleMap mMap;
    private GroundOverlay nucleusOverlay, libraryOverlay;
    private LatLngBounds nucleusBounds = new LatLngBounds(new LatLng(55.922819, -3.174790),
                                                          new LatLng(55.923329, -3.173853));
    private LatLngBounds libraryBounds = new LatLngBounds(new LatLng(55.922732, -3.175183),
                                                          new LatLng(55.923065, -3.174770));
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private int selectedFloor = 1;
    private int currentFloor;
    private String currentBuilding = ""; // "library", "nucleus", or ""
    private Button btnSelectFloorMap; // Button for floor map selection
    private final CharSequence[] LIBRARY_FLOOR_ITEMS = {"Ground Floor", "First Floor", "Second Floor", "Third Floor"};
    private final CharSequence[] NUCLEUS_FLOOR_ITEMS = {"LG Floor","Ground Floor", "First Floor", "Second Floor", "Third Floor"};
    private boolean isAutoFloorMapEnabled = false; // Default to automatic updates
    private Handler elevationUpdateHandler;
    private Runnable elevationUpdateTask;
    private TextView elevation;
    private boolean isTracking = true;
    private ArrayList<LatLng> routePoints = new ArrayList<>();
    private Button startButton;
    private float elevationVal;

    public StartLocationFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((AppCompatActivity) getActivity()).getSupportActionBar().hide();
        View rootView = inflater.inflate(R.layout.fragment_startlocation, container, false);


        startPosition = sensorFusion.getGNSSLatitude(false);
        zoom = startPosition[0] == 0 && startPosition[1] == 0 ? 1f : 19f;

        SupportMapFragment supportMapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.startMap);
        supportMapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                setupMap(googleMap);
            }
        });
        return rootView;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sensorFusion.startRecording();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(getActivity());

        createLocationRequest();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    // Update UI with location data
                    // Check if the current position is inside the boundary
                    if (isAutoFloorMapEnabled) {
                        selectedFloor = determineFloor(elevationVal);
                    }

                    LatLng currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
                    manageOverlaysBasedOnPosition(currentPosition ,selectedFloor);

                    if (isTracking) { // Check if tracking is enabled
                        LatLng newPoint = new LatLng(location.getLatitude(), location.getLongitude());
                        routePoints.add(newPoint);
                        updateMapRoute(); // Update the map with the new route
                    }
                }
            }
        };

        elevationUpdateHandler = new Handler();
        elevationUpdateTask = new Runnable() {
            @Override
            public void run() {
                updateElevationData(); // Your method to update elevation
                elevationUpdateHandler.postDelayed(this, 1000); // Schedule this task again after 500ms
            }
        };
    }

    private void createLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setInterval(2000);
        locationRequest.setFastestInterval(100);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private Polyline currentRoute;

    private void updateMapRoute() {
        if (currentRoute == null) {
            PolylineOptions polylineOptions = new PolylineOptions().addAll(routePoints).width(5).color(Color.RED);
            currentRoute = mMap.addPolyline(polylineOptions);
        } else {
            currentRoute.setPoints(routePoints);
        }
    }

    private int determineFloor(float elevation) {
        // Assuming ground floor starts at 0 meters and each floor is 3 meters high
        return (int) (elevation / 10.0); // This will floor the division result to get the current floor
    }


    private static final CharSequence[] MAP_TYPE_ITEMS =
            {"Road Map", "Hybrid", "Satellite", "Terrain"};

    private void showMapTypeSelectorDialog() {
        // Prepare the dialog by setting up a Builder.
        final String fDialogTitle = "Select Map Type";
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(fDialogTitle);

        // Find the current map type to pre-check the item representing the current state.
        int checkItem = mMap.getMapType();

        // Add an OnClickListener to the dialog, so that the selection will be handled.
        builder.setSingleChoiceItems(
                MAP_TYPE_ITEMS,
                checkItem,
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int item) {
                        // Perform an action depending on which item was selected.
                        switch (item) {
                            case 0: // "Road Map"
                                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                                break;
                            case 1: // "Hybrid"
                                mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
                                break;
                            case 2: // "Satellite"
                                mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
                                break;
                            case 3: // "Terrain"
                                mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
                                break;
                            default:
                                mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
                        }
                        dialog.dismiss();
                    }
                }
        );

        // Build the dialog and show it.
        AlertDialog fMapTypeDialog = builder.create();
        fMapTypeDialog.setCanceledOnTouchOutside(true);
        fMapTypeDialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        startLocationUpdates();
        elevationUpdateHandler.post(elevationUpdateTask); // Start elevation data updates

    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null /* Looper */);
    }

    @Override
    public void onPause() {
        super.onPause();
        stopLocationUpdates();
        elevationUpdateHandler.removeCallbacks(elevationUpdateTask); // Stop elevation data updates

    }

    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }


    @SuppressLint("MissingPermission")
    private void setupMap(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMyLocationEnabled(true);
        mMap.setOnMyLocationButtonClickListener(this);
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);

        enableMapUIControls();

        position = new LatLng(startPosition[0], startPosition[1]);
        addStartPositionMarker();
        mMap.setOnMarkerDragListener(new MarkerDragListener());
    }

    private void enableMapUIControls() {
        mMap.getUiSettings().setAllGesturesEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
    }

    private void addStartPositionMarker() {
        mMap.addMarker(new MarkerOptions().position(position).title("Start Position")).setDraggable(true);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, zoom));
    }


    private void manageOverlaysBasedOnPosition(LatLng currentPosition, int floors) {
        boolean isUserInside = false; // Flag to check if the user is inside any building boundary
        // Handle nucleus overlay
        if (nucleusBounds.contains(currentPosition)) {
            isUserInside = true;
            currentBuilding = "nucleus";
            // If the overlay doesn't exist or the floor has changed
            if (nucleusOverlay == null || floors != currentFloor) {
                if (nucleusOverlay != null) {
                    nucleusOverlay.remove(); // Remove the existing overlay
                }
                // Add the new overlay for the current floor
                nucleusOverlay = mMap.addGroundOverlay(new GroundOverlayOptions()
                        .image(BitmapDescriptorFactory.fromResource(getNucleusFloorResourceId(floors)))
                        .positionFromBounds(nucleusBounds));
                currentFloor = floors;
            }
        } else {
            removeOverlayIfPresent("nucleus");
        }

        // Handle library overlay
        if (libraryBounds.contains(currentPosition)) {
            isUserInside = true;
            currentBuilding = "library";
            // If the overlay doesn't exist or the floor has changed
            if (libraryOverlay == null || floors != currentFloor) {
                if (libraryOverlay != null) {
                    libraryOverlay.remove(); // Remove the existing overlay
                }
                // Add the new overlay for the current floor
                libraryOverlay = mMap.addGroundOverlay(new GroundOverlayOptions()
                        .image(BitmapDescriptorFactory.fromResource(getLibraryFloorResourceId(floors)))
                        .positionFromBounds(libraryBounds));
                currentFloor = floors;
            }
        } else {
            removeOverlayIfPresent("library");
        }

        // Update the visibility of the btnSelectFloorMap based on user location
        if (btnSelectFloorMap != null) {
            btnSelectFloorMap.setVisibility(isUserInside ? View.VISIBLE : View.GONE);
        }
    }

    // Resource ID mapping for library floors
    private int getLibraryFloorResourceId(int floor) {
        switch (floor) {
            case 0: return R.drawable.libraryg;
            case 1: return R.drawable.library1;
            case 2: return R.drawable.library2;
            case 3: return R.drawable.library3;
            default: return 1; // Invalid floor
        }
    }

    // Resource ID mapping for nucleus floors
    private int getNucleusFloorResourceId(int floor) {
        switch (floor) {
            case 0: return R.drawable.nucleuslg;
            case 1: return R.drawable.nucleusg;
            case 2: return R.drawable.nucleus1;
            case 3: return R.drawable.nucleus2;
            case 4: return R.drawable.nucleus3;
            default: return 1; // Invalid floor
        }
    }

    private void removeOverlayIfPresent(String overlayType) {
        if ("nucleus".equals(overlayType) && nucleusOverlay != null) {
            nucleusOverlay.remove();
            nucleusOverlay = null;
            currentBuilding = null; // "library", "nucleus", or ""

        } else if ("library".equals(overlayType) && libraryOverlay != null) {
            libraryOverlay.remove();
            libraryOverlay = null;
            currentBuilding = null;
        }
    }

    @Override
    public boolean onMyLocationButtonClick() {
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false;
    }

    private void updateElevationData() {
        elevationVal = sensorFusion.getElevation(); // Assuming getElevation() returns a float
        elevation.setText(getString(R.string.elevation, String.format("%.1f", elevationVal)));

    }


    class MarkerDragListener implements GoogleMap.OnMarkerDragListener {
        @Override
        public void onMarkerDragStart(Marker marker) {}

        @Override
        public void onMarkerDragEnd(Marker marker) {
            startPosition[0] = (float) marker.getPosition().latitude;
            startPosition[1] = (float) marker.getPosition().longitude;
        }

        @Override
        public void onMarkerDrag(Marker marker) {}
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        button = view.findViewById(R.id.startLocationDone);
        button.setOnClickListener(v -> navigateToRecordingFragment(v));

        button = view.findViewById(R.id.startLocationDone);
        button.setOnClickListener(v -> navigateToRecordingFragment(v));

        Switch switchAutoFloorMap = view.findViewById(R.id.switchAutoFloorMap);
        switchAutoFloorMap.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isAutoFloorMapEnabled = isChecked;
        });

        this.elevation = view.findViewById(R.id.elevationData);

        // Add this part to set up the OnClickListener for your map type button
        Button btnChangeMapType = view.findViewById(R.id.btnChangeMapType);
        btnChangeMapType.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMapTypeSelectorDialog();
            }
        });

        btnSelectFloorMap = view.findViewById(R.id.btnSelectFloorMap);
        btnSelectFloorMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFloorMapSelectorDialog();
            }
        });

        this.startButton = view.findViewById(R.id.startButton);
        startButton.setOnClickListener(v -> {
            if (!isTracking) {
                routePoints.clear(); // Clear previous points
                isTracking = true; // Start tracking
                startButton.setText("Stop"); // Change button text to indicate tracking state
            } else {
                isTracking = false; // Stop tracking
                startButton.setText("Start"); // Reset button text
                // Optionally, stop location updates here
            }
        });

    }

    private void navigateToRecordingFragment(View view) {
        sensorFusion.startRecording();
        sensorFusion.setStartGNSSLatitude(startPosition);
        NavDirections action = StartLocationFragmentDirections.actionStartLocationFragmentToRecordingFragment();
        Navigation.findNavController(view).navigate(action);
    }

    /**private void showFloorMapSelectorDialog() {
        // Prepare the dialog by setting up a Builder.
        final String fDialogTitle = "Select Floor Map";
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(fDialogTitle);

        // Add an OnClickListener to the dialog, so that the selection will be handled.
        builder.setItems(FLOOR_MAP_ITEMS, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                // Update the selected floor based on user selection
                selectedFloor = item; // Update your selectedFloor variable accordingly
                dialog.dismiss();
            }
        });

        // Build the dialog and show it.
        AlertDialog floorMapDialog = builder.create();
        floorMapDialog.setCanceledOnTouchOutside(true);
        floorMapDialog.show();
    }*/

    private void showFloorMapSelectorDialog() {
        final String fDialogTitle = "Select Floor Map";
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(fDialogTitle);

        CharSequence[] items = currentBuilding.equals("nucleus") ? NUCLEUS_FLOOR_ITEMS : LIBRARY_FLOOR_ITEMS;

        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                selectedFloor = item;
                manageOverlaysBasedOnPosition(position, selectedFloor);
                dialog.dismiss();
            }
        });

        AlertDialog floorMapDialog = builder.create();
        floorMapDialog.setCanceledOnTouchOutside(true);
        floorMapDialog.show();
    }



}
