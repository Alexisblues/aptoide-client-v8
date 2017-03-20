/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 27/06/2016.
 */

package cm.aptoide.accountmanager;

import android.accounts.AccountManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import cm.aptoide.pt.crashreports.CrashReport;
import cm.aptoide.pt.dataprovider.exception.AptoideWsV3Exception;
import cm.aptoide.pt.dataprovider.ws.v3.ChangeUserSettingsRequest;
import cm.aptoide.pt.dataprovider.ws.v3.CheckUserCredentialsRequest;
import cm.aptoide.pt.dataprovider.ws.v3.CreateUserRequest;
import cm.aptoide.pt.dataprovider.ws.v3.GetUserRepoSubscriptionRequest;
import cm.aptoide.pt.dataprovider.ws.v3.OAuth2AuthenticationRequest;
import cm.aptoide.pt.dataprovider.ws.v3.V3;
import cm.aptoide.pt.dataprovider.ws.v7.ChangeStoreSubscriptionResponse;
import cm.aptoide.pt.dataprovider.ws.v7.SetUserRequest;
import cm.aptoide.pt.dataprovider.ws.v7.V7;
import cm.aptoide.pt.dataprovider.ws.v7.store.ChangeStoreSubscriptionRequest;
import cm.aptoide.pt.interfaces.AptoideClientUUID;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.model.v3.CheckUserCredentialsJson;
import cm.aptoide.pt.model.v3.Subscription;
import com.jakewharton.rxrelay.PublishRelay;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class AptoideAccountManager {

  public static final String IS_FACEBOOK_OR_GOOGLE = "facebook_google";
  private static final String ACCESS = "access";
  private static final String ACCESS_CONFIRMED = "access_confirmed";
  private static final String MATURE_SWITCH = "aptoide_account_manager_mature_switch";
  private static final String LOGIN_MODE = "aptoide_account_manager_login_mode";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String REFRESH_TOKEN = "refresh_token";
  private static final String USER_NICK_NAME = "username";
  private static final String QUEUE_NAME = "queueName";
  private static final String USER_AVATAR = "useravatar";
  private static final String USER_REPO = "userRepo";
  private static final String USER_ID = "userId";
  private static final String REPO_AVATAR = "storeAvatar";
  private final static String TAG = AptoideAccountManager.class.getSimpleName();

  private final String accountType;
  private final AptoideClientUUID aptoideClientUUID;
  private final AccountManager androidAccountManager;
  private final BodyInterceptorFactory interceptorFactory;
  private final Analytics analytics;
  private final StoreDataPersist storeDataPersist;
  private final CredentialsValidator credentialsValidator;
  private final PublishRelay<Account> accountSubject;
  private final ExternalAccountFactory accountFactory;

  private Account accountCache;

  public AptoideAccountManager(AccountManager androidAccountManager,
      AptoideClientUUID aptoideClientUUID, Analytics analytics, String accountType,
      BodyInterceptorFactory bodyInteceptorFactory, StoreDataPersist storeDataPersist,
      CredentialsValidator credentialsValidator, ExternalAccountFactory accountFactory) {
    this.aptoideClientUUID = aptoideClientUUID;
    this.credentialsValidator = credentialsValidator;
    this.androidAccountManager = androidAccountManager;
    this.analytics = analytics;
    this.accountType = accountType;
    this.storeDataPersist = storeDataPersist;
    this.accountFactory = accountFactory;
    this.accountSubject = PublishRelay.create();
    this.interceptorFactory = bodyInteceptorFactory;
  }

  public Observable<Account> accountStatus() {
    return Observable.merge(accountSubject,
        getAccountAsync().onErrorReturn(throwable -> createLocalAccount()).toObservable());
  }

  public Single<Account> getAccountAsync() {
    if (accountCache != null) {
      return Single.just(accountCache);
    }

    return getAndroidAccountAsync().flatMap(androidAccount -> {

      final Account.Access access =
          getAccessFrom(androidAccountManager.getUserData(androidAccount, ACCESS));

      return storeDataPersist.get()
          .doOnSuccess(stores -> {
            Logger.d("AptoideAccountManager", "nr stores= " + (stores != null ? stores.size() : 0));
          })
          .doOnError(err -> CrashReport.getInstance().log(err))
          .map(stores -> createAccount(access, stores,
              androidAccountManager.getUserData(androidAccount, USER_ID), androidAccount.name,
              androidAccountManager.getUserData(androidAccount, USER_NICK_NAME),
              androidAccountManager.getUserData(androidAccount, USER_AVATAR),
              androidAccountManager.getUserData(androidAccount, REFRESH_TOKEN),
              androidAccountManager.getUserData(androidAccount, ACCESS_TOKEN),
              androidAccountManager.getPassword(androidAccount),
              Account.Type.valueOf(androidAccountManager.getUserData(androidAccount, LOGIN_MODE)),
              androidAccountManager.getUserData(androidAccount, USER_REPO),
              androidAccountManager.getUserData(androidAccount, REPO_AVATAR),
              Boolean.valueOf(androidAccountManager.getUserData(androidAccount, MATURE_SWITCH)),
              Boolean.valueOf(
                  androidAccountManager.getUserData(androidAccount, ACCESS_CONFIRMED))));
    });
  }

  private Single<android.accounts.Account> getAndroidAccountAsync() {
    return Single.defer(() -> {
      try {
        return Single.just(getAndroidAccount());
      } catch (IllegalStateException e) {
        return Single.error(e);
      }
    }).observeOn(Schedulers.from(Executors.newSingleThreadExecutor()));
  }

  @NonNull private Account.Access getAccessFrom(String serverAccess) {
    return TextUtils.isEmpty(serverAccess) ? Account.Access.UNLISTED
        : Account.Access.valueOf(serverAccess.toUpperCase());
  }

  private android.accounts.Account getAndroidAccount() throws IllegalStateException {
    final android.accounts.Account[] accounts =
        androidAccountManager.getAccountsByType(accountType);

    if (accounts.length == 0) {
      throw new IllegalStateException("No account found.");
    }
    return accounts[0];
  }

  /**
   * Use {@link Account#isLoggedIn()} instead.
   *
   * @return true if user is logged in, false otherwise.
   */
  @Deprecated public boolean isLoggedIn() {
    final Account account = getAccount();
    return account != null && account.isLoggedIn();
  }

  /**
   * Use {@link #getAccountAsync()} method instead.
   *
   * @return user Account
   */
  @Deprecated public Account getAccount() {
    if (accountCache != null) {
      return accountCache;
    }

    return getAccountAsync().onErrorReturn(throwable -> null).toBlocking().value();
  }

  public Completable logout() {
    return getAccountAsync().flatMapCompletable(account -> account.logout())
        .doOnCompleted(() -> removeAccount());
  }

  public void removeAccount() {
    getAndroidAccountAsync().doOnSuccess(androidAccount -> {
      if (Build.VERSION.SDK_INT >= 22) {
        androidAccountManager.removeAccountExplicitly(androidAccount);
      } else {
        androidAccountManager.removeAccount(androidAccount, null, null);
      }
      accountCache = null;
      emitAccount(createLocalAccount());
    }).onErrorReturn(throwable -> null).toBlocking().value();
  }

  /**
   * This method uses a 200 millis delay due to other bugs in the app (such as improper component
   * lifecycle in most fragments). After the proper component lifecycle is done, remove this delay.
   *
   * @param account boolean that indicates the current state of the user log in. This will
   * propagate to all listeners of {@link #accountStatus()}
   */
  private void emitAccount(Account account) {
    Observable.timer(650, TimeUnit.MILLISECONDS)
        .doOnNext(__ -> accountSubject.call(account))
        .subscribe(__ -> {
        }, err -> CrashReport.getInstance().log(err));
  }

  public Completable refreshToken() {
    return getAccountAsync().flatMapCompletable(
        account -> account.refreshToken().andThen(saveAccount(account)));
  }

  private Completable saveAccount(Account account) {
    return Completable.defer(() -> {
      final android.accounts.Account[] androidAccounts =
          androidAccountManager.getAccountsByType(accountType);

      final android.accounts.Account androidAccount;
      if (androidAccounts.length == 0) {
        androidAccount = new android.accounts.Account(account.getEmail(), accountType);
        try {
          androidAccountManager.addAccountExplicitly(androidAccount, account.getPassword(), null);
        } catch (SecurityException e) {
          return Completable.error(e);
        }
      } else {
        androidAccount = androidAccounts[0];
      }

      androidAccountManager.setUserData(androidAccount, USER_ID, account.getId());
      androidAccountManager.setUserData(androidAccount, USER_NICK_NAME, account.getNickname());
      androidAccountManager.setUserData(androidAccount, USER_AVATAR, account.getAvatar());
      androidAccountManager.setUserData(androidAccount, REFRESH_TOKEN, account.getRefreshToken());
      androidAccountManager.setUserData(androidAccount, ACCESS_TOKEN, account.getToken());
      androidAccountManager.setUserData(androidAccount, LOGIN_MODE, account.getType().name());
      androidAccountManager.setUserData(androidAccount, USER_REPO, account.getStore());
      androidAccountManager.setUserData(androidAccount, REPO_AVATAR, account.getStoreAvatar());
      androidAccountManager.setUserData(androidAccount, MATURE_SWITCH,
          String.valueOf(account.isAdultContentEnabled()));
      androidAccountManager.setUserData(androidAccount, ACCESS, account.getAccess().name());
      androidAccountManager.setUserData(androidAccount, ACCESS_CONFIRMED,
          String.valueOf(account.isAccessConfirmed()));

      accountCache = account;
      emitAccount(account);
      return storeDataPersist.persist(account.getSubscribedStores());
    }).subscribeOn(Schedulers.io());
  }

  public Completable createAccount(String email, String password) {
    return credentialsValidator.validate(email, password, true)
        .andThen(CreateUserRequest.of(email.toLowerCase(), password,
            aptoideClientUUID.getUniqueIdentifier()).observe(true))
        .toSingle()
        .flatMapCompletable(response -> {
          if (response.hasErrors()) {
            return Completable.error(new AccountException(response.getErrors()));
          }
          return login(Account.Type.APTOIDE, email, password, null);
        })
        .doOnCompleted(() -> analytics.signUp())
        .onErrorResumeNext(throwable -> {
          if (throwable instanceof SocketTimeoutException) {
            return login(Account.Type.APTOIDE, email, password, null);
          }

          if (throwable instanceof AptoideWsV3Exception) {
            return Completable.error(new AccountException(
                ((AptoideWsV3Exception) throwable).getBaseResponse().getError()));
          }

          return Completable.error(throwable);
        });
  }

  public Completable login(Account.Type type, final String email, final String password,
      final String name) {
    return credentialsValidator.validate(email, password, false)
        .andThen(OAuth2AuthenticationRequest.of(email, password, type.name(), name,
            aptoideClientUUID.getUniqueIdentifier())
            .observe()
            .toSingle()
            .flatMapCompletable(oAuth -> {
              if (!oAuth.hasErrors()) {
                return syncAccount(oAuth.getAccessToken(), oAuth.getRefreshToken(), password, type);
              } else {
                return Completable.error(new AccountException(oAuth.getError()));
              }
            }))
        .onErrorResumeNext(throwable -> {
          if (throwable instanceof AptoideWsV3Exception) {
            return Completable.error(new AccountException(
                ((AptoideWsV3Exception) throwable).getBaseResponse().getError()));
          }

          return Completable.error(throwable);
        })
        .doOnCompleted(() -> analytics.login(email));
  }

  private Completable syncAccount(String accessToken, String refreshToken, String encryptedPassword,
      Account.Type type) {
    return Single.zip(getServerAccount(accessToken), getSubscribedStores(accessToken),
        (response, stores) -> mapServerAccountToAccount(response, refreshToken, accessToken,
            encryptedPassword, type, stores)).flatMapCompletable(account -> saveAccount(account));
  }

  private Single<CheckUserCredentialsJson> getServerAccount(String accessToken) {
    return CheckUserCredentialsRequest.of(accessToken).observe().toSingle().flatMap(response -> {
      if (response.getStatus().equals("OK")) {
        return Single.just(response);
      }
      return Single.error(new IllegalStateException("Failed to get user account"));
    });
  }

  private Single<List<Store>> getSubscribedStores(String accessToken) {
    return GetUserRepoSubscriptionRequest.of(accessToken)
        .observe()
        .observeOn(AndroidSchedulers.mainThread())
        .map(getUserRepoSubscription -> getUserRepoSubscription.getSubscription())
        .flatMapIterable(list -> list)
        .map(store -> mapToStore(store))
        .toList()
        .toSingle();
  }

  private Account mapServerAccountToAccount(CheckUserCredentialsJson serverUser,
      String refreshToken, String accessToken, String encryptedPassword, Account.Type accountType,
      List<Store> subscribedStores) {
    return createAccount(Account.Access.valueOf(serverUser.getAccess()), subscribedStores,
        String.valueOf(serverUser.getId()), serverUser.getEmail(), serverUser.getUsername(),
        serverUser.getAvatar(), refreshToken, accessToken, encryptedPassword, accountType,
        serverUser.getRepo(), serverUser.getRavatarHd(),
        serverUser.getSettings().getMatureswitch().equals("active"),
        serverUser.isAccessConfirmed());
  }

  private Account createAccount(Account.Access access, List<Store> stores, String id, String name,
      String nickname, String avatar, String refreshToken, String token, String password,
      Account.Type type, String store, String storeAvatar, boolean adultContentEnabled,
      boolean accessConfirmed) {
    final Account aptoideAccount =
        new AptoideAccount(id, name, nickname, avatar, refreshToken, token, password, type, store,
            storeAvatar, adultContentEnabled, access, accessConfirmed, stores, aptoideClientUUID);
    switch (type) {
      case APTOIDE:
        return aptoideAccount;
      case FACEBOOK:
        return accountFactory.createFacebookAccount(aptoideAccount);
      case GOOGLE:
        return accountFactory.createGoogleAccount(aptoideAccount);
      case ABAN:
        return accountFactory.createABANAccount(aptoideAccount);
      default:
        throw new IllegalArgumentException("Illegal account type " + type);
    }
  }

  private Account createLocalAccount() {
    return new LocalAccount();
  }

  private Store mapToStore(Subscription subscription) {
    Store store = new Store(Long.parseLong(subscription.getDownloads()),
        subscription.getAvatarHd() != null ? subscription.getAvatarHd() : subscription.getAvatar(),
        subscription.getId().longValue(), subscription.getName(), subscription.getTheme(), null,
        null);
    return store;
  }

  /**
   * Use {@link Account#getToken()} instead.
   *
   * @return account access token.
   */
  @Deprecated public String getAccessToken() {
    final Account account = getAccount();
    return account == null ? null : account.getToken();
  }

  public void unsubscribeStore(String storeName, String storeUserName, String storePassword) {
    changeSubscription(storeName, storeUserName, storePassword,
        ChangeStoreSubscriptionResponse.StoreSubscriptionState.UNSUBSCRIBED).subscribe(success -> {
    }, throwable -> CrashReport.getInstance().log(throwable));
  }

  private Observable<ChangeStoreSubscriptionResponse> changeSubscription(String storeName,
      String storeUserName, String sha1Password,
      ChangeStoreSubscriptionResponse.StoreSubscriptionState subscription) {
    return ChangeStoreSubscriptionRequest.of(storeName, subscription, storeUserName, sha1Password,
        interceptorFactory.create(this)).observe();
  }

  public Completable subscribeStore(String storeName, String storeUserName, String storePassword) {
    return changeSubscription(storeName, storeUserName, storePassword,
        ChangeStoreSubscriptionResponse.StoreSubscriptionState.SUBSCRIBED).toCompletable();
  }

  /**
   * Use {@link Account#getEmail()} instead.
   *
   * @return user e-mail.
   */
  @Deprecated public String getAccountEmail() {
    final Account account = getAccount();
    return account == null ? null : account.getEmail();
  }

  /**
   * Use {@link Account#isAdultContentEnabled()} instead.
   *
   * @return true if adult content enabled, false otherwise.
   */
  @Deprecated public boolean isAccountMature() {
    final Account account = getAccount();
    return account == null ? false : account.isAdultContentEnabled();
  }

  /**
   * Use {@link Account#isAccessConfirmed()} instead.
   *
   * @return true if user {@link Account.Access} level is confirmed, false otherwise.
   */
  @Deprecated public boolean isAccountAccessConfirmed() {
    final Account account = getAccount();
    return account == null ? false : account.isAccessConfirmed();
  }

  /**
   * Use {@link Account#getAccess()} instead.
   *
   * @return user {@link Account.Access} level.
   */
  @Deprecated public Account.Access getAccountAccess() {
    return getAccount().getAccess();
  }

  public Completable syncCurrentAccount() {
    return getAccountAsync().flatMapCompletable(
        account -> syncAccount(account.getToken(), account.getRefreshToken(), account.getPassword(),
            account.getType()));
  }

  public Completable updateAccount(boolean adultContentEnabled) {
    return getAccountAsync().flatMapCompletable(account -> {
      return ChangeUserSettingsRequest.of(adultContentEnabled, account.getToken())
          .observe(true)
          .toSingle()
          .flatMapCompletable(response -> {
            if (response.getStatus().equals("OK")) {
              return syncAccount(account.getToken(), account.getRefreshToken(),
                  account.getPassword(), account.getType());
            } else {
              return Completable.error(new Exception(V3.getErrorMessage(response)));
            }
          });
    });
  }

  public Completable updateAccount(Account.Access access) {
    return getAccountAsync().flatMapCompletable(account -> {
      return SetUserRequest.of(access.name(), interceptorFactory.create(this))
          .observe(true)
          .toSingle()
          .flatMapCompletable(response -> {
            if (response.isOk()) {
              return syncAccount(account.getToken(), account.getRefreshToken(),
                  account.getPassword(), account.getType());
            } else {
              return Completable.error(new Exception(V7.getErrorMessage(response)));
            }
          });
    });
  }

  public Completable updateAccount(String nickname, String avatarPath) {
    return getAccountAsync().flatMapObservable(account -> {
      if (TextUtils.isEmpty(nickname)) {
        return Observable.error(
            new AccountValidationException(AccountValidationException.EMPTY_NAME));
      }
      return CreateUserRequest.of(account.getEmail(), nickname, account.getPassword(),
          (TextUtils.isEmpty(avatarPath) ? "" : avatarPath),
          aptoideClientUUID.getUniqueIdentifier(), getAccessToken()).observe(true);
    }).flatMap(response -> {
      if (!response.hasErrors()) {
        return Observable.just(response);
      } else {
        return Observable.error(new AccountException(response.getErrors()));
      }
    }).toCompletable();
  }
}
