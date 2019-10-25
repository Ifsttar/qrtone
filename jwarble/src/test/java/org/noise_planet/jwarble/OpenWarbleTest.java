package org.noise_planet.jwarble;

import com.backblaze.erasure.Galois;
import com.backblaze.erasure.ReedSolomon;
import org.junit.Assert;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class OpenWarbleTest {

    @Test
    public void generalized_goertzel() throws Exception {
        double sampleRate = 44100;
        double powerRMS = 500; // 90 dBspl
        float signalFrequency = 1000;
        double powerPeak = powerRMS * Math.sqrt(2);

        double[] audio = new double[4410];
        for (int s = 0; s < audio.length; s++) {
            double t = s * (1 / sampleRate);
            audio[s] = Math.sin(OpenWarble.M2PI * signalFrequency * t) * (powerPeak);
        }

        double[] rms = OpenWarble.generalized_goertzel(audio,0, audio.length, sampleRate, new double[]{1000.0});

        double signal_rms = OpenWarble.compute_rms(audio);

        Assert.assertEquals(signal_rms, rms[0], 0.1);
    }


    private static void writeShortToFile(String path, short[] signal) throws IOException {
        FileOutputStream fileOutputStream = new FileOutputStream(path);
        try {
            ByteBuffer byteBuffer = ByteBuffer.allocate(Short.SIZE / Byte.SIZE);
            for(int i = 0; i < signal.length; i++) {
                byteBuffer.putShort(0, signal[i]);
                fileOutputStream.write(byteBuffer.array());
            }
        } finally {
            fileOutputStream.close();
        }
    }

    private static void writeDoubleToFile(String path, double[] signal) throws IOException {
        short[] shortSignal = new short[signal.length];
        double maxValue = Double.MIN_VALUE;
        for (double aSignal : signal) {
            maxValue = Math.max(maxValue, aSignal);
        }
        maxValue *= 2;
        for(int i=0; i<signal.length;i++) {
            shortSignal[i] = (short)((signal[i] / maxValue) * Short.MAX_VALUE);
        }
        writeShortToFile(path, shortSignal);
    }

    public static short[] convertBytesToShort(byte[] buffer, int length, ByteOrder byteOrder) {
        ShortBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, length).order(byteOrder).asShortBuffer();
        short[] samplesShort = new short[byteBuffer.capacity()];
        byteBuffer.order();
        byteBuffer.get(samplesShort);
        return samplesShort;
    }

    public static short[] loadShortStream(InputStream inputStream, ByteOrder byteOrder) throws IOException {
        short[] fullArray = new short[0];
        byte[] buffer = new byte[4096];
        int read;
        // Read input signal up to buffer.length
        while ((read = inputStream.read(buffer)) != -1) {
            // Convert bytes into double values. Samples array size is 8 times inferior than buffer size
            if (read < buffer.length) {
                buffer = Arrays.copyOfRange(buffer, 0, read);
            }
            short[] signal = convertBytesToShort(buffer, buffer.length, byteOrder);
            short[] nextFullArray = new short[fullArray.length + signal.length];
            if(fullArray.length > 0) {
                System.arraycopy(fullArray, 0, nextFullArray, 0, fullArray.length);
            }
            System.arraycopy(signal, 0, nextFullArray, fullArray.length, signal.length);
            fullArray = nextFullArray;
        }
        return fullArray;
    }

    @Test
    public void testRecognitionWithoutNoise() throws IOException {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        double blankTime = 1.3;
        int blankSamples = (int)(blankTime * sampleRate);
        byte[] payload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate, false));
        UtCallback utCallback = new UtCallback(false);
        UtMessageCallback messageCallback = new UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        double[] signal = openWarble.generate_signal(powerPeak, payload);
        double[] allSignal = new double[blankSamples+signal.length+blankSamples];
        System.arraycopy(signal, 0, allSignal, blankSamples, signal.length);
        int cursor = 0;
        while (cursor < allSignal.length) {
            int len = Math.min(openWarble.getMaxPushSamplesLength(), allSignal.length - cursor);
            if(len == 0) {
                break;
            }
            openWarble.pushSamples(Arrays.copyOfRange(allSignal, cursor, cursor+len));
            cursor+=len;
        }
        assertTrue(Math.abs(blankSamples - messageCallback.pitchLocation) < openWarble.door_length / 4.0);
        assertArrayEquals(payload, messageCallback.payload);
        assertEquals(0, openWarble.getCorrectedErrors());
        //writeDoubleToFile("target/source.raw", allSignal);
    }


    @Test
    public void testRecognitionWithNoise() throws IOException {
        double sampleRate = 44100;
        double powerPeak = 1; // 90 dBspl
        double noisePeak = 0.1;
        double blankTime = 1.3;
        int blankSamples = (int)(blankTime * sampleRate);
        byte[] payload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(payload.length, sampleRate, false));
        UtCallback utCallback = new UtCallback(false);
        UtMessageCallback messageCallback = new UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        double[] signal = openWarble.generate_signal(powerPeak, payload);
        double[] allSignal = new double[blankSamples+signal.length+blankSamples];
        System.arraycopy(signal, 0, allSignal, blankSamples, signal.length);
        // Average with noise
        Random rand = new Random(1337);
        for(int i = 0; i < allSignal.length; i++) {
            allSignal[i] = (allSignal[i] + rand.nextGaussian() * noisePeak) / 2.0;
        }
        long start = System.nanoTime();
        int cursor = 0;
        while (cursor < allSignal.length) {
            int len = Math.min(openWarble.getMaxPushSamplesLength(), allSignal.length - cursor);
            if (len == 0) {
                break;
            }
            openWarble.pushSamples(Arrays.copyOfRange(allSignal, cursor, cursor + len));
            cursor += len;
        }
        System.out.println(String.format("Execution time %.3f seconds", (System.nanoTime() - start) / 1e9));
        //writeDoubleToFile("target/test.raw", allSignal);
        assertTrue(Math.abs(blankSamples - messageCallback.pitchLocation) < openWarble.door_length / 4.0);
        assertArrayEquals(payload, messageCallback.payload);
        assertEquals(0, openWarble.getCorrectedErrors());
    }


    @Test
    public void testWithRecordedAudio() throws IOException {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate, false));
        UtCallback utCallback = new UtCallback(true);
        UtMessageCallback messageCallback = new UtMessageCallback();
        openWarble.setCallback(messageCallback);
        openWarble.setUnitTestCallback(utCallback);
        short[] signal_short;
        try (InputStream inputStream = OpenWarbleTest.class.getResourceAsStream("with_noise_44100hz_mono_16bits.raw")) {
            signal_short = loadShortStream(inputStream, ByteOrder.LITTLE_ENDIAN);
        }
        // Push audio samples to OpenWarble
        int cursor = 0;
        while (cursor < signal_short.length) {
            int len = Math.min(openWarble.getMaxPushSamplesLength(), signal_short.length - cursor);
            if(len == 0) {
                break;
            }
            double[] window = new double[len];
            for(int i = cursor; i < cursor + len; i++) {
                window[i - cursor] = signal_short[i] / (double)Short.MAX_VALUE;
            }
            openWarble.pushSamples(window);
            cursor+=len;
        }
        assertArrayEquals(expectedPayload, messageCallback.payload);
    }

    private static class UtMessageCallback implements MessageCallback {
        public long pitchLocation = -1;
        public byte[] payload;

        @Override
        public void onNewMessage(byte[] payload, long sampleId) {
            this.payload = payload;
        }

        @Override
        public void onPitch(long sampleId) {
            if(pitchLocation < 0) {
                pitchLocation = sampleId;
            }
        }

        @Override
        public void onError(long sampleId) {

        }
    }

    private static class UtCallback implements OpenWarble.UnitTestCallback {

        boolean print;

        public UtCallback(boolean print) {
            this.print = print;
        }

        @Override
        public void generateWord(byte word, int encodedWord, boolean[] frequencies) {
            if(print) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < frequencies.length; i++) {
                    if (frequencies[i]) {
                        if (sb.length() != 0) {
                            sb.append(", ");
                        }
                        sb.append(i);
                    }
                }
                System.out.println(String.format("New word %02x %s", word, sb.toString()));
            }
        }

        @Override
        public void detectWord(Hamming12_8.CorrectResult result, int encodedWord, boolean[] frequencies) {
            if(print) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < frequencies.length; i++) {
                    if (frequencies[i]) {
                        if (sb.length() != 0) {
                            sb.append(", ");
                        }
                        sb.append(i);
                    }
                }
                if (result.result == Hamming12_8.CorrectResultCode.CORRECTED_ERROR) {
                    int code = Hamming12_8.encode(result.value);
                    StringBuilder wrongFrequencies = new StringBuilder();
                    for(int idfreq = 0; idfreq < OpenWarble.NUM_FREQUENCIES; idfreq++) {
                        if ((code & (1 << idfreq)) != 0 && !frequencies[idfreq]) {
                            wrongFrequencies.append(idfreq);
                        } else if((code & (1 << idfreq)) == 0 && frequencies[idfreq]) {
                            wrongFrequencies.append(idfreq);
                        }
                    }
                    System.out.println(String.format("Fixed new word %02x %s [%s]", result.value, sb.toString(), wrongFrequencies.toString()));
                } else {
                    System.out.println(String.format("New word %02x %s", result.value, sb.toString()));
                }
            }
        }
    }

    @Test
    public void randTest() {
        // This specific random must give the same results regardless of the platform/compiler
        int[] expected= new int[] {1199,22292,14258,30291,11005,15335,22572,27361,8276,27653};
        AtomicLong seed = new AtomicLong(1337);
        for(int expectedValue : expected) {
            assertEquals(expectedValue, OpenWarble.warble_rand(seed));
        }
    }

    @Test(expected = AssertionError.class)
    public void testShuffle1() {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        expectedPayload = Arrays.copyOf(expectedPayload, openWarble.block_length);
        byte[] test = Arrays.copyOf(expectedPayload, expectedPayload.length);
        openWarble.swapChars(test, openWarble.shuffleIndex);
        assertArrayEquals(expectedPayload, test);
    }

    @Test
    public void testShuffle() {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        expectedPayload = Arrays.copyOf(expectedPayload, openWarble.block_length);
        byte[] test = Arrays.copyOf(expectedPayload, expectedPayload.length);
        openWarble.swapChars(test, openWarble.shuffleIndex);
        openWarble.unswapChars(test, openWarble.shuffleIndex);
        assertArrayEquals(expectedPayload, test);
    }

    @Test
    public void testRSEncode() {
        double sampleRate = 44100;
        byte[] expectedPayload = new byte[] {18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        OpenWarble openWarble = new OpenWarble(Configuration.getAudible(expectedPayload.length, sampleRate));
        byte[] blocks = openWarble.encodeReedSolomon(expectedPayload);

        ////////////////////////////////////////////////
        ////////////////////////////////////////////////
        // Check encoding

        System.out.println(Arrays.toString(Arrays.copyOfRange(blocks, expectedPayload.length, blocks.length)));
    }

    @Test
    public void crcTest() {
        byte[] expectedPayload = new byte[]{18, 32, -117, -93, -50, 2, 52, 26, -117, 93, 119, -109, 39, 46, 108, 4, 31, 36, -100, 95, -9, -70, -82, -93, -75, -32, -63, 42, -44, -100, 50, 83, -118, 114};
        byte base = OpenWarble.crc8(expectedPayload, 0, expectedPayload.length);
        AtomicLong next = new AtomicLong(1337);
        for(int i=0; i < expectedPayload.length; i++) {
            byte[] alteredPayload = Arrays.copyOf(expectedPayload, expectedPayload.length);
            alteredPayload[i] = (byte) (OpenWarble.warble_rand(next) % 255);
            assertNotEquals(base, OpenWarble.crc8(alteredPayload, 0, alteredPayload.length));
        }
    }

    @Test
    public void testCorruption() {
        ReedSolomon reedSolomon = ReedSolomon.create(4, 2);
        byte[] [] shards = {
                new byte[] {'A', 'B', 'C', 'D'},
                new byte[] {'E', 'F', 'G', 'H'},
                new byte[] {'I', 'J', 'K', 'L'},
                new byte[] {'M', 'N', 'O', 'P'},
                new byte[] {0, 0, 0, 0},
                new byte[] {0, 0, 0, 0}
        };
        reedSolomon.encodeParity(shards, 0, shards[0].length);
        System.out.println("Original: ");
        for(byte[] shard : shards) {
            for(byte b : shard) {
                System.out.print(String.format("%2x ", b));
            }
            System.out.print("\n");
        }
        assertTrue(reedSolomon.isParityCorrect(shards, 0, shards[0].length));

        // Corrupt in diagonal
        shards[0][0] = 'B';
        shards[1][1] = 'O';
        shards[2][2] = 'R';
        shards[3][3] = 'G';

        assertFalse(reedSolomon.isParityCorrect(shards, 0, shards[0].length));


        System.out.println("Corrupted: ");
        for(byte[] shard : shards) {
            for(byte b : shard) {
                System.out.print(String.format("%2x ", b));
            }
            System.out.print("\n");
        }

        byte[] [] repair_shards = new byte[shards.length][];
        int c = 0;
        for(int i=0; i<shards.length; i++) {
            repair_shards[i] = new byte[] {shards[i][c]};
        }

        reedSolomon.decodeMissing(repair_shards, new boolean[] {false, true, true, true, true, true}, 0, 1);

        System.out.println("Fixed: ");
        for(byte[] shard : repair_shards) {
            for(byte b : shard) {
                System.out.print(String.format("%2x ", b));
            }
            System.out.print("\n");
        }
    }
}