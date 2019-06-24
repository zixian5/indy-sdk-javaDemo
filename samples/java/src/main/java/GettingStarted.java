import com.sun.jna.Native;

import org.apache.commons.lang3.ObjectUtils.Null;
import org.hyperledger.indy.sdk.anoncreds.Anoncreds;
import org.hyperledger.indy.sdk.anoncreds.AnoncredsResults;
import org.hyperledger.indy.sdk.anoncreds.CredentialsSearchForProofReq;
import org.hyperledger.indy.sdk.blob_storage.BlobStorageReader;
import org.hyperledger.indy.sdk.blob_storage.BlobStorageWriter;
import org.hyperledger.indy.sdk.crypto.Crypto;
import org.hyperledger.indy.sdk.crypto.CryptoResults;
import org.hyperledger.indy.sdk.crypto.CryptoResults.AuthDecryptResult;
import org.hyperledger.indy.sdk.did.Did;
import org.hyperledger.indy.sdk.did.DidResults;
import org.hyperledger.indy.sdk.ledger.Ledger;
import org.hyperledger.indy.sdk.ledger.LedgerResults;
import org.hyperledger.indy.sdk.ledger.LedgerResults.ParseResponseResult;
import org.hyperledger.indy.sdk.pool.Pool;
import org.hyperledger.indy.sdk.wallet.Wallet;
import org.json.JSONArray;
import org.json.JSONObject;

import utils.AnoncredsUtils;
import utils.EnvironmentUtils;
import utils.PoolUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static utils.PoolUtils.PROTOCOL_VERSION;

public class GettingStarted {

    public static final String library = "indystrgpostgres";
    public static final String storageType = "postgres_storage";
    public static final String entrypoint = "postgresstorage_init";
    public static final String config = "{\"url\":\"localhost:5432\"}";
    public static final String creds = "{\"account\":\"postgres\",\"password\":\"mysecretpassword\",\"admin_account\":\"postgres\",\"admin_password\":\"mysecretpassword\"}";

    public void loadPostgresStoragePlugin() {
        if (storageType != null) {
            if (library == null || entrypoint == null) {
                System.out.println("please check the parameters!");
            }
        }

        System.out.println("call the postgres library -> start");
        LibPostgres.API api = LibPostgres.api;
        api = Native.loadLibrary(library, LibPostgres.API.class);

        int result = api.postgresstorage_init();
        if (result != 0) {
            System.out.println("Error unable to load wallet storage: " + result);
            return;
        } else {
            System.out.println("Load wallet storage -> succeed");
        }

    }

