package im.fsn.messenger.apisupport;

import android.annotation.TargetApi;
import android.hardware.Camera;
import android.os.Build;

@TargetApi(Build.VERSION_CODES.GINGERBREAD)
public class V9 {
	public static Camera getFrontCamera() {
		int cameraCount = Camera.getNumberOfCameras();
		for (int i = 0; i < cameraCount; i++) {
			Camera.CameraInfo camInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(i, camInfo);
			if (camInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				return Camera.open(i);
			}
		}
		return null;
	}

	public static Camera getFrontCamera(int cameraIndex) {
		return Camera.open(cameraIndex);
	}

	public static int getFrontCameraIndex() {
		int index = -1;
		int cameraCount = Camera.getNumberOfCameras();
		for (int i = 0; i < cameraCount; i++) {
			Camera.CameraInfo camInfo = new Camera.CameraInfo();
			Camera.getCameraInfo(i, camInfo);
			if (camInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				return i;
			}
		}
		return index;
	}
}
