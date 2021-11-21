package com.serenegiant.system;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import java.io.FileNotFoundException;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

/**
 * Storage Access Framework/DocumentFile関係のヘルパークラス
 * KITKAT以降で個別のファイル毎にパーミッション要求する場合をSAFUtilsより分離
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class SAFSingleFileUtils {
	private static final boolean DEBUG = false;	// set false on production
	private static final String TAG = SAFSingleFileUtils.class.getSimpleName();

	private static final String EXTRA_KEY_REQUEST_ID = "SAFSingleFileUtils.EXTRA_KEY_REQUEST_ID";

	@NonNull
	private final ActivityResultLauncher<Intent> mLauncher;

	/**
	 * コンストラクタ
	 * @param activity
	 * @param callback
	 */
	@RequiresApi(api = Build.VERSION_CODES.KITKAT)
	public SAFSingleFileUtils(
		@NonNull final ComponentActivity activity,
		@NonNull final SAFPermission.SAFCallback callback) {

		mLauncher = activity.registerForActivityResult(new SingleDocument(),
			new MyActivityResultCallback(callback));
	}

	/**
	 * コンストラクタ
	 * @param fragment
	 * @param callback
	 */
	@RequiresApi(api = Build.VERSION_CODES.KITKAT)
	public SAFSingleFileUtils(
		@NonNull final Fragment fragment,
		@NonNull final SAFPermission.SAFCallback callback) {

		mLauncher = fragment.registerForActivityResult(new SingleDocument(),
			new MyActivityResultCallback(callback));
	}

	/**
	 * ファイル読み込み用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param id
	 * @param mimeType
	 * @throws IllegalArgumentException
	 */
	public void requestOpen(final int id, @NonNull final String mimeType) throws IllegalArgumentException {
		mLauncher.launch(prepareOpenDocumentIntent(id, mimeType));
	}

	/**
	 * ファイル保存用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param id
	 * @param mimeType
	 * @param defaultName
	 * @throws IllegalArgumentException
	 */
	public void requestCreate(
		final int id, @NonNull final String mimeType,
		@Nullable final String defaultName) throws IllegalArgumentException {

		mLauncher.launch(prepareCreateDocument(id, mimeType, defaultName));
	}

//--------------------------------------------------------------------------------
	private static class MyActivityResultCallback implements ActivityResultCallback<Pair<Integer, Uri>> {
		private static final String TAG = MyActivityResultCallback.class.getSimpleName();
		@NonNull
		final SAFPermission.SAFCallback callback;

		private MyActivityResultCallback(
			@NonNull final SAFPermission.SAFCallback callback) {

			if (DEBUG) Log.v(TAG, "コンストラクタ:");
			this.callback = callback;
		}

		@Override
		public void onActivityResult(final Pair<Integer, Uri> result) {
			if (DEBUG) Log.v(TAG, "onActivityResult:" + result);
			if (result != null) {
				final int requestCode = result.first;
				final Uri uri = result.second;
				if (uri != null) {
					// SingleDocumentのuriを取得できた時
					callback.onResult(requestCode, uri);
				} else {
					// SingleDocumentのuriを取得できなかったとき
					callback.onFailed(requestCode);
				}
			} else {
				// ActivityResultContractの実装上実際にはresultはnullにならないけど
				callback.onFailed(0);
			}
		}
	}

	@TargetApi(19)
	private static class SingleDocument extends ActivityResultContract<Intent, Pair<Integer, Uri>> {
		private static final String TAG = SingleDocument.class.getSimpleName();

		@Nullable
		private Intent input;

		@CallSuper
		@NonNull
		@Override
		public Intent createIntent(@NonNull Context context, @Nullable Intent input) {
			if (DEBUG) Log.v(TAG, "createIntent:" + input);
			this.input = input;
			final String action = input != null ? input.getAction() : null;	// 実装上はnullにならないはず
			final String mimeType = input != null ? input.getType() : null;
			final String defaultName = input != null ? input.getStringExtra(Intent.EXTRA_TITLE) : null;
			final Intent intent = new Intent(action != null ? action : Intent.ACTION_OPEN_DOCUMENT);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input != null) {
				final Uri initialUri = input != null ? input.getData() : null;
				intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
			}
			if (!TextUtils.isEmpty(mimeType)) {
				intent.setType(mimeType);
			}
			if (!TextUtils.isEmpty(defaultName)) {
				intent.putExtra(Intent.EXTRA_TITLE, defaultName);
			}
			return intent;
		}

		@Nullable
		@Override
		public final SynchronousResult<Pair<Integer, Uri>> getSynchronousResult(@NonNull Context context,
				@Nullable Intent input) {
			if (DEBUG) Log.v(TAG, "getSynchronousResult:" + input);
			this.input = input;
			return null;
		}

		@Nullable
		@Override
		public final Pair<Integer, Uri> parseResult(final int resultCode, @Nullable Intent intent) {
			final Uri uri = ((intent!= null) && (resultCode == Activity.RESULT_OK)) ? intent.getData() : null;
			final int id = input != null ? input.getIntExtra(EXTRA_KEY_REQUEST_ID, 0) : 0;
			return Pair.create(id, uri);
		}
	}

