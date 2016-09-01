/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 28/07/2016.
 */

package cm.aptoide.pt.v8engine.fragment.implementations;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.trello.rxlifecycle.FragmentEvent;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import cm.aptoide.pt.actions.PermissionManager;
import cm.aptoide.pt.actions.PermissionRequest;
import cm.aptoide.pt.database.realm.Download;
import cm.aptoide.pt.database.realm.Scheduled;
import cm.aptoide.pt.database.schedulers.RealmSchedulers;
import cm.aptoide.pt.downloadmanager.AptoideDownloadManager;
import cm.aptoide.pt.downloadmanager.DownloadServiceHelper;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.utils.ShowMessage;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.fragment.GridRecyclerFragment;
import cm.aptoide.pt.v8engine.install.InstallManager;
import cm.aptoide.pt.v8engine.install.provider.DownloadInstallationProvider;
import cm.aptoide.pt.v8engine.repository.ScheduledDownloadRepository;
import cm.aptoide.pt.v8engine.util.DownloadFactory;
import cm.aptoide.pt.v8engine.view.recycler.base.BaseAdapter;
import cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.grid.ScheduledDownloadDisplayable;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by sithengineer on 19/07/16.
 */
public class ScheduledDownloadsFragment extends GridRecyclerFragment {

	private static final String TAG = ScheduledDownloadsFragment.class.getSimpleName();

	private TextView emptyData;
	private ScheduledDownloadRepository scheduledDownloadRepository;
	private LinkedList<Download> downloadList;

	public ScheduledDownloadsFragment() {
	}

	public static ScheduledDownloadsFragment newInstance() {
		return new ScheduledDownloadsFragment();
	}

	@Override
	public void load(boolean refresh, Bundle savedInstanceState) {
		Logger.d(TAG, "refresh excluded updates? " + (refresh ? "yes" : "no"));

		fetchScheduledDownloads();
	}

	@Override
	public int getContentViewId() {
		return R.layout.fragment_with_toolbar;
	}

	@Override
	public void bindViews(View view) {
		super.bindViews(view);
		emptyData = (TextView) view.findViewById(R.id.empty_data);
		scheduledDownloadRepository = new ScheduledDownloadRepository();
		setHasOptionsMenu(true);
		downloadList = new LinkedList<>();
	}

	@Override
	public void setupToolbar() {
		super.setupToolbar();
		if (toolbar != null) {
			ActionBar bar = ((AppCompatActivity) getActivity()).getSupportActionBar();
			bar.setDisplayHomeAsUpEnabled(true);
			bar.setTitle(R.string.setting_schdwntitle);
		}
	}

	private void fetchScheduledDownloads() {
		scheduledDownloadRepository
				.getAllScheduledUpdates()
				//.subscribeOn(RealmSchedulers.getScheduler())
				.subscribeOn(AndroidSchedulers.mainThread())
				.observeOn(AndroidSchedulers.mainThread())
				.compose(bindUntilEvent(FragmentEvent.DESTROY_VIEW))
				.subscribe(scheduledDownloads -> {
					updateUi(scheduledDownloads);
		}, t -> {
			Logger.e(TAG, t);
			emptyData.setText(R.string.no_sch_downloads);
			emptyData.setVisibility(View.VISIBLE);
			clearDisplayables();
			finishLoading();
		});
	}

	@UiThread
	private void updateUi(List<Scheduled> scheduledDownloadList) {
		if (scheduledDownloadList == null || scheduledDownloadList.isEmpty()) {
			emptyData.setText(R.string.no_sch_downloads);
			emptyData.setVisibility(View.VISIBLE);
			clearDisplayables();
			finishLoading();
		} else {
			emptyData.setVisibility(View.GONE);
			ArrayList<ScheduledDownloadDisplayable> displayables = new ArrayList<>(scheduledDownloadList.size());
			for (final Scheduled scheduledDownload : scheduledDownloadList) {
				displayables.add(new ScheduledDownloadDisplayable(scheduledDownload));
			}
			setDisplayables(displayables);
		}
	}

