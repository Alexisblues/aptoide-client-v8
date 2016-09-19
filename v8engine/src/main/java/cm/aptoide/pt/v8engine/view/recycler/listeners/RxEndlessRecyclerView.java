package cm.aptoide.pt.v8engine.view.recycler.listeners;

import android.support.v7.widget.RecyclerView;
import cm.aptoide.pt.v8engine.view.recycler.base.BaseAdapter;
import rx.Observable;

/**
 * Created by marcelobenites on 6/27/16.
 */
public final class RxEndlessRecyclerView {

  public static Observable<Void> loadMore(RecyclerView recyclerView, BaseAdapter adapter) {
    return Observable.create(new EndlessRecyclerViewLoadMoreOnSubscribe(recyclerView, adapter));
  }

  private RxEndlessRecyclerView() {
    new AssertionError("No instances!");
  }
}
