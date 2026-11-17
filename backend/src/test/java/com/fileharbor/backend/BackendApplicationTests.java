package com.fileharbor.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		// Fake, non-secret values: building the client does not contact Azure.
		"azure.storage.connection-string=DefaultEndpointsProtocol=https;AccountName=testaccount;AccountKey=dGVzdA==;EndpointSuffix=core.windows.net",
		"azure.storage.blob-container-name=test-container"
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
