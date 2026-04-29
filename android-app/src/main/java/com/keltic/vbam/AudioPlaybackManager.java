package com.krendstudio.krendbuoy;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

final class AudioPlaybackManager {
    private volatile boolean audioRunning;
    private Thread audioThread;
    private AudioTrack audioTrack;

    void start() {
        stop();
        int sampleRate = NativeBridge.getAudioSampleRate();
        int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferBytes = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        if (bufferBytes <= 0) bufferBytes = Math.max(4096, sampleRate / 4);

        audioTrack = new AudioTrack(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                        .build(),
                new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(audioFormat)
                        .setChannelMask(channelConfig)
                        .build(),
                bufferBytes,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );
        audioTrack.play();
        audioRunning = true;
        int shortBufferSize = Math.max(512, bufferBytes / 4);

        audioThread = new Thread(() -> {
            short[] buffer = new short[shortBufferSize];
            while (audioRunning) {
                int count = NativeBridge.readAudioSamples(buffer, buffer.length);
                if (count > 0 && audioTrack != null) {
                    audioTrack.write(buffer, 0, count);
                } else {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        audioRunning = false;
                    }
                }
            }
        }, "VBAM-audio-loop-helper");
        audioThread.start();
    }

    void stop() {
        audioRunning = false;
        if (audioThread != null) {
            audioThread.interrupt();
            audioThread = null;
        }
        if (audioTrack != null) {
            try {
                audioTrack.pause();
                audioTrack.flush();
                audioTrack.release();
            } catch (Throwable ignored) {
            }
            audioTrack = null;
        }
    }
}
