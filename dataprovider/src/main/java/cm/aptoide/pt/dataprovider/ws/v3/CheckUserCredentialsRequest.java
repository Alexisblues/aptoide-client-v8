/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 26/04/2016.
 */

package cm.aptoide.pt.dataprovider.ws.v3;

import cm.aptoide.pt.dataprovider.ws.BodyInterceptor;
import cm.aptoide.pt.model.v3.CheckUserCredentialsJson;
import okhttp3.OkHttpClient;
import retrofit2.Converter;
import rx.Observable;

public class CheckUserCredentialsRequest extends V3<CheckUserCredentialsJson> {

  private final boolean createStore;

  private CheckUserCredentialsRequest(BaseBody baseBody, boolean createStore,
      BodyInterceptor<BaseBody> bodyInterceptor, OkHttpClient httpClient,
      Converter.Factory converterFactory) {
    super(baseBody, httpClient, converterFactory, bodyInterceptor);
    this.createStore = createStore;
  }

  public static CheckUserCredentialsRequest toCreateStore(BodyInterceptor<BaseBody> bodyInterceptor,
      OkHttpClient httpClient, Converter.Factory converterFactory, String storeName) {

    final BaseBody body = new BaseBody();
    body.put("mode", "json");
    body.put("createRepo", "1");
    body.put("oauthCreateRepo", "true");
    body.put("repo", storeName);

    return new CheckUserCredentialsRequest(body, true, bodyInterceptor, httpClient,
        converterFactory);
  }

  public static CheckUserCredentialsRequest toEditStore(BodyInterceptor<BaseBody> bodyInterceptor,
      OkHttpClient httpClient, Converter.Factory converterFactory, String accessToken) {
    final BaseBody body = new BaseBody();
    body.put("access_token", accessToken);
    body.put("mode", "json");
    return new CheckUserCredentialsRequest(body, false, bodyInterceptor, httpClient,
        converterFactory);
  }

  @Override
  protected Observable<CheckUserCredentialsJson> loadDataFromNetwork(Interfaces interfaces,
      boolean bypassCache) {
    if (createStore) {
      return interfaces.checkUserCredentials(map, bypassCache);
    }

    return interfaces.getUserInfo(map, bypassCache);
  }
}
