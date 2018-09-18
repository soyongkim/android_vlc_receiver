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
import android.graphics.drawable.Drawable;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
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
    public ImageView imageView;

    private TextView vt_state;
    private boolean idleActive;
    private UVLinkPacket receivedPacket;
    int IdleUpdateRateMs = 200;
    byte sequenceNumber = 0;

    String errormsg;
    String message;

    public int vtidstate=1;

    private static CSEBase csebase = new CSEBase();
    private static AE ae = new AE();
    private String ServiceAEName = "VR1";
    private EditText EditText_Address = null;
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
                        //SerialConsoleActivity.this.updateReceivedData(data);
                        //byte[] syncData = syncStream(data);
                        updateReceivedData(data);
                        readText(1);
                        }
                });
            }

    };

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
                if(vtidstate >= 30)
                    vtidstate = 1;
                Toast.makeText(getApplicationContext(), "Click", Toast.LENGTH_SHORT).show();
                String hex;
                if(vtidstate < 16)
                    hex = "000" + Integer.toHexString( vtidstate );
                else
                    hex = "00" + Integer.toHexString( vtidstate );
                //String data = "{\"playerID\":1234,\"name\":\"Test\",\"itemList\":[{\"itemID\":1,\"name\":\"Axe\",\"atk\":12,\"def\":0},{\"itemID\":2,\"name\":\"Sword\",\"atk\":5,\"def\":5},{\"itemID\":3,\"name\":\"Shield\",\"atk\":0,\"def\":10}]}";
                String data = "{\"type\":\"0001\",\"id\":\""+ hex +"\",\"data\":\"0102030405060708090a0b0c0d0f\"}";
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
                vt_state.setText("VT" + vtidstate);
                vtidstate++;
                Toast.makeText(getApplicationContext(), csebase.getServiceUrl(), Toast.LENGTH_SHORT).show();
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

                showStatus(mDumpTextView, "CD  - Carrier Detect", sPort.getCD());

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
        //BeginIdleLoop();
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

    public void readText(int id) {
        try{

            // getResources().openRawResource()로 raw 폴더의 원본 파일을 가져온다.
            // txt 파일을 InpuStream에 넣는다. (open 한다)
            InputStream in = getResources().openRawResource(R.raw.vt1);

            if(id == 1)
                in = getResources().openRawResource(R.raw.vt1);
            else if(id==2)
                in = getResources().openRawResource(R.raw.vt2);
            else if(id==3)
                in = getResources().openRawResource(R.raw.vt3);

            if(in != null){

                InputStreamReader stream = new InputStreamReader(in, "utf-8");
                BufferedReader buffer = new BufferedReader(stream);

                String read;
                StringBuilder sb = new StringBuilder("");

                while((read=buffer.readLine())!=null){
                    sb.append(read);
                }

                in.close();

                // id : textView01 TextView를 불러와서
                //메모장에서 읽어온 문자열을 등록한다.
                mDumpTextView.append(sb.toString());
                mScrollView.smoothScrollTo(0, mDumpTextView.getBottom());
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /* AE Create for Androdi AE */
    public void GetAEInfo() {

        Mobius_Address = EditText_Address.getText().toString();

        csebase.setInfo(Mobius_Address,"7579","Mobius","1883");

        //csebase.setInfo("203.253.128.151","7579","Mobius","1883");
        // AE Create for Android AE
        ae.setAppName("usb-vr");
        aeCreateRequest aeCreate = new aeCreateRequest();
        aeCreate.setReceiver(new IReceived() {
            public void getResponseBody(final String msg) {
                handler.post(new Runnable() {
                    public void run() {
                        Log.d(TAG, "** AE Create ResponseCode[" + msg +"]");
                        if( Integer.parseInt(msg) == 201 ){
                            //MQTT_Req_Topic = "/oneM2M/req/Mobius2/"+ae.getAEid()+"_sub"+"/#";
                            //MQTT_Resp_Topic = "/oneM2M/resp/Mobius2/"+ae.getAEid()+"_sub"+"/json";
                            //Log.d(TAG, "ReqTopic["+ MQTT_Req_Topic+"]");
                            //Log.d(TAG, "ResTopic["+ MQTT_Resp_Topic+"]");
                        }
                        else { // If AE is Exist , GET AEID
                            aeRetrieveRequest aeRetrive = new aeRetrieveRequest();
                            aeRetrive.setReceiver(new IReceived() {
                                public void getResponseBody(final String resmsg) {
                                    handler.post(new Runnable() {
                                        public void run() {
                                            //Log.d(TAG, "** AE Retrive ResponseCode[" + resmsg +"]");
                                            //MQTT_Req_Topic = "/oneM2M/req/Mobius2/"+ae.getAEid()+"_sub"+"/#";
                                            //MQTT_Resp_Topic = "/oneM2M/resp/Mobius2/"+ae.getAEid()+"_sub"+"/json";
                                            //Log.d(TAG, "ReqTopic["+ MQTT_Req_Topic+"]");
                                            //Log.d(TAG, "ResTopic["+ MQTT_Resp_Topic+"]");
                                        }
                                    });
                                }
                            });
                            aeRetrive.start();
                        }
                    }
                });
            }
        });
        aeCreate.start();
    }

    /* Request AE Creation */
    class aeCreateRequest extends Thread {
        private final Logger LOG = Logger.getLogger(aeCreateRequest.class.getName());
        String TAG = aeCreateRequest.class.getName();
        private IReceived receiver;
        int responseCode=0;
        public ApplicationEntityObject applicationEntity;
        public void setReceiver(IReceived hanlder) { this.receiver = hanlder; }
        public aeCreateRequest(){
            applicationEntity = new ApplicationEntityObject();
            applicationEntity.setResourceName(ae.getappName());
        }
        @Override
        public void run() {
            try {

                String sb = csebase.getServiceUrl();
                URL mUrl = new URL(sb);

                HttpURLConnection conn = (HttpURLConnection) mUrl.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setInstanceFollowRedirects(false);

                conn.setRequestProperty("Content-Type", "application/vnd.onem2m-res+xml;ty=2");
                conn.setRequestProperty("Accept", "application/xml");
                conn.setRequestProperty("locale", "ko");
                conn.setRequestProperty("X-M2M-Origin", "Mobius");
                conn.setRequestProperty("X-M2M-RI", "12345");
                conn.setRequestProperty("X-M2M-NM", "Mobius" );

                String reqXml = applicationEntity.makeXML();
                conn.setRequestProperty("Content-Length", String.valueOf(reqXml.length()));

                DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
                dos.write(reqXml.getBytes());
                dos.flush();
                dos.close();

                responseCode = conn.getResponseCode();

                BufferedReader in = null;
                String aei = "";
                if (responseCode == 201) {
                    // Get AEID from Response Data
                    in = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                    String resp = "";
                    String strLine;
                    while ((strLine = in.readLine()) != null) {
                        resp += strLine;
                    }

                    ParseElementXml pxml = new ParseElementXml();
                    aei = "Mobius";

                    Log.d(TAG, "Create Get AEID[" + aei + "]");
                    in.close();
                }
                if (responseCode != 0) {
                    receiver.getResponseBody( Integer.toString(responseCode) );
                }
                conn.disconnect();
            } catch (Exception exp) {
                LOG.log(Level.SEVERE, exp.getMessage());
            }

        }
    }

    /* Request Control LED */
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
   /* Retrieve AE-ID */
    class aeRetrieveRequest extends Thread {
        private final Logger LOG = Logger.getLogger(aeCreateRequest.class.getName());
        private IReceived receiver;
        int responseCode=0;

        public aeRetrieveRequest() {
        }
        public void setReceiver(IReceived hanlder) {
            this.receiver = hanlder;
        }

        @Override
        public void run() {
            try {
                String sb = csebase.getServiceUrl()+"/"+ ae.getappName();
                URL mUrl = new URL(sb);

                HttpURLConnection conn = (HttpURLConnection) mUrl.openConnection();
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.setDoOutput(false);

                conn.setRequestProperty("Accept", "application/xml");
                conn.setRequestProperty("X-M2M-RI", "12345");
                conn.setRequestProperty("X-M2M-Origin", "Sandoroid");
                conn.setRequestProperty("nmtype", "short");
                conn.connect();

                responseCode = conn.getResponseCode();

                BufferedReader in = null;
                String aei = "";
                if (responseCode == 200) {
                    // Get AEID from Response Data
                    in = new BufferedReader(new InputStreamReader(conn.getInputStream()));

                    String resp = "";
                    String strLine;
                    while ((strLine = in.readLine()) != null) {
                        resp += strLine;
                    }

                    ParseElementXml pxml = new ParseElementXml();
                    aei = pxml.GetElementXml(resp, "aei");
                    ae.setAEid( aei );
                    //Log.d(TAG, "Retrieve Get AEID[" + aei + "]");
                    in.close();
                }
                if (responseCode != 0) {
                    receiver.getResponseBody( Integer.toString(responseCode) );
                }
                conn.disconnect();
            } catch (Exception exp) {
                LOG.log(Level.SEVERE, exp.getMessage());
            }
        }
    }


}