    public void run() throws Exception {
        loadPostgresStoragePlugin();

        System.out.println("Getting started -> started");

        // Set protocol version 2 to work with Indy Node 1.4
        Pool.setProtocolVersion(PROTOCOL_VERSION).get();
        // Create and Open Pool
        String poolName = PoolUtils.createPoolLedgerConfig();
        Pool pool = Pool.openPoolLedger(poolName, "{}")
                        .get();

        System.out.println("==============================");
        System.out.println("=== Getting Trust Anchor credentials for Faber, Acme, Thrift and Government  ==");
        System.out.println("------------------------------");

        // Create and Open steward Wallet
        System.out.println("\"\\\"Sovrin Steward\\\" -> Create wallet\"");
        Map<String, Object> steward = new HashMap<>();
        steward.put("name", "Sovrin Steward");
        steward.put("walletConfig", "{\"id\":\"sovrin_steward_wallet\"}");
        steward.put("walletCredentials", "{\"key\":\"steward_wallet_key\"}");
        steward.put("seed", "000000000000000000000000Steward1");

        Wallet.createWallet(
                steward.get("walletConfig").toString(), 
                steward.get("walletCredentials").toString()
                )
                .get();
        Wallet stewardWallet = Wallet.openWallet(
                steward.get("walletConfig").toString(), 
                steward.get("walletCredentials").toString()
                )
                .get();
        steward.put("wallet", stewardWallet);
        steward.put("pool", pool);

        // Create and store in Wallet DID from seed
        System.out.println("\"Sovrin Steward\" -> Create and store in Wallet DID from seed");
        steward.put("didInfo", "{\"seed\":\"000000000000000000000000Steward1\"}");
        DidResults.CreateAndStoreMyDidResult stewardDidResult = Did.createAndStoreMyDid(
                        stewardWallet, 
                        steward.get("didInfo").toString())
                .get();
        String stewardDid = stewardDidResult.getDid();
        steward.put("did", stewardDid);
        String stewardKey = stewardDidResult.getVerkey();
        steward.put("key", stewardKey);

        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Government Onboarding  ==");
        System.out.println("------------------------------");

        Map<String, Object> government = new HashMap<>();
        government.put("name", "Government");
        government.put("walletConfig", "{\"id\":\"government_wallet\"}");
        government.put("walletCredentials", "{\"key\":\"government_wallet_key\"}");
        government.put("role", "TRUST_ANCHOR");

        Map<String, String> onBoardResult = onBoard(steward, government);
        steward.put("didForGovernment", onBoardResult.get("fromToDid"));
        steward.put("keyForGovernment", onBoardResult.get("fromToKey"));
        government.put("didForSteward", onBoardResult.get("toFromDid"));
        government.put("keyForSteward", onBoardResult.get("toFromKey"));
        government.put("pool", pool);

        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Government getting Verinym  ==");
        System.out.println("------------------------------");

        government.put("did",getVerinym(
                        steward, 
                        steward.get("didForGovernment").toString(),
                        steward.get("keyForGovernment").toString(), 
                        government,
                        government.get("didForSteward").toString(), 
                        government.get("keyForSteward").toString()
                        ));

        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Faber Onboarding  ==");
        System.out.println("------------------------------");

        Map<String, Object> faber = new HashMap<>();
        faber.put("name", "Faber");
        faber.put("walletConfig", "{\"id\":\"faber_wallet\"}");
        faber.put("walletCredentials", "{\"key\":\"faber_wallet_key\"}");
        faber.put("pool", pool);
        faber.put("role", "TRUST_ANCHOR");
        Map<String, String> didForFaberResult = onBoard(steward, faber);

        steward.put("didForFaber", didForFaberResult.get("fromToDid"));
        steward.put("keyForFaber", didForFaberResult.get("fromToKey"));
        faber.put("didForSteward", didForFaberResult.get("toFromDid"));
        faber.put("keyForSteward", didForFaberResult.get("toFromKey"));

        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Faber getting Verinym  ==");
        System.out.println("------------------------------");

        faber.put("did",getVerinym(
                  steward, 
                  steward.get("didForFaber").toString(), 
                  steward.get("keyForFaber").toString(), 
                  faber,
                  faber.get("didForSteward").toString(), 
                  faber.get("keyForSteward").toString())
                  );

        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Acme Onboarding  ==");
        System.out.println("------------------------------");

        Map<String, Object> acme = new HashMap<>();
        acme.put("name", "Acme");
        acme.put("walletConfig", "{\"id\":\"acme_wallet\"}");
        acme.put("walletCredentials", "{\"key\":\"acme_wallet_key\"}");
        acme.put("pool", pool);
        acme.put("role", "TRUST_ANCHOR");
        Map<String, String> didForAcmeResult = onBoard(steward, acme);
        steward.put("didForAcme", didForAcmeResult.get("fromToDid"));
        steward.put("keyForAcme", didForAcmeResult.get("fromToKey"));
        acme.put("didForSteward", didForAcmeResult.get("toFromDid"));
        acme.put("keyForSteward", didForAcmeResult.get("toFromKey"));

        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Acme getting Verinym  ==");
        System.out.println("------------------------------");

        acme.put("did", getVerinym(steward, steward.get("didForAcme").toString(), 
                steward.get("keyForAcme").toString(),
                acme, acme.get("didForSteward").toString(), acme.get("keyForSteward").toString()));

        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Thrift Onboarding  ==");
        System.out.println("------------------------------");

        Map<String, Object> thrift = new HashMap<>();
        thrift.put("name", "thrift");
        thrift.put("walletConfig", "{\"id\":\"thrift_wallet\"}");
        thrift.put("walletCredentials", "{\"key\":\"thrift_wallet_key\"}");
        thrift.put("pool", pool);
        thrift.put("role", "TRUST_ANCHOR");
        Map<String, String> didForThriftREsult = onBoard(steward, thrift);
        steward.put("didForThrift", didForThriftREsult.get("fromToDid"));
        steward.put("keyForThrift", didForThriftREsult.get("fromToKey"));
        thrift.put("didForSteward", didForThriftREsult.get("toFromDid"));
        thrift.put("keyForSteward", didForThriftREsult.get("toFromKey"));

        System.out.println("==============================");
        System.out.println("== Getting Trust Anchor credentials - Thrift getting Verinym  ==");
        System.out.println("------------------------------");

        thrift.put("did",getVerinym(
                   steward, 
                   steward.get("didForThrift").toString(), 
                   steward.get("keyForThrift").toString(),
                   thrift, 
                   thrift.get("didForSteward").toString(), 
                   thrift.get("keyForSteward").toString())
                   );

        System.out.println("==============================");
        System.out.println("=== Credential Schemas Setup ==");
        System.out.println("------------------------------");

        System.out.println("\"Government\" -> Create \"Job-Certificate\" Schema");

        JSONObject jobCertificate = new JSONObject();
        jobCertificate.put("name", "Job-Certificate");
        jobCertificate.put("version", "0.2");
        String jobCertificateAttributes = "[\"first_name\", \"last_name\", \"salary\", \"employee_status\", \"experience\"]";
        jobCertificate.put("attributes", jobCertificateAttributes);

        AnoncredsResults.IssuerCreateSchemaResult issuerCreateSchemaResult = Anoncreds.issuerCreateSchema(
                        government.get("did").toString(), 
                        jobCertificate.getString("name"),
                        jobCertificate.getString("version"), 
                        jobCertificateAttributes)
                .get();
        government.put("jobCertificateSchemaId", issuerCreateSchemaResult.getSchemaId());
        government.put("jobCertificateSchema", issuerCreateSchemaResult.getSchemaJson());
        String jobCertificateSchemaId = government.get("jobCertificateSchemaId").toString();

        System.out.println("\"Government\" -> Send \"Job-Certificate\" Schema to Ledger");
        sendSchema((Pool) government.get("pool"), (Wallet) government.get("wallet"), 
                government.get("did").toString(),
                government.get("jobCertificateSchema").toString());

        System.out.println("\"Government\" -> Create \"Transcript\" Schema");
        JSONObject transcript = new JSONObject();
        transcript.put("name", "Transcript");
        transcript.put("version", "1.2");
        String transcriptAttributes = "[\"first_name\", \"last_name\", \"degree\", \"status\", \"year\",\"average\",\"ssn\"]";
        transcript.put("attributes", transcriptAttributes);

        AnoncredsResults.IssuerCreateSchemaResult transcriptIssuerCreateSchemaResult = Anoncreds.issuerCreateSchema(
                        government.get("did").toString(), 
                        transcript.getString("name"),
                        transcript.getString("version"), 
                        transcriptAttributes)
                .get();
        government.put("transcriptSchemaId", transcriptIssuerCreateSchemaResult.getSchemaId());
        government.put("transcriptSchema", transcriptIssuerCreateSchemaResult.getSchemaJson());
        String transcriptSchemaId = transcriptIssuerCreateSchemaResult.getSchemaId();

        System.out.println("\"Government\" -> Send \"Transcript\" Schema to Ledger");
        sendSchema(
                (Pool) government.get("pool"), 
                (Wallet) government.get("wallet"), 
                government.get("did").toString(),
                government.get("transcriptSchema").toString()
                );

        Thread.sleep(3000);// sleep 3 seconds before getting schema

        System.out.println("==============================");
        System.out.println("=== Faber Credential Definition Setup ==");
        System.out.println("------------------------------");

        System.out.println("\"Faber\" -> Get \"Transcript\" Schema from Ledger");
        LedgerResults.ParseResponseResult parseResponseResult = getSchema(
                (Pool) faber.get("pool"),
                faber.get("did").toString(), 
                transcriptSchemaId);
        faber.put("transcriptSchemaId", parseResponseResult.getId());
        faber.put("transcriptSchema", parseResponseResult.getObjectJson());

        System.out.println("\"Faber\" -> Create and store in Wallet \"Faber Transcript\" Credential Definition");
        String transcriptCredDefTag = "TAG1";
        String transcriptCredDefType = "CL";
        String transcriptCredDefConfig = "{\"support_revocation\": false}";
        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult issuerCreateAndStoreCredentialDefResult = Anoncreds
                .issuerCreateAndStoreCredentialDef(
                        (Wallet) faber.get("wallet"), 
                        faber.get("did").toString(),
                        faber.get("transcriptSchema").toString(), 
                        transcriptCredDefTag, 
                        transcriptCredDefType, 
                        transcriptCredDefConfig)
                .get();
        faber.put("transcriptCredDefId", issuerCreateAndStoreCredentialDefResult.getCredDefId());
        faber.put("transcriptCredDef", issuerCreateAndStoreCredentialDefResult.getCredDefJson());

        System.out.println("\"Faber\" -> Send  \"Faber Transcript\" Credential Definition to Ledger");
        sendCredDef(
                (Pool) faber.get("pool"), 
                (Wallet) faber.get("wallet"), 
                faber.get("did").toString(),
                faber.get("transcriptCredDef").toString()
                );

        System.out.println("==============================");
        System.out.println("=== Acme Credential Definition Setup ==");
        System.out.println("------------------------------");
        LedgerResults.ParseResponseResult acmResponseResult = getSchema((Pool) acme.get("pool"),
                acme.get("did").toString(), jobCertificateSchemaId);
        acme.put("jobCertificateSchemaId", acmResponseResult.getId());
        acme.put("jobCertificateSchema", acmResponseResult.getObjectJson());

        System.out.println("\"Acme\" -> Create and store in Wallet \"Acme Job-Certificate\" Credential Definition");
        String jobCertificateCredDefTag = "TAG1";
        String jobCertificateCredDefType = "CL";
        String jobCertificateCredDefConfig = "{\"support_revocation\": true}";
        AnoncredsResults.IssuerCreateAndStoreCredentialDefResult acmeAndStoreCredentialDefResult = Anoncreds
                .issuerCreateAndStoreCredentialDef((Wallet) acme.get("wallet"), 
                        acme.get("did").toString(),
                        acme.get("jobCertificateSchema").toString(),
                        jobCertificateCredDefTag,
                        jobCertificateCredDefType, 
                        jobCertificateCredDefConfig)
                .get();
        acme.put("jobCertificateCredDefId", acmeAndStoreCredentialDefResult.getCredDefId());
        acme.put("jobCertificateCredDef", acmeAndStoreCredentialDefResult.getCredDefJson());

        System.out.println("\"Acme\" -> Send \"Acme Job-Certificate\" Credential Definition to Ledger");
        sendCredDef((Pool) acme.get("pool"), (Wallet) acme.get("wallet"), acme.get("did").toString(),
                acme.get("jobCertificateCredDef").toString());

        System.out.println("\"Acme\" -> Creates Revocation Registry");
        acme.put("tailsWriterConfig", "{\"base_dir\": \"/tmp/indy_acme_tails\", 	\"uri_pattern\": \"\" }");
        BlobStorageWriter writer = BlobStorageWriter.openWriter("default", acme.get("tailsWriterConfig").toString())
                                                .get();
        String revRegConfig = "{\"issuance_type\":\"ISSUANCE_ON_DEMAND\",\"max_cred_num\":5}";
        AnoncredsResults.IssuerCreateAndStoreRevocRegResult acmeCreateAndStoreRevocRegResult = Anoncreds
                .issuerCreateAndStoreRevocReg(
                        (Wallet) acme.get("wallet"), 
                        acme.get("did").toString(), "CL_ACCUM",
                        "TAG1",
                        acme.get("jobCertificateCredDefId").toString(), 
                        revRegConfig, 
                        writer)
                .get();
        acme.put("revocRegId", acmeCreateAndStoreRevocRegResult.getRevRegId());
        acme.put("revocRegDef", acmeCreateAndStoreRevocRegResult.getRevRegDefJson());
        acme.put("revocRegEntry", acmeCreateAndStoreRevocRegResult.getRevRegEntryJson());

        System.out.println("\"Acme\" -> Post Revocation Registry Definition to Ledger");
        acme.put("revocRegDefRequest",Ledger
                .buildRevocRegDefRequest(
                        acme.get("did").toString(), 
                        acme.get("revocRegDef").toString())
                .get()
                );
        Ledger.signAndSubmitRequest(
                (Pool) acme.get("pool"), 
                (Wallet) acme.get("wallet"), 
                acme.get("did").toString(),
                acme.get("revocRegDefRequest").toString()
                );

        System.out.println("\"Acme\" -> Post Revocation Registry Entry to Ledger");
        acme.put("revocRegEntryRequest", Ledger
                .buildRevocRegEntryRequest(
                        acme.get("did").toString(),
                        acme.get("revocRegId").toString(), 
                        "CL_ACCUM", acme.get("revocRegEntry").toString())
                .get());
        Ledger.signAndSubmitRequest((Pool) acme.get("pool"), (Wallet) acme.get("wallet"), acme.get("did").toString(),
                acme.get("revocRegEntryRequest").toString());

        System.out.println("==============================");
        System.out.println("=== Getting Transcript with Faber ==");
        System.out.println("==============================");
        System.out.println("== Getting Transcript with Faber - Onboarding ==");
        System.out.println("------------------------------");

        Map<String, Object> alice = new HashMap<>();
        alice.put("name", "alice");
        alice.put("walletConfig", "{\"id\":\"alice_wallet\"}");
        alice.put("walletCredentials", "{\"key\":\"alice_wallet_key\"}");
        alice.put("pool", pool);
        Map<String, String> faberForAlice = onBoard(faber, alice);
        faber.put("didForAlice", faberForAlice.get("fromToDid"));
        faber.put("keyForAlice", faberForAlice.get("fromToKey"));
        alice.put("didForFaber", faberForAlice.get("toFromDid"));
        alice.put("keyForFaber", faberForAlice.get("toFromKey"));
        faber.put("aliceConnectionResponse", faberForAlice.get("fromConnectionResponse"));

        System.out.println("==============================");
        System.out.println("== Getting Transcript with Faber - Getting Transcript Credential ==");
        System.out.println("------------------------------");

        System.out.println("\"Faber\" -> Create \"Transcript\" Credential Offer for Alice");
        faber.put("transcriptCredOffer", Anoncreds
                .issuerCreateCredentialOffer(
                        (Wallet) faber.get("wallet"), 
                        faber.get("transcriptCredDefId").toString())
                .get()
                );

        JSONObject aliceConnectionResponseJsonObject = new JSONObject(faber.get("aliceConnectionResponse").toString());
        System.out.println("\"Faber\" -> Get key for Alice did");
        System.out.println("alice did "+aliceConnectionResponseJsonObject.getString("did"));
        faber.put("aliceKeyForFaber", Did.
                keyForDid(
                        (Pool) faber.get("pool"), 
                        (Wallet) faber.get("wallet"),
                        aliceConnectionResponseJsonObject.getString("did"))
                .get()
                );

        System.out.println("\"Faber\" -> Authcrypt \"Transcript\" Credential Offer for Alice");
        faber.put("authcryptedTranscriptCredOffer",
                Crypto.authCrypt(
                        (Wallet) faber.get("wallet"), 
                        faber.get("keyForAlice").toString(),
                        faber.get("aliceKeyForFaber").toString(),
                        faber.get("transcriptCredOffer").toString().getBytes())
                .get());

        System.out.println("\"Faber\" -> Send authcrypted \"Transcript\" Credential Offer to Alice");
        alice.put("authcryptedTranscriptCredOffer", faber.get("authcryptedTranscriptCredOffer"));

        System.out.println("\"Alice\" -> Authdecrypted \"Transcript\" Credential Offer from Faber");
        Map<String, String> aliceDecryptedResult = authDecrypt((Wallet) alice.get("wallet"),
                alice.get("keyForFaber").toString(), (byte[]) alice.get("authcryptedTranscriptCredOffer"));
        alice.put("faberKeyForAlice", aliceDecryptedResult.get("fromVerkey"));
        alice.put("transcriptCredOffer", aliceDecryptedResult.get("decryptedMessage"));
        JSONObject aliceTranscriptCredOffer = new JSONObject(alice.get("transcriptCredOffer").toString());
        alice.put("transcriptSchemaId", aliceTranscriptCredOffer.getString("schema_id"));
        alice.put("transcriptCredDefId", aliceTranscriptCredOffer.getString("cred_def_id"));

        System.out.println("\"Alice\" -> Create and store \"Alice\" Master Secret in Wallet");
        alice.put("masterSecretId", Anoncreds.proverCreateMasterSecret((Wallet) alice.get("wallet"), null).get());

        System.out.println("\"Alice\" -> Get \"Faber Transcript\" Credential Definition from Ledger");

        LedgerResults.ParseResponseResult faberTranscriptCredDefResult = getCredDef(
                (Pool) alice.get("pool"), 
                alice.get("didForFaber").toString(), 
                alice.get("transcriptCredDefId").toString()
                );
        alice.put("faberTranscriptCredDefId", faberTranscriptCredDefResult.getId());
        alice.put("faberTranscriptCredDef", faberTranscriptCredDefResult.getObjectJson());

        System.out.println("\"Alice\" -> Create \"Transcript\" Credential Request for Faber");
        AnoncredsResults.ProverCreateCredentialRequestResult proverCreateCredentialRequestResult =Anoncreds.
                proverCreateCredentialReq(
                        (Wallet) alice.get("wallet"), 
                        alice.get("didForFaber").toString(), 
                        alice.get("transcriptCredOffer").toString(), 
                        alice.get("faberTranscriptCredDef").toString(), 
                        alice.get("masterSecretId").toString())
                .get();
        alice.put("transcriptCredRequest", proverCreateCredentialRequestResult.getCredentialRequestJson());
        alice.put("transcriptCredRequestMetadata", proverCreateCredentialRequestResult.getCredentialRequestMetadataJson());

        System.out.println("\"Alice\" -> Authcrypt \"Transcript\" Credential Request for Faber");
        alice.put("authcryptedTranscriptCredRequest", Crypto.authCrypt((Wallet) alice.get("wallet"), alice.get("keyForFaber").toString(), alice.get("faberKeyForAlice").toString(), alice.get("transcriptCredRequest").toString().getBytes()).get());

        System.out.println("\"Alice\" -> Send authcrypted \"Transcript\" Credential Request to Faber");
        faber.put("authcryptedTranscriptCredRequest", alice.get("authcryptedTranscriptCredRequest"));

        System.out.println("\"Faber\" -> Authdecrypt \"Transcript\" Credential Request from Alice");
        Map<String, String> faberForAliceDecryptResult = authDecrypt(
                (Wallet) faber.get("wallet"), 
                faber.get("keyForAlice").toString(), 
                (byte[])faber.get("authcryptedTranscriptCredRequest")
                );
        faber.put("aliceKeyForFaber", faberForAliceDecryptResult.get("fromVerkey"));
        faber.put("transcriptCredRequest", faberForAliceDecryptResult.get("decryptedMessage"));

        System.out.println("\"Faber\" -> Create \"Transcript\" Credential for Alice");
        String aliceTranscriptCredValues = "{\"first_name\": { 		\"raw\": \"Alice\", 		\"encoded\": \"1139481716457488690172217916278103335\" 	}, 	\"last_name\": { 		\"raw\": \"Garcia\", 		\"encoded\": \"5321642780241790123587902456789123452\" 	}, 	\"degree\": { 		\"raw\": \"Bachelor of Science, Marketing\", 		\"encoded\": \"12434523576212321\" 	}, 	\"status\": { 		\"raw\": \"graduated\", 		\"encoded\": \"2213454313412354\" 	}, 	\"ssn\": { 		\"raw\": \"123-45-6789\", 		\"encoded\": \"3124141231422543541\" 	}, 	\"year\": { 		\"raw\": \"2015\", 		\"encoded\": \"2015\" 	}, 	\"average\": { 		\"raw\": \"5\", 		\"encoded\": \"5\" 	} }";
        faber.put("aliceTranscriptCredValues", aliceTranscriptCredValues);
        AnoncredsResults.IssuerCreateCredentialResult transcriptCred =Anoncreds.
                issuerCreateCredential(
                        (Wallet) faber.get("wallet"), 
                        faber.get("transcriptCredOffer").toString(), 
                        faber.get("transcriptCredRequest").toString(), 
                        aliceTranscriptCredValues, 
                        null,
                        0)
                .get();
        faber.put("transcriptCred", transcriptCred.getCredentialJson());

        System.out.println("\"Faber\" -> Authcrypt \"Transcript\" Credential for Alice");
        faber.put("authcryptedTranscriptCred",
                Crypto.authCrypt(
                        (Wallet) faber.get("wallet"),
                        faber.get("keyForAlice").toString(), 
                        faber.get("aliceKeyForFaber").toString(), 
                        faber.get("transcriptCred").toString().getBytes())
                .get()
                );

        System.out.println("\"Faber\" -> Send authcrypted \"Transcript\" Credential to Alice");
        alice.put("authcryptedTranscriptCred", faber.get("authcryptedTranscriptCred"));

        System.out.println("\"Alice\" -> Authdecrypted \"Transcript\" Credential from Faber");
        Map<String ,String > resultMap = authDecrypt((Wallet)alice.get("wallet"), alice.get("keyForFaber").toString(), (byte[])alice.get("authcryptedTranscriptCred"));
        alice.put("transcriptCred", resultMap.get("decryptedMessage"));

        System.out.println("\"Alice\" -> Store \"Transcript\" Credential from Faber");
        LedgerResults.ParseResponseResult aliceTranscriptCredDef = getCredDef((Pool) alice.get("pool"), alice.get("didForFaber").toString(), alice.get("transcriptCredDefId").toString());
        alice.put("transcriptCredDef", aliceTranscriptCredDef.getObjectJson());
        Anoncreds.proverStoreCredential(
                (Wallet) alice.get("wallet"), 
                null, 
                alice.get("transcriptCredRequestMetadata").toString(), 
                alice.get("transcriptCred").toString(), 
                alice.get("transcriptCredDef").toString(), 
                null)
        .get();

        System.out.println("==============================");
        System.out.println("=== Apply for the job with Acme ==");
        System.out.println("==============================");
        System.out.println("== Apply for the job with Acme - Onboarding ==");
        System.out.println("------------------------------");
        Map<String, String> acmeForAliceOnboardResult = onBoard(acme, alice);
        acme.put("didForAlice", acmeForAliceOnboardResult.get("fromToDid"));
        acme.put("keyForAlice", acmeForAliceOnboardResult.get("fromToKey"));
        ;
        alice.put("didForAcme", acmeForAliceOnboardResult.get("toFromDid"));
        alice.put("keyForAcme", acmeForAliceOnboardResult.get("toFromKey"));
        acme.put("aliceConnectionResponse", acmeForAliceOnboardResult.get("fromConnectionResponse"));

        System.out.println("==============================");
        System.out.println("== Apply for the job with Acme - Transcript proving ==");
        System.out.println("------------------------------");

        System.out.println("\"Acme\" -> Create \"Job-Application\" Proof Request");
        String faberTranscriptCredDefId = faber.get("transcriptCredDefId").toString();
        String acmeJobApplicationProofRequest = "{\"nonce\": \"1432422343242122312411212\", 	\"name\": \"Job-Application\", 	\"version\": \"0.1\", 	\"requested_attributes\": { 		\"attr1_referent\": { 			\"name\": \"first_name\" 		}, 		\"attr2_referent\": { 			\"name\": \"last_name\" 		}, 		\"attr3_referent\": { 			\"name\": \"degree\", 			\"restrictions\": [{ 				\"cred_def_id\": \"" + faberTranscriptCredDefId + "\" 			}] 		}, 		\"attr4_referent\": { 			\"name\": \"status\", 			\"restrictions\": [{ 				\"cred_def_id\": \"" + faberTranscriptCredDefId + " \"			}] 		}, 		\"attr5_referent\": { 			\"name\": \"ssn\", 			\"restrictions\": [{ 				\"cred_def_id\": \"" + faberTranscriptCredDefId + " \"			}] 		}, 		\"attr6_referent\": { 			\"name\": \"phone_number\" 		} 	}, 	\"requested_predicates\": { 		\"predicate1_referent\": { 			\"name\": \"average\", 			\"p_type\": \">=\", 			\"p_value\": 4, 			\"restrictions\": [{ 				\"cred_def_id\": \"" + faberTranscriptCredDefId + "\"  			}] 		} 	} }";
        acme.put("jobApplicationProofRequest", acmeJobApplicationProofRequest);

        System.out.println("\"Acme\" -> Get key for Alice did");
        JSONObject aliceConnectionResponse = new JSONObject(acme.get("aliceConnectionResponse").toString());
        acme.put("aliceKeyForAcme", 
                Did.keyForDid(
                        (Pool) acme.get("pool"), 
                        (Wallet) acme.get("wallet"), 
                        aliceConnectionResponse.getString("did"))
                .get()
                );

        System.out.println("\"Acme\" -> Authcrypt \"Job-Application\" Proof Request for Alice");
        acme.put("authcryptedJobApplicationProofRequest",
                Crypto.authCrypt((Wallet) acme.get("wallet"), 
                acme.get("keyForAlice").toString(), 
                acme.get("aliceKeyForAcme").toString(), 
                acme.get("jobApplicationProofRequest").toString().getBytes())
                .get());

        System.out.println("\"Acme\" -> Send authcrypted \"Job-Application\" Proof Request to Alice");
        alice.put("authcryptedJobApplicationProofRequest", acme.get("authcryptedJobApplicationProofRequest"));

        System.out.println("\"Alice\" -> Authdecrypt \"Job-Application\" Proof Request from Acme");
        Map<String, String> aliceDecryptAcmeResult = authDecrypt(
                (Wallet) alice.get("wallet"), 
                alice.get("keyForAcme").toString(), 
                (byte[]) alice.get("authcryptedJobApplicationProofRequest")
                );
        alice.put("acmeKeyForAlice", aliceDecryptAcmeResult.get("fromVerkey"));
        alice.put("jobApplicationProofRequest", aliceDecryptAcmeResult.get("decryptedMessage"));

        System.out.println("\"Alice\" -> Get credentials for \"Job-Application\" Proof Request");

        System.out.println("jobRequest:"+alice.get("jobApplicationProofRequest").toString());
        System.out.println("acmeJobApplicationProofRequest: "+acmeJobApplicationProofRequest);
        CredentialsSearchForProofReq searchForJobApplicationProofRequest = CredentialsSearchForProofReq
                .open(
                        (Wallet) alice.get("wallet"), 
                        acmeJobApplicationProofRequest,
                        null)
                .get();
        String credForAttr1 = getCredentialForReferent(searchForJobApplicationProofRequest, "attr1_referent");
        String credForAttr2 = getCredentialForReferent(searchForJobApplicationProofRequest, "attr2_referent");
        String credForAttr3 = getCredentialForReferent(searchForJobApplicationProofRequest, "attr3_referent");
        String credForAttr4 = credForAttr1;
        String credForAttr5 = credForAttr1;
        
        String credForPredicate1 = getCredentialForReferent(searchForJobApplicationProofRequest, "predicate1_referent");
        searchForJobApplicationProofRequest.close();

        JSONObject credForAttr1Object = new JSONObject(credForAttr1);
        JSONObject credForAttr2Object = new JSONObject(credForAttr2);
        JSONObject credForAttr3Object = new JSONObject(credForAttr3);
        JSONObject credForAttr4Object = new JSONObject(credForAttr4);
        JSONObject credForAttr5Objcet = new JSONObject(credForAttr5);
        JSONObject credForPredicate1Object = new JSONObject(credForPredicate1);
        JSONObject credsForJobApplicationProofOJsonObject = new JSONObject();
        credsForJobApplicationProofOJsonObject.put(credForAttr1Object.getString("referent"), credForAttr1);
        credsForJobApplicationProofOJsonObject.put(credForAttr2Object.getString("referent"), credForAttr2);
        credsForJobApplicationProofOJsonObject.put(credForAttr3Object.getString("referent"), credForAttr3);
        credsForJobApplicationProofOJsonObject.put(credForAttr4Object.getString("referent"), credForAttr4);
        credsForJobApplicationProofOJsonObject.put(credForAttr5Objcet.getString("referent"), credForAttr5);
        credsForJobApplicationProofOJsonObject.put(credForPredicate1Object.getString("referent"), credForPredicate1);

        Map<String, String> proverEntitiesResponse = proverGetEntitiesFromLedger((Pool) alice.get("pool"), alice.get("didForAcme").toString(), credsForJobApplicationProofOJsonObject, alice.get("name").toString(), null, null);
        alice.put("schemasForJobApplication", proverEntitiesResponse.get("schemas"));
        alice.put("credDefsForJobApplication", proverEntitiesResponse.get("credDefs"));
        alice.put("revocStatesForJobApplication", proverEntitiesResponse.get("revStates"));

        System.out.println("\"Alice\" -> Create \"Job-Application\" Proof");
        String jobApplicationRequesteCreds = "{\"self_attested_attributes\": { 		\"attr1_referent\": \"Alice\", 		\"attr2_referent\": \"Garcia\", 		\"attr6_referent\": \"123-45-6789\" 	}, 	\"requested_attributes\": { 		\"attr3_referent\": { 			\"cred_id\": \"" + credForAttr3Object.getString("referent") + "\", 			\"revealed\": true 		}, 		\"attr4_referent\": { 			\"cred_id\": \"" + credForAttr4Object.getString("referent") + "\", 			\"revealed\": true 		}, 		\"attr5_referent\": { 			\"cred_id\": \"" + credForAttr5Objcet.getString("referent") + "\", 			\"revealed\": true 		} 	}, 	\"requested_predicates\": { 		\"predicate1_referent\": { 			\"cred_id\": \"" + credForPredicate1Object.getString("referent") + "\" 		} 	} }";
        alice.put("jobApplicationRequestedCreds", jobApplicationRequesteCreds);
        String schemasForJobApplication = new String(alice.get("schemasForJobApplication").toString());
        String credDefsForJobApplication = new String(alice.get("credDefsForJobApplication").toString());
        alice.put("jobApplicationProof", Anoncreds.
                proverCreateProof(
                        (Wallet) alice.get("wallet"), 
                        alice.get("jobApplicationProofRequest").toString(),
                        jobApplicationRequesteCreds, 
                        alice.get("masterSecretId").toString(),
                        schemasForJobApplication.replaceAll("\\\\", ""), 
                        credDefsForJobApplication.replaceAll("\\\\", ""), 
                        alice.get("revocStatesForJobApplication").toString())
                .get());

        System.out.println("\"Alice\" -> Authcrypt \"Job-Application\" Proof for Acme");
        alice.put("authcryptedJobApplicationProof", Crypto.
                authCrypt(
                        (Wallet) alice.get("wallet"), 
                        (String) alice.get("keyForAcme"),
                        alice.get("acmeKeyForAlice").toString(),
                        alice.get("jobApplicationProof").toString().getBytes())
                .get());

        System.out.println("\"Alice\" -> Send authcrypted \"Job-Application\" Proof to Acme");
        acme.put("authcryptedJobApplicationProof", alice.get("authcryptedJobApplicationProof"));

        System.out.println("\"Acme\" -> Authdecrypted \"Job-Application\" Proof from Alice");
        Map<String, String> jobDecryptResponse = authDecrypt(
                (Wallet) acme.get("wallet"), 
                acme.get("keyForAlice").toString(), 
                (byte[]) acme.get("authcryptedJobApplicationProof")
                );
        acme.put("jobApplicationProof", jobDecryptResponse.get("decryptedMessage"));
        String decryptedJobApplicationProof = jobDecryptResponse.get("decryptedMessage");
        System.out.println("decryptedJobApplicationProof"+decryptedJobApplicationProof);
        JSONObject decryptedJobApplicationProofOJsonObject = new JSONObject(decryptedJobApplicationProof);

        Map<String, String> verifierGetEntitiesFromLedgerResponse = verifierGetEntitiesFromLedger((Pool) acme.get("pool"), acme.get("did").toString(), decryptedJobApplicationProofOJsonObject.getJSONArray("identifiers"), acme.get("name").toString(), null);
        acme.put("schemasForJobApplication", verifierGetEntitiesFromLedgerResponse.get("schemas"));
        acme.put("credDefsForJobApplication", verifierGetEntitiesFromLedgerResponse.get("credDefs"));
        acme.put("revocRefDefsForJobApplication", verifierGetEntitiesFromLedgerResponse.get("revRegDefs"));
        acme.put("revocRegsForJobApplication", verifierGetEntitiesFromLedgerResponse.get("revRegs"));

        System.out.println("\"Acme\" -> Verify \"Job-Application\" Proof from Alice");
        assertEquals("Bachelor of Science, Marketing", decryptedJobApplicationProofOJsonObject.getJSONObject("requested_proof").getJSONObject("revealed_attrs").getJSONObject("attr3_referent").getString("raw"));
        assertEquals("graduated", decryptedJobApplicationProofOJsonObject.getJSONObject("requested_proof").getJSONObject("revealed_attrs").getJSONObject("attr4_referent").getString("raw"));
        assertEquals("123-45-6789", decryptedJobApplicationProofOJsonObject.getJSONObject("requested_proof").getJSONObject("revealed_attrs").getJSONObject("attr5_referent").getString("raw"));
        assertEquals("Alice", decryptedJobApplicationProofOJsonObject.getJSONObject("requested_proof").getJSONObject("self_attested_attrs").getString("attr1_referent"));
        assertEquals("Garcia", decryptedJobApplicationProofOJsonObject.getJSONObject("requested_proof").getJSONObject("self_attested_attrs").getString("attr2_referent"));
        assertEquals("123-45-6789", decryptedJobApplicationProofOJsonObject.getJSONObject("requested_proof").getJSONObject("self_attested_attrs").getString("attr6_referent"));
        assertTrue(Anoncreds.verifierVerifyProof(acme.get("jobApplicationProofRequest").toString(), acme.get("jobApplicationProof").toString(), acme.get("schemasForJobApplication").toString(), acme.get("credDefsForJobApplication").toString(), acme.get("revocRefDefsForJobApplication").toString(), acme.get("revocRegsForJobApplication").toString()).get());

        System.out.println("==============================");
        System.out.println("== Apply for the job with Acme - Getting Job-Certificate Credential ==");
        System.out.println("------------------------------");

        System.out.println("\"Acme\" -> Create \"Job-Certificate\" Credential Offer for Alice");
        acme.put("jobCertificateCredOffer", Anoncreds.
                issuerCreateCredentialOffer(
                        (Wallet) acme.get("wallet"),
                        (String) acme.get("jobCertificateCredDefId"))
                .get());
        
        System.out.println("\"Acme\" -> Get key for Alice did");
        acme.put("aliceKeyForAcme", Did.
                keyForDid(
                        (Pool)acme.get("pool"),
                        (Wallet) acme.get("wallet"),
                        new JSONObject(acme.get("aliceConnectionResponse").toString()).getString("did"))
                .get());

        System.out.println("\"Acme\" -> Authcrypt \"Job-Certificate\" Credential Offer for Alice");
        acme.put("authcryptedJobCertificateCredOffer", Crypto.
                authCrypt(
                        (Wallet)acme.get("wallet"),
                        acme.get("keyForAlice").toString(),
                        acme.get("aliceKeyForAcme").toString() , 
                        acme.get("jobCertificateCredOffer").toString().getBytes())
                .get());
       
        System.out.println("\"Acme\" -> Send authcrypted \"Job-Certificate\" Credential Offer to Alice");
        alice.put("authcryptedJobCertificateCredOffer", acme.get("authcryptedJobCertificateCredOffer"));

        System.out.println("\"Alice\" -> Authdecrypted \"Job-Certificate\" Credential Offer from Acme");
        Map<String , String> acmeDecryptAliceResponse = authDecrypt(
                (Wallet)alice.get("wallet"), 
                alice.get("keyForAcme").toString(), 
                (byte[])alice.get("authcryptedJobCertificateCredOffer")
                );
        alice.put("acmeKeyForAliceAlice", acmeDecryptAliceResponse.get("fromVerkey"));
        String jobCertificateCredOffer = acmeDecryptAliceResponse.get("decryptedMessage");
        JSONObject jobCertificateCredOfferObject = new JSONObject(jobCertificateCredOffer);
        alice.put("jobCertificateCredOffer", jobCertificateCredOffer);

        System.out.println("\"Alice\" -> Get \"Acme Job-Certificate\" Credential Definition from Ledger");
        LedgerResults.ParseResponseResult acmeJobCredDefResponse = getCredDef(
                (Pool)alice.get("pool"), 
                alice.get("didForAcme").toString(), 
                jobCertificateCredOfferObject.getString("cred_def_id")
                );
        alice.put("acmeJobCertificateCredDefId", acmeJobCredDefResponse.getId());
        alice.put("acmeJobCertificateCredDef", acmeJobCredDefResponse.getObjectJson());

        System.out.println("\"Alice\" -> Create and store in Wallet \"Job-Certificate\" Credential Request for Acme");
        AnoncredsResults.ProverCreateCredentialRequestResult proverCreateCredentialRequestResult2 = Anoncreds.
                proverCreateCredentialReq(
                        (Wallet)alice.get("wallet"), 
                        alice.get("didForAcme").toString(),
                        alice.get("jobCertificateCredOffer").toString(), 
                        alice.get("acmeJobCertificateCredDef").toString(), 
                        alice.get("masterSecretId").toString())
                .get();
        alice.put("jobCertificateCredRequest", proverCreateCredentialRequestResult2.getCredentialRequestJson());
        alice.put("jobCertificateCredRequestMetadata", proverCreateCredentialRequestResult2.getCredentialRequestMetadataJson());

        System.out.println("\"Alice\" -> Authcrypt \"Job-Certificate\"  Credential Request for Acme");
        alice.put("authcryptedJobCertificateCredRequest", Crypto.authCrypt((Wallet)alice.get("wallet"), alice.get("keyForAcme").toString(),alice.get("acmeKeyForAlice").toString(), alice.get("jobCertificateCredRequest").toString().getBytes()).get());

        System.out.println("\"Alice\" -> Send authcrypted \"Job-Certificate\" Credential Request to Acme");
        String jobCertificateCredValues = "{\"first_name\": { 		\"raw\": \"Alice\", 		\"encoded\": \"245712572474217942457235975012103335\" 	}, 	\"last_name\": { 		\"raw\": \"Garcia\", 		\"encoded\": \"312643218496194691632153761283356127\" 	}, 	\"employee_status\": { 		\"raw\": \"Permanent\", 		\"encoded\": \"2143135425425143112321314321\" 	}, 	\"salary\": { 		\"raw\": \"2400\", 		\"encoded\": \"2400\" 	}, 	\"experience\": { 		\"raw\": \"10\", 		\"encoded\": \"10\" 	} }";
        alice.put("jobCertificateCredValues",jobCertificateCredValues);
        acme.put("authcryptedJobCertificateCredRequest", alice.get("authcryptedJobCertificateCredRequest"));
        acme.put("jobCertificateCredValues", alice.get("jobCertificateCredValues"));

        System.out.println("\"Acme\" -> Authdecrypt \"Job-Certificate\" Credential Request from Alice");
        Map<String , String > aliceDecryptAcmeResponse = authDecrypt(
                (Wallet)acme.get("wallet"), 
                acme.get("keyForAlice").toString(),
                (byte[])acme.get("authcryptedJobCertificateCredRequest")
                );
        acme.put("aliceKeyForAcme", aliceDecryptAcmeResponse.get("fromVerkey"));
        acme.put("jobCertificateCredRequest", aliceDecryptAcmeResponse.get("decryptedMessage"));

        System.out.println("\"Acme\" -> Create \"Job-Certificate\" Credential for Alice");
        BlobStorageReader acmReader = BlobStorageReader.
                openReader("default", acme.get("tailsWriterConfig").toString())
                .get();
        System.out.println("jobCertificateCredOffer:"+acme.get("jobCertificateCredOffer").toString());
        System.out.println("jobCertificateCredRequest"+acme.get("jobCertificateCredRequest").toString());
        System.out.println("jobCertificateCredValues"+acme.get("jobCertificateCredValues").toString());
        System.out.println("revocRegId"+acme.get("revocRegId").toString());
        AnoncredsResults.IssuerCreateCredentialResult issuerCreateCredentialResult = Anoncreds.
                issuerCreateCredential(
                        (Wallet)acme.get("wallet"), 
                        acme.get("jobCertificateCredOffer").toString(),
                        acme.get("jobCertificateCredRequest").toString(),
                        new JSONObject( acme.get("jobCertificateCredValues").toString()).toString(),
                        acme.get("revocRegId").toString(), 
                        acmReader.getBlobStorageReaderHandle())
                .get();
        acme.put("jobCertificateCred", issuerCreateCredentialResult.getCredentialJson());
        acme.put("jobCertificateCredRevId", issuerCreateCredentialResult.getRevocId());
        acme.put("aliceCertRevRegDelta", issuerCreateCredentialResult.getRevocRegDeltaJson());

        System.out.println("\"Acme\" -> Post Revocation Registry Delta to Ledger");
        System.out.println("acme Did:"+acme.get("did").toString());
        System.out.println("revocRegId:"+acme.get("revocRegId").toString());
        System.out.println("aliceCertRevRegDelta"+issuerCreateCredentialResult.getRevocRegDeltaJson());
        acme.put("revocRegEntryReq", Ledger.
                buildRevocRegEntryRequest(
                        acme.get("did").toString() ,
                        acme.get("revocRegId").toString(), "CL_ACCUM", 
                        acme.get("aliceCertRevRegDelta").toString())
                .get());
        Ledger.signAndSubmitRequest(
                (Pool)acme.get("pool"), 
                (Wallet)acme.get("wallet"),
                acme.get("did").toString(), 
                acme.get("revocRegEntryReq").toString())
        .get();
        
        System.out.println("\"Acme\" -> Authcrypt \"Job-Certificate\" Credential for Alice");
        acme.put("authcryptedJobCertificateCred", Crypto.
                authCrypt( 
                        (Wallet)acme.get("wallet"),
                        acme.get("keyForAlice").toString(),
                        acme.get("aliceKeyForAcme").toString(),
                        acme.get("jobCertificateCred").toString().getBytes() )
                .get());
        
        System.out.println("\"Acme\" -> Send authcrypted \"Job-Certificate\" Credential to Alice");
        alice.put("authcryptedJobCertificateCred", acme.get("authcryptedJobCertificateCred"));

        System.out.println("\"Alice\" -> Authdecrypted \"Job-Certificate\" Credential from Acme");
        Map< String , String > aliceJobDecryptResponse =authDecrypt(
                (Wallet)alice.get("wallet"), 
                alice.get("keyForAcme").toString(), 
                (byte[])alice.get("authcryptedJobCertificateCred")
                );
        String aliceJobCertificateCred = aliceJobDecryptResponse.get("decryptedMessage");
        alice.put("jobCertificateCred", aliceJobCertificateCred);

        System.out.println("\"Alice\" -> Gets RevocationRegistryDefinition for \"Job-Certificate\" Credential from Acme");
        alice.put("acmeRevocRegDesReq", Ledger.
                buildGetRevocRegDefRequest(
                        alice.get("didForAcme").toString(),
                        new JSONObject(aliceJobCertificateCred).getString("rev_reg_id"))
                .get());
        alice.put("acmeRevocRegDesResp", Ledger.submitRequest((Pool)alice.get("pool"), alice.get("acmeRevocRegDesReq").toString()).get());
        LedgerResults.ParseResponseResult parseRegistryResponseResult =
                                Ledger.parseGetRevocRegDefResponse(alice.get("acmeRevocRegDesResp").toString()).get();
        alice.put("acmeRevocRegDefId", parseRegistryResponseResult.getId());
        alice.put("acmeRevocRegDefJson", parseRegistryResponseResult.getObjectJson());

        System.out.println("\"Alice\" -> Store \"Job-Certificate\" Credential");
        Anoncreds.
                proverStoreCredential(
                        (Wallet)alice.get("wallet"),
                        null,
                        alice.get("jobCertificateCredRequestMetadata").toString().replaceAll("\\\\", ""), 
                        alice.get("jobCertificateCred").toString().replaceAll("\\\\", ""),
                        alice.get("acmeJobCertificateCredDef").toString().replaceAll("\\\\", ""), 
                        alice.get("acmeRevocRegDefJson").toString().replaceAll("\\\\", ""))
                .get();

        System.out.println("==============================");
        System.out.println("=== Apply for the loan with Thrift ==");
        System.out.println("==============================");
        System.out.println("== Apply for the loan with Thrift - Onboarding ==");
        System.out.println("------------------------------");

        Map<String , String> thriftOnBoardingResponse = onBoard(thrift, alice);
        thrift.put("didForAlice", thriftOnBoardingResponse.get("fromToDid"));
        thrift.put("keyForAlice", thriftOnBoardingResponse.get("fromToKey"));
        alice.put("didForThrift", thriftOnBoardingResponse.get("toFromDid"));
        alice.put("keyForThrift", thriftOnBoardingResponse.get("toFromKey"));
        thrift.put("aliceConnectionResponse", thriftOnBoardingResponse.get("fromConnectionResponse"));

        
        applyLoanBasic(thrift, alice, acme);
        System.out.println("applyLoanProofRequest"+thrift.get("applyLoanProofRequest").toString());
        System.out.println("aliceApplyLoanProof"+thrift.get("aliceApplyLoanProof").toString());
        System.out.println("schemasForLoanApp"+thrift.get("schemasForLoanApp").toString());
        System.out.println("credDefsForLoanApp"+thrift.get("credDefsForLoanApp").toString());
        System.out.println("revocDefsForLoanApp"+thrift.get("revocDefsForLoanApp").toString());
        System.out.println("revocRegsForLoanApp"+thrift.get("revocRegsForLoanApp").toString());
        assertTrue(Anoncreds.verifierVerifyProof( thrift.get("applyLoanProofRequest").toString().replaceAll("\\\\", ""),
                thrift.get("aliceApplyLoanProof").toString().replaceAll("\\\\", ""), thrift.get("schemasForLoanApp").toString(), thrift.get("credDefsForLoanApp").toString().replaceAll("\\\\", "") , 
                thrift.get("revocDefsForLoanApp").toString().replaceAll("\\\\", ""), thrift.get("revocRegsForLoanApp").toString().replaceAll("\\\\", ""))
                .get());
        
        System.out.println("==============================");
        System.out.println("== Apply for the loan with Thrift - Transcript and Job-Certificate proving  ==");
        System.out.println("------------------------------");

        String applyLoanKycProofRequest = "{\"nonce\":\"123432421212\",\"name\":\"Loan-Application-KYC\",\"version\":\"0.1\",\"requested_attributes\":{\"attr1_referent\":{\"name\":\"first_name\"},\"attr2_referent\":{\"name\":\"last_name\"},\"attr3_referent\":{\"name\":\"ssn\"}},\"requested_predicates\":{}}";
        thrift.put("applyLoanKycProofRequest", applyLoanKycProofRequest);

        System.out.println("\"Thrift\" -> Get key for Alice did");
        thrift.put("aliceKeyForThrift", Did.
                keyForDid(
                        (Pool)thrift.get("pool"), 
                        (Wallet)thrift.get("wallet"),
                        new JSONObject(thrift.get("aliceConnectionResponse").toString()).getString("did"))
                .get());
        
        System.out.println("\"Thrift\" -> Authcrypt \"Loan-Application-KYC\" Proof Request for Alice");
        thrift.put("authcryptedApplyLoanKycProofRequest", Crypto.
                authCrypt(
                        (Wallet)thrift.get("wallet"), 
                        thrift.get("keyForAlice").toString(), 
                        thrift.get("aliceKeyForThrift").toString(), 
                        thrift.get("applyLoanKycProofRequest").toString().getBytes())
                .get());
        
        System.out.println("\"Thrift\" -> Send authcrypted \"Loan-Application-KYC\" Proof Request to Alice");
        alice.put("authcryptedApplyLoanKycProofRequest", thrift.get("authcryptedApplyLoanKycProofRequest"));

        System.out.println("\"Alice\" -> Authdecrypt \"Loan-Application-KYC\" Proof Request from Thrift");
        Map<String , String > aliceDecryptLoanResponse = authDecrypt(
                (Wallet)alice.get("wallet"), 
                alice.get("keyForThrift").toString(), 
                (byte[])alice.get("authcryptedApplyLoanKycProofRequest")
                );
        alice.put("thriftKeyForAlice", aliceDecryptLoanResponse.get("fromVerkey"));
        alice.put("applyLoanKycProofRequest", aliceDecryptLoanResponse.get("decryptedMessage"));

        System.out.println("\"Alice\" -> Get credentials for \"Loan-Application-KYC\" Proof Request");
        CredentialsSearchForProofReq searchForApplyLoanKycProofRequest = CredentialsSearchForProofReq.
                open(
                        ( Wallet)alice.get("wallet"), 
                        alice.get("applyLoanKycProofRequest").toString(), 
                        null)
                .get();
        
        credForAttr1 = getCredentialForReferent(searchForApplyLoanKycProofRequest, "attr1_referent");
        credForAttr2 = getCredentialForReferent(searchForApplyLoanKycProofRequest, "attr2_referent");
        credForAttr3 = getCredentialForReferent(searchForApplyLoanKycProofRequest, "attr3_referent");
        credForAttr1Object = new JSONObject(credForAttr1);
        credForAttr2Object = new JSONObject(credForAttr2);
        credForAttr3Object = new JSONObject(credForAttr3);
        searchForApplyLoanKycProofRequest.close();

        JSONObject credsForApplyLoanKycProofObject = new JSONObject();
        credsForApplyLoanKycProofObject.put(credForAttr1Object.getString("referent"), credForAttr1);
        credsForApplyLoanKycProofObject.put(credForAttr2Object.getString("referent"), credForAttr2);
        credsForApplyLoanKycProofObject.put(credForAttr3Object.getString("referent"), credForAttr3);
        alice.put("credsForApplyLoanKycProof", credsForApplyLoanKycProofObject.toString());

        Map<String , String> aliceGetEntitiesFromLedgerResponse = proverGetEntitiesFromLedger(
                (Pool)alice.get("pool"), 
                alice.get("didForThrift").toString(), 
                credsForApplyLoanKycProofObject, 
                alice.get("name").toString(), 
                null , 
                null
                );
        alice.put("schemasForLoanKycApp", aliceGetEntitiesFromLedgerResponse.get("schemas"));
        alice.put("credDefsForLoanKycApp", aliceGetEntitiesFromLedgerResponse.get("credDefs"));
        alice.put("revocStatesForLoanKycApp", aliceGetEntitiesFromLedgerResponse.get("revStates"));

        System.out.println("\"Alice\" -> Create \"Loan-Application-KYC\" Proof");
        JSONObject revocStatesForLoanAppObject = new JSONObject(alice.get("revocStatesForLoanKycApp").toString());
        Long timestampForAttr1 = getTimestampForAttribute(credForAttr1Object, alice.get("revocStatesForLoanKycApp").toString());
        Long timestampForAttr2 = getTimestampForAttribute(credForAttr2Object, alice.get("revocStatesForLoanKycApp").toString());
        Long timestampForAttr3 = getTimestampForAttribute(credForAttr3Object, alice.get("revocStatesForLoanKycApp").toString());

        String applyLoanKycRequestedCreds = "{\"self_attested_attributes\":{},\"requested_attributes\":{\"attr1_referent\":{\"cred_id\":\""+credForAttr1Object.getString("referent")+"\",\"revealed\":true,\"timestamp\":"+timestampForAttr1+"},\"attr2_referent\":{\"cred_id\": \""+credForAttr2Object.getString("referent")+"\",\"revealed\":true,\"timestamp\":"+timestampForAttr2+"},\"attr3_referent\":{\"cred_id\":\""+credForAttr3Object.getString("referent")+"\", 			\"revealed\": true, 			\"timestamp\": "+timestampForAttr3+" 		} 	}, 	\"requested_predicates\": {} }";
        alice.put("applyLoanKycRequestedCreds", applyLoanKycRequestedCreds);
        alice.put("applyLoanKycProof", Anoncreds.
                proverCreateProof(
                        (Wallet)alice.get("wallet"),
                        alice.get("applyLoanKycProofRequest").toString(), 
                        alice.get("applyLoanKycRequestedCreds").toString(), 
                        alice.get("masterSecretId").toString(), 
                        alice.get("schemasForLoanKycApp").toString(), 
                        alice.get("credDefsForLoanKycApp").toString(), 
                        alice.get("revocStatesForLoanKycApp").toString())
                .get());
        
        System.out.println("\"Alice\" -> Authcrypt \"Loan-Application-KYC\" Proof for Thrift");
        alice.put("authcryptedAliceApplyLoanKycProof", Crypto.
                authCrypt(
                        (Wallet)alice.get("wallet"), 
                        alice.get("keyForThrift").toString(),
                        alice.get("thriftKeyForAlice").toString(), 
                        alice.get("applyLoanKycProof").toString().getBytes())
                .get());
        
        System.out.println("\"Alice\" -> Send authcrypted \"Loan-Application-KYC\" Proof to Thrift");
        thrift.put("authcryptedAliceApplyLoanKycProof", alice.get("authcryptedAliceApplyLoanKycProof"));

        System.out.println("\"Thrift\" -> Authdecrypted \"Loan-Application-KYC\" Proof from Alice");
        Map<String , String> aliceDecryptLoanKycResponse = authDecrypt(
                (Wallet)thrift.get("wallet"), 
                thrift.get("keyForAlice").toString(), 
                (byte[])thrift.get("authcryptedAliceApplyLoanKycProof")
                );
        thrift.put("aliceApplyLoanKycProof",aliceDecryptLoanKycResponse.get("decryptedMessage"));
        String aliceApplyLoanKycProof=aliceDecryptLoanKycResponse.get("decryptedMessage");

        System.out.println("\"Thrift\" -> Get Schemas, Credential Definitions and Revocation Registries from Ledger"
                +" required for Proof verifying");
        Map<String , String > thriftGetEntitesFromLedgerResponse = verifierGetEntitiesFromLedger(
                (Pool)thrift.get("pool"), 
                thrift.get("did").toString(),
                new JSONObject(aliceApplyLoanKycProof).getJSONArray("identifiers"),
                thrift.get("name").toString() , 
                null
                );
        thrift.put("schemasForLoanKycApp", thriftGetEntitesFromLedgerResponse.get("schemas"));
        thrift.put("credDefsForLoanKycApp", thriftGetEntitesFromLedgerResponse.get("credDefs"));
        thrift.put("revocDefsForLoanKycApp", thriftGetEntitesFromLedgerResponse.get("revRegDefs"));
        thrift.put("revocRegsForLoanKycApp", thriftGetEntitesFromLedgerResponse.get("revRegs"));

        System.out.println("\"Thrift\" -> Verify \"Loan-Application-KYC\" Proof from Alice");
        assertEquals("Alice", new JSONObject(aliceApplyLoanKycProof).getJSONObject("requested_proof").getJSONObject("revealed_attrs").getJSONObject("attr1_referent").getString("raw"));
        assertEquals("Garcia", new JSONObject(aliceApplyLoanKycProof).getJSONObject("requested_proof").getJSONObject("revealed_attrs").getJSONObject("attr2_referent").getString("raw"));
        assertEquals("123-45-6789", new JSONObject(aliceApplyLoanKycProof).getJSONObject("requested_proof").getJSONObject("revealed_attrs").getJSONObject("attr3_referent").getString("raw"));

        assertTrue(Anoncreds.
                verifierVerifyProof(
                        thrift.get("applyLoanKycProofRequest").toString(), 
                        thrift.get("aliceApplyLoanKycProof").toString(),
                        thrift.get("schemasForLoanKycApp").toString(), 
                        thrift.get("credDefsForLoanKycApp").toString(),
                        thrift.get("revocDefsForLoanKycApp").toString(), 
                        thrift.get("revocRegsForLoanKycApp").toString())
                .get());
        
        System.out.println("==============================");
        System.out.println("==============================");
        System.out.println("== Credential revocation - Acme revokes Alice's Job-Certificate  ==");
        System.out.println("------------------------------");

        System.out.println("\"Acme\" - Revoke  credential");
        acme.put("aliceCertRevRegDelta0", Anoncreds.
                issuerRevokeCredential(
                        (Wallet)acme.get("wallet"), 
                        acmReader.getBlobStorageReaderHandle(), 
                        acme.get("revocRegId").toString(), 
                        acme.get("jobCertificateCredRevId").toString())
                .get());
        
        System.out.println("\"Acme\" - Post RevocationRegistryDelta to Ledger");
        acme.put("revocRegEntryReq0", Ledger.
                buildRevocRegEntryRequest(
                        acme.get("did").toString(), 
                        acme.get("revocRegId").toString(),
                        "CL_ACCUM", 
                        acme.get("aliceCertRevRegDelta0").toString())
                .get());

        System.out.println( "result is"+Ledger.signAndSubmitRequest(
                (Pool)acme.get("pool"), 
                (Wallet)acme.get("wallet"), 
                acme.get("did").toString(), 
                acme.get("revocRegEntryReq0").toString())
        .get());
        
        System.out.println("==============================");

        System.out.println("==============================");
        System.out.println("== Apply for the loan with Thrift again - Job-Certificate proving  ==");
        System.out.println("------------------------------");

        
        applyLoanBasic(thrift, alice, acme);

        assertFalse(Anoncreds.
                verifierVerifyProof(
                        thrift.get("applyLoanProofRequest").toString(), 
                        thrift.get("aliceApplyLoanProof").toString(), 
                        (String) thrift.get("schemasForLoanApp"), 
                        thrift.get("credDefsForLoanApp").toString(),
                        thrift.get("revocDefsForLoanApp").toString(),
                        thrift.get("revocRegsForLoanApp").toString())
                .get()
                );
        
        System.out.println("==============================");
        System.out.println("==============================");
        System.out.println("== Credential recovery - Acme recovers Alice's Job-Certificate  ==");
        System.out.println("------------------------------");
        
        System.out.println("\"Acme\" - Recover  credential");
        acme.put("aliceCertRevRegDelta", AnoncredsUtils.
        issuerRecoverCredential((Wallet)acme.get("wallet"),
            acmReader.getBlobStorageReaderHandle(),
            acme.get("revocRegId").toString(),
            acme.get("jobCertificateCredRevId").toString())
        .get());

        System.out.println("\"Acme\" - Post RecoveryRegistryDelta to Ledger");
        acme.put("revocRegEntryReq", Ledger.
        buildRevocRegEntryRequest(
                acme.get("did").toString(),
                acme.get("revocRegId").toString(),
                "CL_ACCUM",
                acme.get("aliceCertRevRegDelta").toString())
        .get());

        System.out.println( "result is"+Ledger.signAndSubmitRequest(
                (Pool)acme.get("pool"),
                (Wallet)acme.get("wallet"),
                acme.get("did").toString(),
                acme.get("revocRegEntryReq").toString())
        .get());
        System.out.println("==============================");

        System.out.println("==============================");
        System.out.println("== Apply for the loan with Thrift again - Job-Certificate proving  ==");
        System.out.println("------------------------------");

        applyLoanBasic(thrift, alice, acme);
        
        assertTrue(Anoncreds.
                verifierVerifyProof(
                        thrift.get("applyLoanProofRequest").toString(), 
                        thrift.get("aliceApplyLoanProof").toString(), 
                        (String) thrift.get("schemasForLoanApp"), 
                        thrift.get("credDefsForLoanApp").toString(),
                        thrift.get("revocDefsForLoanApp").toString(),
                        thrift.get("revocRegsForLoanApp").toString())
                .get()
                );

        System.out.println("==============================");
        System.out.println(" \"Sovrin Steward\" -> Close and Delete wallet");
        Wallet wallet = (Wallet)steward.get("wallet");
        wallet.close();;
        wallet.deleteWallet(
                steward.get("walletConfig").toString(), 
                steward.get("walletCredentials").toString())
        .get();

        System.out.println("\"Government\" -> Close and Delete wallet");
        wallet = (Wallet)government.get("wallet");
        wallet.close();
        wallet.deleteWallet(
                walletConfig(
                        "delete",
                        government.get("walletConfig").toString()), 
                walletCredentials(
                        "delete",
                        government.get("walletCredentials").toString()))
        .get();

        System.out.println("\"Faber\" -> Close and Delete wallet");
        wallet = (Wallet)faber.get("wallet");
        wallet.close();
        wallet.deleteWallet(
                walletConfig(
                        "delete",
                        faber.get("walletConfig").toString()), 
                walletCredentials(
                        "delete",
                        faber.get("walletCredentials").toString()))
        .get();
        
        System.out.println("\"Acme\" -> Close and Delete wallet");
        wallet = (Wallet)acme.get("wallet");
        wallet.close();
        wallet.deleteWallet(
                walletConfig(
                        "delete",
                        acme.get("walletConfig").toString()), 
                walletCredentials(
                        "delete",
                        acme.get("walletCredentials").toString()))
        .get();

        System.out.println("\"Thrift\" -> Close and Delete wallet");
        wallet = (Wallet)thrift.get("wallet");
        wallet.close();
        wallet.deleteWallet(
                walletConfig(
                        "delete",
                        thrift.get("walletConfig").toString()), 
                walletCredentials(
                        "delete",
                        thrift.get("walletCredentials").toString()))
        .get();
        
        System.out.println("\"Alice\" -> Close and Delete wallet");
        wallet = (Wallet)alice.get("wallet");
        wallet.close();
        wallet.deleteWallet(
                walletConfig(
                        "delete",
                        alice.get("walletConfig").toString()), 
                walletCredentials(
                        "delete",
                        alice.get("walletCredentials").toString()))
        .get();

        System.out.println("Close and Delete pool");
        pool.close();
        pool.deletePoolLedgerConfig(poolName);

        System.out.println("Getting started -> done");
       }


