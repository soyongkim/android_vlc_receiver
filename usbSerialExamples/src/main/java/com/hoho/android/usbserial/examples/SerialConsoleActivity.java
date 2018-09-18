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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
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

    public final byte PacketSignalByte = 0x44;
    private byte VehicleId = 2; // 고정
    private Button vt_btn;
    private Button vr_btn;
    public ImageView consoleImage;
    public VideoView consoleVideo;
    MediaPlayer mplaer;


    private TextView vt_state;
    private boolean idleActive;
    int IdleUpdateRateMs = 200;
    byte sequenceNumber = 0;
    int count = 1;

    String errormsg;
    String message;

    public int vtidstate = 0;
    public int vr_state = 0;
    public int output_mode = 0;
    public int stopPosition;

    private static CSEBase csebase = new CSEBase();
    private EditText EditText_Address = null;
    private EditText vt_edit = null;
    private String Mobius_Address ="";

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
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
                SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int id = byte2int(data);
                        //updateReceivedData(data);
                        //readText(id);
                        //viewImage(id);
                        //playVideo(id);
                        processData(id);

                        vt_state.setText("VT" + id);
                    }
                });
            }

    };

    public static int byte2int(byte[] src) {
        int s1 = 0 & 0xFF;
        int s2 = 0 & 0xFF;
        int s3 = src[2] & 0xFF;
        int s4 = src[3] & 0xFF;

        return ((s1 << 24) + (s2 << 16) + (s3 << 8) + (s4 << 0));
    }

    public class count_video implements Runnable {
        @Override
        public void run() {
            while (true)
            {
                if(output_mode == 2) {
                    try {
                        if (count == 0) {
                            //mplaer.pause();
                        } else {
                            count--;
                            Thread.sleep(1000);
                        }
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "thread:" + e, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_console);
        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        mDumpTextView = (TextView) findViewById(R.id.consoleText);
        vt_state = (TextView) findViewById(R.id.id_state);
        mScrollView = (ScrollView) findViewById(R.id.demoScroller);
        vr_btn = (Button) findViewById(R.id.vr_cnt_btn);
        vt_btn = (Button) findViewById(R.id.vt_id_btn);
        btnAddr_Set = (ToggleButton) findViewById(R.id.toggleButton_Addr);
        EditText_Address = (EditText) findViewById(R.id.editText);
        vt_edit = (EditText) findViewById(R.id.vt_edit);
        consoleImage = (ImageView)findViewById(R.id.consoleImage);
        consoleVideo = (VideoView) findViewById(R.id.consoleVideo);

        consoleVideo.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mplaer = mp;
            }
        });

        //imageView = (ImageView)findViewById(R.id.image);
        btnAddr_Set.setFocusable(true);
        btnAddr_Set.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (((ToggleButton) view).isChecked()) {
                    Toast.makeText(getApplicationContext(), "Start Connecting", Toast.LENGTH_SHORT).show();
                    //GetAEInfo();
                    Mobius_Address = EditText_Address.getText().toString();
                    csebase.setInfo(Mobius_Address,"7579","Mobius","1883");

                }
            }
        });

        vt_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(vt_edit.getText().toString().equals("")) {
                    Toast.makeText(getApplicationContext(), "Please Insert Number from 1 to 30", Toast.LENGTH_SHORT).show();
                } else {
//                    if (vtidstate >= 3)
//                        vtidstate = 0;
//                    vtidstate++;
//                    //Toast.makeText(getApplicationContext(), "Click", Toast.LENGTH_SHORT).show();
//                    String hex;
//                    if (vtidstate < 16)
//                        hex = "000" + Integer.toHexString(vtidstate);
//                    else
//                        hex = "00" + Integer.toHexString(vtidstate);
//                    String data = "{\"type\":\"0001\",\"id\":\"" + hex + "\",\"data\":\"0102030405060708090a0b0c0d0f\"}";
//                    RequestVT req = new RequestVT(data);
//                    req.setReceiver(new IReceived() {
//                        public void getResponseBody(final String msg) {
//                            handler.post(new Runnable() {
//                                public void run() {
//                                    Toast.makeText(getApplicationContext(), "Send Message to VT", Toast.LENGTH_SHORT).show();
//                                }
//                            });
//                        }
//                    });
//                    req.start();
//                    Toast.makeText(getApplicationContext(), csebase.getServiceUrl(), Toast.LENGTH_SHORT).show();
                    try {
                        //Toast.makeText(getApplicationContext(), vt_edit.getText().toString(), Toast.LENGTH_SHORT).show();
                        vtidstate = Integer.parseInt(vt_edit.getText().toString());
                        String hex;
                        if (vtidstate < 16)
                            hex = "000" + Integer.toHexString(vtidstate);
                        else
                            hex = "00" + Integer.toHexString(vtidstate);
                        String data = "{\"type\":\"0001\",\"id\":\"" + hex + "\",\"data\":\"0102030405060708090a0b0c0d0f\"}";
                        RequestVT req = new RequestVT(data);
                        req.setReceiver(new IReceived() {
                            public void getResponseBody(final String msg) {
                                handler.post(new Runnable() {
                                    public void run() {
                                        Toast.makeText(getApplicationContext(), "Send Message to VT", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });
                        req.start();
                        Toast.makeText(getApplicationContext(), csebase.getServiceUrl(), Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(getApplicationContext(), "e:"+e, Toast.LENGTH_LONG).show();
                    }
                }
            }
        });

        vr_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(output_mode >= 2)
                    output_mode = -1;
                output_mode++;
                enableOutput_mode();

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

    void showStatus(TextView theTextView, String theLabel, boolean theValue){
        String msg = theLabel + ": " + (theValue ? "enabled" : "disabled") + "\n";
        theTextView.append(msg);
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

                //showStatus(mDumpTextView, "CD  - Carrier Detect", sPort.getCD());

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
            mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());
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
// 커맨드에 따라서 저런식으로 필요한 정보를 빼내서 쓰면 됨. 패킷에 어떠한 정보가있는지는
    // GeneratedMessages 클래스를 보면 상세하게 나옴.
    private void updateReceivedData(UVLinkPacket packet) {

        switch ((int)packet.Command) {
            case 0:
                message = "MessageId :" + packet.Command +
                        //* "\nYaw :"+ ((GeneratedMessages.GCS_FLIGHT_INFO_1) packet.Message).getYaw()*//* +
                        "\nSync1 :"+packet.Sync1 +
                        "\nSync2 :"+packet.Sync2 +
                        "\nV_id :"+packet.VehicleID +
                        "\nPayload :"+HexDump.toHexString(packet.Payload) +
                        "\nPay_length :"+packet.PayLoadLength +
                        "\nPacketCount :"+packet.PacketCount +
                        "\nCheckSum1 :"+HexDump.toHexString(packet.CheckSum1) +
                        "\nCheckSum2 :"+HexDump.toHexString(packet.CheckSum2) +
                        "\nPacket_length : " + packet.GetPacketSize() +
                        "\ncrc_available :"+packet.crc_available +"\n\n";
                break;
            case 7:
                message = "Read :" + packet.GetPacketSize() + " bytes: \n"
                        + "MessageId :" + packet.Command +
                        "\nYaw :"+ ((GeneratedMessages.GCS_FLIGHT_INFO_1) packet.Message).getRoll() +
                        "\nSync1 :"+packet.Sync1 +
                        "\nSync2 :"+packet.Sync2 +
                        "\nV_id :"+packet.VehicleID +
                        "\nPayload :"+HexDump.toHexString(packet.Payload) +
                        "\nPay_length :"+packet.PayLoadLength +
                        "\nPacketCount :"+(packet.PacketCount & 0xff) +
                        "\nCheckSum1 :"+HexDump.toHexString(packet.CheckSum1) +
                        "\nCheckSum2 :"+HexDump.toHexString(packet.CheckSum2) +
                        "\ncrc_available :"+packet.crc_available +"\n\n";
                break;
            case 8:
                //Toast.makeText(SerialConsoleActivity.this, "pbset :" + ((GeneratedMessages.GCS_FLIGHT_INFO_2) packet.Message).getPb_set(), Toast.LENGTH_SHORT).show();
                message = "Read :" + packet.GetPacketSize() + " bytes: \n"
                        + "MessageId :" +packet.Command+
                        "\nSync1 :"+packet.Sync1 +
                        "\nSync2 :"+packet.Sync2 +
                        "\nV_id :"+packet.VehicleID +
                        "\nPayload :"+HexDump.toHexString(packet.Payload) +
                        "\nPay_length :"+packet.PayLoadLength +
                        "\nPacketCount :"+(packet.PacketCount & 0xff) +
                        "\nCheckSum1 :"+HexDump.toHexString(packet.CheckSum1) +
                        "\nCheckSum2 :"+HexDump.toHexString(packet.CheckSum2) +
                        "\ncrc_available :"+packet.crc_available +"\n\n";
                break;
            case 9:
                //Toast.makeText(SerialConsoleActivity.this, "MISSION_ID :" + ((GeneratedMessages.GCS_MISSION_INFO) packet.Message).getMission_ID(), Toast.LENGTH_SHORT).show();
                       message = "Read :" + packet.GetPacketSize() + " bytes: \n"
                        + "MessageId :" +packet.Command+
                               "\nSync1 :"+packet.Sync1 +
                               "\nSync2 :"+packet.Sync2 +
                               "\nV_id :"+packet.VehicleID +
                               "\nPayload :"+HexDump.toHexString(packet.Payload) +
                               "\nPay_length :"+packet.PayLoadLength +
                               "\nPacketCount :"+(packet.PacketCount & 0xff) +
                               "\nCheckSum1 :"+HexDump.toHexString(packet.CheckSum1) +
                               "\nCheckSum2 :"+HexDump.toHexString(packet.CheckSum2) +
                               "\nMission_ID :"+ ((GeneratedMessages.GCS_MISSION_INFO) packet.Message).getMission_ID() +
                               "\ncrc_available :"+packet.crc_available +"\n\n";
                break;
            case 11:
                byte n = 11;
                UVLinkMessage msg = GeneratedMessages.CreateFromCommand(packet.Command);
                try {
                    Toast.makeText(SerialConsoleActivity.this, "NSV :" + ((GeneratedMessages.GCS_GPS_INFO) packet.Message).getNSV(), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    errormsg = e.getMessage();
                }
                message = "Read :" + packet.GetPacketSize() + " bytes: \n"
                        + "MessageId :" +packet.Command +
                        "\nSync1 :"+packet.Sync1 +
                        "\nSync2 :"+packet.Sync2 +
                        "\nV_id :"+packet.VehicleID +
                        "\nPayload :"+HexDump.toHexString(packet.Payload) +
                        "\nPay_length :"+packet.PayLoadLength +
                        "\nPacketCount :"+(packet.PacketCount & 0xff) +
                        "\nCheckSum1 :"+HexDump.toHexString(packet.CheckSum1) +
                        "\nCheckSum2 :"+HexDump.toHexString(packet.CheckSum2) +
                        "\nHAcc :"+ ((GeneratedMessages.GCS_GPS_INFO) packet.Message).getVAcc() +
                        "\ncrc_available :"+packet.crc_available + "\n\n";
                break;
            case 12:
                //Toast.makeText(SerialConsoleActivity.this, "ELA :" + ((GeneratedMessages.GCS_SYS_STATUS) packet.Message).Elapsed_time(), Toast.LENGTH_SHORT).show();
                message = "Read :" + packet.GetPacketSize() + " bytes: \n"
                        + "MessageId :" +packet.Command +
                        "\nSync1 :"+packet.Sync1 +
                        "\nSync2 :"+packet.Sync2 +
                        "\nV_id :"+packet.VehicleID +
                        "\nPayload :"+ HexDump.toHexString(packet.Payload) +
                        "\nPay_length :"+packet.PayLoadLength +
                        "\nPacketCount :"+packet.PacketCount +
                        "\nCheckSum1 :"+HexDump.toHexString(packet.CheckSum1) +
                        "\nCheckSum2 :"+HexDump.toHexString(packet.CheckSum2) +
                        "\nBatt volt :"+ ((GeneratedMessages.GCS_SYS_STATUS) packet.Message).Batt_volt() +
                        "\ncrc_available :"+packet.crc_available +"\n\n";
                break;
            default:
                message = "Message Command :" +  HexDump.toHexString(packet.Command) +
                        "\nSync1 :"+HexDump.toHexString(packet.Sync1) +
                        "\nSync2 :"+HexDump.toHexString(packet.Sync2) +
                        "\nV_id :"+packet.VehicleID +
                        "\nPayload :"+ HexDump.toHexString(packet.Payload) +
                        "\nPay_length :"+packet.PayLoadLength +
                        "\nPacketCount :"+packet.PacketCount +
                        "\nCheckSum1 :"+HexDump.toHexString(packet.CheckSum1) +
                        "\nCheckSum2 :"+HexDump.toHexString(packet.CheckSum2) +"\n";
                break;

        }
        mDumpTextView.append(message);
        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    private void updateReceivedData(byte[] data) {
        final String message = "Read : " + data.length + " bytes :\n" + HexDump.toHexString(data)+ "\n";
        //Toast.makeText(getApplicationContext(), "Data:" + message, Toast.LENGTH_LONG).show();
        mDumpTextView.append(message);
        mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
    }

    private void SendUVLinkMessage(UVLinkMessage msg) {
        try {
            if (msg.getuMessageId() != GeneratedMessages.UP_LINK_COMMAND.GCS_IDLE_COMMAND && sequenceNumber % 2 != 1) {
                sequenceNumber++;
            }
            byte[] send_data = UVLinkPacket.GetBytesForMessage(msg, VehicleId, sequenceNumber++);
            mSerialIoManager.write(send_data);
            //mSerialIoManager.writeAsync(send_data);
        } catch (Exception e) {
            Toast.makeText(SerialConsoleActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void BeginIdleLoop() {
        idleActive = true;
        mExecutor.submit(new IdleLoop());
        //new Thread(new IdleLoop()).start();

    }

    public class IdleLoop implements Runnable {
        @Override
        public void run() {
            while (idleActive)
            {
                    try {
                        SendUVLinkMessage(new GeneratedMessages().new GCS_IDLE_COMMAND());
                        Thread.sleep(IdleUpdateRateMs);
                    } catch (Exception e) {
                    }

            }
        }
    }

    public byte[] syncStream(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        byte check = buf.get();
        //Toast.makeText(SerialConsoleActivity.this, "check :" + HexDump.toHexString(check), Toast.LENGTH_SHORT).show();
/*        int i = buf.position();
        byte[] check2 = new byte[3];
        buf.get(check2);
        byte check3 = buf.get();
        int i2 = buf.position();
        Toast.makeText(SerialConsoleActivity.this, "check :" + HexDump.toHexString(check), Toast.LENGTH_SHORT).show();
        Toast.makeText(SerialConsoleActivity.this, "position1 :" + i, Toast.LENGTH_SHORT).show();
        Toast.makeText(SerialConsoleActivity.this, "position2 :" + i2, Toast.LENGTH_SHORT).show();*/
        while (check != PacketSignalByte)
        {
            // Skip bytes until a packet start is found
            //Toast.makeText(SerialConsoleActivity.this, "check_while :" + HexDump.toHexString(check), Toast.LENGTH_SHORT).show();
            check = buf.get();
            if(buf.remaining() == 0) break;
        }
        byte[] remain = new byte[buf.remaining()];
        buf.get(remain);
        return remain;
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
        switch (output_mode) {
            case 0:
                //텍스트 실행
                mScrollView.setVisibility(View.VISIBLE);
                consoleVideo.setVisibility(View.INVISIBLE);
                consoleImage.setVisibility(View.INVISIBLE);
                vr_btn.setText("Text");
                break;
            case 1:
                mScrollView.setVisibility(View.INVISIBLE);
                consoleVideo.setVisibility(View.INVISIBLE);
                consoleImage.setVisibility(View.VISIBLE);
                vr_btn.setText("Image");
                //이미지 실행
                break;
            case 2:
                mScrollView.setVisibility(View.INVISIBLE);
                consoleVideo.setVisibility(View.VISIBLE);
                consoleImage.setVisibility(View.INVISIBLE);
                vr_btn.setText("Video");
                //동영상 실행
                break;
        }
    }

    public void processData(int rid) {
        //초기상태에서 처음으로 패킷을 받을 경우
        //Toast.makeText(getApplicationContext(), "rid:" + rid + " vr_state:" + vr_state + " mode:" + output_mode, Toast.LENGTH_SHORT).show();
        if(vr_state == 0) {
            try {
                mExecutor.submit(new count_video());
                vr_state = rid;
                playVideo(rid);
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), "e:" + e, Toast.LENGTH_SHORT).show();
            }
            // 다른 id의 패킷을 받을 경우
        } else    if(vr_state != rid) {
            vr_state = rid;
            switch (output_mode) {
                case 0:
                    readText(rid);
                    //텍스트 실행
                    break;
                case 1:
                    viewImage(rid);
                    //이미지 실행
                    break;
                case 2:
                    //동영상 실행
                    playVideo(rid);
                    break;


            }
        }
        else {
                vr_state = rid;
                switch (output_mode) {
                    case 0:
                        //텍스트 실행
                        readText(rid);
                        break;
                    case 1:
                        //이미지 실행
                        viewImage(rid);
                        break;
                    case 2:
                        //Toast.makeText(getApplicationContext(), "come" , Toast.LENGTH_SHORT).show();
                        try {
                            count = 1;
                            mplaer.start();
                        } catch (Exception e) {
                            Toast.makeText(getApplicationContext(), "e3:" + e, Toast.LENGTH_SHORT).show();
                            //playVideo(rid);
                        }
                        break;
                }
        }
    }

    public void viewImage(int id) {
        try {
            Uri path = Uri.parse("android.resource://com.hoho.android.usbserial.examples/raw/image_vt" + id);
            consoleImage.setImageURI(path);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "ImageError:"+e, Toast.LENGTH_LONG).show();
        }
    }

    public void readText(int id) {
        try{

            Uri path = Uri.parse("android.resource://com.hoho.android.usbserial.examples/raw/text_vt" + id);
            InputStream in = getContentResolver().openInputStream(path);

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
            e.printStackTrace();
        }
    }

    public void playVideo(int id) {
        try {
            Uri path = Uri.parse("android.resource://com.hoho.android.usbserial.examples/raw/video_vt" + id);
            consoleVideo.setVideoURI(path);
            consoleVideo.requestFocus();
            consoleVideo.start();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "play:" + e , Toast.LENGTH_LONG).show();
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

