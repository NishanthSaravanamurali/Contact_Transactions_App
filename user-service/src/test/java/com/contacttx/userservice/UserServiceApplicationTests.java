package com.contacttx.userservice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserServiceApplicationTests {

	@Test
	void applicationEntryPointIsAvailable() {
		assertThat(UserServiceApplication.class).isNotNull();
	}
}