    public Map<String, String> onBoard(Map<String, Object> from, Map<String, Object> to) throws Exception {

        System.out.println(from.get("name").toString() + " -> Create and store in Wallet " + from.get("name").toString()
                + " " + to.get("name").toString() + " DID");
        DidResults.CreateAndStoreMyDidResult didResults = Did.
                createAndStoreMyDid(
                        (Wallet) from.get("wallet"), 
                        "{}")
                .get();
        String fromToDid = didResults.getDid();
        String fromToKey = didResults.getVerkey();

        System.out.println(from.get("name").toString() + "  Send Nym to Ledger for " + from.get("name").toString() + " "
                + to.get("name").toString() + " DID");
        sendNYM(
                (Pool) from.get("pool"), 
                (Wallet) from.get("wallet"), 
                from.get("did").toString(), 
                fromToDid, 
                fromToKey,
                null
                );

        System.out.println(from.get("name").toString() + " -> Send connection request to " + to.get("name").toString()
                + " with " + from.get("name").toString() + " " + to.get("name").toString());

        String connectionRequestDid = fromToDid;
        Long connectionRequestNonce = 123456789l;

        if (!to.containsKey("wallet")) {
                System.out.println(to.get("name").toString() + "  -> Create wallet");
                
                Wallet.createWallet(
                        walletConfig("create", 
                        to.get("walletConfig").toString()),
                        walletCredentials("create", 
                        to.get("walletCredentials").toString()))
                .get();
                to.put("wallet", Wallet.
                        openWallet(
                                walletConfig("create", 
                                to.get("walletConfig").toString()),
                                walletCredentials("create", 
                                to.get("walletCredentials").toString()))
                .get());
        }

        System.out.println(to.get("name").toString() + " -> Create and store in Wallet " + to.get("name").toString()
                + " " + from.get("name").toString() + " DID");
        DidResults.CreateAndStoreMyDidResult result = Did.createAndStoreMyDid(
                (Wallet) to.get("wallet"), 
                "{}")
        .get();
        String toFromDid = result.getDid();
        String toFromKey = result.getVerkey();

        System.out.println(to.get("name").toString() + "-> Get key for did from " + from.get("name").toString()
                + " connection request");
        String fromToVerkey = Did.keyForDid(
                (Pool) from.get("pool"), 
                (Wallet) to.get("wallet"), 
                connectionRequestDid)
        .get();

        System.out.println(to.get("name").toString() + " -> Anoncrypt connection response for "
                + from.get("name").toString() + "  with " + to.get("name").toString() + from.get("name").toString()
                + " DID, verkey and nonce");

        JSONObject connectionResponseObject = new JSONObject();
        connectionResponseObject.put("did", toFromDid);
        connectionResponseObject.put("verkey", toFromKey);
        connectionResponseObject.put("nonce", connectionRequestNonce);
        to.put("connectionResponse", connectionResponseObject.toString());
        byte[] anoncryptedConnectionResponse = Crypto.anonCrypt(
                fromToVerkey, 
                to.get("connectionResponse").toString().getBytes())
        .get();
        to.put("anoncrypted_connection_response", anoncryptedConnectionResponse);

        System.out.println( to.get("name").toString() + " -> Send anoncrypted connection response to " + to.get("name").toString());
        from.put("anoncrypted_connection_response", to.get("anoncrypted_connection_response"));

        System.out.println(from.get("name").toString() + " -> Anondecrypt connection response from" + to.get("name").toString());
        byte[] connectionResponse = Crypto.anonDecrypt(
                (Wallet) from.get("wallet"), 
                fromToKey,
                (byte[]) from.get("anoncrypted_connection_response"))
        .get();
        from.put("connectionResponse", connectionResponse);

        System.out.println(from.get("name").toString() + "  -> Authenticates" + to.get("name").toString()
                + " by comparision of Nonce");
        JSONObject responseObject = new JSONObject(new String((byte[]) from.get("connectionResponse")));
        assert (connectionRequestNonce == responseObject.getLong("nonce"));

        System.out.println(
                from.get("name").toString() + " -> Send Nym to Ledger for " + to.get("name").toString() + " DID");
        sendNYM(
                (Pool) from.get("pool"), 
                (Wallet) from.get("wallet"), 
                from.get("did").toString(), 
                toFromDid, 
                toFromKey,
                null
                );

        Map<String, String> response = new HashMap<>();
        response.put("fromToDid", fromToDid);
        response.put("fromToKey", fromToKey);
        response.put("toFromDid", toFromDid);
        response.put("toFromKey", toFromKey);
        response.put("fromConnectionResponse", responseObject.toString());
        return response;
    }