	@Override
	public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.menu_scheduled_downloads_fragment, menu);
	}

	@Override
	public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
		super.onViewStateRestored(savedInstanceState);
		// TODO: 31/08/16 sithengineer  
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// TODO: 31/08/16 sithengineer
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();

		if (itemId == android.R.id.home) {
			getActivity().onBackPressed();
			return true;
		}

		if (itemId == R.id.menu_install_selected) {

			DownloadFactory factory = new DownloadFactory();
			BaseAdapter adapter = getAdapter();
			downloadList.clear();
			for (int i = 0 ; i < adapter.getItemCount() ; ++i) {
				ScheduledDownloadDisplayable displayable = ((ScheduledDownloadDisplayable) adapter.getDisplayable(i));
				if (displayable.isSelected()) {
					downloadList.add(factory.create(displayable.getPojo()));
					displayable.updateUi(true);
				}
			}

			if (downloadList.size() > 0) {
				downloadAndInstall(downloadList)
						.compose(bindUntilEvent(FragmentEvent.STOP))
						.subscribe(aVoid -> {
							Logger.i(TAG, "finished installing scheduled downloads");
						});

				ShowMessage.asSnack(this.emptyData, R.string.installing_msg);
			} else {
				ShowMessage.asSnack(this.emptyData, R.string.schDown_nodownloadselect);
			}

			return true;
		}

		if (itemId == R.id.menu_select_all) {
			BaseAdapter adapter = getAdapter();
			for (int i = 0 ; i < adapter.getItemCount() ; ++i) {
				((ScheduledDownloadDisplayable) adapter.getDisplayable(i)).setSelected(true);
				adapter.notifyDataSetChanged();
			}
			return true;
		}

		if (itemId == R.id.menu_select_none) {
			BaseAdapter adapter = getAdapter();
			for (int i = 0 ; i < adapter.getItemCount() ; ++i) {
				((ScheduledDownloadDisplayable) adapter.getDisplayable(i)).setSelected(false);
				adapter.notifyDataSetChanged();
			}
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public Observable<List<Download>> downloadAndInstall(List<Download> downloadList) {
		PermissionManager permissionManager = new PermissionManager();
		DownloadServiceHelper downloadManager = new DownloadServiceHelper(AptoideDownloadManager.getInstance(), permissionManager);
		InstallManager installManager = new InstallManager(permissionManager, getContext().getPackageManager(), new DownloadInstallationProvider
				(downloadManager));
		PermissionRequest permissionRequest = ((PermissionRequest) getContext());
		DownloadServiceHelper downloadServiceHelper = new DownloadServiceHelper(AptoideDownloadManager.getInstance(), new PermissionManager());
		Context ctx = getContext();

		return Observable.from(downloadList)
				.flatMap(downloadItem -> downloadAndInstall(downloadItem, permissionRequest, downloadServiceHelper, installManager, ctx))
				.buffer(downloadList.size()) // buffer all downloads in one event
				.first(); // return the whole list in one (first) event
	}

	private Observable<Download> downloadAndInstall(Download download, PermissionRequest permissionRequest, DownloadServiceHelper downloadServiceHelper,
	                                                InstallManager installManager, Context context) {
		Logger.v(TAG, "downloading app with id " + download.getAppId());
		return downloadServiceHelper.startDownload(permissionRequest, download)
				.map(downloadItem -> { // for logging purposes only
					Logger.d(
							TAG,
							String.format("scheduled download progress = %d and status = %d for app id %d",
							downloadItem.getOverallProgress(),
							downloadItem.getOverallDownloadStatus(),
							downloadItem.getAppId())
					);
					return downloadItem;
				})
				.filter(downloadItem -> downloadItem.getOverallDownloadStatus()==Download.COMPLETED)
				.flatMap(downloadItem ->
					installAndRemoveFromList(installManager, context, downloadItem.getAppId()).map(aVoid -> downloadItem)
			);
	}

	private Observable<Void> installAndRemoveFromList(InstallManager installManager, Context context, long appId) {
		Logger.v(TAG, "installing app with id " + appId);
		return installManager.install(context, (PermissionRequest) context, appId)
				.doOnCompleted(()-> Logger.d(TAG, "Scheduled Downloads do on completed called for install manager"))
				.doOnError(err -> Logger.e(TAG, err))
				.doOnUnsubscribe(()-> Logger.d(TAG, "Scheduled Downloads do on unsubscribed called for install manager"))
				//.concatWith(scheduledDownloadRepository.deleteScheduledDownload(appId)); // not working
				.flatMap(aVoid -> scheduledDownloadRepository.deleteScheduledDownload(appId));
	}
}
