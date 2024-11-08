package com.example.mygooglemaps;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MenuItem;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
//import com.example.Fragment.*;

import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.*;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.io.*;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static androidx.core.content.PermissionChecker.PERMISSION_DENIED;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.gms.common.api.Status;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.android.libraries.places.api.Places;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.lang.Math;

import java.sql.ResultSet;
import java.sql.SQLException;


public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, BottomNavigationView.OnNavigationItemSelectedListener, GoogleMap.OnMarkerClickListener,SearchHistoryListener, DatabaseListener {
    String TAG = "placeautocomplete";

    public static double longi;
    public static double lati;
    private TextView textViewDisplay;

    public static String address;

    public static double markerLat = 0;

    public static double markerLong = 0;

    public static double count = 1;
    private static final int PERMISSIONS_FINE_LOCATION = 99;
    private GoogleMap myMap;

    private DatabaseHelper dbHelper;
    private SearchHistory search;

    private Button filterButton;
    private PopupWindow popupWindow;
    private char filterResult = 'c';
    private Place curPlace = null;

    private String place_ID = "";

    public static String distancebtwnmarker = "";
    public static String durationbtwnmarker = "";
    public static String display = "";


    ArrayList<LatLng> nearestCarparks = new ArrayList<LatLng>();


    ArrayList<Marker> markers = new ArrayList<Marker>();

    LocationRequest locationRequest;
    LocationCallback locationCallback;


    //Googles API for location services. the Majority of this apps functions using this class
    FusedLocationProviderClient fusedLocationProviderClient;
    private FusedLocationProviderClient fusedLocationClient;

    final androidx.fragment.app.FragmentManager fragmentManager = getSupportFragmentManager();
    final CarparkInformationFragment carparkInfoFragment = new CarparkInformationFragment();

    BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);
        bottomNavigationView.setSelectedItemId(R.id.home);

        dbHelper = new DatabaseHelper(this);
        search = new SearchHistory(this);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.


        //initialise Places
        if (!Places.isInitialized()) {
            try {
                ApplicationInfo ai = getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
                Bundle bundle = ai.metaData;
                String myApiKey = bundle.getString("com.google.android.geo.API_KEY");
                Places.initialize(getApplicationContext(), myApiKey);
            } catch (Exception e) {
                Log.e(TAG, "API Key failed to configure in your AndroidManifest.xml file,\nPlaces not initialised");
            }
        }
        PlacesClient placesClient = Places.createClient(this);


        //initialise Location request
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10)
                .setWaitForAccurateLocation(false)
                .build();
        //---------------------------------------------------------

        locationCallback = new LocationCallback() {

            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                //save the location
                updateUIValues(locationResult.getLastLocation());
            }
        };


        //check GPS
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_FINE_LOCATION);
        } else {
            // Permission is granted, start location updates
            StartLocationUpdates();
            //updateGPS();
        }


        // Initialize the AutocompleteSupportFragment.
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);
        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG));
        autocompleteFragment.setCountry("SG");
        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {

            @Override
            public void onPlaceSelected(@NonNull Place place) {
                curPlace = place;
                place_ID = place.getId();

                myMap.clear();
                nearestCarparks.clear();
                markers.clear();
                //Log.i("photo", "Place: " + place.getName() + ", " + place.getId());
                findCarpark(place);
                //test debug
                //Toast.makeText(getApplicationContext(), "" + lati + ',' + longi, Toast.LENGTH_LONG).show();

            }

            @Override
            public void onError(@NonNull Status status) {
                // TODO: Handle the error.
                Log.i(TAG, "An error occurred: " + status);

            }
        });


        //initialise map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Button button2 = (Button) findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LatLng new1 = new LatLng(lati, longi);
                myMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new1, 15f)); // Zoom to the current location
            }
        });

        //============================ Buttons ==========================================
        //filter button
        filterButton = findViewById(R.id.filterButton);
        filterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Initialize a LayoutInflater and inflate the custom filter_options layout
                LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                View filterOptionsView = inflater.inflate(R.layout.filter_options, null);
                // Find radio buttons and confirm button in the custom layout
                RadioButton radioOptionCar = filterOptionsView.findViewById(R.id.radioOptionCar);
                RadioButton radioOptionMotorbike = filterOptionsView.findViewById(R.id.radioOptionMotorbike);
                RadioButton radioOptionLorry = filterOptionsView.findViewById(R.id.radioOptionLorry);
                Button confirmButton = filterOptionsView.findViewById(R.id.confirmButton);


                // Create a PopupWindow and set its width and height
                popupWindow = new PopupWindow(filterOptionsView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);

                // Handle radio button selections
                radioOptionCar.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Handle radio option 1
                        radioOptionMotorbike.setChecked(false); // Uncheck other options if needed
                        radioOptionLorry.setChecked(false);
                    }
                });

                radioOptionMotorbike.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Handle radio option 2
                        radioOptionCar.setChecked(false); // Uncheck other options if needed
                        radioOptionLorry.setChecked(false);
                    }
                });

                radioOptionLorry.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Handle radio option 2
                        radioOptionCar.setChecked(false); // Uncheck other options if needed
                        radioOptionMotorbike.setChecked(false);
                    }
                });

                // Handle confirmation
                confirmButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (radioOptionCar.isChecked()) {
                            // Handle radio option 1 selection
                            filterResult = 'c';
                            filterButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.filter_car, 0, 0, 0);
                        } else if (radioOptionMotorbike.isChecked()) {
                            // Handle radio option 2 selection
                            filterResult = 'm';
                            filterButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.filter_bike, 0, 0, 0);
                        } else if (radioOptionLorry.isChecked()) {
                            // Handle radio option 3 selection
                            filterResult = 'h';
                            filterButton.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.filter_lorry, 0, 0, 0);
                        } else {
                            // No option selected
                            showToast("Select a filter option!");
                        }

                        if(curPlace!=null){
                            myMap.clear();
                            nearestCarparks.clear();
                            markers.clear();
                            findCarpark(curPlace);
                        }
                        else{
                            showToast("Enter a location first!");
                        }

                        // Dismiss the PopupWindow
                        popupWindow.dismiss();
                    }
                });

                // Show the PopupWindow at the center of the screen
                popupWindow.showAsDropDown(v);
            }
        });

    }//end of oncreate

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


    // Fragments -----------------------------------------------------------------------------
    FirstFragment firstFragment = new FirstFragment();

    DatabaseFragment databaseFragment = new DatabaseFragment();
    SearchHistoryFragment searchHistoryFragment = new SearchHistoryFragment();
    MostSearchedCarparksFragment mostFragment = new MostSearchedCarparksFragment();

    @Override
    public boolean
    onNavigationItemSelected(@NonNull MenuItem item)
    {

        if (item.getItemId() == R.id.home){
//            final androidx.fragment.app.FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
//            fragmentTransaction.add(R.id.flFragment, firstFragment);
//            fragmentTransaction.addToBackStack("home");
//            fragmentTransaction.commit();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, firstFragment)
                    .commit();
            return true;
        }
        else if (item.getItemId() == R.id.person){
//            final androidx.fragment.app.FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
//            fragmentTransaction.add(R.id.flFragment, databaseFragment);
//            fragmentTransaction.addToBackStack("database");
//            fragmentTransaction.commit();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, databaseFragment)
                    .addToBackStack(null)
                    .commit();
            return true;
        }
        else if (item.getItemId() == R.id.settings){
//            final androidx.fragment.app.FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
//            fragmentTransaction.add(R.id.flFragment, searchHistoryFragment);
//            fragmentTransaction.addToBackStack("history");
//            fragmentTransaction.commit();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, searchHistoryFragment)
                    .addToBackStack(null)
                    .commit();
            return true;
        }
        else if (item.getItemId() == R.id.mostViewed) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.flFragment, mostFragment)
                    .addToBackStack(null)
                    .commit();
            return true;
        }
        return false;
    }

    public static double distance(double lat1,
                                  double lat2, double lon1,
                                  double lon2)
    {

        // The math module contains a function
        // named toRadians which converts from
        // degrees to radians.
        lon1 = Math.toRadians(lon1);
        lon2 = Math.toRadians(lon2);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        // Haversine formula
        double dlon = lon2 - lon1;
        double dlat = lat2 - lat1;
        double a = Math.pow(Math.sin(dlat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.pow(Math.sin(dlon / 2),2);

        double c = 2 * Math.asin(Math.sqrt(a));

        // Radius of earth in kilometers. Use 3956
        // for miles
        double r = 6371;

        // calculate the result
        return(c * r);
    }
    public LatLng getLocationFromAddress(String strAddress) {

        Geocoder coder = new Geocoder(this);
        List<Address> address;
        LatLng p1 = null;

        try {
            // May throw an IOException
            address = coder.getFromLocationName(strAddress, 5);
            if (address == null) {
                return null;
            }

            Address location = address.get(0);
            p1 = new LatLng(location.getLatitude(), location.getLongitude() );

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return p1;
    }
    public SearchHistory getSearchHistoryHelper() {
        return search;
    }
    @Override
    public SearchHistory getSearchHistory() {
        return search;
    }
    public DatabaseHelper getDatabase() {
        // Return your database instance here
        return dbHelper;
    }
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        myMap = googleMap;
        UiSettings settings1 = myMap.getUiSettings();
        settings1.setZoomControlsEnabled(true);
        myMap.setOnMarkerClickListener(this);
    }

    //when carpark marker is clicked
    @Override
    public boolean onMarkerClick(Marker m) {
        for (int i = 0; i < markers.size(); i++) {
            if (m.equals(markers.get(i))) {
                //handle click here
                androidx.constraintlayout.widget.ConstraintLayout parentLayout = findViewById(R.id.parent_layout);
//                parentLayout.setOnTouchListener(new View.OnTouchListener() {
//                    @Override
//                    public boolean onTouch(View v, MotionEvent event) {
//                        // Consume the event, preventing it from reaching the widgets behind
//                        return true;
//                    }
//                });
                LatLng ll = m.getPosition();
                markerLat = ll.latitude;
                markerLong = ll.longitude;
                Bundle carparkInfoBundle = new Bundle();
                carparkInfoBundle.putChar("v", filterResult);
                carparkInfoBundle.putDouble("latitude", ll.latitude);
                carparkInfoBundle.putDouble("longitude", ll.longitude);
                final androidx.fragment.app.FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                carparkInfoFragment.setArguments(carparkInfoBundle);
                fragmentTransaction.replace(R.id.ffframe, carparkInfoFragment);
                fragmentTransaction.addToBackStack("info");
                fragmentTransaction.commit();
                break;
            }
        }
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSIONS_FINE_LOCATION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    updateGPS();
                } else {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage("This app requires permission to be granted in order to work. Change permissions in settings.")
                            .setPositiveButton("Settings", new DialogInterface.OnClickListener() {
                                @RequiresApi(api = Build.VERSION_CODES.N)
                                public void onClick(DialogInterface dialog, int id) {
                                    finish();
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                                    intent.setData(uri);
                                    startActivity(intent);
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // User cancels the dialog.
                                    finish();
                                }
                            })
                            .setCancelable(false);

                    AlertDialog alertDialog = builder.create();// Create the Alert dialog
                    alertDialog.show();// Show the Alert Dialog box

                    //Toast.makeText(this, "This app requires Permissions to be granted to work properly ", Toast.LENGTH_SHORT).show();
                }
        }
    }

    private void updateGPS() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MainActivity.this);

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            fusedLocationProviderClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    updateUIValues(location);
                }
            });
        } else {
            //if permission is not granted yet
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_FINE_LOCATION);
            }
        }
        ;
    }

    ;

    private void updateUIValues(Location location) {

        if (location != null) {
            lati = location.getLatitude();
            longi = location.getLongitude();
        }

    }


