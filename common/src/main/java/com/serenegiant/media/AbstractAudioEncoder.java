package com.serenegiant.media;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2021 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import android.annotation.TargetApi;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;

import androidx.annotation.NonNull;

/**
 * 音声データをMediaCodecを使ってAACエンコードするための基本クラス
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public abstract class AbstractAudioEncoder extends AbstractEncoder
	implements IAudioEncoder {
//	private static final boolean DEBUG = false;	// FIXME 実働時にはfalseにすること
//	private static final String TAG = "AbstractAudioEncoder";

	public static final int DEFAULT_SAMPLE_RATE = 44100;	// 44.1[KHz]	8-48[kHz] 全機種で保証されているのは44100だけ
	public static final int DEFAULT_BIT_RATE = 64000;		// 64[kbps]		5-320[kbps]
    public static final int SAMPLES_PER_FRAME = 1024;		// AAC, bytes/frame/channel
	public static final int FRAMES_PER_BUFFER = 25; 		// AAC, frame/mBuffer/sec

    protected int mAudioSource;
    protected int mChannelCount;
	protected int mSampleRate;
    protected int mBitRate;

	/**
	 * コンストラクタ
	 * @param recorder
	 * @param listener
	 * @param audio_source
	 * @param audio_channels
	 */
	@Deprecated
	public AbstractAudioEncoder(
		@NonNull final IRecorder recorder,
		@NonNull final EncoderListener listener,
		final int audio_source, final int audio_channels) {

		this(recorder, listener, audio_source, audio_channels, DEFAULT_SAMPLE_RATE, DEFAULT_BIT_RATE);
	}

	/**
	 * コンストラクタ
	 * @param recorder
	 * @param listener
	 * @param audio_source
	 * @param audio_channels
	 * @param sample_rate
	 * @param bit_rate
	 */
	public AbstractAudioEncoder(
		@NonNull final IRecorder recorder,
		@NonNull final EncoderListener listener,
		final int audio_source, final int audio_channels, final int sample_rate, final int bit_rate) {

		super(MediaCodecUtils.MIME_AUDIO_AAC, recorder, listener);
//		if (DEBUG) Log.v(TAG, "コンストラクタ:");
		mAudioSource = audio_source;
		mChannelCount = audio_channels;
		mSampleRate = sample_rate;
		mBitRate = bit_rate;
	}

	@Override
	protected boolean internalPrepare(@NonNull final MediaReaper.ReaperListener listener) throws Exception {
//		if (DEBUG) Log.v(TAG, "internalPrepare:");
        mTrackIndex = -1;

// 音声を取り込んでAACにエンコードするためのMediaCodecの準備
        final MediaCodecInfo audioCodecInfo = MediaCodecUtils.selectAudioEncoder(MIME_TYPE);
        if (audioCodecInfo == null) {
//			Log.e(TAG, "Unable to find an appropriate codec for " + MIME_TYPE);
            return true;
        }
//		if (DEBUG) Log.i(TAG, "selected codec: " + audioCodecInfo.getName());

        final MediaFormat audioFormat = MediaFormat.createAudioFormat(MIME_TYPE, mSampleRate, mChannelCount);
		audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		audioFormat.setInteger(MediaFormat.KEY_CHANNEL_MASK,
			mChannelCount == 1 ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO);
		audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
		audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, mChannelCount);
//		audioFormat.setLong(MediaFormat.KEY_MAX_INPUT_SIZE, inputFile.length());
//      audioFormat.setLong(MediaFormat.KEY_DURATION, (long)durationInMs );
//		if (DEBUG) Log.i(TAG, "format: " + audioFormat);

		mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mMediaCodec.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mMediaCodec.start();
        mReaper = new MediaReaper.AudioReaper(mMediaCodec, listener, mSampleRate, mChannelCount);
//		if (DEBUG) Log.i(TAG, "internalPrepare:finished");
		return false;
	}

	@Override
	public final boolean isAudio() {
		return true;
	}

}
