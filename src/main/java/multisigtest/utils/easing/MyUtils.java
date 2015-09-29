package multisigtest.utils.easing;

import multisigtest.Main;
import multisigtest.utils.GuiUtils;
import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Created by dlcl on 9/18/2015.
 */
public class MyUtils {
    public static void saveKeyToFile(ECKey key, String name) {
        System.out.println(key);
        byte[] pubkey = key.getPubKey();
        byte[] prikey = key.getPrivKeyBytes();

        try {
            OutputStream os = new FileOutputStream(new File(Main.SHAREFOLDER_LOCATION + File.separator + name + "-pubkey.dat"));
            os.write(pubkey);
            os.flush();
            os.close();
            os = new FileOutputStream(new File(Main.WALLET_LOCATION + File.separator + name + "-prikey.dat"));
            os.write(prikey);
            os.flush();
            os.close();
        } catch (FileNotFoundException e) {
            GuiUtils.alertException(e);
        } catch (IOException e) {
            GuiUtils.alertException(e);
        }
    }

    public static ECKey loadKeyFromFile(String name) {
        System.out.println("loading keys from " + name);
        byte[] buffer = new byte[64];
        byte[] pubkey = null;
        byte[] prikey = null;
        ECKey key = null;

        try {
            String filename = Main.SHAREFOLDER_LOCATION + File.separator + name + "-pubkey.dat";
            Path pubPath = Paths.get(filename);
            if (Files.exists(pubPath)) {
                InputStream is = new FileInputStream(new File(filename));
                int count = is.read(buffer);
                pubkey = Arrays.copyOf(buffer, count);
            }

            filename = Main.WALLET_LOCATION + File.separator + name + "-prikey.dat";
            Path priPath = Paths.get(filename);
            if (Files.exists(priPath)) {
                InputStream is = new FileInputStream(new File(filename));
                int count = is.read(buffer);
                prikey = Arrays.copyOf(buffer, count);
            }

            if (prikey != null && pubkey != null) {
                key = new ECKey(prikey, pubkey);
            } else if (prikey == null && pubkey != null) {
                key = new ECKey(null, pubkey);
            } else if (prikey != null && pubkey == null) {
                key = new ECKey(prikey, null);
            } else {
                key = null;
            }

        } catch (FileNotFoundException e) {
            GuiUtils.alertException(e);
        } catch (IOException e) {
            GuiUtils.alertException(e);
        }
        System.out.println(key == null ? name + " Key not found" : key);
        return key;
    }

    public static void sendToClientApp(ECKey.ECDSASignature signature, String signatureFilename) {
        // save to a folder
        byte[] der = signature.encodeToDER();

        File signatureFile = new File(signatureFilename);
        try {
            OutputStream os = new FileOutputStream(signatureFile);
            os.write(der);
            os.flush();
            os.close();
        } catch (FileNotFoundException e) {
            GuiUtils.alertException(e);
        } catch (IOException e) {
            GuiUtils.alertException(e);
        }
    }

    public static ECKey.ECDSASignature receiveFromServerApp(String signatureFilename) {
        ECKey.ECDSASignature signature = null;
        byte[] buffer = new byte[256];

        try {
            InputStream is = new FileInputStream(new File(signatureFilename.replace('\\', File.separatorChar)));
            int count = is.read(buffer);
            byte[] der = Arrays.copyOf(buffer, count);
            signature = ECKey.ECDSASignature.decodeFromDER(der);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return signature;
    }

    public static Transaction readTransaction(String txnFilename) {
        File contractFile = new File(txnFilename.replace('\\', File.separatorChar));
        Transaction contract = null;
        try {
            InputStream is = new FileInputStream(contractFile);
            BitcoinSerializer serializer = new BitcoinSerializer(Main.params, false);
            byte[] buffer = new byte[512];
            try {
                int count = is.read(buffer);
                ByteBuffer byteBuffer = ByteBuffer.allocate(512);
                byteBuffer.put(buffer, 0, count);
                contract = serializer.makeTransaction(byteBuffer.array());
                System.out.println("Contract read: " + contract);
            } catch (IOException e) {
                GuiUtils.alertException(e);
            }
        } catch (FileNotFoundException e) {
            GuiUtils.alertException(e);
        }
        return contract;
    }

    public static void saveTransaction(Transaction txn, String txnFilename) {
        // Serialized the contract
        File txnFile = new File(Main.SHAREFOLDER_LOCATION + File.separator + txnFilename);
        try {
            OutputStream os = new FileOutputStream(txnFile);
            txn.bitcoinSerialize(os); // for demo purpose in a single laptop
            os.flush();
            os.close();
        } catch (IOException ex) {
            GuiUtils.alertException(ex);
        }
    }
}

