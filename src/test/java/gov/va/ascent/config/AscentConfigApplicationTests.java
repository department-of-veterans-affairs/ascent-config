package gov.va.ascent.config;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {"spring.cloud.config.server.git.cloneOnStart=false"})
public class AscentConfigApplicationTests {

	@Test
	public void contextLoads() {
	}

}
