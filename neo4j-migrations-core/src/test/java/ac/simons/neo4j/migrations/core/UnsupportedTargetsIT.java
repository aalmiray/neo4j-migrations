/*
 * Copyright 2020-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ac.simons.neo4j.migrations.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.driver.exceptions.ClientException;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.TestcontainersConfiguration;

/**
 * @author Michael J. Simons
 */
class UnsupportedTargetsIT {

	@Test
	void migrationTargetDeterminationMustNotFailWithOlderEnterprise() {

		for (String tag : new String[] { "neo4j:3.5-enterprise", "neo4j:3.5", "neo4j:4.0" }) {
			try (Neo4jContainer<?> neo4jWithoutMultiDB = new Neo4jContainer<>(tag)
				.withReuse(TestcontainersConfiguration.getInstance().environmentSupportsReuse())
				.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")) {

				neo4jWithoutMultiDB.start();

				Config config = Config.builder().withLogging(Logging.none()).build();
				try (Driver localDriver = GraphDatabase.driver(neo4jWithoutMultiDB.getBoltUrl(),
					AuthTokens.basic("neo4j", neo4jWithoutMultiDB.getAdminPassword()), config)) {

					MigrationsConfig migrationsConfig = MigrationsConfig.builder()
						.withPackagesToScan("ac.simons.neo4j.migrations.core.test_migrations.changeset1")
						.withSchemaDatabase("irrelevant")
						.build();

					MigrationContext ctx = new Migrations.DefaultMigrationContext(migrationsConfig, localDriver);
					Optional<String> migrationTarget = migrationsConfig.getMigrationTargetIn(ctx);

					if (tag.contains("3.5")) {
						assertThat(migrationTarget).isEmpty();
					} else {
						assertThat(migrationTarget).hasValue("neo4j");
					}
				}
			}
		}
	}

	@Test
	void impersonationOnOldDBShouldFail() {
		try (Neo4jContainer<?> neo4j43 = new Neo4jContainer<>("neo4j:4.3-enterprise")
			.withReuse(TestcontainersConfiguration.getInstance().environmentSupportsReuse())
			.withEnv("NEO4J_ACCEPT_LICENSE_AGREEMENT", "yes")) {

			neo4j43.start();

			Config config = Config.builder().withLogging(Logging.none()).build();

			try (Driver localDriver = GraphDatabase.driver(neo4j43.getBoltUrl(),
				AuthTokens.basic("neo4j", neo4j43.getAdminPassword()), config)) {

				Migrations migrations = new Migrations(MigrationsConfig.builder()
					.withPackagesToScan("ac.simons.neo4j.migrations.core.test_migrations.changeset1")
					.withImpersonatedUser("TheImposter")
					.build(), localDriver);

				assertThatExceptionOfType(ClientException.class).isThrownBy(migrations::info)
					.withMessageStartingWith(
						"Detected connection that does not support impersonation, please make sure to have all servers running 4.4 version or above and communicating over Bolt version 4.4");
			}
		}
	}
}
