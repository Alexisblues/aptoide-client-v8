package cm.aptoide.pt.abtesting.experiments;

import cm.aptoide.pt.abtesting.ABTestManager;
import cm.aptoide.pt.ads.MoPubAnalytics;

public class MoPubNativeAdExperiment {

  private final String EXPERIMENT_ID = "ASV-1377-MoPub-Ads";
  private final ABTestManager abTestManager;
  private final MoPubAnalytics moPubAnalytics;

  public MoPubNativeAdExperiment(ABTestManager abTestManager, MoPubAnalytics moPubAnalytics) {
    this.abTestManager = abTestManager;
    this.moPubAnalytics = moPubAnalytics;
  }

  public Single<Boolean> loadNative() {
    return abTestManager.getExperiment(EXPERIMENT_ID)
        .flatMap(experiment -> {
          String experimentAssignment = "default";
          if (!experiment.isExperimentOver() && experiment.isPartOfExperiment()) {
            experimentAssignment = experiment.getAssignment();
          }
          switch (experimentAssignment) {
            case "default":
            case "control_group":
              moPubAnalytics.setMoPubAbTestGroup(true);
              return Single.just(false);
            case "mopub":
              moPubAnalytics.setMoPubAbTestGroup(false);
              return Single.just(true);
            default:
              return Single.just(false);
          }
        });
  }

  public Single<Boolean> recordAdImpression() {
    return abTestManager.recordImpression(EXPERIMENT_ID);
  }

  public Single<Boolean> recordAdClick() {
    return abTestManager.recordAction(EXPERIMENT_ID);
  }
}
