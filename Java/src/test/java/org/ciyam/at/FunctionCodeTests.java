package org.ciyam.at;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.ciyam.at.test.ExecutableTest;
import org.junit.Test;

public class FunctionCodeTests extends ExecutableTest {

	private static final byte[] TEST_BYTES = "This string is exactly 32 bytes!".getBytes();

	@Test
	public void testABGetSet() throws ExecutionException {
		int sourceAddress = 2;
		int destAddress = sourceAddress + MachineState.AB_REGISTER_SIZE / MachineState.VALUE_SIZE;

		// Address of source bytes
		dataByteBuffer.putLong(sourceAddress);
		// Address where to save bytes
		dataByteBuffer.putLong(destAddress);

		// Data to load into A (or B)
		assertEquals(sourceAddress * MachineState.VALUE_SIZE, dataByteBuffer.position());
		dataByteBuffer.put(TEST_BYTES);

		// Data saved from A (or B)
		assertEquals(destAddress * MachineState.VALUE_SIZE, dataByteBuffer.position());

		// Set A register using data pointed to by value held in address 0
		codeByteBuffer.put(OpCode.EXT_FUN_DAT.value).putShort(FunctionCode.SET_A_IND.value).putInt(0);
		codeByteBuffer.put(OpCode.EXT_FUN.value).putShort(FunctionCode.SWAP_A_AND_B.value);
		// Save B register to data segment starting at value held in address 1
		codeByteBuffer.put(OpCode.EXT_FUN_DAT.value).putShort(FunctionCode.GET_B_IND.value).putInt(1);
		codeByteBuffer.put(OpCode.FIN_IMD.value);

		execute(true);

		byte[] dest = new byte[TEST_BYTES.length];
		getDataBytes(destAddress, dest);
		assertTrue("Data wasn't copied correctly", Arrays.equals(TEST_BYTES, dest));

		assertTrue(state.isFinished());
		assertFalse(state.hadFatalError());
	}

	@Test
	public void testIncorrectFunctionCodeOldStyle() throws ExecutionException {
		// SET_B_IND should be EXT_FUN_DAT not EXT_FUN_RET
		codeByteBuffer.put(OpCode.EXT_FUN_RET.value).putShort(FunctionCode.SET_B_IND.value).putInt(0);
		codeByteBuffer.put(OpCode.FIN_IMD.value);

		execute(true);

		assertTrue(state.isFinished());
		assertTrue(state.hadFatalError());
	}

	@Test
	public void testIncorrectFunctionCodeNewStyle() throws ExecutionException {
		try {
			// SET_B_IND should be EXT_FUN_DAT not EXT_FUN_RET
			codeByteBuffer.put(OpCode.EXT_FUN_RET.compile(FunctionCode.SET_B_IND, 0));
			codeByteBuffer.put(OpCode.FIN_IMD.compile());

			execute(true);

			assertTrue(state.isFinished());
			assertTrue(state.hadFatalError());
		} catch (CompilationException e) {
			// Expected behaviour!
			return;
		}

		fail("Compilation was expected to fail as EXT_FUN_RET is incorrect for SET_B_IND");
	}

	@Test
	public void testInvalidFunctionCode() throws ExecutionException {
		codeByteBuffer.put(OpCode.EXT_FUN.value).putShort((short) 0xaaaa);
		codeByteBuffer.put(OpCode.FIN_IMD.value);

		execute(true);

		assertTrue(state.isFinished());
		assertTrue(state.hadFatalError());
	}

	@Test
	public void testPlatformSpecific0501() {
		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(0).putLong(Timestamp.toLong(api.getCurrentBlockHeight(), 0));
		codeByteBuffer.put(OpCode.EXT_FUN_DAT.value).putShort((short) 0x0501).putInt(0);
		codeByteBuffer.put(OpCode.FIN_IMD.value);

		execute(true);

		assertTrue(state.isFinished());
		assertFalse(state.hadFatalError());
	}

	@Test
	public void testPlatformSpecific0501Error() {
		codeByteBuffer.put(OpCode.SET_VAL.value).putInt(0).putLong(Timestamp.toLong(api.getCurrentBlockHeight(), 0));
		codeByteBuffer.put(OpCode.EXT_FUN_RET_DAT_2.value).putShort((short) 0x0501).putInt(0).putInt(0); // Wrong OPCODE for function
		codeByteBuffer.put(OpCode.FIN_IMD.value);

		execute(true);

		assertTrue(state.isFinished());
		assertTrue(state.hadFatalError());
	}

}
