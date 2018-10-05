package org.ciyam.at;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MachineState {

	/** Header bytes length */
	public static final int HEADER_LENGTH = 2 + 2 + 2 + 2 + 2 + 2; // version reserved code data call-stack user-stack

	/** Size of one OpCode - typically 1 byte (byte) */
	public static final int OPCODE_SIZE = 1;

	/** Size of one FunctionCode - typically 2 bytes (short) */
	public static final int FUNCTIONCODE_SIZE = 2;

	/** Size of value stored in data segment - typically 8 bytes (long) */
	public static final int VALUE_SIZE = 8;

	/** Size of code-address - typically 4 bytes (int) */
	public static final int ADDRESS_SIZE = 4;

	/** Maximum value for an address in the code segment */
	public static final int MAX_CODE_ADDRESS = 0x1fffffff;

	private static class VersionedConstants {
		/** Bytes per code page */
		public final int CODE_PAGE_SIZE;
		/** Bytes per data page */
		public final int DATA_PAGE_SIZE;
		/** Bytes per call stack page */
		public final int CALL_STACK_PAGE_SIZE;
		/** Bytes per user stack page */
		public final int USER_STACK_PAGE_SIZE;

		public VersionedConstants(int codePageSize, int dataPageSize, int callStackPageSize, int userStackPageSize) {
			CODE_PAGE_SIZE = codePageSize;
			DATA_PAGE_SIZE = dataPageSize;
			CALL_STACK_PAGE_SIZE = callStackPageSize;
			USER_STACK_PAGE_SIZE = userStackPageSize;
		}
	}

	/** Map of constants (e.g. CODE_PAGE_SIZE) by AT version */
	private static final Map<Short, VersionedConstants> VERSIONED_CONSTANTS = new HashMap<Short, VersionedConstants>();
	static {
		VERSIONED_CONSTANTS.put((short) 1, new VersionedConstants(256, 256, 256, 256));
		VERSIONED_CONSTANTS.put((short) 3, new VersionedConstants(OPCODE_SIZE, VALUE_SIZE, ADDRESS_SIZE, VALUE_SIZE));
	}

	// Set during construction
	public final short version;
	public final short reserved;
	public final short numCodePages;
	public final short numDataPages;
	public final short numCallStackPages;
	public final short numUserStackPages;

	private final byte[] headerBytes;

	/** Constants set in effect */
	private final VersionedConstants constants;

	/** Program Counter: offset into code to point of current execution */
	private int programCounter;

	/** Initial program counter value to use on next block after current block's execution has stopped. 0 by default */
	private int onStopAddress;

	/** Program counter value to use if an error occurs during execution. If null upon error, refund all funds to creator and finish */
	private Integer onErrorAddress;

	/** Execution for current block has stopped. Continue at current program counter on next/specific block */
	private boolean isSleeping;

	/** Block height required to wake from sleeping, or null if not in use */
	private Integer sleepUntilHeight;

	/** Execution for current block has stopped. Restart at onStopAddress on next block */
	private boolean isStopped;

	/** Execution stopped due to lack of funds for processing. Restart at onStopAddress if frozenBalance increases */
	private boolean isFrozen;

	/** Balance at which there were not enough funds, or null if not in use */
	private Long frozenBalance;

	/** Execution permanently stopped */
	private boolean isFinished;

	/** Execution permanently stopped due to fatal error */
	private boolean hadFatalError;

	// 256-bit pseudo-registers
	// NOTE: These are package-scope to allow easy access/operations in FunctionCodes.
	// Outside classes (e.g. unit tests) can use getters
	/* package */ long a1;
	/* package */ long a2;
	/* package */ long a3;
	/* package */ long a4;

	/* package */ long b1;
	/* package */ long b2;
	/* package */ long b3;
	/* package */ long b4;

	private int currentBlockHeight;

	/** Number of opcodes processed this execution */
	private int steps;

	private API api;
	private LoggerInterface logger;

	// NOTE: These are package-scope to allow easy access/operations in Opcode/FunctionCode.
	/* package */ ByteBuffer codeByteBuffer;
	/* package */ ByteBuffer dataByteBuffer;
	/* package */ ByteBuffer callStackByteBuffer;
	/* package */ ByteBuffer userStackByteBuffer;

	// Constructors

	/** For internal use when recreating a machine state */
	private MachineState(API api, LoggerInterface logger, byte[] headerBytes) {
		if (headerBytes.length != HEADER_LENGTH)
			throw new IllegalArgumentException("headerBytes length " + headerBytes.length + " incorrect, expected " + HEADER_LENGTH);

		this.headerBytes = headerBytes;

		// Parsing header bytes
		ByteBuffer byteBuffer = ByteBuffer.wrap(this.headerBytes);
		byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

		this.version = byteBuffer.getShort();
		if (this.version < 1)
			throw new IllegalArgumentException("Version must be >= 0");

		this.constants = VERSIONED_CONSTANTS.get(this.version);
		if (this.constants == null)
			throw new IllegalArgumentException("Version " + this.version + " unsupported");

		this.reserved = byteBuffer.getShort();

		this.numCodePages = byteBuffer.getShort();
		if (this.numCodePages < 1)
			throw new IllegalArgumentException("Number of code pages must be > 0");

		this.numDataPages = byteBuffer.getShort();
		if (this.numDataPages < 1)
			throw new IllegalArgumentException("Number of data pages must be > 0");

		this.numCallStackPages = byteBuffer.getShort();
		if (this.numCallStackPages < 0)
			throw new IllegalArgumentException("Number of call stack pages must be >= 0");

		this.numUserStackPages = byteBuffer.getShort();
		if (this.numUserStackPages < 0)
			throw new IllegalArgumentException("Number of user stack pages must be >= 0");

		// Header OK - set up code and data buffers
		this.codeByteBuffer = ByteBuffer.allocate(this.numCodePages * this.constants.CODE_PAGE_SIZE).order(ByteOrder.LITTLE_ENDIAN);
		this.dataByteBuffer = ByteBuffer.allocate(this.numDataPages * this.constants.DATA_PAGE_SIZE).order(ByteOrder.LITTLE_ENDIAN);

		// Set up stacks
		this.callStackByteBuffer = ByteBuffer.allocate(this.numCallStackPages * this.constants.CALL_STACK_PAGE_SIZE).order(ByteOrder.LITTLE_ENDIAN);
		this.callStackByteBuffer.position(this.callStackByteBuffer.limit()); // Downward-growing stack, so start at the end

		this.userStackByteBuffer = ByteBuffer.allocate(this.numUserStackPages * this.constants.USER_STACK_PAGE_SIZE).order(ByteOrder.LITTLE_ENDIAN);
		this.userStackByteBuffer.position(this.userStackByteBuffer.limit()); // Downward-growing stack, so start at the end

		this.api = api;
		this.currentBlockHeight = 0;
		this.steps = 0;
		this.logger = logger;
	}

	/** For creating a new machine state */
	public MachineState(byte[] creationBytes) {
		this(null, null, Arrays.copyOfRange(creationBytes, 0, HEADER_LENGTH));

		int expectedLength = HEADER_LENGTH + this.numCodePages * this.constants.CODE_PAGE_SIZE + this.numDataPages + this.constants.DATA_PAGE_SIZE;
		if (creationBytes.length != expectedLength)
			throw new IllegalArgumentException("Creation bytes length does not match header values");

		System.arraycopy(creationBytes, HEADER_LENGTH, this.codeByteBuffer.array(), 0, this.numCodePages * this.constants.CODE_PAGE_SIZE);

		System.arraycopy(creationBytes, HEADER_LENGTH + this.numCodePages * this.constants.CODE_PAGE_SIZE, this.dataByteBuffer.array(), 0,
				this.numDataPages + this.constants.DATA_PAGE_SIZE);

		commonFinalConstruction();
	}

	/** For creating a new machine state */
	public MachineState(API api, LoggerInterface logger, byte[] headerBytes, byte[] codeBytes, byte[] dataBytes) {
		this(api, logger, headerBytes);

		if (codeBytes.length > this.numCodePages * this.constants.CODE_PAGE_SIZE)
			throw new IllegalArgumentException("Number of code pages too small to hold code bytes");

		if (dataBytes.length > this.numDataPages * this.constants.DATA_PAGE_SIZE)
			throw new IllegalArgumentException("Number of data pages too small to hold data bytes");

		System.arraycopy(codeBytes, 0, this.codeByteBuffer.array(), 0, codeBytes.length);

		System.arraycopy(dataBytes, 0, this.dataByteBuffer.array(), 0, dataBytes.length);

		commonFinalConstruction();
	}

	private void commonFinalConstruction() {
		this.programCounter = 0;
		this.onStopAddress = 0;
		this.onErrorAddress = null;
		this.isSleeping = false;
		this.sleepUntilHeight = null;
		this.isStopped = false;
		this.isFrozen = false;
		this.frozenBalance = null;
		this.isFinished = false;
		this.hadFatalError = false;
	}

	// Getters / setters

	// NOTE: Many setters have package-scope (i.e. org.ciyam.at only) to allow changes
	// during execution but not by outside classes.

	public int getProgramCounter() {
		return this.programCounter;
	}

	public int getOnStopAddress() {
		return this.onStopAddress;
	}

	/* package */ void setOnStopAddress(int address) {
		this.onStopAddress = address;
	}

	public Integer getOnErrorAddress() {
		return this.onErrorAddress;
	}

	/* package */ void setOnErrorAddress(Integer address) {
		this.onErrorAddress = address;
	}

	public boolean getIsSleeping() {
		return this.isSleeping;
	}

	/* package */ void setIsSleeping(boolean isSleeping) {
		this.isSleeping = isSleeping;
	}

	public Integer getSleepUntilHeight() {
		return this.sleepUntilHeight;
	}

	/* package */ void setSleepUntilHeight(Integer address) {
		this.sleepUntilHeight = address;
	}

	public boolean getIsStopped() {
		return this.isStopped;
	}

	/* package */ void setIsStopped(boolean isStopped) {
		this.isStopped = isStopped;
	}

	public boolean getIsFrozen() {
		return this.isFrozen;
	}

	/* package */ void setIsFrozen(boolean isFrozen) {
		this.isFrozen = isFrozen;
	}

	public Long getFrozenBalance() {
		return this.frozenBalance;
	}

	/* package */ void setFrozenBalance(Long frozenBalance) {
		this.frozenBalance = frozenBalance;
	}

	public boolean getIsFinished() {
		return this.isFinished;
	}

	/* package */ void setIsFinished(boolean isFinished) {
		this.isFinished = isFinished;
	}

	public boolean getHadFatalError() {
		return this.hadFatalError;
	}

	/* package */ void setHadFatalError(boolean hadFatalError) {
		this.hadFatalError = hadFatalError;
	}

	// No corresponding setters due to package-scope - see above
	public long getA1() {
		return this.a1;
	}

	public long getA2() {
		return this.a2;
	}

	public long getA3() {
		return this.a3;
	}

	public long getA4() {
		return this.a4;
	}

	public long getB1() {
		return this.b1;
	}

	public long getB2() {
		return this.b2;
	}

	public long getB3() {
		return this.b3;
	}

	public long getB4() {
		return this.b4;
	}
	// End of package-scope pseudo-registers

	public int getCurrentBlockHeight() {
		return this.currentBlockHeight;
	}

	public int getSteps() {
		return this.steps;
	}

	public API getAPI() {
		return this.api;
	}

	public LoggerInterface getLogger() {
		return this.logger;
	}

	// Serialization

	/** For serializing a machine state */
	public byte[] toBytes() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			// Header first
			bytes.write(this.headerBytes);

			// Code
			bytes.write(this.codeByteBuffer.array());

			// Data
			bytes.write(this.dataByteBuffer.array());

			// Call stack length (32bit unsigned int)
			int callStackLength = this.callStackByteBuffer.limit() - this.callStackByteBuffer.position();
			bytes.write(toByteArray(callStackLength));
			// Call stack (only the bytes actually in use)
			bytes.write(this.callStackByteBuffer.array(), this.callStackByteBuffer.position(), callStackLength);

			// User stack length (32bit unsigned int)
			int userStackLength = this.userStackByteBuffer.limit() - this.userStackByteBuffer.position();
			bytes.write(toByteArray(userStackLength));
			// User stack (only the bytes actually in use)
			bytes.write(this.userStackByteBuffer.array(), this.userStackByteBuffer.position(), userStackLength);

			// Actual state
			bytes.write(toByteArray(this.programCounter));
			bytes.write(toByteArray(this.onStopAddress));

			// Various flags
			Flags flags = new Flags();
			flags.push(this.isSleeping);
			flags.push(this.isStopped);
			flags.push(this.isFinished);
			flags.push(this.hadFatalError);
			flags.push(this.isFrozen);

			flags.push(this.onErrorAddress != null); // has onErrorAddress?
			flags.push(this.sleepUntilHeight != null); // has sleepUntilHeight?
			flags.push(this.frozenBalance != null); // has frozenBalance?

			boolean hasNonZeroA = this.a1 != 0 || this.a2 != 0 || this.a3 != 0 || this.a4 != 0;
			flags.push(hasNonZeroA);

			boolean hasNonZeroB = this.b1 != 0 || this.b2 != 0 || this.b3 != 0 || this.b4 != 0;
			flags.push(hasNonZeroB);

			bytes.write(toByteArray(flags.intValue()));

			// Optional flag-indicated extra info in same order as above
			if (this.onErrorAddress != null)
				bytes.write(toByteArray(this.onErrorAddress));

			if (this.sleepUntilHeight != null)
				bytes.write(toByteArray(this.sleepUntilHeight));

			if (this.frozenBalance != null)
				bytes.write(toByteArray(this.frozenBalance));

			if (hasNonZeroA) {
				bytes.write(toByteArray(this.a1));
				bytes.write(toByteArray(this.a2));
				bytes.write(toByteArray(this.a3));
				bytes.write(toByteArray(this.a4));
			}

			if (hasNonZeroB) {
				bytes.write(toByteArray(this.b1));
				bytes.write(toByteArray(this.b2));
				bytes.write(toByteArray(this.b3));
				bytes.write(toByteArray(this.b4));
			}
		} catch (IOException e) {
			return null;
		}

		return bytes.toByteArray();
	}

	/** For restoring a previously serialized machine state */
	public static MachineState fromBytes(API api, LoggerInterface logger, byte[] bytes) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

		byte[] headerBytes = new byte[HEADER_LENGTH];
		byteBuffer.get(headerBytes);

		MachineState state = new MachineState(api, logger, headerBytes);

		byte[] codeBytes = new byte[state.codeByteBuffer.capacity()];
		byteBuffer.get(codeBytes);
		System.arraycopy(codeBytes, 0, state.codeByteBuffer.array(), 0, codeBytes.length);

		byte[] dataBytes = new byte[state.dataByteBuffer.capacity()];
		byteBuffer.get(dataBytes);
		System.arraycopy(dataBytes, 0, state.dataByteBuffer.array(), 0, dataBytes.length);

		int callStackLength = byteBuffer.getInt();
		byte[] callStackBytes = new byte[callStackLength];
		byteBuffer.get(callStackBytes);
		// Restore call stack pointer, and useful for copy below
		state.callStackByteBuffer.position(state.callStackByteBuffer.limit() - callStackLength);
		// Call stack grows downwards so copy to end
		System.arraycopy(callStackBytes, 0, state.callStackByteBuffer.array(), state.callStackByteBuffer.position(), callStackLength);

		int userStackLength = byteBuffer.getInt();
		byte[] userStackBytes = new byte[userStackLength];
		byteBuffer.get(userStackBytes);
		// Restore user stack pointer, and useful for copy below
		state.userStackByteBuffer.position(state.userStackByteBuffer.limit() - userStackLength);
		// User stack grows downwards so copy to end
		System.arraycopy(userStackBytes, 0, state.userStackByteBuffer.array(), state.userStackByteBuffer.position(), userStackLength);

		// Actual state
		state.programCounter = byteBuffer.getInt();
		state.onStopAddress = byteBuffer.getInt();

		// Various flags (reverse order to toBytes)
		Flags flags = state.new Flags(byteBuffer.getInt());
		boolean hasNonZeroB = flags.pop();
		boolean hasNonZeroA = flags.pop();
		boolean hasFrozenBalance = flags.pop();
		boolean hasSleepUntilHeight = flags.pop();
		boolean hasOnErrorAddress = flags.pop();

		state.isFrozen = flags.pop();
		state.hadFatalError = flags.pop();
		state.isFinished = flags.pop();
		state.isStopped = flags.pop();
		state.isSleeping = flags.pop();

		// Optional extras (same order as toBytes)
		if (hasOnErrorAddress)
			state.onErrorAddress = byteBuffer.getInt();

		if (hasSleepUntilHeight)
			state.sleepUntilHeight = byteBuffer.getInt();

		if (hasFrozenBalance)
			state.frozenBalance = byteBuffer.getLong();

		if (hasNonZeroA) {
			state.a1 = byteBuffer.getLong();
			state.a2 = byteBuffer.getLong();
			state.a3 = byteBuffer.getLong();
			state.a4 = byteBuffer.getLong();
		}

		if (hasNonZeroB) {
			state.b1 = byteBuffer.getLong();
			state.b2 = byteBuffer.getLong();
			state.b3 = byteBuffer.getLong();
			state.b4 = byteBuffer.getLong();
		}

		return state;
	}

	/** Class for pushing/popping boolean flags onto/from an int */
	private class Flags {
		private int flags;

		public Flags() {
			flags = 0;
		}

		public Flags(int value) {
			this.flags = value;
		}

		public void push(boolean flag) {
			flags <<= 1;
			flags |= flag ? 1 : 0;
		}

		public boolean pop() {
			boolean result = (flags & 1) != 0;
			flags >>>= 1;
			return result;
		}

		public int intValue() {
			return flags;
		}
	}

	/** Convert int to little-endian byte array */
	private byte[] toByteArray(int value) {
		return new byte[] { (byte) (value), (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24) };
	}

	/** Convert long to little-endian byte array */
	private byte[] toByteArray(long value) {
		return new byte[] { (byte) (value), (byte) (value >> 8), (byte) (value >> 16), (byte) (value >> 24), (byte) (value >> 32), (byte) (value >> 40),
				(byte) (value >> 48), (byte) (value >> 56) };
	}

	// Actual execution

	public void execute() {
		// Set byte buffer position using program counter
		codeByteBuffer.position(this.programCounter);

		// Reset for this round of execution
		this.isSleeping = false;
		this.sleepUntilHeight = null;
		this.isStopped = false;
		this.isFrozen = false;
		this.frozenBalance = null;
		this.steps = 0;
		this.currentBlockHeight = api.getCurrentBlockHeight();

		while (!this.isSleeping && !this.isStopped && !this.isFinished && !this.isFrozen) {
			byte rawOpCode = codeByteBuffer.get();
			OpCode nextOpCode = OpCode.valueOf(rawOpCode);

			try {
				if (nextOpCode == null)
					throw new IllegalOperationException("OpCode 0x" + String.format("%02x", rawOpCode) + " not recognised");

				this.logger.debug("[PC: " + String.format("%04x", this.programCounter) + "] " + nextOpCode.name());

				// TODO: Request cost from API, apply cost to balance, etc.

				// At this point, programCounter is BEFORE opcode (and args).
				nextOpCode.execute(this);

				// Synchronize programCounter with codeByteBuffer in case of JMPs, branches, etc.
				this.programCounter = codeByteBuffer.position();
			} catch (ExecutionException e) {
				this.logger.debug("Error at PC " + String.format("%04x", this.programCounter) + ": " + e.getMessage());

				if (this.onErrorAddress == null) {
					this.isFinished = true;
					this.hadFatalError = true;

					// Ask API to refund remaining funds back to AT's creator
					this.api.onFatalError(this, e);
					break;
				}

				this.programCounter = this.onErrorAddress;
				codeByteBuffer.position(this.programCounter);
			}

			++this.steps;
		}

		if (this.isStopped) {
			this.logger.debug("Setting program counter to stop address: " + String.format("%04x", this.onStopAddress));
			this.programCounter = this.onStopAddress;
		}
	}

	/** Return disassembly of code bytes */
	public String disassemble() throws ExecutionException {
		String output = "";

		codeByteBuffer.position(0);

		while (codeByteBuffer.hasRemaining()) {
			byte rawOpCode = codeByteBuffer.get();
			if (rawOpCode == 0)
				continue;

			OpCode nextOpCode = OpCode.valueOf(rawOpCode);
			if (nextOpCode == null)
				throw new IllegalOperationException("OpCode 0x" + String.format("%02x", rawOpCode) + " not recognised");

			if (!output.isEmpty())
				output += "\n";

			output += "[PC: " + String.format("%04x", codeByteBuffer.position() - 1) + "] " + nextOpCode.disassemble(codeByteBuffer, dataByteBuffer);
		}

		return output;
	}

}
