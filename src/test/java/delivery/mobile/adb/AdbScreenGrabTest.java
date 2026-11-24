package delivery.mobile.adb;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class AdbScreenGrabTest {

    @Test
    public void acceptsAPngHeaderAndRejectsText() throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        assertEquals(AdbScreenGrab.requirePng(png).length, 8);
        try {
            AdbScreenGrab.requirePng("error: device not found".getBytes());
            fail("text was accepted as a PNG");
        } catch (AdbInventoryException expected) {
            // rejected
        }
    }
}
