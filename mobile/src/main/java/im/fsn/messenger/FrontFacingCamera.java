package im.fsn.messenger;

import im.fsn.messenger.apisupport.V9;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import android.os.Build;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import dalvik.system.PathClassLoader;

public class FrontFacingCamera {

	private static class FFCBase // class to test for cam in gingerbread (used
									// as base class)
	{
		protected Method[] methods = null;
		public static final String buildModel = Build.MODEL.toLowerCase();
		public static final int buildSDKVersion = Build.VERSION.SDK_INT;

		public static final String getBuildManufacturer() {
			if (buildSDKVersion >= 4)
				return Build.MANUFACTURER.toLowerCase();
			else
				return new String();
		}

		public boolean init() throws Exception {
			if (buildSDKVersion < 9)
				return false;
			return (V9.getFrontCameraIndex() != -1);
		}

		public Camera getFrontCamera() throws Exception {
			return (V9.getFrontCamera());
		}

		public static Class<?> getClass(String jarName, String className)
				throws Exception {
			if (jarName != null && !(new File(jarName).exists()))
				throw new FileNotFoundException(jarName);
			ClassLoader loader = FFCBase.class.getClassLoader();
			if (jarName != null)
				loader = new PathClassLoader(jarName, loader);
			return Class.forName(className, false, loader);
		}
	}

	private static class FFCByClass extends FFCBase {
		private String jarName;
		private String className;
		private String methodName;
		private Class<?>[] paramTypes;
		private Object[] params;

		public FFCByClass(String jarName, String className, String methodName,
				Class<?>[] paramTypes, Object[] params) {
			this.jarName = jarName;
			this.className = className;
			this.methodName = methodName;
			this.paramTypes = paramTypes;
			this.params = params;
		}

		@Override
		public boolean init() throws Exception {
			Class<?> _class = getClass(this.jarName, this.className);
			methods = new Method[] { _class.getDeclaredMethod(this.methodName,
					this.paramTypes) };
			return (methods != null && methods[0] != null);
		}

		@Override
		public Camera getFrontCamera() throws Exception {
			Camera camera = null;
			if (((methods[0]).getModifiers() & Modifier.STATIC) != 0)
				return (Camera) (methods[0]).invoke(null, params);
			try {
				(methods[0]).invoke(camera = Camera.open(), params);
			} catch (Exception e) {
				if (camera != null)
					camera.release();
				throw e;
			}
			return camera;
		}
	}

	private static class FFCByParameter extends FFCBase {
		private int minSDKVersion;
		private String manufacturer;
		private String model;
		private String paramName;
		private Object paramValue;
		private String getter;
		private String setter;

		public FFCByParameter(int minSDKVersion, String manufacturer,
				String model, String paramName, Object paramValue,
				String getter, String setter) {
			this.minSDKVersion = minSDKVersion;
			this.manufacturer = manufacturer;
			this.model = model;
			this.paramName = paramName;
			this.paramValue = paramValue;
			this.getter = getter;
			this.setter = setter;
		}

		@Override
		public boolean init() throws Exception {
			if ((this.minSDKVersion == 0 || buildSDKVersion >= this.minSDKVersion)
					&& getBuildManufacturer().contains(this.manufacturer)
					&& (this.model == null || buildModel.contains(this.model))) {
				Method getter = Camera.class
						.getDeclaredMethod(this.getter != null ? this.getter
								: "getParameters");
				Method setter = Camera.class.getDeclaredMethod(
						this.setter != null ? this.setter : "setParameters",
						Parameters.class);
				Exception exception = null;
				Camera camera = Camera.open();
				try {
					Parameters parameters = (Parameters) ((Method) getter)
							.invoke(camera);
					if (initCheckParam(parameters))
						methods = new Method[] { getter, setter };
				} catch (Exception e) {
					exception = e;
				}
				if (camera != null)
					camera.release();
				if (exception != null)
					throw exception;
			}
			return (methods != null);
		}

		protected boolean initCheckParam(Parameters parameters)
				throws Exception {
			return (Parameters.class.getDeclaredMethod(
					(this.paramValue instanceof Integer ? "getInt" : "get"),
					String.class).invoke(parameters, this.paramName) != null);
		}

		@Override
		public Camera getFrontCamera() throws Exception {
			Camera camera = Camera.open();
			try {
				Parameters parameters = (Parameters) (methods[0])
						.invoke(camera);
				Parameters.class.getDeclaredMethod(
						"set",
						String.class,
						this.paramValue instanceof Integer ? int.class
								: String.class).invoke(parameters,
						this.paramName, this.paramValue);
				(methods[1]).invoke(camera, parameters);
			} catch (Exception e) {
				if (camera != null)
					camera.release();
				throw e;
			}
			return camera;
		}
	}

	private static FFCBase cameras[] = {
			new FFCBase(), // GingerBread
			new FFCByClass(
					"/system/framework/com.motorola.hardware.frontcamera.jar",
					"com.motorola.hardware.frontcamera.FrontCamera",
					"getFrontCamera", null, null), // motorola
			new FFCByClass(null, "android.hardware.HtcFrontFacingCamera",
					"getCamera", null, null), // htc
			new FFCByClass(null, "android.hardware.CameraSlave", "open", null,
					null), // huawei
			new FFCByClass(null, "com.dell.android.hardwareCameraExtensions",
					"open", new Class<?>[] { int.class }, new Object[] { 1 }), // dell-streak
			new FFCByClass(null, "android.hardware.Camera", "DualCameraSwitch",
					new Class<?>[] { int.class }, new Object[] { 1 }), // dell-streak
			new FFCByParameter(0, "samsung", null, "camera-id", 2, null, null), // samsung
			new FFCByParameter(8, "huawei", null, "mirror", "enable", null,
					null), // huawei SDK >= 8
			new FFCByParameter(0, "dell", "streak 7", "camera-sensor", 0, null,
					null), // Dell Streak 7
			new FFCByParameter(0, "lge", null, "camera-sensor", 1, null, null), // LG
			new FFCByParameter(0, "motorola", null, "camera-sensor", "1",
					"getCustomParameters", "setCustomParameters") // motorola
			{
				@Override
				protected boolean initCheckParam(Parameters parameters)
						throws Exception {
					if (super.initCheckParam(parameters))
						for (String sensor : parameters.get(
								"camera-sensor-values").split(","))
							if (sensor.trim().equals("1"))
								return true;
					return false;
				}
			}, };

	private static int deviceIndex = Integer.MIN_VALUE;

	private static int getDeviceIndex() {
		if (deviceIndex != Integer.MIN_VALUE)
			return deviceIndex;
		deviceIndex = -1;
		for (int i = 0; i < cameras.length; i++) {
			try {
				if (cameras[i].init())
					deviceIndex = i;
			} catch (Exception e) {
			}
		}
		return deviceIndex;
	}

	public static Camera getFrontFacingCamera() {
		Camera camera = null;
		int deviceIndex = getDeviceIndex();
		if (deviceIndex == -1)
			return null;
		try {
			camera = cameras[deviceIndex].getFrontCamera();
		} catch (Exception e) {
		}
		return camera;
	}

	public static boolean isSupported() {
		return (getDeviceIndex() != -1);
	}

}
