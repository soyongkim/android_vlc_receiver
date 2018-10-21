/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */

package com.hoho.android.usbserial.examples;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.widget.VideoView;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.GeneratedMessages;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.hoho.android.usbserial.util.UVLinkMessage;
import com.hoho.android.usbserial.util.UVLinkPacket;
import com.hoho.android.usbserial.util.ApplicationEntityObject;
import com.hoho.android.usbserial.util.*;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Monitors a single {@link UsbSerialPort} instance, showing all data
 * received.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class SerialConsoleActivity extends Activity {

    private final String TAG = SerialConsoleActivity.class.getSimpleName();
    /**
     * Driver instance, passed in statically via
     * {@link #show(Context, UsbSerialPort)}.
     *
     * <p/>
     * This is a devious hack; it'd be cleaner to re-create the driver using
     * arguments passed in with the {@link #startActivity(Intent)} intent. We
     * can get away with it because both activities will run in the same
     * process, and this is a simple demo.
     */
    private static UsbSerialPort sPort = null;

    private TextView mTitleTextView;
    private TextView mDumpTextView;
    private ScrollView mScrollView;

    public ToggleButton btnAddr_Set;
    public Handler handler;

    private Button btn_SndMesg;
    public ImageView consoleImage;
    public VideoView consoleVideo;
    public Spinner type_spinner;
    MediaPlayer mplyaer;

    public TextView rcv_Id;
    public TextView rcv_Type;

    int count = 1;
    int gid=1;
    int gtype=0;
    int first_received = 0;

    public int vtidstate = 1;
    public int curId = 0;
    public int curType=0;
    public int output_mode = 1;

    private static CSEBase csebase = new CSEBase();
    private EditText EditText_Address = null;
    private EditText edit_Id = null;
    private String Mobius_Address ="";

    public Timer timer;
    public TimerTask task = null;

    /* Response callback Interface */
    public interface IReceived {
        void getResponseBody(String msg);
    }

    private final ExecutorService mExecutor = Executors.newCachedThreadPool();

    private SerialInputOutputManager mSerialIoManager;

    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {

        }


        @Override
        public void onNewData(final byte[] data) {
                SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                        if(task != null) {
                            task.cancel();
                            timer.purge();
                        }

                        gid = byte2intId(data);
                        gtype = byte2intType(data);
                        //gtype = 0;
                        rcv_Id.setText(String.valueOf(gid));
                        switch (gtype) {
                            case 0:
                                rcv_Type.setText("TEXT");
                                break;
                            case 1:
                                rcv_Type.setText("IMAGE");
                                break;
                            case 2:
                                rcv_Type.setText("VIDEO");
                                break;
                            default:
                                rcv_Type.setText("Not Processing Type");
                        }

                        //Toast.makeText(getApplicationContext(), "gtype:" + gtype, Toast.LENGTH_SHORT).show();

                        if(first_received == 0) {
                            //mExecutor.submit(new count_video());
                            curId = gid;
                            first_received = 1;
                            //playVideo(gid);
                        }
                        enableOutput_mode();
                        //updateReceivedData(data);
                        processData();
                        //updateReceivedData(data);

                        task = new count_video_task();
                        timer.schedule(task, 1000);
                    }
                });
            }

    };

    public static int byte2intId(byte[] src) {
        int s1 = 0 & 0xFF;
        int s2 = 0 & 0xFF;
        int s3 = src[2] & 0xFF;
        int s4 = src[3] & 0xFF;

        return ((s1 << 24) + (s2 << 16) + (s3 << 8) + (s4 << 0));
    }

    public static int byte2intType(byte[] src) {
        int s1 = 0 & 0xFF;
        int s2 = 0 & 0xFF;
        int s3 = src[0] & 0xFF;
        int s4 = src[1] & 0xFF;

        return ((s1 << 24) + (s2 << 16) + (s3 << 8) + (s4 << 0));
    }

    public class count_video_task extends TimerTask {
        @Override
        public void run() {
                    (SerialConsoleActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if(mplyaer != null)
                                        mplyaer.pause();
                                } catch (Exception e) {
                                    Toast.makeText(getApplicationContext(), "Time out!", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_console);
        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        mScrollView = (ScrollView) findViewById(R.id.demoScroller);
        btn_SndMesg = (Button) findViewById(R.id.btn_SndMesg);
        btnAddr_Set = (ToggleButton) findViewById(R.id.toggleButton_Addr);
        EditText_Address = (EditText) findViewById(R.id.editText);
        edit_Id = (EditText) findViewById(R.id.edit_Id);
        consoleImage = (ImageView)findViewById(R.id.consoleImage);
        consoleVideo = (VideoView) findViewById(R.id.consoleVideo);
        type_spinner = (Spinner)findViewById(R.id.spinner_Type);
        rcv_Id = (TextView)findViewById(R.id.text_RcvId);
        rcv_Type = (TextView)findViewById(R.id.text_RcvType);

        ArrayAdapter adapter = ArrayAdapter.createFromResource(this, R.array.typelist, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
        type_spinner.setAdapter(adapter);

        timer = new Timer();

        consoleVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mplyaer = mp;
                //Toast.makeText(getApplicationContext(), "Ready", Toast.LENGTH_SHORT).show();
            }
        });

        //imageView = (ImageView)findViewById(R.id.image);
        btnAddr_Set.setFocusable(true);
        btnAddr_Set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (((ToggleButton) view).isChecked()) {
                    Toast.makeText(getApplicationContext(), "Start Connecting", Toast.LENGTH_SHORT).show();
                    Mobius_Address = EditText_Address.getText().toString();
                    csebase.setInfo(Mobius_Address,"7579","Mobius","1883");

                }
            }
        });

        btn_SndMesg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(edit_Id.getText().toString().equals("")) {
                    Toast.makeText(getApplicationContext(), "Please Insert Number from 1 to 30", Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        vtidstate = Integer.parseInt(edit_Id.getText().toString());
                        output_mode = type_spinner.getSelectedItemPosition();
                        sendMessage(vtidstate, output_mode);
                        Toast.makeText(getApplicationContext(), "Sent Message", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "Sending Error:"+e, Toast.LENGTH_LONG).show();
                    }
                }
                //playVideo(vtidstate);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            sPort = null;
        }
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            mTitleTextView.setText("No serial device.");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                mTitleTextView.setText("Opening device failed");
                return;
            }

            try {
                sPort.open(connection);
                sPort.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                mTitleTextView.setText("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
            //mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());
        }
        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);

        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    private void updateReceivedData(byte[] data) {
        final String message = "Read : " + data.length + " bytes :\n" + HexDump.toHexString(data)+ "\n";
        //Toast.makeText(getApplicationContext(), "Data:" + message, Toast.LENGTH_LONG).show();
        mDumpTextView.setText(message);
        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context
     * @param
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, SerialConsoleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

    public void enableOutput_mode() {
        switch (gtype) {
            case 0:
                mScrollView.setVisibility(View.VISIBLE);
                consoleVideo.setVisibility(View.INVISIBLE);
                consoleImage.setVisibility(View.INVISIBLE);
                break;
            case 1:
                mScrollView.setVisibility(View.INVISIBLE);
                consoleVideo.setVisibility(View.INVISIBLE);
                consoleImage.setVisibility(View.VISIBLE);
                break;
            case 2:
                mScrollView.setVisibility(View.INVISIBLE);
                consoleVideo.setVisibility(View.VISIBLE);
                consoleImage.setVisibility(View.INVISIBLE);
                break;
            default:
                mScrollView.setVisibility(View.VISIBLE);
                consoleVideo.setVisibility(View.INVISIBLE);
                consoleImage.setVisibility(View.INVISIBLE);
                gtype = 0;
                break;
        }
    }

    public void sendMessage(int id, int type) {
        try {
            String hex_id;
            if (id < 16)
                hex_id = "000" + Integer.toHexString(id);
            else
                hex_id = "00" + Integer.toHexString(id);

            String hex_type = Integer.toHexString(type);

            String data = "{\"type\":\"000" + hex_type + "\",\"id\":\"" + hex_id + "\",\"data\":\"0102030405060708090a0b0c0d0f\"}";

            RequestVT req = new RequestVT(data);
            req.setReceiver(new IReceived() {
                public void getResponseBody(final String msg) {
                    handler.post(new Runnable() {
                        public void run() {

                        }
                    });
                }
            });
            req.start();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(),"Send Error:" + e, Toast.LENGTH_SHORT).show();
        }
    }

    public void processData() {
        //초기상태에서 처음으로 패킷을 받을 경우
        //Toast.makeText(getApplicationContext(), "gid:" + gid + " cur_id:" + curId + " gtype:" + gtype + "cur_type:" + curType, Toast.LENGTH_SHORT).show();
            // 다른 id의 패킷을 받을 경우
        if(curId != gid  || curType != gtype) {
            curId = gid;
            curType = gtype;
            switch (gtype) {
                case 0:
                    readText(gid);
                    //텍스트 실행
                    break;
                case 1:
                    viewImage(gid);
                    //이미지 실행
                    break;
                case 2:
                    //동영상 실행
                    playVideo(gid);
                    break;
            }
        }
        else {
                switch (gtype) {
                    case 0:
                        //텍스트 실행
                        readText(gid);
                        break;
                    case 1:
                        //이미지 실행
                        viewImage(gid);
                        break;
                    case 2:
                        try {
                            count = 1;
                            if(mplyaer != null)
                                mplyaer.start();
                        } catch (Exception e) {
                            //Toast.makeText(getApplicationContext(), "e3:" + e, Toast.LENGTH_SHORT).show();
                            //playVideo(rid);
                        }
                        break;
                }
        }
    }

    public void viewImage(int id) {
        try {
            //Uri path = Uri.parse("android.resource://com.hoho.android.usbserial.examples/raw/image_vt" + id);
            Uri path = Uri.parse(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download" + "/VLC/image_vt" + id + ".jpg");
            //Uri path = Uri.parse(Environment.DIRECTORY_DOWNLOADS + "/image_vt2");
            //Toast.makeText(getApplicationContext(),"Path:" + path,  Toast.LENGTH_SHORT).show();
            consoleImage.setImageURI(path);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Image Error:"+ e, Toast.LENGTH_SHORT).show();
        }
    }

    public void readText(int id) {
        try{
            //Uri path = Uri.parse("android.resource://com.hoho.android.usbserial.examples/raw/text_vt" + id);
            //Uri path = Uri.parse(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download" + "/VLC/text_vt" + id);
            String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download" + "/VLC/text_vt" + id + ".txt";
            //InputStream in = getContentResolver().openInputStream(path);
            InputStream in = new FileInputStream(path);
            if(in != null){

                InputStreamReader stream = new InputStreamReader(in, "utf-8");
                BufferedReader buffer = new BufferedReader(stream);

                String read;
                StringBuilder sb = new StringBuilder("");

                while((read=buffer.readLine())!=null){
                    sb.append(read);
                }

                in.close();

                mDumpTextView.setText(sb.toString());
                mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
            }

        }catch(Exception e){
            Toast.makeText(getApplicationContext(), "Text Error:" + e , Toast.LENGTH_SHORT).show();
        }
    }

    public void playVideo(int id) {
        try {
            //Toast.makeText(getApplicationContext(), "ID:"+ id , Toast.LENGTH_LONG).show();
            //Uri path = Uri.parse("android.resource://com.hoho.android.usbserial.examples/raw/video_vt" + id);
            //Uri path = Uri.parse(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download" + "/VLC/video_vt" + id + ".mp4");
            String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download" + "/VLC/video_vt" + id + ".mp4";
            consoleVideo.setVideoPath(path);
            consoleVideo.requestFocus();
            consoleVideo.start();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Video Error:" + e , Toast.LENGTH_SHORT).show();
        }
    }

    /* Request VT ID change message */
    class RequestVT extends Thread {
        private final Logger LOG = Logger.getLogger(RequestVT.class.getName());
        private IReceived receiver;
        private String container_name = "vt";

        public ContentInstanceObject contentinstance;
        public RequestVT(String comm) {
            contentinstance = new ContentInstanceObject();
            contentinstance.setContent(comm);
        }
        public void setReceiver(IReceived hanlder) { this.receiver = hanlder; }

        @Override
        public void run() {
            try {
                String sb = csebase.getServiceUrl() + "/" +container_name;

                URL mUrl = new URL(sb);

                HttpURLConnection conn = (HttpURLConnection) mUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setInstanceFollowRedirects(false);

                conn.setRequestProperty("Accept", "application/xml");
                conn.setRequestProperty("Content-Type", "application/vnd.onem2m-res+xml;ty=4");
                conn.setRequestProperty("locale", "ko");
                conn.setRequestProperty("X-M2M-RI", "12345");
                conn.setRequestProperty("X-M2M-Origin", "Mobius");

                String reqContent = contentinstance.makeXML();
                conn.setRequestProperty("Content-Length", String.valueOf(reqContent.length()));

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                dos.write(reqContent.getBytes());
                dos.flush();
                dos.close();

                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                String resp = "";
                String strLine="";
                while ((strLine = in.readLine()) != null) {
                    resp += strLine;
                }
                if (resp != "") {
                    receiver.getResponseBody(resp);
                }
                conn.disconnect();

            } catch (Exception exp) {
                LOG.log(Level.SEVERE, exp.getMessage());
            }
        }
    }

}

