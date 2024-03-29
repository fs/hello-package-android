package com.example.tthellopackage;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.vidyo.VidyoClient.Connector.Connector;
import com.vidyo.VidyoClient.Connector.ConnectorPkg;
import com.vidyo.VidyoClient.Device.Device;
import com.vidyo.VidyoClient.Device.LocalCamera;
import com.vidyo.VidyoClient.Endpoint.LogRecord;
import com.vidyo.VidyoClient.NetworkInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.tthellopackage.MainActivity.EXTRA_NAME;

public class SecondActivity extends Activity implements
        View.OnClickListener,
        Connector.IConnect,
        Connector.IRegisterLogEventListener,
        Connector.IRegisterNetworkInterfaceEventListener,
        Connector.IRegisterLocalCameraEventListener,
        IVideoFrameListener {

    private static final String PORTAL_EXTRA_NAME = "PORTAL_EXTRA_NAME";
    private static final String ROOM_EXTRA_NAME = "ROOM_EXTRA_NAME";
    private static final String TOKEN_EXTRA_NAME = "TOKEN_EXTRA_NAME";
    private static final String NAME_EXTRA_NAME = "NAME_EXTRA_NAME";

    // Define the various states of this application.
    enum VidyoConnectorState {
        Connecting,
        Connected,
        Disconnecting,
        Disconnected,
        DisconnectedUnexpected,
        Failure,
        FailureInvalidResource
    }

    public static Intent intent(Context context, String host, String token, String room,
                                Integer name) {
        return new Intent(context, SecondActivity.class)
                .putExtra(PORTAL_EXTRA_NAME, host)
                .putExtra(ROOM_EXTRA_NAME, room)
                .putExtra(TOKEN_EXTRA_NAME, token)
                .putExtra(NAME_EXTRA_NAME, name);
    }

    // Map the application state to the status to display in the toolbar.
    private static final Map<VidyoConnectorState, String> mStateDescription = new HashMap<VidyoConnectorState, String>() {{
        put(VidyoConnectorState.Connecting, "Connecting...");
        put(VidyoConnectorState.Connected, "Connected");
        put(VidyoConnectorState.Disconnecting, "Disconnecting...");
        put(VidyoConnectorState.Disconnected, "Disconnected");
        put(VidyoConnectorState.DisconnectedUnexpected, "Unexpected disconnection");
        put(VidyoConnectorState.Failure, "Connection failed");
        put(VidyoConnectorState.FailureInvalidResource, "Invalid Resource ID");
    }};

    // - This arbitrary, app-internal constant represents a group of requested permissions.
    // - For simplicity, this app treats all desired permissions as part of a single group.
    private final int PERMISSIONS_REQUEST_ALL = 0x7c4;

    // Helps check whether app has permission to access what is declared in its manifest.
    // - Permissions from app's manifest that have a "protection level" of "dangerous".
    private static final String[] PERMISSIONS_FOR_REQUEST = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_PHONE_STATE
    };

    private Connector mVidyoConnector = null;
    private LocalCamera mLastSelectedCamera = null;
    private VideoFrameLayout mVideoFrame;

    private ToggleButton mToggleConnectButton;
    private ToggleButton mMicrophonePrivacyButton;
    private ToggleButton mCameraPrivacyButton;

    private ProgressBar mProgressLoading;

    private LinearLayout mToolbarLayout;

    private TextView mToolbarStatus;

    private VidyoConnectorState mVidyoConnectorState = VidyoConnectorState.Disconnected;

    private Logger mLogger = Logger.getInstance();
    private boolean mDevicesSelected = true;

    private boolean mCameraPrivacy;
    private boolean mMicrophonePrivacy;
    private boolean mEnableDebug;

    private ViewTreeObserver.OnGlobalLayoutListener mOnGlobalLayoutListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_second);

        // Initialize the member variables
        mToolbarLayout = findViewById(R.id.toolbarLayout);

        mVideoFrame = findViewById(R.id.videoFrame);
        mVideoFrame.Register(this);

        mToolbarStatus = findViewById(R.id.toolbarStatusText);

        // Set the onClick listeners for the buttons
        mToggleConnectButton = findViewById(R.id.connect);
        mToggleConnectButton.setOnClickListener(this);
        mToggleConnectButton.setChecked(true);

        mProgressLoading = findViewById(R.id.pb_loading);

        mMicrophonePrivacyButton = findViewById(R.id.microphone_privacy);
        mMicrophonePrivacyButton.setOnClickListener(this);

        mCameraPrivacyButton = findViewById(R.id.camera_privacy);
        mCameraPrivacyButton.setOnClickListener(this);

        findViewById(R.id.camera_switch).setOnClickListener(this);
        findViewById(R.id.toggle_debug).setOnClickListener(this);

        // Set the application's UI context to this activity.
        ConnectorPkg.setApplicationUIContext(this);

        // Initialize the VidyoClient library - this should be done once in the lifetime of the application.
        if (ConnectorPkg.initialize()) {
            // Construct Connector and register for events.
            try {
                mLogger.Log("Constructing Connector");
                String logLevel = mEnableDebug ? "warning debug@VidyoClient all@LmiPortalSession " +
                        "all@LmiPortalMembership info@LmiResourceManagerUpdates info@LmiPace info@LmiIce " +
                        "all@LmiSignaling" : "warning info@VidyoClient info@LmiPortalSession " +
                        "info@LmiPortalMembership info@LmiResourceManagerUpdates info@LmiPace info@LmiIce";
                mVidyoConnector = new Connector(mVideoFrame,
                        Connector.ConnectorViewStyle.VIDYO_CONNECTORVIEWSTYLE_Default,
                        7,
                        logLevel,
                        "",
                        0);

                // Register for local camera events
                if (!mVidyoConnector.registerLocalCameraEventListener(this)) {
                    mLogger.Log("registerLocalCameraEventListener failed");
                }
                // Register for network interface events
                if (!mVidyoConnector.registerNetworkInterfaceEventListener(this)) {
                    mLogger.Log("registerNetworkInterfaceEventListener failed");
                }
                // Register for log events
                if (!mVidyoConnector.registerLogEventListener(this, "info@VidyoClient info@VidyoConnector warning")) {
                    mLogger.Log("registerLogEventListener failed");
                }

                // Beginning in Android 6.0 (API level 23), users grant permissions to an app while
                // the app is running, not when they install the app. Check whether app has permission
                // to access what is declared in its manifest.
                if (Build.VERSION.SDK_INT > 22) {
                    List<String> permissionsNeeded = new ArrayList<>();
                    for (String permission : PERMISSIONS_FOR_REQUEST) {
                        // Check if the permission has already been granted.
                        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED)
                            permissionsNeeded.add(permission);
                    }
                    if (permissionsNeeded.size() > 0) {
                        // Request any permissions which have not been granted. The result will be called back in onRequestPermissionsResult.
                        ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSIONS_REQUEST_ALL);
                    } else {
                        // Begin listening for video view size changes.
                        this.startVideoViewSizeListener();
                    }
                } else {
                    // Begin listening for video view size changes.
                    this.startVideoViewSizeListener();
                }

            } catch (Exception e) {
                mLogger.Log("Connector Construction failed");
                mLogger.Log(e.getMessage());
            }
        }
    }

    @Override
    protected void onStart() {
        mLogger.Log("onStart");
        toggleConnect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        mLogger.Log("onStop");
        super.onStop();

        if (mVidyoConnector != null) {
            if (mVidyoConnectorState != VidyoConnectorState.Connected &&
                    mVidyoConnectorState != VidyoConnectorState.Connecting) {
                // Not connected/connecting to a resource.
                // Release camera, mic, and speaker from this app while backgrounded.
                mVidyoConnector.selectLocalCamera(null);
                mVidyoConnector.selectLocalMicrophone(null);
                mVidyoConnector.selectLocalSpeaker(null);
                mDevicesSelected = false;
            }
            mVidyoConnector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Background);
        }
    }

    @Override
    protected void onRestart() {
        mLogger.Log("onRestart");
        super.onRestart();

        if (mVidyoConnector != null) {
            mVidyoConnector.setMode(Connector.ConnectorMode.VIDYO_CONNECTORMODE_Foreground);

            if (!mDevicesSelected) {
                // Devices have been released when backgrounding (in onStop). Re-select them.
                mDevicesSelected = true;

                // Select the previously selected local camera and default mic/speaker
                mVidyoConnector.selectLocalCamera(mLastSelectedCamera);
                mVidyoConnector.selectDefaultMicrophone();
                mVidyoConnector.selectDefaultSpeaker();

                // Reestablish camera and microphone privacy states
                mVidyoConnector.setCameraPrivacy(mCameraPrivacy);
                mVidyoConnector.setMicrophonePrivacy(mMicrophonePrivacy);
            }
        }
    }

    @Override
    protected void onDestroy() {
        mLogger.Log("onDestroy");
        super.onDestroy();

        // Release device resources
        mLastSelectedCamera = null;
        if (mVidyoConnector != null) {
            mVidyoConnector.selectLocalCamera(null);
            mVidyoConnector.selectLocalMicrophone(null);
            mVidyoConnector.selectLocalSpeaker(null);
        }

        // Connector will be destructed upon garbage collection.
        mVidyoConnector = null;

        ConnectorPkg.setApplicationUIContext(null);

        // Uninitialize the VidyoClient library - this should be done once in the lifetime of the application.
        ConnectorPkg.uninitialize();

        // Remove the global layout listener on the video frame.
        if (mOnGlobalLayoutListener != null) {
            mVideoFrame.getViewTreeObserver().removeOnGlobalLayoutListener(mOnGlobalLayoutListener);
        }
    }

    // The device interface orientation has changed
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mLogger.Log("onConfigurationChanged");
        super.onConfigurationChanged(newConfig);
    }

    // Callback containing the result of the permissions request. If permissions were not previously obtained,
    // wait until this is received until calling startVideoViewSizeListener where Connector is initially rendered.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        mLogger.Log("onRequestPermissionsResult: number of requested permissions = " + permissions.length);

        // If the expected request code is received, begin rendering video.
        if (requestCode == PERMISSIONS_REQUEST_ALL) {
            for (int i = 0; i < permissions.length; ++i)
                mLogger.Log("permission: " + permissions[i] + " " + grantResults[i]);

            // Begin listening for video view size changes.
            this.startVideoViewSizeListener();
        } else {
            mLogger.Log("ERROR! Unexpected permission requested. Video will not be rendered.");
        }
    }

    // Listen for UI changes to the view where the video is rendered.
    private void startVideoViewSizeListener() {
        mLogger.Log("startVideoViewSizeListener");

        // Render the video each time that the video view (mVideoFrame) is resized. This will
        // occur upon activity creation, orientation changes, and when foregrounding the app.
        ViewTreeObserver viewTreeObserver = mVideoFrame.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    // Specify the width/height of the view to render to.
                    mLogger.Log("showViewAt: width = " + mVideoFrame.getWidth() + ", height = " + mVideoFrame.getHeight());
                    mVidyoConnector.showViewAt(mVideoFrame, 0, 0, mVideoFrame.getWidth(), mVideoFrame.getHeight());
                    mOnGlobalLayoutListener = this;
                }
            });
        } else {
            mLogger.Log("ERROR in startVideoViewSizeListener! Video will not be rendered.");
        }
    }

    // The state of the VidyoConnector connection changed, reconfigure the UI.
    // If connected, dismiss the controls layout
    private void changeState(final VidyoConnectorState state) {
        mLogger.Log("changeState: " + state.toString());

        mVidyoConnectorState = state;

        // Execute this code on the main thread since it is updating the UI layout.
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Set the status text in the toolbar.
                mToolbarStatus.setText(mStateDescription.get(mVidyoConnectorState));

                // Depending on the state, do a subset of the following:
                // - update the toggle connect button to either start call or end call image: mToggleConnectButton
                // - display toolbar in case it is hidden: mToolbarLayout
                // - show/hide the connection spinner: mConnectionSpinner
                // - show/hide the input form: mControlsLayout
                switch (mVidyoConnectorState) {
                    case Connecting:
                        mToggleConnectButton.setChecked(true);
                        mProgressLoading.setVisibility(View.VISIBLE);
                        break;

                    case Connected:
                        mToggleConnectButton.setChecked(true);
                        mProgressLoading.setVisibility(View.GONE);

                        // Keep the device awake if connected.
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        break;

                    case Disconnecting:
                        // The button just switched to the callStart image.
                        // Change the button back to the callEnd image because do not want to assume that the Disconnect
                        // call will actually end the call. Need to wait for the callback to be received
                        // before swapping to the callStart image.
                        mProgressLoading.setVisibility(View.VISIBLE);
                        mToggleConnectButton.setChecked(true);
                        break;

                    case Disconnected:
                        mProgressLoading.setVisibility(View.GONE);
                        break;
                    case DisconnectedUnexpected:
                    case Failure:
                    case FailureInvalidResource:
                        mToggleConnectButton.setChecked(false);
                        mToolbarLayout.setVisibility(View.VISIBLE);

                        // Allow the device to sleep if disconnected.
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        break;
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (mVidyoConnector != null) {
            switch (v.getId()) {
                case R.id.connect:
                    // Connect or disconnect.
                    this.toggleConnect();
                    break;

                case R.id.camera_switch:
                    // Cycle the camera.
                    mVidyoConnector.cycleCamera();
                    break;

                case R.id.camera_privacy:
                    // Toggle the camera privacy.
                    mCameraPrivacy = mCameraPrivacyButton.isChecked();
                    mVidyoConnector.setCameraPrivacy(mCameraPrivacy);
                    break;

                case R.id.microphone_privacy:
                    // Toggle the microphone privacy.
                    mMicrophonePrivacy = mMicrophonePrivacyButton.isChecked();
                    mVidyoConnector.setMicrophonePrivacy(mMicrophonePrivacy);
                    break;

                case R.id.toggle_debug:
                    // Toggle debugging.
                    mEnableDebug = !mEnableDebug;
                    if (mEnableDebug) {
                        mVidyoConnector.enableDebug(7776, "warning info@VidyoClient info@VidyoConnector");
                    } else {
                        mVidyoConnector.disableDebug();
                    }
                    break;

                default:
                    mLogger.Log("onClick: Unexpected click event, id=" + v.getId());
                    break;
            }
        } else {
            mLogger.Log("ERROR: not processing click event because Connector is null.");
        }
    }

    /*
     * Button Event Callbacks
     */

    // The Connect button was pressed.
    // If not in a call, attempt to connect to the backend service.
    // If in a call, disconnect.
    public void toggleConnect() {
        if (mToggleConnectButton.isChecked()) {
            this.changeState(VidyoConnectorState.Connecting);

            Bundle extras = getIntent().getExtras();
            assert extras != null;
            String host = extras.getString(PORTAL_EXTRA_NAME, "");
            Integer displayName = extras.getInt(NAME_EXTRA_NAME, 0);
            String token = extras.getString(TOKEN_EXTRA_NAME, "");
            String roomId = extras.getString(ROOM_EXTRA_NAME, "");

            mVidyoConnector.connect(
                    host, token, displayName.toString(), roomId, this
            );

            mLogger.Log("VidyoConnectorConnect status = " + (mVidyoConnectorState == VidyoConnectorState.Connecting));
        } else {
            // The user is either connected to a resource or is in the process of connecting to a resource;
            // Call VidyoConnectorDisconnect to either disconnect or abort the connection attempt.
            this.changeState(VidyoConnectorState.Disconnecting);
            mVidyoConnector.disconnect();
        }
    }

    // Toggle visibility of the toolbar
    @Override
    public void onVideoFrameClicked() {
        if (mVidyoConnectorState == VidyoConnectorState.Connected) {
            if (mToolbarLayout.getVisibility() == View.VISIBLE) {
                mToolbarLayout.setVisibility(View.INVISIBLE);
            } else {
                mToolbarLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    // Handle successful connection.
    @Override
    public void onSuccess() {
        mLogger.Log("onSuccess: successfully connected.");
        this.changeState(VidyoConnectorState.Connected);
    }

    /*
     *  Connector Events
     */

    // Handle attempted connection failure.
    @Override
    public void onFailure(Connector.ConnectorFailReason reason) {
        mLogger.Log("onFailure: connection attempt failed, reason = " + reason.toString());

        setResult(RESULT_OK,
                new Intent().putExtra(EXTRA_NAME, reason.toString()));
        finish();
        this.changeState(VidyoConnectorState.Failure);
    }

    // Handle an existing session being disconnected.
    @Override
    public void onDisconnected(Connector.ConnectorDisconnectReason reason) {
        if (reason == Connector.ConnectorDisconnectReason.VIDYO_CONNECTORDISCONNECTREASON_Disconnected) {
            mLogger.Log("onDisconnected: successfully disconnected, reason = " + reason.toString());
            this.changeState(VidyoConnectorState.Disconnected);
        } else {
            mLogger.Log("onDisconnected: unexpected disconnection, reason = " + reason.toString());
            this.changeState(VidyoConnectorState.DisconnectedUnexpected);
        }
        finish();
    }

    // Handle local camera events.
    @Override
    public void onLocalCameraAdded(LocalCamera localCamera) {
        mLogger.Log("onLocalCameraAdded: " + localCamera.getName());
    }

    @Override
    public void onLocalCameraRemoved(LocalCamera localCamera) {
        mLogger.Log("onLocalCameraRemoved: " + localCamera.getName());
    }

    @Override
    public void onLocalCameraSelected(LocalCamera localCamera) {
        mLogger.Log("onLocalCameraSelected: " + (localCamera == null ? "none" : localCamera.getName()));

        // If a camera is selected, then update mLastSelectedCamera.
        if (localCamera != null) {
            mLastSelectedCamera = localCamera;
        }
    }

    @Override
    public void onLocalCameraStateUpdated(LocalCamera localCamera, Device.DeviceState state) {
        mLogger.Log("onLocalCameraStateUpdated: name=" + localCamera.getName() + " state=" + state);
    }

    // Handle a message being logged.
    @Override
    public void onLog(LogRecord logRecord) {
        // No need to log to console here, since that is implicitly done when calling registerLogEventListener.
    }

    // Handle network interface events
    @Override
    public void onNetworkInterfaceAdded(NetworkInterface vidyoNetworkInterface) {
        mLogger.Log("onNetworkInterfaceAdded: name=" + vidyoNetworkInterface.getName() + " address=" + vidyoNetworkInterface.getAddress() + " type=" + vidyoNetworkInterface.getType() + " family=" + vidyoNetworkInterface.getFamily());
    }

    @Override
    public void onNetworkInterfaceRemoved(NetworkInterface vidyoNetworkInterface) {
        mLogger.Log("onNetworkInterfaceRemoved: name=" + vidyoNetworkInterface.getName() + " address=" + vidyoNetworkInterface.getAddress() + " type=" + vidyoNetworkInterface.getType() + " family=" + vidyoNetworkInterface.getFamily());
    }

    @Override
    public void onNetworkInterfaceSelected(NetworkInterface vidyoNetworkInterface, NetworkInterface.NetworkInterfaceTransportType vidyoNetworkInterfaceTransportType) {
        mLogger.Log("onNetworkInterfaceSelected: name=" + vidyoNetworkInterface.getName() + " address=" + vidyoNetworkInterface.getAddress() + " type=" + vidyoNetworkInterface.getType() + " family=" + vidyoNetworkInterface.getFamily());
    }

    @Override
    public void onNetworkInterfaceStateUpdated(NetworkInterface vidyoNetworkInterface, NetworkInterface.NetworkInterfaceState vidyoNetworkInterfaceState) {
        mLogger.Log("onNetworkInterfaceStateUpdated: name=" + vidyoNetworkInterface.getName() + " address=" + vidyoNetworkInterface.getAddress() + " type=" + vidyoNetworkInterface.getType() + " family=" + vidyoNetworkInterface.getFamily() + " state=" + vidyoNetworkInterfaceState);
    }
}
