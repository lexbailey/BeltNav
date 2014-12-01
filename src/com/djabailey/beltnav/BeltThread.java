package com.djabailey.beltnav; //This is part of the same package in which the MainActivity class is. 

//Some essential imports.
import java.io.IOException;
import java.io.InputStream;

import com.google.android.gms.maps.model.LatLng;

//This is the GloveThread class, it handles communications with the glove. It receives and interprets what the glove sends although it doesn't send anything to the glove. Writing is instead done on the main thread to make synchronisation easier, this class makes requests to the main thread to send stuff.
//The class derives from thread, this is because it needs to be able to run continuously and constantly check for incoming data.
public class BeltThread extends Thread {

	// Flag set when thread is running.
	private boolean running = false;

	// This class needs to keep a copy of the input stream.
	private InputStream mis;

	private double latitude;
	private double longitude;
	
	// This is definition of the callback interface for communicating with the
	// main thread.
	interface BeltThreadCallback {
		
		void distance(String v);
		void heading(String h);
		void facing(String h);
		void satelites(String s);
		void position(LatLng pos);
	}

	// This is the callback object for communicating with the main thread.
	private BeltThreadCallback gtcb;

	// This is the main function loop. It is called repeatedly by
	// GloveThread.run()
	private void loopFunc() {
		try {
			// The function only does useful things when there is data available
			// to interpret.
			if (mis.available() > 0) {
				// The last received byte is stored in this variable
				int in = mis.read();

				// This byte decides what to do.
				switch (in) {
				case 'H':
				case 'F':
				case 'D':
				case 'S':
					
				case 'A':
				case 'O':	
					// Here we go! :-) [This function is a little bit
					// complicated.]

					// A 'D' or 'H' byte is sent just before a distance or heading, then a pipe,
					// then the reading then another pipe. So the first thing to
					// do is move along until we find a pipe (this shouldn't be
					// long as it should be the next byte, spaces between are
					// allowed however and so this is essential.)

					// Flag to say that we are waiting for a pipe.
					boolean waitpipe = true;

					// While we are waiting for a pipe...
					while (waitpipe) {
						// If another byte is available.
						if (mis.available() > 0) {
							// Get the next byte.
							char c = (char) mis.read();

							// If the character is a pipe or if the command was
							// interrupted and ended prematurely with a carriage
							// return or line feed, then we are no longer
							// waiting for a pipe. The flag is reset and the
							// loop ends.
							if ((c == '|') || (c == 13) || (c == 10)) {
								waitpipe = false;
							}
						}
					}

					// This is a buffer to store the value that we are about to
					// read in.
					String buffer = "";

					// A flag to say that we are buffering.
					boolean buffering = true;

					// while we are still buffering...
					while (buffering) {

						// Loop through all the available bytes.
						while ((mis.available() > 0) && buffering) {
							// Get the next byte into a variable.
							char val = (char) mis.read();

							// If it is a pipe, that marks the end of the
							// reading, stop buffering
							if (val == '|') {
								buffering = false;
							} else {
								// Was the command interrupted?
								if ((val == 13) || (val == 10)) {
									// awww shoot, we missed it.
									// Cancel everything.
									buffer = "";
									buffering = false;
								} else {
									// If we got another byte, add it to the end
									// of the string.
									buffer = buffer.concat(Character
											.toString(val));
								}
							}

						}
					}
					// remove any dead space
					buffer = buffer.trim();
					// if the buffer isn't empty then send it to the main
					// thread.
					if (!buffer.equals("")) {
						if (in == 'D') {gtcb.distance(buffer);}
						if (in == 'H') {gtcb.heading(buffer);}
						if (in == 'F') {gtcb.facing(buffer);}	
						if (in == 'S') {gtcb.satelites(buffer);}
						
						if (in == 'A') {
							try{
							latitude = Double.parseDouble(buffer);
							gtcb.position(new LatLng(latitude, longitude));
							}
							catch(java.lang.NumberFormatException e){
								//meh!
							}
						}
						if (in == 'O') {
							try{
								longitude = Double.parseDouble(buffer);
								gtcb.position(new LatLng(latitude, longitude));
						    }
							catch(java.lang.NumberFormatException e){
								//meh!
							}
							
						}
					}
					break;

				default:
					break;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			// If there is an error then we should stop running.
			running = false;
		}
	}

	public void init(InputStream is, BeltThreadCallback cb) {
		// this function initialises the callback and input stream, allowing the
		// glove to talk to this thread and this thread to talk to the main
		// thread.

		// keep a copy of the InputStream.
		mis = is;

		// Keep a copy of the GloveThreadCallback.
		gtcb = cb;
	}

	@Override
	public void run() {
		// We are now running, set running to true.
		running = true;

		// As long as we are still running, call loopFunc().
		while (running) {
			loopFunc();
		}

	}

	public void exitLoop() {
		// External request to exit. (From the main thread.)
		running = false;
	}
}