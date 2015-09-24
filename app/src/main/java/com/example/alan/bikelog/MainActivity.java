package com.example.alan.bikelog;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

import static android.bluetooth.BluetoothAdapter.*;


public class MainActivity extends ActionBarActivity {

    public enum State {
        INITIAL, RUNNING, PAUSED
    }
    private State mAppState = State.INITIAL;
    private BluetoothDevice mDevice = null;
    private BluetoothAdapter mBtAdapter = null;
    private UartService mService = null;
    private boolean mConnected = false;
    public static final String TAG = "BikeLogLog";
    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;
    private int mState = UART_PROFILE_DISCONNECTED;
    private static final int REQUEST_SELECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private boolean mScanning = false;
    private static Handler mHandler;
    private static final long SCAN_PERIOD = 10000; //10 seconds
    private final int mExpectedRssi = -51;
    private final String mExpectedDev = "";
    private Button btnStartStop;
    private BikeData mBikeData = new BikeData();
    private long mStartTime = 0;
    private final long mUIInterval = 1000; // ms
    private final int   mPollMult = 2; // n x UIInterval to poll and log Watt/GPS
    private int mIntervalCount = 0;
    private TextView mTxtVolts;
    private TextView mTxtElapsed;
    private TextView mTxtCurrent;
    private TextView mTxtTemp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final BluetoothManager bluetoothManager;
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBtAdapter = bluetoothManager.getAdapter();
        if (mBtAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        btnStartStop = (Button)findViewById(R.id.startStopButton);
        btnStartStop.setEnabled(false);
        service_init();
        mHandler = new Handler();

        mHandler.postDelayed((new Runnable() {
            @Override
            public void run() {
                if (!mBtAdapter.isEnabled()) {
                    Log.i(TAG, " BT not enabled yet");
                    Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                    Log.i(TAG, " BT request returned");
                } else {
                    Log.i(TAG, "BT already enabled");
                    tryConnect();
                }
                btnStartStop.setEnabled(true);
            }
        }), 500);

        btnStartStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO Start timer or restart
                switch(mAppState){
                    case INITIAL:
                        if (mStartTime == 0) {
                            mStartTime = SystemClock.elapsedRealtime();
                            mBikeData.setStartTime(mStartTime);
                            mAppState = State.RUNNING;
                            mHandler.post(runGetData);
                            ((Button)v).setText(R.string.pause);
                        }
                        else{
                            showMessage("Initial state but not Time zero") ;
                        }
                        break;
                    case RUNNING:
                        // Goto pause and change label
                        mAppState = State.PAUSED;
                        ((Button)v).setText(R.string.start);
                        break;
                    case PAUSED:
                        mAppState = State.RUNNING;
                        ((Button)v).setText(R.string.pause);
                        mHandler.post(runGetData);
                        break;
                }


                // set a period read/log of data going
//                byte value[] = {(byte) 0xAA, 01, (byte) 0xfe};
////				try {
//                //send data to service
//                mService.writeRXCharacteristic(value);
//                //Update the log with time stamp
////				} catch (UnsupportedEncodingException e) {
//                // TODO Auto-generated catch block
////					e.printStackTrace();
////				}

            }
        });

        mTxtCurrent = (TextView)findViewById(R.id.text_current);
        mTxtVolts = (TextView)findViewById(R.id.text_voltage);
        mTxtElapsed = (TextView)findViewById(R.id.text_elapsed);
        mTxtTemp = (TextView)findViewById(R.id.text_temp);

