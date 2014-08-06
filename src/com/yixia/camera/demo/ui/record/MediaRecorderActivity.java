package com.yixia.camera.demo.ui.record;

import java.io.File;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.yixia.camera.FFMpegUtils;
import com.yixia.camera.MediaRecorder;
import com.yixia.camera.MediaRecorder.OnErrorListener;
import com.yixia.camera.MediaRecorder.OnPreparedListener;
import com.yixia.camera.MediaRecorderFilter;
import com.yixia.camera.VCamera;
import com.yixia.camera.demo.R;
import com.yixia.camera.demo.log.Logger;
import com.yixia.camera.demo.ui.BaseActivity;
import com.yixia.camera.demo.ui.record.views.ProgressView;
import com.yixia.camera.demo.ui.record.views.ThemeRadioButton;
import com.yixia.camera.demo.utils.ConvertToUtils;
import com.yixia.camera.demo.utils.NetworkUtils;
import com.yixia.camera.model.MediaObject;
import com.yixia.camera.model.MediaObject.MediaPart;
import com.yixia.camera.util.DeviceUtils;
import com.yixia.camera.util.FileUtils;
import com.yixia.camera.util.StringUtils;
import com.yixia.camera.view.CameraNdkView;

/**
 * 视频录制
 * 
 * @author tangjun@yixia.com
 *
 */
public class MediaRecorderActivity extends BaseActivity implements OnErrorListener, OnClickListener, OnPreparedListener {

	/** 滤镜图标 */
	private final static int[] FILTER_ICONS = new int[] { R.drawable.filter_original, R.drawable.filter_black_white, R.drawable.filter_sharpen, R.drawable.filter_old_film, R.drawable.filter_edge, R.drawable.filter_anti_color, R.drawable.filter_radial, R.drawable.filter_8bit, R.drawable.filter_lomo };
	/** 滤镜枚举值 */
	private final static String[] FILTER_VALUES = new String[] { MediaRecorderFilter.CAMERA_FILTER_NO, MediaRecorderFilter.CAMERA_FILTER_BLACKWHITE, MediaRecorderFilter.CAMERA_FILTER_SHARPEN, MediaRecorderFilter.CAMERA_FILTER_OLD_PHOTOS, MediaRecorderFilter.CAMERA_FILTER_NEON_LIGHT, MediaRecorderFilter.CAMERA_FILTER_ANTICOLOR, MediaRecorderFilter.CAMERA_FILTER_PASS_THROUGH, MediaRecorderFilter.CAMERA_FILTER_MOSAICS, MediaRecorderFilter.CAMERA_FILTER_REMINISCENCE };
	/** 导入图片 */
	public final static int REQUEST_CODE_IMPORT_IMAGE = 999;
	/** 导入视频 */
	public final static int REQUEST_CODE_IMPORT_VIDEO = 998;
	/** 导入视频截取 */
	public final static int REQUEST_CODE_IMPORT_VIDEO_EDIT = 997;
	/** 录制最长时间 */
	public final static int RECORD_TIME_MAX = 10 * 1000;
	/** 录制最小时间 */
	public final static int RECORD_TIME_MIN = 3 * 1000;

	private CheckedTextView mRecordDelete, mRecordDelay, mRecordFilter;
	private ProgressView mProgressView;
	private CameraNdkView mSurfaceView;
	private TextView mTitleText, mTitleNext, mRecordImport;
	private ImageView mPressText;
	/** 滤镜容器 */
	private RadioGroup mRecordFilterContainer;
	private View mRecordFilterLayout;

	private PopupWindow mPopupWindow;

