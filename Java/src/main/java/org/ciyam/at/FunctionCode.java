package org.ciyam.at;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This enum contains function codes for the CIYAM AT machine.
 * <p>
 * Function codes are represented by a short. Functions can take 0 to 2 additional long values and optionally return a value too.
 * <p>
 * FunctionCode instances can be obtained via the default <code>FunctionCode.valueOf(String)</code> or the additional <code>FunctionCode.valueOf(int)</code>.
 * <p>
 * Use the <code>FunctionCode.execute</code> method to perform the operation.
 * <p>
 * For more details, view the <a href="http://ciyam.org/at/at_api.html">API Specification</a>.
 * 
 * @see FunctionCode#valueOf(int)
 * @see FunctionCode#execute(FunctionData, MachineState, short)
 */
public enum FunctionCode {
	/**
	 * <b>ECHO</b> value to logger<br>
	 * <code>0x0001 value</code>
	 */
	ECHO(0x0001, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			String message = String.valueOf(functionData.value1);
			state.getLogger().echo(message);
		}
	},
	/**
	 * <code>0x0100</code><br>
	 * Returns A1 value
	 */
	GET_A1(0x0100, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.a1;
		}
	},
	/**
	 * <code>0x0101</code><br>
	 * Returns A2 value
	 */
	GET_A2(0x0101, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.a2;
		}
	},
	/**
	 * <code>0x0102</code><br>
	 * Returns A3 value
	 */
	GET_A3(0x0102, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.a3;
		}
	},
	/**
	 * <code>0x0103</code><br>
	 * Returns A4 value
	 */
	GET_A4(0x0103, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.a4;
		}
	},
	/**
	 * <code>0x0104</code><br>
	 * Returns B1 value
	 */
	GET_B1(0x0104, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.b1;
		}
	},
	/**
	 * <code>0x0105</code><br>
	 * Returns B2 value
	 */
	GET_B2(0x0105, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.b2;
		}
	},
	/**
	 * <code>0x0106</code><br>
	 * Returns B3 value
	 */
	GET_B3(0x0106, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.b3;
		}
	},
	/**
	 * <code>0x0107</code><br>
	 * Returns B4 value
	 */
	GET_B4(0x0107, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.b4;
		}
	},
	/**
	 * Copies A into addr to addr+3<br>
	 * <code>0x0108 addr</code>
	 */
	GET_A_IND(0x0108, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// Validate data offset in arg1
			checkDataAddress(state, functionData.value1, 4);

			int dataIndex = (int) (functionData.value1 & Integer.MAX_VALUE);

			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.a1);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.a2);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.a3);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.a4);
		}
	},
	/**
	 * Copies B into addr to addr+3<br>
	 * <code>0x0109 addr</code>
	 */
	GET_B_IND(0x0109, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// Validate data offset in arg1
			checkDataAddress(state, functionData.value1, 4);

			int dataIndex = (int) (functionData.value1 & Integer.MAX_VALUE);

			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.b1);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.b2);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.b3);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.b4);
		}
	},
	/**
	 * Copies A into addr to addr+3<br>
	 * <code>0x010a addr</code><br>
	 * To be used by calling EXT_FUN_VAL, passing destination address as <b>value</b>
	 */
	GET_A_DAT(0x010a, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// Validate data offset in arg1
			checkDataAddress(state, functionData.value1, 4);

			int dataIndex = (int) (functionData.value1 & Integer.MAX_VALUE);

			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.a1);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.a2);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.a3);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.a4);
		}
	},
	/**
	 * Copies B into addr to addr+3<br>
	 * <code>0x010b addr</code><br>
	 * To be used by calling EXT_FUN_VAL, passing destination address as <b>value</b>
	 */
	GET_B_DAT(0x010b, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// Validate data offset in arg1
			checkDataAddress(state, functionData.value1, 4);

			int dataIndex = (int) (functionData.value1 & Integer.MAX_VALUE);

			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.b1);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.b2);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.b3);
			state.dataByteBuffer.putLong(dataIndex++ * MachineState.VALUE_SIZE, state.b4);
		}
	},
	/**
	 * Set A1<br>
	 * <code>0x0110 value</code>
	 */
	SET_A1(0x0110, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a1 = functionData.value1;
		}
	},
	/**
	 * Set A2<br>
	 * <code>0x0111 value</code>
	 */
	SET_A2(0x0111, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a2 = functionData.value1;
		}
	},
	/**
	 * Set A3<br>
	 * <code>0x0112 value</code>
	 */
	SET_A3(0x0112, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a3 = functionData.value1;
		}
	},
	/**
	 * Set A4<br>
	 * <code>0x0113 value</code>
	 */
	SET_A4(0x0113, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a4 = functionData.value1;
		}
	},
	/**
	 * Set A1 and A2<br>
	 * <code>0x0114 value value</code>
	 */
	SET_A1_A2(0x0114, 2, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a1 = functionData.value1;
			state.a2 = functionData.value2;
		}
	},
	/**
	 * Set A3 and A4<br>
	 * <code>0x0115 value value</code>
	 */
	SET_A3_A4(0x0115, 2, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a3 = functionData.value1;
			state.a4 = functionData.value2;
		}
	},
	/**
	 * Set B1<br>
	 * <code>0x0116 value</code>
	 */
	SET_B1(0x0116, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b1 = functionData.value1;
		}
	},
	/**
	 * Set B2<br>
	 * <code>0x0117 value</code>
	 */
	SET_B2(0x0117, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b2 = functionData.value1;
		}
	},
	/**
	 * Set B3<br>
	 * <code>0x0118 value</code>
	 */
	SET_B3(0x0118, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b3 = functionData.value1;
		}
	},
	/**
	 * Set B4<br>
	 * <code>0x0119 value</code>
	 */
	SET_B4(0x0119, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b4 = functionData.value1;
		}
	},
	/**
	 * Set B1 and B2<br>
	 * <code>0x011a value value</code>
	 */
	SET_B1_B2(0x011a, 2, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b1 = functionData.value1;
			state.b2 = functionData.value2;
		}
	},
	/**
	 * Set B3 and B4<br>
	 * <code>0x011b value value</code>
	 */
	SET_B3_B4(0x011b, 2, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b3 = functionData.value1;
			state.b4 = functionData.value2;
		}
	},
	/**
	 * Copies addr to addr+3 into A<br>
	 * <code>0x011c addr</code>
	 */
	SET_A_IND(0x011c, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// Validate data offset in arg1
			checkDataAddress(state, functionData.value1, 4);

			int dataIndex = (int) (functionData.value1 & Integer.MAX_VALUE);

			state.a1 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.a2 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.a3 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.a4 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
		}
	},
	/**
	 * Copies addr to addr+3 into B<br>
	 * <code>0x011d addr</code>
	 */
	SET_B_IND(0x011d, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// Validate data offset in arg1
			checkDataAddress(state, functionData.value1, 4);

			int dataIndex = (int) (functionData.value1 & Integer.MAX_VALUE);

			state.b1 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.b2 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.b3 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.b4 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
		}
	},
	/**
	 * Copies addr to addr+3 into A<br>
	 * <code>0x011e addr</code><br>
	 * To be used by calling EXT_FUN_VAL, passing source address as <b>value</b>
	 */
	SET_A_DAT(0x011e, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// Validate data offset in arg1
			checkDataAddress(state, functionData.value1, 4);

			int dataIndex = (int) (functionData.value1 & Integer.MAX_VALUE);

			state.a1 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.a2 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.a3 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.a4 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
		}
	},
	/**
	 * Copies addr to addr+3 into B<br>
	 * <code>0x011f addr</code><br>
	 * To be used by calling EXT_FUN_VAL, passing source address as <b>value</b>
	 */
	SET_B_DAT(0x011f, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// Validate data offset in arg1
			checkDataAddress(state, functionData.value1, 4);

			int dataIndex = (int) (functionData.value1 & Integer.MAX_VALUE);

			state.b1 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.b2 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.b3 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
			state.b4 = state.dataByteBuffer.getLong(dataIndex++ * MachineState.VALUE_SIZE);
		}
	},
	/**
	 * Clear A<br>
	 * <code>0x0120</code>
	 */
	CLEAR_A(0x0120, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a1 = 0;
			state.a2 = 0;
			state.a3 = 0;
			state.a4 = 0;
		}
	},
	/**
	 * Clear B<br>
	 * <code>0x0121</code>
	 */
	CLEAR_B(0x0121, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b1 = 0;
			state.b2 = 0;
			state.b3 = 0;
			state.b4 = 0;
		}
	},
	/**
	 * Clear A and B<br>
	 * <code>0x0122</code>
	 */
	CLEAR_A_AND_B(0x0122, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a1 = 0;
			state.a2 = 0;
			state.a3 = 0;
			state.a4 = 0;
			state.b1 = 0;
			state.b2 = 0;
			state.b3 = 0;
			state.b4 = 0;
		}
	},
	/**
	 * Copy A from B<br>
	 * <code>0x0123</code>
	 */
	COPY_A_FROM_B(0x0123, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a1 = state.b1;
			state.a2 = state.b2;
			state.a3 = state.b3;
			state.a4 = state.b4;
		}
	},
	/**
	 * Copy B from A<br>
	 * <code>0x0124</code>
	 */
	COPY_B_FROM_A(0x0124, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b1 = state.a1;
			state.b2 = state.a2;
			state.b3 = state.a3;
			state.b4 = state.a4;
		}
	},
	/**
	 * Check A is zero<br>
	 * <code>0x0125</code><br>
	 * Returns 1 if true, 0 if false
	 */
	CHECK_A_IS_ZERO(0x0125, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			if (state.a1 == 0 && state.a2 == 0 && state.a3 == 0 && state.a4 == 0)
				functionData.returnValue = 1L; // true
			else
				functionData.returnValue = 0L; // false
		}
	},
	/**
	 * Check B is zero<br>
	 * <code>0x0126</code><br>
	 * Returns 1 if true, 0 if false
	 */
	CHECK_B_IS_ZERO(0x0126, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			if (state.b1 == 0 && state.b2 == 0 && state.b3 == 0 && state.b4 == 0)
				functionData.returnValue = 1L; // true
			else
				functionData.returnValue = 0L; // false
		}
	},
	/**
	 * Check A equals B<br>
	 * <code>0x0127</code><br>
	 * Returns 1 if true, 0 if false
	 */
	CHECK_A_EQUALS_B(0x0127, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			if (state.a1 == state.b1 && state.a2 == state.b2 && state.a3 == state.b3 && state.a4 == state.b4)
				functionData.returnValue = 1L; // true
			else
				functionData.returnValue = 0L; // false
		}
	},
	/**
	 * Swap A with B<br>
	 * <code>0x0128</code>
	 */
	SWAP_A_AND_B(0x0128, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			long tmp1 = state.a1;
			long tmp2 = state.a2;
			long tmp3 = state.a3;
			long tmp4 = state.a4;

			state.a1 = state.b1;
			state.a2 = state.b2;
			state.a3 = state.b3;
			state.a4 = state.b4;

			state.b1 = tmp1;
			state.b2 = tmp2;
			state.b3 = tmp3;
			state.b4 = tmp4;
		}
	},
	/**
	 * Bitwise-OR A with B<br>
	 * <code>0x0129</code><br>
	 * A = A | B
	 */
	OR_A_WITH_B(0x0129, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a1 = state.a1 | state.b1;
			state.a2 = state.a2 | state.b2;
			state.a3 = state.a3 | state.b3;
			state.a4 = state.a4 | state.b4;
		}
	},
	/**
	 * Bitwise-OR B with A<br>
	 * <code>0x012a</code><br>
	 * B = B | A
	 */
	OR_B_WITH_A(0x012a, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b1 = state.b1 | state.a1;
			state.b2 = state.b2 | state.a2;
			state.b3 = state.b3 | state.a3;
			state.b4 = state.b4 | state.a4;
		}
	},
	/**
	 * Bitwise-AND A with B<br>
	 * <code>0x012b</code><br>
	 * A = A &amp; B
	 */
	AND_A_WITH_B(0x012b, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a1 = state.a1 & state.b1;
			state.a2 = state.a2 & state.b2;
			state.a3 = state.a3 & state.b3;
			state.a4 = state.a4 & state.b4;
		}
	},
	/**
	 * Bitwise-AND B with A<br>
	 * <code>0x012c</code><br>
	 * B = B &amp; A
	 */
	AND_B_WITH_A(0x012c, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b1 = state.b1 & state.a1;
			state.b2 = state.b2 & state.a2;
			state.b3 = state.b3 & state.a3;
			state.b4 = state.b4 & state.a4;
		}
	},
	/**
	 * Bitwise-XOR A with B<br>
	 * <code>0x012d</code><br>
	 * A = A ^ B
	 */
	XOR_A_WITH_B(0x012d, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.a1 = state.a1 ^ state.b1;
			state.a2 = state.a2 ^ state.b2;
			state.a3 = state.a3 ^ state.b3;
			state.a4 = state.a4 ^ state.b4;
		}
	},
	/**
	 * Bitwise-XOR B with A<br>
	 * <code>0x012e</code><br>
	 * B = B ^ A
	 */
	XOR_B_WITH_A(0x012e, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.b1 = state.b1 ^ state.a1;
			state.b2 = state.b2 ^ state.a2;
			state.b3 = state.b3 ^ state.a3;
			state.b4 = state.b4 ^ state.a4;
		}
	},
	/**
	 * Unsigned compare A with B<br>
	 * <code>0x0130</code><br>
	 * Returns -1, 0 or 1 depending on whether A is less than, equal or greater than B.
	 */
	UNSIGNED_COMPARE_A_WITH_B(0x0130, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			int result = Long.compareUnsigned(state.a1, state.b1);

			if (result == 0)
				result = Long.compareUnsigned(state.a2, state.b2);

			if (result == 0)
				result = Long.compareUnsigned(state.a3, state.a3);

			if (result == 0)
				result = Long.compareUnsigned(state.a4, state.a4);

			functionData.returnValue = Long.valueOf(result);
		}
	},
	/**
	 * Signed compare A with B<br>
	 * <code>0x0131</code><br>
	 * Returns -1, 0 or 1 depending on whether A is less than, equal or greater than B.
	 */
	SIGNED_COMPARE_A_WITH_B(0x0131, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			int result = Long.compare(state.a1, state.b1);

			if (result == 0)
				result = Long.compare(state.a2, state.b2);

			if (result == 0)
				result = Long.compare(state.a3, state.a3);

			if (result == 0)
				result = Long.compare(state.a4, state.a4);

			functionData.returnValue = Long.valueOf(result);
		}
	},
	/**
	 * MD5 data into B<br>
	 * <code>0x0200 start-addr byte-length</code><br>
	 * MD5 hash stored in B1 and B2. B3 and B4 are zeroed.
	 */
	MD5_INTO_B(0x0200, 2, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			byte[] message = getHashData(functionData, state);

			try {
				MessageDigest digester = MessageDigest.getInstance("MD5");
				byte[] digest = digester.digest(message);

				ByteBuffer digestByteBuffer = ByteBuffer.wrap(digest);

				state.b1 = digestByteBuffer.getLong();
				state.b2 = digestByteBuffer.getLong();
				state.b3 = 0L;
				state.b4 = 0L;
			} catch (NoSuchAlgorithmException e) {
				throw new ExecutionException("No MD5 message digest service available", e);
			}
		}
	},
	/**
	 * Check MD5 of data matches B<br>
	 * <code>0x0201 start-addr byte-length</code><br>
	 * Other MD5 hash is in B1 and B2. B3 and B4 are ignored.<br>
	 * Returns 1 if true, 0 if false
	 */
	CHECK_MD5_WITH_B(0x0201, 2, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			byte[] message = getHashData(functionData, state);

			try {
				MessageDigest digester = MessageDigest.getInstance("MD5");
				byte[] actualDigest = digester.digest(message);

				ByteBuffer digestByteBuffer = ByteBuffer.allocate(digester.getDigestLength());

				digestByteBuffer.putLong(state.b1);
				digestByteBuffer.putLong(state.b2);

				byte[] expectedDigest = digestByteBuffer.array();

				if (Arrays.equals(actualDigest, expectedDigest))
					functionData.returnValue = 1L; // true
				else
					functionData.returnValue = 0L; // false
			} catch (NoSuchAlgorithmException e) {
				throw new ExecutionException("No MD5 message digest service available", e);
			}
		}
	},
	/**
	 * RIPE-MD160 data into B<br>
	 * <code>0x0202 start-addr byte-length</code><br>
	 * RIPE-MD160 hash stored in LSB of B1 and all of B2 and B3. B4 is zeroed.
	 */
	RMD160_INTO_B(0x0202, 2, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			byte[] message = getHashData(functionData, state);

			try {
				MessageDigest digester = MessageDigest.getInstance("RIPEMD160");
				byte[] digest = digester.digest(message);

				ByteBuffer digestByteBuffer = ByteBuffer.wrap(digest);

				state.b1 = digestByteBuffer.getLong();
				state.b2 = digestByteBuffer.getLong();
				state.b3 = ((long) digestByteBuffer.getInt()) << 32; // MSB of B3
				state.b4 = 0L;
			} catch (NoSuchAlgorithmException e) {
				throw new ExecutionException("No RIPEMD160 message digest service available", e);
			}
		}
	},
	/**
	 * Check RIPE-MD160 of data matches B<br>
	 * <code>0x0203 start-addr byte-length</code><br>
	 * Other RIPE-MD160 hash is in LSB of B1 and all of B2 and B3. B4 is ignored.<br>
	 * Returns 1 if true, 0 if false
	 */
	CHECK_RMD160_WITH_B(0x0203, 2, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			byte[] message = getHashData(functionData, state);

			try {
				MessageDigest digester = MessageDigest.getInstance("RIPEMD160");
				byte[] actualDigest = digester.digest(message);

				ByteBuffer digestByteBuffer = ByteBuffer.allocate(digester.getDigestLength());

				digestByteBuffer.putLong(state.b1);
				digestByteBuffer.putLong(state.b2);
				digestByteBuffer.putInt((int) (state.b3 >>> 32)); // MSB of B3
				// NOTE: b4 ignored

				byte[] expectedDigest = digestByteBuffer.array();

				if (Arrays.equals(actualDigest, expectedDigest))
					functionData.returnValue = 1L; // true
				else
					functionData.returnValue = 0L; // false
			} catch (NoSuchAlgorithmException e) {
				throw new ExecutionException("No RIPEMD160 message digest service available", e);
			}
		}
	},
	/**
	 * SHA256 data into B<br>
	 * <code>0x0204 start-addr byte-length</code><br>
	 * SHA256 hash is stored in B1 through B4.
	 */
	SHA256_INTO_B(0x0204, 2, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			byte[] message = getHashData(functionData, state);

			try {
				MessageDigest digester = MessageDigest.getInstance("SHA-256");
				byte[] digest = digester.digest(message);

				ByteBuffer digestByteBuffer = ByteBuffer.wrap(digest);

				state.b1 = digestByteBuffer.getLong();
				state.b2 = digestByteBuffer.getLong();
				state.b3 = digestByteBuffer.getLong();
				state.b4 = digestByteBuffer.getLong();
			} catch (NoSuchAlgorithmException e) {
				throw new ExecutionException("No SHA-256 message digest service available", e);
			}
		}
	},
	/**
	 * Check SHA256 of data matches B<br>
	 * <code>0x0205 start-addr byte-length</code><br>
	 * Other SHA256 hash is in B1 through B4.<br>
	 * Returns 1 if true, 0 if false
	 */
	CHECK_SHA256_WITH_B(0x0205, 2, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			byte[] message = getHashData(functionData, state);

			try {
				MessageDigest digester = MessageDigest.getInstance("SHA-256");
				byte[] actualDigest = digester.digest(message);

				ByteBuffer digestByteBuffer = ByteBuffer.allocate(digester.getDigestLength());

				digestByteBuffer.putLong(state.b1);
				digestByteBuffer.putLong(state.b2);
				digestByteBuffer.putLong(state.b3);
				digestByteBuffer.putLong(state.b4);

				byte[] expectedDigest = digestByteBuffer.array();

				if (Arrays.equals(actualDigest, expectedDigest))
					functionData.returnValue = 1L; // true
				else
					functionData.returnValue = 0L; // false
			} catch (NoSuchAlgorithmException e) {
				throw new ExecutionException("No SHA256 message digest service available", e);
			}
		}
	},
	/**
	 * HASH160 data into B<br>
	 * <code>0x0206 start-addr byte-length</code><br>
	 * Bitcoin's HASH160 hash is equivalent to RMD160(SHA256(data)).<br>
	 * HASH160 hash stored in LSB of B1 and all of B2 and B3. B4 is zeroed.
	 */
	HASH160_INTO_B(0x0206, 2, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			byte[] message = getHashData(functionData, state);

			try {
				MessageDigest sha256Digester = MessageDigest.getInstance("SHA-256");
				byte[] sha256Digest = sha256Digester.digest(message);

				MessageDigest rmd160Digester = MessageDigest.getInstance("RIPEMD160");
				byte[] rmd160Digest = rmd160Digester.digest(sha256Digest);

				ByteBuffer digestByteBuffer = ByteBuffer.wrap(rmd160Digest);

				state.b1 = digestByteBuffer.getLong();
				state.b2 = digestByteBuffer.getLong();
				state.b3 = ((long) digestByteBuffer.getInt()) << 32; // MSB of B3
				state.b4 = 0L;
			} catch (NoSuchAlgorithmException e) {
				throw new ExecutionException("No SHA-256 or RIPEMD160 message digest service available", e);
			}
		}
	},
	/**
	 * Check HASH160 of data matches B<br>
	 * <code>0x0207 start-addr byte-length</code><br>
	 * Other HASH160 hash is in B1, B2 and LSB of B3. B4 is ignored.
	 * Returns 1 if true, 0 if false
	 */
	CHECK_HASH160_WITH_B(0x0207, 2, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			byte[] message = getHashData(functionData, state);

			try {
				MessageDigest sha256Digester = MessageDigest.getInstance("SHA-256");
				byte[] sha256Digest = sha256Digester.digest(message);

				MessageDigest rmd160Digester = MessageDigest.getInstance("RIPEMD160");
				byte[] rmd160Digest = rmd160Digester.digest(sha256Digest);

				ByteBuffer digestByteBuffer = ByteBuffer.allocate(rmd160Digester.getDigestLength());

				digestByteBuffer.putLong(state.b1);
				digestByteBuffer.putLong(state.b2);
				digestByteBuffer.putInt((int) (state.b3 >>> 32)); // MSB of B3
				// NOTE: b4 ignored

				byte[] expectedDigest = digestByteBuffer.array();

				if (Arrays.equals(rmd160Digest, expectedDigest))
					functionData.returnValue = 1L; // true
				else
					functionData.returnValue = 0L; // false
			} catch (NoSuchAlgorithmException e) {
				throw new ExecutionException("No SHA-256 or RIPEMD160 message digest service available", e);
			}
		}
	},
	/**
	 * <code>0x0300</code><br>
	 * Returns current block's "timestamp"
	 */
	GET_BLOCK_TIMESTAMP(0x0300, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = Timestamp.toLong(state.getAPI().getCurrentBlockHeight(), 0);
		}
	},
	/**
	 * <code>0x0301</code><br>
	 * Returns AT's creation block's "timestamp"
	 */
	GET_CREATION_TIMESTAMP(0x0301, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = Timestamp.toLong(state.getAPI().getATCreationBlockHeight(state), 0);
		}
	},
	/**
	 * <code>0x0302</code><br>
	 * Returns previous block's "timestamp"
	 */
	GET_PREVIOUS_BLOCK_TIMESTAMP(0x0302, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = Timestamp.toLong(state.getAPI().getPreviousBlockHeight(), 0);
		}
	},
	/**
	 * Put previous block's hash in A<br>
	 * <code>0x0303</code>
	 */
	PUT_PREVIOUS_BLOCK_HASH_INTO_A(0x0303, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.getAPI().putPreviousBlockHashIntoA(state);
		}
	},
	/**
	 * Put transaction (to this AT) after timestamp in A, or zero if none<br>
	 * <code>0x0304 timestamp</code><br>
	 * a-k-a "A_To_Tx_After_Timestamp"
	 */
	PUT_TX_AFTER_TIMESTAMP_INTO_A(0x0304, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.getAPI().putTransactionAfterTimestampIntoA(new Timestamp(functionData.value1), state);
		}
	},
	/**
	 * <code>0x0305</code><br>
	 * Return transaction type from transaction in A<br>
	 * Returns 0xffffffffffffffff in A not valid transaction
	 */
	GET_TYPE_FROM_TX_IN_A(0x0305, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.getAPI().getTypeFromTransactionInA(state);
		}
	},
	/**
	 * <code>0x0306</code><br>
	 * Return transaction amount from transaction in A<br>
	 * Returns 0xffffffffffffffff in A not valid transaction
	 */
	GET_AMOUNT_FROM_TX_IN_A(0x0306, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.getAPI().getAmountFromTransactionInA(state);
		}
	},
	/**
	 * <code>0x0307</code><br>
	 * Return transaction timestamp from transaction in A<br>
	 * Returns 0xffffffffffffffff in A not valid transaction
	 */
	GET_TIMESTAMP_FROM_TX_IN_A(0x0307, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.getAPI().getTimestampFromTransactionInA(state);
		}
	},
	/**
	 * Generate random number using transaction in A<br>
	 * <code>0x0308</code><br>
	 * Returns 0xffffffffffffffff in A not valid transaction<br>
	 * Can sleep to use next block as source of entropy
	 */
	GENERATE_RANDOM_USING_TX_IN_A(0x0308, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.getAPI().generateRandomUsingTransactionInA(state);

			// If API set isSleeping then rewind program counter (actually codeByteBuffer) ready for being awoken
			if (state.isSleeping()) {
				// EXT_FUN_RET(1) + our function code(2) + address(4)
				state.rewindCodePosition(MachineState.OPCODE_SIZE + MachineState.FUNCTIONCODE_SIZE + MachineState.ADDRESS_SIZE);

				// If specific sleep height not set, default to next block
				if (state.getSleepUntilHeight() == null)
					state.setSleepUntilHeight(state.getCurrentBlockHeight() + 1);
			}
		}
	},
	/**
	 * Put 'message' from transaction in A into B<br>
	 * <code>0x0309</code><br>
	 * If transaction has no 'message' then zero B<br>
	 * Example 'message' could be 256-bit shared secret
	 */
	PUT_MESSAGE_FROM_TX_IN_A_INTO_B(0x0309, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.getAPI().putMessageFromTransactionInAIntoB(state);
		}
	},
	/**
	 * Put sender/creator address from transaction in A into B<br>
	 * <code>0x030a</code>
	 */
	PUT_ADDRESS_FROM_TX_IN_A_INTO_B(0x030a, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.getAPI().putAddressFromTransactionInAIntoB(state);
		}
	},
	/**
	 * Put AT's creator's address into B<br>
	 * <code>0x030b</code>
	 */
	PUT_CREATOR_INTO_B(0x030b, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.getAPI().putCreatorAddressIntoB(state);
		}
	},
	/**
	 * <code>0x0400</code><br>
	 * Returns AT's current balance
	 */
	GET_CURRENT_BALANCE(0x0400, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.getCurrentBalance();
		}
	},
	/**
	 * <code>0x0401</code><br>
	 * Returns AT's previous balance at end of last execution round<br>
	 * Does not include any amounts sent to AT since
	 */
	GET_PREVIOUS_BALANCE(0x0401, 0, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.getPreviousBalance();
		}
	},
	/**
	 * Pay fee-inclusive amount to account address in B<br>
	 * <code>0x0402 amount</code><br>
	 * Reduces amount to current balance rather than failing due to insufficient funds
	 */
	PAY_TO_ADDRESS_IN_B(0x0402, 1, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// Reduce amount to current balance if insufficient funds to pay full amount in value1
			long amount = Math.min(state.getCurrentBalance(), functionData.value1);

			// Actually pay
			state.getAPI().payAmountToB(amount, state);

			// Update current balance to reflect payment
			state.setCurrentBalance(state.getCurrentBalance() - amount);

			// With no balance left, this AT is effectively finished?
			if (state.getCurrentBalance() == 0)
				state.setIsFinished(true);
		}
	},
	/**
	 * Pay all remaining funds to account address in B<br>
	 * <code>0x0403</code>
	 */
	PAY_ALL_TO_ADDRESS_IN_B(0x0403, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.getAPI().payAmountToB(state.getCurrentBalance(), state);

			// With no balance left, this AT is effectively finished?
			state.setCurrentBalance(0);
			state.setIsFinished(true);
		}
	},
	/**
	 * Pay previous balance to account address in B<br>
	 * <code>0x0404</code><br>
	 * Reduces amount to current balance rather than failing due to insufficient funds
	 */
	PAY_PREVIOUS_TO_ADDRESS_IN_B(0x0404, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// Reduce amount to previous balance if insufficient funds to pay previous balance amount
			long amount = Math.min(state.getCurrentBalance(), state.getPreviousBalance());

			// Actually pay
			state.getAPI().payAmountToB(amount, state);

			// Update current balance to reflect payment
			state.setCurrentBalance(state.getCurrentBalance() - amount);

			// With no balance left, this AT is effectively finished?
			if (state.getCurrentBalance() == 0)
				state.setIsFinished(true);
		}
	},
	/**
	 * Send A as a message to address in B<br>
	 * <code>0x0405</code>
	 */
	MESSAGE_A_TO_ADDRESS_IN_B(0x0405, 0, false) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			state.getAPI().messageAToB(state);
		}
	},
	/**
	 * Add minutes to timestamp<br>
	 * <code>0x0406 timestamp minutes</code><br>
	 * Return 'timestamp' based on passed 'timestamp' plus minutes
	 */
	ADD_MINUTES_TO_TIMESTAMP(0x0406, 2, true) {
		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			functionData.returnValue = state.getAPI().addMinutesToTimestamp(new Timestamp(functionData.value1), functionData.value2, state);
		}
	},
	/**
	 * <code>0x0500 - 0x06ff</code><br>
	 * Platform-specific functions.<br>
	 * These are passed through to the API
	 */
	API_PASSTHROUGH(0x0500, 0, false) {
		@Override
		public void preExecuteCheck(int paramCount, boolean returnValueExpected) throws ExecutionException {
			// We don't know so skip checks at this point
		}

		@Override
		protected void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
			// XXX somehow we need to call something like this:
			// state.getAPI().platformSpecificPreExecuteCheck(functionData.paramCount, functionData.returnValueExpected, rawFunctionCode);

			state.getAPI().platformSpecificPostCheckExecute(functionData, state, rawFunctionCode);
		}
	};

	public final short value;
	public final int paramCount;
	public final boolean returnsValue;

	private static final Map<Short, FunctionCode> map = Arrays.stream(FunctionCode.values())
			.collect(Collectors.toMap(functionCode -> functionCode.value, functionCode -> functionCode));

	private FunctionCode(int value, int paramCount, boolean returnsValue) {
		this.value = (short) value;
		this.paramCount = paramCount;
		this.returnsValue = returnsValue;
	}

	public static FunctionCode valueOf(int value) {
		// Platform-specific?
		if (value >= 0x0500 && value <= 0x06ff)
			return API_PASSTHROUGH;

		return map.get((short) value);
	}

	public void preExecuteCheck(int paramCount, boolean returnValueExpected) throws ExecutionException {
		if (paramCount != this.paramCount)
			throw new IllegalFunctionCodeException(
					"Passed paramCount (" + paramCount + ") does not match function's required paramCount (" + this.paramCount + ")");

		if (returnValueExpected != this.returnsValue)
			throw new IllegalFunctionCodeException(
					"Passed returnValueExpected (" + returnValueExpected + ") does not match function's return signature (" + this.returnsValue + ")");
	}

	/**
	 * Execute Function
	 * <p>
	 * Can modify various fields of <code>state</code>, including <code>programCounter</code>.
	 * <p>
	 * Throws a subclass of <code>ExecutionException</code> on error, e.g. <code>InvalidAddressException</code>.
	 *
	 * @throws ExecutionException
	 */
	public void execute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException {
		// Check passed functionData against requirements of this function
		preExecuteCheck(functionData.paramCount, functionData.returnValueExpected);

		if (functionData.paramCount >= 1 && functionData.value1 == null)
			throw new IllegalFunctionCodeException("Passed value1 is null but function has paramCount of (" + this.paramCount + ")");

		if (functionData.paramCount == 2 && functionData.value2 == null)
			throw new IllegalFunctionCodeException("Passed value2 is null but function has paramCount of (" + this.paramCount + ")");

		state.getLogger().debug(() -> String.format("Function \"%s\"", this.name()));

		postCheckExecute(functionData, state, rawFunctionCode);
	}

	/** Actually execute function after checks have completed. */
	protected abstract void postCheckExecute(FunctionData functionData, MachineState state, short rawFunctionCode) throws ExecutionException;

	// TODO: public abstract String disassemble();

	protected byte[] getHashData(FunctionData functionData, MachineState state) throws ExecutionException {
		// Validate data offset in arg1
		checkDataAddress(state, functionData.value1, 1);

		// Validate data length in arg2
		if (functionData.value2 < 0L || functionData.value2 > Integer.MAX_VALUE || functionData.value1 + byteLengthToDataLength(functionData.value2) > state.numDataPages)
			throw new ExecutionException(this.name() + " data length invalid");

		final int dataStart = (int) (functionData.value1 & Integer.MAX_VALUE);
		final int dataLength = (int) (functionData.value2 & Integer.MAX_VALUE);

		byte[] message = new byte[dataLength];

		ByteBuffer messageByteBuffer = state.dataByteBuffer.asReadOnlyBuffer();
		messageByteBuffer.position(dataStart * MachineState.VALUE_SIZE);
		messageByteBuffer.limit(dataStart * MachineState.VALUE_SIZE + dataLength);

		messageByteBuffer.get(message);

		return message;
	}

	/** Returns the number of data-page values to contain specific length of bytes. */
	protected int byteLengthToDataLength(long byteLength) {
		return (MachineState.VALUE_SIZE - 1 + (int) (byteLength & Integer.MAX_VALUE)) / MachineState.VALUE_SIZE;
	}

	/** Check that data segment address (+ subsequent locations) are within data segment bounds. */
	protected void checkDataAddress(MachineState state, long address, int count) throws ExecutionException {
		final int maxAddress = state.numDataPages - count;

		if (address < 0L || address > maxAddress)
			throw new ExecutionException(String.format("%s data address %d out of bounds: 0 to %d", this.name(), address, maxAddress));
	}

}
