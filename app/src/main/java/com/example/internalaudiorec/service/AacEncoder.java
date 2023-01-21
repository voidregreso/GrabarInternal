package com.example.internalaudiorec.service;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AacEncoder {
    private int mSampleRate;
    private int mInChannel = AudioFormat.CHANNEL_IN_MONO;
    private int mSampleFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int mChannelCount;
    private String mDstFilePath;
    private String mSrcFilePath;
    MediaCodec mEncorder;
    ExecutorService mExecutorService;
    private int mBufferSize;
    private final IHandlerCallback mCallback;


    public AacEncoder(int sampleRate,
                     int channelCount,
                     int keyBitRate,
                     String srcPath, String dstPath,
                     IHandlerCallback callback) {

        mSampleRate = sampleRate;
        mChannelCount = channelCount;
        mSrcFilePath = srcPath;
        mDstFilePath = dstPath;
        mExecutorService = Executors.newCachedThreadPool();
        mCallback = callback;
        mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mInChannel, mSampleFormat);
        Log.e("yuanBuffer", "buffer_size=" + mBufferSize + " nb_sample=" + mBufferSize / 4);
        try {
            mEncorder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        } catch (Exception e) {
            e.printStackTrace();
        }
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                mSampleRate,
                mChannelCount);
        format.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, keyBitRate);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mBufferSize);

        mEncorder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }
    
    public void startEncoding() {
        mExecutorService.execute(this::startEncodingV1);
    }

    public void startEncodingV1() {
        if (mEncorder == null) {
            if (mCallback != null) {
                mCallback.onFail();
            }
            return;
        }
        mEncorder.start();
        try {
            FileInputStream inputStream = new FileInputStream(mSrcFilePath);
            FileOutputStream mFileStream = new FileOutputStream(mDstFilePath);
            MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
            long a = System.currentTimeMillis();
            while (true) {
                // Recuperar un fotograma de datos de audio grabados de la cola
                byte[] byteArray = new byte[mBufferSize/2];
                int readSize = inputStream.read(byteArray);
                if (readSize <= 0) {
                    break;
                }
                ByteBuffer buf = ByteBuffer.wrap(byteArray);
                // El InputBuffer se obtiene, se llena con datos de audio y luego se envía al codificador para su codificación
                int inputBufferIndex = mEncorder.dequeueInputBuffer(0);
                if (inputBufferIndex >= 0) {
                    ByteBuffer inputBuffer = mEncorder.getInputBuffer(inputBufferIndex);
                    inputBuffer.clear();
                    inputBuffer.put(buf);
                    mEncorder.queueInputBuffer(inputBufferIndex, 0, readSize, System.nanoTime(), 0);
                }

                // Toma la trama codificada de datos de audio y añade una cabecera ADTS a la trama
                int outputBufferIndex = mEncorder.dequeueOutputBuffer(mBufferInfo, 0);
                while (outputBufferIndex >= 0) {
                    ByteBuffer outputBuffer = mEncorder.getOutputBuffer(outputBufferIndex);
                    int outBufferSize = outputBuffer.limit() + 7;
                    byte[] aacBytes = new byte[outBufferSize];
                    addADTStoPacket(aacBytes, outBufferSize);
                    outputBuffer.get(aacBytes, 7, outputBuffer.limit());
                    mFileStream.write(aacBytes);

                    mEncorder.releaseOutputBuffer(outputBufferIndex, false);
                    outputBufferIndex = mEncorder.dequeueOutputBuffer(mBufferInfo, 0);
                }
            }
            long b = System.currentTimeMillis() - a;
            Log.i("AudioEncode", "Consumed " + b + "ms");
            try {
                mFileStream.flush();
                mFileStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                mFileStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (mEncorder != null) {
                mEncorder.stop();
            }
            if (mCallback != null) {
                mCallback.onSuccess();
            }
        } catch (IOException e) {
            if (mCallback != null) {
                mCallback.onFail();
            }
            e.printStackTrace();
        }
        if (mEncorder == null) {
            return;
        }
        mEncorder.release();
        mEncorder = null;
        new File(mSrcFilePath).delete();
    }


    /**
     * Add ADTS header at the beginning of each and every AAC packet.
     * This is needed as MediaCodec encoder generates a packet of raw
     * AAC data.
     * <p>
     * Note the packetLen must count in the ADTS header itself !!! .
     **/
    private void addADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC，MediaCodecInfo.CodecProfileLevel.AACObjectLC;
        int freqIdx = 4;
        int chanCfg = 1;

        /*int avpriv_mpeg4audio_sample_rates[] = {96000, 88200, 64000, 48000, 44100, 32000,24000, 22050, 16000, 12000, 11025, 8000, 7350};
        channel_configuration: Indica el número de canales de sonido chanCfg
        0: Defined in AOT Specifc Config
        1: 1 channel: front-center
        2: 2 channels: front-left, front-right
        3: 3 channels: front-center, front-left, front-right
        4: 4 channels: front-center, front-left, front-right, back-center
        5: 5 channels: front-center, front-left, front-right, back-left, back-right
        6: 6 channels: front-center, front-left, front-right, back-left, back-right, LFE-channel
        7: 8 channels: front-center, front-left, front-right, side-left, side-right, back-left, back-right, LFE-channel
        8-15: Reserved
        */

        // fill in ADTS data
        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF1;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;
    }
}