    public String getVerinym(Map<String, Object> from, String fromToDid, String fromToKey, Map<String, Object> to,
                             String toFromDid, String toFromKey) throws Exception {
        String toName = to.get("name").toString();
        String fromName = from.get("name").toString();

        System.out.println("fromToDid " + fromToDid);
        System.out.println("fromToKey" + fromToKey);
        System.out.println("toFromDid" + toFromDid);
        System.out.println("toFromKey" + toFromKey);

        System.out.println(toName + " -> Create and store in Wallet " + toName + " new DID");

        DidResults.CreateAndStoreMyDidResult result = Did.createAndStoreMyDid(
                (Wallet) to.get("wallet"), 
                "{}")
        .get();
        String toDid = result.getDid();
        String toKey = result.getVerkey();

        System.out.println(toName + " -> Authcrypt " + toName + " DID info\" for " + fromName);

        JSONObject didInfo = new JSONObject();
        didInfo.put("did", toDid);
        didInfo.put("verkey", toKey);
        to.put("didInfo", didInfo.toString());

        byte[] authcryptedDidInfo = Crypto.authCrypt(
                (Wallet) to.get("wallet"),
                toFromKey, 
                fromToKey, 
                to.get("didInfo").toString().getBytes())
        .get();
        to.put("authcryptedDidInfo", authcryptedDidInfo);

        System.out.println(toName + " -> Send authcrypted " + toName + " DID info" + toName + " to" + fromName);
        System.out.println(fromName + "-> Authdecrypted " + fromName + " DID info" + toName + "from " + toName);

        Map<String, String> authDecryptInfo = authDecrypt(
                (Wallet) from.get("wallet"), 
                fromToKey,
                (byte[]) to.get("authcryptedDidInfo")
                );
        String senderVerkey = authDecryptInfo.get("fromVerkey");
        String authDecryptedDidInfo = authDecryptInfo.get("decryptedMessage");

        System.out.println(fromName + "  -> Authenticate " + toName + " by comparision of Verkeys");
        assertEquals(senderVerkey,Did.
                keyForDid(
                        (Pool) from.get("pool"), 
                        (Wallet) from.get("wallet"), 
                        toFromDid)
                .get());

        System.out.println(
                fromName + " -> Send Nym to Ledger for " + toName + " DID with " + to.get("role").toString() + " Role");
        String authDecryInfo = new String(authDecryptedDidInfo);
        JSONObject authDecryptedDidInfoObject = new JSONObject(authDecryInfo);
        sendNYM(
                (Pool) from.get("pool"), 
                (Wallet) from.get("wallet"), 
                from.get("did").toString(),
                authDecryptedDidInfoObject.getString("did"), 
                authDecryptedDidInfoObject.getString("verkey"),
                to.get("role").toString()
                );

        return toDid;
    }

