/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.auth.FirebaseAuth;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends AppCompatActivity{
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public final String DATA_KEY = "DATA";

    private TextView mDataField;
    private TextView mDataField2;
    private String mDeviceAddress;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    RequestQueue requestQueue;
    private FirebaseAuth mAuth;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final Handler handler = new Handler();
            final int delay = 5000; //milliseconds
            final Runnable reloadData = new Runnable() {
                @Override
                public void run(){
                    onClickRead();
                }
            };

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                invalidateOptionsMenu();

                handler.postDelayed(reloadData, delay);
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                invalidateOptionsMenu();
                clearUI();
                handler.removeCallbacks(reloadData);
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                addToDB(BluetoothLeService.temp, BluetoothLeService.heartbeat);
                displayData(BluetoothLeService.temp + "°C", BluetoothLeService.heartbeat + "bpm");
            }
        }
    };
    private static final String LAST_ADDED_TIMESTAMP = "lastAdded";

    private void clearUI() {
        mDataField.setText(R.string.no_data);
    }

    SQLiteDatabase db;

    private void addToDB(String temperature, String heartbeat) {
        if (temperature.length() == 4){
            db.execSQL("INSERT INTO Data(Temperature, HeartBeat, added) VALUES(" + temperature + ", " + heartbeat + ", " + System.currentTimeMillis() + ");");
            uploadData();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.button_control);
        mAuth = FirebaseAuth.getInstance();

        final Intent intent = getIntent();
        String mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
        mDataField = findViewById(R.id.data_value);
        mDataField2 = findViewById(R.id.data_value2);

        findViewById(R.id.viewGraph).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(DeviceControlActivity.this, GraphActivity.class));
            }
        });

        ActionBar actionBar = getSupportActionBar();
        assert actionBar != null;
        actionBar.setTitle(mDeviceName);
        actionBar.setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Open Database
        db = openOrCreateDatabase("UnderMyCareData",MODE_PRIVATE,null);
        db.execSQL("CREATE TABLE IF NOT EXISTS Data(Temperature FLOAT, HeartBeat INT, added TIMESTAMP);");

        // Setup Volley
        requestQueue = Volley.newRequestQueue(getApplicationContext()); // 'this' is the Context
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void displayData(String data1, String data2) {
        if (data1!=null) {
            mDataField.setText(data1);
        }
        else{
            Log.e("null", "null");
        }
        if(data2!=null)
            mDataField2.setText(data2);
        else
            Log.e("null", "null2");
    }


    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void onClickRead(){
        if(mBluetoothLeService != null) {
            mBluetoothLeService.readHeartCharacteristic();
//            Handler handler = new Handler();
//            Handler handler = new Handler();
//            handler.postDelayed(new Runnable() {
//                public void run() {
//                    if(mBluetoothLeService != null)
//                        mBluetoothLeService.readTempCharacteristic();
//                }
//            }, 1000);
        }
    }

    private void uploadData() {
        // Select data to be uploaded from DB
        final SharedPreferences sharedPref = getSharedPreferences(
                DATA_KEY, Context.MODE_PRIVATE);
        long lastAdded = sharedPref.getLong(LAST_ADDED_TIMESTAMP, 0);
        Cursor dataToUpload = db.rawQuery("SELECT Temperature, HeartBeat, added FROM Data WHERE added > ?;", new String[] {String.valueOf(lastAdded)});
        dataToUpload.moveToFirst();
        String url = "http://undermycare.com/Backend/insert.php";
        String uuid = mAuth.getCurrentUser().getUid();
        Log.e("UnderMyCareUser", uuid);
        do {
            Float temp = dataToUpload.getFloat(0);
            int heart = dataToUpload.getInt(1);
            Log.d("UnderMyCareSync", "Uploading "+temp+", "+heart);
            StringRequest stringRequest = new StringRequest(Request.Method.GET, url + "?uuid=" + uuid + "&heart="+heart+"&temp="+temp,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.putLong(LAST_ADDED_TIMESTAMP, System.currentTimeMillis());
                            editor.apply();
                            Log.e("reply", response);

                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                        }
                    });
            //add request to queue
            requestQueue.add(stringRequest);

        } while (dataToUpload.moveToNext());
        dataToUpload.close();


   }

}
