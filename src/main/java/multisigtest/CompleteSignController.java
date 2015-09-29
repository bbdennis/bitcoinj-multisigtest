package multisigtest;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import multisigtest.utils.GuiUtils;
import multisigtest.utils.easing.MyUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Created by dlcl on 9/19/2015.
 */
public class CompleteSignController {
    public Main.OverlayUI overlayUI;

    public Button completeSignBtn;
    public TextField contractFile;
    public TextField signatureFile;
    public Label titleLabel;

    public void cancel(ActionEvent actionEvent)  {
        overlayUI.done();
    }

    public void completeSign(ActionEvent actionEvent) {
        if (Main.myKey == null) {
            Main.myKey = MyUtils.loadKeyFromFile(Main.APP_NAME);
        }
        ECKey clientKey = Main.myKey;

        Transaction contract = MyUtils.readTransaction(this.contractFile.getText());
        GuiUtils.alertInfo("Contract loaded: " + contract);

        TransactionOutput multisigOutput = null;
        Script multisigScript = null;
        for(Transaction tx : Main.bitcoin.wallet().getTransactions(true)) {
            if (tx.getHashAsString().equals("88ad08a27e72098ddf9192d27dd475bd49fe265755f81b85c9179598ebf02dcc")) {
                for (TransactionOutput txo : tx.getOutputs()) {
                    if(txo.getScriptPubKey().isSentToMultiSig()){
                        System.out.println(txo.getScriptPubKey());
                        contract = tx;
                        multisigOutput = txo;
                        multisigScript = txo.getScriptPubKey();
                        MyUtils.saveTransaction(tx, "contract.dat");
                        break;
                    }
                }
                if (contract!=null) {
                    break;
                }
            }

        }
        completeSignContract(contract, clientKey);
        GuiUtils.alertInfo("Contract signed and published to the network");

    }
    public void completeSignContract(Transaction contract, ECKey clientKey) {
        try {
            TransactionOutput multisigOutput = contract.getOutput(0);
            Script multisigScript = multisigOutput.getScriptPubKey();

            Transaction spendTx = MyUtils.readTransaction(Main.SHAREFOLDER_LOCATION + File.separator + "spendTx.dat");
            ECKey.ECDSASignature serverSignature = MyUtils.receiveFromServerApp(this.signatureFile.getText());

            Sha256Hash sighash = spendTx.hashForSignature(0, multisigScript, Transaction.SigHash.ALL, false);
            ECKey.ECDSASignature mySignature = clientKey.sign(sighash);

            // Create the script that spends the multi-sig output.
            TransactionSignature myTxnSign = new TransactionSignature(mySignature, Transaction.SigHash.ALL, false);
            TransactionSignature serverTxnSign = new TransactionSignature(serverSignature, Transaction.SigHash.ALL, false);
//            List<TransactionSignature> txnSignatures = new ArrayList<>();
//            txnSignatures.add(myTxnSign);
//            txnSignatures.add(serverTxnSign);
            Script inputScript = ScriptBuilder.createMultiSigInputScript(myTxnSign, serverTxnSign);
            // Add it to the input.
            spendTx.getInput(0).setScriptSig(inputScript);

            // We can now check the server provided signature is correct, of course...
            spendTx.getInput(0).verify(multisigOutput);  // Throws an exception if the script doesn't run.
            spendTx.setPurpose(Transaction.Purpose.ASSURANCE_CONTRACT_CLAIM);
            //
//            TransactionBroadcast transactionBroadcast = Main.bitcoin.peerGroup().broadcastTransaction(spendTx);
//            Transaction tx = transactionBroadcast.future().get(5, TimeUnit.MINUTES);
            //
            Wallet.SendResult sendResult = new Wallet.SendResult();
            sendResult.tx = spendTx;
            sendResult.broadcast = Main.bitcoin.peerGroup().broadcastTransaction(spendTx);
            sendResult.broadcastComplete = sendResult.broadcast.future();
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

            System.out.println("Contract boardcasted: " + spendTx);
        } catch (Exception e) {
            GuiUtils.alertException(e);
            return;
        }

    }

    public void completeSignContractBK(Transaction contract, ECKey clientKey) {
//        if (Main.myKey == null) {
//            Main.myKey = MyUtils.loadKeyFromFile(Main.APP_NAME);
//        }
//        ECKey clientKey = Main.myKey;
//
//        Transaction contract = MyUtils.readTransaction(this.contractFile.getText());
        TransactionOutput multisigOutput = contract.getOutput(0);
        Script multisigScript = multisigOutput.getScriptPubKey();

        ECKey.ECDSASignature serverSignature = MyUtils.receiveFromServerApp(this.signatureFile.getText());

        // Client side code.
        Coin value = multisigOutput.getValue();
        Transaction spendTx = new Transaction(Main.params);
        spendTx.addOutput(value.subtract(Coin.valueOf(0, 5)), clientKey);
        spendTx.getConfidence().setDepthInBlocks(6);
        spendTx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);

        TransactionInput input = spendTx.addInput(multisigOutput);

        // set the lock time to make it a standard txn
//        input.setSequenceNumber(0);
//        spendTx.setLockTime(60*1000);
        int height = Main.bitcoin.wallet().getLastBlockSeenHeight();
        spendTx.setLockTime(height + 3);

        Sha256Hash sighash = spendTx.hashForSignature(0, multisigScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature mySignature = clientKey.sign(sighash);

        // Create the script that spends the multi-sig output.
        TransactionSignature myTxnSign = new TransactionSignature(mySignature, Transaction.SigHash.ALL, false);
        TransactionSignature serverTxnSign = new TransactionSignature(serverSignature, Transaction.SigHash.ALL, false);
        List<TransactionSignature> txnSignatures = new ArrayList<>();
        txnSignatures.add(myTxnSign);
        txnSignatures.add(serverTxnSign);
        Script inputScript = ScriptBuilder.createMultiSigInputScript(txnSignatures);
        // Add it to the input.
        input.setScriptSig(inputScript);

        // We can now check the server provided signature is correct, of course...
        input.verify(multisigOutput);  // Throws an exception if the script doesn't run.

        // It's valid! Let's take back the money.
//        Wallet.SendRequest req = Wallet.SendRequest.forTx(spendTx);
//
//        try {
//            Main.bitcoin.wallet().completeTx(req);   // Could throw InsufficientMoneyException
//        } catch (InsufficientMoneyException e) {
//            GuiUtils.alertException(e);
//        }

//        try {
//            TransactionBroadcast transactionBroadcast = Main.bitcoin.peerGroup().broadcastTransaction(spendTx);
//            Transaction tx = transactionBroadcast.future().get();
//            System.out.println("Contract boardcasted: " + tx);
//
//        } catch (InterruptedException | ExecutionException e) {
//            GuiUtils.alertException(e);
//        }
        //
        Wallet.SendResult sendResult = new Wallet.SendResult();
        sendResult.tx = spendTx;
        sendResult.broadcast = Main.bitcoin.peerGroup().broadcastTransaction(spendTx);
        sendResult.broadcastComplete = sendResult.broadcast.future();
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

        System.out.println("Contract boardcasted: " + spendTx);

        // Wallet now has the money back in it.
    }

    private void updateTitleForBroadcast(Wallet.SendResult sendResult) {
        final int peers = sendResult.tx.getConfidence().numBroadcastPeers();
        titleLabel.setText(String.format("Broadcasting ... seen by %d peers", peers));
    }

}