    public void sendNYM(Pool pool, Wallet wallet, String did, String newDid, String newKey, String role)
            throws Exception {
        // Build Nym Request
        String nymRequest = Ledger.buildNymRequest(did, newDid, newKey, null, role)
                                  .get();
        // Trustee Sign Nym Request
        String nymResponseJson = Ledger.signAndSubmitRequest(pool, wallet, did, nymRequest)
                                  .get();
        JSONObject nymResponse = new JSONObject(nymResponseJson);

        assertEquals(newDid,nymResponse.
                        getJSONObject("result").
                        getJSONObject("txn").
                        getJSONObject("data").
                        getString("dest"));
        assertEquals(newKey,nymResponse.
                        getJSONObject("result").
                        getJSONObject("txn").
                        getJSONObject("data").
                        getString("verkey"));
    }

    public String walletConfig(String operation, String walletConfigStr) {
        if (storageType == null) {
            return walletConfigStr;
        }
        JSONObject walletConfigJson = new JSONObject(walletConfigStr);
        walletConfigJson.put("storage_type", storageType);

        if (config != null) {
            JSONObject configObject = new JSONObject(config);
            walletConfigJson.put("storage_config", configObject);
        }

        return walletConfigJson.toString();
    }

    public String walletCredentials(String operation, String walletCredentialsStr) {
        if (storageType == null) {
            return walletCredentialsStr;
        }
        JSONObject walletCredentialsJson = new JSONObject(walletCredentialsStr);

        if (creds != null) {
            JSONObject credObject = new JSONObject(creds);
            walletCredentialsJson.put("storage_credentials", credObject);
        }

        return walletCredentialsJson.toString();
    }