//    private void StartLocationUpdates () {
//
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            return;
//        }
//        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
//    }

    private void StartLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    super.onLocationResult(locationResult);
                    if (locationResult.getLastLocation() != null) {
                        // Update map marker with new location
                        lati = locationResult.getLastLocation().getLatitude();
                        longi = locationResult.getLastLocation().getLongitude();
                        LatLng latLng = new LatLng(lati, longi);

//                        myMap.clear(); // Clear previous markers
                        myMap.addMarker(new MarkerOptions().position(latLng).title("Current Location"));
                        if (count == 1){
                            myMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f)); // Zoom to the current location
                            count++;
                        }

                    }
                }
            }, null);
        }
    }

    public void findCarpark(Place place){
        search.insertLocation(place.getName());
        //set text
        LatLng destinationLatLng = place.getLatLng();

        //Log.d("coor", "checkpoint 1 reached");
        myMap.clear();
        nearestCarparks.clear();
        markers.clear();
        myMap.moveCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 16f));
        myMap.addMarker(new MarkerOptions().position(destinationLatLng).title("Destination").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ROSE)));
        CarparkLatLng cll = (CarparkLatLng) new CarparkLatLng(filterResult, new CarparkLatLng.AsyncResponse(){
            @Override
            public void processFinish(ResultSet rs) {
                //data processing code will be written here
                //Log.d("coor", "checkpoint 2 reached: " + address);
                try {
                    //Log.d("coor", "checkpoint 3 reached");
                    while (rs.next()) {
                        //Log.d("coor", "checkpoint 4 reached");
                        double lat = rs.getDouble("latitude");
                        double lng = rs.getDouble("longitude");
                        //Log.d("coor", "lat:" + lat + "long:" + lng);
                        double dist = distance(destinationLatLng.latitude, lat, destinationLatLng.longitude, lng);
                        double positiveDist = Math.abs(dist);
                        //Log.d("coor", "distance: " + dist);
                        if (positiveDist < 1){
                            LatLng newCarpark = new LatLng(lat, lng);
                            nearestCarparks.add(newCarpark);
                            Log.d("coor", "checkpoint 5 reached");
                        }
                                /*
                                for (int a=0;a<5;a++){
                                    if (positiveDist < distances[a]){
                                        if (a == 4){
                                            LatLng newCarpark = new LatLng(lat, lng);
                                            carparksCoords[a] = newCarpark;
                                            distances[a] = positiveDist;
                                        }
                                        else{
                                            for (int b=4;b>=a+1;b--){
                                                carparksCoords[b] = carparksCoords[b-1];
                                                distances[b] = distances[b-1];
                                            }
                                            LatLng newCarpark = new LatLng(lat,lng);
                                            carparksCoords[a] = newCarpark;
                                            distances[a] = positiveDist;
                                        }

                                    }
                                }
                                */
                    }
                    if (!nearestCarparks.isEmpty()) {
                        for (int a = 0; a < nearestCarparks.size(); a++) {
                            String carparkNumber = String.format("Carpark %d", a + 1);
                            Marker m = myMap.addMarker(new MarkerOptions()
                                    .position(nearestCarparks.get(a))
                                    .title(carparkNumber)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
                            markers.add(m);
                            Log.d("coor", "checkpoint 6 reached");
                        }
                    }
                    else{
                        Toast.makeText(getApplicationContext(), "No carparks within 1km.", Toast.LENGTH_SHORT).show();
                    }
                    Log.d("coor", "checkpoint 7 reached");
                }
                catch (SQLException e) {
                    Log.e("InfoAsyncTask", "Error reading information", e);
                }
            }
        }).execute();
    }

    public void setCurPlace (Place place){

        curPlace = place;
        myMap.clear();
    }

    public void mainonNavigateButtonClick(double destlat, double destlong) {

        myMap.clear();
        LatLng origin = new LatLng(lati, longi);
        LatLng dest = new LatLng(destlat, destlong);


        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(origin);
        builder.include(dest);
        LatLngBounds bounds = builder.build();
        int padding = 50;
        CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds,padding);


        // Getting URL to the Google Directions API
        DirectionsHandler directionsHandler = new DirectionsHandler();
        String url = directionsHandler.getDirectionsUrl(origin, dest);
        DownloadTask downloadTask = new DownloadTask();
        downloadTask.execute(url);

        //display = "Distance: " + distancebtwnmarker + ",Duration: "+ durationbtwnmarker;
        Marker destination = myMap.addMarker(
                new MarkerOptions()
                .position(dest)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
        if (destination.isVisible()) {
            destination.showInfoWindow();
        }
//        LatLng ori = new LatLng(lati, longi);
//        Marker originn = myMap.addMarker(
//                new MarkerOptions()
//                        .position(ori)
//                        .title("Distance to carpark: " + distancebtwnmarker)
//                        .snippet("Duration to carpark: "+ durationbtwnmarker)
//                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
//        if (originn.isVisible()) {
//            originn.showInfoWindow();
//        }


        myMap.moveCamera(cu);

    }

    public class DownloadTask extends AsyncTask<String, Void, String> {
        @Override
        public String doInBackground(String... url) {

            // For storing data from web service
            String data = "";
            try{
                DirectionsHandler directionsHandler = new DirectionsHandler();
                data = directionsHandler.downloadUrl(url[0]);
            }catch(Exception e){
                Log.d("Background Task",e.toString());
            }
            return data;
        }

        // Executes in UI thread, after the execution of
        // doInBackground()
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            ParserTask parserTask = new ParserTask();
            parserTask.execute(result);
        }
    }

    /** A class to parse the Google Places in JSON format */
    public class ParserTask extends AsyncTask<String, Integer, List<List<HashMap<String,String>>> >{
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(String... jsonData) {
            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;
            try{
                jObject = new JSONObject(jsonData[0]);
                DirectionsJSONParser parser = new DirectionsJSONParser();
                routes = parser.parse(jObject);
            }catch(Exception e){
                e.printStackTrace();
            }
            return routes;
        }

        // Executes in UI thread, after the parsing process
        @Override
        public void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList<LatLng> points = null;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();

            if(result.size()<1){
                Toast.makeText(getBaseContext(), "No Route", Toast.LENGTH_SHORT).show();
                return;
            }
            // Traversing through all the routes
            for(int i=0;i<result.size();i++) {
                points = new ArrayList<LatLng>();
                lineOptions = new PolylineOptions();
                List<HashMap<String, String>> path = result.get(i);
                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);
                    if (j == 0) {    // Get distance from the list
                        distancebtwnmarker = (String) point.get("distance");
                        continue;
                    } else if (j == 1) { // Get duration from the list
                        durationbtwnmarker = (String) point.get("duration");
                        continue;
                    }
                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);
                    points.add(position);
                }
                // Adding all the points in the route to LineOptions
                lineOptions.addAll(points);
                lineOptions.width(4);
                lineOptions.color(Color.RED);
            }

            Toast.makeText(getBaseContext(), "Distance:"+distancebtwnmarker + ", Duration:"+durationbtwnmarker, Toast.LENGTH_LONG).show();
            //tvDistanceDuration.setText("Distance:"+distance + ", Duration:"+duration);

            myMap.addPolyline(lineOptions);
            LatLng ori = new LatLng(lati, longi);
            Marker originn = myMap.addMarker(
                    new MarkerOptions()
                            .position(ori)
                            .title(distancebtwnmarker)
                            .snippet(durationbtwnmarker)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            if (originn.isVisible()) {
                originn.showInfoWindow();
            }

        }
    }

}

