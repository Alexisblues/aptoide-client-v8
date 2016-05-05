/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 05/05/2016.
 */

package cm.aptoide.pt.v8engine.activity;

import android.support.v7.widget.Toolbar;

import cm.aptoide.pt.v8engine.R;

/**
 * Created by neuro on 05-05-2016.
 */
public abstract class AptoideSimpleFragmentActivity extends AptoideFragmentActivity {

	protected Toolbar mToolbar;

	@Override
	protected void setupViews() {
	}

	@Override
	protected void setupToolbar() {
		if (mToolbar != null) {
			setSupportActionBar(mToolbar);
			mToolbar.setLogo(R.drawable.ic_aptoide_toolbar);
		}
	}

	@Override
	protected void bindViews() {
		mToolbar = (Toolbar) findViewById(R.id.toolbar);
	}
}
