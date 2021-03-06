package org.hyperledger.indy.sdk.ledger;

import org.hyperledger.indy.sdk.IndyIntegrationTestWithPoolAndSingleWallet;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class RevocRegEntryRequestTest extends IndyIntegrationTestWithPoolAndSingleWallet {

	@Test
	public void testBuildRevocRegEntryRequestWorks() throws Exception {
		String expectedResult ="\"operation\": {\n" +
						"            \"type\": \"114\",\n" +
						"            \"revocRegDefId\": \"RevocRegID\",\n" +
						"            \"revocDefType\": \"CL_ACCUM\",\n" +
						"            \"value\": {\"accum\": \"1 0000000000000000000000000000000000000000000000000000000000000000 1 0000000000000000000000000000000000000000000000000000000000000000 1 0000000000000000000000000000000000000000000000000000000000000000 1 0000000000000000000000000000000000000000000000000000000000000000 1 0000000000000000000000000000000000000000000000000000000000000000 1 0000000000000000000000000000000000000000000000000000000000000000\"}\n" +
						"        }";

		String value = "{\"ver\":\"1.0\"," +
				"        \"value\": {\n" +
				"            \"accum\": \"1 0000000000000000000000000000000000000000000000000000000000000000 1 0000000000000000000000000000000000000000000000000000000000000000 1 0000000000000000000000000000000000000000000000000000000000000000 1 0000000000000000000000000000000000000000000000000000000000000000 1 0000000000000000000000000000000000000000000000000000000000000000 1 0000000000000000000000000000000000000000000000000000000000000000\"\n" +
				"        }}";

		String request = Ledger.buildRevocRegEntryRequest(DID, "RevocRegID", "CL_ACCUM", value).get();

		assertTrue(request.replaceAll("\\s+", "").contains(expectedResult.replaceAll("\\s+", "")));
	}
}
