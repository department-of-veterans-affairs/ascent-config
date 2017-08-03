package gov.va.ascent.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * An <tt>Ascent Configuration Server</tt> enabled for Spring Boot Application and 
 * Spring Cloud Config Server for externalized configuration in a distributed system.
 *
 */
@SpringBootApplication
@EnableConfigServer
@EnableDiscoveryClient
public class AscentConfigApplication {

	public static void main(String[] args) {
		SpringApplication.run(AscentConfigApplication.class, args);
	}
}
