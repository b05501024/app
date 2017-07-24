package com.example.android.myapplication;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;


public class MainActivity extends AppCompatActivity {
    // Message types sent from the BluetoothService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_SAVEDB = 6;

    // Key names received from the BluetoothService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    private static final int REQUEST_GET_RECORDEDITOR = 2;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_GET_PATIENT = 0;




    public static BluetoothDevice device ;
    // The Handler that gets information back from the BluetoothService
    private final Handler bt_handler = new Handler() {
        public void handleMessage(Message msg) {
            Log.d("MainActivity","handleMessage msg.what="+msg.what);
            switch (msg.what) {
                case MESSAGE_TOAST:
                    TextView blueToo = (TextView)findViewById(R.id.blue_Tooth);
                switch (msg.arg1) {

                    case BluetoothService.STATE_CONNECTED:

                        blueToo.setText("OK");
                        break;
                    case BluetoothService.STATE_CONNECTING:

                        blueToo.setText("CONNECTING");
                        break;
                    case BluetoothService.STATE_LISTEN:
                    case BluetoothService.STATE_NONE:
                        blueToo.setText("NO!!!");
                        break;
                }
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name

                    String mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(MainActivity.this,  " 連線至" + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
            }

        }


    };
    BluetoothSocket socket;
    int handlerState;
    OutputStream outputStream;
    InputStream inputStream;
 //88:1B:99:06:15:EB
    String mac1="88:1B:99:06:15:EB" ;
    String mac="C6:05:04:03:60:F4" ;
    private BluetoothService mBTService = null;
    private ConnectThread connectThread;
    private boolean startConnectFlag = true;
    private BluetoothAdapter mBluetoothAdapter = null ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);




       mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(MainActivity.this, "bluetooth_is_not_available", Toast.LENGTH_LONG).show();
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else if (mBTService == null) {// Otherwise, setup the chat session

            setup();
        }

    }

    private void setup() {

        // Initialize the BluetoothService to perform bluetooth connections
        mBTService = new BluetoothService(MainActivity.this , bt_handler);
        startConnectFlag = true;
        connectThread = new ConnectThread();
        connectThread.start();
    }

    class ConnectThread extends Thread{

        public void run() {
            super.run();
            while (startConnectFlag) {

                if (mBTService.getState() != 3) {
                    device = mBluetoothAdapter.getRemoteDevice(mac);
                    mBTService.connect(device,true);


                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }



}

