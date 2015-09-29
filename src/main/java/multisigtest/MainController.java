package multisigtest;

import com.google.common.collect.ImmutableList;
import com.subgraph.orchid.TorClient;
import com.subgraph.orchid.TorInitializationListener;
import multisigtest.controls.ClickableBitcoinAddress;
import multisigtest.controls.NotificationBarPane;
import multisigtest.utils.BitcoinUIModel;
import multisigtest.utils.GuiUtils;
import multisigtest.utils.easing.MyUtils;
import multisigtest.utils.easing.EasingMode;
import multisigtest.utils.easing.ElasticInterpolator;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.utils.MonetaryFormat;
import org.fxmisc.easybind.EasyBind;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Gets created auto-magically by FXMLLoader via reflection. The widget fields are set to the GUI controls they're named
 * after. This class handles all the updates and event handling for the main UI.
 */
public class MainController {
    public HBox controlsBox;
    public Label balance;
    public Button sendMoneyOutBtn;
    public ClickableBitcoinAddress addressControl;
    public ListView<Transaction> transactionList;
    public Button createMultisignTxn;
    public Button partialSignBtn;
    public Button importNewKey;
    public ListView<Address> addressList;
    public Button completeSignBtn;

    private BitcoinUIModel model = new BitcoinUIModel();
    private NotificationBarPane.Item syncItem;

    // Called by FXMLLoader.
    public void initialize() {
        addressControl.setOpacity(0.0);
    }

    public void onBitcoinSetup() {
        model.setWallet(Main.bitcoin.wallet());
        addressControl.addressProperty().bind(model.addressProperty());
        balance.textProperty().bind(EasyBind.map(model.balanceProperty(), coin -> MonetaryFormat.BTC.noCode().format(coin).toString()));
        // Don't let the user click send money when the wallet is empty.
        sendMoneyOutBtn.disableProperty().bind(model.balanceProperty().isEqualTo(Coin.ZERO));

        TorClient torClient = Main.bitcoin.peerGroup().getTorClient();
        if (torClient != null) {
            SimpleDoubleProperty torProgress = new SimpleDoubleProperty(-1);
            String torMsg = "Initialising Tor";
            syncItem = Main.instance.notificationBar.pushItem(torMsg, torProgress);
            torClient.addInitializationListener(new TorInitializationListener() {
                @Override
                public void initializationProgress(String message, int percent) {
                    Platform.runLater(() -> {
                        syncItem.label.set(torMsg + ": " + message);
                        torProgress.set(percent / 100.0);
                    });
                }

                @Override
                public void initializationCompleted() {
                    Platform.runLater(() -> {
                        syncItem.cancel();
                        showBitcoinSyncMessage();
                    });
                }
            });
        } else {
            showBitcoinSyncMessage();
        }
        model.syncProgressProperty().addListener(x -> {
            if (model.syncProgressProperty().get() >= 1.0) {
                readyToGoAnimation();
                if (syncItem != null) {
                    syncItem.cancel();
                    syncItem = null;
                }
            } else if (syncItem == null) {
                showBitcoinSyncMessage();
            }
        });
        Bindings.bindContent(transactionList.getItems(), model.getTransactions());
        Bindings.bindContent(addressList.getItems(), model.getAddresses());

        transactionList.setCellFactory(param -> new TextFieldListCell<>(new StringConverter<Transaction>() {
            @Override
            public String toString(Transaction tx) {
                long sequenceNumber = tx.getInput(0).getSequenceNumber();
                long locktime = tx.getLockTime();
                Coin value = tx.getValue(Main.bitcoin.wallet());
                if (value.isPositive()) {
                    Address address = tx.getOutput(0).getAddressFromP2PKHScript(Main.params);
//                    Address p2shAddress = Address.fromP2SHScript(Main.params, tx.getOutput(0).getScriptPubKey());
                    return "Incoming Payment of " + MonetaryFormat.BTC.format(value) + " to address " + address + '\n' + tx.toString() + "\nlocktime: " + locktime + " : sequence: " + sequenceNumber;
                } else if (value.isNegative()) {
                    Address address = tx.getOutput(0).getAddressFromP2PKHScript(Main.params);
                    return "Outbound Payment of " + MonetaryFormat.BTC.format(value) + " to address " + address + '\n' + tx.toString() + "\nlocktime: " + locktime + " : sequence: " + sequenceNumber;
                } else {
                    return "Payment with id " + tx.getHash();
                }
            }

            @Override
            public Transaction fromString(String string) {
                return null;
            }
        }));
        String mode = System.getProperty("Mode");
        if (mode != null && mode.equalsIgnoreCase("client")) {
            partialSignBtn.setDisable(true);
            completeSignBtn.setDisable(false);
        } else if (mode != null && mode.equalsIgnoreCase("server")) {
            partialSignBtn.setDisable(false);
            completeSignBtn.setDisable(true);
            createMultisignTxn.setDisable(true);
        }
        List<ECKey> importedKeys = Main.bitcoin.wallet().getImportedKeys();
        for(ECKey k : importedKeys) {
            Address addr = k.toAddress(Main.params);
            System.out.println("Imported Key: " + addr);
        }
    }

