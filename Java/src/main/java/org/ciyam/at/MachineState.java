package org.ciyam.at;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MachineState {

	/** Header bytes length */
	// version + reserved + code + data + call-stack + user-stack + min-activation-amount
	public static final int HEADER_LENGTH = 2 + 2 + 2 + 2 + 2 + 2 + 8;

	/** Size of one OpCode - typically 1 byte (byte) */
	public static final int OPCODE_SIZE = 1;

	/** Size of one FunctionCode - typically 2 bytes (short) */
	public static final int FUNCTIONCODE_SIZE = 2;

	/** Size of value stored in data segment - typically 8 bytes (long) */
	public static final int VALUE_SIZE = 8;

	/** Size of code-address - typically 4 bytes (int) */
	public static final int ADDRESS_SIZE = 4;

	/** Maximum value for an address in the code segment */
	public static final int MAX_CODE_ADDRESS = 0x0000ffff;

	/** Size of A or B register. */
	public static final int AB_REGISTER_SIZE = 32;

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
	private static final Map<Short, VersionedConstants> VERSIONED_CONSTANTS = new HashMap<>();
	static {
		VERSIONED_CONSTANTS.put((short) 1, new VersionedConstants(256, 256, 256, 256));
		VERSIONED_CONSTANTS.put((short) 2, new VersionedConstants(OPCODE_SIZE, VALUE_SIZE, ADDRESS_SIZE, VALUE_SIZE));
	}

	// Set during construction
	public final short version;
	public final short reserved;
	public final short numCodePages;
	public final short numDataPages;
	public final short numCallStackPages;
	public final short numUserStackPages;
	public final long minActivationAmount;

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

	// Internal use
	private int currentBlockHeight;
	private long currentBalance;

	/** Previous balance after end of last round of execution */
	private long previousBalance;

	/** Number of opcodes processed this execution round */
	private int steps;

	private boolean isFirstOpCodeAfterSleeping;

	private API api;
	private AtLoggerFactory loggerFactory;
	private AtLogger logger;

	// NOTE: These are package-scope to allow easy access/operations in Opcode/FunctionCode.
	/* package */ ByteBuffer codeByteBuffer;
	/* package */ ByteBuffer dataByteBuffer;
	/* package */ ByteBuffer callStackByteBuffer;
	/* package */ ByteBuffer userStackByteBuffer;

	// Constructors

	/** For internal use when recreating a machine state */
	private MachineState(byte[] headerBytes, API api, AtLoggerFactory loggerFactory) {
		if (headerBytes.length != HEADER_LENGTH)
			throw new IllegalArgumentException("headerBytes length " + headerBytes.length + " incorrect, expected " + HEADER_LENGTH);

		this.headerBytes = headerBytes;

		// Parsing header bytes
		ByteBuffer byteBuffer = ByteBuffer.wrap(this.headerBytes);

		this.version = byteBuffer.getShort();
		if (this.version < 1)
			throw new IllegalArgumentException("Version must be > 0");

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

		this.minActivationAmount = byteBuffer.getLong();

		// Header OK - set up code and data buffers
		this.codeByteBuffer = ByteBuffer.allocate(this.numCodePages * this.constants.CODE_PAGE_SIZE);
		this.dataByteBuffer = ByteBuffer.allocate(this.numDataPages * this.constants.DATA_PAGE_SIZE);

		// Set up stacks
		this.callStackByteBuffer = ByteBuffer.allocate(this.numCallStackPages * this.constants.CALL_STACK_PAGE_SIZE);
		this.callStackByteBuffer.position(this.callStackByteBuffer.limit()); // Downward-growing stack, so start at the end

		this.userStackByteBuffer = ByteBuffer.allocate(this.numUserStackPages * this.constants.USER_STACK_PAGE_SIZE);
		this.userStackByteBuffer.position(this.userStackByteBuffer.limit()); // Downward-growing stack, so start at the end

		this.api = api;
		this.currentBlockHeight = 0;
		this.currentBalance = 0;
		this.previousBalance = 0;
		this.steps = 0;
		this.loggerFactory = loggerFactory;
		this.logger = loggerFactory.create(MachineState.class);
	}

	/** For creating a new machine state */
	public MachineState(API api, AtLoggerFactory loggerFactory, byte[] creationBytes) {
		this(Arrays.copyOfRange(creationBytes, 0, HEADER_LENGTH), api, loggerFactory);

		int expectedLength = HEADER_LENGTH + this.numCodePages * this.constants.CODE_PAGE_SIZE + this.numDataPages * this.constants.DATA_PAGE_SIZE;
		if (creationBytes.length != expectedLength)
			throw new IllegalArgumentException("Creation bytes length does not match header values");

		System.arraycopy(creationBytes, HEADER_LENGTH, this.codeByteBuffer.array(), 0, this.numCodePages * this.constants.CODE_PAGE_SIZE);

		System.arraycopy(creationBytes, HEADER_LENGTH + this.numCodePages * this.constants.CODE_PAGE_SIZE, this.dataByteBuffer.array(), 0,
				this.numDataPages * this.constants.DATA_PAGE_SIZE);

		commonFinalConstruction();
	}

	/** For creating a new machine state - used in tests */
	public MachineState(API api, AtLoggerFactory loggerFactory, byte[] headerBytes, byte[] codeBytes, byte[] dataBytes) {
		this(headerBytes, api, loggerFactory);

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
		this.previousBalance = this.api.getCurrentBalance(this); // Initial previousBalance is deployment balance

		// If we have a minimum activation amount then create AT in frozen state, requiring that amount to unfreeze.
		// If creator also sends funds with creation then AT will unfreeze on first call.
		if (this.minActivationAmount > 0) {
			this.isFrozen = true;
			// -1 because current balance has to exceed frozenBalance to unfreeze AT
			this.frozenBalance = this.minActivationAmount - 1;
		}
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

	public boolean isSleeping() {
		return this.isSleeping;
	}

	/* package */ void setIsSleeping(boolean isSleeping) {
		this.isSleeping = isSleeping;
	}

	public Integer getSleepUntilHeight() {
		return this.sleepUntilHeight;
	}

	/* package */ void setSleepUntilHeight(Integer height) {
		this.sleepUntilHeight = height;
	}

	public boolean isStopped() {
		return this.isStopped;
	}

	/* package */ void setIsStopped(boolean isStopped) {
		this.isStopped = isStopped;
	}

	public boolean isFrozen() {
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

	public boolean isFinished() {
		return this.isFinished;
	}

	/* package */ void setIsFinished(boolean isFinished) {
		this.isFinished = isFinished;
	}

	public boolean hadFatalError() {
		return this.hadFatalError;
	}

	/* package */ void setHadFatalError(boolean hadFatalError) {
		this.hadFatalError = hadFatalError;
	}

	public int getCurrentBlockHeight() {
		return this.currentBlockHeight;
	}

	/** So API can determine final execution fee */
	public int getSteps() {
		return this.steps;
	}

	public API getAPI() {
		return this.api;
	}

	public AtLoggerFactory getLoggerFactory() {
		return this.loggerFactory;
	}

	/* package */ AtLogger getLogger() {
		return this.logger;
	}

	public long getCurrentBalance() {
		return this.currentBalance;
	}

	// For FunctionCode use
	/* package */ void setCurrentBalance(long currentBalance) {
		this.currentBalance = currentBalance;
	}

	public long getPreviousBalance() {
		return this.previousBalance;
	}

	// For FunctionCode/API use
	/* package */ boolean isFirstOpCodeAfterSleeping() {
		return this.isFirstOpCodeAfterSleeping;
	}

	/**
	 * Rewinds program counter by amount.
	 * <p>
	 * Actually rewinds codeByteBuffer's position, not PC, as the later is synchronized from the former after each OpCode is executed.
	 * 
	 * @param offset
	 */
	/* package */ void rewindCodePosition(int offset) {
		this.codeByteBuffer.position(this.codeByteBuffer.position() - offset);
	}

	// Serialization

	public static byte[] toCreationBytes(short version, byte[] codeBytes, byte[] dataBytes, short numCallStackPages, short numUserStackPages, long minActivationAmount) {
		if (version < 1)
			throw new IllegalArgumentException("Version must be > 0");

		VersionedConstants constants = VERSIONED_CONSTANTS.get(version);
		if (constants == null)
			throw new IllegalArgumentException("Version " + version + " unsupported");

		// Calculate number of code pages
		if (codeBytes.length == 0)
			throw new IllegalArgumentException("Empty code bytes");
		short numCodePages = (short) (((codeBytes.length - 1) / constants.CODE_PAGE_SIZE) + 1);

		// Calculate number of data pages
		if (dataBytes.length == 0)
			throw new IllegalArgumentException("Empty data bytes");
		short numDataPages = (short) (((dataBytes.length - 1) / constants.DATA_PAGE_SIZE) + 1);

		int creationBytesLength = HEADER_LENGTH + numCodePages * constants.CODE_PAGE_SIZE + numDataPages * constants.DATA_PAGE_SIZE;
		byte[] creationBytes = new byte[creationBytesLength];

		ByteBuffer byteBuffer = ByteBuffer.wrap(creationBytes);

		// Header bytes:

		// Version
		byteBuffer.putShort(version);

		// Reserved
		byteBuffer.putShort((short) 0);

		// Code length
		byteBuffer.putShort(numCodePages);

		// Data length
		byteBuffer.putShort(numDataPages);

		// Call stack length
		byteBuffer.putShort(numCallStackPages);

		// User stack length
		byteBuffer.putShort(numUserStackPages);

		// Minimum activation amount
		byteBuffer.putLong(minActivationAmount);

		// Code bytes
		System.arraycopy(codeBytes, 0, creationBytes, HEADER_LENGTH, codeBytes.length);

		// Data bytes
		System.arraycopy(dataBytes, 0, creationBytes, HEADER_LENGTH + numCodePages * constants.CODE_PAGE_SIZE, dataBytes.length);

		return creationBytes;
	}

	/** Returns code bytes only as these are read-only so no need to be duplicated in every serialized state */
	public byte[] getCodeBytes() {
		return this.codeByteBuffer.array();
	}

	/** For serializing a machine state */
	public byte[] toBytes() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		try {
			// Header first
			bytes.write(this.headerBytes);

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
			bytes.write(toByteArray(this.previousBalance));

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
	public static MachineState fromBytes(API api, AtLoggerFactory loggerFactory, byte[] stateBytes, byte[] codeBytes) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(stateBytes);

		byte[] headerBytes = new byte[HEADER_LENGTH];
		byteBuffer.get(headerBytes);

		MachineState state = new MachineState(headerBytes, api, loggerFactory);

		if (codeBytes.length != state.codeByteBuffer.capacity())
			throw new IllegalStateException("Passed codeBytes does not match length in header");

		// Pull in code bytes
		System.arraycopy(codeBytes, 0, state.codeByteBuffer.array(), 0, codeBytes.length);

		// Pull in data bytes
		int dataBytesLength = state.dataByteBuffer.capacity();
		System.arraycopy(stateBytes, byteBuffer.position(), state.dataByteBuffer.array(), 0, dataBytesLength);
		byteBuffer.position(byteBuffer.position() + dataBytesLength);

		// Pull in call stack
		int callStackLength = byteBuffer.getInt();
		// Restore call stack pointer, and useful for copy below
		state.callStackByteBuffer.position(state.callStackByteBuffer.limit() - callStackLength);
		// Call stack grows downwards so copy to end
		System.arraycopy(stateBytes, byteBuffer.position(), state.callStackByteBuffer.array(), state.callStackByteBuffer.position(), callStackLength);
		byteBuffer.position(byteBuffer.position() + callStackLength);

		// Pull in user stack
		int userStackLength = byteBuffer.getInt();
		// Restore user stack pointer, and useful for copy below
		state.userStackByteBuffer.position(state.userStackByteBuffer.limit() - userStackLength);
		// User stack grows downwards so copy to end
		System.arraycopy(stateBytes, byteBuffer.position(), state.userStackByteBuffer.array(), state.userStackByteBuffer.position(), userStackLength);
		byteBuffer.position(byteBuffer.position() + userStackLength);

		// Actual state
		state.programCounter = byteBuffer.getInt();
		state.onStopAddress = byteBuffer.getInt();
		state.previousBalance = byteBuffer.getLong();

		// Various flags (reverse order to toBytes)
		Flags flags = new Flags(byteBuffer.getInt());
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

	/** Returns data bytes from saved state to allow external analysis, e.g. confirming expected payouts, etc. */
	public static byte[] extractDataBytes(AtLoggerFactory loggerFactory, byte[] stateBytes) {
		ByteBuffer byteBuffer = ByteBuffer.wrap(stateBytes);

		byte[] headerBytes = new byte[HEADER_LENGTH];
		byteBuffer.get(headerBytes);

		MachineState state = new MachineState(headerBytes, null, loggerFactory);

		// Extract data bytes
		int dataBytesLength = state.dataByteBuffer.capacity();
		byte[] dataBytes = new byte[dataBytesLength];
		// More efficient than ByteBuffer.get()
		System.arraycopy(stateBytes, byteBuffer.position(), dataBytes, 0, dataBytesLength);

		return dataBytes;
	}

	/** Class for pushing/popping boolean flags onto/from an int */
	private static class Flags {
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

	/** Convert int to big-endian byte array */
	public static byte[] toByteArray(int value) {
		return new byte[] { (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) (value) };
	}

	/** Convert long to big-endian byte array */
	public static byte[] toByteArray(long value) {
		return new byte[] { (byte) (value >> 56), (byte) (value >> 48), (byte) (value >> 40), (byte) (value >> 32),
				(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) (value) };
	}

	/**
	 * Actually perform a round of execution
	 * <p>
	 * On return, caller is expected to call getCurrentBalance() to update their account records, and also to call getSteps() to calculate final execution fee
	 * for block records.
	 */
	public void execute() {
		// Initialization
		this.steps = 0;
		this.currentBlockHeight = api.getCurrentBlockHeight();
		this.currentBalance = api.getCurrentBalance(this);
		this.isFirstOpCodeAfterSleeping = false;

		// Pre-execution checks
		if (this.isFinished) {
			logger.debug(() -> "Not executing as already finished!");
			return;
		}

		if (this.isFrozen && this.currentBalance <= this.frozenBalance) {
			logger.debug(() -> String.format("Not executing as current balance [%d] hasn't increased since being frozen at [%d]", this.currentBalance, this.frozenBalance));
			return;
		}

		if (this.isSleeping && this.sleepUntilHeight != null && this.currentBlockHeight < this.sleepUntilHeight) {
			logger.debug(() -> String.format("Not executing as current block height [%d] hasn't reached sleep-until block height [%d]", this.currentBlockHeight, this.sleepUntilHeight));
			return;
		}

		// If we were previously sleeping then set first-opcode-after-sleeping to help FunctionCodes that need to detect this
		if (this.isSleeping)
			this.isFirstOpCodeAfterSleeping = true;

		// Reset for this round of execution
		this.isSleeping = false;
		this.sleepUntilHeight = null;
		this.isStopped = false;
		this.isFrozen = false;
		this.frozenBalance = null;

		// Cache useful info from API
		long feePerStep = this.api.getFeePerStep();
		int maxSteps = api.getMaxStepsPerRound();

		// Set byte buffer position using program counter
		codeByteBuffer.position(this.programCounter);

		while (!this.isSleeping && !this.isStopped && !this.isFinished && !this.isFrozen) {
			byte rawOpCode = codeByteBuffer.get();
			OpCode nextOpCode = OpCode.valueOf(rawOpCode);

			try {
				if (nextOpCode == null)
					throw new IllegalOperationException("OpCode 0x" + String.format("%02x", rawOpCode) + " not recognised");

				this.logger.debug(() -> String.format("[PC: %04x] %s", this.programCounter, nextOpCode.name()));

				// Request opcode step-fee from API, apply fee to balance, etc.
				int opcodeSteps = this.api.getOpCodeSteps(nextOpCode);
				long opcodeFee = opcodeSteps * feePerStep;

				if (this.steps + opcodeSteps > maxSteps) {
					logger.debug(() -> String.format("Enforced sleep due to exceeding maximum number of steps (%d) per execution round", maxSteps));
					this.isSleeping = true;
					break;
				}

				if (this.currentBalance < opcodeFee) {
					// Not enough balance left to continue execution - freeze AT
					logger.debug(() -> "Frozen due to lack of balance");
					this.isFrozen = true;
					this.frozenBalance = this.currentBalance;
					break;
				}

				// Apply opcode step-fee
				this.currentBalance -= opcodeFee;
				this.steps += opcodeSteps;

				// At this point, programCounter is BEFORE opcode (and args).
				nextOpCode.execute(this);

				// Synchronize programCounter with codeByteBuffer in case of JMPs, branches, etc.
				this.programCounter = codeByteBuffer.position();
			} catch (ExecutionException e) {
				this.logger.error(() -> String.format("Error at PC %04x: %s", this.programCounter, e.getMessage()));

				if (this.onErrorAddress == null) {
					this.isFinished = true;
					this.hadFatalError = true;

					// Notify API that there was an error
					this.api.onFatalError(this, e);
					break;
				}

				this.programCounter = this.onErrorAddress;
				codeByteBuffer.position(this.programCounter);
			}

			// No longer true
			this.isFirstOpCodeAfterSleeping = false;
		}

		if (this.isSleeping) {
			if (this.sleepUntilHeight != null)
				this.logger.debug(() -> String.format("Sleeping until block %d", this.sleepUntilHeight));
			else
				this.logger.debug(() -> "Sleeping until next block");
		}

		if (this.isStopped) {
			this.logger.debug(() -> String.format("Setting program counter to stop address: %04x", this.onStopAddress));
			this.programCounter = this.onStopAddress;
		}

		if (this.isFinished) {
			this.logger.debug(() -> "Finished - refunding remaining funds back to creator");
			this.api.onFinished(this.currentBalance, this);
			this.currentBalance = 0;
		}

		// Set new value for previousBalance prior to serialization, ready for next round
		this.previousBalance = this.currentBalance;
	}

	/** Return disassembly of code bytes */
	public static String disassemble(byte[] codeBytes, int dataBufferLength) throws ExecutionException {
		StringBuilder output = new StringBuilder();

		ByteBuffer codeByteBuffer = ByteBuffer.wrap(codeBytes);
		ByteBuffer dataByteBuffer = ByteBuffer.allocate(dataBufferLength);

		while (codeByteBuffer.hasRemaining()) {
			byte rawOpCode = codeByteBuffer.get();
			if (rawOpCode == 0)
				continue;

			OpCode nextOpCode = OpCode.valueOf(rawOpCode);
			if (nextOpCode == null)
				throw new IllegalOperationException("OpCode 0x" + String.format("%02x", rawOpCode) + " not recognised");

			if (output.length() != 0)
				output.append("\n");

			output.append(String.format("[PC: %04x] %s", codeByteBuffer.position() - 1, nextOpCode.disassemble(codeByteBuffer, dataByteBuffer)));
		}

		return output.toString();
	}

}
