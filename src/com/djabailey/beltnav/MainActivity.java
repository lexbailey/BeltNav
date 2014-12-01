package com.djabailey.beltnav; //Name of this Java package.

//Important java libraries needed for this app.
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
//These imports are for various android libraries.
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.djabailey.beltnav.BeltThread.BeltThreadCallback;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

//define an activity (part of an app) that derives from the activity super class.
public class MainActivity extends FragmentActivity {

	// Definitions of variables to help with bluetoothing.
	private BluetoothAdapter mBluetoothAdapter = null;
	private BluetoothSocket btSocket = null;
	private OutputStream outStream = null;
	private InputStream inStream = null;
	private BluetoothDevice device = null;

	//map stuff
	private GoogleMap mMap;
	private LatLng SelectedLocation;
	private Marker BusStop, myPos, myRot;
	
	// BeltThread is a class that handles communication with the Belt. This is
	// a variable for our instance of it.
	private BeltThread gt;
	
	//any old number for request code
	public static final int ID_GETDEST = 123;

	// Unique identifier for bluetooth SPP.
	private static final UUID MY_UUID = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");

	// This is a tag that is used only for debugging purposes.
	protected static final String TAG = "BeltNav";

	//our buttons
	private Button btnConnect;
	private Button btnDisConnect;
	private Button btnSetDest;
	private Button btnSetNorth;
	
	private TextView tvData;
	
	String sats, dist, heading;

	// These variables are for keeping track of the current connection (if any).
	private boolean connected = false;
	private String connectionAddress = "";

	private String output;
	
	public void updateText(){
		String text = "Sats: " + sats + " Dist: " + dist + "m Heading: "+heading;
		String text2 = "Satellites: " + sats + "\nDistance: " + dist + "m\nHeading: "+heading;
		tvData.setText(text);
		PebbleDictionary data = new PebbleDictionary();
        data.addString(1234, text2);
        PebbleKit.sendDataToPebble(getApplicationContext(), UUID.fromString("0f08a738-2ee1-4506-a130-1122d0f632d5"), data);
	}
	
	// This is our definition and instance of the interface for the Belt
	// thread.
	BeltThreadCallback cb = new BeltThreadCallback() {

		@Override
		public void distance(final String v) {
			runOnUiThread(new Runnable() {
				public void run() {
					dist = v;
					updateText();
				}
			});
		}

		@Override
		public void heading(final String h) {
			runOnUiThread(new Runnable() {
				public void run() {
					heading = h;
					updateText();
					myPos.setRotation(
							(Float.parseFloat(h))+180 % 360
					);
				}
			});
		}

		@Override
		public void satelites(final String s) {
			runOnUiThread(new Runnable() {
				public void run() {
					sats = s;
					updateText();
				}
			});
		}

		@Override
		public void position(final LatLng pos) {
			runOnUiThread(new Runnable() {
				public void run() {
					myPos.setPosition(pos);
					myRot.setPosition(pos);
				}
			});
			
		}

		@Override
		public void facing(final String h) {
			runOnUiThread(new Runnable() {
				public void run() {
					heading = h;
					updateText();
					myRot.setRotation(
							(Float.parseFloat(h))+180 % 360
					);
				}
			});
		}
	};

	// Called when the app is first created to initialise all of the Buttons,
	// TextViews, ToggleButtons and RadioButtons.
	private void initButtons() {
		// Each button is bound to an object and then assigned an
		// OnClickListener (a function that is called when it is clicked).
		btnConnect = (Button) findViewById(R.id.btnConnect);
		btnConnect.setOnClickListener(connectClick);
		btnDisConnect = (Button) findViewById(R.id.btnDisconnect);
		btnDisConnect.setOnClickListener(disconnectClick);
		btnSetDest = (Button) findViewById(R.id.btnSelectDest);
		btnSetDest.setOnClickListener(selectDestClick);
		tvData = (TextView) findViewById(R.id.tvData);
		
		btnSetNorth = (Button) findViewById(R.id.btnSetNorth);
		btnSetNorth.setOnClickListener(setNorthClick);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// This function is called when the activity first starts.
		// This function must call the same function that it overrides in the
		// superclass.
		Log.i(TAG, "Create!");
		super.onCreate(savedInstanceState);
		// This loads the layout file called "activity_main" and sets it as the
		// current content view (it puts the UI on the screen).
		setContentView(R.layout.activity_main);
		// Initialise the UI elements.
		initButtons();
		setUpMapIfNeeded();
		sats = "0";
		dist = "0";
		heading = "0";
		updateText();
	}