/*        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                tryConnect();
            }
        },2000);*/
      //  tryConnect();
    }

    private Runnable runGetData = new Runnable() {
        @Override
        public void run() {
            // Update time field
            long nElapsed = SystemClock.elapsedRealtime() - mStartTime;
            TextView v = (TextView)findViewById(R.id.text_elapsed);
            v.setText(DateUtils.formatElapsedTime(nElapsed/1000));

            // Every nth time poll WattMeter and GPS
            if(++mIntervalCount >= mPollMult){
                mIntervalCount = 0;
                // Do the actual poll
                if( mService.getmConnectionState() == STATE_CONNECTED){
                    byte value[] = {(byte) 0xAA, 01, (byte) 0xfe};
                    mService.writeRXCharacteristic(value);
                }
            }
            // Set to run again
            if(mAppState == State.RUNNING)
                mHandler.postDelayed(runGetData,mUIInterval);
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == REQUEST_ENABLE_BT){
            if(mBtAdapter.isEnabled()){
                TextView v = (TextView)findViewById(R.id.text_status);
                v.setText(R.string.ble_enabled);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                tryConnect();
                            }
                        }, 8000);
                    }

                    ;
                });
            }
            else{
                Toast.makeText(this, "Bluetooth scuppered", Toast.LENGTH_LONG).show();
                finish();
            }
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if( mBtAdapter != null) {
            mBtAdapter.disable();
        }
    }
    @Override
    public  void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
    }

    /* Todo
        Need to start the connection process
        What is the opposite of onCreate -> need to close BTLe if enabled and gps
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void tryConnect(){
        Log.i(TAG, "tryConnect");
        if(mBtAdapter != null) {
            if (!mScanning) {
                // Post a delayed try again here
                // Guard against already scanning and found
                TextView v = (TextView)findViewById(R.id.text_status);
                v.setText(R.string.ble_scan);
                scanLeDevice(true);
            }
        }
        else{
            Log.i(TAG, "Adapter null");
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    TextView v = (TextView)findViewById(R.id.text_status);
                    v.setText(R.string.ble_scan_stop);
                    mScanning = false;
                    mBtAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);
            Log.i(TAG, "Scan enabled ");
            mScanning = true;
            mBtAdapter.startLeScan(mLeScanCallback);
        } else {
            Log.i(TAG, "Scan disabled ");
            mScanning = false;
            mBtAdapter.stopLeScan(mLeScanCallback);
        }

    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    testDevice(device,rssi);
                }
            });
        }
    };

    private void testDevice(BluetoothDevice device, int rssi){
        Log.i(TAG, "Test device");
        String s = device.getAddress();
        if (device.getAddress().equals("CD:D1:67:37:99:87")) {
            mScanning = false;
            mBtAdapter.stopLeScan(mLeScanCallback);
            Log.i(TAG, "Device match");
            mDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device.getAddress());
            TextView v = (TextView)findViewById(R.id.text_status);
            v.setText(R.string.ble_uart_found);
            if(mService != null) {
                mService.connect(device.getAddress().toString());
            }
        }
    }

    private void setText(String s){
        TextView v = (TextView)findViewById(R.id.text_status);
        v.setText(s);
    }


    private double Volts(int ADC){
        double dRet = (double)(ADC);
        dRet = dRet * 0.011856 +0.003394;
        return dRet;
    }

    private double Current(int ADC){
        double dRet = (double)ADC * -0.0126781 + 29.2785;
        return dRet;
    }

    private double Temp(int ADC){
        double dRet = (double)ADC / 2048;
        return (dRet -0.5) * 100;
    }

    //UART service connected/disconnected
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder rawBinder) {
            mService = ((UartService.LocalBinder) rawBinder).getService();
            Log.d(TAG, "onServiceConnected mService= " + mService);
            if (!mService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

        }

        public void onServiceDisconnected(ComponentName classname) {
            ////     mService.disconnect(mDevice);
            mService = null;
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(UartService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(UartService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(UartService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(UartService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }

    private final BroadcastReceiver UARTStatusChangeReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            final Intent mIntent = intent;
            //*********************//
            if (action.equals(UartService.ACTION_GATT_CONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_CONNECT_MSG");
/*                        btnConnectDisconnect.setText("Disconnect");
                        edtMessage.setEnabled(true);
                        btnSend.setEnabled(true);
                        ((TextView) findViewById(R.id.deviceName)).setText(mDevice.getName()+ " - ready");
                        listAdapter.add("["+currentDateTimeString+"] Connected to: "+ mDevice.getName());
                        messageListView.smoothScrollToPosition(listAdapter.getCount() - 1);*/
                        mState = UART_PROFILE_CONNECTED;
                    }
                });
            }

            //*********************//
            if (action.equals(UartService.ACTION_GATT_DISCONNECTED)) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                        Log.d(TAG, "UART_DISCONNECT_MSG");
/*                        btnConnectDisconnect.setText("Connect");
                        edtMessage.setEnabled(false);
                        btnSend.setEnabled(false);
                        ((TextView) findViewById(R.id.deviceName)).setText("Not Connected");
                        listAdapter.add("["+currentDateTimeString+"] Disconnected to: "+ mDevice.getName());*/
                        mState = UART_PROFILE_DISCONNECTED;
                        mService.close();
                        //setUiState();

                    }
                });
            }


            //*********************//
            if (action.equals(UartService.ACTION_GATT_SERVICES_DISCOVERED)) {
                Log.d(TAG, "GATT discovered_MSG");
                mService.enableTXNotification();
            }
            //*********************//
            if (action.equals(UartService.ACTION_DATA_AVAILABLE)) {
                final byte[] txValue = intent.getByteArrayExtra(UartService.EXTRA_DATA);
                StringBuilder sb = new StringBuilder();
                if(mBikeData.setData(txValue)) {
                    int val = (txValue[2] & 0xff) + (txValue[3] & 0xff) * 256;
                    sb.append("\r\nRx1 : = ");
                    sb.append(mBikeData.getVolts());
                    sb.append("\r\nRx2 : = ");
                    sb.append(mBikeData.getCurrent());
                    sb.append("\r\nRx3 : = ");
                    sb.append(mBikeData.getTemp1());
                }
                else {
                    sb.append("\rDud\r\n");
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTxtVolts.setText(mBikeData.getVoltsTxt());
                        mTxtCurrent.setText(mBikeData.getCurrentTxt());
                        mTxtTemp.setText(mBikeData.getTemp1Txt());
                    }
                });
                /*final String txt = sb.toString();
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            setText(txt);
                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }
                    }
                });*/
            }
            //*********************//
            if (action.equals(UartService.DEVICE_DOES_NOT_SUPPORT_UART)){
                showMessage("Device doesn't support UART. Disconnecting");
                mService.disconnect();
            }


        }
    };

    private void service_init() {
        Intent bindIntent = new Intent(this, UartService.class);
        if(mServiceConnection == null){
            Log.d(TAG, "No service connection");
        }
        boolean bRet =  bindService(bindIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
        if( !bRet ){
            Log.d(TAG,"Bind no good");
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(UARTStatusChangeReceiver, makeGattUpdateIntentFilter());
    }
    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

    }


}
