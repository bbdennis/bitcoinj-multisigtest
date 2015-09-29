package multisigtest.utils;

import multisigtest.Main;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.bitcoinj.core.listeners.AbstractWalletEventListener;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.core.*;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A class that exposes relevant bitcoin stuff as JavaFX bindable properties.
 */
public class BitcoinUIModel {
    private SimpleObjectProperty<Address> address = new SimpleObjectProperty<>();
    private SimpleObjectProperty<Coin> balance = new SimpleObjectProperty<>(Coin.ZERO);
    private SimpleDoubleProperty syncProgress = new SimpleDoubleProperty(-1);
    private ProgressBarUpdater syncProgressUpdater = new ProgressBarUpdater();

    private ObservableList<Transaction> transactions = FXCollections.observableArrayList();
    private ObservableList<Address> addresses = FXCollections.observableArrayList();

    public BitcoinUIModel() {
    }

    public BitcoinUIModel(Wallet wallet) {
        setWallet(wallet);
    }

    public void setWallet(Wallet wallet) {
        wallet.addEventListener(Platform::runLater,
                new AbstractWalletEventListener() {
                    @Override
                    public void onWalletChanged(Wallet wallet) {
                        super.onWalletChanged(wallet);
                        update(wallet);
                    }
                }
        );
        update(wallet);
        List<Address> addressList = wallet.getImportedKeys().stream().map(k -> k.toAddress(Main.params)).collect(Collectors.toList());
        addresses.addAll(addressList);

    }

    private void update(Wallet wallet) {
        balance.set(wallet.getBalance());
        address.set(wallet.currentReceiveAddress());
        transactions.setAll(wallet.getTransactionsByTime());
    }

    private class ProgressBarUpdater extends DownloadProgressTracker {
        @Override
        protected void progress(double pct, int blocksLeft, Date date) {
            super.progress(pct, blocksLeft, date);
            Platform.runLater(() -> syncProgress.set(pct / 100.0));
        }

        @Override
        protected void doneDownload() {
            super.doneDownload();
            Platform.runLater(() -> syncProgress.set(1.0));
        }
    }

    public DownloadProgressTracker getDownloadProgressTracker() { return syncProgressUpdater; }

    public ReadOnlyDoubleProperty syncProgressProperty() { return syncProgress; }

    public ReadOnlyObjectProperty<Address> addressProperty() {
        return address;
    }

    public ReadOnlyObjectProperty<Coin> balanceProperty() {
        return balance;
    }

    public ObservableList<Transaction> getTransactions() {
        return transactions;
    }

    public ObservableList<Address> getAddresses() { return addresses;  }
}
