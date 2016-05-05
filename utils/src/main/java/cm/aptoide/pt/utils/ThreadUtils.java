/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 05/05/2016.
 */

package cm.aptoide.pt.utils;

import cm.aptoide.pt.logger.Logger;
import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by neuro on 02-05-2016.
 */
public class ThreadUtils {

	public static void runOnUiThread(Runnable runnable) {
		Observable.just(null).observeOn(AndroidSchedulers.mainThread()).subscribe(o -> runnable.run(), Logger::printException);
	}

	public static void sleep(long l) {
		try {
			Thread.sleep(l);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