    private void showBitcoinSyncMessage() {
        syncItem = Main.instance.notificationBar.pushItem("Synchronising with the Bitcoin network", model.syncProgressProperty());
    }

    public void sendMoneyOut(ActionEvent event) {
        // Hide this UI and show the send money UI. This UI won't be clickable until the user dismisses send_money.
        Main.instance.overlayUI("send_money.fxml");
    }

    public void settingsClicked(ActionEvent event) {
        Main.OverlayUI<WalletSettingsController> screen = Main.instance.overlayUI("wallet_settings.fxml");
        screen.controller.initialize(null);
    }

    public void restoreFromSeedAnimation() {
        // Buttons slide out ...
        TranslateTransition leave = new TranslateTransition(Duration.millis(1200), controlsBox);
        leave.setByY(80.0);
        leave.play();
    }

    public void readyToGoAnimation() {
        // Buttons slide in and clickable address appears simultaneously.
        TranslateTransition arrive = new TranslateTransition(Duration.millis(1200), controlsBox);
        arrive.setInterpolator(new ElasticInterpolator(EasingMode.EASE_OUT, 1, 2));
        arrive.setToY(0.0);
        FadeTransition reveal = new FadeTransition(Duration.millis(1200), addressControl);
        reveal.setToValue(1.0);
        ParallelTransition group = new ParallelTransition(arrive, reveal);
        group.setDelay(NotificationBarPane.ANIM_OUT_DURATION);
        group.setCycleCount(1);
        group.play();
    }

    public DownloadProgressTracker progressBarUpdater() {
        return model.getDownloadProgressTracker();
    }

    public Transaction getInputTransaction(Coin amount) {

        List<Transaction> txns = Main.bitcoin.wallet().getTransactionsByTime();
        for(Transaction txn : txns) {
            Coin coin = txn.getOutput(0).getValue();
            if (coin.isPositive() && coin.isGreaterThan(amount)) {
                // need to create a new txn to send the exact amount to myself
//                GuiUtils.alertInfo("Please send the exact amount to yourself first");
                System.out.println("Spendable Txn: "+ txn);
                return txn;
            } else if (coin.compareTo(amount) == 0) {
                // found an exact amount txn
                System.out.println("Spendable Txn: "+ txn);
                return txn;
            }
        }
        return null;
    }

    public void partialSignContract(ActionEvent actionEvent) {
        Main.instance.overlayUI("partial_sign.fxml");
    }

    public void onClickImportNewKey(Event event) {
        ECKey newKey = MyUtils.loadKeyFromFile(Main.APP_NAME);
        if (newKey == null) {
            newKey = new ECKey();
        }
        System.out.println("Address    : " + newKey.toAddress(Main.params));
        System.out.println("Public Key : " + newKey.getPublicKeyAsHex());
        System.out.println("Private Key: " + newKey.getPrivateKeyAsHex());
        MyUtils.saveKeyToFile(newKey, Main.APP_NAME);
//        ECKey restoredKey = MyUtils.loadKeyFromFile(Main.APP_NAME);
        Main.bitcoin.wallet().importKey(newKey);
        Main.myKey = newKey;
    }

    public void eventAddressClicked(Event event) {
        Object src = event.getSource();
        ListView<Address> addressListView = (ListView<Address>) src;
        Address addr = addressListView.getSelectionModel().getSelectedItem();
        if (addr != null) {
            // User clicked icon or menu item.
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(addr.toString());
//        content.putHtml(String.format("<a href='%s'>%s</a>", uri(), addressStr.get()));
            clipboard.setContent(content);

            Main.myKey = Main.bitcoin.wallet().findKeyFromPubHash(addr.getHash160());
            MyUtils.saveKeyToFile(Main.myKey, Main.APP_NAME);
        }
    }

    public void completeSignContract(ActionEvent actionEvent) {
        Main.instance.overlayUI("complete_sign.fxml");
    }

    public void saveWallet() {

        try {
            Main.bitcoin.wallet().saveToFile(new File(Main.SHAREFOLDER_LOCATION + File.separator + Main.APP_NAME + ".wal"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void createMultisignTxn(ActionEvent actionEvent) {
        Main.instance.overlayUI("createMultiSigTxn.fxml");
    }
}
