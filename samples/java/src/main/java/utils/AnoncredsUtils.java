package utils;

import org.hyperledger.indy.sdk.IndyJava;
import java.util.concurrent.CompletableFuture;

import org.hyperledger.indy.sdk.IndyException;
import org.hyperledger.indy.sdk.IndyJava;
import org.hyperledger.indy.sdk.LibIndy;
import org.hyperledger.indy.sdk.ParamGuard;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults.IssuerCreateSchemaResult;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults.IssuerCreateAndStoreCredentialDefResult;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults.IssuerCreateAndStoreRevocRegResult;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults.IssuerCreateCredentialResult;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults.ProverCreateCredentialRequestResult;
import org.hyperledger.indy.sdk.blob_storage.BlobStorageWriter;
import org.hyperledger.indy.sdk.wallet.Wallet;

import com.sun.jna.Callback;

public class AnoncredsUtils extends IndyJava.API {
    //	/**
//	 * Recover a credential identified by a cred_revoc_id (returned by indy_issuer_create_credential).
//	 * <p>
//	 * The corresponding credential definition and revocation registry must be already
//	 * created an stored into the wallet.
//	 * <p>
//	 * This call returns revoc registry delta as json file intended to be shared as REVOC_REG_ENTRY transaction.
//	 * Note that it is possible to accumulate deltas to reduce ledger load.
//	 *
//	 * @param wallet                  A wallet.
//	 * @param blobStorageReaderHandle Pre-configured blob storage reader instance handle that will allow to read revocation tails
//	 * @param revRegId                Id of revocation registry stored in wallet.
//	 * @param credRevocId             Local id for revocation info
//	 * @return A future resolving to a revocation registry update json with a recovered credential
//	 * @throws IndyException Thrown if an error occurs when calling the underlying SDK.
//	 */
    public static CompletableFuture<String> issuerRecoverCredential(
            Wallet wallet,
            int blobStorageReaderHandle,
            String revRegId,
            String credRevocId) throws IndyException {

        ParamGuard.notNull(wallet, "wallet");
        ParamGuard.notNull(revRegId, "revRegId");
        ParamGuard.notNull(credRevocId, "credRevocId");

        CompletableFuture<String> future = new CompletableFuture<String>();
        int commandHandle = addFuture(future);

        int walletHandle = wallet.getWalletHandle();

        int result = LibIndyUtils.api.indy_issuer_recover_credential(
                commandHandle,
                walletHandle,
                blobStorageReaderHandle,
                revRegId,
                credRevocId,
                stringCb);

        checkResult(future, result);

        return future;
    }

    /**
     * Callback used when function returning string completes.
     */
    static Callback stringCb = new Callback() {

        @SuppressWarnings({"unused", "unchecked"})
        public void callback(int xcommand_handle, int err, String str) {

            CompletableFuture<String> future = (CompletableFuture<String>) removeFuture(xcommand_handle);
            if (!checkResult(future, err)) return;

            String result = str;
            future.complete(result);
        }
    };
}