    public Map<String, String> authDecrypt(Wallet wallet, String key, byte[] message) throws Exception {
        CryptoResults.AuthDecryptResult result = Crypto.authDecrypt(
                wallet, 
                key, 
                message)
        .get();
        String fromVerkey = result.getVerkey();
        byte[] decryptedMessageJson = result.getDecryptedMessage();
        String decryptedMessage = new String(decryptedMessageJson);

        Map<String, String> response = new HashMap<>();
        response.put("fromVerkey", fromVerkey);
        response.put("decryptedMessage", decryptedMessage);

        return response;
    }

    public void sendSchema(Pool pool, Wallet wallet, String did, String schema) throws Exception {
        String schemaRequest = Ledger.buildSchemaRequest(did, schema).get();
        Ledger.signAndSubmitRequest(pool, wallet, did, schemaRequest).get();
    }

    public LedgerResults.ParseResponseResult getSchema(Pool pool, String did, String schemaId) throws Exception {
        String getSchemaRequest = Ledger.buildGetSchemaRequest(did, schemaId).get();
        String getSchemaResponse = Ledger.submitRequest(pool, getSchemaRequest).get();
        return Ledger.parseGetSchemaResponse(getSchemaResponse).get();
    }

    public void sendCredDef(Pool pool, Wallet wallet, String did, String credDefJson) throws Exception {
        String credDefRequest = Ledger.buildCredDefRequest(did, credDefJson).get();
        Ledger.signAndSubmitRequest(pool, wallet, did, credDefRequest).get();
    }

