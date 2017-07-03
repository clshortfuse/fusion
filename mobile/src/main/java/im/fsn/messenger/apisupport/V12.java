package im.fsn.messenger.apisupport;

import android.annotation.TargetApi;
import android.graphics.Bitmap;

@TargetApi(12)
public class V12 {
	public static boolean sameAs(Bitmap bitmap, Bitmap other) {
		return bitmap.sameAs(other);
	}
}
