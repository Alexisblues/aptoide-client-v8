/*
 * Copyright (c) 2016.
 * Modified by Marcelo Benites on 11/08/2016.
 */

package cm.aptoide.pt.billing;

import android.content.SharedPreferences;
import android.content.res.Resources;
import cm.aptoide.pt.billing.exception.MerchantNotFoundException;
import cm.aptoide.pt.billing.exception.ProductNotFoundException;
import cm.aptoide.pt.billing.exception.PurchaseNotFoundException;
import cm.aptoide.pt.billing.product.InAppProduct;
import cm.aptoide.pt.billing.product.InAppPurchase;
import cm.aptoide.pt.billing.product.PaidAppProduct;
import cm.aptoide.pt.billing.product.ProductFactory;
import cm.aptoide.pt.dataprovider.interfaces.TokenInvalidator;
import cm.aptoide.pt.dataprovider.model.v3.ErrorResponse;
import cm.aptoide.pt.dataprovider.model.v3.PaidApp;
import cm.aptoide.pt.dataprovider.ws.BodyInterceptor;
import cm.aptoide.pt.dataprovider.ws.v3.BaseBody;
import cm.aptoide.pt.dataprovider.ws.v3.GetApkInfoRequest;
import cm.aptoide.pt.dataprovider.ws.v3.InAppBillingConsumeRequest;
import cm.aptoide.pt.dataprovider.ws.v3.V3;
import cm.aptoide.pt.dataprovider.ws.v7.V7;
import cm.aptoide.pt.dataprovider.ws.v7.billing.GetMerchantRequest;
import cm.aptoide.pt.dataprovider.ws.v7.billing.GetProductsRequest;
import cm.aptoide.pt.dataprovider.ws.v7.billing.GetPurchasesRequest;
import cm.aptoide.pt.dataprovider.ws.v7.billing.GetServicesRequest;
import cm.aptoide.pt.install.PackageRepository;
import java.util.Collections;
import java.util.List;
import okhttp3.OkHttpClient;
import retrofit2.Converter;
import rx.Completable;
import rx.Observable;
import rx.Single;

public class V3BillingService implements BillingService {

  private final BodyInterceptor<BaseBody> bodyInterceptorV3;
  private final OkHttpClient httpClient;
  private final Converter.Factory converterFactory;
  private final TokenInvalidator tokenInvalidator;
  private final SharedPreferences sharedPreferences;
  private final PurchaseMapper purchaseMapper;
  private final ProductFactory productFactory;
  private final PackageRepository packageRepository;
  private final PaymentMethodMapper paymentMethodMapper;
  private final Resources resources;
  private final BillingIdResolver idResolver;
  private final int apiVersion;
  private final BodyInterceptor<cm.aptoide.pt.dataprovider.ws.v7.BaseBody> bodyInterceptorV7;

  public V3BillingService(BodyInterceptor<BaseBody> bodyInterceptorV3,
      BodyInterceptor<cm.aptoide.pt.dataprovider.ws.v7.BaseBody> bodyInterceptorV7,
      OkHttpClient httpClient, Converter.Factory converterFactory,
      TokenInvalidator tokenInvalidator, SharedPreferences sharedPreferences,
      PurchaseMapper purchaseMapper, ProductFactory productFactory,
      PackageRepository packageRepository, PaymentMethodMapper paymentMethodMapper,
      Resources resources, BillingIdResolver idResolver, int apiVersion) {
    this.bodyInterceptorV3 = bodyInterceptorV3;
    this.httpClient = httpClient;
    this.converterFactory = converterFactory;
    this.tokenInvalidator = tokenInvalidator;
    this.sharedPreferences = sharedPreferences;
    this.purchaseMapper = purchaseMapper;
    this.productFactory = productFactory;
    this.packageRepository = packageRepository;
    this.paymentMethodMapper = paymentMethodMapper;
    this.resources = resources;
    this.idResolver = idResolver;
    this.apiVersion = apiVersion;
    this.bodyInterceptorV7 = bodyInterceptorV7;
  }

  @Override public Single<List<PaymentMethod>> getPaymentMethods() {
    return GetServicesRequest.of(sharedPreferences, httpClient, converterFactory, bodyInterceptorV7,
        tokenInvalidator)
        .observe()
        .toSingle()
        .flatMap(response -> {
          if (response != null && response.isOk()) {
            return Single.just(paymentMethodMapper.map(response.getList()));
          } else {
            return Single.error(new IllegalArgumentException(V7.getErrorMessage(response)));
          }
        });
  }

  @Override public Single<Merchant> getMerchant(String merchantName) {
    return GetMerchantRequest.of(merchantName, bodyInterceptorV7, httpClient, converterFactory,
        tokenInvalidator, sharedPreferences)
        .observe()
        .toSingle()
        .flatMap(response -> {
          if (response != null && response.isOk()) {
            return Single.just(new Merchant(response.getData()
                .getId(), response.getData()
                .getName()));
          } else {
            return Single.error(new MerchantNotFoundException(V7.getErrorMessage(response)));
          }
        });
  }