	@Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }
    
    private void setUpMap() {
        BusStop = mMap.addMarker(new MarkerOptions().position(new LatLng(54, -3)).title("Destination"));
        myRot= mMap.addMarker(new MarkerOptions()
          .position(new LatLng(0, 0))
          .title("My Heading")
          .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
          .flat(true)
          .rotation(0.0f));
        myPos= mMap.addMarker(new MarkerOptions()
        .position(new LatLng(0, 0))
        .title("Current Location")
        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        .flat(true)
        .rotation(0.0f));
        SelectedLocation = new LatLng(54,-3);
        mMap.setOnMapClickListener(new OnMapClickListener() {
			
			@Override
			public void onMapClick(LatLng arg0) {
				SelectedLocation = new LatLng(arg0.latitude,arg0.longitude);
				BusStop.setPosition(arg0);
			}
		});
        mMap.setIndoorEnabled(false);
		
    }
	
	@Override
	public void onSaveInstanceState(Bundle state) {
		Log.i(TAG, "Save instance state");
		// This function is called when the app must stop temporarily to
		// re-initialise so that it can save the current state ready to load it
		// again when it is ready.
		// This is usually called just before the app rotates.

		// Call superclass function. (This is unlikely to be needed but is here for
		// good measure.)
		super.onSaveInstanceState(state);

		// The State object is for storing the current state
		// Add a boolean value to the saved state to remember if we had a
		// connection.
		state.putBoolean("connected", connected && (gt != null));
		// if we did have a connection, save some more information about the
		// connection.
		if (connected && (gt != null)) {
			state.putString("connectionAddress", connectionAddress);
		}

		// Before a rotation occurs, we must dispose of the current connection
		// to re-initialise it after rotation
		killConnection();
	}

	// This is a function that will stop the current connection.
	public void killConnection() {
		try {
			// Exit the Belt thread loop.
			gt.exitLoop();
			// Close and deallocate the input and output streams.
			inStream.close();
			inStream = null;
			outStream.close();
			outStream = null;
			// Close and deallocate the bluetooth socket
			btSocket.close();
			btSocket = null;
			// Deallocate other objects that are no longer needed
			device = null;
			mBluetoothAdapter = null;
			gt = null;
			// Force the garbage collector to clear up the mess we just made.
			// :-) [Oh Java, HaHaHa... at least you'll never have a fatal memory
			// leak.]
			java.lang.Runtime.getRuntime().gc();
		} catch (Exception e) {
			// We aren't bothered if the disconnect wasn't particularly graceful
			// so there isn't much point printing the errors here.
		}
	}

	@Override
	public void onRestoreInstanceState(Bundle state) {
		// This is the function that restores a saved state. This is called when
		// the app reinitialises after a rotation.
		// Call the superclass funtion.
		Log.i(TAG, "Restore instance state1");
		super.onRestoreInstanceState(state);
		
		Log.i(TAG, "Restore instance state2");

		// Check the previous state to see if there was a connection.
		if (state.getBoolean("connected", false)) {
			try {
				// if there was a connection, the first thing to do is wait 500
				// milliseconds. This is because the bluetooth module in the
				// Belt needs a little time to reinitialise itself after a
				// disconnect. Half a second is far more than enough.
				// Sleep for half a second.
				Thread.sleep(500);
			} catch (InterruptedException e1) {
				// Just in case the app can't successfully do nothing for half a
				// second. :-)
				e1.printStackTrace();
			}

			// Get the address of the last device to which we were connected.
			// This is the MAC address, it should be unique to each Belt
			// (although it isn't guaranteed it's good-enough).
			String address = state.getString("connectionAddress");

			// Get the default bluetooth adapter.
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

			// Since there was a connection before this was called, it is safe
			// to assume that bluetooth is available and enabled.

			// Get a device object from the MAC address.
			BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

			try {
				// Get a socket from the device object.
				btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
				// Just in case we are in discovery mode, stop!
				mBluetoothAdapter.cancelDiscovery();

				try {
					// Try to connect the socket.
					btSocket.connect();

					try {
						// Create the data streams so we can talk to the Belt.
						outStream = btSocket.getOutputStream();
						inStream = btSocket.getInputStream();

						// create a BeltThread to handle comms.
						gt = new BeltThread();
						gt.init(inStream, cb);
						gt.start();


						try {
							// Retrieve the units just to make sure that it
							// works. If it doesn't, this will jump to the catch
							// block and so avoid semi-initialised connections.
							outStream.write('U');

							// If everything is fine, hooray, set connection
							// information.
							connected = true;
							connectionAddress = device.getAddress();

							// Change the connection buttons to only enable the
							// disconnect button.
							btnDisConnect.setEnabled(true);
							btnSetDest.setEnabled(true);
							btnSetNorth.setEnabled(true);
							btnConnect.setEnabled(false);
							
						} catch (IOException e) {
							// If there is a problem, let the user know. Tron:
							// "I fight for the users." :-)
							Toast.makeText(getBaseContext(),
									"Write error: " + device.getAddress(),
									Toast.LENGTH_LONG).show();
						}
					} catch (IOException e) {
						// This is unlikely to happen, if however it does, show
						// a debug message
						Toast.makeText(
								getBaseContext(),
								"Listener thread not initialised, stream creation failure?: "
										+ device.getAddress(),
								Toast.LENGTH_LONG).show();
					}
				} catch (IOException e) {
					try {
						btSocket.close();
						// This is where failure occurs under normal
						// circumstances where the Belt is switched off
						// (unreachable)
						// no error messages produced here as this is a normal
						// place to end up.
					} catch (IOException e2) {
						// Something is really badly wrong if the program gets
						// here, it is however still theoretically possible and
						// so a message will be shown.
						Toast.makeText(
								getBaseContext(),
								"Absolute Catastrophe! (Failed to close a failed socket connection): "
										+ device.getAddress(),
								Toast.LENGTH_LONG).show();
					}
				}

			} catch (IOException e) {
				// Generic error that shows if the connection fails quickly.
				Toast.makeText(getBaseContext(),
						"Unable to create connection: " + device.getAddress(),
						Toast.LENGTH_LONG).show();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Function for creating the menu (what you see when you press the menu
		// button in the top right corner or on the keypad).
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	// This is very similar to the code used in the OnRestoreInstanceState
	// function, only the differences are commented.
	private OnClickListener connectClick = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
			// Variable to state if iteration is done.
			boolean doneItter = false;

			// If there is still a BeltThread, kill it.
			if (gt != null) {
				killConnection();
			}

			// Connect to the Belt (VIA Bluetooth)
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

			// If no bluetooth is available, forget the whole thing the device
			// running this app is incompatible.
			if (mBluetoothAdapter == null) {
				Toast.makeText(getBaseContext(), "Bluetooth is not available.",
						Toast.LENGTH_LONG).show();
				return;
			}

			// Ensure that the bluetooth is enabled before continuing
			if (!mBluetoothAdapter.isEnabled()) {
				mBluetoothAdapter.enable();
				try {
					// Give the bluetooth a little time to init. (I know this is
					// dodgy but if it fails it only means you have to press the
					// button again.)
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			// Get an iterator object from the bluetooth adapter that will
			// allow us to iterate through all paired devices.
			Iterator<BluetoothDevice> itr = mBluetoothAdapter
					.getBondedDevices().iterator();

			// Loop through ALL devices that are PAIRED.
			while (itr.hasNext() && !doneItter) {

				// Get the next device in the list.
				BluetoothDevice btd = itr.next();

				// Check to see if it has the correct name, if it does, it is a
				// wayfinder Belt, if not then ignore it as it's just another
				// bluetooth device.
				if (btd.getName().toLowerCase().equals("linvor")) {

					// Put the Belt device into the device variable
					device = btd;

					// Mostly same as before...
					try {
						btSocket = device
								.createRfcommSocketToServiceRecord(MY_UUID);
						mBluetoothAdapter.cancelDiscovery();

						try {
							btSocket.connect();

							try {
								outStream = btSocket.getOutputStream();
								inStream = btSocket.getInputStream();
								gt = new BeltThread();
								gt.init(inStream, cb);
								gt.start();
								String message = "U";
								byte[] msgBuffer = message.getBytes();
								try {
									outStream.write(msgBuffer);

									// We can now stop iterating as the Belt
									// is connected.
									doneItter = true;
									connected = true;
									btnDisConnect.setEnabled(true);
									btnSetDest.setEnabled(true);
									btnSetNorth.setEnabled(true);
									btnConnect.setEnabled(false);
									connectionAddress = device.getAddress();
								} catch (IOException e) {
									Toast.makeText(
											getBaseContext(),
											"Write error: "
													+ device.getAddress(),
											Toast.LENGTH_LONG).show();
								}
							} catch (IOException e) {
								Toast.makeText(
										getBaseContext(),
										"Listener thread not initialised, stream creation failure?: "
												+ device.getAddress(),
										Toast.LENGTH_LONG).show();
							}

						} catch (IOException e) {
							try {
								btSocket.close();

							} catch (IOException e2) {
								Toast.makeText(
										getBaseContext(),
										"Absolute Catastrophe! (Failed to close a failed socket connection): "
												+ device.getAddress(),
										Toast.LENGTH_LONG).show();
							}
						}

					} catch (IOException e) {
						Toast.makeText(
								getBaseContext(),
								"Unable to create connection: "
										+ device.getAddress(),
								Toast.LENGTH_LONG).show();
					}
				}
			}

			if (device == null) {
				Toast.makeText(getBaseContext(),
						"No paired device with name 'linvor'.",
						Toast.LENGTH_LONG).show();
			} else {
				if (gt != null) {
					Toast.makeText(getBaseContext(),
							"Connected: " + device.getAddress(),
							Toast.LENGTH_LONG).show();
				} else {
					Toast.makeText(getBaseContext(),
							"No reachable devices found.", Toast.LENGTH_LONG)
							.show();
					btnDisConnect.setEnabled(false);
					btnSetDest.setEnabled(false);
					btnSetNorth.setEnabled(false);
					btnConnect.setEnabled(true);
				}
			}
		}

	};

	private OnClickListener disconnectClick = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
			// If this button is pressed, simply kill the connection and reset
			// everything.
			killConnection();
			connected = false;
			connectionAddress = "";

			// Set the connect button to be enabled.
			btnDisConnect.setEnabled(false);
			btnSetDest.setEnabled(false);
			btnSetNorth.setEnabled(false);
			btnConnect.setEnabled(true);
		}
	};
	
	private OnClickListener selectDestClick = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
			output = "A"; 
			output += Double.toString(SelectedLocation.latitude);
			output += ";";
			output += "O"; 
			output += Double.toString(SelectedLocation.longitude);
			output += ";";
			
			if (outStream != null){
				try {
					outStream.write(output.getBytes());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			else{
				Toast.makeText(getBaseContext(),
						"no output stream?", Toast.LENGTH_LONG)
						.show(); 
			}
		}
	};
	
	private OnClickListener setNorthClick = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
			if (outStream != null){
				try {
					outStream.write("N".getBytes());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			else{
				Toast.makeText(getBaseContext(),
						"no output stream?", Toast.LENGTH_LONG)
						.show(); 
			}
		}
	};
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
		Log.i(TAG, "Activity result");
	}
	
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item){
		switch (item.getItemId()){
		case R.id.menu_satellite:
			mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
			return true;
		case R.id.menu_map:
			mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
			return true;	
		}
		return false;
	}

}
