package com.yixia.camera.demo.ui.record;

import java.io.File;

import android.content.Intent;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Toast;

import com.yixia.camera.FFMpegUtils;
import com.yixia.camera.demo.R;
import com.yixia.camera.demo.media.MediaPlayer;
import com.yixia.camera.demo.ui.BaseActivity;
import com.yixia.camera.demo.ui.record.views.FrameImageView;
import com.yixia.camera.demo.ui.widget.VideoView;
import com.yixia.camera.demo.ui.widget.VideoView.OnPlayStateListener;
import com.yixia.camera.demo.utils.IsUtils;
import com.yixia.camera.demo.utils.ResourceUtils;
import com.yixia.camera.model.MediaObject;
import com.yixia.camera.model.MediaThemeObject;
import com.yixia.camera.util.DeviceUtils;
import com.yixia.camera.util.FileUtils;
import com.yixia.camera.util.StringUtils;

/**
 * 视频预览
 * 
 * @author tangjun@yixia.com
 *
 */
public class MediaPreviewActivity extends BaseActivity implements OnClickListener, OnPlayStateListener, OnPreparedListener {

	/** 视频预览 */
	private VideoView mVideoView;
	/** 应用主题图片 */
	private FrameImageView mRecordThemeImage;
	/** 暂停图标 */
	private View mRecordPlay;