//--------------------------------------------------------------------------------
	/**
	 * ファイル読み込み用のUriを要求するヘルパーメソッド
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param id
	 * @param mimeType
	 * @return
	 * @throws IllegalArgumentException
	 */
	private static Intent prepareOpenDocumentIntent(
		final int id, @NonNull final String mimeType) throws IllegalArgumentException {
		if (TextUtils.isEmpty(mimeType)) {
			throw new IllegalArgumentException("mime type should not a null/empty");
		}
		return new Intent(Intent.ACTION_OPEN_DOCUMENT)
			.setType(mimeType)
			.putExtra(EXTRA_KEY_REQUEST_ID, id);
	}

	/**
	 * ファイル保存用のUriを要求するヘルパーメソッド
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param id
	 * @param mimeType
	 * @param defaultName
	 * @return
	 * @throws IllegalArgumentException
	 */
	private static Intent prepareCreateDocument(
		final int id, @NonNull final String mimeType,
		@Nullable final String defaultName) throws IllegalArgumentException {

		if (TextUtils.isEmpty(mimeType)) {
			throw new IllegalArgumentException("mime type should not a null/empty");
		}
		return new Intent(Intent.ACTION_OPEN_DOCUMENT)
			.setType(mimeType)
			.putExtra(Intent.EXTRA_TITLE, defaultName);
	}

	/**
	 * ファイル削除要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param context
	 * @param uri
	 * @return
	 */
	public static boolean requestDeleteDocument(
		@NonNull final Context context, final Uri uri) {

		try {
			return BuildCheck.isKitKat()
				&& DocumentsContract.deleteDocument(context.getContentResolver(), uri);
		} catch (final FileNotFoundException e) {
			return false;
		}
	}

