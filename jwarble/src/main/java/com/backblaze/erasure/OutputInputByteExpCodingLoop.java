/**
 * One specific ordering/nesting of the coding loops.
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Backblaze
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.backblaze.erasure;

public class OutputInputByteExpCodingLoop extends CodingLoopBase {

    @Override
    public void codeSomeShards(
            byte[][] matrixRows,
            byte[][] inputs, int inputCount,
            byte[][] outputs, int outputCount,
            int offset, int byteCount) {

        for (int iOutput = 0; iOutput < outputCount; iOutput++) {
            final byte [] outputShard = outputs[iOutput];
            final byte [] matrixRow = matrixRows[iOutput];
            {
                final int iInput = 0;
                final byte [] inputShard = inputs[iInput];
                final byte matrixByte = matrixRow[iInput];
                for (int iByte = offset; iByte < offset + byteCount; iByte++) {
                    outputShard[iByte] = Galois.multiply(matrixByte, inputShard[iByte]);
                }
            }
            for (int iInput = 1; iInput < inputCount; iInput++) {
                final byte [] inputShard = inputs[iInput];
                final byte matrixByte = matrixRow[iInput];
                for (int iByte = offset; iByte < offset + byteCount; iByte++) {
                    outputShard[iByte] ^= Galois.multiply(matrixByte, inputShard[iByte]);
                }
            }
        }
    }

}