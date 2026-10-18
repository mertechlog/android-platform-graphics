package com.example.manualvideo;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Android API 28+. No MediaPlayer, ExoPlayer, XML layout, or third-party libraries.
 * Video-only learning example: no audio, seeking UI, DRM, retries, or HDR policy.
 * MediaExtractor performs MP4 parsing through Android's native implementation.
 */
public final class VideoPlayerActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "ManualVideo";
    private static final String VIDEO_URL =
            "https://raw.githubusercontent.com/mertechlog/"
            + "android-platform-graphics/main/sample/test.mp4";

    private SurfaceView surfaceView;
    private Session session;
    private boolean resumed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        setContentView(surfaceView);
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        startIfReady();
    }

    @Override protected void onPause() {
        resumed = false;
        stopSession();
        super.onPause();
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { startIfReady(); }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { stopSession(); }

    private void startIfReady() {
        Surface surface = surfaceView.getHolder().getSurface();
        if (resumed && session == null && surface.isValid()) {
            session = new Session(surface);
            session.worker.start();
        }
    }

    private void stopSession() {
        Session old = session;
        session = null;
        if (old != null) old.cancel();
    }

    private final class Session {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final Surface surface;
        final Thread worker;
        volatile HttpRangeSource source;

        Session(Surface surface) {
            this.surface = surface;
            worker = new Thread(this::play, "MP4-extract-decode");
        }

        void cancel() {
            cancelled.set(true);
            worker.interrupt();
            // Network teardown must not block the UI thread.
            HttpRangeSource current = source;
            if (current != null) {
                new Thread(current::cancel, "MP4-network-cancel").start();
            }
        }

        void play() {
            MediaExtractor extractor = new MediaExtractor();
            MediaCodec codec = null;
            boolean started = false;
            try {
                // 1. APPLICATION-OWNED NETWORK SOURCE.
                source = new HttpRangeSource(VIDEO_URL, cancelled);
                if (cancelled.get()) return;
                // Prime the HTTP cache so Content-Range/Length can establish size
                // before the native extractor queries getSize().
                if (source.readAt(0, new byte[1], 0, 1) < 0) {
                    throw new EOFException("Empty video source");
                }

                // 2. NATIVE MP4 PARSER requests bytes through source.readAt().
                extractor.setDataSource(source);
                int track = -1;
                MediaFormat format = null;
                for (int i = 0; i < extractor.getTrackCount(); i++) {
                    MediaFormat candidate = extractor.getTrackFormat(i);
                    String mime = candidate.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("video/")) {
                        track = i;
                        format = candidate;
                        break;
                    }
                }
                if (track < 0 || format == null) throw new IOException("No video track");
                extractor.selectTrack(track);
                Log.i(TAG, "Extracted video format: " + format);

                // 3. SELECT A DEVICE DECODER using the extracted MIME/configuration.
                String decoderName = new MediaCodecList(MediaCodecList.REGULAR_CODECS)
                        .findDecoderForFormat(format);
                if (decoderName == null) throw new IOException("No decoder for " + format);
                Log.i(TAG, "Selected decoder: " + decoderName);
                codec = MediaCodec.createByCodecName(decoderName);
                codec.configure(format, surface, null, 0);
                codec.start();
                started = true;

                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                boolean inputEnded = false;
                boolean outputEnded = false;
                long firstPtsUs = Long.MIN_VALUE;
                long clockOriginNs = 0;

                while (!cancelled.get() && !outputEnded) {
                    // 4. EXTRACT ONE COMPRESSED SAMPLE into codec input memory.
                    if (!inputEnded) {
                        int in = codec.dequeueInputBuffer(10_000);
                        if (in >= 0) {
                            ByteBuffer codecInput = codec.getInputBuffer(in);
                            if (codecInput == null) throw new IOException("Missing codec input");
                            codecInput.clear();
                            long sampleSize = extractor.getSampleSize();
                            if (sampleSize > codecInput.remaining()) {
                                throw new IOException("Sample exceeds codec input capacity");
                            }
                            if ((extractor.getSampleFlags()
                                    & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) {
                                throw new IOException("This example does not support DRM");
                            }
                            int size = extractor.readSampleData(codecInput, 0);
                            if (size < 0) {
                                codec.queueInputBuffer(in, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputEnded = true;
                            } else {
                                long ptsUs = extractor.getSampleTime();
                                // Ordinary, unencrypted MP4 access units; codec-specific
                                // initialization data was already passed in MediaFormat.
                                codec.queueInputBuffer(in, 0, size, ptsUs, 0);
                                extractor.advance();
                            }
                        }
                    }

                    // 5. DRAIN DECODED OUTPUT. With Surface output there is no
                    // application-readable pixel ByteBuffer in this example.
                    int out = codec.dequeueOutputBuffer(info, 10_000);
                    if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.i(TAG, "Decoded format: " + codec.getOutputFormat());
                    } else if (out >= 0) {
                        boolean image = info.size > 0
                                && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0;
                        if (image && !cancelled.get()) {
                            if (firstPtsUs == Long.MIN_VALUE) {
                                firstPtsUs = info.presentationTimeUs;
                                clockOriginNs = System.nanoTime();
                            }
                            long targetNs = clockOriginNs
                                    + (info.presentationTimeUs - firstPtsUs) * 1000L;
                            // Standalone video clock. If loading starves playback,
                            // rebase the clock instead of racing through late frames.
                            long nowNs = System.nanoTime();
                            if (nowNs - targetNs > 250_000_000L) {
                                clockOriginNs += nowNs - targetNs;
                                targetNs = nowNs;
                            }
                            waitUntil(targetNs);
                            if (cancelled.get()) codec.releaseOutputBuffer(out, false);
                            else codec.releaseOutputBuffer(out, targetNs);
                        } else {
                            codec.releaseOutputBuffer(out, false);
                        }
                        outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    }
                }
                if (!cancelled.get()) Log.i(TAG, "Video playback completed");
            } catch (Exception e) {
                if (!cancelled.get()) {
                    Log.e(TAG, "Playback failed", e);
                    runOnUiThread(() -> {
                        if (session == this && resumed) {
                            Toast.makeText(VideoPlayerActivity.this,
                                    "Playback failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } finally {
                if (codec != null) {
                    if (started) {
                        try { codec.stop(); } catch (RuntimeException ignored) { }
                    }
                    try { codec.release(); } catch (RuntimeException ignored) { }
                }
                try { extractor.release(); } catch (RuntimeException ignored) { }
                if (source != null) source.close();
            }
        }

        private void waitUntil(long targetNs) throws InterruptedException {
            while (!cancelled.get()) {
                long remaining = targetNs - System.nanoTime();
                if (remaining <= 0) return;
                Thread.sleep(Math.min(remaining / 1_000_000L, 10L),
                        (int) (remaining % 1_000_000L));
            }
        }
    }

    /**
     * One-block HTTP byte-range cache controlled by the app.
     * Bytes remain MP4 container bytes here, NOT decoded pixels.
     * readAt may be called by framework callback threads, so it is synchronized.
     * Unknown file size is -1 as required by MediaDataSource.
     */
    private static final class HttpRangeSource extends MediaDataSource {
        private static final int BLOCK_SIZE = 256 * 1024;
        private final URL url;
        private final AtomicBoolean cancelled;
        private final byte[] httpBytes = new byte[BLOCK_SIZE];
        private long blockStart = -1;
        private int blockLength;
        private volatile long totalSize = -1;
        private volatile boolean closed;
        private volatile HttpURLConnection activeConnection;

        HttpRangeSource(String url, AtomicBoolean cancelled) throws IOException {
            this.url = new URL(url);
            this.cancelled = cancelled;
        }

        @Override public synchronized int readAt(long position, byte[] buffer,
                                                 int offset, int size) throws IOException {
            if (position < 0 || offset < 0 || size < 0 || size > buffer.length - offset) {
                throw new IllegalArgumentException("Invalid read bounds");
            }
            checkOpen();
            if (size == 0) return 0;
            if (totalSize >= 0 && position >= totalSize) return -1;
            if (blockStart < 0 || position < blockStart
                    || position >= blockStart + blockLength) {
                fetchBlock(position);
            }
            if (blockLength == 0) return -1;
            int inBlock = (int) (position - blockStart);
            int count = Math.min(size, blockLength - inBlock);
            // The framework supplies buffer. We copy file bytes from our cache
            // into it; the native extractor parses those bytes afterward.
            System.arraycopy(httpBytes, inBlock, buffer, offset, count);
            return count;
        }

        private void fetchBlock(long position) throws IOException {
            checkOpen();
            blockStart = -1;
            blockLength = 0;
            long end = position + BLOCK_SIZE - 1L;
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("Range", "bytes=" + position + "-" + end);
            connection.setRequestProperty("Accept-Encoding", "identity");
            activeConnection = connection;
            try {
                checkOpen();
                int status = connection.getResponseCode();
                long skip = 0;
                if (status == HttpURLConnection.HTTP_PARTIAL) {
                    String range = connection.getHeaderField("Content-Range");
                    if (range == null || !range.startsWith("bytes " + position + "-")) {
                        throw new IOException("Unexpected Content-Range: " + range);
                    }
                    int slash = range.lastIndexOf('/');
                    if (slash >= 0 && !range.substring(slash + 1).equals("*")) {
                        totalSize = Long.parseLong(range.substring(slash + 1));
                    }
                } else if (status == HttpURLConnection.HTTP_OK) {
                    // Server ignored Range: correct but inefficient fallback.
                    String length = connection.getHeaderField("Content-Length");
                    if (length != null) totalSize = Long.parseLong(length);
                    skip = position;
                } else if (status == 416) {
                    String range = connection.getHeaderField("Content-Range");
                    if (range != null && range.startsWith("bytes */")) {
                        totalSize = Long.parseLong(range.substring(8));
                        if (position >= totalSize) {
                            blockStart = position;
                            return;
                        }
                    }
                    throw new IOException("HTTP range rejected");
                } else {
                    throw new IOException("HTTP " + status);
                }
                try (InputStream input = connection.getInputStream()) {
                    while (skip > 0) {
                        checkOpen();
                        int n = input.read(httpBytes, 0, (int) Math.min(skip, BLOCK_SIZE));
                        if (n < 0) throw new EOFException("Cannot reach requested offset");
                        skip -= n;
                    }
                    int count = 0;
                    while (count < BLOCK_SIZE) {
                        checkOpen();
                        int n = input.read(httpBytes, count, BLOCK_SIZE - count);
                        if (n < 0) break;
                        count += n;
                    }
                    blockLength = count;
                    blockStart = position;
                    Log.d(TAG, "HTTP file bytes: offset=" + position + " count=" + count);
                }
            } finally {
                connection.disconnect();
                activeConnection = null;
            }
        }

        private void checkOpen() throws IOException {
            if (closed || cancelled.get() || Thread.currentThread().isInterrupted()) {
                throw new IOException("Source cancelled or closed");
            }
        }

        @Override public long getSize() { return totalSize; }
        @Override public void close() { cancel(); }
        void cancel() {
            closed = true;
            HttpURLConnection connection = activeConnection;
            if (connection != null) connection.disconnect();
        }
    }
}
