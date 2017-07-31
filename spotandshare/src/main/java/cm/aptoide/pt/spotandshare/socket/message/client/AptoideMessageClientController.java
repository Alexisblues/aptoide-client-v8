package cm.aptoide.pt.spotandshare.socket.message.client;

import cm.aptoide.pt.spotandshare.socket.entities.AndroidAppInfo;
import cm.aptoide.pt.spotandshare.socket.entities.Friend;
import cm.aptoide.pt.spotandshare.socket.interfaces.OnError;
import cm.aptoide.pt.spotandshare.socket.interfaces.SocketBinder;
import cm.aptoide.pt.spotandshare.socket.interfaces.TransferLifecycleProvider;
import cm.aptoide.pt.spotandshare.socket.message.AptoideMessageController;
import cm.aptoide.pt.spotandshare.socket.message.Message;
import cm.aptoide.pt.spotandshare.socket.message.handlers.v1.DefaultClientHandlersListV1;
import cm.aptoide.pt.spotandshare.socket.message.interfaces.AndroidAppInfoAccepter;
import cm.aptoide.pt.spotandshare.socket.message.interfaces.Sender;
import cm.aptoide.pt.spotandshare.socket.message.interfaces.StorageCapacity;
import cm.aptoide.pt.spotandshare.socket.message.messages.v1.WelcomeMessage;
import java.io.IOException;

/**
 * Created by neuro on 29-01-2017.
 */
public class AptoideMessageClientController extends AptoideMessageController
    implements Sender<Message> {

  private final Friend friend;

  public AptoideMessageClientController(AptoideMessageClientSocket aptoideMessageClientSocket,
      String rootDir, StorageCapacity storageCapacity,
      TransferLifecycleProvider<AndroidAppInfo> transferLifecycleProvider,
      SocketBinder socketBinder,
      OnError<IOException> onError, AndroidAppInfoAccepter androidAppInfoAccepter, Friend friend) {
    super(DefaultClientHandlersListV1.create(rootDir, storageCapacity, transferLifecycleProvider,
        aptoideMessageClientSocket, socketBinder, androidAppInfoAccepter), onError);
    this.friend = friend;
  }

  @Override protected void doOnConnect() {
    send(new WelcomeMessage(getLocalhost(), friend));
  }
}