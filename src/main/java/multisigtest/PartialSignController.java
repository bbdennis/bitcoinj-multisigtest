package multisigtest;

import multisigtest.utils.GuiUtils;
import multisigtest.utils.easing.MyUtils;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.bitcoinj.core.*;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.KeyChain;

import java.io.*;
import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkState;

/**
 * Created by dlcl on 9/18/2015.
 */
public class PartialSignController {
    public Main.OverlayUI overlayUI;
    public TextField contractFile;
    public TextField address;
    public Button cancelBtn;
    public Button partialSignBtn;
    public TextField signatureFile;

    public void partialSign(ActionEvent actionEvent) {
        Transaction contract = MyUtils.readTransaction(this.contractFile.getText());
        System.out.println("Contract loaded: " + contract );

        ECKey clientKey = MyUtils.loadKeyFromFile("ClientWallet");
//        Address destination = Address.fromBase58(Main.params, address.getText());
//
//        NetworkParameters params = destination.getParameters();
//        byte[] data = destination.getHash160();
//        byte[] header = new byte[] {0x76,(byte) 0xa9,0x14};
//        byte[] trailer = new byte[] {(byte) 0x88, (byte) 0xac};
//        ByteBuffer bf = ByteBuffer.allocate(35);
//        bf.put(header).put(data).put(trailer);
//        ECKey clientKey = new ECKey(null, bf.array()); //client's pubkey
//        System.out.println(Utils.HEX.encode(clientKey.getPubKey()));
//        ECKey testKey = new ECKey();
//        System.out.println(Utils.HEX.encode(testKey.getPubKey()));
//        ECKey pk = Main.bitcoin.wallet().currentKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
//        ECKey pk2 = Main.bitcoin.wallet().currentKey(KeyChain.KeyPurpose.AUTHENTICATION);
//        System.out.println(Utils.HEX.encode(pk.getPubKey()));

        partialSignContract(contract, clientKey);
    }

    public void cancel(ActionEvent actionEvent) {
        overlayUI.done();
    }

    public void partialSignContract(Transaction contract, ECKey clientKey) {
        // Assume we get the multisig transaction we're trying to spend from
        // somewhere, like a network connection.
        if (Main.myKey == null) {
            Main.myKey = MyUtils.loadKeyFromFile(Main.APP_NAME);
        }
        ECKey serverKey = Main.myKey;
//        Transaction contract = ....;

        TransactionOutput multisigOutput = contract.getOutput(0);
        Script multisigScript = multisigOutput.getScriptPubKey();
        // Is the output what we expect?
        checkState(multisigScript.isSentToMultiSig());
        Coin value = multisigOutput.getValue();

        // OK, now build a transaction that spends the money back to the client.
        Transaction spendTx = new Transaction(Main.params);
        spendTx.addOutput(value.subtract(Coin.valueOf(0, 1)), clientKey);
//        spendTx.getConfidence().setDepthInBlocks(6);
//        spendTx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);

        TransactionInput input = spendTx.addInput(multisigOutput);

        // set the lock time to make it a standard txn
//        input.setSequenceNumber(0);
//        spendTx.setLockTime(60*1000);
        int height = Main.bitcoin.wallet().getLastBlockSeenHeight();
//        spendTx.setLockTime(height + 3);

        // It's of the right form. But the wallet can't sign it. So, we have to
        // do it ourselves.
        Sha256Hash sighash = spendTx.hashForSignature(0, multisigScript, Transaction.SigHash.ALL, false);
        ECKey.ECDSASignature signature = serverKey.sign(sighash);
        // We have calculated a valid signature, so send it back to the client:
        MyUtils.sendToClientApp(signature, this.signatureFile.getText().replace('\\', File.separatorChar));
        MyUtils.saveTransaction(spendTx, "spendTx.dat");
    }



}
