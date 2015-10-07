package com.sphero.bigandroidbbq_sphero;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.orbotix.ConvenienceRobot;
import com.orbotix.DualStackDiscoveryAgent;
import com.orbotix.async.DeviceSensorAsyncMessage;
import com.orbotix.classic.DiscoveryAgentClassic;
import com.orbotix.command.BackLEDOutputCommand;
import com.orbotix.command.RGBLEDOutputCommand;
import com.orbotix.command.SetMotionTimeoutCommand;
import com.orbotix.common.DiscoveryAgentEventListener;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.ResponseListener;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;
import com.orbotix.common.internal.AsyncMessage;
import com.orbotix.common.internal.DeviceResponse;
import com.orbotix.common.sensor.SensorFlag;
import com.orbotix.le.DiscoveryAgentLE;
import com.orbotix.macro.Macro;
import com.orbotix.macro.MacroObject;
import com.orbotix.macro.cmd.BackLED;
import com.orbotix.macro.cmd.Delay;
import com.orbotix.macro.cmd.Fade;
import com.orbotix.macro.cmd.LoopEnd;
import com.orbotix.macro.cmd.LoopStart;
import com.orbotix.macro.cmd.MacroCommandCreationException;
import com.orbotix.macro.cmd.RGB;
import com.orbotix.macro.cmd.RotateOverTime;
import com.orbotix.ovalcompiler.OvalControl;
import com.orbotix.ovalcompiler.response.async.OvalDeviceBroadcast;
import com.orbotix.ovalcompiler.response.async.OvalErrorBroadcast;
import com.orbotix.subsystem.SensorControl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements RobotChangedStateListener, DiscoveryAgentEventListener, AdapterView.OnItemClickListener, ResponseListener, OvalControl.OvalControlListener {

    private final int REQUEST_CODE = 42;

    private Toolbar mToolbar;
    private ListView mList;
    private TextView mTextView;
    private OvalControl mOvalControl;
    private ConvenienceRobot mRobot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        DualStackDiscoveryAgent.getInstance().addRobotStateListener(this);
        DualStackDiscoveryAgent.getInstance().addDiscoveryListener(this);
        initToolbar();
        initViews();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch( requestCode ) {
            case REQUEST_CODE: {
                for( int i = 0; i < permissions.length; i++ ) {
                    if( permissions[i].equalsIgnoreCase( Manifest.permission.ACCESS_COARSE_LOCATION ) ) {
                        if( grantResults[i] == PackageManager.PERMISSION_GRANTED ) {
                            startRobotDiscovery();
                        } else {
                            Toast.makeText( this, "Location permission required for Bluetooth communication to robot", Toast.LENGTH_SHORT ).show();
                        }
                    }
                }

                break;
            } default: {
                super.onRequestPermissionsResult( requestCode, permissions, grantResults );
            }
        }
    }

    private void initToolbar() {
        mToolbar = (Toolbar) findViewById( R.id.toolbar );
        setSupportActionBar(mToolbar);
    }

    private void initViews() {
        mList = (ListView) findViewById( R.id.list_view );
        ArrayAdapter<String> adapter = new ArrayAdapter<String>( this, android.R.layout.simple_list_item_1, getResources().getStringArray( R.array.robot_actions ) );
        mList.setAdapter(adapter);
        mList.setOnItemClickListener(this);

        mTextView = (TextView) findViewById( R.id.text_view );
    }

    @Override
    protected void onStart() {
        super.onStart();
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
            List<String> permissions = new ArrayList<String>();
            int hasLocationPermission = checkSelfPermission( Manifest.permission.ACCESS_COARSE_LOCATION );

            if( hasLocationPermission == PackageManager.PERMISSION_GRANTED ) {
                startRobotDiscovery();
            } else {
                permissions.add( Manifest.permission.ACCESS_COARSE_LOCATION );
            }

            if( !permissions.isEmpty() ) {
                requestPermissions( permissions.toArray( new String[permissions.size()]), REQUEST_CODE );
            }

        } else {
            startRobotDiscovery();
        }
    }

    private void startRobotDiscovery() {
        try {
            DualStackDiscoveryAgent.getInstance().startDiscovery( this );
        } catch( DiscoveryException e ) {
            Log.e("Sphero", e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onStop() {
        if( mRobot != null && mRobot.isConnected() ) {
            mRobot.disconnect();
        }
        super.onStop();
    }

    @Override
    public void handleRobotChangedState(Robot robot, RobotChangedStateNotificationType stateType) {
        switch( stateType ) {
            case Online: {
                robot.sendCommand( new RGBLEDOutputCommand( 0.0f, 1.0f, 0.0f ) );
                robot.sendCommand( new SetMotionTimeoutCommand( 0 ) );

                mRobot = new ConvenienceRobot( robot );
                mRobot.addResponseListener(this);

                mOvalControl = new OvalControl( robot, this );
                break;
            }
            case Offline: {
                mRobot = null;
                break;
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if( mRobot == null || !mRobot.isConnected() ) {
            return;
        }

        String item = (String) parent.getItemAtPosition( position );

        if( item.equalsIgnoreCase( getString( R.string.robot_action_led ) ) ) {
            setRandomRobotLEDColor();
        }
        else if( item.equalsIgnoreCase( getString( R.string.robot_action_drive ) ) ) {
            moveRobot();
        }
        else if( item.equalsIgnoreCase( getString( R.string.robot_action_stop ) ) ) {
            resetRobot();
        }
        else if( item.equalsIgnoreCase( getString( R.string.robot_action_request_sensors ) ) ) {
            requestSensorData();
        }
        else if( item.equalsIgnoreCase( getString( R.string.robot_action_macro ) ) ) {
            loadMacro();
        }
        else if( item.equalsIgnoreCase( getString( R.string.robot_action_oval ) ) ) {
            loadOvalProgram();
        }
    }

    private void setRandomRobotLEDColor() {
        Random random = new Random( System.currentTimeMillis() );
        mRobot.setLed( random.nextFloat(), random.nextFloat(), random.nextFloat() );
    }

    private void moveRobot() {
        mRobot.drive(mRobot.getLastHeading(), 1.0f);
    }

    private void resetRobot() {
        mRobot.stop();
        mRobot.abortMacro();
        mRobot.disableSensors();
        mRobot.enableStabilization(true);
        mOvalControl.resetOvm(true);
        mTextView.setText("");
    }

    private void requestSensorData() {
        long sensorFlag = SensorFlag.ACCELEROMETER_NORMALIZED.longValue() | SensorFlag.GYRO_NORMALIZED.longValue();
        mRobot.enableStabilization( false );
        mRobot.enableSensors( sensorFlag, SensorControl.StreamingRate.STREAMING_RATE10 );
    }

    private void loadMacro() {
        MacroObject macro = new MacroObject();

        //Set the back LED to full brightness
        macro.addCommand( new BackLED( 255, 0 ) );

        macro.addCommand( new RGB( 255, 0, 0, 0 ) );
        macro.addCommand( new Fade( 0, 0, 255, 2500 ) );

        //Loop through rotating the robot
        macro.addCommand( new LoopStart( 5 ) );
        macro.addCommand( new RotateOverTime( 360, 500 ) );
        macro.addCommand( new Delay(500 ) );
        macro.addCommand( new LoopEnd() );

        //Dim the back LED
        macro.addCommand(new BackLED(0, 0));

        //Send the macro to the robot and play
        macro.setMode(MacroObject.MacroObjectMode.Normal);
        mRobot.loadMacro(macro);
        mRobot.playMacro();
    }

    private void loadOvalProgram() {
        String program = null;
        program = getOvalProgram( "sample.oval" );
        if( program != null ) {
            mOvalControl.sendOval( program );
        }
    }

    @Override
    public void handleResponse(DeviceResponse deviceResponse, Robot robot) {

    }

    @Override
    public void handleStringResponse(String s, Robot robot) {

    }

    @Override
    public void handleAsyncMessage(AsyncMessage asyncMessage, Robot robot) {
        if (asyncMessage instanceof DeviceSensorAsyncMessage) {
            DeviceSensorAsyncMessage message = (DeviceSensorAsyncMessage) asyncMessage;
            double accelerometerX = message.getAsyncData().get(0).
                    getAccelerometerData().getFilteredAcceleration().x;
            double accelerometerY = message.getAsyncData().get(0)
                    .getAccelerometerData().getFilteredAcceleration().y;
            double accelerometerZ = message.getAsyncData().get(0)
                    .getAccelerometerData().getFilteredAcceleration().z;

            double gyroX = message.getAsyncData().get(0)
                    .getGyroData().getRotationRateFiltered().x;
            double gyroY = message.getAsyncData().get(0)
                    .getGyroData().getRotationRateFiltered().y;
            double gyroZ = message.getAsyncData().get(0)
                    .getGyroData().getRotationRateFiltered().z;

            mTextView.setText( getString( R.string.display_sensor_data,
                    accelerometerX,
                    accelerometerY,
                    accelerometerZ,
                    gyroX,
                    gyroY,
                    gyroZ ) );
        }
    }

    @Override
    public void handleRobotsAvailable(List<Robot> list) {
        for( Robot robot : list ) {
               Log.e( "Sphero", "Robot found: " + robot.getName() );
        }
    }

    public String getOvalProgram( String file ) {
        if( file == null || "".equals( file ) ) {
            return null;
        }

        try {
            InputStream inputStream = getAssets().open(file);
            if (inputStream == null) {
                return null;
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            int next = inputStream.read();

            while (next > -1) {
                bos.write(next);
                next = inputStream.read();
            }

            byte[] bytes = bos.toByteArray();
            return new String(bytes);
        } catch( IOException e ) {
            return null;
        }
    }

    @Override
    public void onProgramFailedToSend(OvalControl ovalControl, String s) {
        Log.e( "Sphero", "Program failed to send: " + s );
    }

    @Override
    public void onProgramSentSuccessfully(OvalControl ovalControl) {
        Log.e( "Sphero", "Program sent successfully" );
    }

    @Override
    public void onOvmReset(OvalControl ovalControl) {
        Log.e( "Sphero", "OVM successfully reset" );
    }

    @Override
    public void onOvalNotificationReceived(OvalControl ovalControl, OvalDeviceBroadcast ovalDeviceBroadcast) {
        Log.e( "Sphero", "Oval notification received: " + ovalDeviceBroadcast.toString() );
    }

    @Override
    public void onOvmRuntimeErrorReceived(OvalControl ovalControl, OvalErrorBroadcast ovalErrorBroadcast) {
        Log.e( "Sphero", "OVM runtime error code received: " + ovalErrorBroadcast.getErrorCode() );
    }

    @Override
    public void onOvalQueueEmptied(OvalControl ovalControl) {
        Log.e( "Sphero", "Oval queue emptied" );
    }
}