//--------------------------------------------------------------------------------
	/**
	 * ファイル読み込み用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param activity
	 * @param mimeType
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@Deprecated
	public static void requestOpenDocument(
		@NonNull final Activity activity,
		@NonNull final String mimeType, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			activity.startActivityForResult(prepareOpenDocumentIntent(requestCode, mimeType), requestCode);
		}
	}

	/**
	 * ファイル読み込み用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param activity
	 * @param mimeType
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@SuppressWarnings("deprecation")
	@Deprecated
	public static void requestOpenDocument(
		@NonNull final FragmentActivity activity,
		@NonNull final String mimeType, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			activity.startActivityForResult(prepareOpenDocumentIntent(requestCode, mimeType), requestCode);
		}
	}

	/**
	 * ファイル読み込み用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param fragment
	 * @param mimeType
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@Deprecated
	public static void requestOpenDocument(@NonNull final android.app.Fragment fragment,
		@NonNull final String mimeType, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			fragment.startActivityForResult(prepareOpenDocumentIntent(requestCode, mimeType), requestCode);
		}
	}

	/**
	 * ファイル読み込み用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param fragment
	 * @param mimeType
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@SuppressWarnings("deprecation")
	@Deprecated
	public static void requestOpenDocument(
		@NonNull final Fragment fragment,
		@NonNull final String mimeType, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			fragment.startActivityForResult(prepareOpenDocumentIntent(requestCode, mimeType), requestCode);
		}
	}

	/**
	 * ファイル保存用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param activity
	 * @param mimeType
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@Deprecated
	public static void requestCreateDocument(
		@NonNull final Activity activity,
		@NonNull final String mimeType, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			activity.startActivityForResult(prepareCreateDocument(requestCode, mimeType, null), requestCode);
		}
	}

	/**
	 * ファイル保存用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param activity
	 * @param mimeType
	 * @param defaultName
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@Deprecated
	public static void requestCreateDocument(
		@NonNull final Activity activity,
		@NonNull final String mimeType, final String defaultName, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			activity.startActivityForResult(prepareCreateDocument(requestCode, mimeType, defaultName), requestCode);
		}
	}

	/**
	 * ファイル保存用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param activity
	 * @param mimeType
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@SuppressWarnings("deprecation")
	@Deprecated
	public static void requestCreateDocument(
		@NonNull final FragmentActivity activity,
		@NonNull final String mimeType, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			activity.startActivityForResult(prepareCreateDocument(requestCode, mimeType, null), requestCode);
		}
	}

	/**
	 * ファイル保存用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param activity
	 * @param mimeType
	 * @param defaultName
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@SuppressWarnings("deprecation")
	@Deprecated
	public static void requestCreateDocument(
		@NonNull final FragmentActivity activity,
		@NonNull final String mimeType, final String defaultName, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			activity.startActivityForResult(prepareCreateDocument(requestCode, mimeType, defaultName), requestCode);
		}
	}

	/**
	 * ファイル保存用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param fragment
	 * @param mimeType
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@Deprecated
	public static void requestCreateDocument(
		@NonNull final android.app.Fragment fragment,
		@NonNull final String mimeType, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			fragment.startActivityForResult(prepareCreateDocument(requestCode, mimeType, null), requestCode);
		}
	}

	/**
	 * ファイル保存用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param fragment
	 * @param mimeType
	 * @param defaultName
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@Deprecated
	public static void requestCreateDocument(
		@NonNull final android.app.Fragment fragment,
		@NonNull final String mimeType, final String defaultName, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			fragment.startActivityForResult(prepareCreateDocument(requestCode, mimeType, defaultName), requestCode);
		}
	}

	/**
	 * ファイル保存用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
	 * @param fragment
	 * @param mimeType
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@SuppressWarnings("deprecation")
	@Deprecated
	public static void requestCreateDocument(
		@NonNull final Fragment fragment,
		@NonNull final String mimeType, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			fragment.startActivityForResult(prepareCreateDocument(requestCode, mimeType, null), requestCode);
		}
	}

	/**
	 * ファイル保存用のUriを要求
	 * KITKAT以降で個別のファイル毎にパーミッション要求する場合
 	 * @param fragment
	 * @param mimeType
	 * @param defaultName
	 * @param requestCode
	 * @throws IllegalArgumentException
	 */
	@SuppressWarnings("deprecation")
	@Deprecated
	public static void requestCreateDocument(
		@NonNull final Fragment fragment,
		@NonNull final String mimeType, final String defaultName, final int requestCode) throws IllegalArgumentException {

		if (BuildCheck.isKitKat()) {
			fragment.startActivityForResult(prepareCreateDocument(requestCode, mimeType, defaultName), requestCode);
		}
	}

}
