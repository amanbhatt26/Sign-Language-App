package com.example.examplebluetooth;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class TTS extends AppCompatActivity {
    private static final String TAG = "TTS";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    BluetoothDevice device;
    BluetoothSocket mBluetoothSocket;

    // threads for accepting connections
    private HandlerThread connectThread;
    private Handler connectHandler;
    private Handler uiHandler;
    TextToSpeech textToSpeech;
    String prevVal;
    TextView textview;
    private Boolean mute;

    TensorFlowModelRunner modelRunner;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.text_to_speech);
//        Log.d(TAG, "onCreate: TTS activity");
        device = getIntent().getExtras().getParcelable("btDevice");
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if(i!=TextToSpeech.ERROR){
                    textToSpeech.setLanguage(Locale.UK);
                }
            }
        });
        connectThread = new HandlerThread("connectThread");
        connectThread.start();
        connectHandler = new Handler(connectThread.getLooper());
        uiHandler = new Handler(getMainLooper());

        connectHandler.post(new Runnable() {
            @Override
            public void run() {
                connectDevice(device);
            }
        });

        textview = findViewById(R.id.textView);
        mute = false;

        Button muteBtn = findViewById(R.id.mute);
        muteBtn.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                mute = !mute;
                if(!mute){
                    textToSpeech.speak("", TextToSpeech.QUEUE_FLUSH,null );
                }
            }
        });

        setupModelRunner();

    }

    private void clearModel(){
        modelRunner = null;
    }

    private void setupModelRunner(){

        try {
            int inputSize = 4; // Adjust to the actual input size
            int outputSize = 8; // Adjust to the actual output size
            modelRunner = new TensorFlowModelRunner(getAssets(),"model.tflite" , inputSize, outputSize);
        } catch (IOException e) {
            // Handle the exception
            System.out.println("Cannot find the model.tflite file");
            return;
        }
    }

    private void connectDevice(BluetoothDevice device) {


        BluetoothSocket tmp = null;
        try {
            // Get a BluetoothSocket to connect with the given BluetoothDevice.
            // MY_UUID is the app's UUID string, also used in the server code.
            tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            tmp.connect();
        } catch (IOException e) {
//            Log.e(TAG, "Socket's create() method failed", e);
        }catch(SecurityException se){
//            Log.d(TAG, "ConnectThread: Cannot connect to device");
        }
        mBluetoothSocket = tmp;
        if(mBluetoothSocket !=null && mBluetoothSocket.isConnected()){
//            Log.d(TAG, "connectDevice: " + mBluetoothSocket);
            connectHandler.post(new Runnable() {
                @Override
                public void run() {
                    listenForMessages(mBluetoothSocket);
                }
            });
        }else{
            this.finish();
        }
    }

    private void listenForMessages(BluetoothSocket socket) {
        // todo display the input messages on the logcat
        String result = "";
        Boolean mListening = true;
        int bufferSize = 100;
        int delay = 200;
        byte[] buffer = new byte[bufferSize];
        try {
            InputStream instream = socket.getInputStream();
            long prevTime = new Date().getTime();
            while(true){
                int bytesRead = -1;
                bytesRead = instream.read(buffer);
                long curTime = new Date().getTime();
                if(curTime < prevTime + delay){
                    continue;
                }
                prevTime = curTime;
                if(bytesRead > 16){
//                    Log.d(TAG, "listenForMessages: " + bytesRead);
                    result = new String(buffer, 0, bytesRead);
//                    Log.d(TAG, "listenForMessages: " + result);
//                    Thread.sleep(delay);

//                    Log.d(TAG, "listenForMessages: " + result);
                    String[] strings = result.split("\\$", -1);
                    for(String string:strings){

                       if(string.length() >=12){
//                           Log.d(TAG, "listenForMessages: " + string);
                           String speech = angleToString(string);
                           System.out.println(string + " " + speech);
                           updateTextView(speech);
                           textToSpeech(speech, mute);
                           break;
                       }
                    }
                }

            }

//            if (bytesRead != -1) {
//                while ((bytesRead == bufferSize) &&
//                        (buffer[bufferSize-1] != 0)) {
//
//                    bytesRead = instream.read(buffer);
//                }
//                result = result + new String(buffer, 0, bytesRead - 1);
//                Log.d(TAG, "listenForMessages: " + result);
//            }
        } catch (Exception e) {
            Log.e(TAG, "Message receive failed.", e);
        }
//return result;
    }
//    {"$090120150180090120150180090120", "$090120150180090120150180090150",
//            "$090120150180090120150180090180", "$090120120180090120150180090180","$090120150180090120150090090180", "$180120150180090120150090090180",
//            "$150120150180090120150090090180"};
    private String angleToString(String string){

        if(modelRunner == null) {
            setupModelRunner();
            System.out.println(modelRunner);

        }

        float[] inputData = new float[4];
        for (int i = 0; i < 4; i++) {
            String subString = string.substring(i * 3, (i * 3) + 3);
            inputData[i] = Float.parseFloat(subString);
        }



// Run inference
        float[] outputData = modelRunner.runInference(inputData);

        int maxIndex = -1;
        float maxVal = Float.MIN_VALUE;

        for(int i=0;i<outputData.length;i++){
            if(outputData[i] > maxVal) {
                maxIndex = i;
                maxVal = outputData[i];
            }
        }

        if(maxIndex == -1) return "--";

        String []labels = {"zero", "one", "two", "three", "four", "five", "yo", "closed"};
        return labels[maxIndex];

//        for(float f:outputData){
//            System.out.print(f + " ");
//        }
//        System.out.println("");
//
//
//        if(string.equals(prevVal)){
//            prevVal = string;
//            return "--";
//        }
//        prevVal = string;
//        switch (string){
//            case "320000000000040000200000320000":
//                return "idle";
//            case "320000000000080000200000320000":
//                return "idle";
//
//            case "320000160000160000320000320000":
//                return "namaste";
//            case "320000160000200000320000320000":
//                return "namaste";
//
//            case "320000160000000000320000400000":
//                return "namaste";
//
//            case "320000160000000000280000320000":
//                return "namaste";
//            case "320000000000160000320000320000":
//                return "one";
//
//            case "320000000000000000240000320000":
//                return "one";
//            case "320000000000160000280000320000":
//                return "one";
//
//            case "320000000000000000280000320000":
//                return "one";
//
//            case "320000000000080000240000320000":
//                return "two";
//
//            case "320000000000080000320000320000":
//                return "two";
//
//            case "320000000000080000280000320000":
//                return "two";
//
//            case "320000120000080000200000320000":
//                return "ok";
//
//            case "320000160000080000200000320000":
//                return "ok";
//
//            case "320000160000080000240000320000":
//                return "ok";
//
//            case "320000160000080000280000320000":
//                return "ok";
//
////            case "090120150180090120150180090150":
////                return "namaste";
////
////            case "090120150180090120150180090180":
////               return "hahaha";
////
////            case "090120120180090120150180090180":
////                return "orange";
////
////            case "090120150180090120150090090180":
////                return "blue";
////
////            case "180120150180090120150090090180":
////                return "sunday";
////
////            case "150120150180090120150090090180":
////                return "monday";
//        }
    }

    private void textToSpeech(String string, Boolean mute){
        if(!mute)
        textToSpeech.speak(string,TextToSpeech.QUEUE_FLUSH,null);
    }
    private void updateTextView(String text) {
        uiHandler.post(new Runnable() {
            @Override
            public void run() {
                if(textview !=null){
                    textview.setText(text);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectThread.quit();
        try {
            mBluetoothSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}