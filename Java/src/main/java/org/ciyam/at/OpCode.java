package org.ciyam.at;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This enum contains op codes for the CIYAM AT machine.
 * <p>
 * Op codes are represented by a single byte and maybe be followed by additional arguments like data addresses, offset, immediate values, etc.
 * <p>
 * OpCode instances can be obtained via the default <code>OpCode.valueOf(String)</code> or the additional <code>OpCode.valueOf(int)</code>.
 * <p>
 * Use the <code>OpCode.execute</code> method to perform the operation.
 * <p>
 * In the documentation for each OpCode:
 * <p>
 * <code>@addr</code> means "store at <code>addr</code>"
 * <p>
 * <code>$addr</code> means "fetch from <code>addr</code>"
 * <p>
 * <code>@($addr)</code> means "store at address fetched from <code>addr</code>", i.e. indirect
 * <p>
 * <code>$($addr1 + $addr2)</code> means "fetch from address fetched from <code>addr1</code> plus offset fetched from <code>addr2</code>", i.e. indirect indexed
 * 
 * @see OpCode#valueOf(int)
 * @see OpCode#executeWithParams(MachineState, Object...)
 */
public enum OpCode {

	/**
	 * <b>N</b>o <b>OP</b>eration<br>
	 * <code>0x7f</code><br>
	 * (Does nothing)
	 */
	NOP(0x7f) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) {
			// Do nothing
		}
	},
	/**
	 * <b>SET</b> <b>VAL</b>ue<br>
	 * <code>0x01 addr value</code><br>
	 * <code>@addr = value</code>
	 */
	SET_VAL(0x01, OpCodeParam.DEST_ADDR, OpCodeParam.VALUE) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];
			long value = (long) args[1];

			state.dataByteBuffer.putLong(address, value);
		}
	},
	/**
	 * <b>SET</b> <b>DAT</b>a<br>
	 * <code>0x02 addr1 addr2</code><br>
	 * <code>@addr1 = $addr2</code>
	 */
	SET_DAT(0x02, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address1 = (int) args[0];
			int address2 = (int) args[1];

			long value = state.dataByteBuffer.getLong(address2);
			state.dataByteBuffer.putLong(address1, value);
		}
	},
	/**
	 * <b>CL</b>ea<b>R</b> <b>DAT</b>a<br>
	 * <code>0x03 addr</code><br>
	 * <code>@addr = 0</code>
	 */
	CLR_DAT(0x03, OpCodeParam.DEST_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			state.dataByteBuffer.putLong(address, 0L);
		}
	},
	/**
	 * <b>INC</b>rement <b>DAT</b>a<br>
	 * <code>0x04 addr</code><br>
	 * <code>@addr += 1</code>
	 */
	INC_DAT(0x04, OpCodeParam.DEST_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			long value = state.dataByteBuffer.getLong(address);
			state.dataByteBuffer.putLong(address, value + 1);
		}
	},
	/**
	 * <b>DEC</b>rement <b>DAT</b>a<br>
	 * <code>0x05 addr</code><br>
	 * <code>@addr -= 1</code>
	 */
	DEC_DAT(0x05, OpCodeParam.DEST_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			long value = state.dataByteBuffer.getLong(address);
			state.dataByteBuffer.putLong(address, value - 1);
		}
	},
	/**
	 * <b>ADD</b> <b>DAT</b>a<br>
	 * <code>0x06 addr1 addr2</code><br>
	 * <code>@addr1 += $addr2</code>
	 */
	ADD_DAT(0x06, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeDataOperation(state, (a, b) -> a + b, args);
		}
	},
	/**
	 * <b>SUB</b>tract <b>DAT</b>a<br>
	 * <code>0x07 addr1 addr2</code><br>
	 * <code>@addr1 -= $addr2</code>
	 */
	SUB_DAT(0x07, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeDataOperation(state, (a, b) -> a - b, args);
		}
	},
	/**
	 * <b>MUL</b>tiply <b>DAT</b>a<br>
	 * <code>0x08 addr1 addr2</code><br>
	 * <code>@addr1 *= $addr2</code>
	 */
	MUL_DAT(0x08, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeDataOperation(state, (a, b) -> a * b, args);
		}
	},
	/**
	 * <b>DIV</b>ide <b>DAT</b>a<br>
	 * <code>0x09 addr1 addr2</code><br>
	 * <code>@addr1 /= $addr2</code><br>
	 * Can also throw <code>IllegalOperationException</code> if divide-by-zero attempted.
	 */
	DIV_DAT(0x09, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			try {
				executeDataOperation(state, (a, b) -> a / b, args);
			} catch (ArithmeticException e) {
				throw new IllegalOperationException("Divide by zero", e);
			}
		}
	},
	/**
	 * <b>B</b>inary-<b>OR</b> <b>DAT</b>a<br>
	 * <code>0x0a addr1 addr2</code><br>
	 * <code>@addr1 |= $addr2</code>
	 */
	BOR_DAT(0x0a, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeDataOperation(state, (a, b) -> a | b, args);
		}
	},
	/**
	 * Binary-<b>AND</b> <b>DAT</b>a<br>
	 * <code>0x0b addr1 addr2</code><br>
	 * <code>@addr1 &amp;= $addr2</code>
	 */
	AND_DAT(0x0b, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeDataOperation(state, (a, b) -> a & b, args);
		}
	},
	/**
	 * E<b>X</b>clusive <b>OR</b> <b>DAT</b>a<br>
	 * <code>0x0c addr1 addr2</code><br>
	 * <code>@addr1 ^= $addr2</code>
	 */
	XOR_DAT(0x0c, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeDataOperation(state, (a, b) -> a ^ b, args);
		}
	},
	/**
	 * Bitwise-<b>NOT</b> <b>DAT</b>a<br>
	 * <code>0x0d addr</code><br>
	 * <code>@addr = ~$addr</code>
	 */
	NOT_DAT(0x0d, OpCodeParam.DEST_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			long value = state.dataByteBuffer.getLong(address);
			state.dataByteBuffer.putLong(address, ~value);
		}
	},
	/**
	 * <b>SET</b> using <b>IND</b>irect data<br>
	 * <code>0x0e addr1 addr2</code><br>
	 * <code>@addr1 = $($addr2)</code>
	 */
	SET_IND(0x0e, OpCodeParam.DEST_ADDR, OpCodeParam.INDIRECT_SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address1 = (int) args[0];
			int address2 = (int) args[1];

			long address3 = state.dataByteBuffer.getLong(address2) * MachineState.VALUE_SIZE;

			if (address3 < 0 || address3 + MachineState.VALUE_SIZE >= state.dataByteBuffer.limit())
				throw new InvalidAddressException("Data address out of bounds");

			long value = state.dataByteBuffer.getLong((int) address3);
			state.dataByteBuffer.putLong(address1, value);
		}
	},
	/**
	 * <b>SET</b> using indirect <b>I</b>n<b>D</b>e<b>X</b>ed data<br>
	 * <code>0x0f addr1 addr2 addr3</code><br>
	 * <code>@addr1 = $($addr2 + $addr3)</code>
	 */
	SET_IDX(0x0f, OpCodeParam.DEST_ADDR, OpCodeParam.INDIRECT_SRC_ADDR_WITH_INDEX, OpCodeParam.INDEX) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address1 = (int) args[0];
			int address2 = (int) args[1];
			int address3 = (int) args[2];

			long baseAddress = state.dataByteBuffer.getLong(address2) * MachineState.VALUE_SIZE;
			long offset = state.dataByteBuffer.getLong(address3) * MachineState.VALUE_SIZE;

			long newAddress = baseAddress + offset;

			if (newAddress < 0 || newAddress + MachineState.VALUE_SIZE >= state.dataByteBuffer.limit())
				throw new InvalidAddressException("Data address out of bounds");

			long value = state.dataByteBuffer.getLong((int) newAddress);
			state.dataByteBuffer.putLong(address1, value);
		}
	},
	/**
	 * <b>P</b>u<b>SH</b> <b>DAT</b>a onto user stack<br>
	 * <code>0x10 addr</code><br>
	 * <code>@--user_stack = $addr</code><br>
	 * Can also throw <code>StackBoundsException</code> if user stack exhausted.
	 */
	PSH_DAT(0x10, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			long value = state.dataByteBuffer.getLong(address);

			try {
				// Simulate backwards-walking stack
				int newPosition = state.userStackByteBuffer.position() - MachineState.VALUE_SIZE;
				state.userStackByteBuffer.putLong(newPosition, value);
				state.userStackByteBuffer.position(newPosition);
			} catch (IndexOutOfBoundsException | IllegalArgumentException e) {
				throw new StackBoundsException("No room on user stack to push data", e);
			}
		}
	},
	/**
	 * <b>POP</b> <b>DAT</b>a from user stack<br>
	 * <code>0x11 addr</code><br>
	 * <code>@addr = $user_stack++</code><br>
	 * Can also throw <code>StackBoundsException</code> if user stack empty.
	 */
	POP_DAT(0x11, OpCodeParam.DEST_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			try {
				long value = state.userStackByteBuffer.getLong();

				// Clear old stack entry
				state.userStackByteBuffer.putLong(state.userStackByteBuffer.position() - MachineState.VALUE_SIZE, 0L);

				// Put popped value into data address
				state.dataByteBuffer.putLong(address, value);
			} catch (BufferUnderflowException e) {
				throw new StackBoundsException("Empty user stack from which to pop data", e);
			}
		}
	},
	/**
	 * <b>J</b>u<b>MP</b> into <b>SUB</b>routine<br>
	 * <code>0x12 addr</code><br>
	 * <code>@--call_stack = PC after opcode and args</code>,<br>
	 * <code>PC = addr</code><br>
	 * Can also throw <code>StackBoundsException</code> if call stack exhausted.
	 */
	JMP_SUB(0x12, OpCodeParam.CODE_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			try {
				// Simulate backwards-walking stack
				int newPosition = state.callStackByteBuffer.position() - MachineState.ADDRESS_SIZE;
				state.callStackByteBuffer.putInt(newPosition, state.codeByteBuffer.position());
				state.callStackByteBuffer.position(newPosition);
			} catch (IndexOutOfBoundsException | IllegalArgumentException e) {
				throw new StackBoundsException("No room on call stack to call subroutine", e);
			}

			state.codeByteBuffer.position(address);
		}
	},
	/**
	 * <b>RET</b>urn from <b>SUB</b>routine<br>
	 * <code>0x13</code><br>
	 * <code>PC = $call_stack++</code><br>
	 * Can also throw <code>StackBoundsException</code> if call stack empty.
	 */
	RET_SUB(0x13) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			try {
				int returnAddress = state.callStackByteBuffer.getInt();

				// Clear old stack entry
				state.callStackByteBuffer.putInt(state.callStackByteBuffer.position() - MachineState.ADDRESS_SIZE, 0);

				state.codeByteBuffer.position(returnAddress);
			} catch (BufferUnderflowException e) {
				throw new StackBoundsException("Empty call stack missing return address from subroutine", e);
			}
		}
	},
	/**
	 * Store <b>IND</b>irect <b>DAT</b>a<br>
	 * <code>0x14 addr1 addr2</code><br>
	 * <code>@($addr1) = $addr2</code>
	 */
	IND_DAT(0x14, OpCodeParam.INDIRECT_DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address1 = (int) args[0];
			int address2 = (int) args[1];

			long address3 = state.dataByteBuffer.getLong(address1) * MachineState.VALUE_SIZE;

			if (address3 < 0 || address3 + MachineState.VALUE_SIZE >= state.dataByteBuffer.limit())
				throw new InvalidAddressException("Data address out of bounds");

			long value = state.dataByteBuffer.getLong(address2);
			state.dataByteBuffer.putLong((int) address3, value);
		}
	},
	/**
	 * Store indirect <b>I</b>n<b>D</b>e<b>X</b>ed <b>DAT</b>a<br>
	 * <code>0x15 addr1 addr2</code><br>
	 * <code>@($addr1 + $addr2) = $addr3</code>
	 */
	IDX_DAT(0x15, OpCodeParam.INDIRECT_DEST_ADDR_WITH_INDEX, OpCodeParam.INDEX, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address1 = (int) args[0];
			int address2 = (int) args[1];
			int address3 = (int) args[2];

			long baseAddress = state.dataByteBuffer.getLong(address1) * MachineState.VALUE_SIZE;
			long offset = state.dataByteBuffer.getLong(address2) * MachineState.VALUE_SIZE;

			long newAddress = baseAddress + offset;

			if (newAddress < 0 || newAddress + MachineState.VALUE_SIZE >= state.dataByteBuffer.limit())
				throw new InvalidAddressException("Data address out of bounds");

			long value = state.dataByteBuffer.getLong(address3);
			state.dataByteBuffer.putLong((int) newAddress, value);
		}
	},
	/**
	 * <b>MOD</b>ulo <b>DAT</b>a<br>
	 * <code>0x16 addr1 addr2</code><br>
	 * <code>@addr1 %= $addr2</code>
	 */
	MOD_DAT(0x16, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			try {
				executeDataOperation(state, (a, b) -> a % b, args);
			} catch (ArithmeticException e) {
				throw new IllegalOperationException("Divide by zero", e);
			}
		}
	},
	/**
	 * <b>SH</b>ift <b>L</b>eft <b>DAT</b>a<br>
	 * <code>0x17 addr1 addr2</code><br>
	 * <code>@addr1 &lt;&lt;= $addr2</code>
	 */
	SHL_DAT(0x17, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		private static final long MAX_SHIFT = MachineState.VALUE_SIZE * 8L;

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			// If 2nd arg is more than value size (in bits) then return 0 to simulate all bits being shifted out of existence
			executeDataOperation(state, (a, b) -> b >= MAX_SHIFT ? 0 : a << b, args);
		}
	},
	/**
	 * <b>SH</b>ift <b>R</b>ight <b>DAT</b>a<br>
	 * <code>0x18 addr1 addr2</code><br>
	 * <code>@addr1 &gt;&gt;= $addr2</code><br>
	 * Note: new MSB bit will be zero
	 */
	SHR_DAT(0x18, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		private static final long MAX_SHIFT = MachineState.VALUE_SIZE * 8L;

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			// If 2nd arg is more than value size (in bits) then return 0 to simulate all bits being shifted out of existence
			executeDataOperation(state, (a, b) -> b >= MAX_SHIFT ? 0 : a >>> b, args);
		}
	},
	/**
	 * <b>J</b>u<b>MP</b> to <b>AD</b>d<b>R</b>ess<br>
	 * <code>0x1a addr</code><br>
	 * <code>PC = addr</code>
	 */
	JMP_ADR(0x1a, OpCodeParam.CODE_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			state.codeByteBuffer.position(address);
		}
	},
	/**
	 * <b>B</b>ranch if <b>Z</b>e<b>R</b>o<br>
	 * <code>0x1b addr offset</code><br>
	 * <code>if ($addr == 0) PC += offset</code><br>
	 * Note: <code>PC</code> is considered to be immediately before opcode byte.
	 */
	BZR_DAT(0x1b, OpCodeParam.SRC_ADDR, OpCodeParam.OFFSET) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];
			byte offset = (byte) args[1];

			int branchTarget = calculateBranchTarget(state, offset);

			long value = state.dataByteBuffer.getLong(address);

			if (value == 0)
				state.codeByteBuffer.position(branchTarget);
		}
	},
	/**
	 * <b>B</b>ranch if <b>N</b>ot <b>Z</b>ero<br>
	 * <code>0x1e addr offset</code><br>
	 * <code>if ($addr != 0) PC += offset</code><br>
	 * Note: <code>PC</code> is considered to be immediately before opcode byte.
	 */
	BNZ_DAT(0x1e, OpCodeParam.SRC_ADDR, OpCodeParam.OFFSET) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];
			byte offset = (byte) args[1];

			int branchTarget = calculateBranchTarget(state, offset);

			long value = state.dataByteBuffer.getLong(address);

			if (value != 0)
				state.codeByteBuffer.position(branchTarget);
		}
	},
	/**
	 * <b>B</b>ranch if <b>G</b>reater-<b>T</b>han <b>DAT</b>a<br>
	 * <code>0x1f addr1 addr2 offset</code><br>
	 * <code>if ($addr1 &gt; $addr2) PC += offset</code><br>
	 * Note: <code>PC</code> is considered to be immediately before opcode byte.
	 */
	BGT_DAT(0x1f, OpCodeParam.SRC_ADDR, OpCodeParam.SRC_ADDR, OpCodeParam.OFFSET) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeBranchConditional(state, (a, b) -> a > b, args);
		}
	},
	/**
	 * <b>B</b>ranch if <b>L</b>ess-<b>T</b>han <b>DAT</b>a<br>
	 * <code>0x20 addr1 addr2 offset</code><br>
	 * <code>if ($addr1 &lt; $addr2) PC += offset</code><br>
	 * Note: <code>PC</code> is considered to be immediately before opcode byte.
	 */
	BLT_DAT(0x20, OpCodeParam.SRC_ADDR, OpCodeParam.SRC_ADDR, OpCodeParam.OFFSET) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeBranchConditional(state, (a, b) -> a < b, args);
		}
	},
	/**
	 * <b>B</b>ranch if <b>G</b>reater-or-<b>E</b>qual <b>DAT</b>a<br>
	 * <code>0x21 addr1 addr2 offset</code><br>
	 * <code>if ($addr1 &gt;= $addr2) PC += offset</code><br>
	 * Note: <code>PC</code> is considered to be immediately before opcode byte.
	 */
	BGE_DAT(0x21, OpCodeParam.SRC_ADDR, OpCodeParam.SRC_ADDR, OpCodeParam.OFFSET) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeBranchConditional(state, (a, b) -> a >= b, args);
		}
	},
	/**
	 * <b>B</b>ranch if <b>L</b>ess-or-<b>E</b>qual <b>DAT</b>a<br>
	 * <code>0x22 addr1 addr2 offset</code><br>
	 * <code>if ($addr1 &lt;= $addr2) PC += offset</code><br>
	 * Note: <code>PC</code> is considered to be immediately before opcode byte.
	 */
	BLE_DAT(0x22, OpCodeParam.SRC_ADDR, OpCodeParam.SRC_ADDR, OpCodeParam.OFFSET) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeBranchConditional(state, (a, b) -> a <= b, args);
		}
	},
	/**
	 * <b>B</b>ranch if <b>EQ</b>ual <b>DAT</b>a<br>
	 * <code>0x23 addr1 addr2 offset</code><br>
	 * <code>if ($addr1 == $addr2) PC += offset</code><br>
	 * Note: <code>PC</code> is considered to be immediately before opcode byte.
	 */
	BEQ_DAT(0x23, OpCodeParam.SRC_ADDR, OpCodeParam.SRC_ADDR, OpCodeParam.OFFSET) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeBranchConditional(state, (a, b) -> a == b, args);
		}
	},
	/**
	 * <b>B</b>ranch if <b>N</b>ot-<b>E</b>qual <b>DAT</b>a<br>
	 * <code>0x24 addr1 addr2 offset</code><br>
	 * <code>if ($addr1 != $addr2) PC += offset</code><br>
	 * Note: <code>PC</code> is considered to be immediately before opcode byte.
	 */
	BNE_DAT(0x24, OpCodeParam.SRC_ADDR, OpCodeParam.SRC_ADDR, OpCodeParam.OFFSET) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeBranchConditional(state, (a, b) -> a != b, args);
		}
	},
	/**
	 * <b>SL</b>ee<b>P</b> until <b>DAT</b>a<br>
	 * <code>0x25 addr</code><br>
	 * Sleep until <code>$addr</code>, then carry on from current <code>PC</code><br>
	 * Note: The value from <code>$addr</code> is considered to be a block height.
	 */
	SLP_DAT(0x25, OpCodeParam.BLOCK_HEIGHT) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			long value = state.dataByteBuffer.getLong(address);

			state.setSleepUntilHeight((int) value);
			state.setIsSleeping(true);
		}
	},
	/**
	 * <b>FI</b>nish if <b>Z</b>ero <b>DAT</b>a<br>
	 * <code>0x26 addr</code><br>
	 * <code>if ($addr == 0) permanently stop</code>
	 */
	FIZ_DAT(0x26, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			long value = state.dataByteBuffer.getLong(address);

			if (value == 0)
				state.setIsFinished(true);
		}
	},
	/**
	 * <b>ST</b>op if <b>Z</b>ero <b>DAT</b>a<br>
	 * <code>0x27 addr</code><br>
	 * <code>if ($addr == 0) PC = PCS and stop</code>
	 */
	STZ_DAT(0x27, OpCodeParam.SRC_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			long value = state.dataByteBuffer.getLong(address);

			if (value == 0) {
				state.codeByteBuffer.position(state.getOnStopAddress());
				state.setIsStopped(true);
			}
		}
	},
	/**
	 * <b>FIN</b>ish <b>IM</b>me<b>D</b>iately<br>
	 * <code>0x28</code><br>
	 * <code>permanently stop</code>
	 */
	FIN_IMD(0x28) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			state.setIsFinished(true);
		}
	},
	/**
	 * <b>ST</b>o<b>P</b> <b>IM</b>me<b>D</b>iately<br>
	 * <code>0x29</code><br>
	 * <code>stop</code>
	 */
	STP_IMD(0x29) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) {
			state.setIsStopped(true);
		}
	},
	/**
	 * <b>SL</b>ee<b>P</b> <b>IM</b>me<b>D</b>iately<br>
	 * <code>0x2a</code><br>
	 * Sleep until next block, then carry on from current <code>PC</code>
	 */
	SLP_IMD(0x2a) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) {
			state.setSleepUntilHeight(state.getCurrentBlockHeight() + 1);
			state.setIsSleeping(true);
		}
	},
	/**
	 * Set <b>ERR</b>or <b>AD</b>d<b>R</b>ess<br>
	 * <code>0x2b addr</code><br>
	 * <code>PCE = addr</code>
	 */
	ERR_ADR(0x2b, OpCodeParam.CODE_ADDR) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			int address = (int) args[0];

			state.setOnErrorAddress(address);
		}
	},
	/**
	 * <b>SL</b>ee<b>P</b> for <b>VAL</b>ue blocks<br>
	 * <code>0x2c value</code><br>
	 * Sleep for <code>value</code> blocks, then carry on from current <code>PC</code>
	 */
	SLP_VAL(0x2c, OpCodeParam.VALUE) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			long value = (long) args[0];

			state.setSleepUntilHeight(state.getCurrentBlockHeight() + (int) value);
			state.setIsSleeping(true);
		}
	},
	/**
	 * <b>SET</b> <b>PCS</b> (stop address)<br>
	 * <code>0x30</code><br>
	 * <code>PCS = PC</code><br>
	 * Note: <code>PC</code> is considered to be immediately after this opcode byte.
	 */
	SET_PCS(0x30) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) {
			state.setOnStopAddress(state.codeByteBuffer.position());
		}
	},
	/**
	 * Call <b>EXT</b>ernal <b>FUN</b>ction<br>
	 * <code>0x32 func</code><br>
	 * <code>func()</code>
	 */
	EXT_FUN(0x32, OpCodeParam.FUNC) {
		@Override
		protected void preExecuteCheck(Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);

			if (functionCode == null)
				throw new IllegalFunctionCodeException(String.format("Unknown function code 0x%04x encountered at %s",
						rawFunctionCode, this.name()));

			functionCode.preExecuteCheck(0, false);
		}

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);

			FunctionData functionData = new FunctionData(false);

			functionCode.execute(functionData, state, rawFunctionCode);
		}
	},
	/**
	 * Call <b>EXT</b>ernal <b>FUN</b>ction with <b>DAT</b>a<br>
	 * <code>0x33 func addr</code><br>
	 * <code>func($addr)</code>
	 */
	EXT_FUN_DAT(0x33, OpCodeParam.FUNC, OpCodeParam.SRC_ADDR) {
		@Override
		protected void preExecuteCheck(Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);

			if (functionCode == null)
				throw new IllegalFunctionCodeException(String.format("Unknown function code 0x%04x encountered at %s",
						rawFunctionCode, this.name()));

			functionCode.preExecuteCheck(1, false);
		}

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];
			int address = (int) args[1];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);
			long value = state.dataByteBuffer.getLong(address);

			FunctionData functionData = new FunctionData(value, false);

			functionCode.execute(functionData, state, rawFunctionCode);
		}
	},
	/**
	 * Call <b>EXT</b>ernal <b>FUN</b>ction with <b>DAT</b>a x<b>2</b><br>
	 * <code>0x34 func addr1 addr2</code><br>
	 * <code>func($addr1, $addr2)</code>
	 */
	EXT_FUN_DAT_2(0x34, OpCodeParam.FUNC, OpCodeParam.SRC_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void preExecuteCheck(Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);

			if (functionCode == null)
				throw new IllegalFunctionCodeException(String.format("Unknown function code 0x%04x encountered at %s",
						rawFunctionCode, this.name()));

			functionCode.preExecuteCheck(2, false);
		}

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];
			int address1 = (int) args[1];
			int address2 = (int) args[2];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);
			long value1 = state.dataByteBuffer.getLong(address1);
			long value2 = state.dataByteBuffer.getLong(address2);

			FunctionData functionData = new FunctionData(value1, value2, false);

			functionCode.execute(functionData, state, rawFunctionCode);
		}
	},
	/**
	 * Call <b>EXT</b>ernal <b>FUN</b>ction expecting <b>RET</b>urn value<br>
	 * <code>0x35 func addr</code><br>
	 * <code>@addr = func()</code>
	 */
	EXT_FUN_RET(0x35, OpCodeParam.FUNC, OpCodeParam.DEST_ADDR) {
		@Override
		protected void preExecuteCheck(Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);

			if (functionCode == null)
				throw new IllegalFunctionCodeException(String.format("Unknown function code 0x%04x encountered at %s",
						rawFunctionCode, this.name()));

			functionCode.preExecuteCheck(0, true);
		}

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];
			int address = (int) args[1];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);

			FunctionData functionData = new FunctionData(true);

			functionCode.execute(functionData, state, rawFunctionCode);

			if (functionData.returnValue == null)
				throw new ExecutionException("Function failed to return a value as expected of EXT_FUN_RET");

			state.dataByteBuffer.putLong(address, functionData.returnValue);
		}
	},
	/**
	 * Call <b>EXT</b>ernal <b>FUN</b>ction expecting <b>RET</b>urn value with <b>DAT</b>a<br>
	 * <code>0x36 func addr1 addr2</code><br>
	 * <code>@addr1 = func($addr2)</code>
	 */
	EXT_FUN_RET_DAT(0x36, OpCodeParam.FUNC, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void preExecuteCheck(Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);

			if (functionCode == null)
				throw new IllegalFunctionCodeException(String.format("Unknown function code 0x%04x encountered at %s",
						rawFunctionCode, this.name()));

			functionCode.preExecuteCheck(1, true);
		}

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];
			int address1 = (int) args[1];
			int address2 = (int) args[2];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);
			long value = state.dataByteBuffer.getLong(address2);

			FunctionData functionData = new FunctionData(value, true);

			functionCode.execute(functionData, state, rawFunctionCode);

			if (functionData.returnValue == null)
				throw new ExecutionException("Function failed to return a value as expected of EXT_FUN_RET_DAT");

			state.dataByteBuffer.putLong(address1, functionData.returnValue);
		}
	},
	/**
	 * Call <b>EXT</b>ernal <b>FUN</b>ction expecting <b>RET</b>urn value with <b>DAT</b>a x<b>2</b><br>
	 * <code>0x37 func addr1 addr2 addr3</code><br>
	 * <code>@addr1 = func($addr2, $addr3)</code>
	 */
	EXT_FUN_RET_DAT_2(0x37, OpCodeParam.FUNC, OpCodeParam.DEST_ADDR, OpCodeParam.SRC_ADDR, OpCodeParam.SRC_ADDR) {
		@Override
		protected void preExecuteCheck(Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);

			if (functionCode == null)
				throw new IllegalFunctionCodeException(String.format("Unknown function code 0x%04x encountered at %s",
						rawFunctionCode, this.name()));

			functionCode.preExecuteCheck(2, true);
		}

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];
			int address1 = (int) args[1];
			int address2 = (int) args[2];
			int address3 = (int) args[3];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);
			long value1 = state.dataByteBuffer.getLong(address2);
			long value2 = state.dataByteBuffer.getLong(address3);

			FunctionData functionData = new FunctionData(value1, value2, true);

			functionCode.execute(functionData, state, rawFunctionCode);

			if (functionData.returnValue == null)
				throw new ExecutionException("Function failed to return a value as expected of EXT_FUN_RET_DAT_2");

			state.dataByteBuffer.putLong(address1, functionData.returnValue);
		}
	},
	/**
	 * Call <b>EXT</b>ernal <b>FUN</b>ction with <b>VAL</b>ue<br>
	 * <code>0x38 func value</code><br>
	 * <code>func(value)</code>
	 */
	EXT_FUN_VAL(0x38, OpCodeParam.FUNC, OpCodeParam.VALUE) {
		@Override
		protected void preExecuteCheck(Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);

			if (functionCode == null)
				throw new IllegalFunctionCodeException(String.format("Unknown function code 0x%04x encountered at %s",
						rawFunctionCode, this.name()));

			functionCode.preExecuteCheck(1, false);
		}

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			short rawFunctionCode = (short) args[0];
			long value = (long) args[1];

			FunctionCode functionCode = FunctionCode.valueOf(rawFunctionCode);

			FunctionData functionData = new FunctionData(value, false);

			functionCode.execute(functionData, state, rawFunctionCode);
		}
	},
	/**
	 * <b>ADD</b> <b>VAL</b>ue<br>
	 * <code>0x46 addr1 value</code><br>
	 * <code>@addr1 += value</code>
	 */
	ADD_VAL(0x46, OpCodeParam.DEST_ADDR, OpCodeParam.VALUE) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeValueOperation(state, (a, b) -> a + b, args);
		}
	},
	/**
	 * <b>SUB</b>tract <b>VAL</b>ue<br>
	 * <code>0x47 addr1 value</code><br>
	 * <code>@addr1 -= value</code>
	 */
	SUB_VAL(0x47, OpCodeParam.DEST_ADDR, OpCodeParam.VALUE) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeValueOperation(state, (a, b) -> a - b, args);
		}
	},
	/**
	 * <b>MUL</b>tiply <b>VAL</b>ue<br>
	 * <code>0x48 addr1 value</code><br>
	 * <code>@addr1 *= value</code>
	 */
	MUL_VAL(0x48, OpCodeParam.DEST_ADDR, OpCodeParam.VALUE) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			executeValueOperation(state, (a, b) -> a * b, args);
		}
	},
	/**
	 * <b>DIV</b>ide <b>VAL</b>ue<br>
	 * <code>0x49 addr1 value</code><br>
	 * <code>@addr1 /= value</code>
	 * Can also throw <code>IllegalOperationException</code> if divide-by-zero attempted.
	 */
	DIV_VAL(0x49, OpCodeParam.DEST_ADDR, OpCodeParam.VALUE) {
		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			try {
				executeValueOperation(state, (a, b) -> a / b, args);
			} catch (ArithmeticException e) {
				throw new IllegalOperationException("Divide by zero", e);
			}
		}
	},
	/**
	 * <b>SH</b>ift <b>L</b>eft <b>VAL</b>ue<br>
	 * <code>0x4a addr1 value</code><br>
	 * <code>@addr1 &lt;&lt;= value</code>
	 */
	SHL_VAL(0x4a, OpCodeParam.DEST_ADDR, OpCodeParam.VALUE) {
		private static final long MAX_SHIFT = MachineState.VALUE_SIZE * 8L;

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			// If 2nd arg is more than value size (in bits) then return 0 to simulate all bits being shifted out of existence
			executeValueOperation(state, (a, b) -> b >= MAX_SHIFT ? 0 : a << b, args);
		}
	},
	/**
	 * <b>SH</b>ift <b>R</b>ight <b>VAL</b>ue<br>
	 * <code>0x4b addr1 value</code><br>
	 * <code>@addr1 &gt;&gt;= value</code><br>
	 * Note: new MSB bit will be zero
	 */
	SHR_VAL(0x4b, OpCodeParam.DEST_ADDR, OpCodeParam.VALUE) {
		private static final long MAX_SHIFT = MachineState.VALUE_SIZE * 8L;

		@Override
		protected void executeWithParams(MachineState state, Object... args) throws ExecutionException {
			// If 2nd arg is more than value size (in bits) then return 0 to simulate all bits being shifted out of existence
			executeValueOperation(state, (a, b) -> b >= MAX_SHIFT ? 0 : a >>> b, args);
		}
	};

	public final byte value;
	private final OpCodeParam[] params;

	// Create a map of opcode values to OpCode
	private static final Map<Byte, OpCode> map = Arrays.stream(OpCode.values()).collect(Collectors.toMap(opcode -> opcode.value, opcode -> opcode));

	private OpCode(int value, OpCodeParam... params) {
		this.value = (byte) value;
		this.params = params;
	}

	public static OpCode valueOf(int value) {
		return map.get((byte) value);
	}

	/**
	 * Execute OpCode with args fetched from code bytes
	 * <p>
	 * Assumes <code>codeByteBuffer.position()</code> is already placed immediately after opcode and params.<br>
	 * <code>state.getProgramCounter()</code> is available to return position immediately before opcode and params.
	 * <p>
	 * OpCode execution can modify <code>codeByteBuffer.position()</code> in cases like jumps, branches, etc.
	 * <p>
	 * Can also modify <code>userStackByteBuffer</code> and various fields of <code>state</code>.
	 * <p>
	 * Throws a subclass of <code>ExecutionException</code> on error, e.g. <code>InvalidAddressException</code>.
	 * 
	 * @param state Machine state to work against
	 * @param args list of arg style objects
	 * 
	 * @throws ExecutionException from internal function this.executeWithParams
	 */
	protected abstract void executeWithParams(MachineState state, Object... args) throws ExecutionException;

	protected void preExecuteCheck(Object... args) throws ExecutionException {
		/* Can be overridden on a per-opcode basis */
	}

	/* package */ void execute(MachineState state) throws ExecutionException {
		List<Object> args = new ArrayList<>();

		for (OpCodeParam param : this.params)
			args.add(param.fetch(state.codeByteBuffer, state.dataByteBuffer));

		Object[] argsArray = args.toArray();

		preExecuteCheck(argsArray);

		this.executeWithParams(state, argsArray);
	}

	public static int calcOffset(ByteBuffer byteBuffer, Integer branchTarget) throws CompilationException {
		// First-pass of compilation where we don't know branchTarget yet
		if (branchTarget == null)
			return 0;

		int offset = branchTarget - byteBuffer.position();

		// Bounds checking
		if (offset < Byte.MIN_VALUE || offset > Byte.MAX_VALUE)
			throw new CompilationException(String.format("Branch offset %02x (from PC %04x) is wider than a byte", offset, byteBuffer.position()));

		return offset;
	}

	public byte[] compile(Object... args) throws CompilationException {
		if (args.length != this.params.length)
			throw new IllegalArgumentException(String.format("%s requires %d arg%s, but %d passed",
					this.name(),
					this.params.length,
					this.params.length != 1 ? "s" : "",
					args.length));

		ByteBuffer byteBuffer = ByteBuffer.allocate(32); // 32 should easily be enough

		byteBuffer.put(this.value);

		for (int i = 0; i < this.params.length; ++i)
			try {
				byteBuffer.put(this.params[i].compile(this, args[i]));
			} catch (ClassCastException e) {
				throw new CompilationException(String.format("%s arg[%d] could not coerced to required type: %s", this.name(), i, e.getMessage()));
			}

		byteBuffer.flip();

		byte[] bytes = new byte[byteBuffer.limit()];
		byteBuffer.get(bytes);

		return bytes;
	}

	/**
	 * Returns string representing disassembled OpCode and parameters
	 * 
	 * @param codeByteBuffer OpCode
	 * @param dataByteBuffer Params for OpCode
	 * @return String Easily readable export of OpCode and Params
	 * @throws ExecutionException Because it might go boom
	 */
	public String disassemble(ByteBuffer codeByteBuffer, ByteBuffer dataByteBuffer) throws ExecutionException {
		StringBuilder output = new StringBuilder(this.name());

		int postOpcodeProgramCounter = codeByteBuffer.position();

		for (OpCodeParam param : this.params) {
			output.append(" ");
			output.append(param.disassemble(codeByteBuffer, dataByteBuffer, postOpcodeProgramCounter));
		}

		return output.toString();
	}

	/**
	 * Common code for ADD_DAT/SUB_DAT/MUL_DAT/DIV_DAT/MOD_DAT/SHL_DAT/SHR_DAT
	 * 
	 * @param state
	 * @param operator
	 *            - typically a lambda operating on two <code>long</code> params, e.g. <code>(a, b) &rarr; a + b</code>
	 * @param args
	 * @throws ExecutionException
	 */
	protected void executeDataOperation(MachineState state, TwoValueOperator operator, Object... args) throws ExecutionException {
		int address1 = (int) args[0];
		int address2 = (int) args[1];

		long value1 = state.dataByteBuffer.getLong(address1);
		long value2 = state.dataByteBuffer.getLong(address2);

		long newValue = operator.apply(value1, value2);

		state.dataByteBuffer.putLong(address1, newValue);
	}

	/**
	 * Common code for ADD_VAL/SUB_VAL/MUL_VAL/DIV_VAL/MOD_VAL/SHL_VAL/SHR_VAL
	 *
	 * @param state
	 * @param operator
	 *            - typically a lambda operating on two <code>long</code> params, e.g. <code>(a, b) &rarr; a + b</code>
	 * @param args
	 * @throws ExecutionException
	 */
	protected void executeValueOperation(MachineState state, TwoValueOperator operator, Object... args) throws ExecutionException {
		int address1 = (int) args[0];

		long value1 = state.dataByteBuffer.getLong(address1);
		long value2 = (long) args[1];

		long newValue = operator.apply(value1, value2);

		state.dataByteBuffer.putLong(address1, newValue);
	}

	/**
	 * Common code for BGT/BLT/BGE/BLE/BEQ/BNE
	 * 
	 * @param state
	 * @param comparator
	 *            - typically a lambda comparing two <code>long</code> params, e.g. <code>(a, b) &rarr; a == b</code>
	 * @param args
	 * @throws ExecutionException
	 */
	protected void executeBranchConditional(MachineState state, TwoValueComparator comparator, Object... args) throws ExecutionException {
		int address1 = (int) args[0];
		int address2 = (int) args[1];
		byte offset = (byte) args[2];

		int branchTarget = calculateBranchTarget(state, offset);

		long value1 = state.dataByteBuffer.getLong(address1);
		long value2 = state.dataByteBuffer.getLong(address2);

		if (comparator.compare(value1, value2))
			state.codeByteBuffer.position(branchTarget);
	}

	protected int calculateBranchTarget(MachineState state, byte offset) throws ExecutionException {
		final int branchTarget = state.getProgramCounter() + offset;

		if (branchTarget < 0 || branchTarget >= state.codeByteBuffer.limit())
			throw new InvalidAddressException(String.format("%s code target PC(%04x) + %02x = %04x out of bounds: 0x0000 to 0x%04x",
					this.name(), state.getProgramCounter(), offset, branchTarget, state.codeByteBuffer.limit() - 1));

		return branchTarget;
	}

}
