package org.aprsdroid.app;

import org.junit.Test;

import static org.junit.Assert.*;

public class AprsPacketTests {
	@Test
	public void testBasic() {
		assertEquals(18403, AprsPacket.passcode("AB1CD"));
	}
}