	private MediaRecorderFilter mMediaRecorder;
	private MediaObject mMediaObject;
	private int mWindowWidth;
	/** 是否是点击状态 */
	private volatile boolean mPressedStatus, mReleased, mStartEncoding;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);// 防止锁屏
		mWindowWidth = DeviceUtils.getScreenWidth(this);
		setContentView(R.layout.activity_media_recorder);

		// ~~~ 绑定控件
		mSurfaceView = (CameraNdkView) findViewById(R.id.record_preview);
		mProgressView = (ProgressView) findViewById(R.id.record_progress);
		mTitleText = (TextView) findViewById(R.id.title_text);
		mTitleNext = (TextView) findViewById(R.id.title_right);
		mRecordDelay = (CheckedTextView) findViewById(R.id.record_delay);
		mRecordDelete = (CheckedTextView) findViewById(R.id.record_delete);
		mRecordFilter = (CheckedTextView) findViewById(R.id.record_filter);
		mPressText = (ImageView) findViewById(R.id.record_tips_text);
		mRecordFilterContainer = (RadioGroup) findViewById(R.id.record_filter_container);
		mRecordFilterLayout = findViewById(R.id.record_filter_layout);
		mRecordImport = (TextView) findViewById(R.id.record_import);

		// ~~~ 绑定事件
		findViewById(R.id.record_layout).setOnTouchListener(mOnSurfaceViewTouchListener);
		mTitleNext.setOnClickListener(this);
		mRecordDelete.setOnClickListener(this);
		mRecordFilter.setOnClickListener(this);
		mRecordDelay.setOnClickListener(this);
		mRecordImport.setOnClickListener(this);

		// ~~~ 初始数据
		mSurfaceView.getLayoutParams().height = mWindowWidth;//(int) (mWindowWidth * 3F / 2);//视频为640x480，后期裁剪成1：1的视频
		findViewById(R.id.record_layout).getLayoutParams().height = mWindowWidth;//设置1：1预览范围
		mProgressView.invalidate();
		//		mDelayAnimation = AnimationUtils.loadAnimation(this, R.anim.record_delay_anim);
	}

	@Override
	protected void onStart() {
		super.onStart();

		if (mMediaRecorder == null)
			initMediaRecorder();
		else {
			mMediaRecorder.setSurfaceHolder(mSurfaceView.getHolder());
			mMediaRecorder.prepare();
		}
		checkStatus();
	}

	@Override
	protected void onStop() {
		super.onStop();
		if (mMediaRecorder != null && !mReleased) {
			mMediaRecorder.release();
		}
	}

	@Override
	public void onBackPressed() {
		if (mRecordDelete.isChecked()) {
			cancelDelete();
			return;
		}

		if (mMediaObject != null && mMediaObject.getDuration() > 1) {
			//未转码
			new AlertDialog.Builder(this).setTitle(R.string.hint).setMessage(R.string.record_camera_exit_dialog_message).setNegativeButton(R.string.dialog_yes, new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					mMediaObject.delete();
					finish();
				}

			}).setPositiveButton(R.string.dialog_no, null).setCancelable(false).show();
			return;
		}

		if (mMediaObject != null)
			mMediaObject.delete();
		super.onBackPressed();
	}

	private void initMediaRecorder() {
		mMediaRecorder = new MediaRecorderFilter();
		mMediaRecorder.setOnErrorListener(this);
		mMediaRecorder.setOnPreparedListener(this);
		//WIFI下800k码率，其他情况（4G/3G/2G）600K码率
		mMediaRecorder.setVideoBitRate(NetworkUtils.isWifiAvailable(this) ? MediaRecorder.VIDEO_BITRATE_MEDIUM : MediaRecorder.VIDEO_BITRATE_NORMAL);
		//		mMediaRecorder.setSurfaceHolder(mSurfaceView.getHolder());
		mMediaRecorder.setSurfaceView(mSurfaceView);
		String key = String.valueOf(System.currentTimeMillis());
		mMediaObject = mMediaRecorder.setOutputDirectory(key, VCamera.getVideoCachePath() + key);
		if (mMediaObject != null) {
			mMediaRecorder.prepare();
			mMediaRecorder.setCameraFilter(MediaRecorderFilter.CAMERA_FILTER_NO);
			mProgressView.setData(mMediaObject);
		} else {
			Toast.makeText(this, R.string.record_camera_init_faild, Toast.LENGTH_SHORT).show();
			finish();
		}
	}

	private View.OnTouchListener mOnSurfaceViewTouchListener = new View.OnTouchListener() {

		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if (mMediaRecorder == null || mMediaObject == null) {
				return false;
			}

			switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:

				//检测是否手动对焦
				//				if (checkCameraFocus(event))
				//					return true;

				//取消回删
				if (cancelDelete())
					return true;

				//取消延时拍摄
				//				if (mRecordDelay.isChecked()) {
				//					stopRecord();
				//					return true;
				//				}

				//判断是否已经超时
				if (mMediaObject.getDuration() >= RECORD_TIME_MAX) {
					return true;
				}

				//显示当前时间
				mTitleText.setText(String.format("%.1f", mMediaObject.getDuration() / 1000F));

				startRecord();

				break;
			case MotionEvent.ACTION_MOVE:
				break;
			case MotionEvent.ACTION_UP:
				// 暂停
				if (mPressedStatus) {
					stopRecord();

					//检测是否已经完成
					if (mMediaObject.getDuration() >= RECORD_TIME_MAX) {
						mTitleNext.performClick();
					}
				}

				mTitleText.setText(R.string.record_camera_title);
				break;
			}
			return true;
		}

	};

	/** 开始拍摄 */
	private void startRecord() {
		mPressedStatus = true;

		if (mMediaRecorder != null) {
			mMediaRecorder.startRecord();
		}

		if (mHandler != null) {
			mHandler.sendEmptyMessage(HANDLE_INVALIDATE_PROGRESS);
			mHandler.sendEmptyMessageDelayed(HANDLE_STOP_RECORD, RECORD_TIME_MAX - mMediaObject.getDuration());
		}

		mHandler.removeMessages(HANDLE_SHOW_TIPS);
		mHandler.sendEmptyMessage(HANDLE_SHOW_TIPS);
		mRecordDelete.setEnabled(false);
		if (!mRecordDelay.isChecked())
			mRecordDelay.setEnabled(false);

		mPressText.setImageResource(R.drawable.record_tips_pause);
	}

	private void stopRecord() {
		mPressedStatus = false;
		//提示完成
		mPressText.setImageResource(R.drawable.record_tips_press);

		if (mMediaRecorder != null)
			mMediaRecorder.stopRecord();

		//取消倒计时
		mHandler.removeMessages(HANDLE_STOP_RECORD);

		mRecordDelay.setChecked(false);
		mRecordDelay.setEnabled(true);
		mRecordDelete.setEnabled(true);
	}

	/** 是否可回删 */
	private boolean cancelDelete() {
		if (mMediaObject != null) {
			MediaPart part = mMediaObject.getCurrentPart();
			if (part != null && part.remove) {
				part.remove = false;
				mRecordDelete.setChecked(false);

				if (mProgressView != null)
					mProgressView.invalidate();

				return true;
			}
		}
		return false;
	}

	/** 刷新进度条 */
	private static final int HANDLE_INVALIDATE_PROGRESS = 0;
	/** 延迟拍摄停止 */
	private static final int HANDLE_STOP_RECORD = 1;
	/** 显示下一步 */
	private static final int HANDLE_SHOW_TIPS = 2;

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case HANDLE_INVALIDATE_PROGRESS:
				if (mMediaObject != null && !isFinishing()) {
					if (mProgressView != null)
						mProgressView.invalidate();
					if (mPressedStatus)
						mTitleText.setText(String.format("%.1f", mMediaObject.getDuration() / 1000F));
					if (mPressedStatus)
						sendEmptyMessageDelayed(0, 30);
				}
				break;
			case HANDLE_SHOW_TIPS:
				if (mMediaRecorder != null && !isFinishing()) {
					int duration = checkStatus();

					if (mPressedStatus) {
						if (duration < RECORD_TIME_MAX) {
							sendEmptyMessageDelayed(HANDLE_SHOW_TIPS, 200);
						} else {
							sendEmptyMessageDelayed(HANDLE_SHOW_TIPS, 500);
						}
					}
				}
				break;
			case HANDLE_STOP_RECORD:
				stopRecord();
				startEncoding();
				break;
			}
		}
	};

	/** 导入图片或视频 */
	private void importImageOrVideo(final String path) {
		if (!isFinishing() && mMediaObject != null && StringUtils.isNotEmpty(path) && new File(path).exists()) {
			new AsyncTask<Void, Void, Boolean>() {

				@Override
				protected void onPreExecute() {
					super.onPreExecute();
					showProgress("", getString(R.string.record_camera_progress_message));
				}

				@Override
				protected Boolean doInBackground(Void... params) {
					MediaPart part = mMediaObject.buildMediaPart(path, 1000, MediaObject.MEDIA_PART_TYPE_IMPORT_IMAGE);
					if (part != null) {
						return FFMpegUtils.convertImage2Video(part);
					}
					return false;
				}

				@Override
				protected void onPostExecute(Boolean result) {
					super.onPostExecute(result);
					hideProgress();
					if (result) {
						mProgressView.setData(mMediaObject);
					} else {
						Toast.makeText(MediaRecorderActivity.this, R.string.record_video_transcoding_faild, Toast.LENGTH_SHORT).show();
					}
				}

			}.execute();
		}
	}

	private void startEncoding() {
		//检测磁盘空间
		if (FileUtils.showFileAvailable() < 200) {
			Toast.makeText(this, R.string.record_camera_check_available_faild, Toast.LENGTH_SHORT).show();
			return;
		}

		if (!isFinishing() && mMediaRecorder != null && mMediaObject != null && !mStartEncoding) {
			mStartEncoding = true;

			new AsyncTask<Void, Void, Boolean>() {

				@Override
				protected void onPreExecute() {
					super.onPreExecute();
					showProgress("", getString(R.string.record_camera_progress_message));
				}

				@Override
				protected Boolean doInBackground(Void... params) {
					boolean result = FFMpegUtils.videoTranscoding(mMediaObject, mMediaObject.getOutputTempVideoPath(), mWindowWidth, false);
					if (result && mMediaRecorder != null) {
						mMediaRecorder.release();
						mReleased = true;
					}
					return result;
				}

				@Override
				protected void onCancelled() {
					super.onCancelled();
					mStartEncoding = false;
				}

				@Override
				protected void onPostExecute(Boolean result) {
					super.onPostExecute(result);
					hideProgress();
					if (result) {
						/** 序列化保存数据 */
						if (saveMediaObject(mMediaObject)) {
							Intent intent = new Intent(MediaRecorderActivity.this, MediaPreviewActivity.class);
							intent.putExtra("obj", mMediaObject.getObjectFilePath());
							startActivity(intent);
						} else {
							Toast.makeText(MediaRecorderActivity.this, R.string.record_camera_save_faild, Toast.LENGTH_SHORT).show();
						}
					} else {
						Toast.makeText(MediaRecorderActivity.this, R.string.record_video_transcoding_faild, Toast.LENGTH_SHORT).show();
					}
					mStartEncoding = false;
				}
			}.execute();
		}
	}

	/** 检测是否超过三秒 */
	private int checkStatus() {
		int duration = 0;
		if (!isFinishing() && mMediaObject != null) {
			duration = mMediaObject.getDuration();
			if (duration < RECORD_TIME_MIN) {
				//视频必须大于3秒
				if (mTitleNext.getVisibility() != View.INVISIBLE)
					mTitleNext.setVisibility(View.INVISIBLE);
			} else {
				//下一步
				if (mTitleNext.getVisibility() != View.VISIBLE) {
					mTitleNext.setVisibility(View.VISIBLE);
					mTitleNext.setText(R.string.record_camera_next);
				}
			}
		}
		return duration;
	}

	@Override
	public void onVideoError(int what, int extra) {
		Logger.e("[MediaRecorderActvity]onVideoError: what" + what + " extra:" + extra);
	}

	@Override
	public void onAudioError(int what, String message) {
		Logger.e("[MediaRecorderActvity]onAudioError: what" + what + " message:" + message);
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast.makeText(MediaRecorderActivity.this, R.string.record_camera_open_audio_faild, Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void onClick(View v) {
		final int id = v.getId();

		//处理开启回删后其他点击操作
		if (id != R.id.record_delete) {
			if (mMediaObject != null) {
				MediaPart part = mMediaObject.getCurrentPart();
				if (part != null) {
					if (part.remove) {
						part.remove = false;
						mRecordDelete.setChecked(false);
						if (mProgressView != null)
							mProgressView.invalidate();
					}
				}
			}
		}

		if (id != R.id.record_filter) {
			if (mRecordFilter.isChecked()) {
				mRecordFilterLayout.setVisibility(View.GONE);
				mRecordFilter.setChecked(false);
			}
		}

		switch (v.getId()) {
		case R.id.record_import://导入
			showImportMenu();
			break;
		case R.id.title_right:
			startEncoding();
			break;
		case R.id.record_delete:
			if (mMediaObject != null) {
				MediaPart part = mMediaObject.getCurrentPart();
				if (part != null) {
					if (part.remove) {
						//确认删除分块
						part.remove = false;
						mMediaObject.removePart(part, true);
						mRecordDelete.setChecked(false);
					} else {
						part.remove = true;
						mRecordDelete.setChecked(true);
					}
				}
				if (mProgressView != null)
					mProgressView.invalidate();

				//检测按钮状态
				checkStatus();
			}
			break;
		case R.id.record_filter:
			if (mRecordFilter.isChecked()) {
				mRecordFilterLayout.setVisibility(View.GONE);
				mRecordFilter.setChecked(false);
			} else {
				loadFilter();
				mRecordFilterLayout.setVisibility(View.VISIBLE);
				mRecordFilter.setChecked(true);
			}
			break;
		case R.id.import_image: {
			if (mPopupWindow != null) {
				mPopupWindow.dismiss();
				mPopupWindow = null;
			}
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_GET_CONTENT);
			intent.setType("image/*");
			startActivityForResult(Intent.createChooser(intent, getString(R.string.record_camera_import_image_choose)), REQUEST_CODE_IMPORT_IMAGE);
		}
			break;
		case R.id.import_video: {
			if (mPopupWindow != null) {
				mPopupWindow.dismiss();
				mPopupWindow = null;
			}
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_GET_CONTENT);
			intent.addCategory(Intent.CATEGORY_OPENABLE);
			intent.setType("video/*");
			startActivityForResult(Intent.createChooser(intent, getString(R.string.record_camera_import_video_choose)), REQUEST_CODE_IMPORT_VIDEO);
		}
			break;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			if (requestCode == REQUEST_CODE_IMPORT_VIDEO_EDIT) {
				mMediaObject = restoneMediaObject(mMediaObject.getObjectFilePath());
				mProgressView.setData(mMediaObject);
			} else {
				//处理导入图片和视频
				if (data != null) {
					Uri uri = data.getData();
					if (uri != null) {
						String columnName;
						switch (requestCode) {
						case REQUEST_CODE_IMPORT_IMAGE://导入图片
							columnName = MediaStore.Images.Media.DATA;
							break;
						case REQUEST_CODE_IMPORT_VIDEO:
							columnName = MediaStore.Video.Media.DATA;
							break;
						default:
							return;
						}
						if (StringUtils.isNotEmpty(columnName)) {
							Cursor cursor = getContentResolver().query(uri, new String[] { columnName }, null, null, null);
							if (cursor != null) {
								String path = "";
								if (cursor.moveToNext()) {
									path = cursor.getString(0);
								}
								cursor.close();

								switch (requestCode) {
								case REQUEST_CODE_IMPORT_IMAGE://导入图片
									importImageOrVideo(path);
									break;
								case REQUEST_CODE_IMPORT_VIDEO:
									if (saveMediaObject(mMediaObject)) {
										startActivityForResult(new Intent(this, ImportVideoActivity.class).putExtra("obj", mMediaObject.getObjectFilePath()).putExtra("path", path), REQUEST_CODE_IMPORT_VIDEO_EDIT);
									}
									break;
								}
							}
						}
					}
				}
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	@SuppressWarnings("deprecation")
	private void showImportMenu() {
		View view = LayoutInflater.from(this).inflate(R.layout.menu_record_import, null);
		mPopupWindow = new PopupWindow(view, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, true);
		view.findViewById(R.id.import_image).setOnClickListener(this);
		view.findViewById(R.id.import_video).setOnClickListener(this);
		mPopupWindow.setFocusable(true);
		mPopupWindow.setOutsideTouchable(true);
		mPopupWindow.setBackgroundDrawable(new BitmapDrawable());
		view.measure(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		mPopupWindow.showAsDropDown(mRecordImport, 0, -view.getMeasuredHeight() - mRecordImport.getHeight());
	}

	/** 加载滤镜 */
	private void loadFilter() {
		if (!isFinishing() && mRecordFilterContainer.getChildCount() == 0) {
			final String[] filterNames = getResources().getStringArray(R.array.record_filter);
			int leftMargin = ConvertToUtils.dipToPX(this, 10);
			LayoutInflater mInflater = LayoutInflater.from(this);
			for (int i = 0; i < FILTER_ICONS.length; i++) {
				ThemeRadioButton filterView = (ThemeRadioButton) mInflater.inflate(R.layout.view_radio_item, null);
				filterView.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						int index = ConvertToUtils.toInt(v.getTag().toString());
						if (mMediaRecorder != null)
							mMediaRecorder.setCameraFilter(FILTER_VALUES[index]);
					}
				});
				filterView.setCompoundDrawablesWithIntrinsicBounds(0, FILTER_ICONS[i], 0, 0);
				filterView.setText(filterNames[i]);
				filterView.setTag(i);
				RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(RadioGroup.LayoutParams.WRAP_CONTENT, RadioGroup.LayoutParams.WRAP_CONTENT);
				lp.leftMargin = leftMargin;
				mRecordFilterContainer.addView(filterView, lp);
			}

			mRecordFilterContainer.getChildAt(0).performClick();
		}
	}

	@Override
	public void onPrepared() {
		if (mMediaRecorder != null) {
			//自动对焦
			mMediaRecorder.autoFocus(new AutoFocusCallback() {

				@Override
				public void onAutoFocus(boolean success, Camera camera) {
					if (success) {

					}
				}
			});
		}
	}
}
