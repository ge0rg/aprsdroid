// (C) http://blog.tomgibara.com/post/190539066/android-unscaled-bitmaps

package org.aprsdroid.app;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.os.Build;

public abstract class UnscaledBitmapLoader {

	public static final UnscaledBitmapLoader instance;

	static {
		instance = Integer.parseInt(Build.VERSION.SDK) < 4 ? new Old() : new New();
	}

	public static Bitmap loadFromResource(Resources resources, int resId, BitmapFactory.Options options) {
		return instance.load(resources, resId, options);
	}

	private static class Old extends UnscaledBitmapLoader {

		@Override
		Bitmap load(Resources resources, int resId, Options options) {
			return BitmapFactory.decodeResource(resources, resId, options);
		}

	}

	private static class New extends UnscaledBitmapLoader {

		@Override
		Bitmap load(Resources resources, int resId, Options options) {
			if (options == null) options = new BitmapFactory.Options();
			options.inScaled = false;
			return BitmapFactory.decodeResource(resources, resId, options);
		}

	}

	abstract Bitmap load(Resources resources, int resId, BitmapFactory.Options options);

}

