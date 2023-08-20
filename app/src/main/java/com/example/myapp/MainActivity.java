package com.example.myapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private GoogleMap googleMap;
    private MapView mapView;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private Button button;
    private Button stopButton; // 종료 버튼 추가
    private TextView distanceTextView;
    private TextView timeTextView;
    private TextView speedTextView;
    private TextView averageSpeedTextView;

    private Location previousLocation;

    private boolean isTracking = false;
    private boolean isPaused = false;
    private LatLng previousLatLng;
    private double totalDistance = 0;
    private long startTime = 0;
    private long elapsedTime = 0;
    double averageSpeed;

    String speedText;
    double speedValue; // 숫자와 소수점만 남기고 제거

    String speedStr;
    String distanceStr;
    private android.os.Handler handler = new android.os.Handler();
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            elapsedTime = System.currentTimeMillis() - startTime;
            int seconds = (int) (elapsedTime / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            int hours = minutes / 60;
            minutes = minutes % 60;
            String time = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            timeTextView.setText(time);

            handler.postDelayed(this, 1000); // 1초마다 시간 업데이트
        }
    };

    private List<LatLng> pathPoints;
    private Polyline pathPolyline;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Intent intent = getIntent();
        distanceStr = intent.getStringExtra("distance");
        speedStr = intent.getStringExtra("speed");

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        button = findViewById(R.id.button);
        stopButton = findViewById(R.id.stopButton); // 종료 버튼 연결
        distanceTextView = findViewById(R.id.distanceTextView);
        timeTextView = findViewById(R.id.timeTextView);
        speedTextView = findViewById(R.id.speedTextView);
        averageSpeedTextView = findViewById(R.id.averageSpeedTextView);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isTracking) {
                    if (isPaused) {
                        resumeTracking();
                    } else {
                        pauseTracking();
                    }
                } else {
                    startTracking();
                }
            }
        });

        stopButton.setOnClickListener(new View.OnClickListener() { // 종료 버튼 클릭 이벤트 처리
            @Override
            public void onClick(View v) {
                stopTracking();
            }
        });

        pathPoints = new ArrayList<>();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (isTracking && isPaused) {
            resumeTracking();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (isTracking && !isPaused) {
            pauseTracking();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        if (locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
        handler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        enableMyLocation();
        moveCameraToCurrentLocation(); // 현재 위치로 지도 이동
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            currentLocation = location;
                            moveCameraToCurrentLocation(); // 현재 위치로 지도 이동
                        }
                    }
                });
    }


    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            googleMap.setMyLocationEnabled(true);
            startLocationUpdates();
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        if (isTracking && !isPaused) {
                            updatePath(location);
                            updateUI(location);
                        }
                        currentLocation = location;
                    }
                }
            }
        };

        fusedLocationProviderClient.requestLocationUpdates(locationRequest,
                locationCallback,
                null);
    }

    private void moveCameraToCurrentLocation() {
        if (currentLocation != null) {
            LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15));
        }
    }

    private void startTracking() {
        button.setText("정지");
        stopButton.setVisibility(View.VISIBLE); // 종료 버튼 표시
        button.setBackgroundColor(Color.parseColor("#2196F3"));
        button.setTextColor(Color.parseColor("#FFFFFF"));

        isTracking = true;
        startTime = System.currentTimeMillis();
        handler.postDelayed(timerRunnable, 0); // 타이머 시작
        moveCameraToCurrentLocation(); // 현재 위치로 지도 이동
    }

    private void pauseTracking() {
        button.setText("계속하기");
        stopButton.setVisibility(View.VISIBLE); // 종료 버튼 표시
        button.setBackgroundColor(Color.parseColor("#2196F3"));
        button.setTextColor(Color.parseColor("#FFFFFF"));

        isPaused = true;
        handler.removeCallbacks(timerRunnable); // 타이머 중지
    }

    private void resumeTracking() {
        button.setText("일시정지");
        stopButton.setVisibility(View.VISIBLE); // 종료 버튼 표시
        button.setBackgroundColor(Color.parseColor("#2196F3"));
        button.setTextColor(Color.parseColor("#FFFFFF"));

        isPaused = false;
        startTime = System.currentTimeMillis() - elapsedTime;
        handler.postDelayed(timerRunnable, 0); // 타이머 시작
    }
    private Bitmap captureScreen() {
        View rootView = mapView.getRootView();
        rootView.setDrawingCacheEnabled(true);
        Bitmap screenshotBitmap = Bitmap.createBitmap(rootView.getDrawingCache());
        rootView.setDrawingCacheEnabled(false);

        // 압축 수준 설정
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        screenshotBitmap.compress(Bitmap.CompressFormat.PNG, 10, outputStream);
        byte[] bitmapData = outputStream.toByteArray();

        // 압축된 데이터로 다시 비트맵 생성
        Bitmap compressedBitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length);

        return compressedBitmap;
    }

    private String saveBitmapToFile(Bitmap bitmap) {
        // 비트맵을 파일로 저장
        File file = new File(getCacheDir(), "screenshot.png");
        try {
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 80, fos);
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return file.getAbsolutePath();
    }

    private void stopTracking() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("screenshot_path", saveBitmapToFile((captureScreen())));
        startActivity(intent);
        button.setText("시작");
        stopButton.setVisibility(View.GONE); // 종료 버튼 숨기기
        button.setBackgroundColor(Color.parseColor("#2196F3"));
        button.setTextColor(Color.parseColor("#FFFFFF"));

        isTracking = false;
        isPaused = false;
        totalDistance = 0;
        elapsedTime = 0;
        handler.removeCallbacks(timerRunnable); // 타이머 중지

        clearMap();
        updateUI(null);
    }

    private void updatePath(Location location) {
        if (previousLocation == null) {
            previousLocation = location;
            previousLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        } else {
            double distance = location.distanceTo(previousLocation);
            totalDistance += distance;
            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            PolylineOptions polylineOptions = new PolylineOptions()
                    .add(previousLatLng, latLng)
                    .color(Color.parseColor("#FF6200EE"))
                    .width(10);
            pathPolyline = googleMap.addPolyline(polylineOptions);

            previousLocation = location;
            previousLatLng = latLng;
        }
    }

    private void clearMap() {
        if (pathPolyline != null) {
            pathPolyline.remove();
            pathPolyline = null;
        }
        pathPoints.clear();
        previousLocation = null;
        previousLatLng = null;

        googleMap.clear(); // 맵에서 모든 마커와 선을 지웁니다.
    }


    private void updateUI(Location location) {
        if (location != null) {
            DecimalFormat decimalFormat = new DecimalFormat("#.##");
            double speed = location.getSpeed() * 3.6; // m/s to km/h
            averageSpeed = totalDistance / (elapsedTime / 1000.0) * 3.6; // m/s to km/h

            distanceTextView.setText(decimalFormat.format(totalDistance / 1000) + " km");
            speedTextView.setText(decimalFormat.format(speed) + " km/h");
            averageSpeedTextView.setText(decimalFormat.format(averageSpeed) + " km/h");

            speedText = speedTextView.getText().toString();
            speedValue = Double.parseDouble(speedText.replaceAll("[^\\d.]", "")); // 숫자와 소수점만 남기고 제거
            if (speedValue > 40) {
                // 40를 넘으면 "차량 탑승!!!!" 메시지를 Toast로 띄움
                Toast.makeText(MainActivity.this, "!!!!!! 차량 탑승 !!!!!", Toast.LENGTH_LONG).show();
            }
        } else {
            distanceTextView.setText("0 km");
            timeTextView.setText("00:00:00");
            speedTextView.setText("0 km/h");
            averageSpeedTextView.setText("0 km/h");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            }
        }
    }
}