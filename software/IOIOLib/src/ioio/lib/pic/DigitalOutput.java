package ioio.lib.pic;

import android.util.Log;

import ioio.lib.DigitalOutputMode;
import ioio.lib.IOIOException.ConnectionLostException;
import ioio.lib.IOIOException.InvalidOperationException;
import ioio.lib.IOIOException.InvalidStateException;
import ioio.lib.Output;

/**
 * Represent and manage digital output pins on the IOIO.
 *
 * TODO(TF): implement and add PinChangeListener
 * TODO(arshan): DigitalIO class that supports changing mode while in use.
 *
 * @author arshan
 */
public class DigitalOutput extends IOIOPin implements IOIOPacketListener, Output<Boolean> {

	public static final int SOURCE = 0;
	public static final int SINK = 1;

	IOIOImpl ioio;

	// Only true when we are confirmed active from the IOIO
	// looks like we can Q requests anyway as they are in order.
	boolean active = false;

	// Keep a local version of the state, not sure its necessary.
	// TODO(TF): should we set changeNotify on the pin and only update this on set from the IOIO?
	Boolean shadowState = false;

	// cache most used packets
	public final IOIOPacket setHi;
	public final IOIOPacket setLo;

	DigitalOutput(IOIOImpl ioio, PacketFramerRegistry framerRegistry, int pin, DigitalOutputMode mode, boolean startValue)
	throws ConnectionLostException, InvalidOperationException {
		super(pin);
		this.shadowState = startValue;
		this.ioio = ioio;
		ioio.reservePin(pin);
		framerRegistry.registerFramer(Constants.SET_OUTPUT, SET_DIGITAL_OUTPUT_PACKET_FRAMER);

		setHi = new IOIOPacket(Constants.SET_VALUE, new byte[]{(byte)(pin<<2|1)});
		setLo = new IOIOPacket(Constants.SET_VALUE, new byte[]{(byte)(pin<<2)});

		ioio.registerListener(this);
		init(mode);
	}

	private void init(DigitalOutputMode mode) throws ConnectionLostException {
		// TODO(arshan): does this need a sanity check?
		IOIOPacket request_output =
			new IOIOPacket(
			  Constants.SET_OUTPUT,
			  new byte[]{(byte) (pin << 2
					  | (shadowState?1:0) << 1
					  | (DigitalOutputMode.OPEN_DRAIN.equals(mode) ? 1:0))}
			);
		ioio.sendPacket(request_output);
	}

	@Override
    public void write(Boolean val) throws ConnectionLostException, InvalidStateException {
       if (isInvalid()) {
            throw Constants.INVALID_STATE_EXCEPTION;
        }

		if (!active) {
			// TODO(arshan): need a policy for this, not likely to come up, but ...
			// throw new IOException("output not yet active");
			// or maybe it will come up
			Log.i("IOIO output", "using digital output before confirmation");
		}

		if (val != shadowState) {
			shadowState = val;
			if (val) {
				Log.i("IOIO output", "pin " + pin + " is set high");
				ioio.sendPacket(setHi);
			}
			else {
				Log.i("IOIO output", "pin " + pin + " is set low");
				ioio.sendPacket(setLo);
			}

		}
	}

	@Override
    public Boolean getLastWrittenValue() throws InvalidStateException {
        if (isInvalid()) {
            throw Constants.INVALID_STATE_EXCEPTION;
        }
		return shadowState;
	}

	@Override
    public void handlePacket(IOIOPacket packet) {
		switch (packet.message) {
			case Constants.SET_OUTPUT:
				active = true;
				Log.i("IOIO","pin " + pin + " set as output");
		}
	}

    @Override
    public void close() {
        ioio.releasePin(pin);
        // TODO(TF): Implement this
    }

    private static final PacketFramer SET_DIGITAL_OUTPUT_PACKET_FRAMER =
        PacketFramers.getNBytePacketFramerFor(Constants.SET_OUTPUT, 1);
}