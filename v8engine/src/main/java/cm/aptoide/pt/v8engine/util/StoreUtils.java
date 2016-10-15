package cm.aptoide.pt.v8engine.util;

import android.support.annotation.Nullable;
import cm.aptoide.pt.database.accessors.DeprecatedDatabase;
import cm.aptoide.pt.database.realm.Store;
import cm.aptoide.pt.dataprovider.ws.v7.BaseRequestWithStore;
import cm.aptoide.pt.dataprovider.ws.v7.V7Url;
import cm.aptoide.pt.dataprovider.ws.v7.store.GetStoreMetaRequest;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.model.v7.store.GetStoreMeta;
import cm.aptoide.pt.networkclient.interfaces.ErrorRequestListener;
import cm.aptoide.pt.networkclient.interfaces.SuccessRequestListener;
import io.realm.Realm;
import java.util.Locale;
import lombok.Cleanup;

/**
 * Created by neuro on 14-10-2016.
 */

public class StoreUtils {

  public static BaseRequestWithStore.StoreCredentials getStoreCredentials(long storeId) {

    @Cleanup Realm realm = DeprecatedDatabase.get();

    Store store = DeprecatedDatabase.StoreQ.get(storeId, realm);

    String username = null;
    String passwordSha1 = null;

    if (store != null) {
      username = store.getUsername();
      passwordSha1 = store.getPasswordSha1();
    }

    return new BaseRequestWithStore.StoreCredentials(storeId, username, passwordSha1);
  }

  public static BaseRequestWithStore.StoreCredentials getStoreCredentials(String storeName) {

    @Cleanup Realm realm = DeprecatedDatabase.get();

    Store store = DeprecatedDatabase.StoreQ.get(storeName, realm);

    String username = null;
    String passwordSha1 = null;

    if (store != null) {
      username = store.getUsername();
      passwordSha1 = store.getPasswordSha1();
    }

    return new BaseRequestWithStore.StoreCredentials(storeName, username, passwordSha1);
  }

  public static BaseRequestWithStore.StoreCredentials getStoreCredentialsFromUrl(String url) {

    V7Url v7Url = new V7Url(url);
    Long storeId = v7Url.getStoreId();
    String storeName = v7Url.getStoreName();
    String username;
    String passwordSha1;

    if (storeId == null && storeName == null) {
      throw new IllegalArgumentException("Given url doesn't contain a StoreId or StoreName!");
    }

    if (storeId != null) {
      return getStoreCredentials(storeId);
    } else {
      return getStoreCredentials(storeName);
    }
  }

  /**
   * If you want to do event tracking (Analytics) use (v8engine)StoreUtilsProxy.subscribeStore
   * instead, else, use this
   */
  @Deprecated public static void subscribeStore(String storeName,
      @Nullable SuccessRequestListener<GetStoreMeta> successRequestListener,
      @Nullable ErrorRequestListener errorRequestListener) {
    cm.aptoide.pt.dataprovider.ws.v7.listapps.StoreUtils.subscribeStore(
        GetStoreMetaRequest.of(getStoreCredentials(storeName)), successRequestListener,
        errorRequestListener);
  }

  public static boolean isSubscribedStore(String storeName) {
    @Cleanup Realm realm = DeprecatedDatabase.get();
    return DeprecatedDatabase.StoreQ.get(storeName, realm) != null;
  }

  public static String split(String repoUrl) {
    Logger.d("Aptoide-RepoUtils", "Splitting " + repoUrl);
    repoUrl = formatRepoUri(repoUrl);
    return repoUrl.split("http://")[1].split("\\.store")[0].split("\\.bazaarandroid.com")[0];
  }

  public static String formatRepoUri(String repoUri) {

    repoUri = repoUri.toLowerCase(Locale.ENGLISH);

    if (repoUri.contains("http//")) {
      repoUri = repoUri.replaceFirst("http//", "http://");
    }

    if (repoUri.length() != 0 && repoUri.charAt(repoUri.length() - 1) != '/') {
      repoUri = repoUri + '/';
      Logger.d("Aptoide-ManageRepo", "repo uri: " + repoUri);
    }
    if (!repoUri.startsWith("http://")) {
      repoUri = "http://" + repoUri;
      Logger.d("Aptoide-ManageRepo", "repo uri: " + repoUri);
    }

    return repoUri;
  }
}
