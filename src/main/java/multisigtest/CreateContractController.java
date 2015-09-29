package multisigtest;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javafx.event.ActionEvent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import multisigtest.utils.GuiUtils;
import multisigtest.utils.easing.MyUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Created by dennis on 28/9/15.
 */
public class CreateContractController {
    public Main.OverlayUI overlayUI;
    public TextField coinAmount;
    public Label titleLabel;

    public void closeAction(ActionEvent actionEvent) {
        overlayUI.done();
    }

    public Transaction getInputTransaction(Coin amount) {

        Set<Transaction> txns = Main.bitcoin.wallet().getTransactions(false);
        for(Transaction txn : txns) {
            for (TransactionOutput o: txn.getOutputs()) {
                ECKey k = Main.bitcoin.wallet().findKeyFromPubHash(o.getScriptPubKey().getPubKeyHash());
                String myImportedAddress = Main.bitcoin.wallet().getImportedKeys().get(0).toAddress(Main.params).toString();
                String txnAddress = k.toAddress(Main.params).toString();
                if (k != null && txnAddress.equals(myImportedAddress)) {
                    Coin coin = txn.getOutput(0).getValue();
                    if (coin.isPositive() && coin.isGreaterThan(amount)) {
                        System.out.println("Spendable Txn: \n"+ txn);
                        return txn;
                    }
                }
            }
        }
        System.out.println("Do not have sufficient coin.");
        return null;
    }


    public void genMultisigContract(ActionEvent actionEvent) {
//        GuiUtils.alertInfo("Button Clicked");
        String ca = coinAmount.getText();
        float coinValue = 0.0f;
        try {
            coinValue = Float.parseFloat(ca);
        }catch (NumberFormatException e) {
            GuiUtils.alertInfo("Invalid coin value");
            return;
        }
        int coins = (int) (coinValue / 1);
        int cents = (int) (coinValue % 1 * 100);

        // Create a random key.
//        ECKey clientKey = new ECKey();
        if (Main.myKey == null) {
            Main.myKey = MyUtils.loadKeyFromFile(Main.APP_NAME);
        }
        ECKey clientKey = Main.myKey;
        // We get the other parties public key from somewhere ...
//        ECKey serverKey = new ECKey(null, publicKeyBytes);
        ECKey serverKey = MyUtils.loadKeyFromFile("ServerWallet"); // this should match the server's name
//        ECKey serverKey = new ECKey(null, clientKey.getPubKey());

        // Prepare a template for the contract.
        Transaction contract = new Transaction(Main.params);
        List<ECKey> keys = ImmutableList.of(clientKey, serverKey);
        // Create a 2-of-2 multisig output script.
        Script script = ScriptBuilder.createMultiSigOutputScript(2, keys);
        // Now add an output for 0.50 bitcoins that uses that script.
        Coin amount = Coin.valueOf(coins, cents);
        contract.addOutput(amount, script);

        // hmm. need to find a spendable txn
        Transaction inputTxn = getInputTransaction(amount);
        if (inputTxn == null) {
            return;  // do nothing
        }

        TransactionOutput srcOutput = inputTxn.getOutput(0);
        Script inScript = srcOutput.getScriptPubKey();

        TransactionInput input = contract.addInput(srcOutput);
        Sha256Hash sighash = contract.hashForSignature(0, inScript, Transaction.SigHash.ALL, false);
        ECKey key = Main.bitcoin.wallet().findKeyFromPubHash(inScript.getPubKeyHash());
        Address originalAddr = key.toAddress(Main.params);
        System.out.println(originalAddr);
        ECKey.ECDSASignature mySignature = key.sign(sighash);

        TransactionSignature myTxnSign = new TransactionSignature(mySignature, Transaction.SigHash.ALL, false);
        Script inputScript = ScriptBuilder.createInputScript(myTxnSign, key);
        // Add it to the input.
        input.setScriptSig(inputScript);

        // We can now check the server provided signature is correct, of course...
        input.verify(srcOutput);  // Throws an exception if the script doesn't run.

        // We have said we want to make 0.5 coins controlled by us and them.
        // But it's not a valid tx yet because there are no inputs.
        Wallet.SendRequest req = Wallet.SendRequest.forTx(contract);

        try {
            System.out.println("Txn before completeTx: " + contract);
            Main.bitcoin.wallet().completeTx(req);   // Could throw InsufficientMoneyException
            System.out.println("Txn after completeTx: " + contract);
            // Serialized the contract
            MyUtils.saveTransaction(contract, "contract.dat");
        } catch (InsufficientMoneyException e) {
            GuiUtils.alertException(e);
        }
        // Broadcast and wait for it to propagate across the network.
        // It should take a few seconds unless something went wrong.

//        return; // exit before the coding is fine.
        // uncomment below when the above code is correct
//        TransactionBroadcast transactionBroadcast = Main.bitcoin.peerGroup().broadcastTransaction(req.tx);

        Wallet.SendResult sendResult = new Wallet.SendResult();
        sendResult.tx = req.tx;
        sendResult.broadcast = Main.bitcoin.peerGroup().broadcastTransaction(req.tx);
        sendResult.broadcastComplete = sendResult.broadcast.future();
//            Transaction tx = transactionBroadcast.future().get();
        Futures.addCallback(sendResult.broadcastComplete, new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(Transaction result) {
                GuiUtils.checkGuiThread();
                overlayUI.done();
            }

            @Override
            public void onFailure(Throwable t) {
                // We died trying to empty the wallet.
                GuiUtils.crashAlert(t);
            }
        });
        sendResult.tx.getConfidence().addEventListener((tx, reason) -> {
            if (reason == TransactionConfidence.Listener.ChangeReason.SEEN_PEERS)
                updateTitleForBroadcast(sendResult);
        });

        System.out.println("Contract boardcasted: " + req.tx);
    }

    private void updateTitleForBroadcast(Wallet.SendResult sendResult) {
        final int peers = sendResult.tx.getConfidence().numBroadcastPeers();
        titleLabel.setText(String.format("Broadcasting ... seen by %d peers", peers));
    }


}
