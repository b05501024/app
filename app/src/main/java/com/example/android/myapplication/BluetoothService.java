package com.example.android.myapplication;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.Vector;

import static android.content.ContentValues.TAG;

/**
 * Created by 黃小維 on 2017/7/14.
 */

public class BluetoothService  {
    // Unique UUID for this application

    //00002A00-0000-1000-8000-00805F9B34FB
    private static final UUID MY_UUID_SECURE1 = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // Member fields
    private final BluetoothAdapter mAdapter;
    private final Handler mHandler;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private boolean first_Flag = true;


    private Context mContext;

    private OutputStream mmOutStream;

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



    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    Temperature mTemperature=new Temperature();
    private Vector<Byte> originalData = new Vector<Byte>();
    private AnalyseDataThread analyseDataThread;

    public BluetoothService(Context context, Handler handler) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mHandler = handler;

        mContext = context;


    }





    private synchronized void setState(int state) {
        Log.d(TAG, "setState() " + mState + " -> " + state);
        mState = state;
    }
    public synchronized int getState() {
        return mState;
    }
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }


        setState(STATE_NONE);
    }

    public synchronized void  connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to: " + device.getAddress());

        // Cancel any thread attempting to make a connection
        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();

        setState(STATE_CONNECTING);
    }
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        Log.d(TAG,"here1");
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();


        // Send the name of the connected device back to the UI Activity

        Message msg = mHandler.obtainMessage(MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);

        if (analyseDataThread != null) {
            try {
                analyseDataThread.stop();
            } catch (Exception e) {
            }
            analyseDataThread = null;
        }
        analyseDataThread = new AnalyseDataThread();
        analyseDataThread.start();


    }
    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        @SuppressLint("NewApi")
        public ConnectThread(BluetoothDevice device, boolean secure) {
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {


                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE1);


                } else {
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_SECURE1);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + mSocketType + "create() failed", e);

            }
            mmSocket = tmp;



        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            setName("ConnectThread" + mSocketType);


            // Always cancel discovery because it will slow down a connection
            mAdapter.cancelDiscovery();


            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect();
                Log.d(TAG, "CO:" +mmSocket);


            } catch (IOException e) {
                // Close the socket
                Log.d(TAG, "COoooo:" +e.toString());
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + mSocketType + " socket during connection failure", e2);
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                mConnectThread = null;
            }

            // Start the connected thread
            connected(mmSocket, mmDevice, mSocketType);

        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }
    }
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final DataInputStream dinput;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            dinput = new DataInputStream(mmInStream);
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            int bufferSize = 8;
            byte[] buffer = new byte[bufferSize];



            first_Flag = true;

            // Keep listening to the InputStream while connected
            while (true) {

                try {
                    // Read from the InputStream
                    dinput.readFully(buffer, 0, buffer.length); //read 8 bytes




                    synchronized (originalData) {
                        for (int i = 0; i < buffer.length; i++)
                            originalData.add((byte) (buffer[i] & 0xFE));

                    }

                } catch (IOException e) {
                    connectionLost();
                    // Start the service over to restart listening mode
                    BluetoothService.this.start();
                    break;
                }
            }

        }

        public void write(byte[] buffer) {
            try {
                DataOutputStream dos = new DataOutputStream(mmOutStream);
                if (buffer.length > 0) {
                    dos.write(buffer, 0, buffer.length);
                }
                dos.flush();

                // Share the sent message back to the UI Activity
                mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
    class AnalyseDataThread extends Thread {
        @Override
        public void run() {
            super.run();
            try {
                while (mState == STATE_CONNECTED) {
                    synchronized (this) {
                        if (originalData.size() >= 8)
                            test();
                        else
                            Thread.sleep(50L);
                    }
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
    }
    private  void test(){
        originalData.remove(0);
        originalData.remove(0);
        originalData.remove(0);
        originalData.remove(0);
        int temp = originalData.remove(0).byteValue() & 0xFF;
        originalData.remove(0);
        int tempa = originalData.remove(0).byteValue() & 0xFF;
        originalData.remove(0);
        originalData.remove(0);
        int temperature = temp*100+tempa;
        mTemperature.setTemp(temperature);

    }
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "0");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }
    private void connectionLost() {
        // Send a failure message back to the Activity
        Message msg = mHandler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, "1");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // Start the service over to restart listening mode
        BluetoothService.this.start();
    }
}
