package com.yixia.camera.demo;

import java.io.File;

import android.app.Application;
import android.os.Environment;

import com.yixia.camera.VCamera;
import com.yixia.camera.util.DeviceUtils;

public class VCameraDemoApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();

		// 设置拍摄视频缓存路径
		File dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
		if (DeviceUtils.isZte()) {
			if (dcim.exists()) {
				VCamera.setVideoCachePath(dcim + "/Camera/VCameraDemo/");
			} else {
				VCamera.setVideoCachePath(dcim.getPath().replace("/sdcard/", "/sdcard-ext/") + "/Camera/VCameraDemo/");
			}
		} else {
			VCamera.setVideoCachePath(dcim + "/Camera/VCameraDemo/");
		}
		// 开启log输出,ffmpeg输出到logcat
		VCamera.setDebugMode(true);
		// 初始化拍摄SDK，必须
		VCamera.initialize(this);
	}

}