	/** 应用主题音频 */
	private MediaPlayer mAudioPlayer;
	/** 窗体宽度 */
	private int mWindowWidth;
	/** 视频信息 */
	private MediaObject mMediaObject;
	/** 主题缓存的目录 */
	private File mThemeCacheDir;
	/** 当前主题 */
	private String mCurrentTheme = null;
	/** 是否需要恢复视频播放 */
	private boolean mNeedResume;
	/** 是否需要恢复主题音乐播放 */
	private boolean mNeedResumeThemeAudio;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		String obj = getIntent().getStringExtra("obj");
		mMediaObject = restoneMediaObject(obj);
		if (mMediaObject == null) {
			Toast.makeText(this, R.string.record_read_object_faild, Toast.LENGTH_SHORT).show();
			finish();
			return;
		}

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);// 防止锁屏
		mWindowWidth = DeviceUtils.getScreenWidth(this);
		setContentView(R.layout.activity_media_preview);

		// ~~~ 绑定控件
		mVideoView = (VideoView) findViewById(R.id.record_preview);
		mRecordPlay = findViewById(R.id.record_play);
		mRecordThemeImage = (FrameImageView) findViewById(R.id.record_theme_image);

		// ~~~ 绑定事件
		mVideoView.setOnClickListener(this);
		mVideoView.setOnPreparedListener(this);
		mVideoView.setOnPlayStateListener(this);
		findViewById(R.id.record_preview_theme_original).setOnClickListener(this);
		findViewById(R.id.record_preview_theme_news).setOnClickListener(this);
		findViewById(R.id.record_preview_theme_goddess).setOnClickListener(this);
		findViewById(R.id.record_preview_theme_recording).setOnClickListener(this);
		findViewById(R.id.record_preview_theme_bsmall).setOnClickListener(this);
		findViewById(R.id.title_right).setOnClickListener(this);

		// ~~~ 初始数据
		findViewById(R.id.record_layout).getLayoutParams().height = mWindowWidth;//设置1：1预览范围
		if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) && !isExternalStorageRemovable())
			mThemeCacheDir = new File(getExternalCacheDir(), "Theme");
		else
			mThemeCacheDir = new File(getCacheDir(), "Theme");
		mVideoView.setVideoPath(mMediaObject.getOutputTempVideoPath());
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mVideoView != null) {
			if (mNeedResume) {
				mVideoView.start();
			}
		}

		if (mAudioPlayer != null && mNeedResumeThemeAudio) {
			mAudioPlayer.start();
			mNeedResumeThemeAudio = false;
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mVideoView != null) {
			if (mVideoView.isPlaying()) {
				mVideoView.pause();
				mNeedResume = true;
			}
		}

		if (mAudioPlayer != null) {
			if (mAudioPlayer.isPlaying()) {
				mNeedResumeThemeAudio = true;
				mAudioPlayer.pause();
			}
		}
	}

	@Override
	public void onPrepared(android.media.MediaPlayer mp) {
		if (!isFinishing()) {
			mVideoView.start();
			mVideoView.setLooping(true);
		}
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.record_preview:
			if (mVideoView.isPlaying()) {
				mVideoView.pause();
				if (mAudioPlayer != null)
					mAudioPlayer.pause();
			} else {
				mVideoView.start();
				if (mAudioPlayer != null)
					mAudioPlayer.start();
			}
			break;
		case R.id.record_preview_theme_original:
			loadTheme(null);
			break;
		case R.id.record_preview_theme_news:
			loadTheme("News");
			break;
		case R.id.record_preview_theme_goddess:
			loadTheme("Goddess");
			break;
		case R.id.record_preview_theme_recording:
			loadTheme("Recording");
			break;
		case R.id.record_preview_theme_bsmall:
			loadTheme("Bsmall");
			break;
		case R.id.title_right:
			startEncoding();
			break;
		}
	}

	private void startEncoding() {
		//检测磁盘空间
		if (FileUtils.showFileAvailable() < 200) {
			Toast.makeText(this, R.string.record_camera_check_available_faild, Toast.LENGTH_SHORT).show();
			return;
		}

		if (!isFinishing() && mMediaObject != null) {
			new AsyncTask<Void, Void, Boolean>() {

				@Override
				protected void onPreExecute() {
					super.onPreExecute();
					showProgress("", getString(R.string.record_camera_progress_message));
				}

				@Override
				protected Boolean doInBackground(Void... params) {
					return FFMpegUtils.videoTranscoding(mMediaObject, mMediaObject.getOutputVideoPath(), mWindowWidth, true);
				}

				@Override
				protected void onPostExecute(Boolean result) {
					super.onPostExecute(result);
					hideProgress();
					if (result) {
						Toast.makeText(MediaPreviewActivity.this, getString(R.string.record_video_transcoding_success, mMediaObject.getOutputVideoPath()), Toast.LENGTH_LONG).show();
						/** 序列化保存数据 */
						Intent intent = new Intent(Intent.ACTION_VIEW);
						intent.setDataAndType(Uri.parse(mMediaObject.getOutputVideoPath()), "video/mp4");
						startActivity(intent);
					} else {
						Toast.makeText(MediaPreviewActivity.this, R.string.record_video_transcoding_faild, Toast.LENGTH_SHORT).show();
					}
				}
			}.execute();
		}
	}

	/** 加载主题 */
	private void loadTheme(String key) {
		if (IsUtils.equals(mCurrentTheme, key))
			return;

		mCurrentTheme = key;

		//关闭主题音乐
		if (mAudioPlayer != null) {
			mAudioPlayer.stop();
			mAudioPlayer.release();
			mAudioPlayer = null;
		}

		if (StringUtils.isEmpty(key)) {
			mMediaObject.mThemeObject = null;
			mRecordThemeImage.setEmptyImage();
		} else {
			//检测主题是否已经拷贝到SDCARD
			boolean exists = mThemeCacheDir.exists();
			if (!exists)
				exists = mThemeCacheDir.mkdir();

			MediaThemeObject theme = new MediaThemeObject();
			File themeDir = new File(mThemeCacheDir, key);
			File mp3 = new File(themeDir, key + ".mp3");
			if ("News".equals(key)) {
				theme.watermarkes.add(new File(themeDir, key + ".png").getAbsolutePath());
				theme.frameCount = 1;
			} else if ("Bsmall".equals(key)) {
				theme.watermarkes.add(new File(themeDir, key + ".gif").getAbsolutePath());
			} else {
				theme.watermarkes.add(new File(themeDir, key + "1.png").getAbsolutePath());
				theme.watermarkes.add(new File(themeDir, key + "2.png").getAbsolutePath());
				theme.frameCount = 2;
				theme.frameDuration = 500;
			}

			//从Assert拷贝到缓冲文件夹
			if (exists) {
				if (!themeDir.exists()) {
					if (themeDir.mkdir()) {
						ResourceUtils.copyToSdcard(this, String.format("Theme/%s/%s.mp3", key, key), mp3.getAbsolutePath());
						if ("News".equals(key)) {
							ResourceUtils.copyToSdcard(this, String.format("Theme/%s/%s.png", key, key), theme.watermarkes.get(0));
						} else if ("Bsmall".equals(key)) {
							ResourceUtils.copyToSdcard(this, String.format("Theme/%s/%s.gif", key, key), theme.watermarkes.get(0));
						} else {
							ResourceUtils.copyToSdcard(this, String.format("Theme/%s/%s1.png", key, key), theme.watermarkes.get(0));
							ResourceUtils.copyToSdcard(this, String.format("Theme/%s/%s2.png", key, key), theme.watermarkes.get(1));
						}
					} else {
						//创建目录失败
						return;
					}
				}

				//播放音频
				if (mp3.exists()) {
					theme.audio = mp3.getAbsolutePath();
					mAudioPlayer = MediaPlayer.create(this, Uri.fromFile(mp3));
					if (mAudioPlayer != null) {
						mAudioPlayer.setLooping(true);
						mAudioPlayer.setVolume(1F, 1F);
						mAudioPlayer.start();
					}
				}

				//显示主题水印
				if ("Bsmall".equals(key))//加载gif动画
					mRecordThemeImage.setImageResource(theme.watermarkes.get(0));
				else
					mRecordThemeImage.setImagePath(theme.watermarkes, 250);

				mMediaObject.mThemeObject = theme;
			}
		}
	}

	@Override
	public void onStateChanged(boolean isPlaying) {
		if (isPlaying)
			mRecordPlay.setVisibility(View.GONE);
		else
			mRecordPlay.setVisibility(View.VISIBLE);
	}

	public static boolean isExternalStorageRemovable() {
		if (DeviceUtils.hasGingerbread())
			return Environment.isExternalStorageRemovable();
		else
			return Environment.MEDIA_REMOVED.equals(Environment.getExternalStorageState());
	}
}