  @Override public Completable deletePurchase(String merchantName, String purchaseToken) {
    return InAppBillingConsumeRequest.of(apiVersion, merchantName, purchaseToken, bodyInterceptorV3,
        httpClient, converterFactory, tokenInvalidator, sharedPreferences)
        .observe()
        .first()
        .toSingle()
        .flatMapCompletable(response -> {
          if (response != null && response.isOk()) {
            return Completable.complete();
          }
          if (isDeletionItemNotFound(response.getErrors())) {
            return Completable.error(new PurchaseNotFoundException(V3.getErrorMessage(response)));
          }
          return Completable.error(new IllegalArgumentException(V3.getErrorMessage(response)));
        });
  }

  @Override public Single<List<Purchase>> getPurchases(String merchantName) {
    return GetPurchasesRequest.of(merchantName, bodyInterceptorV7, httpClient, converterFactory,
        tokenInvalidator, sharedPreferences)
        .observe(true)
        .toSingle()
        .flatMap(response -> {
          if (response != null && response.isOk()) {
            return Single.just(purchaseMapper.map(response.getList()));
          }
          // If user not logged in return a empty purchase list.
          return Single.<List<Purchase>>just(Collections.emptyList());
        });
  }

  @Override public Single<Purchase> getPurchase(String merchantName, String purchaseToken) {
    return getPurchases(merchantName).flatMapObservable(purchases -> Observable.from(purchases))
        .filter(purchase -> purchaseToken.equals(((InAppPurchase) purchase).getToken()))
        .first()
        .toSingle();
  }

  @Override public Single<List<Product>> getProducts(String merchantName, List<String> productIds) {
    return getServerSKUs(merchantName, idResolver.resolveSkus(productIds), false).flatMap(
        response -> mapToProducts(merchantName, response.getList()));
  }

  @Override public Single<Purchase> getPurchase(Product product) {
    if (product instanceof InAppProduct) {
      return getPurchases(((InAppProduct) product).getPackageName()).flatMapObservable(
          purchases -> Observable.from(purchases))
          .first(purchase -> purchase.getProductId()
              .equals(idResolver.resolveSku(product.getId())))
          .toSingle();
    }

    if (product instanceof PaidAppProduct) {
      return getServerPaidApp(true, ((PaidAppProduct) product).getAppId()).map(
          app -> purchaseMapper.map(app));
    }

    throw new IllegalArgumentException("Invalid product. Must be "
        + InAppProduct.class.getSimpleName()
        + " or "
        + PaidAppProduct.class.getSimpleName());
  }

  @Override public Single<Product> getProduct(String merchantName, String productId) {
    if (idResolver.isInAppId(productId)) {
      return getInAppProduct(merchantName, productId);
    }
    if (idResolver.isPaidAppId(productId)) {
      return getPaidAppProduct(productId);
    }

    return Single.error(new IllegalArgumentException("Invalid product id " + productId));
  }

  private Single<Product> getInAppProduct(String merchantName, String productId) {
    return getServerSKUs(merchantName, Collections.singletonList(idResolver.resolveSku(productId)),
        false).flatMap(response -> mapToProducts(merchantName, response.getList()))
        .flatMap(products -> {
          if (products.isEmpty()) {
            return Single.error(new ProductNotFoundException(
                "No product found for sku: " + idResolver.resolveSku(productId)));
          }
          return Single.just(products.get(0));
        });
  }

  public Single<Product> getPaidAppProduct(String productId) {
    return getServerPaidApp(false, idResolver.resolveAppId(productId)).map(
        paidApp -> productFactory.create(paidApp));
  }

  private Single<GetProductsRequest.ResponseBody> getServerSKUs(String packageName,
      List<String> skuList, boolean bypassCache) {
    return GetProductsRequest.of(packageName, skuList, bodyInterceptorV7, httpClient,
        converterFactory, tokenInvalidator, sharedPreferences)
        .observe(bypassCache)
        .first()
        .toSingle()
        .flatMap(response -> {
          if (response != null && response.isOk()) {
            return Single.just(response);
          } else {
            return Single.error(new IllegalArgumentException(V7.getErrorMessage(response)));
          }
        });
  }

  private boolean isDeletionItemNotFound(List<ErrorResponse> errors) {
    for (ErrorResponse error : errors) {
      if (error.code.equals("PRODUCT-201")) {
        return true;
      }
    }
    return false;
  }

  private Single<PaidApp> getServerPaidApp(boolean bypassCache, long appId) {
    return GetApkInfoRequest.of(appId, bodyInterceptorV3, httpClient, converterFactory,
        tokenInvalidator, sharedPreferences, resources)
        .observe(bypassCache)
        .flatMap(response -> {
          if (response != null && response.isOk() && response.isPaid()) {
            return Observable.just(response);
          } else {
            return Observable.error(new IllegalArgumentException(V3.getErrorMessage(response)));
          }
        })
        .first()
        .toSingle();
  }

  private Single<List<Product>> mapToProducts(String packageName,
      List<GetProductsRequest.ResponseBody.Product> responseList) {
    return Single.zip(packageRepository.getPackageVersionCode(packageName),
        packageRepository.getPackageLabel(packageName),
        (packageVersionCode, applicationName) -> productFactory.create(packageName, responseList,
            packageVersionCode, applicationName));
  }
}
