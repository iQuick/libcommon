package com.serenegiant.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;

import com.serenegiant.glutils.es2.GLDrawer2D;
import com.serenegiant.utils.BuildCheck;
import com.serenegiant.utils.HandlerThreadHandler;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class CameraDelegator {
	private static final boolean DEBUG = true; // TODO set false on release
	private static final String TAG = CameraDelegator.class.getSimpleName();

	public static final int PREVIEW_WIDTH = 1280;
	public static final int PREVIEW_HEIGHT = 720;

	public interface OnFrameAvailableListener {
		public void onFrameAvailable();
	}

	@NonNull
	private final GLSurfaceView mView;

	private static final int TARGET_FPS_MS = 60 * 1000;
	private static final int CAMERA_ID = 0;

	private static final int SCALE_STRETCH_FIT = 0;
	private static final int SCALE_KEEP_ASPECT_VIEWPORT = 1;
	private static final int SCALE_KEEP_ASPECT = 2;
	private static final int SCALE_CROP_CENTER = 3;

	private final CameraSurfaceRenderer mRenderer;
	private final Set<OnFrameAvailableListener> mListeners
		= new CopyOnWriteArraySet<>();
	private CameraHandler mCameraHandler = null;
	private int mVideoWidth, mVideoHeight;
	private int mRotation;
	private int mScaleMode = SCALE_STRETCH_FIT;

	/**
	 * コンストラクタ
	 * @param view
	 */
	public CameraDelegator(@NonNull final GLSurfaceView view) {
		mView = view;
		mRenderer = new CameraSurfaceRenderer(this);
		// XXX GLES30はAPI>=18以降なんだけどAPI=18でもGLコンテキスト生成に失敗する端末があるのでAP1>=21に変更
		view.setEGLContextClientVersion((Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) ? 3 : 2);	// GLES20 API >= 8, GLES30 API>=18
		view.setRenderer(mRenderer);
		final SurfaceHolder holder = view.getHolder();
		holder.addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceCreated(final SurfaceHolder holder) {
				// do nothing
			}

			@Override
			public void surfaceChanged(final SurfaceHolder holder, final int format, final int width, final int height) {
				// do nothing
			}

			@Override
			public void surfaceDestroyed(final SurfaceHolder holder) {
				CameraDelegator.this.surfaceDestroyed(holder);
			}
		});
		mVideoWidth = PREVIEW_WIDTH;
		mVideoHeight = PREVIEW_HEIGHT;
	}

	/**
	 * GLSurfaceView#onResumeが呼ばれたときの処理
	 */
	public void onResume() {
		if (DEBUG) Log.v(TAG, "onResume:");
		if (mRenderer.mHasSurface) {
			if (mCameraHandler == null) {
				if (DEBUG) Log.v(TAG, "surface already exist");
				startPreview(mView.getWidth(),  mView.getHeight());
			}
		}
	}

	/**
	 * GLSurfaceView#onPauseが呼ばれたときの処理
	 */
	public void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
		if (mCameraHandler != null) {
			// just request stop previewing
			mCameraHandler.stopPreview(true);
		}
	}

	public void addListener(final OnFrameAvailableListener listener) {
		if (DEBUG) Log.v(TAG, "addListener:" + listener);
		if (listener != null) {
			mListeners.add(listener);
		}
	}

	public void removeListener(final OnFrameAvailableListener listener) {
		if (DEBUG) Log.v(TAG, "removeListener:" + listener);
		mListeners.remove(listener);
	}

	public void callOnFrameAvailable() {
		for (final OnFrameAvailableListener listener: mListeners) {
			try {
				listener.onFrameAvailable();
			} catch (final Exception e) {
				mListeners.remove(listener);
			}
		}
	}

	/**
	 * スケールモードをセット
	 * @param mode
	 */
	public void setScaleMode(final int mode) {
		if (DEBUG) Log.v(TAG, "setScaleMode:" + mode);
		if (mScaleMode != mode) {
			mScaleMode = mode;
			mView.queueEvent(new Runnable() {
				@Override
				public void run() {
					mRenderer.updateViewport();
				}
			});
		}
	}

	/**
	 * 現在のスケールモードを取得
	 * @return
	 */
	public int getScaleMode() {
		if (DEBUG) Log.v(TAG, "getScaleMode:" + mScaleMode);
		return mScaleMode;
	}

	/**
	 * カメラ映像サイズを変更要求
	 * @param width
	 * @param height
	 */
	@SuppressWarnings("SuspiciousNameCombination")
	public void setVideoSize(final int width, final int height) {
		if (DEBUG) Log.v(TAG, String.format("setVideoSize:(%dx%d)", width, height));
		if ((mRotation % 180) == 0) {
			mVideoWidth = width;
			mVideoHeight = height;
		} else {
			mVideoWidth = height;
			mVideoHeight = width;
		}
		mView.queueEvent(new Runnable() {
			@Override
			public void run() {
				mRenderer.updateViewport();
			}
		});
	}

	/**
	 * カメラ映像幅を取得
	 * @return
	 */
	public int getWidth() {
		return mVideoWidth;
	}

	/**
	 * カメラ映像高さを取得
	 * @return
	 */
	public int getHeight() {
		return mVideoHeight;
	}

	protected abstract SurfaceTexture getInputSurfaceTexture();

	/**
	 * プレビュー表示用Surfaceを追加
	 * @param id
	 * @param surface
	 * @param isRecordable
	 */
	public abstract void addSurface(final int id, final Object surface,
		final boolean isRecordable);

	/**
	 * プレビュー表示用Surfaceを除去
	 * @param id
	 */
	public abstract void removeSurface(final int id);
