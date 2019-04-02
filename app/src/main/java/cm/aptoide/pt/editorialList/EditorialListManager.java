package cm.aptoide.pt.editorialList;

import cm.aptoide.pt.reactions.ReactionsManager;
import cm.aptoide.pt.reactions.network.LoadReactionModel;
import cm.aptoide.pt.reactions.network.ReactionsResponse;
import java.util.List;
import rx.Single;

public class EditorialListManager {

  private final EditorialListRepository editorialListRepository;
  private final ReactionsManager reactionsManager;

  public EditorialListManager(EditorialListRepository editorialListRepository,
      ReactionsManager reactionsManager) {
    this.editorialListRepository = editorialListRepository;
    this.reactionsManager = reactionsManager;
  }

  Single<EditorialListViewModel> loadEditorialListViewModel(boolean loadMore,
      boolean invalidateCache) {
    if (loadMore) {
      return loadMoreCurationCards();
    } else {
      return editorialListRepository.loadEditorialListViewModel(invalidateCache);
    }
  }

  public boolean hasMore() {
    return editorialListRepository.hasMore();
  }

  private Single<EditorialListViewModel> loadMoreCurationCards() {
    return editorialListRepository.loadMoreCurationCards();
  }

  public Single<List<CurationCard>> loadReactionModel(String cardId, String groupId) {
    return reactionsManager.loadReactionModel(cardId, groupId)
        .flatMap(loadReactionModel -> editorialListRepository.loadEditorialListViewModel(false)
            .flatMap(
                editorialListViewModel -> getUpdatedCards(editorialListViewModel, loadReactionModel,
                    cardId)));
  }

  private Single<List<CurationCard>> getUpdatedCards(EditorialListViewModel editorialViewModel,
      LoadReactionModel loadReactionModel, String cardId) {
    List<CurationCard> curationCards = editorialViewModel.getCurationCards();
    for (CurationCard curationCard : curationCards) {
      if (curationCard.getId()
          .equals(cardId)) {
        curationCard.setReactions(loadReactionModel.getTopReactionList());
        curationCard.setNumberOfReactions(loadReactionModel.getTotal());
        curationCard.setUserReaction(loadReactionModel.getMyReaction());
      }
    }
    editorialListRepository.updateCache(editorialViewModel, curationCards);
    return Single.just(curationCards);
  }

  public Single<ReactionsResponse> setReaction(String cardId, String groupId, String reaction) {
    return reactionsManager.setReaction(cardId, groupId, reaction);
  }
}
