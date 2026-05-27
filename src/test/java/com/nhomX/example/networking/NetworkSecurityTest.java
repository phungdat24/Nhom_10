package com.nhomX.example.networking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import com.nhomX.example.model.RegularUser;
import com.nhomX.example.model.Role;
import org.junit.jupiter.api.Test;

class NetworkSecurityTest {
    @Test
    void objectFilterAllowsProtocolMessage() throws Exception {
        Message original = new Message("FORGOT_PASSWORD_RESULT",
                new String[]{"true", "OTP sent"});

        Object decoded = readWithNetworkFilter(serialize(original));

        Message message = (Message) decoded;
        String[] data = (String[]) message.getData();
        assertEquals("FORGOT_PASSWORD_RESULT", message.getType());
        assertEquals("true", data[0]);
        assertEquals("OTP sent", data[1]);
    }

    @Test
    void objectFilterRejectsClassOutsideAllowList() throws Exception {
        byte[] payload = serialize(new File("not-allowed.txt"));

        assertThrows(InvalidClassException.class, () -> readWithNetworkFilter(payload));
    }

    @Test
    void objectFilterAllowsLoginSuccessUserPayload() throws Exception {
        RegularUser user = new RegularUser(
                "user-1", "bidder@example.com", "hash", "Bidder", 1000L);
        user.addRole(Role.BIDDER);
        user.addRole(Role.SELLER);
        Message original = Message.loginSuccess(user);

        Message decoded = (Message) readWithNetworkFilter(serialize(original));
        RegularUser decodedUser = (RegularUser) decoded.getData();

        assertEquals("LOGIN_SUCCESS", decoded.getType());
        assertEquals("bidder@example.com", decodedUser.getUserName());
        assertTrue(decodedUser.hasRole(Role.BIDDER));
        assertTrue(decodedUser.hasRole(Role.SELLER));
    }

    @Test
    void staleForgotPasswordOtpRemovalDoesNotDeleteNewOtp() {
        String email = "race-test@example.com";
        ClientHandler.otpStorage.remove(email);
        ClientHandler.OtpData oldOtp = new ClientHandler.OtpData("111111");
        ClientHandler.OtpData newOtp = new ClientHandler.OtpData("222222");

        ClientHandler.otpStorage.put(email, oldOtp);
        ClientHandler.otpStorage.put(email, newOtp);

        assertFalse(ClientHandler.otpStorage.remove(email, oldOtp));
        assertSame(newOtp, ClientHandler.otpStorage.get(email));

        ClientHandler.otpStorage.remove(email);
    }

    private static byte[] serialize(Object object) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(object);
        }
        return bytes.toByteArray();
    }

    private static Object readWithNetworkFilter(byte[] payload) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(payload))) {
            input.setObjectInputFilter(NetworkObjectFilter.create());
            return input.readObject();
        }
    }
}