//--------------------------------------------------------------------------------
	private synchronized void startPreview(final int width, final int height) {
		if (DEBUG) Log.v(TAG, String.format("startPreview:(%dx%d)", width, height));
		if (mCameraHandler == null) {
			final CameraThread thread = new CameraThread(this);
			thread.start();
			mCameraHandler = thread.getHandler();
		}
		mCameraHandler.startPreview(PREVIEW_WIDTH, PREVIEW_HEIGHT);
	}

	@Nullable
	private SurfaceTexture getSurfaceTexture() {
		if (DEBUG) Log.v(TAG, "getSurfaceTexture:");
		return mRenderer != null ? mRenderer.mSTexture : null;
	}

	/**
	 * GLSurfaceView#surfaceDestroyedが呼ばれた時の処理
	 * @param holder
	 */
	private void surfaceDestroyed(final SurfaceHolder holder) {
		if (DEBUG) Log.v(TAG, "surfaceDestroyed:");
		if (mCameraHandler != null) {
			// wait for finish previewing here
			// otherwise camera try to display on un-exist Surface and some error will occur
			mCameraHandler.stopPreview(true);
		}
		mCameraHandler = null;
		mRenderer.onSurfaceDestroyed();
	}

	/**
	 * GLSurfaceViewのRenderer
	 */
	private static final class CameraSurfaceRenderer
		implements GLSurfaceView.Renderer,
			SurfaceTexture.OnFrameAvailableListener {	// API >= 11

		private final WeakReference<CameraDelegator> mWeakParent;
		private SurfaceTexture mSTexture;	// API >= 11
		private int hTex;
		private GLDrawer2D mDrawer;
		private final float[] mStMatrix = new float[16];
		private final float[] mMvpMatrix = new float[16];
		private boolean mHasSurface;

		public CameraSurfaceRenderer(final CameraDelegator parent) {
			if (DEBUG) Log.v(TAG, "CameraSurfaceRenderer:");
			mWeakParent = new WeakReference<CameraDelegator>(parent);
			Matrix.setIdentityM(mMvpMatrix, 0);
		}

		@Override
		public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
			if (DEBUG) Log.v(TAG, "onSurfaceCreated:");
			// This renderer required OES_EGL_image_external extension
			final String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);	// API >= 8