    public ParseResponseResult getCredDef(Pool pool, String did, String credDefId) throws Exception {
        String getCredDefRequest = Ledger.buildGetCredDefRequest(did, credDefId).get();
        String getCredDefResponse = Ledger.submitRequest(pool, getCredDefRequest).get();
        return Ledger.parseGetCredDefResponse(getCredDefResponse).get();
    }

    public String getCredentialForReferent(CredentialsSearchForProofReq credentialsSearch, String referent) throws Exception {
        JSONArray credentials = new JSONArray(credentialsSearch.fetchNextCredentials(referent, 10).get());
        System.out.println("credentials"+credentials.toString());
        return credentials.getJSONObject(0).getJSONObject("cred_info").toString();
    }

    public Map<String, String> proverGetEntitiesFromLedger(Pool pool, String did, JSONObject identifiers, String actor, Long timestampFrom, Long timestampTo) throws Exception {
        JSONObject schemas = new JSONObject();
        JSONObject credDefs = new JSONObject();
        JSONObject revStates = new JSONObject();

        Iterator iterator = identifiers.keys();
        while (iterator.hasNext()) {
            String key = (String) iterator.next();
            String value = identifiers.getString(key);
            JSONObject item = new JSONObject(value);

            System.out.println(actor + " -> Get Schema from Ledger");
            LedgerResults.ParseResponseResult result = getSchema(pool, did, item.getString("schema_id"));
            String receivedSchemaId = result.getId();
            String receivedSchema = result.getObjectJson();
            schemas.put(receivedSchemaId, new JSONObject(receivedSchema));
            System.out.println("item"+item.toString());
            System.out.println(actor+" -> Get Claim Definition from Ledger");
            LedgerResults.ParseResponseResult pResponseResult =  getCredDef(pool, did, item.getString("cred_def_id"));
            credDefs.put(pResponseResult.getId(),new JSONObject( pResponseResult.getObjectJson()));
            System.out.println("rev_reg_id:"+item.get("rev_reg_id").toString());
        
            if (item.has("rev_reg_id") && !item.isNull("rev_reg_id")) {
                System.out.println(actor + " -> Get Revocation Registry Definition from Ledger");
                String getRevocRegDefRequest = Ledger.buildGetRevocRegDefRequest(
                        did,
                        item.getString("rev_reg_id"))
                .get();
                String getRevocRegDefResponse = Ledger.submitRequest(pool, getRevocRegDefRequest).get();
                LedgerResults.ParseResponseResult responseResult = Ledger.parseGetRevocRegDefResponse(
                        getRevocRegDefResponse)
                .get();
                String revRegId = responseResult.getId();
                String revocRegDefJson = responseResult.getObjectJson();

                System.out.println(actor + " -> Get Revocation Registry Delta from Ledger");
                timestampTo = timestampTo == null ? new Integer( new Long(System.currentTimeMillis()).toString().substring(0,10 )) : timestampTo;
                timestampFrom = timestampTo;
                System.out.println("did:"+did);
                System.out.println("rev_reg_id"+item.getString("rev_reg_id"));
                System.out.println("timestampFrom"+timestampFrom);
                System.out.println("timeStampTo"+timestampTo);
                String getRevocRegDeltaRequest = Ledger.buildGetRevocRegDeltaRequest(
                        did, 
                        item.getString("rev_reg_id"), 
                        timestampFrom, 
                        timestampTo)
                .get();
                String getRevocRegDeltaResponse = Ledger.submitRequest(
                        pool,
                        getRevocRegDeltaRequest)
                .get();
                LedgerResults.ParseRegistryResponseResult parseRegistryResponseResult = Ledger.parseGetRevocRegDeltaResponse(
                        getRevocRegDeltaResponse)
                .get();
                revRegId = parseRegistryResponseResult.getId();
                String revocRegDeltaJson = parseRegistryResponseResult.getObjectJson();
                Long timeStamp = parseRegistryResponseResult.getTimestamp();

                JSONObject revocRegDefJsonObject = new JSONObject(revocRegDefJson);
                File file = new File(revocRegDefJsonObject.getJSONObject("value").getString("tailsLocation").replace('\\', '/'));
                String tailsReaderConfig = "{ \"base_dir\":\"" + file.getParent()
                        + "\", \"uri_pattern\":\"\"}";
                System.out.println("tailsReaderConfig"+tailsReaderConfig);
                BlobStorageReader reader = BlobStorageReader.openReader("default", tailsReaderConfig).get();

                System.out.println(actor + " - Create Revocation State");
                String revStateJson = Anoncreds.createRevocationState(
                        reader.getBlobStorageReaderHandle(), 
                        revocRegDefJson, 
                        revocRegDeltaJson, 
                        timeStamp, 
                        item.getString("cred_rev_id"))
                .get();
                JSONObject revStateJsonObject = new JSONObject(revStateJson);
                JSONObject revJson = new JSONObject();
                revJson.put(Long.toString(timeStamp),  revStateJsonObject);
                revStates.put(revRegId, revJson);
            }

        }
        Map<String, String> response = new HashMap<>();
        response.put("schemas", schemas.toString());
        response.put("credDefs", credDefs.toString());
        response.put("revStates", revStates.toString());

        return response;
    }

    public Map<String, String> verifierGetEntitiesFromLedger(Pool pool, String did, JSONArray identifiers, String actor, Long timestamp) throws Exception {
        JSONObject schemas = new JSONObject();
        JSONObject credDefs = new JSONObject();
        JSONObject revRegDefs = new JSONObject();
        JSONObject revRegs = new JSONObject();

        for (int i =0; i < identifiers.length(); i++) {

            JSONObject item = identifiers.getJSONObject(i);
            System.out.println(actor + " -> Get Schema from Ledger");
            LedgerResults.ParseResponseResult parseResponseResult = getSchema(
                    pool, 
                    did, 
                    item.getString("schema_id")
                    );
            String reveivedSchemaId = parseResponseResult.getId();
            String receivedSchema = parseResponseResult.getObjectJson();
            JSONObject receivedSchemaObject = new JSONObject(receivedSchema);
            schemas.put(reveivedSchemaId, receivedSchemaObject);

            System.out.println(actor + " -> Get Claim Definition from Ledger");
            LedgerResults.ParseResponseResult getDefinitionResponse = getCredDef(
                    pool, 
                    did, 
                    item.getString("cred_def_id")
                    );
            String receivedCredDefId = getDefinitionResponse.getId();
            String receivedCredDef = getDefinitionResponse.getObjectJson();
            JSONObject receivedCredDefObject = new JSONObject(receivedCredDef);
            credDefs.put(receivedCredDefId, receivedCredDefObject);

            if (item.has("rev_reg_id") && !item.isNull("rev_reg_id")) {
                System.out.println(actor + " -> Get Revocation Definition from Ledger");
                String getRevocRegDefRequest = Ledger.buildGetRevocRegDefRequest(
                        did, 
                        item.getString("rev_reg_id"))
                .get();
                String getRevocRegDefResponse = Ledger.submitRequest(pool, getRevocRegDefRequest).get();
                LedgerResults.ParseResponseResult parseGetRevocRegDefResult = Ledger.
                        parseGetRevocRegDefResponse(
                                getRevocRegDefResponse)
                        .get();
                String revRegId = parseGetRevocRegDefResult.getId();
                String revocRegDefJson = parseGetRevocRegDefResult.getObjectJson();

                System.out.println(actor + " -> Get Revocation Registry from Ledger");
                timestamp = timestamp != null ? timestamp : item.getLong("timestamp");
                String getRevocRegRequest = Ledger.buildGetRevocRegRequest(
                        did, 
                        item.getString("rev_reg_id"), 
                        timestamp)
                .get();
                String getRevocRegResponse = Ledger.submitRequest(pool, getRevocRegRequest).get();
                LedgerResults.ParseRegistryResponseResult parseRegistryResponseResult = Ledger.
                        parseGetRevocRegResponse(
                                getRevocRegResponse)
                        .get();
                revRegId = parseRegistryResponseResult.getId();
                String revRegJson = parseRegistryResponseResult.getObjectJson();
                JSONObject revRegJsonObject = new JSONObject(revRegJson);
                Long timestamp2 = parseRegistryResponseResult.getTimestamp();
                JSONObject revRegIdObject = new JSONObject();
                revRegIdObject.put(Long.toString(timestamp2), revRegJsonObject);
                revRegs.put(revRegId, revRegIdObject);
                revRegDefs.put(revRegId, new JSONObject(revocRegDefJson));
            }
        }
        Map<String, String> response = new HashMap<>();
        response.put("schemas", schemas.toString());
        response.put("credDefs", credDefs.toString());
        response.put("revRegDefs", revRegDefs.toString());
        response.put("revRegs", revRegs.toString());

        return response;
    }