//			if (DEBUG) Log.i(TAG, "onSurfaceCreated:Gl extensions: " + extensions);
			if (!extensions.contains("OES_EGL_image_external"))
				throw new RuntimeException("This system does not support OES_EGL_image_external.");
			mDrawer = new GLDrawer2D(true);
			// create texture ID
			hTex = mDrawer.initTex();
			// create SurfaceTexture with texture ID.
			mSTexture = new SurfaceTexture(hTex);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				mSTexture.setOnFrameAvailableListener(this, HandlerThreadHandler.createHandler(TAG));
			} else {
				mSTexture.setOnFrameAvailableListener(this);
			}
			// clear screen with yellow color so that you can see rendering rectangle
			GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f);
			mHasSurface = true;
			// create object for preview display
			mDrawer.setMvpMatrix(mMvpMatrix, 0);
		}

		@Override
		public void onSurfaceChanged(final GL10 unused, final int width, final int height) {
			if (DEBUG) Log.v(TAG, String.format("onSurfaceChanged:(%d,%d)", width, height));
			// if at least with or height is zero, initialization of this view is still progress.
			if ((width == 0) || (height == 0)) return;
			updateViewport();
			final CameraDelegator parent = mWeakParent.get();
			if (parent != null) {
				parent.startPreview(width, height);
			}
		}

		/**
		 * when GLSurface context is soon destroyed
		 */
		public void onSurfaceDestroyed() {
			if (DEBUG) Log.v(TAG, "onSurfaceDestroyed:");
			mHasSurface = false;
			if (mDrawer != null) {
				mDrawer.deleteTex(hTex);
				mDrawer.release();
				mDrawer = null;
			}
			if (mSTexture != null) {
				mSTexture.release();
				mSTexture = null;
			}
		}

		private final void updateViewport() {
			if (DEBUG) Log.v(TAG, "updateViewport:");
			final CameraDelegator parent = mWeakParent.get();
			if (parent != null) {
				final int view_width = parent.mView.getWidth();
				final int view_height = parent.mView.getHeight();
				GLES20.glViewport(0, 0, view_width, view_height);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
				final double video_width = parent.mVideoWidth;
				final double video_height = parent.mVideoHeight;
				if (video_width == 0 || video_height == 0) return;
				Matrix.setIdentityM(mMvpMatrix, 0);
				final double view_aspect = view_width / (double)view_height;
				Log.i(TAG, String.format("view(%d,%d)%f,video(%1.0f,%1.0f)", view_width, view_height, view_aspect, video_width, video_height));
				switch (parent.mScaleMode) {
				case SCALE_STRETCH_FIT:
					break;
				case SCALE_KEEP_ASPECT_VIEWPORT:
				{
					final double req = video_width / video_height;
					int x, y;
					int width, height;
					if (view_aspect > req) {
						// if view is wider than camera image, calc width of drawing area based on view height
						y = 0;
						height = view_height;
						width = (int)(req * view_height);
						x = (view_width - width) / 2;
					} else {
						// if view is higher than camera image, calc height of drawing area based on view width
						x = 0;
						width = view_width;
						height = (int)(view_width / req);
						y = (view_height - height) / 2;
					}
					// set viewport to draw keeping aspect ration of camera image
					if (DEBUG) Log.v(TAG, String.format("xy(%d,%d),size(%d,%d)", x, y, width, height));
					GLES20.glViewport(x, y, width, height);
					break;
				}
				case SCALE_KEEP_ASPECT:
				case SCALE_CROP_CENTER:
				{
					final double scale_x = view_width / video_width;
					final double scale_y = view_height / video_height;
					final double scale = (parent.mScaleMode == SCALE_CROP_CENTER
						? Math.max(scale_x,  scale_y) : Math.min(scale_x, scale_y));
					final double width = scale * video_width;
					final double height = scale * video_height;
					Log.v(TAG, String.format("size(%1.0f,%1.0f),scale(%f,%f),mat(%f,%f)",
						width, height, scale_x, scale_y, width / view_width, height / view_height));
					Matrix.scaleM(mMvpMatrix, 0, (float)(width / view_width), (float)(height / view_height), 1.0f);
					break;
				}
				}
				if (mDrawer != null) {
					mDrawer.setMvpMatrix(mMvpMatrix, 0);
				}
			}
		}

		private volatile boolean requestUpdateTex = false;
		private boolean flip = true;
		/**
		 * drawing to GLSurface
		 * we set renderMode to GLSurfaceView.RENDERMODE_WHEN_DIRTY,
		 * this method is only called when #requestRender is called(= when texture is required to update)
		 * if you don't set RENDERMODE_WHEN_DIRTY, this method is called at maximum 60fps
		 */
		@Override
		public void onDrawFrame(final GL10 unused) {
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

			if (requestUpdateTex) {
				requestUpdateTex = false;
				// update texture(came from camera)
				mSTexture.updateTexImage();
				// get texture matrix
				mSTexture.getTransformMatrix(mStMatrix);
			}
			// draw to preview screen
			if (mDrawer != null) {
				mDrawer.draw(hTex, mStMatrix, 0);
			}
			flip = !flip;
			if (flip) {	// ~30fps
				synchronized (this) {
				}
			}
		}

		/**
		 * OnFrameAvailableListenerインターフェースの実装
		 * @param st
		 */
		@Override
		public void onFrameAvailable(final SurfaceTexture st) {
			requestUpdateTex = true;
		}
	}

	/**
	 * Handler class for asynchronous camera operation
	 */
	private static final class CameraHandler extends Handler {
		private static final int MSG_PREVIEW_START = 1;
		private static final int MSG_PREVIEW_STOP = 2;
		private CameraThread mThread;

		public CameraHandler(final CameraThread thread) {
			mThread = thread;
		}

		public void startPreview(final int width, final int height) {
			if (DEBUG) Log.v(TAG, "CameraHandler#handleStartPreview:");
			sendMessage(obtainMessage(MSG_PREVIEW_START, width, height));
		}

		/**
		 * request to stop camera preview
		 * @param needWait need to wait for stopping camera preview
		 */
		public void stopPreview(final boolean needWait) {
			if (DEBUG) Log.v(TAG, "CameraHandler#stopPreview:");
			synchronized (this) {
				sendEmptyMessage(MSG_PREVIEW_STOP);
				if (needWait) {
					if (DEBUG) Log.d(TAG, "wait for terminating of camera thread");
					for (; (mThread != null) && mThread.mIsRunning ; ) {
						try {
							wait(1000);
						} catch (final InterruptedException e) {
							// ignore
						}
					}
				}
			}
		}

		/**
		 * message handler for camera thread
		 */
		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
			case MSG_PREVIEW_START:
				mThread.handleStartPreview(msg.arg1, msg.arg2);
				break;
			case MSG_PREVIEW_STOP:
				mThread.handleStopPreview();
				synchronized (this) {
					notifyAll();
				}
				Looper.myLooper().quit();
				mThread = null;
				break;
			default:
				throw new RuntimeException("unknown message:what=" + msg.what);
			}
		}
	}

	/**
	 * Thread for asynchronous operation of camera preview
	 */
	private static final class CameraThread extends Thread {
    	private final Object mReadyFence = new Object();
    	private final WeakReference<CameraDelegator> mWeakParent;
    	private CameraHandler mHandler;
    	private volatile boolean mIsRunning = false;
		private Camera mCamera;

    	public CameraThread(@NonNull final CameraDelegator parent) {
			super("Camera thread");
    		mWeakParent = new WeakReference<CameraDelegator>(parent);
    	}

    	public CameraHandler getHandler() {
            synchronized (mReadyFence) {
            	try {
            		mReadyFence.wait();
            	} catch (final InterruptedException e) {
            		// ignore
                }
            }
            return mHandler;
    	}

    	/**
    	 * message loop
    	 * prepare Looper and create Handler for this thread
    	 */
		@Override
		public void run() {
            if (DEBUG) Log.d(TAG, "Camera thread start");
            Looper.prepare();
            synchronized (mReadyFence) {
                mHandler = new CameraHandler(this);
                mIsRunning = true;
                mReadyFence.notify();
            }
            Looper.loop();
            if (DEBUG) Log.d(TAG, "Camera thread finish");
            synchronized (mReadyFence) {
                mHandler = null;
                mIsRunning = false;
            }
		}

		/**
		 * start camera preview
		 * @param width
		 * @param height
		 */
		private final void handleStartPreview(final int width, final int height) {
			if (DEBUG) Log.v(TAG, "CameraThread#handleStartPreview:");
			final CameraDelegator parent = mWeakParent.get();
			if ((parent != null) && (mCamera == null)) {
				final GLSurfaceView view = parent.mView;
				// This is a sample project so just use 0 as camera ID.
				// it is better to selecting camera is available
				try {
					mCamera = Camera.open(CAMERA_ID);
					final Camera.Parameters params = mCamera.getParameters();
					final List<String> focusModes = params.getSupportedFocusModes();
					if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
						params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
					} else if(focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
						params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
					} else {
						if (DEBUG) Log.i(TAG, "Camera does not support autofocus");
					}
					// let's try fastest frame rate. You will get near 60fps, but your device become hot.
					final List<int[]> supportedFpsRange = params.getSupportedPreviewFpsRange();
					final int n = supportedFpsRange != null ? supportedFpsRange.size() : 0;
					int[] max_fps = null;
					for (int i = n - 1; i >= 0; i--) {
						final int[] range = supportedFpsRange.get(i);
						Log.i(TAG, String.format("supportedFpsRange(%d)=(%d,%d)", i, range[0], range[1]));
						if ((range[0] <= TARGET_FPS_MS) && (TARGET_FPS_MS <= range[1])) {
							max_fps = range;
							break;
						}
					}
					if (max_fps == null) {
						// 見つからなかったときは一番早いフレームレートを選択
						max_fps = supportedFpsRange.get(supportedFpsRange.size() - 1);
					}
					Log.i(TAG, String.format("found fps:%d-%d", max_fps[0], max_fps[1]));
					params.setPreviewFpsRange(max_fps[0], max_fps[1]);
					params.setRecordingHint(true);
					// request closest supported preview size
					final Camera.Size closestSize = getClosestSupportedSize(
						params.getSupportedPreviewSizes(), width, height);
					params.setPreviewSize(closestSize.width, closestSize.height);
					// request closest picture size for an aspect ratio issue on Nexus7
					final Camera.Size pictureSize = getClosestSupportedSize(
						params.getSupportedPictureSizes(), width, height);
					params.setPictureSize(pictureSize.width, pictureSize.height);
					// rotate camera preview according to the device orientation
					setRotation(params);
					mCamera.setParameters(params);
					// get the actual preview size
					final Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
					Log.i(TAG, String.format("previewSize(%d, %d)", previewSize.width, previewSize.height));
					// adjust view size with keeping the aspect ration of camera preview.
					// here is not a UI thread and we should request parent view to execute.
					view.post(new Runnable() {
						@Override
						public void run() {
							parent.setVideoSize(previewSize.width, previewSize.height);
						}
					});
					// カメラ映像のプレビュー表示用SurfaceTextureを生成
					final SurfaceTexture st = parent.getSurfaceTexture();
					st.setDefaultBufferSize(previewSize.width, previewSize.height);
					if (true) {
						// カメラ映像のプレビュー表示用SurfaceTextureをIRendererHolderへセット
						parent.addSurface(1, st, false);
						// カメラ映像受け取り用SurfaceTextureをセット
						mCamera.setPreviewTexture(parent.getInputSurfaceTexture());
					} else {
						// こっちはIRendererを経由せずに直接カメラにプレビュー表示用SurfaceTextureをセットする
						mCamera.setPreviewTexture(st);
					}
				} catch (final IOException e) {
					Log.e(TAG, "handleStartPreview:", e);
					if (mCamera != null) {
						mCamera.release();
						mCamera = null;
					}
				} catch (final RuntimeException e) {
					Log.e(TAG, "handleStartPreview:", e);
					if (mCamera != null) {
						mCamera.release();
						mCamera = null;
					}
				}
				if (mCamera != null) {
					// start camera preview display
					mCamera.startPreview();
				}
			}
		}

		private static Camera.Size getClosestSupportedSize(
			final List<Camera.Size> supportedSizes,
			final int requestedWidth, final int requestedHeight) {

			return Collections.min(supportedSizes, new Comparator<Camera.Size>() {

				private int diff(final Camera.Size size) {
					return Math.abs(requestedWidth - size.width)
						+ Math.abs(requestedHeight - size.height);
				}

				@Override
				public int compare(final Camera.Size lhs, final Camera.Size rhs) {
					return diff(lhs) - diff(rhs);
				}
			});

		}

		/**
		 * stop camera preview
		 */
		private void handleStopPreview() {
			if (DEBUG) Log.v(TAG, "CameraThread#handleStopPreview:");
			if (mCamera != null) {
				mCamera.stopPreview();
		        mCamera.release();
		        mCamera = null;
			}
			final CameraDelegator parent = mWeakParent.get();
			if (parent == null) return;
			parent.mCameraHandler = null;
		}

		/**
		 * rotate preview screen according to the device orientation
		 * @param params
		 */
		@SuppressLint("NewApi")
		private final void setRotation(final Camera.Parameters params) {
			if (DEBUG) Log.v(TAG, "CameraThread#setRotation:");
			final CameraDelegator parent = mWeakParent.get();
			if (parent == null) return;

			final GLSurfaceView view = parent.mView;
			final int rotation;
			if (BuildCheck.isAPI17()) {
				rotation = view.getDisplay().getRotation();
			} else {
				final Display display = ((WindowManager)view.getContext()
					.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
				rotation = display.getRotation();
			}
			int degrees = 0;
			switch (rotation) {
				case Surface.ROTATION_0: degrees = 0; break;
				case Surface.ROTATION_90: degrees = 90; break;
				case Surface.ROTATION_180: degrees = 180; break;
				case Surface.ROTATION_270: degrees = 270; break;
			}
			// get whether the camera is front camera or back camera
			final Camera.CameraInfo info =
					new Camera.CameraInfo();
				Camera.getCameraInfo(CAMERA_ID, info);
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {	// front camera
				degrees = (info.orientation + degrees) % 360;
				degrees = (360 - degrees) % 360;  // reverse
			} else {  // back camera
				degrees = (info.orientation - degrees + 360) % 360;
			}
			// apply rotation setting
			mCamera.setDisplayOrientation(degrees);
			parent.mRotation = degrees;
			// XXX This method fails to call and camera stops working on some devices.
//			params.setRotation(degrees);
		}

	}

}