    public void applyLoanBasic( Map<String, Object> thrift, Map<String,Object> alice ,Map<String , Object> acme) throws Exception
    {
        System.out.println("==============================");
        System.out.println("== Apply for the loan with Thrift - Job-Certificate proving  ==");
        System.out.println("------------------------------");

        System.out.println("\"Thrift\" -> Create \"Loan-Application-Basic\" Proof Request");
        String applyLoanProofRequest = "{\"nonce\":\"123432421212\",\"name\":\"Loan-Application-Basic\",\"version\":\"0.1\",\"requested_attributes\":{\"attr1_referent\":{\"name\":\"employee_status\",\"restrictions\":[{\"cred_def_id\":\""+acme.get("jobCertificateCredDefId").toString()+"\"}]}},\"requested_predicates\":{\"predicate1_referent\":{\"name\":\"salary\",\"p_type\":\">=\",\"p_value\":2000,\"restrictions\":[{\"cred_def_id\":\""+acme.get("jobCertificateCredDefId").toString()+"\"}]},\"predicate2_referent\":{\"name\":\"experience\",\"p_type\":\">=\",\"p_value\":1,\"restrictions\":[{\"cred_def_id\":\""+acme.get("jobCertificateCredDefId").toString()+"\"}]}},\"non_revoked\":{\"to\":"+new Integer( new Long(System.currentTimeMillis()).toString().substring(0,10 ))+"}}";
        System.out.println("applyLoanProofRequest:"+applyLoanProofRequest);
        thrift.put("applyLoanProofRequest", applyLoanProofRequest);

        System.out.println("\"Thrift\" -> Get key for Alice did");
        thrift.put("aliceKeyForThrift", Did.
                keyForDid(
                        (Pool)thrift.get("pool"),
                        (Wallet) thrift.get("wallet"), 
                        new JSONObject( thrift.get("aliceConnectionResponse").toString()).getString("did"))
                .get());
        
        System.out.println("\"Thrift\" -> Authcrypt \"Loan-Application-Basic\" Proof Request for Alice");
        thrift.put("authcryptedApplyLoanProofRequest", Crypto.
                authCrypt((Wallet)thrift.get("wallet"),
                thrift.get("keyForAlice").toString(), 
                thrift.get("aliceKeyForThrift").toString(),
                applyLoanProofRequest.getBytes())
                .get());
        
        System.out.println("\"Thrift\" -> Send authcrypted \"Loan-Application-Basic\" Proof Request to Alice");
        alice.put("authcryptedApplyLoanProofRequest", thrift.get("authcryptedApplyLoanProofRequest"));

        System.out.println("\"Alice\" -> Authdecrypt \"Loan-Application-Basic\" Proof Request from Thrift");
        Map<String , String> aliceDecryptThriftResponse = authDecrypt(
                (Wallet)alice.get("wallet"), 
                alice.get("keyForThrift").toString(), 
                (byte[])alice.get("authcryptedApplyLoanProofRequest"));
        alice.put("thriftKeyForAlice",aliceDecryptThriftResponse.get("fromVerkey"));
        alice.put("applyLoanProofRequest", aliceDecryptThriftResponse.get("decryptedMessage"));

        System.out.println("\"Alice\" -> Get credentials for \"Loan-Application-Basic\" Proof Request");
        CredentialsSearchForProofReq searchForApplyLoanProofRequest = CredentialsSearchForProofReq.
                open(
                        (Wallet)alice.get("wallet"),
                        alice.get("applyLoanProofRequest").toString(), 
                        null)
                .get();

        String credForAttr1 = getCredentialForReferent(searchForApplyLoanProofRequest,"attr1_referent");
        String credForPredicate1 = getCredentialForReferent(searchForApplyLoanProofRequest, "predicate1_referent");
        String credForPredicate2 = getCredentialForReferent(searchForApplyLoanProofRequest, "predicate2_referent");
        JSONObject credForAttr1Object = new JSONObject(credForAttr1);
        JSONObject credForPredicate1Object = new JSONObject(credForPredicate1);
        JSONObject credForPredicate2Object = new JSONObject(credForPredicate1);

        searchForApplyLoanProofRequest.close();

        JSONObject credsForApplyLoanProofObject = new JSONObject();
        credsForApplyLoanProofObject.put(credForAttr1Object.getString("referent"), credForAttr1);
        credsForApplyLoanProofObject.put(credForPredicate1Object.getString("referent"), credForPredicate1);
        credsForApplyLoanProofObject.put(credForPredicate2Object.getString("referent"), credForPredicate2);
        
        alice.put("credsForApplyLoanProof", credsForApplyLoanProofObject.toString());
        long requestedTimestamp = new JSONObject(thrift.get("applyLoanProofRequest").toString()).getJSONObject("non_revoked").getLong("to");
        Map<String , String> aliceEntityFromLedgerResponse = proverGetEntitiesFromLedger(
                (Pool)alice.get("pool"), 
                alice.get("didForThrift").toString(), 
                credsForApplyLoanProofObject ,
                alice.get("name").toString(), 
                null,
                requestedTimestamp
                );
        alice.put("schemasForLoanApp", aliceEntityFromLedgerResponse.get("schemas"));
        alice.put("credDefsForLoanApp", aliceEntityFromLedgerResponse.get("credDefs"));
        alice.put("revocStatesForLoanApp", aliceEntityFromLedgerResponse.get("revStates"));

        System.out.println("\"Alice\" -> Create \"Loan-Application-Basic\" Proof");
        String  revocStatesForLoanApp = alice.get("revocStatesForLoanApp").toString();
        Integer timestampForAttr1 = new Integer(new Long(getTimestampForAttribute(credForAttr1Object, revocStatesForLoanApp)).toString().substring(0,10));
        Integer timestampForPredicate1 = new Integer(new Long(getTimestampForAttribute(credForPredicate1Object, revocStatesForLoanApp)).toString().substring(0,10));
        Integer timestampForPredicate2 = new Integer(new Long(getTimestampForAttribute(credForPredicate2Object, revocStatesForLoanApp)).toString().substring(0,10));
        
        String applyLoanRequestedCred = "{\"self_attested_attributes\":{},\"requested_attributes\": {\"attr1_referent\":{\"cred_id\":\""+credForAttr1Object.getString("referent")+"\",\"revealed\": true,\"timestamp\":"+timestampForAttr1+"}},\"requested_predicates\":{\"predicate1_referent\":{\"cred_id\":\""+credForPredicate1Object.getString("referent")+"\",\"timestamp\":"+timestampForPredicate1+"},\"predicate2_referent\":{\"cred_id\": \""+credForPredicate2Object.getString("referent")+"\",\"timestamp\":"+timestampForPredicate2+"}}}";

        alice.put("applyLoanRequestedCreds", applyLoanRequestedCred);
        System.out.println("applyLoanProofRequest"+alice.get("applyLoanProofRequest").toString());
        System.out.println("applyLoanRequestedCreds"+applyLoanRequestedCred);
        System.out.println("masterSecretId"+ alice.get("masterSecretId").toString());
        System.out.println("schemasForLoanApp"+alice.get("schemasForLoanApp").toString());
        System.out.println("credDefsForLoanApp"+alice.get("credDefsForLoanApp").toString());
        System.out.println("revocStatesForLoanApp"+alice.get("revocStatesForLoanApp").toString());
        alice.put("applyLoanProof", Anoncreds.
                proverCreateProof(
                        (Wallet)alice.get("wallet"),
                        alice.get("applyLoanProofRequest").toString().replaceAll("\\\\", ""), 
                        alice.get("applyLoanRequestedCreds").toString().replaceAll("\\\\", ""),
                        alice.get("masterSecretId").toString().replaceAll("\\\\", ""), 
                        alice.get("schemasForLoanApp").toString().replaceAll("\\\\", ""), 
                        alice.get("credDefsForLoanApp").toString().replaceAll("\\\\", ""), 
                        alice.get("revocStatesForLoanApp").toString().replaceAll("\\\\", ""))
                .get());
        
        System.out.println("\"Alice\" -> Authcrypt \"Loan-Application-Basic\" Proof for Thrift");
        alice.put("authcryptedAliceApplyLoanProof", Crypto.authCrypt((Wallet)alice.get("wallet"), alice.get("keyForThrift").toString(),
                alice.get("thriftKeyForAlice").toString(), 
                alice.get("applyLoanProof").toString().getBytes())
                .get());
        
        System.out.println("\"Alice\" -> Send authcrypted \"Loan-Application-Basic\" Proof to Thrift");
        thrift.put("authcryptedAliceApplyLoanProof", alice.get("authcryptedAliceApplyLoanProof"));

        System.out.println("\"Thrift\" -> Authdecrypted \"Loan-Application-Basic\" Proof from Alice");
        Map<String , String> aliceDecryptLoanResponse = authDecrypt(
                (Wallet)thrift.get("wallet"), 
                thrift.get("keyForAlice").toString(), 
                (byte[])thrift.get("authcryptedAliceApplyLoanProof")
                );
        String authdecryptedAliceApplyLoanProof = aliceDecryptLoanResponse.get("decryptedMessage");
        thrift.put("aliceApplyLoanProof", authdecryptedAliceApplyLoanProof);

        System.out.println("\"Thrift\" -> Get Schemas, Credential Definitions and Revocation Registries from Ledger"
                +" required for Proof verifying");
        
        Map<String , String> thriftEntitiesFromLedger = verifierGetEntitiesFromLedger(
                (Pool)thrift.get("pool"), 
                thrift.get("did").toString(),
                new JSONObject(authdecryptedAliceApplyLoanProof).getJSONArray("identifiers"), 
                thrift.get("name").toString(), 
                requestedTimestamp
                );
        thrift.put("schemasForLoanApp", thriftEntitiesFromLedger.get("schemas"));
        thrift.put("credDefsForLoanApp", thriftEntitiesFromLedger.get("credDefs"));
        thrift.put("revocDefsForLoanApp", thriftEntitiesFromLedger.get("revRegDefs"));
        thrift.put("revocRegsForLoanApp", thriftEntitiesFromLedger.get("revRegs"));

        System.out.println("\"Thrift\" -> Verify \"Loan-Application-Basic\" Proof from Alice");
        assertEquals("Permanent", new JSONObject(authdecryptedAliceApplyLoanProof).
                getJSONObject("requested_proof").
                getJSONObject("revealed_attrs").
                getJSONObject("attr1_referent").
                getString("raw")
                );
        

    }
    public Long getTimestampForAttribute(JSONObject credForAttribute , String revocStates)
    {
        JSONObject revocStatesObject = new JSONObject(revocStates);
        if(credForAttribute.isNull("rev_reg_id"))
        {
                return null;
        }
        if(revocStatesObject.has(credForAttribute.getString("rev_reg_id")))
        {
                String revRegId = credForAttribute.getString("rev_reg_id");
                JSONObject revRegIdObject = revocStatesObject.getJSONObject(revRegId);
                Iterator keys = revRegIdObject.keys();
                JSONObject tmp = revRegIdObject.getJSONObject(keys.next().toString());
                return tmp.getLong("timestamp");
        }
        return null;
    }
}
























